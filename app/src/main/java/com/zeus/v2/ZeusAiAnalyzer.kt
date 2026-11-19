package com.zeus.v2

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.ln
import kotlin.math.max

/**
 * Block-level audio analysis for Zeus AI.
 *
 * Feed interleaved stereo Float PCM before adaptive DSP decisions are applied.
 * The sample loop performs no object allocation; one immutable Metrics snapshot
 * is published per analyzed block for consumers on non-audio threads.
 */
class ZeusAiAnalyzer(sampleRate: Int = 48000) {
    data class Metrics(
        val subRmsDbfs: Float = -120f,
        val bassRmsDbfs: Float = -120f,
        val midRmsDbfs: Float = -120f,
        val highRmsDbfs: Float = -120f,
        val broadbandRmsDbfs: Float = -120f,
        val peakDbfs: Float = -120f,
        val headroomDb: Float = 0f,
        val transientActivity: Float = 0f,
        val crestDb: Float = 0f,
        val stereoCorrelation: Float = 0f,
        val sideEnergyPercent: Float = 0f,
        val framesAnalyzed: Int = 0
    )

    private val sr = sampleRate.coerceIn(8000, 192000).toFloat()
    private var lp60L = 0f
    private var lp60R = 0f
    private var lp150L = 0f
    private var lp150R = 0f
    private var lp2000L = 0f
    private var lp2000R = 0f
    private var fastEnvelope = 0f
    private var slowEnvelope = 0f

    private val a60 = coefficient(60f)
    private val a150 = coefficient(150f)
    private val a2000 = coefficient(2000f)
    private val fastA = coefficient(35f)
    private val slowA = coefficient(4f)
    private val metricA = coefficient(3f)

    @Volatile
    var metrics: Metrics = Metrics()
        private set

    fun reset() {
        lp60L = 0f; lp60R = 0f
        lp150L = 0f; lp150R = 0f
        lp2000L = 0f; lp2000R = 0f
        fastEnvelope = 0f; slowEnvelope = 0f
        metrics = Metrics()
    }

    /**
     * Analyze interleaved stereo Float PCM.
     * offset is a sample index; frames is a stereo-frame count.
     */
    fun analyze(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2): Metrics {
        val start = offset.coerceIn(0, buffer.size)
        val safeFrames = frames.coerceAtLeast(0).coerceAtMost((buffer.size - start) / 2)
        if (safeFrames < 1) return metrics

        var subEnergy = 0.0
        var bassEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        var totalEnergy = 0.0
        var centerEnergy = 0.0
        var sideEnergy = 0.0
        var sumLL = 0.0
        var sumRR = 0.0
        var sumLR = 0.0
        var peak = 0f
        var transientSamples = 0
        var i = start
        val end = start + safeFrames * 2

        while (i + 1 < end) {
            val l = finite(buffer[i])
            val r = finite(buffer[i + 1])
            val mono = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            lp60L += a60 * (l - lp60L)
            lp60R += a60 * (r - lp60R)
            lp150L += a150 * (l - lp150L)
            lp150R += a150 * (r - lp150R)
            lp2000L += a2000 * (l - lp2000L)
            lp2000R += a2000 * (r - lp2000R)

            val subL = lp60L
            val subR = lp60R
            val bassL = lp150L - lp60L
            val bassR = lp150R - lp60R
            val midL = lp2000L - lp150L
            val midR = lp2000R - lp150R
            val highL = l - lp2000L
            val highR = r - lp2000R

            subEnergy += (subL.toDouble() * subL + subR.toDouble() * subR) * 0.5
            bassEnergy += (bassL.toDouble() * bassL + bassR.toDouble() * bassR) * 0.5
            midEnergy += (midL.toDouble() * midL + midR.toDouble() * midR) * 0.5
            highEnergy += (highL.toDouble() * highL + highR.toDouble() * highR) * 0.5
            totalEnergy += (l.toDouble() * l + r.toDouble() * r) * 0.5
            centerEnergy += mono.toDouble() * mono
            sideEnergy += side.toDouble() * side

            sumLL += l.toDouble() * l
            sumRR += r.toDouble() * r
            sumLR += l.toDouble() * r
            peak = max(peak, max(abs(l), abs(r)))

            val absMono = abs(mono)
            fastEnvelope += fastA * (absMono - fastEnvelope)
            slowEnvelope += slowA * (absMono - slowEnvelope)
            val transientRatio = (fastEnvelope - slowEnvelope) / (slowEnvelope + 1e-5f)
            if (transientRatio > 1.15f && absMono > 0.003f) {
                transientSamples++
            }
            i += 2
        }

        val n = safeFrames.toDouble().coerceAtLeast(1.0)
        val subDb = rmsDb(subEnergy / n)
        val bassDb = rmsDb(bassEnergy / n)
        val midDb = rmsDb(midEnergy / n)
        val highDb = rmsDb(highEnergy / n)
        val broadbandDb = rmsDb(totalEnergy / n)
        val peakDb = amplitudeDb(peak)
        val corrDenom = sqrt(max(1e-20, sumLL * sumRR))
        val corr = (sumLR / corrDenom).toFloat().coerceIn(-1f, 1f)
        val sidePct = (sideEnergy / (sideEnergy + centerEnergy + 1e-20) * 100.0).toFloat().coerceIn(0f, 100f)
        val crest = (peakDb - broadbandDb).coerceIn(0f, 60f)
        val activity = (transientSamples.toFloat() / safeFrames * 100f).coerceIn(0f, 100f)

        val old = metrics
        metrics = Metrics(
            smooth(old.subRmsDbfs, subDb),
            smooth(old.bassRmsDbfs, bassDb),
            smooth(old.midRmsDbfs, midDb),
            smooth(old.highRmsDbfs, highDb),
            smooth(old.broadbandRmsDbfs, broadbandDb),
            smooth(old.peakDbfs, peakDb),
            (0f - peakDb).coerceIn(0f, 120f),
            smooth(old.transientActivity, activity),
            smooth(old.crestDb, crest),
            smooth(old.stereoCorrelation, corr),
            smooth(old.sideEnergyPercent, sidePct),
            safeFrames
        )
        return metrics
    }

    private fun smooth(previous: Float, current: Float): Float {
        if (previous <= -119f && current > -119f) return current
        return previous + metricA * (current - previous)
    }

    private fun finite(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f

    private fun rmsDb(power: Double): Float =
        if (power <= 1e-20 || !power.isFinite()) -120f
        else (10.0 * log10Safe(power)).toFloat().coerceIn(-120f, 0f)

    private fun amplitudeDb(value: Float): Float =
        if (value <= 1e-6f) -120f else (20.0 * log10Safe(value.toDouble())).toFloat().coerceIn(-120f, 0f)

    private fun log10Safe(value: Double): Double = ln(value.coerceAtLeast(1e-20)) / ln(10.0)

    private fun coefficient(cutoffHz: Float): Float {
        val cutoff = cutoffHz.coerceIn(1f, sr * 0.45f)
        val x = (2f * Math.PI.toFloat() * cutoff / sr).coerceIn(0.0001f, 0.45f)
        return x / (1f + x)
    }
}
