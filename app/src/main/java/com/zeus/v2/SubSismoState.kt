package com.zeus.v2

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import java.util.WeakHashMap

private val subFrequencyStates = WeakHashMap<EqViewModel, MutableState<Float>>()

/** UI state for the selectable SUB/SISMO center. Kept outside EqSettings for backward JSON compatibility. */
var EqViewModel.subFrequencyHz: Float
    get() = synchronized(subFrequencyStates) {
        subFrequencyStates.getOrPut(this) { mutableFloatStateOf(60f) }.value
    }
    set(value) = synchronized(subFrequencyStates) {
        subFrequencyStates.getOrPut(this) { mutableFloatStateOf(60f) }.value = value.coerceIn(18f, 90f)
    }
