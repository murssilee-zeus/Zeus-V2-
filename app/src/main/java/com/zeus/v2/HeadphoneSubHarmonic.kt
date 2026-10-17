package com.zeus.v2

import kotlin.math.abs
import kotlin.math.tanh

/**
 * Sub-octave helper tuned for headphone output.
 * The caller supplies mono bass so synthesized low-frequency energy stays centered.
 */
class HeadphoneSubHarmonic(private val sampleRate: Int) {
    private var lastSample = 0f
    private var flipFlopState = 1f
    private var envelope = 0f
    private val lowPassPurifier = Biquad(sampleRate.coerceAtLeast(8000))

    init {
        lowPassPurifier.lowPass(45f, 1.2f)
    }

    fun configure(targetFrequencyHz: Float) {
        lowPassPurifier.lowPass(targetFrequencyHz.coerceIn(32f, 55f), 1.2f)
    }

    fun process(inputSample: Float): Float {
        val input = inputSample.coerceIn(-1f, 1f)
        val envCoeff = if (abs(input) > envelope) 0.008f else 0.0012f
        envelope += envCoeff * (abs(input) - envelope)

        if (input > 0.001f && lastSample <= 0.001f) {
            flipFlopState = -flipFlopState
        }
        lastSample = input

        val rawSub = flipFlopState * envelope
        val filteredSub = lowPassPurifier.process(rawSub)
        return tanh(filteredSub.toDouble()).toFloat()
    }

    fun reset() {
        lastSample = 0f
        flipFlopState = 1f
        envelope = 0f
        lowPassPurifier.reset()
    }
}
