package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Zeus Bass low-frequency reconstruction stage.
 *
 * This is intentionally not a simple bass exciter. It reconstructs a
 * perceptual low-frequency fundamental from bass energy already present in
 * the PCM signal, then adds controlled subharmonic and harmonic components.
 * The goal is pressure/depth/"rrrr" sensation without simply turning up EQ.
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

    private var epicenterAmount = 0f
    private var exciterDrive = 1f
    private var exciterMix = 0f
    private var subMix = 0f

    /**
     * Epicenter is deliberately independent from Bass Amount, Punch and
     * Bass Harmonics. Those controls shape Zeus Bass; this stage creates
     * the additional "pressure / rrrrr / seismic" transformation.
     */
    fun configure(
        epicenterPercent: Float,
        drivePercent: Float,
        depthPercent: Float,
        harmonicsPercent: Float,
        frequencyHz: Float
    ) {
        epicenterAmount = epicenterPercent.coerceIn(0f, 100f) / 100f
        val drive = drivePercent.coerceIn(0f, 100f) / 100f
        val depth = depthPercent.coerceIn(0f, 100f) / 100f
        val harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        val target = frequencyHz.coerceIn(18f, 80f)

        if (epicenterAmount <= 0f) {
            exciterDrive = 1f
            exciterMix = 0f
            subMix = 0f
        } else {
            // Amount is the master wet control. Drive controls nonlinear
            // density; Depth controls how aggressively the sub stage follows.
            exciterDrive = 1f + drive * 5f + epicenterAmount * 1.5f
            exciterMix = (epicenterAmount * (0.10f + drive * .20f) +
                harmonics * .22f).coerceAtMost(.55f)
            subMix = (epicenterAmount * (.18f + depth * .62f)).coerceAtMost(.72f)
        }

        // The target frequency is the center of the transformation, not a
        // fixed bass EQ. This keeps Epicenter perceptually independent.
        val center = target.coerceIn(25f, 120f)
        val subTarget = (target * .72f).coerceIn(18f, 50f)
        lowL.lowShelf(subTarget, (epicenterAmount * depth * 4f).coerceAtMost(4f))
        lowR.lowShelf(subTarget, (epicenterAmount * depth * 4f).coerceAtMost(4f))
        punchL.peak(center, (epicenterAmount * drive * 4f).coerceAtMost(4f), .85f)
        punchR.peak(center, (epicenterAmount * drive * 4f).coerceAtMost(4f), .85f)

        val h2Gain = (epicenterAmount * drive * 2f + harmonics * 3.5f).coerceAtMost(6f)
        val h3Gain = (harmonics * 1.8f + epicenterAmount * drive * 1.2f).coerceAtMost(4f)
        h2L.peak((center * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h2R.peak((center * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h3L.peak((center * 3f).coerceIn(240f, 1200f), h3Gain, .95f)
        h3R.peak((center * 3f).coerceIn(240f, 1200f), h3Gain, .95f)

        bassLowL.configure(center.coerceIn(55f, 150f))
        bassLowR.configure(center.coerceIn(55f, 150f))
        bassFloorL.configure(24f)
        bassFloorR.configure(24f)

        subL.configure(subTarget)
        subR.configure(subTarget)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled || epicenterAmount <= 0f) return

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
     * Perceptual low-frequency reconstruction.
     *
     * Instead of a fixed sub oscillator, this stage follows the bass already
     * present in the signal, estimates its fundamental, and synthesizes a
     * controlled component below it. Phase is continuous, so the result does
     * not become a periodic drone or a clicky reset on every bass cycle.
     */
    private class SubHarmonic(private val sr: Int) {
        private var targetHz = 36f
        private var trackedHz = 36f
        private var phase = 0.0
        private var previous = 0f
        private var samplesSinceCross = 0
        private var envelope = 0f
        private var envelopeA = .01f
        private var releaseA = .0025f
        private var mixLimit = 1f

        fun configure(target: Float) {
            targetHz = target.coerceIn(18f, 50f)
            trackedHz = targetHz
            mixLimit = .92f
            envelopeA = (1f - kotlin.math.exp((-2.0 * PI * 18f / sr))).toFloat()
            releaseA = (1f - kotlin.math.exp((-2.0 * PI * 7f / sr))).toFloat()
        }

        fun process(x: Float): Float {
            samplesSinceCross++

            // Estimate the actual bass period. We use the real incoming bass
            // instead of forcing the selected UI frequency onto every track.
            if (previous <= 0f && x > 0f && samplesSinceCross > sr / 150) {
                val measuredHz = (sr.toFloat() / samplesSinceCross.toFloat())
                    .coerceIn(22f, 110f)
                trackedHz = trackedHz * .88f + measuredHz * .12f
                samplesSinceCross = 0
            }
            previous = x

            // When the mix has no clean fundamental, gently fall back toward
            // the selected reconstruction frequency rather than producing
            // random low-frequency motion.
            val confidence = (abs(x) * 8f).coerceIn(0f, 1f)
            val reconstructionHz = trackedHz * confidence + targetHz * (1f - confidence)

            val targetEnvelope = abs(x).coerceIn(0f, 1f)
            val envCoeff = if (targetEnvelope > envelope) envelopeA else releaseA
            envelope += envCoeff * (targetEnvelope - envelope)

            // Reconstruct below the detected fundamental. A small target bias
            // keeps the effect centered around the user's selected frequency.
            val octaveDown = (reconstructionHz * .5f).coerceIn(18f, 55f)
            phase += 2.0 * PI * octaveDown / sr
            while (phase >= 2.0 * PI) phase -= 2.0 * PI

            // Fundamental body + a very small second partial. This makes the
            // reconstructed bass remain audible on small speakers without
            // turning the stage into a conventional harmonic exciter.
            val fundamental = sin(phase)
            val body = fundamental + sin(phase * 2.0) * .12
            val shaped = tanh((body * envelope * 4.2f).toDouble()).toFloat()
            return shaped * mixLimit
        }

        fun reset() {
            previous = 0f
            samplesSinceCross = 0
            envelope = 0f
            trackedHz = targetHz
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
