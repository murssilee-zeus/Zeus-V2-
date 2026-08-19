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
    EQUALIZER, CROSSOVER, LIMITER, PIPELINE
}

class EqViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_BANDS = 18

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                EqViewModel(app)
            }
        }
    }

    // ====== BANDAS (18, con subgrave 20-40 Hz potenciado) ======
    val bands = mutableStateListOf<EqBand>().apply { addAll(createDefaultBands()) }

    var selectedBandIndex by mutableIntStateOf(2)
        private set

    var currentSection by mutableStateOf(EqSection.EQUALIZER)
        private set

    // ====== PREAMP + SUB BOOST ======
    var preamp by mutableFloatStateOf(-6.0f)
    var subBoost by mutableFloatStateOf(0f)

    // ====== LIMITER (protección) ======
    var limiterEnabled by mutableStateOf(true)
    var limiterThreshold by mutableFloatStateOf(-2.5f)
    var limiterAttack by mutableFloatStateOf(0.5f)
    var limiterRelease by mutableFloatStateOf(120f)
    var limiterRatio by mutableFloatStateOf(20f)
    var limiterPostGain by mutableFloatStateOf(0f)

    // ====== PIPELINE ======
    var pipelineEnabled by mutableStateOf(true)
    var audioSessionEnabled by mutableStateOf(false)
    var lowShelfEnabled by mutableStateOf(true)
    var peakBandsEnabled by mutableStateOf(true)
    var highShelfEnabled by mutableStateOf(true)
    var compressorMultibandEnabled by mutableStateOf(true)
    var selectedAudioSession by mutableStateOf("0: LOAD - Audio TX Output (Float)")

    // ====== COMPRESOR MULTIBANDA: 3 CORTES / 4 BANDAS + KNEE(dB) ======
    var crossoverFrequencies = mutableStateListOf(180f, 1800f, 8000f)

    var compMbThLow by mutableFloatStateOf(-18f)
    var compMbThLoMid by mutableFloatStateOf(-14f)
    var compMbThHiMid by mutableFloatStateOf(-12f)
    var compMbThHigh by mutableFloatStateOf(-14f)
    var compMbRatio by mutableFloatStateOf(5f)
    var compMbKnee by mutableFloatStateOf(6f)
    var compMbAttack by mutableFloatStateOf(4f)
    var compMbRelease by mutableFloatStateOf(90f)
    var compMbPostGain by mutableFloatStateOf(0f)

    var isEngineRunning by mutableStateOf(false)
    var spectrum by mutableStateOf(FloatArray(128) { 0f })

    // ====== Persistencia ======
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
        cross3 = crossoverFrequencies.getOrElse(2) { 8000f },
        compThLow = compMbThLow,
        compThLoMid = compMbThLoMid,
        compThHiMid = compMbThHiMid,
        compThHigh = compMbThHigh,
        compRatio = compMbRatio,
        compKnee = compMbKnee,
        compAttack = compMbAttack,
        compRelease = compMbRelease,
        compPostGain = compMbPostGain,
        pipelineEnabled = pipelineEnabled,
        lowShelfEnabled = lowShelfEnabled,
        peakEnabled = peakBandsEnabled,
        highShelfEnabled = highShelfEnabled,
        audioSessionEnabled = audioSessionEnabled,
        selectedAudioSession = selectedAudioSession
    )

    fun loadFrom(s: EqSettings) {
        preamp = s.preGain
        subBoost = s.subBoost
        bands.clear()
        bands.addAll(s.bands)
        limiterEnabled = s.limiterEnabled
        limiterThreshold = s.limiterThreshold
        limiterAttack = s.limiterAttack
        limiterRelease = s.limiterRelease
        limiterRatio = s.limiterRatio
        limiterPostGain = s.limiterPostGain
        compressorMultibandEnabled = s.compEnabled
        crossoverFrequencies[0] = s.cross1
        crossoverFrequencies[1] = s.cross2
        crossoverFrequencies[2] = s.cross3
        compMbThLow = s.compThLow
        compMbThLoMid = s.compThLoMid
        compMbThHiMid = s.compThHiMid
        compMbThHigh = s.compThHigh
        compMbRatio = s.compRatio
        compMbKnee = s.compKnee
        compMbAttack = s.compAttack
        compMbRelease = s.compRelease
        compMbPostGain = s.compPostGain
        pipelineEnabled = s.pipelineEnabled
        lowShelfEnabled = s.lowShelfEnabled
        peakBandsEnabled = s.peakEnabled
        highShelfEnabled = s.highShelfEnabled
        audioSessionEnabled = s.audioSessionEnabled
        selectedAudioSession = s.selectedAudioSession
    }

    fun saveSettings() {
        EqPrefs.save(getApplication(), toSettings())
    }

    fun loadSavedIfAny() {
        EqPrefs.load(getApplication())?.let { loadFrom(it) }
    }

    fun selectBand(index: Int) {
        if (index in bands.indices) selectedBandIndex = index
    }

    fun updateSelectedBand(
        frequency: Float? = null,
        gain: Float? = null,
        q: Float? = null,
        enabled: Boolean? = null,
        filterType: EqBand.FilterType? = null
    ) {
        val idx = selectedBandIndex
        if (idx !in bands.indices) return
        val current = bands[idx]
        bands[idx] = current.copy(
            frequency = frequency?.coerceIn(1f, 30000f) ?: current.frequency,
            gain = gain?.coerceIn(-30f, 30f) ?: current.gain,
            q = q?.coerceIn(0.1f, 40f) ?: current.q,
            enabled = enabled ?: current.enabled,
            filterType = filterType ?: current.filterType
        )
    }

    fun addBand() {
        if (bands.size >= MAX_BANDS) return
        val newId = (bands.maxOfOrNull { it.id } ?: -1) + 1
        val newFreq = when {
            bands.isEmpty() -> 1000f
            else -> (bands.last().frequency * 1.8f).coerceIn(1f, 30000f)
        }
        bands.add(createNewBand(newId, newFreq))
        selectedBandIndex = bands.lastIndex
    }

    fun removeSelectedBand() {
        if (bands.size <= 1) return
        val idx = selectedBandIndex
        if (idx !in bands.indices) return
        bands.removeAt(idx)
        selectedBandIndex = idx.coerceIn(0, bands.lastIndex)
    }

    fun nextSection() {
        currentSection = when (currentSection) {
            EqSection.EQUALIZER -> EqSection.CROSSOVER
            EqSection.CROSSOVER -> EqSection.LIMITER
            EqSection.LIMITER -> EqSection.EQUALIZER
            EqSection.PIPELINE -> EqSection.EQUALIZER
        }
    }

    fun previousSection() {
        currentSection = when (currentSection) {
            EqSection.EQUALIZER -> EqSection.LIMITER
            EqSection.CROSSOVER -> EqSection.EQUALIZER
            EqSection.LIMITER -> EqSection.CROSSOVER
            EqSection.PIPELINE -> EqSection.LIMITER
        }
    }

    fun sectionTitle(): String = when (currentSection) {
        EqSection.EQUALIZER -> "Equalizer"
        EqSection.CROSSOVER -> "Compresor Multibanda"
        EqSection.LIMITER -> "Limiter"
        EqSection.PIPELINE -> "Equalizer"
    }

    fun selectedBand(): EqBand? = bands.getOrNull(selectedBandIndex)
}
