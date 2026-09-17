package com.zeus.v2

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time stereo-space analyzer. Feed interleaved L/R PCM when a stereo source
 * is available. Metrics are normalized 0..100 for UI/decision logic.
 */
class SpatialAnalysisEngine {
    data class Metrics(
        val center: Float = 50f,
        val side: Float = 50f,
        val correlation: Float = 0f,
        val lateralEnergy: Float = 50f,
        val depth: Float = 50f,
        val available: Boolean = false
    )

    private var smooth = Metrics()

    @Synchronized
    fun analyzeStereo(interleaved: FloatArray, frameCount: Int = interleaved.size / 2): Metrics {
        if (frameCount < 32 || interleaved.size < frameCount * 2) return smooth
        var sumC = 0.0
        var sumS = 0.0
        var sumLL = 0.0
        var sumRR = 0.0
        var sumLR = 0.0
        var sumAbs = 0.0
        val n = frameCount.coerceAtMost(interleaved.size / 2)
        for (i in 0 until n) {
            val l = interleaved[i * 2].coerceIn(-1f, 1f).toDouble()
            val r = interleaved[i * 2 + 1].coerceIn(-1f, 1f).toDouble()
            val c = (l + r) * 0.5
            val s = (l - r) * 0.5
            sumC += c * c
            sumS += s * s
            sumLL += l * l
            sumRR += r * r
            sumLR += l * r
            sumAbs += abs(l) + abs(r)
        }
        val centerPower = sumC / n
        val sidePower = sumS / n
        val total = (centerPower + sidePower).coerceAtLeast(1e-12)
        val sidePct = (sidePower / total * 100.0).toFloat().coerceIn(0f, 100f)
        val centerPct = 100f - sidePct
        val denom = sqrt((sumLL * sumRR).coerceAtLeast(1e-12))
        val corr = (sumLR / denom).toFloat().coerceIn(-1f, 1f)

        // Lateral energy rewards actual Side power while correlation loss reduces confidence.
        val lateral = (sidePct * (1f - corr.coerceAtLeast(0f) * 0.45f)).coerceIn(0f, 100f)
        // Depth is a conservative proxy: decorrelation + low average absolute level leaves headroom for ambience.
        val rms = sqrt(total).toFloat().coerceIn(0f, 1f)
        val depth = ((1f - corr.coerceIn(0f, 1f)) * 60f + (1f - rms) * 40f).coerceIn(0f, 100f)

        val next = Metrics(centerPct, sidePct, corr, lateral, depth, true)
        smooth = Metrics(
            lerp(smooth.center, next.center),
            lerp(smooth.side, next.side),
            lerp(smooth.correlation, next.correlation),
            lerp(smooth.lateralEnergy, next.lateralEnergy),
            lerp(smooth.depth, next.depth),
            true
        )
        return smooth
    }

    private fun lerp(a: Float, b: Float, amount: Float = 0.18f): Float = a + (b - a) * amount
}
