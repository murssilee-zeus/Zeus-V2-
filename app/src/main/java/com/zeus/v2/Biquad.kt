package com.zeus.v2

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.abs

/**
 * Biquad de audio en tiempo real con estado independiente por instancia.
 * Incluye los tipos de filtro utilizados por ZeusEpicenter y SubHarmonic.
 */
class Biquad(private val sampleRate: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun lowPass(frequencyHz: Float, q: Float) {
        val (w, sn, cs, alpha) = params(frequencyHz, q)
        b0 = (1.0 - cs) / 2.0
        b1 = 1.0 - cs
        b2 = (1.0 - cs) / 2.0
        normalize(1.0 + alpha, -2.0 * cs, 1.0 - alpha)
    }

    fun bandPass(frequencyHz: Float, q: Float) {
        val (_, _, cs, alpha) = params(frequencyHz, q)
        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        normalize(1.0 + alpha, -2.0 * cs, 1.0 - alpha)
    }

    fun peak(frequencyHz: Float, gainDb: Float, q: Float) {
        val (_, _, cs, alpha) = params(frequencyHz, q)
        val a = 10.0.pow(gainDb.coerceIn(-24f, 24f) / 40.0)
        b0 = 1.0 + alpha * a
        b1 = -2.0 * cs
        b2 = 1.0 - alpha * a
        normalize(1.0 + alpha / a, -2.0 * cs, 1.0 - alpha / a)
    }

    fun lowShelf(frequencyHz: Float, gainDb: Float) {
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val f = frequencyHz.coerceIn(1f, (sr * 0.45).toFloat()).toDouble()
        val a = 10.0.pow(gainDb.coerceIn(-24f, 24f) / 40.0)
        val w = 2.0 * PI * f / sr
        val cs = cos(w)
        val sn = sin(w)
        val slope = 1.0
        val alpha = (sn / 2.0) * sqrt(((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0).coerceAtLeast(0.0))
        val two = 2.0 * sqrt(a) * alpha
        b0 = a * ((a + 1.0) - (a - 1.0) * cs + two)
        b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cs)
        b2 = a * ((a + 1.0) - (a - 1.0) * cs - two)
        normalize((a + 1.0) + (a - 1.0) * cs + two,
            -2.0 * ((a - 1.0) + (a + 1.0) * cs),
            (a + 1.0) + (a - 1.0) * cs - two)
    }

    fun process(input: Float): Float {
        val x = input.toDouble()
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        if (!y.isFinite() || !z1.isFinite() || !z2.isFinite()) {
            reset()
            return 0f
        }
        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    private fun params(frequencyHz: Float, q: Float): DoubleArray {
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val f = frequencyHz.coerceIn(1f, (sr * 0.45).toFloat()).toDouble()
        val quality = q.coerceIn(0.1f, 20f).toDouble()
        val w = 2.0 * PI * f / sr
        val sn = sin(w)
        val cs = cos(w)
        return doubleArrayOf(w, sn, cs, sn / (2.0 * quality))
    }

    private fun normalize(a0: Double, rawA1: Double, rawA2: Double) {
        if (!a0.isFinite() || abs(a0) < 1e-12) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
            reset()
            return
        }
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 = rawA1 / a0
        a2 = rawA2 / a0
    }
}
