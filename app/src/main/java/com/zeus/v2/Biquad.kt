package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Biquad de audio en tiempo real (Direct Form II transpuesta, en Double).
 *
 * Los coeficientes se publican como un objeto inmutable de una sola vez, así que el
 * hilo de audio nunca ve una mezcla de coeficientes viejos y nuevos aunque configure()
 * se llame desde el hilo de la UI.
 */
class Biquad(private val sampleRate: Int) {

    private class Coeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private val identity = Coeffs(1.0, 0.0, 0.0, 0.0, 0.0)

    @Volatile
    private var c: Coeffs = identity

    private var z1 = 0.0
    private var z2 = 0.0

    fun lowPass(frequencyHz: Float, q: Float) {
        val cs = cos(omega(frequencyHz))
        val alpha = sin(omega(frequencyHz)) / (2.0 * quality(q))
        publish(
            (1.0 - cs) / 2.0, 1.0 - cs, (1.0 - cs) / 2.0,
            1.0 + alpha, -2.0 * cs, 1.0 - alpha
        )
    }

    fun highPass(frequencyHz: Float, q: Float) {
        val cs = cos(omega(frequencyHz))
        val alpha = sin(omega(frequencyHz)) / (2.0 * quality(q))
        publish(
            (1.0 + cs) / 2.0, -(1.0 + cs), (1.0 + cs) / 2.0,
            1.0 + alpha, -2.0 * cs, 1.0 - alpha
        )
    }

    /** Pasa-banda con ganancia 0 dB en el centro y ganancia 0 en DC. */
    fun bandPass(frequencyHz: Float, q: Float) {
        val cs = cos(omega(frequencyHz))
        val alpha = sin(omega(frequencyHz)) / (2.0 * quality(q))
        publish(
            alpha, 0.0, -alpha,
            1.0 + alpha, -2.0 * cs, 1.0 - alpha
        )
    }

    fun peak(frequencyHz: Float, gainDb: Float, q: Float) {
        val cs = cos(omega(frequencyHz))
        val alpha = sin(omega(frequencyHz)) / (2.0 * quality(q))
        val a = 10.0.pow(gainDb.coerceIn(-24f, 24f) / 40.0)
        publish(
            1.0 + alpha * a, -2.0 * cs, 1.0 - alpha * a,
            1.0 + alpha / a, -2.0 * cs, 1.0 - alpha / a
        )
    }

    fun lowShelf(frequencyHz: Float, gainDb: Float) {
        val w = omega(frequencyHz)
        val cs = cos(w)
        val sn = sin(w)
        val a = 10.0.pow(gainDb.coerceIn(-24f, 24f) / 40.0)
        val slope = 1.0
        val alpha = (sn / 2.0) * sqrt(((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0).coerceAtLeast(0.0))
        val two = 2.0 * sqrt(a) * alpha

        publish(
            a * ((a + 1.0) - (a - 1.0) * cs + two),
            2.0 * a * ((a - 1.0) - (a + 1.0) * cs),
            a * ((a + 1.0) - (a - 1.0) * cs - two),
            (a + 1.0) + (a - 1.0) * cs + two,
            -2.0 * ((a - 1.0) + (a + 1.0) * cs),
            (a + 1.0) + (a - 1.0) * cs - two
        )
    }

    fun process(input: Float): Float {
        val k = c // una sola lectura por muestra
        val x = input.toDouble()
        val y = k.b0 * x + z1
        z1 = k.b1 * x - k.a1 * y + z2
        z2 = k.b2 * x - k.a2 * y

        if (!y.isFinite() || !z1.isFinite() || !z2.isFinite()) {
            reset()
            return 0f
        }

        // Evita valores subnormales tras silencio digital.
        if (abs(z1) < 1e-30) z1 = 0.0
        if (abs(z2) < 1e-30) z2 = 0.0

        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    private fun omega(frequencyHz: Float): Double {
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val f = frequencyHz.coerceIn(1f, (sr * 0.45).toFloat()).toDouble()
        return 2.0 * PI * f / sr
    }

    private fun quality(q: Float): Double = q.coerceIn(0.1f, 20f).toDouble()

    private fun publish(
        b0: Double, b1: Double, b2: Double,
        a0: Double, a1: Double, a2: Double
    ) {
        c = if (!a0.isFinite() || abs(a0) < 1e-12) {
            identity
        } else {
            Coeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}
