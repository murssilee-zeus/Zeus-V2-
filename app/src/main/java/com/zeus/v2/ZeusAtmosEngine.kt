package com.zeus.v2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tanh

/**
 * PCM Atmos-style spatial processor.
 *
 * The PCM path now uses a real LR4 four-way frequency split:
 * 180 / 1800 / 8000 Hz, with 24 dB/octave slopes.
 *
 * The low band remains protected from ITD/spatial widening. Spatial energy
 * is progressively introduced through low-mid, high-mid and high bands.
 * This keeps the previous Zeus sonic character while making the crossover
 * behavior substantially more controlled than the former one-pole split.
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

    /** Bass protection reference. The LR4 low band extends through the 180 Hz crossover. */
    var lowProtectHz: Float = 120f
        set(value) { field = value.coerceIn(60f, 250f) }

    /** First LR4 crossover. */
    var spatialStartHz: Float = 180f
        set(value) { field = value.coerceIn(120f, 1000f) }

    /** Second LR4 crossover. */
    var spatialMidHz: Float = 1800f
        set(value) { field = value.coerceIn(spatialStartHz + 100f, 8000f) }

    /** Third LR4 crossover / high spatial band boundary. */
    var hrtfStartHz: Float = 8000f
        set(value) { field = value.coerceIn(spatialMidHz + 100f, 16000f) }

    /** Kept as a public tuning point for compatibility. */
    var hrtfEndHz: Float = 12000f
        set(value) { field = value.coerceIn(hrtfStartHz + 500f, 18000f) }

    private val maxDelaySamples = (sampleRate * 0.0016f).toInt().coerceAtLeast(2)
    private val sideDelay = FloatArray(maxDelaySamples + 2)
    private var delayIndex = 0

    private var lr4 = ZeusLr4BandSplitter(
        sampleRate = sampleRate,
        crossover1Hz = spatialStartHz,
        crossover2Hz = spatialMidHz,
        crossover3Hz = hrtfStartHz
    )

    /**
     * Processes interleaved signed 16-bit stereo PCM in-place.
     * size is the number of samples, not frames.
     */
    fun processAtmosPCM(pcmBuffer: ShortArray, size: Int = pcmBuffer.size) {
        val sampleCount = size.coerceIn(0, pcmBuffer.size - (pcmBuffer.size % 2))
        if (sampleCount < 2) return

        val itdSamples = (sampleRate * itdMs * 0.001f)
            .coerceIn(1f, maxDelaySamples.toFloat())

        val immersion = atmosImmersion
        val centerGain = centerFocus.coerceIn(0f, 1.5f)

        for (i in 0 until sampleCount step 2) {
            val left = pcmBuffer[i] / 32768f
            val right = pcmBuffer[i + 1] / 32768f

            // M/S extraction.
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // True LR4 four-way split: 24 dB/octave at 180 / 1800 / 8000 Hz.
            val bands = lr4.process(side)

            // Keep the low band out of ITD. Low-mid is introduced gently,
            // while upper bands receive progressively more spatial energy.
            val protectedLow = bands.low
            val lowMidSpatial = bands.lowMid * (0.42f + 0.16f * immersion)
            val highMidSpatial = bands.highMid * (0.95f + 0.30f * immersion)
            val highSpatial = bands.high * (1.05f + 0.35f * immersion)
            val spatialInput = lowMidSpatial + highMidSpatial + highSpatial

            // Fractional causal delay using linear interpolation in the ring buffer.
            sideDelay[delayIndex] = spatialInput
            val readPos = delayIndex - itdSamples
            val base = kotlin.math.floor(readPos).toInt()
            val frac = readPos - base
            val delayedA = sideDelay[wrap(base, sideDelay.size)]
            val delayedB = sideDelay[wrap(base - 1, sideDelay.size)]
            val delayedSide = delayedA * (1f - frac) + delayedB * frac
            val depthDelaySamples = (sampleRate * (0.0010f + 0.00035f * height3D)).coerceIn(1f, maxDelaySamples.toFloat())
            val depthRead = delayIndex - depthDelaySamples
            val depthBase = kotlin.math.floor(depthRead).toInt()
            val depthFrac = depthRead - depthBase
            val depthA = sideDelay[wrap(depthBase, sideDelay.size)]
            val depthB = sideDelay[wrap(depthBase - 1, sideDelay.size)]
            val depthSide = depthA * (1f - depthFrac) + depthB * depthFrac
            delayIndex = (delayIndex + 1) % sideDelay.size
            val airCue = bands.high * (0.10f + 0.12f * height3D) * height3D
            val sideGain = 1f + immersion * (1.15f + 0.25f * height3D)
            val delayedMix = immersion * (0.34f + 0.10f * height3D)
            val depthMix = immersion * (0.12f + 0.10f * height3D)
            val ild = immersion * (0.045f + 0.035f * height3D)
            val sideLeft = spatialInput * sideGain * (1f + ild) + delayedSide * delayedMix + depthSide * depthMix + airCue
            val sideRight = delayedSide * sideGain * (1f + ild) + spatialInput * delayedMix + depthSide * depthMix + airCue

            // Reinsert the protected low band naturally. Mid receives center focus.
            val outMid = mid * centerGain
            val outLeft = outMid + protectedLow + sideLeft
            val outRight = outMid - protectedLow - sideRight

            pcmBuffer[i] = (softClip(outLeft) * 32767f).toInt().toShort()
            pcmBuffer[i + 1] = (softClip(outRight) * 32767f).toInt().toShort()
        }
    }


    /** Float32 path preserves fractional precision between DSP stages. */
    fun processAtmosPCM(pcmBuffer: FloatArray, size: Int = pcmBuffer.size) {
        val sampleCount = size.coerceIn(0, pcmBuffer.size - (pcmBuffer.size % 2))
        if (sampleCount < 2) return

        val itdSamples = (sampleRate * itdMs * 0.001f)
            .coerceIn(1f, maxDelaySamples.toFloat())

        val immersion = atmosImmersion
        val centerGain = centerFocus.coerceIn(0f, 1.5f)

        for (i in 0 until sampleCount step 2) {
            val left = pcmBuffer[i]
            val right = pcmBuffer[i + 1]

            // M/S extraction.
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            // True LR4 four-way split: 24 dB/octave at 180 / 1800 / 8000 Hz.
            val bands = lr4.process(side)

            // Keep the low band out of ITD. Low-mid is introduced gently,
            // while upper bands receive progressively more spatial energy.
            val protectedLow = bands.low
            val lowMidSpatial = bands.lowMid * (0.42f + 0.16f * immersion)
            val highMidSpatial = bands.highMid * (0.95f + 0.30f * immersion)
            val highSpatial = bands.high * (1.05f + 0.35f * immersion)
            val spatialInput = lowMidSpatial + highMidSpatial + highSpatial

            // Fractional causal delay using linear interpolation in the ring buffer.
            sideDelay[delayIndex] = spatialInput
            val readPos = delayIndex - itdSamples
            val base = kotlin.math.floor(readPos).toInt()
            val frac = readPos - base
            val delayedA = sideDelay[wrap(base, sideDelay.size)]
            val delayedB = sideDelay[wrap(base - 1, sideDelay.size)]
            val delayedSide = delayedA * (1f - frac) + delayedB * frac
            val depthDelaySamples = (sampleRate * (0.0010f + 0.00035f * height3D)).coerceIn(1f, maxDelaySamples.toFloat())
            val depthRead = delayIndex - depthDelaySamples
            val depthBase = kotlin.math.floor(depthRead).toInt()
            val depthFrac = depthRead - depthBase
            val depthA = sideDelay[wrap(depthBase, sideDelay.size)]
            val depthB = sideDelay[wrap(depthBase - 1, sideDelay.size)]
            val depthSide = depthA * (1f - depthFrac) + depthB * depthFrac
            delayIndex = (delayIndex + 1) % sideDelay.size
            val airCue = bands.high * (0.10f + 0.12f * height3D) * height3D
            val sideGain = 1f + immersion * (1.15f + 0.25f * height3D)
            val delayedMix = immersion * (0.34f + 0.10f * height3D)
            val depthMix = immersion * (0.12f + 0.10f * height3D)
            val ild = immersion * (0.045f + 0.035f * height3D)
            val sideLeft = spatialInput * sideGain * (1f + ild) + delayedSide * delayedMix + depthSide * depthMix + airCue
            val sideRight = delayedSide * sideGain * (1f + ild) + spatialInput * delayedMix + depthSide * depthMix + airCue

            // Reinsert the protected low band naturally. Mid receives center focus.
            val outMid = mid * centerGain
            val outLeft = outMid + protectedLow + sideLeft
            val outRight = outMid - protectedLow - sideRight

            pcmBuffer[i] = softClip(outLeft).coerceIn(-1f, 1f)
            pcmBuffer[i + 1] = softClip(outRight).coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        sideDelay.fill(0f)
        delayIndex = 0
        lr4.reset()
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
