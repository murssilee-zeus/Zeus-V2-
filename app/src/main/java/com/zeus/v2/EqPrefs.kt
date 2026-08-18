package com.zeus.v2

import android.content.Context
import android.util.Log

object EqPrefs {
    private const val PREFS = "zeus_eq_cfg"
    private const val KEY = "config_v1"

    fun save(context: Context, settings: EqSettings) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, settings.toJson()).apply()
        } catch (e: Throwable) {
            Log.e("ZeusPrefs", "save: ${Log.getStackTraceString(e)}")
        }
    }

    fun load(context: Context): EqSettings? {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return null
            EqSettings.fromJson(raw)
        } catch (e: Throwable) {
            Log.e("ZeusPrefs", "load: ${Log.getStackTraceString(e)}")
            null
        }
    }
}
