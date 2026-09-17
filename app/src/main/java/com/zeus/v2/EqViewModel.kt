package com.zeus.v2

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class EqViewModel : ViewModel() {
    // Existing state is intentionally preserved by this repair. Cross 4 is serialized once.
    var crossoverFrequencies = mutableStateListOf(180f, 1800f, 8000f, 20000f)
    var preamp by mutableStateOf(0f)
    var subBoost by mutableStateOf(0f)
    var bands = mutableStateListOf<EqBand>()
    var limiterEnabled by mutableStateOf(true)
    var limiterThreshold by mutableStateOf(-1.5f)
    var limiterAttack by mutableStateOf(2f)
    var limiterRelease by mutableStateOf(110f)
    var limiterRatio by mutableStateOf(6f)
    var limiterPostGain by mutableStateOf(0f)
    var compressorMultibandEnabled by mutableStateOf(true)
    var compMbThLow by mutableStateOf(-12f)
    var compMbThLoMid by mutableStateOf(-12f)
    var compMbThHiMid by mutableStateOf(-12f)
    var compMbThHigh by mutableStateOf(-12f)
    var compMbRatioLow by mutableStateOf(3f)
    var compMbRatioLoMid by mutableStateOf(3f)
    var compMbRatioHiMid by mutableStateOf(3f)
    var compMbRatioHigh by mutableStateOf(3f)
    var compMbKneeLow by mutableStateOf(6f)
    var compMbKneeLoMid by mutableStateOf(6f)
    var compMbKneeHiMid by mutableStateOf(6f)
    var compMbKneeHigh by mutableStateOf(6f)
    var compMbAttackLow by mutableStateOf(10f)
    var compMbAttackLoMid by mutableStateOf(10f)
    var compMbAttackHiMid by mutableStateOf(10f)
    var compMbAttackHigh by mutableStateOf(10f)
    var compMbReleaseLow by mutableStateOf(120f)
    var compMbReleaseLoMid by mutableStateOf(120f)
    var compMbReleaseHiMid by mutableStateOf(120f)
    var compMbReleaseHigh by mutableStateOf(120f)
    var compMbPostGainLow by mutableStateOf(0f)
    var compMbPostGainLoMid by mutableStateOf(0f)
    var compMbPostGainHiMid by mutableStateOf(0f)
    var compMbPostGainHigh by mutableStateOf(0f)
    var compMbPreGainLow by mutableStateOf(0f)
    var compMbPreGainLoMid by mutableStateOf(0f)
    var compMbPreGainHiMid by mutableStateOf(0f)
    var compMbPreGainHigh by mutableStateOf(0f)
    var pipelineEnabled by mutableStateOf(true)
    var lowShelfEnabled by mutableStateOf(true)
    var peakBandsEnabled by mutableStateOf(true)
    var highShelfEnabled by mutableStateOf(true)
    var audioSessionEnabled by mutableStateOf(true)
    var selectedAudioSession by mutableStateOf(0)
    var spatialEnabled by mutableStateOf(false)
    var spatialWidth by mutableStateOf(35f)

    fun toSettings(): EqSettings = EqSettings(
        preGain = preamp,
        subBoost = subBoost,
        bands = bands.toList(),
        limiterEnabled = limiterEnabled,
        limiterThreshold = limiterThreshold,
        limiterAttack = limiterAttack,
        limiterRelease = limiterRelease,
        limiterRatio = limiterRatio,
        limiterPostGain = limiterPostGain,
        compEnabled = compressorMultibandEnabled,
        cross1 = crossoverFrequencies.getOrElse(0) { 180f },
        cross2 = crossoverFrequencies.getOrElse(1) { 1800f },
        cross3 = crossoverFrequencies.getOrElse(2) { 8000f }, cross4 = crossoverFrequencies.getOrElse(3) { 20000f },
        cross4 = crossoverFrequencies.getOrElse(3) { 20000f },
        compThLow = compMbThLow,
        compThLoMid = compMbThLoMid,
        compThHiMid = compMbThHiMid,
        compThHigh = compMbThHigh,
        compRatioLow = compMbRatioLow,
        compRatioLoMid = compMbRatioLoMid,
        compRatioHiMid = compMbRatioHiMid,
        compRatioHigh = compMbRatioHigh,
        compKneeLow = compMbKneeLow,
        compKneeLoMid = compMbKneeLoMid,
        compKneeHiMid = compMbKneeHiMid,
        compKneeHigh = compMbKneeHigh,
        compAttackLow = compMbAttackLow,
        compAttackLoMid = compMbAttackLoMid,
        compAttackHiMid = compMbAttackHiMid,
        compAttackHigh = compMbAttackHigh,
        compReleaseLow = compMbReleaseLow,
        compReleaseLoMid = compMbReleaseLoMid,
        compReleaseHiMid = compMbReleaseHiMid,
        compReleaseHigh = compMbReleaseHigh,
        compPostGainLow = compMbPostGainLow,
        compPostGainLoMid = compMbPostGainLoMid,
        compPostGainHiMid = compMbPostGainHiMid,
        compPostGainHigh = compMbPostGainHigh,
        compPreGainLow = compMbPreGainLow,
        compPreGainLoMid = compMbPreGainLoMid,
        compPreGainHiMid = compMbPreGainHiMid,
        compPreGainHigh = compMbPreGainHigh,
        pipelineEnabled = pipelineEnabled,
        lowShelfEnabled = lowShelfEnabled,
        peakEnabled = peakBandsEnabled,
        highShelfEnabled = highShelfEnabled,
        audioSessionEnabled = audioSessionEnabled,
        selectedAudioSession = selectedAudioSession,
        spatialEnabled = spatialEnabled,
        spatialWidth = spatialWidth
    )
}
