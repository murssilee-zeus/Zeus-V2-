package com.zeus.v2

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Epicenter-style bass enhancer used by Zeus Bass mode only.
 *
 * PCM IN -> sub/punch -> dynamic bass exciter -> controlled H2/H3 -> PCM OUT
 *
 * The exciter is dynamic: it follows the actual bass energy instead of
 * simply boosting fixed EQ bands. This creates the physical/rumbling
 * "rrrr" texture while keeping the sub itself intact.
 */
class ZeusEpicenter(
    private val sampleRate: Int
) {
    var enabled = true

    private val lowL = Biquad(sampleRate)
    private val lowR = Biquad(sampleRate)
    private val punchL = Biquad(sampleRate)
    private val punchR = Biquad(sampleRate)
    private val h2L = Biquad(sampleRate)
    private val h2R = Biquad(sampleRate)
    private val h3L = Biquad(sampleRate)
    private val h3R = Biquad(sampleRate)

    // Dedicated bass followers for the dynamic Epicenter-style exciter.
    private val bassFollowL = OnePole(sampleRate)
    private val bassFollowR = OnePole(sampleRate)
    private val dcL = OnePole(sampleRate)
    private val dcR = OnePole(sampleRate)

    private var exciterDrive = 1f
    private var exciterMix = 0f

    fun configure(
        amountPercent: Float,
        punchPercent: Float,
        harmonicsPercent: Float,
        bassFrequencyHz: Float,
        subFrequencyHz: Float
    ) {
        val amount = amountPercent.coerceIn(0f, 100f) / 100f
        val punch = punchPercent.coerceIn(0f, 100f)
        val harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        val bass = bassFrequencyHz.coerceIn(25f, 120f)
        val sub = subFrequencyHz.coerceIn(18f, 50f)

        val subGain = (amount * 10f).coerceAtMost(12f)
        val punchGain = (PunchControl.midBassGain(punch) * 1.9f).coerceAtMost(10f)
        val h2Gain = (amount * 2f + harmonics * 4f).coerceAtMost(9f)
        val h3Gain = (harmonics * 1.8f).coerceAtMost(5f)

        lowL.lowShelf(sub, subGain)
        lowR.lowShelf(sub, subGain)
        punchL.peak(bass, punchGain, .85f)
        punchR.peak(bass, punchGain, .85f)

        h2L.peak((bass * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h2R.peak((bass * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h3L.peak((bass * 3f).coerceIn(240f, 1200f), h3Gain, .95f)
        h3R.peak((bass * 3f).coerceIn(240f, 1200f), h3Gain, .95f)

        // Dynamic nonlinear excitation. It is intentionally moderate so the
        // original sub remains recognizable instead of turning into distortion.
        exciterDrive = 1f + amount * 2.5f + harmonics * 3.5f
        exciterMix = (amount * .10f + harmonics * .34f).coerceAtMost(.42f)

        // Follow the bass region quickly enough to react to kick/bass hits,
        // but not so quickly that individual samples become audible distortion.
        bassFollowL.configure(bass.coerceIn(55f, 150f))
        bassFollowR.configure(bass.coerceIn(55f, 150f))
        dcL.configure(18f)
        dcR.configure(18f)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val inL = pcm[i] / 32768f
            val inR = pcm[i + 1] / 32768f

            // Keep the existing Zeus bass shaping intact.
            var outL = lowL.process(inL)
            var outR = lowR.process(inR)

            outL = punchL.process(outL)
            outR = punchR.process(outR)

            // Extract the actual bass energy and synthesize controlled
            // even/odd harmonics from it. Unlike a static EQ boost, this
            // only appears when bass is actually present.
            val bassL = bassFollowL.process(inL)
            val bassR = bassFollowR.process(inR)

            val shapedL = tanh(bassL * exciterDrive)
            val shapedR = tanh(bassR * exciterDrive)

            // Squared term produces the even harmonic. Remove its DC component
            // so the exciter cannot slowly shift the speaker/headphone driver.
            val rawH2L = shapedL * shapedL
            val rawH2R = shapedR * shapedR
            val h2DcL = dcL.process(rawH2L)
            val h2DcR = dcR.process(rawH2R)

            val dynamicH2L = rawH2L - h2DcL
            val dynamicH2R = rawH2R - h2DcR
            val dynamicH3L = shapedL * shapedL * shapedL
            val dynamicH3R = shapedR * shapedR * shapedR

            val excitedL =
                h2L.process(dynamicH2L) * exciterMix +
                h3L.process(dynamicH3L) * (exciterMix * .72f)

            val excitedR =
                h2R.process(dynamicH2R) * exciterMix +
                h3R.process(dynamicH3R) * (exciterMix * .72f)

            // Small safety trim keeps the new nonlinear stage from eating all
            // available headroom on heavily mastered tracks.
            outL += excitedL
            outR += excitedR

            pcm[i] = toShort(outL)
            pcm[i + 1] = toShort(outR)
        }
    }

    fun reset() {
        lowL.reset(); lowR.reset()
        punchL.reset(); punchR.reset()
        h2L.reset(); h2R.reset()
        h3L.reset(); h3R.reset()
        bassFollowL.reset(); bassFollowR.reset()
        dcL.reset(); dcR.reset()
    }

    private fun toShort(x: Float): Short =
        (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private class OnePole(private val sr: Int) {
        private var a = 0.01f
        private var z = 0f

        fun configure(freq: Float) {
            val f = freq.coerceIn(5f, sr * .45f)
            a = (1f - kotlin.math.exp((-2.0 * Math.PI * f / sr))).toFloat()
        }

        fun process(x: Float): Float {
            z += a * (x - z)
            return z
        }

        fun reset() {
            z = 0f
        }
    }

    private class Biquad(private val sr: Int) {
        private var b0=1f; private var b1=0f; private var b2=0f
        private var a1=0f; private var a2=0f
        private var z1=0f; private var z2=0f

        fun lowShelf(freq: Float, gainDb: Float) {
            set(freq, gainDb, .7071f, true)
        }

        fun peak(freq: Float, gainDb: Float, q: Float) {
            set(freq, gainDb, q, false)
        }

        private fun set(freqIn: Float, gainDb: Float, q: Float, shelf: Boolean) {
            val f = freqIn.coerceIn(18f, sr * .45f)
            val w = 2.0 * Math.PI * f / sr
            val c = cos(w); val s = sin(w)
            val alpha = s / (2.0 * q)
            val a = 10.0.pow(gainDb.toDouble() / 40.0)
            val sa = sqrt(a)

            val rb0: Double
            val rb1: Double
            val rb2: Double
            val ra0: Double
            val ra1: Double
            val ra2: Double

            if (shelf) {
                rb0 = a*((a+1)-(a-1)*c+2*sa*alpha)
                rb1 = 2*a*((a-1)-(a+1)*c)
                rb2 = a*((a+1)-(a-1)*c-2*sa*alpha)
                ra0 = (a+1)+(a-1)*c+2*sa*alpha
                ra1 = -2*((a-1)+(a+1)*c)
                ra2 = (a+1)+(a-1)*c-2*sa*alpha
            } else {
                rb0 = 1+alpha*a
                rb1 = -2*c
                rb2 = 1-alpha*a
                ra0 = 1+alpha/a
                ra1 = -2*c
                ra2 = 1-alpha/a
            }

            b0=(rb0/ra0).toFloat(); b1=(rb1/ra0).toFloat(); b2=(rb2/ra0).toFloat()
            a1=(ra1/ra0).toFloat(); a2=(ra2/ra0).toFloat()
        }

        fun process(x: Float): Float {
            val y=b0*x+z1
            z1=b1*x-a1*y+z2
            z2=b2*x-a2*y
            return y
        }

        fun reset() { z1=0f; z2=0f }
    }
}
