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
        const val MAX_BANDS = 32

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                EqViewModel(app)
            }
        }
    }

    val bands = mutableStateListOf<EqBand>().apply { addAll(createDefaultBands()) }

    var selectedBandIndex by mutableIntStateOf(2)
        private set

    var currentSection by mutableStateOf(EqSection.EQUALIZER)
        private set

    var preamp by mutableFloatStateOf(-6.0f)
    /** Manual safety trim used by the Punch/Headroom UI; 0 dB preserves the existing DSP behavior. */
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
    var lowShelfEnabled by mutableStateOf(true)
    var peakBandsEnabled by mutableStateOf(true)
    var highShelfEnabled by mutableStateOf(true)
    var compressorMultibandEnabled by mutableStateOf(true)
    var selectedAudioSession by mutableStateOf("0: LOAD - Audio TX Output (Float)")

    var crossoverFrequencies = mutableStateListOf(180f, 1800f, 8000f)

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
        if (index !in 0..2) return
        val f = freq.coerceIn(40f, 19500f)
        when (index) {
            0 -> crossoverFrequencies[0] = f.coerceAtMost(crossoverFrequencies[1] - 50f)
            1 -> crossoverFrequencies[1] = f.coerceIn(
                crossoverFrequencies[0] + 50f,
                crossoverFrequencies[2] - 50f
            )
            2 -> crossoverFrequencies[2] = f.coerceAtLeast(crossoverFrequencies[1] + 50f)
        }
    }

    // ===================== PRESETS =====================

    fun applyPresetFlat() {
        preamp = -3f
        subBoost = 0f
        bands.clear()
        bands.addAll(createDefaultBands().map { it.copy(gain = 0f, enabled = true) })
        selectedBandIndex = 0
        compressorMultibandEnabled = false
        limiterEnabled = true
        limiterThreshold = -1.5f
        limiterRatio = 8f
        limiterAttack = 1f
        limiterRelease = 120f
        limiterPostGain = 0f
    }

    fun applyPresetZeusInfrabass() {
        preamp = -5.5f
        subBoost = 6f

        bands.clear()
        bands.add(
            createNewBand(0, 18f).copy(
                gain = 9f,
                q = 0.75f,
                filterType = EqBand.FilterType.LOW_SHELF,
                enabled = true
            )
        )
        bands.add(
            createNewBand(1, 33f).copy(
                gain = 3.5f,
                q = 1.1f,
                filterType = EqBand.FilterType.PEAK,
                enabled = true
            )
        )
        bands.add(
            createNewBand(2, 48f).copy(
                gain = 2.5f,
                q = 2.2f,
                filterType = EqBand.FilterType.PEAK,
                enabled = true
            )
        )
        bands.add(
            createNewBand(3, 90f).copy(
                gain = 1.5f,
                q = 0.9f,
                filterType = EqBand.FilterType.LOW_SHELF,
                enabled = true
            )
        )
        bands.add(createNewBand(4, 250f).copy(gain = 0f, q = 1.0f, enabled = true))
        bands.add(createNewBand(5, 500f).copy(gain = 0f, q = 1.0f, enabled = true))
        bands.add(createNewBand(6, 1000f).copy(gain = 0f, q = 1.0f, enabled = true))
        bands.add(createNewBand(7, 2500f).copy(gain = 0.5f, q = 1.2f, enabled = true))
        bands.add(createNewBand(8, 6000f).copy(gain = 1.0f, q = 1.0f, enabled = true))
        bands.add(
            createNewBand(9, 12000f).copy(
                gain = 1.0f,
                q = 0.8f,
                filterType = EqBand.FilterType.HIGH_SHELF,
                enabled = true
            )
        )
        selectedBandIndex = 0

        compressorMultibandEnabled = true
        crossoverFrequencies[0] = 120f
        crossoverFrequencies[1] = 2500f
        crossoverFrequencies[2] = 8000f

        compMbThLow = -16f
        compMbRatioLow = 2.2f
        compMbKneeLow = 8f
        compMbAttackLow = 12f
        compMbReleaseLow = 160f
        compMbPreGainLow = 1.0f
        compMbPostGainLow = 1.0f

        compMbThLoMid = -14f
        compMbRatioLoMid = 2.0f
        compMbKneeLoMid = 6f
        compMbAttackLoMid = 10f
        compMbReleaseLoMid = 120f
        compMbPreGainLoMid = 0f
        compMbPostGainLoMid = 0f

        compMbThHiMid = -12f
        compMbRatioHiMid = 2.0f
        compMbKneeHiMid = 6f
        compMbAttackHiMid = 8f
        compMbReleaseHiMid = 90f
        compMbPreGainHiMid = 0f
        compMbPostGainHiMid = 0f

        compMbThHigh = -14f
        compMbRatioHigh = 2.5f
        compMbKneeHigh = 6f
        compMbAttackHigh = 5f
        compMbReleaseHigh = 70f
        compMbPreGainHigh = 0f
        compMbPostGainHigh = 0f

        limiterEnabled = true
        limiterThreshold = -1.2f
        limiterRatio = 6f
        limiterAttack = 1.0f
        limiterRelease = 120f
        limiterPostGain = 0f
    }

    fun applyPresetBassBoost() {
        applyPresetZeusInfrabass()
        preamp = -4.8f
        subBoost = 3f
        if (bands.isNotEmpty()) {
            bands[0] = bands[0].copy(
                frequency = 40f,
                gain = 5.5f,
                q = 0.85f,
                filterType = EqBand.FilterType.LOW_SHELF
            )
        }
        if (bands.size > 1) {
            bands[1] = bands[1].copy(frequency = 55f, gain = 2.5f, q = 1.0f)
        }
    }

    fun applyPresetVocalClear() {
        preamp = -3f
        subBoost = 0f
        bands.clear()
        bands.add(
            createNewBand(0, 60f).copy(
                gain = -1.5f,
                q = 0.9f,
                filterType = EqBand.FilterType.LOW_SHELF,
                enabled = true
            )
        )
        bands.add(createNewBand(1, 200f).copy(gain = -1f, q = 1.2f, enabled = true))
        bands.add(createNewBand(2, 1000f).copy(gain = 1.5f, q = 1.0f, enabled = true))
        bands.add(createNewBand(3, 3000f).copy(gain = 2.5f, q = 1.3f, enabled = true))
        bands.add(createNewBand(4, 6000f).copy(gain = 1.5f, q = 1.0f, enabled = true))
        bands.add(
            createNewBand(5, 12000f).copy(
                gain = 1f,
                q = 0.8f,
                filterType = EqBand.FilterType.HIGH_SHELF,
                enabled = true
            )
        )
        selectedBandIndex = 2
        compressorMultibandEnabled = true
        limiterEnabled = true
        limiterThreshold = -2f
        limiterRatio = 8f
        limiterAttack = 1f
        limiterRelease = 100f
    }

    fun applyPresetJazz() {
        preamp = -4f
        subBoost = 0.5f
        bands.clear()
        listOf(
            60f to -1.0f, 120f to 0.5f, 250f to -0.5f, 500f to 0.0f,
            1000f to 1.0f, 2200f to 1.5f, 4500f to 1.0f, 8000f to 0.5f,
            12000f to 1.0f
        ).forEachIndexed { i, (freq, gain) ->
            bands.add(createNewBand(i, freq).copy(gain = gain, q = if (freq in 1800f..5000f) 1.15f else 1.0f, enabled = true))
        }
        selectedBandIndex = 4
        compressorMultibandEnabled = true
        limiterEnabled = true
        limiterThreshold = -2f
        limiterRatio = 5f
        limiterAttack = 8f
        limiterRelease = 120f
    }

    fun applyPresetBassTreble() {
        preamp = -5f
        subBoost = 2f
        bands.clear()
        listOf(
            45f to 3.5f, 100f to 2.5f, 250f to 0f, 500f to -0.5f,
            1000f to 0f, 2500f to -0.5f, 5000f to 2.0f, 10000f to 3.0f,
            16000f to 3.5f
        ).forEachIndexed { i, (freq, gain) ->
            val type = when (i) {
                0 -> EqBand.FilterType.LOW_SHELF
                8 -> EqBand.FilterType.HIGH_SHELF
                else -> EqBand.FilterType.PEAK
            }
            bands.add(createNewBand(i, freq).copy(gain = gain, q = 0.95f, filterType = type, enabled = true))
        }
        selectedBandIndex = 0
        compressorMultibandEnabled = true
        limiterEnabled = true
        limiterThreshold = -1.5f
        limiterRatio = 6f
        limiterAttack = 2f
        limiterRelease = 110f
    }

    fun applyPresetAcoustic() {
        // Acoustic: graves profundos, voces presentes y agudos finos, sin volver áspera la zona alta.
        preamp = -5.5f
        subBoost = 2.5f
        bands.clear()
        listOf(
            32f to 2.8f, 70f to 3.2f, 140f to 1.2f, 280f to -0.8f,
            900f to 0.8f, 1800f to 1.8f, 3200f to 2.2f, 6500f to 1.5f,
            10000f to 1.8f, 14500f to 1.2f, 18000f to 0.8f
        ).forEachIndexed { i, (freq, gain) ->
            val type = when {
                i == 0 -> EqBand.FilterType.LOW_SHELF
                i == 10 -> EqBand.FilterType.HIGH_SHELF
                else -> EqBand.FilterType.PEAK
            }
            val q = when {
                freq in 1500f..4000f -> 1.05f
                freq >= 6000f -> 0.8f
                else -> 1.0f
            }
            bands.add(createNewBand(i, freq).copy(gain = gain, q = q, filterType = type, enabled = true))
        }
        selectedBandIndex = 5
        compressorMultibandEnabled = true
        limiterEnabled = true
        limiterThreshold = -1.8f
        limiterRatio = 5f
        limiterAttack = 3f
        limiterRelease = 120f
    }

    // ===================== PERSISTENCIA =====================

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
        compMbRatioLow = s.compRatioLow
        compMbRatioLoMid = s.compRatioLoMid
        compMbRatioHiMid = s.compRatioHiMid
        compMbRatioHigh = s.compRatioHigh
        compMbKneeLow = s.compKneeLow
        compMbKneeLoMid = s.compKneeLoMid
        compMbKneeHiMid = s.compKneeHiMid
        compMbKneeHigh = s.compKneeHigh
        compMbAttackLow = s.compAttackLow
        compMbAttackLoMid = s.compAttackLoMid
        compMbAttackHiMid = s.compAttackHiMid
        compMbAttackHigh = s.compAttackHigh
        compMbReleaseLow = s.compReleaseLow
        compMbReleaseLoMid = s.compReleaseLoMid
        compMbReleaseHiMid = s.compReleaseHiMid
        compMbReleaseHigh = s.compReleaseHigh
        compMbPostGainLow = s.compPostGainLow
        compMbPostGainLoMid = s.compPostGainLoMid
        compMbPostGainHiMid = s.compPostGainHiMid
        compMbPostGainHigh = s.compPostGainHigh
        compMbPreGainLow = s.compPreGainLow
        compMbPreGainLoMid = s.compPreGainLoMid
        compMbPreGainHiMid = s.compPreGainHiMid
        compMbPreGainHigh = s.compPreGainHigh
        pipelineEnabled = s.pipelineEnabled
        lowShelfEnabled = s.lowShelfEnabled
        peakBandsEnabled = s.peakEnabled
        highShelfEnabled = s.highShelfEnabled
        audioSessionEnabled = s.audioSessionEnabled
        selectedAudioSession = s.selectedAudioSession
    }

    fun saveNamedPreset(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) EqPrefs.saveNamed(getApplication(), clean, toSettings())
    }

    fun loadNamedPreset(name: String) {
        EqPrefs.loadNamed(getApplication(), name)?.let { loadFrom(it) }
    }

    fun deleteNamedPreset(name: String) { EqPrefs.deleteNamed(getApplication(), name) }

    fun namedPresetNames(): List<String> = EqPrefs.listNamed(getApplication())

    fun applyAutoEqProfile(profile: AutoEqProfile) {
        preamp = profile.preamp
        bands.clear()
        profile.filters.take(MAX_BANDS).forEachIndexed { i, f ->
            bands.add(createNewBand(i, f.frequency).copy(gain=f.gain, q=f.q, filterType=f.type, enabled=true))
        }
        if (bands.isEmpty()) bands.add(createNewBand(0, 1000f))
        selectedBandIndex = 0
        limiterEnabled = true
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
