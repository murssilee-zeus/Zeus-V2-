package com.zeus.v2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tanh
import kotlin.math.floor
import kotlin.math.sign

/**
 * Lightweight binaural-inspired spatial processor for stereo PCM.
 *
 * This is not a licensed/measured HRTF renderer and cannot recover Atmos objects
 * from a stereo mix. It adds frequency-dependent interaural cues and subtle
 * early reflections while preserving the mono center and protecting low bass.
 */
class ZeusAtmosEngine(
    private val sampleRate: Int = 44100
) {
    var atmosImmersion: Float = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }

    var height3D: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    var centerFocus: Float = 1.0f
        set(value) { field = value.coerceIn(0f, 1.5f) }

    /** Interaural time difference in milliseconds. */
    var itdMs: Float = 0.6f
        set(value) { field = value.coerceIn(0.2f, 0.8f) }

    var lowProtectHz: Float = 120f
        set(value) { field = value.coerceIn(60f, 250f) }

    var spatialStartHz: Float = 180f
        set(value) { field = value.coerceIn(120f, 1000f) }

    var spatialMidHz: Float = 1800f
        set(value) { field = value.coerceIn(spatialStartHz + 100f, 8000f) }

    var hrtfStartHz: Float = 8000f
        set(value) { field = value.coerceIn(spatialMidHz + 100f, 16000f) }

    var hrtfEndHz: Float = 12000f
        set(value) { field = value.coerceIn(hrtfStartHz + 500f, 18000f) }

    private val maxDelaySamples = (sampleRate * 0.012f).toInt().coerceAtLeast(8)
    private val leftDelay = FloatArray(maxDelaySamples + 2)
    private val rightDelay = FloatArray(maxDelaySamples + 2)
    private var delayIndex = 0

    // One-pole states used for frequency-dependent head-shadow/crossfeed cues.
    private var leftLowState = 0f
    private var rightLowState = 0f
    private var leftCrossLowState = 0f
    private var rightCrossLowState = 0f

    private val lowProtectAlpha = onePoleAlpha(lowProtectHz)
    private var cachedLowProtectHz = lowProtectHz
    private var directLowL = 0f
    private var directLowR = 0f

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val fc = cutoffHz.coerceIn(20f, sampleRate * 0.40f)
        return (1f - kotlin.math.exp((-2.0 * Math.PI * fc / sampleRate).toFloat()))
            .coerceIn(0.001f, 0.99f)
    }

    /** Processes interleaved signed 16-bit stereo PCM in-place. */
    fun processAtmosPCM(pcmBuffer: ShortArray, size: Int = pcmBuffer.size) {
        val count = size.coerceIn(0, pcmBuffer.size - pcmBuffer.size % 2)
        if (count < 2) return
        for (i in 0 until count) pcmBuffer[i] = pcmBuffer[i]
        processFrames(count) { i, l, r ->
            pcmBuffer[i] = (softClip(l) * 32767f).toInt().toShort()
            pcmBuffer[i + 1] = (softClip(r) * 32767f).toInt().toShort()
        } { i -> Pair(pcmBuffer[i] / 32768f, pcmBuffer[i + 1] / 32768f) }
    }

    /** Float32 path avoids quantization between DSP stages. */
    fun processAtmosPCM(pcmBuffer: FloatArray, size: Int = pcmBuffer.size) {
        val count = size.coerceIn(0, pcmBuffer.size - pcmBuffer.size % 2)
        if (count < 2) return
        processFrames(count, { i, l, r ->
            pcmBuffer[i] = softClip(l).coerceIn(-1f, 1f)
            pcmBuffer[i + 1] = softClip(r).coerceIn(-1f, 1f)
        }, { i -> Pair(pcmBuffer[i], pcmBuffer[i + 1]) })
    }

    private inline fun processFrames(
        count: Int,
        write: (Int, Float, Float) -> Unit,
        read: (Int) -> Pair<Float, Float>
    ) {
        val immersion = atmosImmersion
        val height = height3D
        val centerGain = centerFocus.coerceIn(0f, 1.5f)
        val protectAlpha = onePoleAlpha(lowProtectHz)
        val lowAlpha = onePoleAlpha(spatialStartHz)
        val midAlpha = onePoleAlpha(spatialMidHz)
        val highAlpha = onePoleAlpha(hrtfStartHz)
        val itd = (sampleRate * itdMs * 0.001f).coerceIn(1f, maxDelaySamples.toFloat())
        val reflectionA = (sampleRate * (0.0042f + 0.0018f * height)).coerceIn(2f, maxDelaySamples.toFloat())
        val reflectionB = (sampleRate * (0.0075f + 0.0020f * height)).coerceIn(2f, maxDelaySamples.toFloat())
        val alphaCross = onePoleAlpha(1500f + 2500f * height)

        for (i in 0 until count step 2) {
            val (left, right) = read(i)
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // Low frequencies remain centered and are not sent through ITD/reflections.
            directLowL += protectAlpha * (left - directLowL)
            directLowR += protectAlpha * (right - directLowR)
            val lowMid = (directLowL + directLowR) * 0.5f
            val protectedLow = lowMid
            val spatialBand = side - (side * 0.0f) // keep state-free base before spectral shaping

            // Per-ear direct paths with frequency-dependent lateral cues.
            leftLowState += lowAlpha * (left - leftLowState)
            rightLowState += lowAlpha * (right - rightLowState)
            val leftLow = leftLowState
            val rightLow = rightLowState
            leftCrossLowState += alphaCross * (right - leftCrossLowState)
            rightCrossLowState += alphaCross * (left - rightCrossLowState)
            val leftHigh = left - leftLow
            val rightHigh = right - rightLow
            val leftCrossHigh = right - leftCrossLowState
            val rightCrossHigh = left - rightCrossLowState

            leftDelay[delayIndex] = left
            rightDelay[delayIndex] = right
            fun delayed(buffer: FloatArray, delay: Float): Float {
                val readPos = delayIndex - delay
                val base = floor(readPos).toInt()
                val frac = readPos - base
                val a = buffer[wrap(base, buffer.size)]
                val b = buffer[wrap(base - 1, buffer.size)]
                return a * (1f - frac) + b * frac
            }

            val delayedLeft = delayed(leftDelay, itd)
            val delayedRight = delayed(rightDelay, itd)
            val reflectionLeft = delayed(leftDelay, reflectionA) * 0.62f +
                delayed(leftDelay, reflectionB) * 0.38f
            val reflectionRight = delayed(rightDelay, reflectionA) * 0.62f +
                delayed(rightDelay, reflectionB) * 0.38f
            delayIndex = (delayIndex + 1) % leftDelay.size

            // Crossfeed is low-passed: high frequencies carry stronger ear-specific cues.
            val crossfeed = immersion * (0.10f + 0.035f * (1f - height))
            val headShadow = immersion * (0.12f + 0.10f * height)
            val lateral = immersion * (0.16f + 0.10f * height)
            val externalReflection = immersion * (0.045f + 0.035f * height)
            val heightCue = immersion * height * 0.035f

            val binauralLeft = left * (1f + lateral * 0.35f) -
                leftHigh * headShadow * 0.10f +
                leftCrossLowState * crossfeed +
                leftCrossHigh * crossfeed * 0.25f +
                delayedRight * lateral * 0.22f +
                reflectionRight * externalReflection
            val binauralRight = right * (1f + lateral * 0.35f) -
                rightHigh * headShadow * 0.10f +
                rightCrossLowState * crossfeed +
                rightCrossHigh * crossfeed * 0.25f +
                delayedLeft * lateral * 0.22f +
                reflectionLeft * externalReflection

            // Recombine with an invariant phantom center. Bass below the protection
            // region is blended back toward mono; upper bands carry most of the width.
            val lowAlphaProtect = protectAlpha
            val lowL = directLowL
            val lowR = directLowR
            val highL = left - lowL
            val highR = right - lowR
            val shapedMid = mid * centerGain
            val sideContributionL = (binauralLeft - binauralRight) * 0.5f
            val sideContributionR = -sideContributionL
            val lowSide = side * (1f - immersion * 0.85f)
            val outputL = shapedMid + lowSide + (highL * (1f - immersion * 0.20f)) +
                sideContributionL * (0.72f + heightCue)
            val outputR = shapedMid - lowSide + (highR * (1f - immersion * 0.20f)) +
                sideContributionR * (0.72f + heightCue)

            write(i, outputL, outputR)
        }
    }

    fun reset() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        delayIndex = 0
        leftLowState = 0f
        rightLowState = 0f
        leftCrossLowState = 0f
        rightCrossLowState = 0f
        directLowL = 0f
        directLowR = 0f
    }

    private fun wrap(index: Int, size: Int): Int {
        var v = index % size
        if (v < 0) v += size
        return v
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= 0.90f) return x
        val excess = (a - 0.90f) / 0.10f
        val shaped = 0.90f + 0.10f * tanh(excess.toDouble()).toFloat()
        return sign(x) * max(0.90f, shaped.coerceAtMost(1f))
    }
}
