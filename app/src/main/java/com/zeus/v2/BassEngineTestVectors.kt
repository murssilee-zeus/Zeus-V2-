package com.zeus.v2

import kotlin.math.abs
import kotlin.math.sin

/** Deterministic smoke vectors for the PCM bass processor. */
object BassEngineTestVectors {
    fun sine(frequencyHz: Float, sampleRate: Int, seconds: Float = 0.25f): FloatArray {
        val n = (sampleRate * seconds).toInt().coerceAtLeast(1)
        return FloatArray(n * 2) { i ->
            val sample = sin(2.0 * Math.PI * frequencyHz * (i / 2) / sampleRate).toFloat() * 0.25f
            sample
        }
    }

    fun peak(samples: FloatArray): Float = samples.maxOfOrNull { abs(it) } ?: 0f
}
