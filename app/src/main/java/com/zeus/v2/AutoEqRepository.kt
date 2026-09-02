package com.zeus.v2

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

data class AutoEqProfile(val preamp: Float, val filters: List<AutoEqFilter>)
data class AutoEqFilter(val frequency: Float, val gain: Float, val q: Float, val type: EqBand.FilterType = EqBand.FilterType.PEAK)
data class AutoEqModel(
    val name: String,
    val source: String = "",
    val type: String = "",
    val path: String = ""
)

object AutoEqRepository {
    @Volatile private var cachedModels: List<AutoEqModel>? = null
    @Volatile private var cachedTargets: List<String>? = null

    private fun readIndex(context: Context): List<AutoEqModel> = runCatching {
        val json = context.assets.open("autoeq/index.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(AutoEqModel(o.optString("n"), o.optString("s"), o.optString("t"), o.optString("p")))
            }
        }
    }.getOrDefault(emptyList())

    fun allModels(context: Context): List<AutoEqModel> {
        cachedModels?.let { return it }
        return synchronized(this) { cachedModels ?: readIndex(context).also { cachedModels = it } }
    }

    fun manufacturers(context: Context): List<String> =
        allModels(context).map { it.source.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    fun types(context: Context): List<String> =
        allModels(context).map { it.type.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    fun models(context: Context, query: String = "", manufacturer: String = "", type: String = ""): List<AutoEqModel> {
        val q = query.trim().lowercase(Locale.ROOT)
        val maker = manufacturer.trim().lowercase(Locale.ROOT)
        val kind = type.trim().lowercase(Locale.ROOT)
        return allModels(context).filter {
            (q.isEmpty() || it.name.lowercase(Locale.ROOT).contains(q) || it.source.lowercase(Locale.ROOT).contains(q)) &&
            (maker.isEmpty() || it.source.lowercase(Locale.ROOT) == maker) &&
            (kind.isEmpty() || it.type.lowercase(Locale.ROOT) == kind)
        }
    }

    fun targets(context: Context): List<String> {
        cachedTargets?.let { return it }
        return synchronized(this) {
            cachedTargets ?: runCatching {
                context.assets.list("targets")?.filter { it.isNotBlank() }?.sorted() ?: emptyList()
            }.getOrDefault(emptyList()).also { cachedTargets = it }
        }
    }

    suspend fun load(context: Context, model: AutoEqModel): AutoEqProfile {
        return withContext(Dispatchers.IO) {
        require(model.path.isNotBlank()) { "Perfil AutoEQ no disponible" }
        val text = context.assets.open("autoeq/profiles/" + model.path).bufferedReader().use { it.readText() }
        parse(text)
    }

    private fun parse(text: String): AutoEqProfile {
        var preamp = 0f
        val filters = mutableListOf<AutoEqFilter>()
        val pre = Regex("""(?i)^\s*Preamp\s*:\s*([-+]?\d+(?:\.\d+)?)\s*dB""")
        val filter = Regex("""(?i)^\s*Filter\s*\d+\s*:\s*ON\s+(PK|LSC|HSC|LP|HP)\s+Fc\s+([-+]?\d+(?:\.\d+)?)\s*Hz\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB\s+Q\s+([-+]?\d+(?:\.\d+)?)""")
        text.lineSequence().forEach { line ->
            pre.find(line)?.let { preamp = it.groupValues[1].toFloatOrNull() ?: preamp }
            filter.find(line)?.let {
                val type = when (it.groupValues[1].uppercase(Locale.ROOT)) {
                    "LSC" -> EqBand.FilterType.LOW_SHELF
                    "HSC" -> EqBand.FilterType.HIGH_SHELF
                    "LP" -> EqBand.FilterType.LOW_PASS
                    "HP" -> EqBand.FilterType.HIGH_PASS
                    else -> EqBand.FilterType.PEAK
                }
                filters += AutoEqFilter(it.groupValues[2].toFloat().coerceIn(20f, 20000f), it.groupValues[3].toFloat().coerceIn(-30f, 30f), it.groupValues[4].toFloat().coerceIn(.1f, 40f), type)
            }
        }
        AutoEqProfile(preamp.coerceIn(-30f, 12f), filters)
        }
    }
}
