package com.zeus.v2

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native PCM Epicenter processor.
 *
 * PCM IN -> Zeus MBC -> Epicenter Extreme -> Atmos -> PCM OUT
 */
class ZeusEpicenter(
    private val sampleRate: Int
) {
    var enabled: Boolean = true

    private val left = Channel(sampleRate)
    private val right = Channel(sampleRate)

    private var lastAmount = -1f
    private var lastPunch = -1f
    private var lastHarmonics = -1f
    private var lastBassFreq = -1f
    private var lastSubFreq = -1f

    fun configure(
        amountPercent: Float,
        punchPercent: Float,
        harmonicsPercent: Float,
        bassFrequencyHz: Float,
        subFrequencyHz: Float
    ) {
        val amount = amountPercent.coerceIn(0f, 100f)
        val punch = punchPercent.coerceIn(0f, 100f)
        val harmonics = harmonicsPercent.coerceIn(0f, 100f)
        val bassFreq = bassFrequencyHz.coerceIn(25f, 120f)
        val subFreq = subFrequencyHz.coerceIn(18f, 50f)

        if (
            abs(amount - lastAmount) < 0.01f &&
            abs(punch - lastPunch) < 0.01f &&
            abs(harmonics - lastHarmonics) < 0.01f &&
            abs(bassFreq - lastBassFreq) < 0.01f &&
            abs(subFreq - lastSubFreq) < 0.01f
        ) return

        lastAmount = amount
        lastPunch = punch
        lastHarmonics = harmonics
        lastBassFreq = bassFreq
        lastSubFreq = subFreq

        val a = amount / 100f
        val h = harmonics / 100f

        // Extreme profile, distributed over four musical regions.
        val subGain = (a * 10.0f).coerceAtMost(12f)
        val punchGain =
            (PunchControl.midBassGain(punch) * 1.90f).coerceAtMost(10f)
        val secondGain =
            ((h * 4.0f) + (a * 2.0f)).coerceAtMost(9f)
        val thirdGain =
            (h * 1.8f).coerceAtMost(5f)

        left.configure(
            subFreq,
            subGain,
            bassFreq,
            punchGain,
            secondGain,
            thirdGain
        )
        right.configure(
            subFreq,
            subGain,
            bassFreq,
            punchGain,
            secondGain,
            thirdGain
        )
    }

    fun processStereo(
        pcm: ShortArray,
        size: Int = pcm.size
    ) {
        if (!enabled) return

        val n =
            size.coerceIn(
                0,
                pcm.size - (pcm.size % 2)
            )

        if (n < 2) return

        for (i in 0 until n step 2) {
            val l = pcm[i] / 32768f
            val r = pcm[i + 1] / 32768f

            pcm[i] = toShort(left.process(l))
            pcm[i + 1] = toShort(right.process(r))
        }
    }

    fun reset() {
        left.reset()
        right.reset()
    }

    private fun toShort(x: Float): Short =
        (x.coerceIn(-1f, 1f) * 32767f)
            .toInt()
            .toShort()

    private class Channel(
        sampleRate: Int
    ) {
        private val sub = Biquad(sampleRate)
        private val punch = Biquad(sampleRate)
        private val second = Biquad(sampleRate)
        private val third = Biquad(sampleRate)

        fun configure(
            subFreq: Float,
            subGain: Float,
            punchFreq: Float,
            punchGain: Float,
            secondGain: Float,
            thirdGain: Float
        ) {
            sub.setLowShelf(
                subFreq,
                subGain
            )

            punch.setPeaking(
                punchFreq,
                punchGain,
                0.85f
            )

            second.setPeaking(
                (punchFreq * 2f)
                    .coerceIn(80f, 320f),
                secondGain,
                0.90f
            )

            third.setPeaking(
                (punchFreq * 3f)
                    .coerceIn(240f, 1200f),
                thirdGain,
                0.95f
            )
        }

        fun process(x: Float): Float =
            third.process(
                second.process(
                    punch.process(
                        sub.process(x)
                    )
                )
            )

        fun reset() {
            sub.reset()
            punch.reset()
            second.reset()
            third.reset()
        }
    }

    private class Biquad(
        private val sampleRate: Int
    ) {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f

        private var z1 = 0f
        private var z2 = 0f

        fun setLowShelf(
            freq: Float,
            gainDb: Float
        ) {
            setCoefficients(
                freq,
                gainDb,
                0.7071f,
                true
            )
        }

        fun setPeaking(
            freq: Float,
            gainDb: Float,
            q: Float
        ) {
            setCoefficients(
                freq,
                gainDb,
                q,
                false
            )
        }

        private fun setCoefficients(
            frequency: Float,
            gainDb: Float,
            q: Float,
            lowShelf: Boolean
        ) {
            val freq =
                frequency.coerceIn(
                    18f,
                    sampleRate * 0.45f
                )

            val w0 =
                2.0 * Math.PI *
                    freq /
                    sampleRate

            val c = cos(w0)
            val s = sin(w0)
            val alpha =
                s /
                    (2.0 * q)

            val a =
                10.0.pow(
                    gainDb / 40.0
                )

            val sqrtA = sqrt(a)

            val rb0: Double
            val rb1: Double
            val rb2: Double
            val ra0: Double
            val ra1: Double
            val ra2: Double

            if (lowShelf) {
                rb0 =
                    a * (
                        (a + 1.0) -
                            (a - 1.0) * c +
                            2.0 * sqrtA * alpha
                    )

                rb1 =
                    2.0 * a * (
                        (a - 1.0) -
                            (a + 1.0) * c
                    )

                rb2 =
                    a * (
                        (a + 1.0) -
                            (a - 1.0) * c -
                            2.0 * sqrtA * alpha
                    )

                ra0 =
                    (a + 1.0) +
                        (a - 1.0) * c +
                        2.0 * sqrtA * alpha

                ra1 =
                    -2.0 * (
                        (a - 1.0) +
                            (a + 1.0) * c
                    )

                ra2 =
                    (a + 1.0) +
                        (a - 1.0) * c -
                        2.0 * sqrtA * alpha
            } else {
                rb0 = 1.0 + alpha * a
                rb1 = -2.0 * c
                rb2 = 1.0 - alpha * a

                ra0 = 1.0 + alpha / a
                ra1 = -2.0 * c
                ra2 = 1.0 - alpha / a
            }

            b0 = (rb0 / ra0).toFloat()
            b1 = (rb1 / ra0).toFloat()
            b2 = (rb2 / ra0).toFloat()
            a1 = (ra1 / ra0).toFloat()
            a2 = (ra2 / ra0).toFloat()
        }

        fun process(x: Float): Float {
            val y =
                b0 * x +
                    z1

            z1 =
                b1 * x -
                    a1 * y +
                    z2

            z2 =
                b2 * x -
                    a2 * y

            return y
        }

        fun reset() {
            z1 = 0f
            z2 = 0f
        }
    }
}
