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
    }

    var amount by mutableFloatStateOf(PunchPreset.DEFAULT)
        private set

    fun updatePunchAmount(value: Float) {
        amount = value.coerceIn(0f, 100f)
    }

    fun loadSaved() {
        amount = getApplication<Application>()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_AMOUNT, PunchPreset.DEFAULT)
            .coerceIn(0f, 100f)
    }

    fun save() {
        getApplication<Application>()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_AMOUNT, amount)
            .apply()
    }
}
