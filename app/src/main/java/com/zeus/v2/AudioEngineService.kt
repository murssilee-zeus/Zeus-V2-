package com.zeus.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
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
        const val EXTRA_PROJECTION_RESULT = "zeus.extra.PROJECTION_RESULT"
        const val EXTRA_PROJECTION_DATA = "zeus.extra.PROJECTION_DATA"
    }

    private val binder = LocalBinder()
    @Volatile
    var audioEngine: AudioEngine? = null
        private set
    @Volatile
    private var pcmPlayback: ZeusPcmPlaybackEngine? = null
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
        val projectionData = intent?.getParcelableExtraCompat<Intent>(EXTRA_PROJECTION_DATA)
        val hasProjection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            intent?.hasExtra(EXTRA_PROJECTION_RESULT) == true &&
            projectionData != null

        startAsForeground(hasProjection)
        startEngine(projectionData, intent?.getIntExtra(EXTRA_PROJECTION_RESULT, -1) ?: -1)
        return START_STICKY
    }

    private fun startAsForeground(withProjection: Boolean) {
        val notificationText = if (withProjection) {
            "Zeus activo • PCM Atmos • preparando entrada/salida"
        } else {
            "Zeus activo • iniciando DSP"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            startForeground(NOTIFICATION_ID, buildNotification(notificationText), type)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(notificationText))
        }
    }

    private fun startEngine(projectionData: Intent?, resultCode: Int) {
        if (initializing || audioEngine != null) return
        initializing = true

        Thread({
            try {
                val engine = AudioEngine(this)
                val saved = EqPrefs.load(this)
                saved?.let { engine.settings = it }
                val ok = engine.attachToMediaSession()

                if (!ok) {
                    engine.release()
                    mainHandler.post {
                        updateNotification("Zeus activo • motor de audio no disponible")
                    }
                    return@Thread
                }

                audioEngine = engine

                var pcmStarted = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    projectionData != null &&
                    resultCode > 0
                ) {
                    try {
                        val manager = getSystemService(MediaProjectionManager::class.java)
                        val projection = manager?.getMediaProjection(resultCode, projectionData)
                        if (projection != null) {
                            val route = ZeusPcmPlaybackEngine(engine, projection, 48000)
                            if (route.start()) {
                                pcmPlayback = route
                                pcmStarted = true
                            } else {
                                projection.stop()
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e(
                            "ZeusSvc",
                            "PCM projection init: " + android.util.Log.getStackTraceString(t)
                        )
                    }
                }

                mainHandler.post {
                    if (pcmStarted) {
                        updateNotification(
                            "Zeus activo • PCM IN → Atmos DSP → PCM OUT"
                        )
                    } else {
                        updateNotification(
                            "Zeus activo • Audio Framework • Parametric EQ + MBC + Limiter"
                        )
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e(
                    "ZeusSvc",
                    "init: " + android.util.Log.getStackTraceString(e)
                )
                mainHandler.post {
                    updateNotification("Zeus activo • error al iniciar DSP")
                }
            } finally {
                initializing = false
            }
        }, "ZeusAudioInit").start()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        pcmPlayback?.stop()
        pcmPlayback = null
        audioEngine?.release()
        audioEngine = null
        super.onDestroy()
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

    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key)
        }
    }
}
