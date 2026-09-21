package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Native Zeus four-band multiband compressor.
 *
 * PCM stereo -> LR4 split -> independent band dynamics -> recombine.
 * The three crossover points use LR4 24 dB/octave filters.
 *
 * Bands:
 *   Low      < cross1
 *   Low-Mid  cross1 .. cross2
 *   High-Mid cross2 .. cross3
 *   High     > cross3
 *
 * Deliberately independent from ZeusAtmosEngine.
 */
class ZeusMultibandCompressor(
    private val sampleRate: Int
) {
    var enabled: Boolean = true
    var cross1: Float = 180f
    var cross2: Float = 1800f
    var cross3: Float = 8000f

    private data class Params(
        var threshold: Float = -18f,
        var ratio: Float = 4f,
        var knee: Float = 6f,
        var attack: Float = 15f,
        var release: Float = 180f,
        var preGain: Float = 0f,
        var postGain: Float = 0f
    )

    private val params = Array(4) { Params() }
    private var splitL = ZeusLr4BandSplitter(sampleRate)
    private var splitR = ZeusLr4BandSplitter(sampleRate)
    private var configuredC1 = 180f
    private var configuredC2 = 1800f
    private var configuredC3 = 8000f
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
            splitL = ZeusLr4BandSplitter(sampleRate, cross1, cross2, cross3)
            splitR = ZeusLr4BandSplitter(sampleRate, cross1, cross2, cross3)
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

            val g0 = bandGain(0, lb.low, rb.low)
            val g1 = bandGain(1, lb.lowMid, rb.lowMid)
            val g2 = bandGain(2, lb.highMid, rb.highMid)
            val g3 = bandGain(3, lb.high, rb.high)

            val outL =
                lb.low * g0.first + lb.lowMid * g1.first +
                lb.highMid * g2.first + lb.high * g3.first
            val outR =
                rb.low * g0.second + rb.lowMid * g1.second +
                rb.highMid * g2.second + rb.high * g3.second

            pcm[i] = clampAudio(outL)
            pcm[i + 1] = clampAudio(outR)
        }
    }

    fun reset() {
        splitL.reset()
        splitR.reset()
        gainDb.fill(0f)
    }

    private fun bandGain(band: Int, left: Float, right: Float): Pair<Float, Float> {
        val p = params[band]
        val pre = dbToLinear(p.preGain)
        val lPre = left * pre
        val rPre = right * pre

        val level = sqrt(max(1e-12f, (lPre * lPre + rPre * rPre) * 0.5f))
        val levelDb = 20f * kotlin.math.log10(level.coerceAtLeast(1e-6f))
        val targetGr = compressionGainDb(levelDb, p.threshold, p.ratio, p.knee)

        val coefficient = if (targetGr < gainDb[band]) {
            exp(-1f / (0.001f * p.attack * sampleRate))
        } else {
            exp(-1f / (0.001f * p.release * sampleRate))
        }
        gainDb[band] += (targetGr - gainDb[band]) * (1f - coefficient)

        val gain = dbToLinear(gainDb[band] + p.postGain)
        return Pair(lPre * gain, rPre * gain)
    }

    private fun compressionGainDb(levelDb: Float, threshold: Float, ratio: Float, knee: Float): Float {
        if (ratio <= 1.0001f || levelDb <= threshold - knee * 0.5f) return 0f
        if (knee <= 0.01f || levelDb >= threshold + knee * 0.5f) {
            return threshold + (levelDb - threshold) / ratio - levelDb
        }

        val x = levelDb - (threshold - knee * 0.5f)
        val compressed = x * x / (2f * knee * ratio)
        return -compressed
    }

    private fun crossoverChanged(): Boolean =
        abs(cross1 - configuredC1) > 0.01f ||
        abs(cross2 - configuredC2) > 0.01f ||
        abs(cross3 - configuredC3) > 0.01f

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private fun clampAudio(x: Float): Short {
        val shaped = if (abs(x) > 0.92f) kotlin.math.tanh(x.toDouble()).toFloat() else x
        return (shaped.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}
