package com.zeus.v2

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.pow

/**
 * Native Zeus 4-band multiband compressor.
 *
 * PCM stereo -> LR4 split -> independent band dynamics -> recombine.
 * Crossovers are LR4 (24 dB/oct) and the detector is stereo-linked RMS.
 *
 * Bands:
 *   0 Low       < cross1
 *   1 Low-Mid   cross1 .. cross2
 *   2 High-Mid  cross2 .. cross3
 *   3 High      > cross3
 *
 * This processor is deliberately independent from ZeusAtmosEngine.
 */
class ZeusMultibandCompressor(
    private val sampleRate: Int
) {
    var enabled: Boolean = true
    var cross1: Float = 180f
    var cross2: Float = 1800f
    var cross3: Float = 8000f

    private data class Params(
        var threshold: Float,
        var ratio: Float,
        var knee: Float,
        var attack: Float,
        var release: Float,
        var preGain: Float,
        var postGain: Float
    )

    private val params = Array(4) {
        Params(-18f, 4f, 6f, 15f, 180f, 0f, 0f)
    }

    private val splitL = ZeusLr4BandSplitter(sampleRate)
    private val splitR = ZeusLr4BandSplitter(sampleRate)

    private var configuredC1 = 180f
    private var configuredC2 = 1800f
    private var configuredC3 = 8000f

    private val envelope = FloatArray(4)
    private val gainDb = FloatArray(4)

    fun configure(
        c1: Float,
        c2: Float,
        c3: Float,
        thresholds: FloatArray,
        ratios: FloatArray,
        knees: FloatArray,
        attacks: FloatArray,
        releases: FloatArray,
        preGains: FloatArray,
        postGains: FloatArray
    ) {
        cross1 = c1.coerceIn(40f, sampleRate * 0.20f)
        cross2 = c2.coerceIn(cross1 + 50f, sampleRate * 0.35f)
        cross3 = c3.coerceIn(cross2 + 50f, sampleRate * 0.45f)

        for (i in 0..3) {
            params[i].threshold = thresholds.getOrElse(i) { -18f }.coerceIn(-60f, 0f)
            params[i].ratio = ratios.getOrElse(i) { 3f }.coerceIn(1f, 24f)
            params[i].knee = knees.getOrElse(i) { 6f }.coerceIn(0f, 20f)
            params[i].attack = attacks.getOrElse(i) { 10f }.coerceIn(0.5f, 200f)
            params[i].release = releases.getOrElse(i) { 100f }.coerceIn(10f, 1000f)
            params[i].preGain = preGains.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            params[i].postGain = postGains.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
        }

        if (crossoverChanged()) {
            splitL.reconfigure(cross1, cross2, cross3)
            splitR.reconfigure(cross1, cross2, cross3)
            configuredC1 = cross1
            configuredC2 = cross2
            configuredC3 = cross3
            reset()
        }
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - (pcm.size % 2))
        if (n < 2) return

        for (i in 0 until n step 2) {
            val left = pcm[i] / 32768f
            val right = pcm[i + 1] / 32768f

            val lb = splitL.process(left)
            val rb = splitR.process(right)

            val l0 = lb.low
            val l1 = lb.lowMid
            val l2 = lb.highMid
            val l3 = lb.high
            val r0 = rb.low
            val r1 = rb.lowMid
            val r2 = rb.highMid
            val r3 = rb.high

            val ls = floatArrayOf(l0, l1, l2, l3)
            val rs = floatArrayOf(r0, r1, r2, r3)

            for (band in 0..3) {
                val p = params[band]
                val pre = dbToLinear(p.preGain)
                val lPre = ls[band] * pre
                val rPre = rs[band] * pre

                // Stereo-linked RMS detector: both channels receive the same GR.
                val level = sqrt(
                    max(1e-12f, (lPre * lPre + rPre * rPre) * 0.5f)
                )
                val levelDb = 20f * kotlin.math.log10(level.coerceAtLeast(1e-6f))

                val targetGr = compressionGainDb(levelDb, p.threshold, p.ratio, p.knee)
                val coefficient = if (targetGr < gainDb[band]) {
                    // Faster movement into compression.
                    exp(-1f / (0.001f * p.attack * sampleRate))
                } else {
                    // Slower recovery.
                    exp(-1f / (0.001f * p.release * sampleRate))
                }
                gainDb[band] = coefficient * gainDb[band] + (1f - coefficient) * targetGr

                val gain = dbToLinear(gainDb[band] + p.postGain)
                ls[band] = lPre * gain
                rs[band] = rPre * gain
            }

            val outL = ls[0] + ls[1] + ls[2] + ls[3]
            val outR = rs[0] + rs[1] + rs[2] + rs[3]

            pcm[i] = clampAudio(outL)
            pcm[i + 1] = clampAudio(outR)
        }
    }

    fun reset() {
        splitL.reset()
        splitR.reset()
        envelope.fill(0f)
        gainDb.fill(0f)
    }

    private fun crossoverChanged(): Boolean =
        abs(cross1 - configuredC1) > 0.01f ||
        abs(cross2 - configuredC2) > 0.01f ||
        abs(cross3 - configuredC3) > 0.01f

    private fun compressionGainDb(levelDb: Float, threshold: Float, ratio: Float, knee: Float): Float {
        if (ratio <= 1.0001f) return 0f

        if (knee <= 0.01f) {
            return if (levelDb > threshold) {
                threshold + (levelDb - threshold) / ratio - levelDb
            } else 0f
        }

        val lower = threshold - knee * 0.5f
        val upper = threshold + knee * 0.5f
        return when {
            levelDb <= lower -> 0f
            levelDb >= upper -> threshold + (levelDb - threshold) / ratio - levelDb
            else -> {
                val x = levelDb - lower
                val compressed = x * x / (2f * knee * ratio)
                -compressed
            }
        }
    }

    private fun dbToLinear(db: Float): Float =
        10f.pow(db / 20f)

    private fun clampAudio(x: Float): Short {
        val shaped = if (abs(x) > 0.92f) {
            kotlin.math.tanh(x.toDouble()).toFloat()
        } else x
        return (shaped.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}
