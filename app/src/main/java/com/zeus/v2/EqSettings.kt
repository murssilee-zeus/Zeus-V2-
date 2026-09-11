package com.zeus.v2

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Configuración completa del ecualizador / compresor / limitador. */
data class EqSettings(
    var preGain: Float = -6f,
    var subBoost: Float = 0f,
    var bassMono: Boolean = true,
    var bassAmount: Float = 0f,
    var bassPunch: Float = 0f,
    var bassHarmonics: Float = 0f,
    var bands: List<EqBand> = defaultBands(),
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
    var compThLow: Float = -18f,
    var compThLoMid: Float = -14f,
    var compThHiMid: Float = -12f,
    var compThHigh: Float = -14f,
    var compRatioLow: Float = 4f,
    var compRatioLoMid: Float = 3f,
    var compRatioHiMid: Float = 2.5f,
    var compRatioHigh: Float = 3.5f,
    var compKneeLow: Float = 6f,
    var compKneeLoMid: Float = 6f,
    var compKneeHiMid: Float = 6f,
    var compKneeHigh: Float = 6f,
    var compAttackLow: Float = 15f,
    var compAttackLoMid: Float = 12f,
    var compAttackHiMid: Float = 8f,
    var compAttackHigh: Float = 5f,
    var compReleaseLow: Float = 180f,
    var compReleaseLoMid: Float = 120f,
    var compReleaseHiMid: Float = 90f,
    var compReleaseHigh: Float = 60f,
    var compPostGainLow: Float = 0f,
    var compPostGainLoMid: Float = 0f,
    var compPostGainHiMid: Float = 0f,
    var compPostGainHigh: Float = 0f,
    var compPreGainLow: Float = 0f,
    var compPreGainLoMid: Float = 0f,
    var compPreGainHiMid: Float = 0f,
    var compPreGainHigh: Float = 0f,
    var pipelineEnabled: Boolean = true,
    var lowShelfEnabled: Boolean = true,
    var peakEnabled: Boolean = true,
    var highShelfEnabled: Boolean = true,
    var audioSessionEnabled: Boolean = false,
    var selectedAudioSession: String = "0: LOAD - Audio TX Output (Float)",
    var hiResEnabled: Boolean = false
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("version", 4)
        o.put("preGain", preGain.toDouble()); o.put("subBoost", subBoost.toDouble())
        o.put("bassMono", bassMono); o.put("bassAmount", bassAmount.toDouble()); o.put("bassPunch", bassPunch.toDouble()); o.put("bassHarmonics", bassHarmonics.toDouble())
        val bArr = JSONArray()
        bands.forEach { b ->
            val jo = JSONObject(); jo.put("id", b.id); jo.put("f", b.frequency.toDouble()); jo.put("g", b.gain.toDouble()); jo.put("q", b.q.toDouble()); jo.put("e", b.enabled); jo.put("t", b.filterType.ordinal); bArr.put(jo)
        }
        o.put("bands", bArr)
        o.put("limiterEnabled", limiterEnabled); o.put("limiterThreshold", limiterThreshold.toDouble()); o.put("limiterAttack", limiterAttack.toDouble()); o.put("limiterRelease", limiterRelease.toDouble()); o.put("limiterRatio", limiterRatio.toDouble()); o.put("limiterPostGain", limiterPostGain.toDouble())
        o.put("compEnabled", compEnabled); o.put("cross1", cross1.toDouble()); o.put("cross2", cross2.toDouble()); o.put("cross3", cross3.toDouble())
        o.put("compThLow", compThLow.toDouble()); o.put("compThLoMid", compThLoMid.toDouble()); o.put("compThHiMid", compThHiMid.toDouble()); o.put("compThHigh", compThHigh.toDouble())
        o.put("compRatioLow", compRatioLow.toDouble()); o.put("compRatioLoMid", compRatioLoMid.toDouble()); o.put("compRatioHiMid", compRatioHiMid.toDouble()); o.put("compRatioHigh", compRatioHigh.toDouble())
        o.put("compKneeLow", compKneeLow.toDouble()); o.put("compKneeLoMid", compKneeLoMid.toDouble()); o.put("compKneeHiMid", compKneeHiMid.toDouble()); o.put("compKneeHigh", compKneeHigh.toDouble())
        o.put("compAttackLow", compAttackLow.toDouble()); o.put("compAttackLoMid", compAttackLoMid.toDouble()); o.put("compAttackHiMid", compAttackHiMid.toDouble()); o.put("compAttackHigh", compAttackHigh.toDouble())
        o.put("compReleaseLow", compReleaseLow.toDouble()); o.put("compReleaseLoMid", compReleaseLoMid.toDouble()); o.put("compReleaseHiMid", compReleaseHiMid.toDouble()); o.put("compReleaseHigh", compReleaseHigh.toDouble())
        o.put("compPostGainLow", compPostGainLow.toDouble()); o.put("compPostGainLoMid", compPostGainLoMid.toDouble()); o.put("compPostGainHiMid", compPostGainHiMid.toDouble()); o.put("compPostGainHigh", compPostGainHigh.toDouble())
        o.put("compPreGainLow", compPreGainLow.toDouble()); o.put("compPreGainLoMid", compPreGainLoMid.toDouble()); o.put("compPreGainHiMid", compPreGainHiMid.toDouble()); o.put("compPreGainHigh", compPreGainHigh.toDouble())
        o.put("pipelineEnabled", pipelineEnabled); o.put("lowShelfEnabled", lowShelfEnabled); o.put("peakEnabled", peakEnabled); o.put("highShelfEnabled", highShelfEnabled)
        o.put("audioSessionEnabled", audioSessionEnabled); o.put("selectedAudioSession", selectedAudioSession); o.put("hiResEnabled", hiResEnabled)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String): EqSettings {
            val s = EqSettings()
            try {
                val o = JSONObject(json)
                s.preGain=o.optDouble("preGain",-6.0).toFloat(); s.subBoost=o.optDouble("subBoost",0.0).toFloat(); s.bassMono=o.optBoolean("bassMono",true); s.bassAmount=o.optDouble("bassAmount",0.0).toFloat(); s.bassPunch=o.optDouble("bassPunch",0.0).toFloat(); s.bassHarmonics=o.optDouble("bassHarmonics",0.0).toFloat()
                val bArr=o.optJSONArray("bands")
                if(bArr!=null&&bArr.length()>0){val list=mutableListOf<EqBand>();for(i in 0 until bArr.length()){val jo=bArr.getJSONObject(i);val typeOrd=jo.optInt("t",EqBand.FilterType.PEAK.ordinal);val type=EqBand.FilterType.values().getOrElse(typeOrd){EqBand.FilterType.PEAK};val id=jo.optInt("id",i);list.add(EqBand(id,jo.optDouble("f",1000.0).toFloat(),jo.optDouble("g",0.0).toFloat(),jo.optDouble("q",1.0).toFloat(),jo.optBoolean("e",true),type,bandColor(id)))};s.bands=list}
                s.limiterEnabled=o.optBoolean("limiterEnabled",true);s.limiterThreshold=o.optDouble("limiterThreshold",-2.5).toFloat();s.limiterAttack=o.optDouble("limiterAttack",0.5).toFloat().coerceIn(.01f,100f);s.limiterRelease=o.optDouble("limiterRelease",120.0).toFloat();s.limiterRatio=o.optDouble("limiterRatio",20.0).toFloat();s.limiterPostGain=o.optDouble("limiterPostGain",0.0).toFloat()
                s.compEnabled=o.optBoolean("compEnabled",true);s.cross1=o.optDouble("cross1",180.0).toFloat();s.cross2=o.optDouble("cross2",1800.0).toFloat();s.cross3=o.optDouble("cross3",8000.0).toFloat()
                val legacyRatio=o.optDouble("compRatio",4.0);val legacyKnee=o.optDouble("compKnee",6.0);val legacyAttack=o.optDouble("compAttack",10.0);val legacyRelease=o.optDouble("compRelease",100.0);val legacyPost=o.optDouble("compPostGain",0.0)
                s.compThLow=o.optDouble("compThLow",-18.0).toFloat();s.compThLoMid=o.optDouble("compThLoMid",-14.0).toFloat();s.compThHiMid=o.optDouble("compThHiMid",-12.0).toFloat();s.compThHigh=o.optDouble("compThHigh",-14.0).toFloat()
                s.compRatioLow=o.optDouble("compRatioLow",legacyRatio).toFloat();s.compRatioLoMid=o.optDouble("compRatioLoMid",legacyRatio).toFloat();s.compRatioHiMid=o.optDouble("compRatioHiMid",legacyRatio).toFloat();s.compRatioHigh=o.optDouble("compRatioHigh",legacyRatio).toFloat()
                s.compKneeLow=o.optDouble("compKneeLow",legacyKnee).toFloat();s.compKneeLoMid=o.optDouble("compKneeLoMid",legacyKnee).toFloat();s.compKneeHiMid=o.optDouble("compKneeHiMid",legacyKnee).toFloat();s.compKneeHigh=o.optDouble("compKneeHigh",legacyKnee).toFloat()
                s.compAttackLow=o.optDouble("compAttackLow",legacyAttack).toFloat();s.compAttackLoMid=o.optDouble("compAttackLoMid",legacyAttack).toFloat();s.compAttackHiMid=o.optDouble("compAttackHiMid",legacyAttack).toFloat();s.compAttackHigh=o.optDouble("compAttackHigh",legacyAttack).toFloat()
                s.compReleaseLow=o.optDouble("compReleaseLow",legacyRelease).toFloat();s.compReleaseLoMid=o.optDouble("compReleaseLoMid",legacyRelease).toFloat();s.compReleaseHiMid=o.optDouble("compReleaseHiMid",legacyRelease).toFloat();s.compReleaseHigh=o.optDouble("compReleaseHigh",legacyRelease).toFloat()
                s.compPostGainLow=o.optDouble("compPostGainLow",legacyPost).toFloat();s.compPostGainLoMid=o.optDouble("compPostGainLoMid",legacyPost).toFloat();s.compPostGainHiMid=o.optDouble("compPostGainHiMid",legacyPost).toFloat();s.compPostGainHigh=o.optDouble("compPostGainHigh",legacyPost).toFloat()
                s.compPreGainLow=o.optDouble("compPreGainLow",0.0).toFloat();s.compPreGainLoMid=o.optDouble("compPreGainLoMid",0.0).toFloat();s.compPreGainHiMid=o.optDouble("compPreGainHiMid",0.0).toFloat();s.compPreGainHigh=o.optDouble("compPreGainHigh",0.0).toFloat()
                s.pipelineEnabled=o.optBoolean("pipelineEnabled",true);s.lowShelfEnabled=o.optBoolean("lowShelfEnabled",true);s.peakEnabled=o.optBoolean("peakEnabled",true);s.highShelfEnabled=o.optBoolean("highShelfEnabled",true);s.audioSessionEnabled=o.optBoolean("audioSessionEnabled",false);s.selectedAudioSession=o.optString("selectedAudioSession","0: LOAD - Audio TX Output (Float)");s.hiResEnabled=o.optBoolean("hiResEnabled",false)
            }catch(_:Exception){}
            return s
        }
        private fun defaultBands():List<EqBand>{val freqs=listOf(31.5f,63f,125f,250f,500f,1000f,2000f,4000f,8000f,16000f);return freqs.mapIndexed{i,f->EqBand(i,f,0f,1.2f,true,when(i){0->EqBand.FilterType.LOW_SHELF;freqs.lastIndex->EqBand.FilterType.HIGH_SHELF;else->EqBand.FilterType.PEAK},bandColor(i))}}
        private val PALETTE=listOf(Color(0xFFFF6B6B),Color(0xFFFF9F43),Color(0xFFFFEAA7),Color(0xFF55EFC4),Color(0xFF74B9FF),Color(0xFFA29BFE),Color(0xFFFD79A8),Color(0xFF00CEC9),Color(0xFFE17055),Color(0xFF6C5CE7),Color(0xFFFF7675),Color(0xFFFDCB6E),Color(0xFF00B894),Color(0xFF0984E3),Color(0xFF6C5CE7),Color(0xFFE84393),Color(0xFF2D3436),Color(0xFFD63031))
        fun bandColor(index:Int):Color=PALETTE[index%PALETTE.size]
    }
}
