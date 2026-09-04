package com.zeus.v2

import android.content.Context
import android.util.Log
import org.json.JSONObject

object EqPrefs {
    private const val PREFS = "zeus_eq_cfg"
    private const val KEY = "config_v1"
    private const val NAMED_KEY = "named_presets_v1"

    fun save(context: Context, settings: EqSettings) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, settings.toJson()).apply()
        } catch (e: Throwable) {
            Log.e("ZeusPrefs", "save: ${Log.getStackTraceString(e)}")
        }
    }

    fun saveNamed(context: Context, name: String, settings: EqSettings) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val root = JSONObject(prefs.getString(NAMED_KEY, "{}") ?: "{}")
            root.put(name.trim(), settings.toJson())
            prefs.edit().putString(NAMED_KEY, root.toString()).apply()
        } catch (e: Throwable) { Log.e("ZeusPrefs", "saveNamed: ${Log.getStackTraceString(e)}") }
    }

    fun loadNamed(context: Context, name: String): EqSettings? = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = JSONObject(prefs.getString(NAMED_KEY, "{}") ?: "{}")
        if (!root.has(name)) null else EqSettings.fromJson(root.getString(name))
    } catch (e: Throwable) { Log.e("ZeusPrefs", "loadNamed: ${Log.getStackTraceString(e)}"); null }

    fun listNamed(context: Context): List<String> = try {
        val root = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(NAMED_KEY, "{}") ?: "{}")
        buildList { val keys = root.keys(); while (keys.hasNext()) add(keys.next()) }.sorted()
    } catch (_: Throwable) { emptyList() }

    fun deleteNamed(context: Context, name: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val root = JSONObject(prefs.getString(NAMED_KEY, "{}") ?: "{}")
            root.remove(name)
            prefs.edit().putString(NAMED_KEY, root.toString()).apply()
        } catch (e: Throwable) { Log.e("ZeusPrefs", "deleteNamed: ${Log.getStackTraceString(e)}") }
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
