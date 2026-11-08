package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.log10
import kotlin.math.tanh

/**
 * Zeus Epicenter: independent automotive-style low-frequency exciter.
 *
 * Detects energy in three bass bands, follows its envelope, and drives a
 * centered sub oscillator with a slow release/hold. This is an original DSP
 * implementation inspired by the audible behavior of car-audio Epicenter
 * processors; it does not copy proprietary source code.
 */
class ZeusEpicenter(private val sampleRate: Int) {
    var enabled = true

    @Volatile var lastSubRmsDbfs: Float = -120f
        private set

    @Volatile
    var headphoneMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                reset()
            }
        }

    private val detector55 = Biquad(sampleRate)
    private val detector80 = Biquad(sampleRate)
    private val detector110 = Biquad(sampleRate)
    private val subLowPass = Biquad(sampleRate)
    private val subHighPass = Biquad(sampleRate)

    private var amount = 0f
    private var drive = 0f
    private var depth = 0f
    private var harmonics = 0f
    private var targetHz = 34f
    private var sweepHz = 80f

    private var env55 = 0f
    private var env80 = 0f
    private var env110 = 0f
    private var subEnvelope = 0f
    private var holdSamples = 0
    private var phase = 0.0
    private var oscillator = 0f
    private var previousMono = 0f
    private var voiceEnvelope = 0f

    fun configure(
        epicenterPercent: Float,
        drivePercent: Float,
        depthPercent: Float,
        harmonicsPercent: Float,
        frequencyHz: Float,
        sweepHz: Float
    ) {
        amount = epicenterPercent.coerceIn(0f, 100f) / 100f
        drive = drivePercent.coerceIn(0f, 100f) / 100f
        depth = depthPercent.coerceIn(0f, 100f) / 100f
        harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        targetHz = frequencyHz.coerceIn(27f, 63f)
        this.sweepHz = sweepHz.coerceIn(50f, 140f)

        detector55.bandPass(60f, 0.85f)
        detector80.bandPass(82f, 0.9f)
        detector110.bandPass(108f, 0.8f)
        subLowPass.lowPass((targetHz * 1.65f).coerceIn(48f, 105f), 0.707f)
        subHighPass.highPass(18f, 0.707f)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled || amount <= 0f) {
            lastSubRmsDbfs = -120f
            return
        }
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        var energy = 0.0
        var count = 0
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val attack = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.008))).toFloat()
        val release = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.185))).toFloat()
        val subRelease = (1.0 - kotlin.math.exp(-1.0 / (sr * (0.24 + depth * 0.30)))).toFloat()
        val voiceCoeff = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.045))).toFloat()
        val holdLength = (sr * (0.045 + depth * 0.11)).toInt()
        val gain = (0.62f + amount * 1.18f + drive * 0.62f) *
            (if (headphoneMode) 0.52f else 1f)

        for (i in 0 until n step 2) {
            val left = pcm[i] / 32768f
            val right = pcm[i + 1] / 32768f
            val mono = (left + right) * 0.5f
            val side = abs(left - right) * 0.5f

            val b1 = abs(detector55.process(mono))
            val b2 = abs(detector80.process(mono))
            val b3 = abs(detector110.process(mono))
            env55 += (if (b1 > env55) attack else release) * (b1 - env55)
            env80 += (if (b2 > env80) attack else release) * (b2 - env80)
            env110 += (if (b3 > env110) attack else release) * (b3 - env110)

            val detector = (env55 * 0.52f + env80 * 0.34f + env110 * 0.14f)
            if (detector > 0.00035f) holdSamples = holdLength
            else if (holdSamples > 0) holdSamples--

            val hold = if (holdSamples > 0) 1f else 0.68f
            val detectorDrive = (detector * (22.0f + drive * 30.0f)).coerceIn(0f, 1f)
            val targetEnvelope = detectorDrive * hold
            subEnvelope += (if (targetEnvelope > subEnvelope) attack else subRelease) *
                (targetEnvelope - subEnvelope)

            // Keep the synthesized sub centered and suppress excitation caused
            // mostly by wide stereo content rather than bass energy.
            voiceEnvelope += voiceCoeff * (side - voiceEnvelope)
            val stereoGuard = (1f - (voiceEnvelope / (detector + 0.025f)).coerceIn(0f, 0.55f) * 0.16f)
            phase += (2.0 * PI * targetHz / sr)
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            oscillator = sin(phase).toFloat()

            val body = subLowPass.process(oscillator * subEnvelope)
            val sub = subHighPass.process(body) * gain * stereoGuard *
                (0.78f + depth * 0.42f)
            energy += sub.toDouble() * sub
            count++

            // Small even-harmonic texture gives the sub a physical speaker-like
            // edge without turning it into a mid-bass boost.
            val texture = (sub * abs(sub) * harmonics * 0.22f)
            val add = tanh((sub + texture).toDouble()).toFloat()
            val outL = left + add
            val outR = right + add
            pcm[i] = toShort(tanh(outL.toDouble()).toFloat())
            pcm[i + 1] = toShort(tanh(outR.toDouble()).toFloat())
            previousMono = mono
        }
        lastSubRmsDbfs = if (count > 0 && energy > 0.0)
            (20.0 * log10(sqrt(energy / count))).toFloat().coerceIn(-120f, 0f)
        else -120f
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled || amount <= 0f) {
            lastSubRmsDbfs = -120f
            return
        }
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        var energy = 0.0
        var count = 0
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val attack = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.008))).toFloat()
        val release = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.185))).toFloat()
        val subRelease = (1.0 - kotlin.math.exp(-1.0 / (sr * (0.24 + depth * 0.30)))).toFloat()
        val voiceCoeff = (1.0 - kotlin.math.exp(-1.0 / (sr * 0.045))).toFloat()
        val holdLength = (sr * (0.045 + depth * 0.11)).toInt()
        val gain = (0.62f + amount * 1.18f + drive * 0.62f) *
            (if (headphoneMode) 0.52f else 1f)

        for (i in 0 until n step 2) {
            val left = pcm[i]
            val right = pcm[i + 1]
            val mono = (left + right) * 0.5f
            val side = abs(left - right) * 0.5f

            val b1 = abs(detector55.process(mono))
            val b2 = abs(detector80.process(mono))
            val b3 = abs(detector110.process(mono))
            env55 += (if (b1 > env55) attack else release) * (b1 - env55)
            env80 += (if (b2 > env80) attack else release) * (b2 - env80)
            env110 += (if (b3 > env110) attack else release) * (b3 - env110)

            val detector = env55 * 0.52f + env80 * 0.34f + env110 * 0.14f
            if (detector > 0.00035f) holdSamples = holdLength
            else if (holdSamples > 0) holdSamples--

            val hold = if (holdSamples > 0) 1f else 0.68f
            val detectorDrive = (detector * (22.0f + drive * 30.0f)).coerceIn(0f, 1f)
            val targetEnvelope = detectorDrive * hold
            subEnvelope += (if (targetEnvelope > subEnvelope) attack else subRelease) *
                (targetEnvelope - subEnvelope)

            voiceEnvelope += voiceCoeff * (side - voiceEnvelope)
            val stereoGuard = 1f - (voiceEnvelope / (detector + 0.025f)).coerceIn(0f, 0.55f) * 0.16f
            phase += 2.0 * PI * targetHz / sr
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            oscillator = sin(phase).toFloat()

            val body = subLowPass.process(oscillator * subEnvelope)
            val sub = subHighPass.process(body) * gain * stereoGuard *
                (0.78f + depth * 0.42f)
            energy += sub.toDouble() * sub
            count++

            val texture = sub * abs(sub) * harmonics * 0.22f
            val add = tanh((sub + texture).toDouble()).toFloat()
            pcm[i] = tanh((left + add).toDouble()).toFloat()
            pcm[i + 1] = tanh((right + add).toDouble()).toFloat()
            previousMono = mono
        }
        lastSubRmsDbfs = if (count > 0 && energy > 0.0)
            (20.0 * log10(sqrt(energy / count))).toFloat().coerceIn(-120f, 0f)
        else -120f
    }

    fun reset() {
        detector55.reset()
        detector80.reset()
        detector110.reset()
        subLowPass.reset()
        subHighPass.reset()
        env55 = 0f
        env80 = 0f
        env110 = 0f
        subEnvelope = 0f
        holdSamples = 0
        phase = 0.0
        oscillator = 0f
        previousMono = 0f
        voiceEnvelope = 0f
        lastSubRmsDbfs = -120f
    }

    private fun toShort(value: Float): Short =
        (value.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort()
}
