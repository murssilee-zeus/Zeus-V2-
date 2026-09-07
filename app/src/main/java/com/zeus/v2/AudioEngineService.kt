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
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat

class AudioEngineService : Service() {

    companion object {
        const val CHANNEL_ID = "zeus_eq_channel"
        const val NOTIFICATION_ID = 1801
        const val ACTION_START_PCM = "com.zeus.v2.action.START_PCM"
        const val ACTION_STOP_PCM = "com.zeus.v2.action.STOP_PCM"
        const val EXTRA_MEDIA_PROJECTION_DATA = "com.zeus.v2.extra.MEDIA_PROJECTION_DATA"
    }

    private val binder = LocalBinder()
    @Volatile
    var audioEngine: AudioEngine? = null
        private set
    private val pcmEngine = PcmAudioEngine()
    private var pcmCapture: PcmCaptureEngine? = null
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
        val projectionData = intent?.let { readProjectionData(it) }
        val wantsPcm = intent?.action == ACTION_START_PCM && projectionData != null
        val notification = buildNotification(if (wantsPcm) "Zeus activo • iniciando captura PCM" else "Zeus activo • iniciando DSP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                if (wantsPcm) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent?.action == ACTION_STOP_PCM) {
            pcmCapture?.stop()
            pcmCapture = null
            updateNotification("Zeus activo • DSP en tiempo real • ruta externa")
        }

        if (wantsPcm && projectionData != null) {
            startPcmCapture(projectionData)
        }

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
                            updateNotification(if (pcmCapture?.running == true) {
                                "Zeus activo • PCM real • DSP en tiempo real"
                            } else {
                                "Zeus activo • DSP en tiempo real • ruta externa"
                            })
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

    private fun startPcmCapture(resultData: Intent) {
        if (pcmCapture?.running == true) return
        val capture = PcmCaptureEngine(this)
        val settings = audioEngine?.settings ?: EqPrefs.load(this) ?: EqSettings()
        if (capture.start(resultData, settings)) {
            pcmCapture = capture
            updateNotification("Zeus activo • PCM real • captura + DSP")
        } else {
            updateNotification("Zeus activo • PCM no disponible • ruta externa")
            android.util.Log.e("ZeusSvc", "PCM capture failed: ${capture.lastError}")
        }
    }

    @Suppress("DEPRECATION")
    private fun readProjectionData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        pcmCapture?.stop()
        pcmCapture = null
        audioEngine?.release()
        audioEngine = null
        pcmEngine.stop()
        super.onDestroy()
    }

    fun configurePcm(settings: EqSettings) {
        pcmEngine.configure(settings)
        pcmCapture?.configure(settings)
    }

    fun processPcm(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2) {
        pcmEngine.write(buffer, offset, frames)
    }

    fun isPcmCaptureRunning(): Boolean = pcmCapture?.running == true

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
