package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.log10
import kotlin.math.tanh
import kotlin.math.exp

/**
 * Zeus Epicenter v2: independent bass excitation.
 *
 * The detector is fed by the SUB crossover branch before Sub/Sismo. Its
 * generated mono signal is mixed into that branch independently; Sub/Sismo
 * never gates, scales, or enables this generator.
 *
 * Original implementation inspired by the audible behavior and public control
 * concepts of car-audio bass processors. No proprietary native algorithm copied.
 */
class ZeusEpicenter(private val sampleRate: Int) {
    @Volatile var enabled = true

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
    private var targetHz = 36f
    private var sweepHz = 72f
    private var oscillatorHz = 36f

    private var env55 = 0f
    private var env80 = 0f
    private var env110 = 0f
    private var subEnvelope = 0f
    private var holdSamples = 0
    private var holdEnvelope = 0f
    private var phase = 0.0
    private var phase2 = 0.0
    private var phase3 = 0.0
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
        targetHz = frequencyHz.coerceIn(18f, 63f)
        this.sweepHz = sweepHz.coerceIn(targetHz, 140f)
        oscillatorHz = targetHz

        detector55.bandPass(48f, 0.72f)
        detector80.bandPass(72f, 0.82f)
        detector110.bandPass(100f, 0.78f)
        subLowPass.lowPass((targetHz * 1.75f).coerceIn(48f, 110f), 0.707f)
        subHighPass.highPass(18f, 0.707f)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled || amount <= 0f) {
            lastSubRmsDbfs = -120f
            return
        }
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (n < 2) return
        var energy = 0.0
        var count = 0
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val attack = (1.0 - exp(-1.0 / (sr * 0.006))).toFloat()
        val detectorRelease = (1.0 - exp(-1.0 / (sr * 0.100))).toFloat()
        val subRelease = (1.0 - exp(-1.0 / (sr * (0.28 + depth * 0.38)))).toFloat()
        val holdAttack = (1.0 - exp(-1.0 / (sr * 0.010))).toFloat()
        val holdRelease = (1.0 - exp(-1.0 / (sr * (0.22 + depth * 0.16)))).toFloat()
        val voiceCoeff = (1.0 - exp(-1.0 / (sr * 0.050))).toFloat()
        val holdLength = (sr * (0.06 + depth * 0.16)).toInt()
        val sensitivity = 34f + drive * 126f
        val outputGain = (0.30f + amount * 2.65f) * (0.65f + drive * 0.75f) *
            (0.75f + depth * 0.50f) * (if (headphoneMode) 0.48f else 1f)

        for (i in 0 until n step 2) {
            val left = pcm[i] / 32768f
            val right = pcm[i + 1] / 32768f
            val mono = (left + right) * 0.5f
            val side = abs(left - right) * 0.5f

            val b1 = abs(detector55.process(mono))
            val b2 = abs(detector80.process(mono))
            val b3 = abs(detector110.process(mono))
            env55 += (if (b1 > env55) attack else detectorRelease) * (b1 - env55)
            env80 += (if (b2 > env80) attack else detectorRelease) * (b2 - env80)
            env110 += (if (b3 > env110) attack else detectorRelease) * (b3 - env110)
            val detector = env55 * 0.50f + env80 * 0.34f + env110 * 0.16f

            if (detector > 0.00012f) holdSamples = holdLength
            else if (holdSamples > 0) holdSamples--
            val holdTarget = if (holdSamples > 0) 1f else 0.55f
            holdEnvelope += (if (holdTarget > holdEnvelope) holdAttack else holdRelease) *
                (holdTarget - holdEnvelope)

            // Drive is detector sensitivity, not a dependency on Sub/Sismo.
            val driven = (detector * sensitivity).coerceIn(0f, 1f)
            val shaped = driven * (0.35f + 0.65f * driven)
            val targetEnvelope = shaped * holdEnvelope
            subEnvelope += (if (targetEnvelope > subEnvelope) attack else subRelease) *
                (targetEnvelope - subEnvelope)

            voiceEnvelope += voiceCoeff * (side - voiceEnvelope)
            val stereoGuard = (1f - (voiceEnvelope / (detector + 0.018f)).coerceIn(0f, 0.65f) * 0.12f)
            val sweepTarget = targetHz + (sweepHz - targetHz) * subEnvelope
            oscillatorHz += 0.0035f * (sweepTarget - oscillatorHz)

            val increment = 2.0 * PI * oscillatorHz / sr
            phase += increment
            phase2 += increment * 2.0
            phase3 += increment * 3.0
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            if (phase2 >= 2.0 * PI) phase2 -= 2.0 * PI
            if (phase3 >= 2.0 * PI) phase3 -= 2.0 * PI

            val fundamental = sin(phase).toFloat()
            val h2 = sin(phase2).toFloat()
            val h3 = sin(phase3).toFloat()
            val generated = fundamental + h2 * harmonics * 0.24f + h3 * harmonics * 0.10f
            val body = subLowPass.process(generated * subEnvelope)
            val sub = subHighPass.process(body) * outputGain * stereoGuard
            energy += sub.toDouble() * sub
            count++

            // Soft clip only the generated component, then share headroom between L/R.
            val add = tanh(sub.toDouble()).toFloat()
            val safeAdd = limitCenteredSubToHeadroom(left, right, add)
            pcm[i] = toShort(left + safeAdd)
            pcm[i + 1] = toShort(right + safeAdd)
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
        if (n < 2) return
        var energy = 0.0
        var count = 0
        val sr = sampleRate.coerceAtLeast(8000).toDouble()
        val attack = (1.0 - exp(-1.0 / (sr * 0.006))).toFloat()
        val detectorRelease = (1.0 - exp(-1.0 / (sr * 0.100))).toFloat()
        val subRelease = (1.0 - exp(-1.0 / (sr * (0.28 + depth * 0.38)))).toFloat()
        val holdAttack = (1.0 - exp(-1.0 / (sr * 0.010))).toFloat()
        val holdRelease = (1.0 - exp(-1.0 / (sr * (0.22 + depth * 0.16)))).toFloat()
        val voiceCoeff = (1.0 - exp(-1.0 / (sr * 0.050))).toFloat()
        val holdLength = (sr * (0.06 + depth * 0.16)).toInt()
        val sensitivity = 34f + drive * 126f
        val outputGain = (0.30f + amount * 2.65f) * (0.65f + drive * 0.75f) *
            (0.75f + depth * 0.50f) * (if (headphoneMode) 0.48f else 1f)

        for (i in 0 until n step 2) {
            val left = pcm[i]
            val right = pcm[i + 1]
            val mono = (left + right) * 0.5f
            val side = abs(left - right) * 0.5f

            val b1 = abs(detector55.process(mono))
            val b2 = abs(detector80.process(mono))
            val b3 = abs(detector110.process(mono))
            env55 += (if (b1 > env55) attack else detectorRelease) * (b1 - env55)
            env80 += (if (b2 > env80) attack else detectorRelease) * (b2 - env80)
            env110 += (if (b3 > env110) attack else detectorRelease) * (b3 - env110)
            val detector = env55 * 0.50f + env80 * 0.34f + env110 * 0.16f

            if (detector > 0.00012f) holdSamples = holdLength
            else if (holdSamples > 0) holdSamples--
            val holdTarget = if (holdSamples > 0) 1f else 0.55f
            holdEnvelope += (if (holdTarget > holdEnvelope) holdAttack else holdRelease) *
                (holdTarget - holdEnvelope)

            val driven = (detector * sensitivity).coerceIn(0f, 1f)
            val shaped = driven * (0.35f + 0.65f * driven)
            val targetEnvelope = shaped * holdEnvelope
            subEnvelope += (if (targetEnvelope > subEnvelope) attack else subRelease) *
                (targetEnvelope - subEnvelope)

            voiceEnvelope += voiceCoeff * (side - voiceEnvelope)
            val stereoGuard = 1f - (voiceEnvelope / (detector + 0.018f)).coerceIn(0f, 0.65f) * 0.12f
            val sweepTarget = targetHz + (sweepHz - targetHz) * subEnvelope
            oscillatorHz += 0.0035f * (sweepTarget - oscillatorHz)

            val increment = 2.0 * PI * oscillatorHz / sr
            phase += increment
            phase2 += increment * 2.0
            phase3 += increment * 3.0
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
            if (phase2 >= 2.0 * PI) phase2 -= 2.0 * PI
            if (phase3 >= 2.0 * PI) phase3 -= 2.0 * PI

            val fundamental = sin(phase).toFloat()
            val h2 = sin(phase2).toFloat()
            val h3 = sin(phase3).toFloat()
            val generated = fundamental + h2 * harmonics * 0.24f + h3 * harmonics * 0.10f
            val body = subLowPass.process(generated * subEnvelope)
            val sub = subHighPass.process(body) * outputGain * stereoGuard
            energy += sub.toDouble() * sub
            count++

            val add = tanh(sub.toDouble()).toFloat()
            val safeAdd = limitCenteredSubToHeadroom(left, right, add)
            pcm[i] = (left + safeAdd).coerceIn(-1f, 1f)
            pcm[i + 1] = (right + safeAdd).coerceIn(-1f, 1f)
        }
        lastSubRmsDbfs = if (count > 0 && energy > 0.0)
            (20.0 * log10(sqrt(energy / count))).toFloat().coerceIn(-120f, 0f)
        else -120f
    }

    private fun limitCenteredSubToHeadroom(left: Float, right: Float, addition: Float): Float {
        val positiveHeadroom = minOf(0.995f - left, 0.995f - right).coerceAtLeast(0f)
        val negativeHeadroom = maxOf(-0.995f - left, -0.995f - right).coerceAtMost(0f)
        return addition.coerceIn(negativeHeadroom, positiveHeadroom)
    }

    fun reset() {
        detector55.reset(); detector80.reset(); detector110.reset()
        subLowPass.reset(); subHighPass.reset()
        env55 = 0f; env80 = 0f; env110 = 0f
        subEnvelope = 0f; holdSamples = 0; holdEnvelope = 0f
        phase = 0.0; phase2 = 0.0; phase3 = 0.0
        oscillatorHz = targetHz
        voiceEnvelope = 0f
        lastSubRmsDbfs = -120f
    }

    private fun toShort(value: Float): Short =
        (value.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort()
}
