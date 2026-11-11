package com.zeus.v2

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Four-way Linkwitz-Riley 4th-order splitter.
 *
 * Each crossover is a Butterworth 2nd-order section cascaded twice,
 * producing LR4 slopes of 24 dB/octave with matched phase at each
 * crossover. The splitter is used only by the PCM DSP path.
 */
class ZeusLr4BandSplitter(
    sampleRate: Int,
    crossover1Hz: Float = 180f,
    crossover2Hz: Float = 1800f,
    crossover3Hz: Float = 8000f
) {
    private val safeSampleRate = sampleRate.coerceIn(8000, 192000)
    private val maxHz = safeSampleRate * 0.45f
    private val f1Max = minOf(1000f, maxHz / 3f).coerceAtLeast(20f)
    private val f1 = (if (crossover1Hz.isFinite()) crossover1Hz else 180f)
        .coerceIn(20f, f1Max)
    private val f2Max = minOf(8000f, maxHz * 0.75f).coerceAtLeast(f1 + 20f)
    private val f2 = (if (crossover2Hz.isFinite()) crossover2Hz else 1800f)
        .coerceIn(f1 + 20f, f2Max)
    private val f3 = (if (crossover3Hz.isFinite()) crossover3Hz else 8000f)
        .coerceIn(f2 + 20f, maxHz)

    private val low = Lr4Filter(safeSampleRate, f1, false)
    private val lowMidHp = Lr4Filter(safeSampleRate, f1, true)
    private val lowMidLp = Lr4Filter(safeSampleRate, f2, false)
    private val highMidHp = Lr4Filter(safeSampleRate, f2, true)
    private val highMidLp = Lr4Filter(safeSampleRate, f3, false)
    private val high = Lr4Filter(safeSampleRate, f3, true)

    data class Bands(
        val low: Float,
        val lowMid: Float,
        val highMid: Float,
        val high: Float
    )

    fun process(input: Float): Bands {
        val l = low.process(input)
        val lm = lowMidLp.process(lowMidHp.process(input))
        val hm = highMidLp.process(highMidHp.process(input))
        val h = high.process(input)
        return Bands(l, lm, hm, h)
    }

    fun reset() {
        low.reset()
        lowMidHp.reset()
        lowMidLp.reset()
        highMidHp.reset()
        highMidLp.reset()
        high.reset()
    }

    private class Lr4Filter(
        sampleRate: Int,
        cutoffHz: Float,
        highPass: Boolean
    ) {
        private val first = Biquad(sampleRate, cutoffHz, highPass, 0.70710678f)
        private val second = Biquad(sampleRate, cutoffHz, highPass, 0.70710678f)

        fun process(x: Float): Float = second.process(first.process(x))
        fun reset() { first.reset(); second.reset() }
    }

    private class Biquad(
        sampleRate: Int,
        cutoffHz: Float,
        highPass: Boolean,
        q: Float
    ) {
        private val b0: Float
        private val b1: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float

        private var z1 = 0f
        private var z2 = 0f

        init {
            val w0 = 2.0 * PI * cutoffHz / sampleRate.toDouble()
            val c = cos(w0)
            val s = sin(w0)
            val alpha = s / (2.0 * q)
            val a0 = 1.0 + alpha

            val rawB0: Double
            val rawB1: Double
            val rawB2: Double
            if (highPass) {
                rawB0 = (1.0 + c) * 0.5
                rawB1 = -(1.0 + c)
                rawB2 = rawB0
            } else {
                rawB0 = (1.0 - c) * 0.5
                rawB1 = 1.0 - c
                rawB2 = rawB0
            }

            b0 = (rawB0 / a0).toFloat()
            b1 = (rawB1 / a0).toFloat()
            b2 = (rawB2 / a0).toFloat()
            a1 = (-2.0 * c / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun reset() {
            z1 = 0f
            z2 = 0f
        }
    }
}
