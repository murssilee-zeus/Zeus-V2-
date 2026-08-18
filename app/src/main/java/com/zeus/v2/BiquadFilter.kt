package com.zeus.v2

import kotlin.math.*

/**
 * Biquad de 2º orden para calcular respuesta en frecuencia real.
 * Usado solo para generar la curva objetivo que luego se mapea
 * a las bandas Peak de DynamicsProcessing.
 */
class BiquadFilter(
    frequency: Float,
    gainDb: Float,
    q: Float,
    type: EqBand.FilterType,
    private val sampleRate: Float = 48000f
) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    init {
        calculate(frequency, gainDb, q, type)
    }

    fun calculate(frequency: Float, gainDb: Float, q: Float, type: EqBand.FilterType) {
        val fc = frequency.coerceIn(10f, sampleRate * 0.45f)
        val qSafe = q.coerceIn(0.1f, 40f).toDouble()
        val A = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * fc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * qSafe)
        val beta = sqrt(A) / qSafe

        when (type) {
            EqBand.FilterType.PEAK -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cs
                b2 = 1.0 - alpha * A
                val a0 = 1.0 + alpha / A
                a1 = -2.0 * cs
                a2 = 1.0 - alpha / A
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.LOW_SHELF -> {
                b0 = A * ((A + 1) - (A - 1) * cs + beta * sn)
                b1 = 2.0 * A * ((A - 1) - (A + 1) * cs)
                b2 = A * ((A + 1) - (A - 1) * cs - beta * sn)
                val a0 = (A + 1) + (A - 1) * cs + beta * sn
                a1 = -2.0 * ((A - 1) + (A + 1) * cs)
                a2 = (A + 1) + (A - 1) * cs - beta * sn
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.HIGH_SHELF -> {
                b0 = A * ((A + 1) + (A - 1) * cs + beta * sn)
                b1 = -2.0 * A * ((A - 1) + (A + 1) * cs)
                b2 = A * ((A + 1) + (A - 1) * cs - beta * sn)
                val a0 = (A + 1) - (A - 1) * cs + beta * sn
                a1 = 2.0 * ((A - 1) - (A + 1) * cs)
                a2 = (A + 1) - (A - 1) * cs - beta * sn
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.LOW_PASS -> {
                b0 = (1.0 - cs) / 2.0
                b1 = 1.0 - cs
                b2 = (1.0 - cs) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.HIGH_PASS -> {
                b0 = (1.0 + cs) / 2.0
                b1 = -(1.0 + cs)
                b2 = (1.0 + cs) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cs
                b2 = 1.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
            }
            EqBand.FilterType.BYPASS -> {
                b0 = 1.0; b1 = 0.0; b2 = 0.0
                a1 = 0.0; a2 = 0.0
            }
        }
    }

    /**
     * Devuelve la magnitud de la respuesta en dB a la frecuencia dada.
     */
    fun responseDb(freq: Float): Float {
        val w = 2.0 * PI * freq / sampleRate
        val cosw = cos(w)
        val cos2w = cos(2.0 * w)
        val sinw = sin(w)
        val sin2w = sin(2.0 * w)

        val numRe = b0 + b1 * cosw + b2 * cos2w
        val numIm = b1 * sinw + b2 * sin2w
        val denRe = 1.0 + a1 * cosw + a2 * cos2w
        val denIm = a1 * sinw + a2 * sin2w

        val numMag2 = numRe * numRe + numIm * numIm
        val denMag2 = denRe * denRe + denIm * denIm

        if (denMag2 < 1e-20) return 0f
        val mag = sqrt(numMag2 / denMag2)
        return (20.0 * log10(mag.coerceAtLeast(1e-10))).toFloat()
    }
}
