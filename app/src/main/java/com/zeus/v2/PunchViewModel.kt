package com.zeus.v2

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** State holder for the real Audio Framework bass controls. */
class PunchViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PREFS = "zeus_punch"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_CENTER = "center"
        private const val KEY_Q = "q"
        private const val KEY_BASS_AMOUNT = "bass_amount"
        private const val KEY_BASS_FREQUENCY = "bass_frequency"
        private const val KEY_BASS_MONO = "bass_mono"
        private const val KEY_BASS_HARMONICS = "bass_harmonics"
        private const val KEY_EPIC_AMOUNT = "epicenter_amount"
        private const val KEY_EPIC_DRIVE = "epicenter_drive"
        private const val KEY_EPIC_DEPTH = "epicenter_depth"
        private const val KEY_EPIC_HARMONICS = "epicenter_harmonics"
        private const val KEY_EPIC_FREQ = "epicenter_frequency"
    }

    var amount by mutableFloatStateOf(PunchPreset.DEFAULT)
        private set
    var centerHz by mutableFloatStateOf(49.6f)
        private set
    var q by mutableFloatStateOf(1.20f)
        private set
    var bassAmount by mutableFloatStateOf(0f)
        private set
    var bassFrequencyHz by mutableFloatStateOf(45f)
        private set
    /** Requested bass-centering mode. Stock DynamicsProcessing has no channel-summing primitive. */
    var bassMono by mutableStateOf(true)
        private set
    var bassHarmonics by mutableFloatStateOf(0f)
    var epicenterAmount by mutableFloatStateOf(0f)
    var epicenterDrive by mutableFloatStateOf(0f)
    var epicenterDepth by mutableFloatStateOf(0f)
    var epicenterHarmonics by mutableFloatStateOf(0f)
    var epicenterFrequencyHz by mutableFloatStateOf(36f)
        private set

    fun updatePunchAmount(value: Float) { amount = value.coerceIn(0f, 100f) }
    fun updatePunchCenter(value: Float) { centerHz = value.coerceIn(35f, 65f) }
    fun updatePunchQ(value: Float) { q = value.coerceIn(0.5f, 3.0f) }
    fun updateBassAmount(value: Float) { bassAmount = value.coerceIn(0f, 100f) }
    fun updateBassFrequency(value: Float) { bassFrequencyHz = value.coerceIn(25f, 120f) }
    fun updateBassMono(value: Boolean) { bassMono = value }
    fun updateBassHarmonics(value: Float) { bassHarmonics = value.coerceIn(0f, 100f) }
    fun updateEpicenterAmount(value: Float) { epicenterAmount = value.coerceIn(0f, 100f) }
    fun updateEpicenterDrive(value: Float) { epicenterDrive = value.coerceIn(0f, 100f) }
    fun updateEpicenterDepth(value: Float) { epicenterDepth = value.coerceIn(0f, 100f) }
    fun updateEpicenterHarmonics(value: Float) { epicenterHarmonics = value.coerceIn(0f, 100f) }
    fun updateEpicenterFrequency(value: Float) { epicenterFrequencyHz = value.coerceIn(18f, 80f) }

    fun loadSaved() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        amount = prefs.getFloat(KEY_AMOUNT, PunchPreset.DEFAULT).coerceIn(0f, 100f)
        centerHz = prefs.getFloat(KEY_CENTER, 49.6f).coerceIn(35f, 65f)
        q = prefs.getFloat(KEY_Q, 1.20f).coerceIn(0.5f, 3.0f)
        bassAmount = prefs.getFloat(KEY_BASS_AMOUNT, 0f).coerceIn(0f, 100f)
        bassFrequencyHz = prefs.getFloat(KEY_BASS_FREQUENCY, 45f).coerceIn(25f, 120f)
        bassMono = prefs.getBoolean(KEY_BASS_MONO, true)
        bassHarmonics = prefs.getFloat(KEY_BASS_HARMONICS, 0f).coerceIn(0f, 100f)
        epicenterAmount = prefs.getFloat(KEY_EPIC_AMOUNT, 0f).coerceIn(0f, 100f)
        epicenterDrive = prefs.getFloat(KEY_EPIC_DRIVE, 0f).coerceIn(0f, 100f)
        epicenterDepth = prefs.getFloat(KEY_EPIC_DEPTH, 0f).coerceIn(0f, 100f)
        epicenterHarmonics = prefs.getFloat(KEY_EPIC_HARMONICS, 0f).coerceIn(0f, 100f)
        epicenterFrequencyHz = prefs.getFloat(KEY_EPIC_FREQ, 36f).coerceIn(18f, 80f)
    }

    fun save() {
        getApplication<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AMOUNT, amount)
            .putFloat(KEY_CENTER, centerHz)
            .putFloat(KEY_Q, q)
            .putFloat(KEY_BASS_AMOUNT, bassAmount)
            .putFloat(KEY_BASS_FREQUENCY, bassFrequencyHz)
            .putBoolean(KEY_BASS_MONO, bassMono)
            .putFloat(KEY_BASS_HARMONICS, bassHarmonics)
            .putFloat(KEY_EPIC_AMOUNT, epicenterAmount)
            .putFloat(KEY_EPIC_DRIVE, epicenterDrive)
            .putFloat(KEY_EPIC_DEPTH, epicenterDepth)
            .putFloat(KEY_EPIC_HARMONICS, epicenterHarmonics)
            .putFloat(KEY_EPIC_FREQ, epicenterFrequencyHz)
            .apply()
    }
}
