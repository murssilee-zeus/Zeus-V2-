package com.zeus.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

class AudioEngineService : Service() {

    companion object {
        const val CHANNEL_ID = "zeus_eq_channel"
        const val NOTIFICATION_ID = 1801
    }

    private val binder = LocalBinder()
    @Volatile
    var audioEngine: AudioEngine? = null
        private set
    private val pcmEngine = PcmAudioEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var initializing = false

    inner class LocalBinder : Binder() {
        fun getService(): AudioEngineService = this@AudioEngineService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Zeus activo • iniciando DSP"))
        if (!initializing && audioEngine == null) {
            initializing = true
            Thread({
                try {
                    val engine = AudioEngine(this)
                    val saved = EqPrefs.load(this)
                    saved?.let {
                        engine.settings = it
                        pcmEngine.configure(it)
                    }
                    val ok = engine.attachToMediaSession()
                    if (ok) {
                        audioEngine = engine
                        if (saved == null) pcmEngine.configure(engine.settings)
                        mainHandler.post {
                            updateNotification("Zeus activo • DSP en tiempo real • ruta externa + PCM")
                        }
                    } else {
                        engine.release()
                        mainHandler.post { updateNotification("Zeus activo • motor de audio no disponible") }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("ZeusSvc", "init: " + android.util.Log.getStackTraceString(e))
                    mainHandler.post { updateNotification("Zeus activo • error al iniciar DSP") }
                } finally {
                    initializing = false
                }
            }, "ZeusAudioInit").start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        audioEngine?.release()
        audioEngine = null
        pcmEngine.stop()
        super.onDestroy()
    }

    /** Updates the direct PCM DSP route with the same settings used by Zeus. */
    fun configurePcm(settings: EqSettings) {
        pcmEngine.configure(settings)
    }

    /** Processes an interleaved stereo Float PCM block in-place. */
    fun processPcm(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2) {
        pcmEngine.write(buffer, offset, frames)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zeus EQ",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado del procesamiento de audio de Zeus EQ"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zeus EQ Pro18")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_eq_tile)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
