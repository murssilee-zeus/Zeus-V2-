package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/**
 * PCM Atmos-style spatial processor.
 *
 * Independent from Android AudioEffect/Virtualizer. This is the DSP core for
 * a future PCM-controlled path.
 *
 * Frequency architecture:
 * - Side below lowProtectHz stays in the direct path for bass/punch safety.
 * - Side between lowProtectHz and spatialStartHz is only partly spatialized.
 * - Side above spatialStartHz receives the ITD spatial path.
 * - A subtle 6..12 kHz band is added to the delayed path for height/air.
 *
 * ITD is fractional-sample capable so 0.2..0.8 ms is not forced to integer samples.
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
    private val sideDelay = FloatArray(maxDelaySamples + 2)
    private var delayIndex = 0

    // Proper complementary Side split: LP120, LP250, then the differences.
    private var sideLow = 0f
    private var sideLowSpatial = 0f

    // Two low-pass states make a real 6..12 kHz band from LP(end) - LP(start).
    private var hrtfLpStart = 0f
    private var hrtfLpEnd = 0f

    /**
     * Processes interleaved signed 16-bit stereo PCM in-place.
     * size is the number of samples, not frames.
     */
    fun processAtmosPCM(pcmBuffer: ShortArray, size: Int = pcmBuffer.size) {
        val sampleCount = size.coerceIn(0, pcmBuffer.size - (pcmBuffer.size % 2))
        if (sampleCount < 2) return

        val itdSamples = (sampleRate * itdMs * 0.001f)
            .coerceIn(1f, maxDelaySamples.toFloat())

        val lowAlpha = onePoleAlpha(lowProtectHz)
        val spatialAlpha = onePoleAlpha(spatialStartHz)
        val hrtfStartAlpha = onePoleAlpha(hrtfStartHz)
        val hrtfEndAlpha = onePoleAlpha(hrtfEndHz)
        val immersion = atmosImmersion
        val centerGain = centerFocus.coerceIn(0f, 1.5f)

        for (i in 0 until sampleCount step 2) {
            val left = pcmBuffer[i] / 32768f
            val right = pcmBuffer[i + 1] / 32768f

            // M/S extraction.
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // Complementary frequency split.
            sideLow += lowAlpha * (side - sideLow)
            sideLowSpatial += spatialAlpha * (side - sideLowSpatial)

            val protectedLow = sideLow
            val transition = (sideLowSpatial - sideLow).coerceAtLeast(0f)
            val spatialSide = (side - sideLowSpatial).coerceAtLeast(-1f)

            // Keep half of the transition band spatializable, but never feed the
            // protected low band into ITD.
            val spatialInput = spatialSide + transition * 0.5f

            // Fractional causal delay using linear interpolation in the ring buffer.
            sideDelay[delayIndex] = spatialInput
            val readPos = delayIndex - itdSamples
            val base = kotlin.math.floor(readPos).toInt()
            val frac = readPos - base
            val delayedA = sideDelay[wrap(base, sideDelay.size)]
            val delayedB = sideDelay[wrap(base - 1, sideDelay.size)]
            val delayedSide = delayedA * (1f - frac) + delayedB * frac
            delayIndex = (delayIndex + 1) % sideDelay.size

            // A subtle 6..12 kHz band on the delayed path. This is an HRTF-like
            // coloration, not a measured HRTF profile.
            hrtfLpStart += hrtfStartAlpha * (delayedSide - hrtfLpStart)
            hrtfLpEnd += hrtfEndAlpha * (delayedSide - hrtfLpEnd)
            val hrtfBand = (hrtfLpEnd - hrtfLpStart).coerceIn(-1f, 1f)
            val delayedHigh = hrtfBand * (0.08f + 0.10f * height3D) * height3D

            val sideGain = 1f + immersion * 0.90f
            val delayedMix = immersion * 0.30f

            // Fixed direction for the first PCM core. A later azimuth control can
            // swap direct/delayed ears without changing the frequency architecture.
            val sideLeft = spatialInput * sideGain + delayedSide * delayedMix + delayedHigh
            val sideRight = delayedSide * sideGain + spatialInput * delayedMix + delayedHigh

            // Reinsert protected low Side naturally. Mid receives center focus.
            val outMid = mid * centerGain
            val outLeft = outMid + protectedLow + sideLeft
            val outRight = outMid - protectedLow - sideRight

            pcmBuffer[i] = (softClip(outLeft) * 32767f).toInt().toShort()
            pcmBuffer[i + 1] = (softClip(outRight) * 32767f).toInt().toShort()
        }
    }

    fun reset() {
        sideDelay.fill(0f)
        sideLow = 0f
        sideLowSpatial = 0f
        hrtfLpStart = 0f
        hrtfLpEnd = 0f
        delayIndex = 0
    }

    private fun onePoleAlpha(cutoffHz: Float): Float {
        val fc = cutoffHz.coerceIn(20f, sampleRate * 0.45f)
        val x = (2f * PI.toFloat() * fc / sampleRate).coerceAtLeast(0.0001f)
        return (x / (1f + x)).coerceIn(0.001f, 0.95f)
    }

    private fun wrap(index: Int, size: Int): Int {
        var v = index % size
        if (v < 0) v += size
        return v
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= 0.85f) return x
        val excess = (a - 0.85f) / 0.15f
        val shaped = 0.85f + 0.15f * tanh(excess.toDouble()).toFloat()
        return kotlin.math.sign(x) * max(0.85f, shaped.coerceAtMost(1f))
    }
}
