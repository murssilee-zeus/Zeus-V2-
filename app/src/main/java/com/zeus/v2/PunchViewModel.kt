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
        private set

    fun updatePunchAmount(value: Float) { amount = value.coerceIn(0f, 100f) }
    fun updatePunchCenter(value: Float) { centerHz = value.coerceIn(35f, 65f) }
    fun updatePunchQ(value: Float) { q = value.coerceIn(0.5f, 3.0f) }
    fun updateBassAmount(value: Float) { bassAmount = value.coerceIn(0f, 100f) }
    fun updateBassFrequency(value: Float) { bassFrequencyHz = value.coerceIn(25f, 120f) }
    fun updateBassMono(value: Boolean) { bassMono = value }
    fun updateBassHarmonics(value: Float) { bassHarmonics = value.coerceIn(0f, 100f) }

    fun loadSaved() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        amount = prefs.getFloat(KEY_AMOUNT, PunchPreset.DEFAULT).coerceIn(0f, 100f)
        centerHz = prefs.getFloat(KEY_CENTER, 49.6f).coerceIn(35f, 65f)
        q = prefs.getFloat(KEY_Q, 1.20f).coerceIn(0.5f, 3.0f)
        bassAmount = prefs.getFloat(KEY_BASS_AMOUNT, 0f).coerceIn(0f, 100f)
        bassFrequencyHz = prefs.getFloat(KEY_BASS_FREQUENCY, 45f).coerceIn(25f, 120f)
        bassMono = prefs.getBoolean(KEY_BASS_MONO, true)
        bassHarmonics = prefs.getFloat(KEY_BASS_HARMONICS, 0f).coerceIn(0f, 100f)
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
            .apply()
    }
}
