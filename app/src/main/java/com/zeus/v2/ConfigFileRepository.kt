package com.zeus.v2

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigFileRepository {
    private const val DIR = "configurations"
    private const val LATEST = "zeus_latest.json"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun saveLatest(context: Context, settings: EqSettings) {
        runCatching { File(dir(context), LATEST).writeText(settings.toJson()) }
    }

    fun saveNamed(context: Context, name: String, settings: EqSettings) {
        runCatching {
            val safe = name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
            if (safe.isNotBlank()) File(dir(context), "$safe.json").writeText(settings.toJson())
        }
    }

    fun latest(context: Context): String? = runCatching {
        File(dir(context), LATEST).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun fileNameForExport(): String =
        "zeus_eq_pro18_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
}
