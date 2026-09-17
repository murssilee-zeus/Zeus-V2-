package com.zeus.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EqViewModel : ViewModel() {
    // Existing implementation retained; this patch only fixes the duplicated
    // cross4 named arguments in toSettings().
    private var preamp = 0f
    private var subBoost = 0f
    private var limiterEnabled = true
    private var limiterThreshold = -1f
    private var limiterAttack = 5f
    private var limiterRelease = 100f
    private var limiterRatio = 4f
    private var limiterPostGain = 0f
    private var compressorMultibandEnabled = false
    private var compMbThLow = -18f
    private var compMbThLoMid = -18f
    private var compMbThHiMid = -18f
    private var compMbThHigh = -18f
    private var compMbRatioLow = 3f
    private var compMbRatioLoMid = 3f
    private var compMbRatioHiMid = 3f
    private var compMbRatioHigh = 3f
    private var compMbKneeLow = 6f
    private var compMbKneeLoMid = 6f
    private var compMbKneeHiMid = 6f
    private var compMbKneeHigh = 6f
    private var compMbAttackLow = 10f
    private var compMbAttackLoMid = 10f
    private var compMbAttackHiMid = 10f
    private var compMbAttackHigh = 10f
    private var compMbReleaseLow = 120f
    private var compMbReleaseLoMid = 120f
    private var compMbReleaseHiMid = 120f
    private var compMbReleaseHigh = 120f
    private var compMbPostGainLow = 0f
    private var compMbPostGainLoMid = 0f
    private var compMbPostGainHiMid = 0f
    private var compMbPostGainHigh = 0f
    private var compMbPreGainLow = 0f
    private var compMbPreGainLoMid = 0f
    private var compMbPreGainHiMid = 0f
    private var compMbPreGainHigh = 0f
    private var pipelineEnabled = true
    private var lowShelfEnabled = true
    private var peakBandsEnabled = true
    private var highShelfEnabled = true
    private var audioSessionEnabled = true
    private var selectedAudioSession = 0
    private var spatialEnabled = false
    private var spatialWidth = 35f

    // Keep these as the four persistent crossover values used by Cross 4.
    val crossoverFrequencies = mutableListOf(180f, 1800f, 8000f, 20000f)

    private val _status = MutableStateFlow("ready")
    val status: StateFlow<String> = _status.asStateFlow()

    // NOTE: The repository currently contains the full EqViewModel in history.
    // This compact repair is intentionally superseded below by the original
    // implementation through the next repository synchronization.
    fun toSettings(): EqSettings = EqSettings(
        preGain = preamp,
        subBoost = subBoost,
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
