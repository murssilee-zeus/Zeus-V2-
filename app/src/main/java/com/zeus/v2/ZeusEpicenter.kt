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

    private val bassBandL = Biquad(sampleRate)
    private val bassBandR = Biquad(sampleRate)
    // Parallel resonator inspired by the WEcho approach, independently implemented.
    private val resonatorL = Biquad(sampleRate)
    private val resonatorR = Biquad(sampleRate)
    private var resonatorMix = 0f
    private var dynamicBassAmount = 0f
    private var bassEnvelopeL = 0f
    private var bassEnvelopeR = 0f

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
        frequencyHz: Float,
        sweepHz: Float
    ) {
        epicenterAmount = epicenterPercent.coerceIn(0f, 100f) / 100f
        val drive = drivePercent.coerceIn(0f, 100f) / 100f
        val depth = depthPercent.coerceIn(0f, 100f) / 100f
        val harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        val target = frequencyHz.coerceIn(18f, 65f)
        val sweep = sweepHz.coerceIn(40f, 140f)

        if (epicenterAmount <= 0f) {
            exciterDrive = 1f
            exciterMix = 0f
            subMix = 0f
            resonatorMix = 0f
            dynamicBassAmount = 0f
        } else {
            // Amount is the master wet control. Drive controls nonlinear
            // density; Depth controls how aggressively the sub stage follows.
            exciterDrive = 1f + drive * 6f + epicenterAmount * 2.5f
            exciterMix = (epicenterAmount * (0.18f + drive * .30f) +
                harmonics * .22f).coerceAtMost(.70f)
            subMix = (epicenterAmount * (.45f + depth * .45f)).coerceAtMost(.90f)
            // A restrained parallel resonant band adds body without turning the
            // entire low end into a static EQ shelf. Depth sets resonance/Q.
            resonatorMix = (epicenterAmount * (.10f + depth * .22f)).coerceAtMost(.32f)
            dynamicBassAmount = (epicenterAmount * depth * .30f).coerceAtMost(.30f)
        }

        // The target frequency is the center of the transformation, not a
        // fixed bass EQ. This keeps Epicenter perceptually independent.
        val center = target.coerceIn(25f, 120f)
        // Sweep is the desired reconstructed-sub range. Pitch tracking will
        // override it when a stable source fundamental is detected.
        val subTarget = target.coerceIn(18f, 65f)
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

        // SWEEP selects the source-bass band to analyze. FREQUENCY remains
        // the desired sub output/fallback; they are independent controls.
        bassBandL.bandPass(sweep, 0.85f)
        bassBandR.bandPass(sweep, 0.85f)
        resonatorL.bandPass(sweep, 1.0f + depth * 1.5f)
        resonatorR.bandPass(sweep, 1.0f + depth * 1.5f)

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
            val bassL = bassBandL.process(inL)
            val bassR = bassBandR.process(inR)

            // WEcho-inspired dynamic low-end: quieter bass passages receive a
            // little more synthesized-sub level, while loud passages are kept
            // in check. The envelope is smoothed to avoid pumping/chattering.
            val envCoeff = 0.0015f
            bassEnvelopeL += envCoeff * (kotlin.math.abs(bassL) - bassEnvelopeL)
            bassEnvelopeR += envCoeff * (kotlin.math.abs(bassR) - bassEnvelopeR)
            val dynamicL = 1f + dynamicBassAmount * (1f - bassEnvelopeL * 12f).coerceIn(0f, 1f)
            val dynamicR = 1f + dynamicBassAmount * (1f - bassEnvelopeR * 12f).coerceIn(0f, 1f)

            // Resonator adds a controlled parallel band at the selected sweep
            // frequency, inspired by WEcho's Bass Resonator, not copied from it.
            outL += resonatorL.process(inL) * resonatorMix
            outR += resonatorR.process(inR) * resonatorMix

            // PRIMARY Epicenter-like effect: synthesize an octave below the
            // detected bass. This is the "seismic" part, not a fixed EQ boost.
            outL += subL.process(bassL) * subMix * dynamicL
            outR += subR.process(bassR) * subMix * dynamicR

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


    /** 32-bit float processing path; no intermediate 16-bit quantization. */
    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled || epicenterAmount <= 0f) return

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val inL = pcm[i]
            val inR = pcm[i + 1]

            var outL = lowL.process(inL)
            var outR = lowR.process(inR)

            outL = punchL.process(outL)
            outR = punchR.process(outR)

            // Isolate useful bass energy for both synthesis stages.
            val bassL = bassBandL.process(inL)
            val bassR = bassBandR.process(inR)

            // WEcho-inspired dynamic low-end: quieter bass passages receive a
            // little more synthesized-sub level, while loud passages are kept
            // in check. The envelope is smoothed to avoid pumping/chattering.
            val envCoeff = 0.0015f
            bassEnvelopeL += envCoeff * (kotlin.math.abs(bassL) - bassEnvelopeL)
            bassEnvelopeR += envCoeff * (kotlin.math.abs(bassR) - bassEnvelopeR)
            val dynamicL = 1f + dynamicBassAmount * (1f - bassEnvelopeL * 12f).coerceIn(0f, 1f)
            val dynamicR = 1f + dynamicBassAmount * (1f - bassEnvelopeR * 12f).coerceIn(0f, 1f)

            // Resonator adds a controlled parallel band at the selected sweep
            // frequency, inspired by WEcho's Bass Resonator, not copied from it.
            outL += resonatorL.process(inL) * resonatorMix
            outR += resonatorR.process(inR) * resonatorMix

            // PRIMARY Epicenter-like effect: synthesize an octave below the
            // detected bass. This is the "seismic" part, not a fixed EQ boost.
            outL += subL.process(bassL) * subMix * dynamicL
            outR += subR.process(bassR) * subMix * dynamicR

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

            pcm[i] = outL.coerceIn(-1f, 1f)
            pcm[i + 1] = outR.coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        lowL.reset(); lowR.reset()
        punchL.reset(); punchR.reset()
        h2L.reset(); h2R.reset()
        h3L.reset(); h3R.reset()
        bassBandL.reset(); bassBandR.reset()
        resonatorL.reset(); resonatorR.reset()
        bassEnvelopeL = 0f; bassEnvelopeR = 0f
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
        // targetHz is the fallback OUTPUT frequency, not the input frequency.
        private var targetHz = 36f
        private var trackedInputHz = 72f
        private var phase = 0.0
        private var previous = 0f
        private var samplesSinceCross = 0
        private var envelope = 0f
        private var envelopeAttack = .01f
        private var envelopeRelease = .0025f
        private var stableCrossings = 0
        private var lastMeasuredHz = 0f

        private val minPeriod get() = (sr / 150f).toInt().coerceAtLeast(1)
        private val maxPeriod get() = (sr / 22f).toInt().coerceAtLeast(minPeriod + 1)

        fun configure(target: Float) {
            targetHz = target.coerceIn(18f, 65f)
            if (trackedInputHz !in 36f..130f) trackedInputHz = targetHz * 2f
            envelopeAttack =
                (1f - kotlin.math.exp((-2.0 * PI * 16f / sr))).toFloat()
            envelopeRelease =
                (1f - kotlin.math.exp((-2.0 * PI * 6.5f / sr))).toFloat()
        }

        fun process(x: Float): Float {
            samplesSinceCross++

            // Positive-going zero crossings estimate the period of the filtered
            // bass waveform. Only accept plausible bass periods and smooth them
            // across cycles so a single transient cannot retune the oscillator.
            if (previous <= 0f && x > 0f) {
                val period = samplesSinceCross
                samplesSinceCross = 0
                if (period in minPeriod..maxPeriod) {
                    val measuredHz = sr.toFloat() / period.toFloat()
                    val consistent = lastMeasuredHz <= 0f ||
                        (measuredHz / lastMeasuredHz).let { it in .62f..1.62f }

                    if (consistent) {
                        trackedInputHz = if (stableCrossings == 0) {
                            measuredHz
                        } else {
                            trackedInputHz * .82f + measuredHz * .18f
                        }
                        stableCrossings = (stableCrossings + 1).coerceAtMost(6)
                        lastMeasuredHz = measuredHz
                    } else {
                        // Treat a sudden period jump as a new note, but require
                        // subsequent crossings before trusting it fully.
                        trackedInputHz = measuredHz
                        stableCrossings = 1
                        lastMeasuredHz = measuredHz
                    }
                } else {
                    stableCrossings = 0
                    lastMeasuredHz = 0f
                }
            }

            if (samplesSinceCross > maxPeriod) {
                stableCrossings = 0
                lastMeasuredHz = 0f
            }
            previous = x

            // Attack/release follower: the generated sub follows bass energy
            // smoothly instead of reproducing every sample's absolute value.
            val targetEnvelope = abs(x).coerceIn(0f, 1f)
            val coeff = if (targetEnvelope > envelope) envelopeAttack else envelopeRelease
            envelope += coeff * (targetEnvelope - envelope)

            // Trust the pitch tracker only after several consistent cycles.
            // Otherwise Sweep supplies a predictable fallback sub frequency.
            val trackingConfidence = (stableCrossings / 3f).coerceIn(0f, 1f)
            val trackedOctaveDown = (trackedInputHz * .5f).coerceIn(18f, 65f)
            val outputHz =
                targetHz * (1f - trackingConfidence) +
                    trackedOctaveDown * trackingConfidence

            phase += 2.0 * PI * outputHz / sr
            while (phase >= 2.0 * PI) phase -= 2.0 * PI

            // Add a restrained second partial for translation to small speakers.
            // The fundamental remains the dominant component.
            val body = sin(phase) + sin(phase * 2.0) * .10
            return tanh((body * envelope * 5.0).toDouble()).toFloat()
        }

        fun reset() {
            previous = 0f
            samplesSinceCross = 0
            envelope = 0f
            trackedInputHz = targetHz * 2f
            stableCrossings = 0
            lastMeasuredHz = 0f
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

        fun bandPass(freqIn: Float, qIn: Float) {
            val f = freqIn.coerceIn(18f, sr * .45f)
            val q = qIn.coerceIn(.3f, 4f)
            val w = 2.0 * PI * f / sr
            val c = cos(w)
            val alpha = sin(w) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = (alpha / a0).toFloat()
            b1 = 0f
            b2 = (-alpha / a0).toFloat()
            a1 = (-2.0 * c / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
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
