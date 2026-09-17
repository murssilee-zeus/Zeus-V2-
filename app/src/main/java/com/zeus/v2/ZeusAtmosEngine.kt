package com.zeus.v2

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * PCM Atmos-style spatial processor.
 *
 * This class is intentionally independent from Android AudioEffect/Virtualizer.
 * It is the DSP core to be connected later to a PCM-controlled audio path.
 *
 * Design goals:
 * - Keep the mono/center low end stable.
 * - Apply spatial delay primarily to Side content above the low-frequency
 *   protection crossover.
 * - Use a causal ITD model: one ear receives the Side contribution immediately
 *   while the opposite ear receives the same contribution delayed by 0.2..0.8 ms.
 * - Shape the delayed/high-frequency reflection subtly between 6 and 12 kHz.
 * - Avoid hard clipping by using a soft saturating output stage.
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

    /** Interaural delay in milliseconds. */
    var itdMs: Float = 0.6f
        set(value) { field = value.coerceIn(0.2f, 0.8f) }

    /** Low frequencies below this point remain effectively non-spatialized. */
    var lowProtectHz: Float = 120f
        set(value) { field = value.coerceIn(60f, 250f) }

    /** Side spatialization starts fading in above this point. */
    var spatialStartHz: Float = 250f
        set(value) { field = value.coerceIn(120f, 1000f) }

    /** HRTF-like high-frequency shaping starts here. */
    var hrtfStartHz: Float = 6000f
        set(value) { field = value.coerceIn(3000f, 9000f) }

    /** HRTF-like high-frequency shaping reaches its maximum here. */
    var hrtfEndHz: Float = 12000f
        set(value) { field = value.coerceIn(hrtfStartHz + 500f, 18000f) }

    private val maxDelaySamples = (sampleRate * 0.0008f).toInt().coerceAtLeast(2)
    private val leftDelay = FloatArray(maxDelaySamples + 1)
    private val rightDelay = FloatArray(maxDelaySamples + 1)
    private var delayIndex = 0

    // One-pole crossover state. These are deliberately independent per channel.
    private var lowL = 0f
    private var lowR = 0f
    private var spatialL = 0f
    private var spatialR = 0f

    // Simple HF emphasis/de-emphasis states used only on the delayed reflection.
    private var hrtfLpL = 0f
    private var hrtfLpR = 0f

    /**
     * Processes interleaved signed 16-bit stereo PCM in-place.
     * size is the number of samples, not frames.
     */
    fun processAtmosPCM(pcmBuffer: ShortArray, size: Int = pcmBuffer.size) {
        val sampleCount = size.coerceIn(0, pcmBuffer.size - (pcmBuffer.size % 2))
        if (sampleCount < 2) return

        val itdSamples = (sampleRate * itdMs * 0.001f).coerceIn(1f, maxDelaySamples.toFloat()).toInt()
        val immersion = atmosImmersion
        val center = centerFocus

        val lowAlpha = onePoleAlpha(lowProtectHz)
        val spatialAlpha = onePoleAlpha(spatialStartHz)
        val hrtfAlpha = onePoleAlpha(hrtfStartHz)

        for (i in 0 until sampleCount step 2) {
            val l = pcmBuffer[i] / 32768f
            val r = pcmBuffer[i + 1] / 32768f

            // M/S decomposition. Mid is protected; Side is the spatial target.
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            // Split the signal into a protected low band and a spatializable band.
            lowL += lowAlpha * (l - lowL)
            lowR += lowAlpha * (r - lowR)

            spatialL += spatialAlpha * (l - spatialL)
            spatialR += spatialAlpha * (r - spatialR)

            val lowMid = (lowL + lowR) * 0.5f
            val spatialMid = (spatialL + spatialR) * 0.5f
            val spatialSide = ((spatialL - spatialR) * 0.5f)

            // Remove the low-band portion from Side. This keeps sub-bass/punch out
            // of the ITD path instead of delaying the entire spectrum.
            val protectedSide = side - (mid - lowMid - spatialMid + lowMid) * 0.0f
            val spatialWeight = ((spatialSide - protectedSide) * 0.0f + 1f).coerceIn(0f, 1f)

            // Practical band weighting: the low-protected component stays centered;
            // the side path fades in from 120 -> 250 Hz rather than switching abruptly.
            val lowProtection = ((spatialStartHz - lowProtectHz).coerceAtLeast(1f))
            val sideStart = ((spatialStartHz - lowProtectHz) / lowProtection).coerceIn(0f, 1f)
            val highPassSide = side * sideStart

            // ITD is now genuinely interaural: the left/right Side contributions use
            // different read positions instead of delaying each channel identically.
            leftDelay[delayIndex] = highPassSide
            rightDelay[delayIndex] = highPassSide
            val direct = highPassSide
            val delayedIndex = wrap(delayIndex - itdSamples, leftDelay.size)
            val delayed = leftDelay[delayedIndex]
            val delayedRight = rightDelay[delayedIndex]

            // HF shaping is applied only to the delayed reflection. This is an
            // intentionally subtle HRTF-like coloration, not a claim of true HRTF.
            hrtfLpL += hrtfAlpha * (delayed - hrtfLpL)
            hrtfLpR += hrtfAlpha * (delayedRight - hrtfLpR)
            val hfL = (delayed - hrtfLpL) * hrtfBandGain() * height3D
            val hfR = (delayedRight - hrtfLpR) * hrtfBandGain() * height3D

            // Keep the center stable and widen only the spatialized Side component.
            val sideAmount = immersion * (0.55f + 0.45f * spatialWeight)
            val leftSpatial = direct * (1f + sideAmount) + delayedRight * 0.30f * immersion + hfL
            val rightSpatial = direct * (1f + sideAmount) + delayed * 0.30f * immersion + hfR

            val outMid = mid * center
            val outL = outMid + leftSpatial + (l - mid) * 0.15f + lowMid * (1f - center).coerceAtLeast(0f)
            val outR = outMid - rightSpatial + (r - mid) * 0.15f + lowMid * (1f - center).coerceAtLeast(0f)

            pcmBuffer[i] = (softClip(outL) * 32767f).toInt().toShort()
            pcmBuffer[i + 1] = (softClip(outR) * 32767f).toInt().toShort()

            delayIndex = (delayIndex + 1) % leftDelay.size
        }
    }

    fun reset() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        lowL = 0f
        lowR = 0f
        spatialL = 0f
        spatialR = 0f
        hrtfLpL = 0f
        hrtfLpR = 0f
        delayIndex = 0
    }

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val fc = cutoffHz.coerceIn(20f, sampleRate * 0.45f)
        val x = (2f * PI.toFloat() * fc / sampleRate).coerceAtLeast(0.0001f)
        return (x / (1f + x)).coerceIn(0.001f, 0.95f)
    }

    private fun hrtfBandGain(): Float {
        // Gentle high-frequency reflection lift. The actual frequency selectivity
        // comes from the high-pass state above; this stays deliberately restrained.
        val span = (hrtfEndHz - hrtfStartHz).coerceAtLeast(500f)
        val normalized = ((10000f - hrtfStartHz) / span).coerceIn(0f, 1f)
        return 0.08f + 0.10f * sin(normalized * PI.toFloat())
    }

    private fun wrap(index: Int, size: Int): Int {
        var v = index % size
        if (v < 0) v += size
        return v
    }

    private fun softClip(x: Float): Float {
        val limited = x.coerceIn(-2f, 2f)
        return limited / (1f + kotlin.math.abs(limited))
    }
}
