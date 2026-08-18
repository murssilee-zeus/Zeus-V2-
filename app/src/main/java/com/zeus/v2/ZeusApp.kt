package com.zeus.v2

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZeusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val msg = "=== CRASH $ts thread=${t.name} ===\n" + Log.getStackTraceString(e)
                Log.e("ZeusCrash", msg)
                File(filesDir, "crash.log").appendText(msg + "\n")
                File(filesDir, "crash-${System.currentTimeMillis()}.log").writeText(msg)
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
}
