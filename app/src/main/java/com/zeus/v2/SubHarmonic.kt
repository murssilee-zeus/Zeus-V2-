package com.zeus.v2

import kotlin.math.abs

/**
 * Sintetizador de sub-octava por conmutación de estado (flip-flop).
 *
 * Alterna la polaridad en cada cruce ascendente de la señal de entrada.
 * La salida se suaviza con una envolvente y un filtro pasa-bajas.
 *
 * Nota: con audio musical complejo, los cruces por cero no garantizan una
 * división exacta entre dos de la nota fundamental; para ello se requiere
 * detección/seguimiento de tono.
 */
class SubHarmonic(private val sampleRate: Int) {
    private val safeSampleRate = sampleRate.coerceAtLeast(8000)
    private var lastSample = 0f
    private var flipFlopState = 1f
    private var envelope = 0f

    private val lowPassPurifier = Biquad(safeSampleRate)

    fun configure(targetFrequencyHz: Float) {
        val cutoff = targetFrequencyHz.coerceIn(18f, 48f)
            .coerceAtMost(safeSampleRate * 0.45f)
        lowPassPurifier.lowPass(cutoff, 1.0f)
    }

    fun process(inputSample: Float): Float {
        val input = inputSample.coerceIn(-1f, 1f)

        // Ataque rápido y liberación más suave para conservar el impacto
        // sin hacer que el nivel siga cada muestra de la forma de onda.
        val envCoeff = if (abs(input) > envelope) 0.008f else 0.0012f
        envelope += envCoeff * (abs(input) - envelope)

        // Un cambio de estado por cruce ascendente con pequeña histéresis.
        if (input > 0.001f && lastSample <= 0.001f) {
            flipFlopState = -flipFlopState
        }
        lastSample = input

        val rawSub = flipFlopState * envelope
        return lowPassPurifier.process(rawSub)
    }

    fun reset() {
        lastSample = 0f
        flipFlopState = 1f
        envelope = 0f
        lowPassPurifier.reset()
    }
}
