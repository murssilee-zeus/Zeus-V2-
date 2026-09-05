package com.zeus.v2

import kotlin.math.*

/**
 * Robust RBJ biquad used to calculate the real target response that is
 * subsequently sampled into Android DynamicsProcessing EQ bands.
 *
 * Shelf filters use the RBJ shelf-slope equation. The previous implementation
 * substituted sqrt(A) / Q for the shelf alpha term, which is not the RBJ
 * shelf equation and caused LOW_SHELF/HIGH_SHELF curves to have the wrong
 * shape and gain. Peak, pass, notch and band-pass filters keep their standard
 * RBJ equations.
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
        val sr = sampleRate.coerceIn(8000f, 384000f).toDouble()
        val fc = frequency.coerceIn(1f, (sr.toFloat() * 0.49f)).toDouble()
        val qSafe = q.coerceIn(0.1f, 40f).toDouble()
        val omega = 2.0 * PI * fc / sr
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * qSafe)
        val A = 10.0.pow(gainDb.coerceIn(-60f, 60f) / 40.0)
        val sqrtA = sqrt(A)

        // For RBJ shelf filters the fourth parameter is shelf slope S,
        // not the PEAK Q. EqBand already exposes a Q-like control, so use it
        // as S here. This gives stable, predictable shelf transitions while
        // preserving the existing UI and preset data.
        val shelfSlope = qSafe.coerceIn(0.1, 10.0)
        val shelfAlpha = (sn / 2.0) * sqrt(
            (A + 1.0 / A) * (1.0 / shelfSlope - 1.0) + 2.0
        )
        val shelfTwoSqrtAAlpha = 2.0 * sqrtA * shelfAlpha

        when (type) {
            EqBand.FilterType.PEAK -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cs
                b2 = 1.0 - alpha * A
                val a0 = 1.0 + alpha / A
                a1 = -2.0 * cs
                a2 = 1.0 - alpha / A
                normalize(a0)
            }
            EqBand.FilterType.LOW_SHELF -> {
                b0 = A * ((A + 1.0) - (A - 1.0) * cs + shelfTwoSqrtAAlpha)
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cs)
                b2 = A * ((A + 1.0) - (A - 1.0) * cs - shelfTwoSqrtAAlpha)
                val a0 = (A + 1.0) + (A - 1.0) * cs + shelfTwoSqrtAAlpha
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cs)
                a2 = (A + 1.0) + (A - 1.0) * cs - shelfTwoSqrtAAlpha
                normalize(a0)
            }
            EqBand.FilterType.HIGH_SHELF -> {
                b0 = A * ((A + 1.0) + (A - 1.0) * cs + shelfTwoSqrtAAlpha)
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cs)
                b2 = A * ((A + 1.0) + (A - 1.0) * cs - shelfTwoSqrtAAlpha)
                val a0 = (A + 1.0) - (A - 1.0) * cs + shelfTwoSqrtAAlpha
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cs)
                a2 = (A + 1.0) - (A - 1.0) * cs - shelfTwoSqrtAAlpha
                normalize(a0)
            }
            EqBand.FilterType.LOW_PASS -> {
                b0 = (1.0 - cs) / 2.0
                b1 = 1.0 - cs
                b2 = (1.0 - cs) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                normalize(a0)
            }
            EqBand.FilterType.HIGH_PASS -> {
                b0 = (1.0 + cs) / 2.0
                b1 = -(1.0 + cs)
                b2 = (1.0 + cs) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                normalize(a0)
            }
            EqBand.FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cs
                b2 = 1.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                normalize(a0)
            }
            EqBand.FilterType.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                val a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
                normalize(a0)
            }
            EqBand.FilterType.BYPASS -> {
                b0 = 1.0
                b1 = 0.0
                b2 = 0.0
                a1 = 0.0
                a2 = 0.0
            }
        }
    }

    private fun normalize(a0: Double) {
        if (!a0.isFinite() || abs(a0) < 1e-12) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
            return
        }
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
    }

    /** Magnitude in dB at [freq], using the exact normalized coefficients. */
    fun responseDb(freq: Float): Float {
        val f = freq.coerceAtLeast(0.01f).toDouble()
        val w = 2.0 * PI * f / sampleRate.coerceAtLeast(8000f)
        val c = cos(w)
        val s = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)

        val numRe = b0 + b1 * c + b2 * c2
        val numIm = b1 * s + b2 * s2
        val denRe = 1.0 + a1 * c + a2 * c2
        val denIm = a1 * s + a2 * s2
        val num2 = numRe * numRe + numIm * numIm
        val den2 = denRe * denRe + denIm * denIm

        if (!num2.isFinite() || !den2.isFinite() || den2 < 1e-24) return 0f
        val mag = sqrt((num2 / den2).coerceAtLeast(1e-20))
        return (20.0 * log10(mag)).toFloat().coerceIn(-80f, 80f)
    }
}
