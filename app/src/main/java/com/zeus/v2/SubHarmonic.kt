package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Sintetizador de subgrave senoidal con frecuencia objetivo configurable.
 * La amplitud sigue la energía del bajo de entrada para conservar su dinámica.
 */
class SubHarmonic(private val sampleRate: Int) {
    private var phase = 0.0
    private var subTargetFreq = 30f
    private val lpFilter = Biquad(sampleRate)

    fun configure(targetFrequencyHz: Float) {
        subTargetFreq = targetFrequencyHz.coerceIn(20f, 50f)
        lpFilter.lowPass(subTargetFreq, 1.2f)
    }

    fun process(bassSample: Float): Float {
        // Oscilador de fase continua: genera una senoide real a la frecuencia objetivo.
        val safeRate = sampleRate.coerceAtLeast(1)
        phase += 2.0 * PI * subTargetFreq.toDouble() / safeRate.toDouble()
        if (phase >= 2.0 * PI) phase -= 2.0 * PI

        val rawSub = sin(phase) * abs(bassSample)
        return lpFilter.process(rawSub.toFloat())
    }

    fun reset() {
        phase = 0.0
        lpFilter.reset()
    }
}
