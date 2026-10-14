package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generador senoidal de subgrave para Zeus Bass.
 *
 * La frecuencia de salida es estable y configurable; la amplitud sigue la
 * envolvente del bajo de entrada con suavizado para reducir bombeo y aspereza.
 * No afirma detectar automáticamente la mitad exacta de cada nota de entrada.
 */
class SubHarmonic(private val sampleRate: Int) {
    private val safeSampleRate = sampleRate.coerceAtLeast(8000)
    private var phase = 0.0
    private var subTargetFreq = 24f
    private var envelope = 0f

    private val lpFilter = Biquad(safeSampleRate)

    // Coeficientes calculados una sola vez, no dentro del bucle de audio.
    private val attackCoeff =
        (1.0 - exp(-1.0 / (safeSampleRate * 0.008))).toFloat()
    private val releaseCoeff =
        (1.0 - exp(-1.0 / (safeSampleRate * 0.120))).toFloat()

    fun configure(targetFrequencyHz: Float) {
        subTargetFreq = targetFrequencyHz.coerceIn(18f, 55f)
        lpFilter.lowPass(subTargetFreq.coerceIn(18f, 60f), 0.9f)
    }

    fun process(bassSample: Float): Float {
        val inputLevel = abs(bassSample).coerceAtMost(1f)
        val coeff = if (inputLevel > envelope) attackCoeff else releaseCoeff
        envelope += coeff * (inputLevel - envelope)

        phase += 2.0 * PI * subTargetFreq.toDouble() / safeSampleRate
        if (phase >= 2.0 * PI) phase -= 2.0 * PI

        val rawSub = sin(phase) * envelope
        return lpFilter.process(rawSub.toFloat())
    }

    fun reset() {
        phase = 0.0
        envelope = 0f
        lpFilter.reset()
    }
}
