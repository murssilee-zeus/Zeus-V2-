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
    private fun readIndex(context: Context): List<AutoEqModel> = runCatching {
        val json = context.assets.open("autoeq/index.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(AutoEqModel(
                    name = o.optString("n"),
                    source = o.optString("s"),
                    type = o.optString("t"),
                    path = o.optString("p")
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun models(context: Context, query: String = ""): List<AutoEqModel> {
        val q = query.trim().lowercase(Locale.ROOT)
        return readIndex(context).filter {
            q.isEmpty() ||
            it.name.lowercase(Locale.ROOT).contains(q) ||
            it.source.lowercase(Locale.ROOT).contains(q)
        }
    }

    suspend fun load(context: Context, model: AutoEqModel): AutoEqProfile = withContext(Dispatchers.IO) {
        require(model.path.isNotBlank()) { "Perfil AutoEQ no disponible" }
        val text = context.assets.open("autoeq/profiles/" + model.path)
            .bufferedReader().use { it.readText() }
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
                filters += AutoEqFilter(
                    frequency = it.groupValues[2].toFloat().coerceIn(20f, 20000f),
                    gain = it.groupValues[3].toFloat().coerceIn(-30f, 30f),
                    q = it.groupValues[4].toFloat().coerceIn(.1f, 40f),
                    type = type
                )
            }
        }
        return AutoEqProfile(preamp.coerceIn(-30f, 12f), filters)
    }
}
