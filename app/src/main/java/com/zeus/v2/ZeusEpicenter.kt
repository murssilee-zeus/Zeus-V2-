package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Epicenter-style bass enhancer used by Zeus Bass mode only.
 *
 * The important part is subharmonic synthesis: a controlled octave-below
 * component is generated from the real bass, then blended with the existing
 * Zeus sub/punch stage. This is what creates the physical "rrrr"/seismic
 * sensation instead of merely making the bass louder.
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

    private val bassLowL = OnePole(sampleRate)
    private val bassLowR = OnePole(sampleRate)
    private val bassFloorL = OnePole(sampleRate)
    private val bassFloorR = OnePole(sampleRate)

    private val subL = SubHarmonic(sampleRate)
    private val subR = SubHarmonic(sampleRate)

    private var exciterDrive = 1f
    private var exciterMix = 0f
    private var subMix = 0f

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

        // Harmonic exciter is now secondary. The seismic character comes
        // primarily from the synthesized octave-down component below.
        exciterDrive = 1f + amount * 1.8f + harmonics * 2.8f
        exciterMix = (amount * .06f + harmonics * .22f).coerceAtMost(.30f)

        // Strong enough to be audible, but still dynamically tied to bass.
        subMix = (amount * .34f + harmonics * .10f).coerceAtMost(.48f)

        bassLowL.configure(bass.coerceIn(70f, 150f))
        bassLowR.configure(bass.coerceIn(70f, 150f))
        bassFloorL.configure(24f)
        bassFloorR.configure(24f)

        subL.configure(sub)
        subR.configure(sub)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val inL = pcm[i] / 32768f
            val inR = pcm[i + 1] / 32768f

            var outL = lowL.process(inL)
            var outR = lowR.process(inR)

            outL = punchL.process(outL)
            outR = punchR.process(outR)

            // Isolate useful bass energy for both synthesis stages.
            val lowBandL = bassLowL.process(inL)
            val lowBandR = bassLowR.process(inR)
            val bassL = lowBandL - bassFloorL.process(lowBandL)
            val bassR = lowBandR - bassFloorR.process(lowBandR)

            // PRIMARY Epicenter-like effect: synthesize an octave below the
            // detected bass. This is the "seismic" part, not a fixed EQ boost.
            outL += subL.process(bassL) * subMix
            outR += subR.process(bassR) * subMix

            // Secondary nonlinear harmonics add texture to the new sub without
            // replacing the original Zeus bass character.
            val shapedL = tanh((bassL * exciterDrive).toDouble()).toFloat()
            val shapedR = tanh((bassR * exciterDrive).toDouble()).toFloat()

            val h2Lx = shapedL * shapedL
            val h2Rx = shapedR * shapedR

            val excitedL =
                h2L.process(h2Lx) * exciterMix +
                h3L.process(shapedL * shapedL * shapedL) * (exciterMix * .65f)

            val excitedR =
                h2R.process(h2Rx) * exciterMix +
                h3R.process(shapedR * shapedR * shapedR) * (exciterMix * .65f)

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
        bassLowL.reset(); bassLowR.reset()
        bassFloorL.reset(); bassFloorR.reset()
        subL.reset(); subR.reset()
    }

    private fun toShort(x: Float): Short =
        (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    /**
     * Lightweight octave-down generator.
     *
     * It measures bass zero-crossing periods and drives a synchronized sine
     * oscillator at half the detected frequency. The envelope follows bass
     * energy, so there is no constant artificial drone.
     */
    private class SubHarmonic(private val sr: Int) {
        private var targetHz = 42f
        private var smoothedHz = 42f
        private var phase = 0.0
        private var previous = 0f
        private var samplesSinceCross = 0
        private var envelope = 0f
        private var envelopeA = .01f
        private var mixLimit = 1f

        fun configure(target: Float) {
            targetHz = target.coerceIn(18f, 50f)
            mixLimit = .85f
            envelopeA = (1f - kotlin.math.exp((-2.0 * PI * 14f / sr))).toFloat()
        }

        fun process(x: Float): Float {
            samplesSinceCross++

            if (previous <= 0f && x > 0f && samplesSinceCross > sr / 140) {
                val measuredHz = (sr.toFloat() / samplesSinceCross.toFloat())
                    .coerceIn(25f, 120f)

                // Slow tracking prevents unstable pitch jumps on complex mixes.
                smoothedHz = smoothedHz * .82f + measuredHz * .18f
                samplesSinceCross = 0

                // Reset phase at each detected fundamental cycle. The oscillator
                // runs at half that frequency, therefore completing one cycle
                // every two bass cycles.
                phase = 0.0
            }

            previous = x

            val targetEnvelope = abs(x)
            envelope += envelopeA * (targetEnvelope - envelope)

            val frequency = smoothedHz * .5f
            phase += (2.0 * PI * frequency / sr)
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            // Envelope + soft saturation gives the sub some physical density.
            val shaped = tanh((sin(phase) * envelope * 3.2f).toDouble()).toFloat()
            return shaped * mixLimit
        }

        fun reset() {
            previous = 0f
            samplesSinceCross = 0
            envelope = 0f
            smoothedHz = targetHz
            phase = 0.0
        }
    }

    private class OnePole(private val sr: Int) {
        private var a = 0.01f
        private var z = 0f

        fun configure(freq: Float) {
            val f = freq.coerceIn(5f, sr * .45f)
            a = (1f - kotlin.math.exp((-2.0 * PI * f / sr))).toFloat()
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
            val w = 2.0 * PI * f / sr
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
