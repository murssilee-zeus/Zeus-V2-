package com.zeus.v2

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** Dedicated state holder for the real DSP Punch control. */
class PunchViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PREFS = "zeus_punch"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_CENTER = "center"
        private const val KEY_Q = "q"
    }

    var amount by mutableFloatStateOf(PunchPreset.DEFAULT)
        private set

    /** User-adjustable punch center. Kept inside the intended 35-65 Hz impact region. */
    var centerHz by mutableFloatStateOf(49.6f)
        private set

    /** User-adjustable punch Q. */
    var q by mutableFloatStateOf(1.20f)
        private set

    fun updatePunchAmount(value: Float) {
        amount = value.coerceIn(0f, 100f)
    }

    fun updatePunchCenter(value: Float) {
        centerHz = value.coerceIn(35f, 65f)
    }

    fun updatePunchQ(value: Float) {
        q = value.coerceIn(0.5f, 3.0f)
    }

    fun loadSaved() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        amount = prefs.getFloat(KEY_AMOUNT, PunchPreset.DEFAULT).coerceIn(0f, 100f)
        centerHz = prefs.getFloat(KEY_CENTER, 49.6f).coerceIn(35f, 65f)
        q = prefs.getFloat(KEY_Q, 1.20f).coerceIn(0.5f, 3.0f)
    }

    fun save() {
        getApplication<Application>()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AMOUNT, amount)
            .putFloat(KEY_CENTER, centerHz)
            .putFloat(KEY_Q, q)
            .apply()
    }
}
