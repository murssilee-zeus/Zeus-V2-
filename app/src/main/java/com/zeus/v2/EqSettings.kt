package com.zeus.v2

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class EqSettings(
    var preGain: Float = -6f,
    var subBoost: Float = 0f,
    var bands: List<EqBand> = createDefaultBands(),
    var limiterEnabled: Boolean = true,
    var limiterThreshold: Float = -2.5f,
    var limiterAttack: Float = 0.5f,
    var limiterRelease: Float = 120f,
    var limiterRatio: Float = 20f,
    var limiterPostGain: Float = 0f,
    var compEnabled: Boolean = true,
    var cross1: Float = 180f,
    var cross2: Float = 1800f,
    var cross3: Float = 8000f,
    // Threshold por banda
    var compThLow: Float = -18f,
    var compThLoMid: Float = -14f,
    var compThHiMid: Float = -12f,
    var compThHigh: Float = -14f,
    // Ratio por banda
    var compRatioLow: Float = 4f,
    var compRatioLoMid: Float = 3f,
    var compRatioHiMid: Float = 2.5f,
    var compRatioHigh: Float = 3.5f,
    // Knee por banda
    var compKneeLow: Float = 6f,
    var compKneeLoMid: Float = 6f,
    var compKneeHiMid: Float = 6f,
    var compKneeHigh: Float = 6f,
    // Attack por banda (ms)
    var compAttackLow: Float = 15f,
    var compAttackLoMid: Float = 12f,
    var compAttackHiMid: Float = 8f,
    var compAttackHigh: Float = 5f,
    // Release por banda (ms)
    var compReleaseLow: Float = 180f,
    var compReleaseLoMid: Float = 120f,
    var compReleaseHiMid: Float = 90f,
    var compReleaseHigh: Float = 60f,
    // Post Gain por banda
    var compPostGainLow: Float = 0f,
    var compPostGainLoMid: Float = 0f,
    var compPostGainHiMid: Float = 0f,
    var compPostGainHigh: Float = 0f,
    var pipelineEnabled: Boolean = true,
    var lowShelfEnabled: Boolean = true,
    var peakEnabled: Boolean = true,
    var highShelfEnabled: Boolean = true,
    var audioSessionEnabled: Boolean = false,
    var selectedAudioSession: String = "0: LOAD - Audio TX Output (Float)"
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("version", 2)
        o.put("preGain", preGain.toDouble())
        o.put("subBoost", subBoost.toDouble())
        val bArr = JSONArray()
        bands.forEach { b ->
            val jo = JSONObject()
            jo.put("f", b.frequency.toDouble())
            jo.put("g", b.gain.toDouble())
            jo.put("q", b.q.toDouble())
            jo.put("e", b.enabled)
            jo.put("t", b.filterType.ordinal)
            bArr.put(jo)
        }
        o.put("bands", bArr)
        o.put("limiterEnabled", limiterEnabled)
        o.put("limiterThreshold", limiterThreshold.toDouble())
        o.put("limiterAttack", limiterAttack.toDouble())
        o.put("limiterRelease", limiterRelease.toDouble())
        o.put("limiterRatio", limiterRatio.toDouble())
        o.put("limiterPostGain", limiterPostGain.toDouble())
        o.put("compEnabled", compEnabled)
        o.put("cross1", cross1.toDouble())
        o.put("cross2", cross2.toDouble())
        o.put("cross3", cross3.toDouble())
        o.put("compThLow", compThLow.toDouble())
        o.put("compThLoMid", compThLoMid.toDouble())
        o.put("compThHiMid", compThHiMid.toDouble())
        o.put("compThHigh", compThHigh.toDouble())
        o.put("compRatioLow", compRatioLow.toDouble())
        o.put("compRatioLoMid", compRatioLoMid.toDouble())
        o.put("compRatioHiMid", compRatioHiMid.toDouble())
        o.put("compRatioHigh", compRatioHigh.toDouble())
        o.put("compKneeLow", compKneeLow.toDouble())
        o.put("compKneeLoMid", compKneeLoMid.toDouble())
        o.put("compKneeHiMid", compKneeHiMid.toDouble())
        o.put("compKneeHigh", compKneeHigh.toDouble())
        o.put("compAttackLow", compAttackLow.toDouble())
        o.put("compAttackLoMid", compAttackLoMid.toDouble())
        o.put("compAttackHiMid", compAttackHiMid.toDouble())
        o.put("compAttackHigh", compAttackHigh.toDouble())
        o.put("compReleaseLow", compReleaseLow.toDouble())
        o.put("compReleaseLoMid", compReleaseLoMid.toDouble())
        o.put("compReleaseHiMid", compReleaseHiMid.toDouble())
        o.put("compReleaseHigh", compReleaseHigh.toDouble())
        o.put("compPostGainLow", compPostGainLow.toDouble())
        o.put("compPostGainLoMid", compPostGainLoMid.toDouble())
        o.put("compPostGainHiMid", compPostGainHiMid.toDouble())
        o.put("compPostGainHigh", compPostGainHigh.toDouble())
        o.put("pipelineEnabled", pipelineEnabled)
        o.put("lowShelfEnabled", lowShelfEnabled)
        o.put("peakEnabled", peakEnabled)
        o.put("highShelfEnabled", highShelfEnabled)
        o.put("audioSessionEnabled", audioSessionEnabled)
        o.put("selectedAudioSession", selectedAudioSession)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String): EqSettings {
            val o = JSONObject(json)
            val s = EqSettings()
            s.preGain = o.optDouble("preGain", -6.0).toFloat()
            s.subBoost = o.optDouble("subBoost", 0.0).toFloat()
            val bArr = o.optJSONArray("bands")
            if (bArr != null) {
                val list = mutableListOf<EqBand>()
                for (i in 0 until bArr.length()) {
                    val jo = bArr.getJSONObject(i)
                    list.add(
                        EqBand(
                            id = i,
                            frequency = jo.optDouble("f", 1000.0).toFloat(),
                            gain = jo.optDouble("g", 0.0).toFloat(),
                            q = jo.optDouble("q", 1.0).toFloat(),
                            enabled = jo.optBoolean("e", true),
                            filterType = EqBand.FilterType.values().getOrElse(jo.optInt("t", 2)) { EqBand.FilterType.PEAK },
                            color = bandColor(i)
                        )
                    )
                }
                s.bands = list
            }
            s.limiterEnabled = o.optBoolean("limiterEnabled", true)
            s.limiterThreshold = o.optDouble("limiterThreshold", -2.5).toFloat()
            s.limiterAttack = o.optDouble("limiterAttack", 0.5).toFloat()
            s.limiterRelease = o.optDouble("limiterRelease", 120.0).toFloat()
            s.limiterRatio = o.optDouble("limiterRatio", 20.0).toFloat()
            s.limiterPostGain = o.optDouble("limiterPostGain", 0.0).toFloat()
            s.compEnabled = o.optBoolean("compEnabled", true)
            s.cross1 = o.optDouble("cross1", 180.0).toFloat()
            s.cross2 = o.optDouble("cross2", 1800.0).toFloat()
            s.cross3 = o.optDouble("cross3", 8000.0).toFloat()
            s.compThLow = o.optDouble("compThLow", -18.0).toFloat()
            s.compThLoMid = o.optDouble("compThLoMid", -14.0).toFloat()
            s.compThHiMid = o.optDouble("compThHiMid", -12.0).toFloat()
            s.compThHigh = o.optDouble("compThHigh", -14.0).toFloat()
            s.compRatioLow = o.optDouble("compRatioLow", 4.0).toFloat()
            s.compRatioLoMid = o.optDouble("compRatioLoMid", 3.0).toFloat()
            s.compRatioHiMid = o.optDouble("compRatioHiMid", 2.5).toFloat()
            s.compRatioHigh = o.optDouble("compRatioHigh", 3.5).toFloat()
            s.compKneeLow = o.optDouble("compKneeLow", 6.0).toFloat()
            s.compKneeLoMid = o.optDouble("compKneeLoMid", 6.0).toFloat()
            s.compKneeHiMid = o.optDouble("compKneeHiMid", 6.0).toFloat()
            s.compKneeHigh = o.optDouble("compKneeHigh", 6.0).toFloat()
            s.compAttackLow = o.optDouble("compAttackLow", 15.0).toFloat()
            s.compAttackLoMid = o.optDouble("compAttackLoMid", 12.0).toFloat()
            s.compAttackHiMid = o.optDouble("compAttackHiMid", 8.0).toFloat()
            s.compAttackHigh = o.optDouble("compAttackHigh", 5.0).toFloat()
            s.compReleaseLow = o.optDouble("compReleaseLow", 180.0).toFloat()
            s.compReleaseLoMid = o.optDouble("compReleaseLoMid", 120.0).toFloat()
            s.compReleaseHiMid = o.optDouble("compReleaseHiMid", 90.0).toFloat()
            s.compReleaseHigh = o.optDouble("compReleaseHigh", 60.0).toFloat()
            s.compPostGainLow = o.optDouble("compPostGainLow", 0.0).toFloat()
            s.compPostGainLoMid = o.optDouble("compPostGainLoMid", 0.0).toFloat()
            s.compPostGainHiMid = o.optDouble("compPostGainHiMid", 0.0).toFloat()
            s.compPostGainHigh = o.optDouble("compPostGainHigh", 0.0).toFloat()
            s.pipelineEnabled = o.optBoolean("pipelineEnabled", true)
            s.lowShelfEnabled = o.optBoolean("lowShelfEnabled", true)
            s.peakEnabled = o.optBoolean("peakEnabled", true)
            s.highShelfEnabled = o.optBoolean("highShelfEnabled", true)
            s.audioSessionEnabled = o.optBoolean("audioSessionEnabled", false)
            s.selectedAudioSession = o.optString("selectedAudioSession", "0: LOAD - Audio TX Output (Float)")
            return s
        }

        private val PALETTE = listOf(
            Color(0xFFFF6B6B), Color(0xFFFF9F43), Color(0xFFFFEAA7), Color(0xFF55EFC4),
            Color(0xFF74B9FF), Color(0xFFA29BFE), Color(0xFFFD79A8), Color(0xFF00CEC9),
            Color(0xFFE17055), Color(0xFF6C5CE7), Color(0xFFFF7675), Color(0xFFFDCB6E),
            Color(0xFF00B894), Color(0xFF0984E3), Color(0xFF6C5CE7), Color(0xFFE84393),
            Color(0xFF2D3436), Color(0xFFD63031)
        )

        fun bandColor(index: Int): Color = PALETTE[index % PALETTE.size]
    }
}
