package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Low-distortion linked-stereo peak safety stage for the Hi-Res + Epicenter bus.
 * It is idle below its ceiling and only attenuates when the combined signal
 * approaches full scale. Gain reduction is shared by L/R to preserve imaging.
 */
class ZeusHybridHeadroom(
    sampleRate: Int,
    private val ceilingDb: Float = -1.0f,
    releaseMs: Float = 85f
) {
    private val ceiling = 10.0.pow(ceilingDb / 20.0).toFloat().coerceIn(0.5f, 0.99f)
    private val releaseCoeff = exp(
        -1f / (sampleRate.coerceAtLeast(8000) * (releaseMs.coerceIn(20f, 250f) / 1000f))
    )
    private var gain = 1f

    fun processStereo(buffer: FloatArray, size: Int = buffer.size) {
        val n = size.coerceIn(0, buffer.size - buffer.size % 2)
        for (i in 0 until n step 2) {
            val peak = max(abs(buffer[i]), abs(buffer[i + 1]))
            val target = if (peak > ceiling) ceiling / peak else 1f
            gain = if (target < gain) target else 1f - (1f - gain) * releaseCoeff
            buffer[i] = (buffer[i] * gain).coerceIn(-1f, 1f)
            buffer[i + 1] = (buffer[i + 1] * gain).coerceIn(-1f, 1f)
        }
    }

    fun processStereo(buffer: ShortArray, size: Int = buffer.size) {
        val n = size.coerceIn(0, buffer.size - buffer.size % 2)
        for (i in 0 until n step 2) {
            val left = buffer[i] / 32768f
            val right = buffer[i + 1] / 32768f
            val peak = max(abs(left), abs(right))
            val target = if (peak > ceiling) ceiling / peak else 1f
            gain = if (target < gain) target else 1f - (1f - gain) * releaseCoeff
            buffer[i] = (left * gain * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i + 1] = (right * gain * 32768f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    fun reset() {
        gain = 1f
    }
}
