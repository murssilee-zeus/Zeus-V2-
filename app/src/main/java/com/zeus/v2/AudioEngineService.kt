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
import androidx.core.app.NotificationCompat

class AudioEngineService : Service() {

    companion object {
        const val CHANNEL_ID = "zeus_eq_channel"
        const val NOTIFICATION_ID = 1801
    }

    private val binder = LocalBinder()
    var audioEngine: AudioEngine? = null
        private set

    inner class LocalBinder : Binder() {
        fun getService(): AudioEngineService = this@AudioEngineService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
        val engine = AudioEngine(this)
        EqPrefs.load(this)?.let { engine.settings = it }
        engine.attachToMediaSession()
            audioEngine = engine
        } catch (e: Throwable) {
            android.util.Log.e("ZeusSvc", "onCreate: ${android.util.Log.getStackTraceString(e)}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
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
                description = "Mantiene el motor de audio de Zeus EQ activo"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zeus EQ Pro18")
            .setContentText("Motor de audio activo")
            .setSmallIcon(R.drawable.ic_eq_tile)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zeus EQ Pro18")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_eq_tile)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
