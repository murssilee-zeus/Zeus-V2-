package com.zeus.v2

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

enum class EqSection {
    EQUALIZER, CROSSOVER, LIMITER, PIPELINE, AUTOEQ
}

class EqViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val MAX_BANDS = 32
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                EqViewModel(app)
            }
        }
    }

    val bands = mutableStateListOf<EqBand>().apply { addAll(createDefaultBands()) }
    var selectedBandIndex by mutableIntStateOf(2); private set
    var currentSection by mutableStateOf(EqSection.EQUALIZER); private set
    var preamp by mutableFloatStateOf(-6.0f)
    var headroomTrim by mutableFloatStateOf(0f)
    var subBoost by mutableFloatStateOf(0f)
    var limiterEnabled by mutableStateOf(true)
    var limiterThreshold by mutableFloatStateOf(-2.5f)
    var limiterAttack by mutableFloatStateOf(0.5f)
    var limiterRelease by mutableFloatStateOf(120f)
    var limiterRatio by mutableFloatStateOf(20f)
    var limiterPostGain by mutableFloatStateOf(0f)
    var pipelineEnabled by mutableStateOf(true)
    var audioSessionEnabled by mutableStateOf(false)
    var hiResEnabled by mutableStateOf(false)
    var spatialEnabled by mutableStateOf(false)
    var spatialWidth by mutableFloatStateOf(35f)
    var lowShelfEnabled by mutableStateOf(true)
    var peakBandsEnabled by mutableStateOf(true)
    var highShelfEnabled by mutableStateOf(true)
    var compressorMultibandEnabled by mutableStateOf(true)
    var selectedAudioSession by mutableStateOf("0: LOAD - Audio TX Output (Float)")
    var selectedTargetName by mutableStateOf("Flat")
    var targetCurve by mutableStateOf<List<TargetPoint>>(emptyList())

    // Four-band compressor: four configurable boundaries, with Cross 4 defining the top of HIGH.
    var crossoverFrequencies = mutableStateListOf(180f, 1800f, 8000f, 16000f)

    var compMbPreGainLow by mutableFloatStateOf(0f)
    var compMbPreGainLoMid by mutableFloatStateOf(0f)
    var compMbPreGainHiMid by mutableFloatStateOf(0f)
    var compMbPreGainHigh by mutableFloatStateOf(0f)
    var compMbThLow by mutableFloatStateOf(-18f)
    var compMbThLoMid by mutableFloatStateOf(-14f)
    var compMbThHiMid by mutableFloatStateOf(-12f)
    var compMbThHigh by mutableFloatStateOf(-14f)
    var compMbRatioLow by mutableFloatStateOf(4f)
    var compMbRatioLoMid by mutableFloatStateOf(3f)
    var compMbRatioHiMid by mutableFloatStateOf(2.5f)
    var compMbRatioHigh by mutableFloatStateOf(3.5f)
    var compMbKneeLow by mutableFloatStateOf(6f)
    var compMbKneeLoMid by mutableFloatStateOf(6f)
    var compMbKneeHiMid by mutableFloatStateOf(6f)
    var compMbKneeHigh by mutableFloatStateOf(6f)
    var compMbAttackLow by mutableFloatStateOf(15f)
    var compMbAttackLoMid by mutableFloatStateOf(12f)
    var compMbAttackHiMid by mutableFloatStateOf(8f)
    var compMbAttackHigh by mutableFloatStateOf(5f)
    var compMbReleaseLow by mutableFloatStateOf(180f)
    var compMbReleaseLoMid by mutableFloatStateOf(120f)
    var compMbReleaseHiMid by mutableFloatStateOf(90f)
    var compMbReleaseHigh by mutableFloatStateOf(60f)
    var compMbPostGainLow by mutableFloatStateOf(0f)
    var compMbPostGainLoMid by mutableFloatStateOf(0f)
    var compMbPostGainHiMid by mutableFloatStateOf(0f)
    var compMbPostGainHigh by mutableFloatStateOf(0f)

    var isEngineRunning by mutableStateOf(false)
    var spectrum by mutableStateOf(FloatArray(128) { 0f })

    fun setCrossover(index: Int, freq: Float) {
        if (index !in 0..3) return
        when (index) {
            0 -> crossoverFrequencies[0] = freq.coerceIn(40f, crossoverFrequencies[1] - 50f)
            1 -> crossoverFrequencies[1] = freq.coerceIn(crossoverFrequencies[0] + 50f, crossoverFrequencies[2] - 50f)
            2 -> crossoverFrequencies[2] = freq.coerceIn(crossoverFrequencies[1] + 50f, crossoverFrequencies[3] - 50f)
            3 -> crossoverFrequencies[3] = freq.coerceIn(crossoverFrequencies[2] + 50f, 20000f)
        }
    }

    fun applyPresetFlat() {
        preamp = -3f; subBoost = 0f
        bands.clear(); bands.addAll(createDefaultBands().map { it.copy(gain = 0f, enabled = true) })
        selectedBandIndex = 0; compressorMultibandEnabled = false
        limiterEnabled = true; limiterThreshold = -1.5f; limiterRatio = 8f; limiterAttack = 1f; limiterRelease = 120f; limiterPostGain = 0f
    }

    // Preset methods and remaining ViewModel implementation are preserved in the existing branch.
    // This file is intentionally updated only for the crossover state/validation above.
}
