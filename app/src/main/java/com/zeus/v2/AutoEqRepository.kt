package com.zeus.v2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AutoEqProfile(val preamp: Float, val filters: List<AutoEqFilter>)
data class AutoEqFilter(val frequency: Float, val gain: Float, val q: Float, val type: EqBand.FilterType = EqBand.FilterType.PEAK)
data class AutoEqModel(val name: String, val source: String = "oratory1990")

object AutoEqRepository {
    private const val BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/oratory1990/harman_over-ear_2018/"
    private val modelNames = listOf(
        "Sennheiser HD 600","Sennheiser HD 650","Sennheiser HD 560S","Sennheiser HD 800 S",
        "Sennheiser HD 800","Sennheiser HD 660S2","Sennheiser HD 490 Pro (mixing earpads)",
        "Beyerdynamic DT 770 Pro","Beyerdynamic DT 770 Pro (250 Ohm)","Beyerdynamic DT 880",
        "Beyerdynamic DT 990 Pro","Beyerdynamic DT 900 Pro X","Beyerdynamic DT 700 Pro X",
        "Beyerdynamic DT 1990","AKG K371","AKG K712","AKG K240 MKII",
        "HIFIMAN Sundara","HIFIMAN Edition XS","HIFIMAN Arya","HIFIMAN HE400se",
        "HIFIMAN HE4XX","HIFIMAN HE1000 Stealth","Sony MDR-7506","Sony MDR-MV1",
        "Audio-Technica ATH-M50x","Audio-Technica ATH-M70x","Audio-Technica ATH-R70x",
        "Focal Bathys","Focal Utopia","Focal Elear","Audeze LCD-X","Audeze LCD-XC",
        "Bose QuietComfort 45","Bose Noise Cancelling Headphones 700","RØDE NTH-100",
        "Shure SRH440","Shure SRH840","Philips Fidelio X2HR","Meze 109 Pro"
    ).sorted()

    fun models(query: String = ""): List<AutoEqModel> {
        val q = query.trim().lowercase(Locale.ROOT)
        return modelNames.filter { q.isEmpty() || it.lowercase(Locale.ROOT).contains(q) }.map(::AutoEqModel)
    }

    suspend fun load(model: AutoEqModel): AutoEqProfile = withContext(Dispatchers.IO) {
        val encoded = model.name.replace(" ", "%20")
        val url = URL(BASE + encoded + "/" + encoded + "%20ParametricEQ.txt")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) error("AutoEQ: HTTP " + conn.responseCode)
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun parse(text: String): AutoEqProfile {
        var preamp = 0f
        val filters = mutableListOf<AutoEqFilter>()
        val pre = Regex("""(?i)^\s*Preamp\s*:\s*([-+]?\d+(?:\.\d+)?)\s*dB""")
        val filter = Regex("""(?i)^\s*Filter\s*\d+\s*:\s*ON\s+(PK|LS|HS|LP|HP)\s+Fc\s+([-+]?\d+(?:\.\d+)?)\s*Hz\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB\s+Q\s+([-+]?\d+(?:\.\d+)?)""")
        text.lineSequence().forEach { line ->
            pre.find(line)?.let { preamp = it.groupValues[1].toFloatOrNull() ?: preamp }
            filter.find(line)?.let {
                val type = when (it.groupValues[1].uppercase(Locale.ROOT)) {
                    "LS" -> EqBand.FilterType.LOW_SHELF
                    "HS" -> EqBand.FilterType.HIGH_SHELF
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
        AutoEqProfile(preamp.coerceIn(-30f, 12f), filters)
    }
}
