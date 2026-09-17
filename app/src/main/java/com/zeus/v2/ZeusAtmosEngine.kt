package com.zeus.v2

import kotlin.math.PI
import kotlin.math.sin

/**
 * PCM Atmos-style spatial processor.
 *
 * Independent from Android AudioEffect/Virtualizer. This is the DSP core for
 * a future PCM-controlled path.
 *
 * The important rule is simple: protect the low end, spatialize Side above the
 * crossover, and introduce a real interaural delay rather than delaying both
 * channels equally.
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

    /** Sub-bass / punch protection starts here. */
    var lowProtectHz: Float = 120f
        set(value) { field = value.coerceIn(60f, 250f) }

    /** Side spatialization is fully active above this point. */
    var spatialStartHz: Float = 250f
        set(value) { field = value.coerceIn(120f, 1000f) }

    /** HRTF-like shaping starts here. */
    var hrtfStartHz: Float = 6000f
        set(value) { field = value.coerceIn(3000f, 9000f) }

    /** HRTF-like shaping ends here. */
    var hrtfEndHz: Float = 12000f
        set(value) { field = value.coerceIn(hrtfStartHz + 500f, 18000f) }

    private val maxDelaySamples = (sampleRate * 0.0008f).toInt().coerceAtLeast(2)
    private val sideDelay = FloatArray(maxDelaySamples + 1)
    private var delayIndex = 0

    // Low-pass states used to derive two complementary Side bands:
    // <120 Hz protected, 120..250 Hz transitional, >250 Hz spatialized.
    private var sideLow120 = 0f
    private var sideLow250 = 0f

    // HF state for the delayed reflection only.
    private var hrtfLp = 0f

    /**
     * Processes interleaved signed 16-bit stereo PCM in-place.
     * size is the number of samples, not frames.
     */
    fun processAtmosPCM(pcmBuffer: ShortArray, size: Int = pcmBuffer.size) {
        val sampleCount = size.coerceIn(0, pcmBuffer.size - (pcmBuffer.size % 2))
        if (sampleCount < 2) return

        val itdSamples = (sampleRate * itdMs * 0.001f)
            .coerceIn(1f, maxDelaySamples.toFloat())
            .toInt()

        val lowAlpha = onePoleAlpha(lowProtectHz)
        val spatialAlpha = onePoleAlpha(spatialStartHz)
        val hrtfAlpha = onePoleAlpha(hrtfStartHz)
        val immersion = atmosImmersion
        val centerGain = centerFocus.coerceIn(0f, 1.5f)

        for (i in 0 until sampleCount step 2) {
            val left = pcmBuffer[i] / 32768f
            val right = pcmBuffer[i + 1] / 32768f

            // M/S extraction.
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // Complementary low-pass states produce a frequency-aware Side split.
            // sideBelow120 is protected; side120to250 is transition; sideAbove250
            // is the main spatial path.
            sideLow120 += lowAlpha * (side - sideLow120)
            sideLow250 += spatialAlpha * (side - sideLow250)

            val side120to250 = sideLow120 - sideLow250
            val sideAbove250 = side - sideLow250

            // Smoothly preserve a small part of the transition band while keeping
            // the sub-120 Hz Side component out of the ITD path.
            val spatialSide = sideAbove250 + side120to250 * 0.5f

            // Causal ITD: one ear gets the Side contribution immediately and the
            // opposite ear gets it after 0.2..0.8 ms. This is genuinely interaural;
            // the previous model delayed both channels identically.
            sideDelay[delayIndex] = spatialSide
            val delayedIndex = wrap(delayIndex - itdSamples, sideDelay.size)
            val delayedSide = sideDelay[delayedIndex]
            delayIndex = (delayIndex + 1) % sideDelay.size

            // Apply the HRTF-like coloration only to the delayed reflection.
            // This is intentionally subtle and is not a true measured HRTF.
            hrtfLp += hrtfAlpha * (delayedSide - hrtfLp)
            val delayedHigh = (delayedSide - hrtfLp) * hrtfBandGain() * height3D

            // Keep the center stable. Spatial gain is applied to Side only.
            val sideGain = 1f + immersion * 0.90f
            val delayedMix = immersion * 0.30f

            // Direction is intentionally fixed for this first PCM core. A later
            // azimuth control can swap direct/delayed ears without changing DSP.
            val sideLeft = spatialSide * sideGain + delayedSide * delayedMix + delayedHigh
            val sideRight = delayedSide * sideGain + spatialSide * delayedMix + delayedHigh

            // Reinsert the protected low Side naturally. Mid receives center focus.
            val protectedSide = sideLow120
            val outMid = mid * centerGain
            val outLeft = outMid + protectedSide + sideLeft
            val outRight = outMid - protectedSide - sideRight

            pcmBuffer[i] = (softClip(outLeft) * 32767f).toInt().toShort()
            pcmBuffer[i + 1] = (softClip(outRight) * 32767f).toInt().toShort()
        }
    }

    fun reset() {
        sideDelay.fill(0f)
        sideLow120 = 0f
        sideLow250 = 0f
        hrtfLp = 0f
        delayIndex = 0
    }

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val fc = cutoffHz.coerceIn(20f, sampleRate * 0.45f)
        val x = (2f * PI.toFloat() * fc / sampleRate).coerceAtLeast(0.0001f)
        return (x / (1f + x)).coerceIn(0.001f, 0.95f)
    }

    private fun hrtfBandGain(): Float {
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
