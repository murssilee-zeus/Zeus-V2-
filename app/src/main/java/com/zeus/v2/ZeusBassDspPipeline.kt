package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Float PCM Zeus Bass chain. Processing order:
 * subsonic -> parametric EQ -> bass mono -> crossover -> linked band dynamics
 * -> output gain -> peak ceiling.
 * Bass/Epicenter generators are deliberately upstream in AudioEngine.
 */
class ZeusBassDspPipeline(private val sampleRate: Int) {
    private val hpfL = Biquad(sampleRate)
    private val hpfR = Biquad(sampleRate)
    private val monoLpL = Biquad(sampleRate)
    private val monoLpR = Biquad(sampleRate)
    private val eqL = Array(16) { Biquad(sampleRate) }
    private val eqR = Array(16) { Biquad(sampleRate) }
    private var eqCount = 0

    private val splitL = Array(4) { Pair(Biquad(sampleRate), Biquad(sampleRate)) }
    private val splitR = Array(4) { Pair(Biquad(sampleRate), Biquad(sampleRate)) }
    private val cutoffs = floatArrayOf(180f, 2400f, 7200f, 18300f)
    private val configured = FloatArray(4) { -1f }
    private val bandsScratchL = FloatArray(5)
    private val bandsScratchR = FloatArray(5)

    private val thresholds = floatArrayOf(-18f, -14f, -12f, -14f, 0f)
    private val ratios = floatArrayOf(4f, 3f, 2.5f, 3.5f, 1f)
    private val knees = floatArrayOf(6f, 6f, 6f, 6f, 0f)
    private val attacks = floatArrayOf(15f, 12f, 8f, 5f, 10f)
    private val releases = floatArrayOf(180f, 120f, 90f, 60f, 100f)
    private val preGains = FloatArray(5)
    private val postGains = FloatArray(5)
    private val grDb = FloatArray(5)

    private var bassMono = true
    private var outputGain = 1f
    private var compEnabled = true
    private var limiterEnabled = true
    private var ceiling = 10f.pow(-1f / 20f)
    private var limiterGain = 1f
    private val limiterReleaseCoeff = 1f - exp(-1f / (0.050f * sampleRate.coerceAtLeast(8000)))

    init {
        configureCrossover(cutoffs)
        configureSubsonic(30f)
        configureBassMono(true)
    }

    fun configureSubsonic(hz: Float) {
        val safe = hz.coerceIn(24f, 40f)
        hpfL.highPass(safe, 0.707f)
        hpfR.highPass(safe, 0.707f)
    }

    fun configureBassMono(enabled: Boolean, cutoffHz: Float = 120f) {
        bassMono = enabled
        monoLpL.lowPass(cutoffHz.coerceIn(80f, 160f), 0.707f)
        monoLpR.lowPass(cutoffHz.coerceIn(80f, 160f), 0.707f)
    }

    fun configureEq(bands: List<EqBand>) {
        eqCount = bands.size.coerceAtMost(eqL.size)
        for (i in 0 until eqCount) {
            val b = bands[i]
            val f = b.frequency.coerceIn(20f, (sampleRate * 0.45f).coerceAtLeast(20f))
            val q = b.q.coerceIn(0.1f, 12f)
            val gain = b.gain.coerceIn(-18f, 18f)
            val left = eqL[i]
            val right = eqR[i]
            if (!b.enabled || b.filterType == EqBand.FilterType.BYPASS) {
                left.resetCoefficientsToIdentity()
                right.resetCoefficientsToIdentity()
                continue
            }
            when (b.filterType) {
                EqBand.FilterType.LOW_SHELF -> { left.lowShelf(f, gain); right.lowShelf(f, gain) }
                EqBand.FilterType.HIGH_SHELF -> { left.highShelf(f, gain); right.highShelf(f, gain) }
                EqBand.FilterType.PEAK -> { left.peak(f, gain, q); right.peak(f, gain, q) }
                EqBand.FilterType.LOW_PASS -> { left.lowPass(f, q); right.lowPass(f, q) }
                EqBand.FilterType.HIGH_PASS -> { left.highPass(f, q); right.highPass(f, q) }
                EqBand.FilterType.NOTCH -> { left.notch(f, q); right.notch(f, q) }
                EqBand.FilterType.BAND_PASS -> { left.bandPass(f, q); right.bandPass(f, q) }
                EqBand.FilterType.BYPASS -> Unit
            }
        }
    }

    fun configureCrossover(frequencies: FloatArray = cutoffs) {
        for (i in 0..3) {
            val safeMax = (sampleRate * 0.45f).coerceAtLeast(100f)
            val f = frequencies.getOrElse(i) { cutoffs[i] }.coerceIn(30f, safeMax)
            cutoffs[i] = f
            if (abs(configured[i] - f) < 0.01f) continue
            // LR4 = two cascaded Butterworth biquads in each branch.
            splitL[i].first.lowPass(f, 0.707f)
            splitL[i].second.lowPass(f, 0.707f)
            splitR[i].first.lowPass(f, 0.707f)
            splitR[i].second.lowPass(f, 0.707f)
            configured[i] = f
        }
    }

    fun configureDynamics(
        enabled: Boolean,
        thresholdDb: FloatArray,
        ratio: FloatArray,
        kneeDb: FloatArray,
        attackMs: FloatArray,
        releaseMs: FloatArray,
        preGainDb: FloatArray,
        postGainDb: FloatArray
    ) {
        compEnabled = enabled
        for (i in 0..4) {
            thresholds[i] = thresholdDb.getOrElse(i) { if (i == 4) 0f else -18f }.coerceIn(-60f, 0f)
            ratios[i] = ratio.getOrElse(i) { 1f }.coerceIn(1f, 24f)
            knees[i] = kneeDb.getOrElse(i) { 0f }.coerceIn(0f, 20f)
            attacks[i] = attackMs.getOrElse(i) { 10f }.coerceIn(0.5f, 200f)
            releases[i] = releaseMs.getOrElse(i) { 100f }.coerceIn(10f, 1000f)
            preGains[i] = preGainDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            postGains[i] = postGainDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
        }
    }

    fun configureOutput(gainDb: Float, limiter: Boolean = true, ceilingDbfs: Float = -1f) {
        outputGain = 10f.pow(gainDb.coerceIn(-24f, 12f) / 20f)
        limiterEnabled = limiter
        ceiling = 10f.pow(ceilingDbfs.coerceIn(-6f, -0.1f) / 20f)
    }

    fun processPre(pcm: FloatArray, size: Int = pcm.size) {
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            var l = finite(pcm[i])
            var r = finite(pcm[i + 1])
            l = hpfL.process(l)
            r = hpfR.process(r)
            for (b in 0 until eqCount) {
                l = eqL[b].process(l)
                r = eqR[b].process(r)
            }
            if (bassMono) {
                val lowL = monoLpL.process(l)
                val lowR = monoLpR.process(r)
                val center = (lowL + lowR) * 0.5f
                l += center - lowL
                r += center - lowR
            }
            pcm[i] = l
            pcm[i + 1] = r
        }
    }

    fun processPost(pcm: FloatArray, size: Int = pcm.size) {
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (n < 2) return
        for (i in 0 until n step 2) {
            splitInto(pcm[i], splitL, bandsScratchL)
            splitInto(pcm[i + 1], splitR, bandsScratchR)
            var outL = 0f
            var outR = 0f
            for (b in 0..4) {
                val gain = bandGain(b, bandsScratchL[b], bandsScratchR[b])
                outL += bandsScratchL[b] * gain
                outR += bandsScratchR[b] * gain
            }
            outL *= outputGain
            outR *= outputGain
            if (limiterEnabled) {
                val peak = max(abs(outL), abs(outR))
                val targetGain = if (peak > ceiling) ceiling / peak else 1f
                // Instant attack, smoothed 50 ms release, stereo-linked.
                limiterGain = if (targetGain < limiterGain) targetGain
                    else limiterGain + limiterReleaseCoeff * (targetGain - limiterGain)
                outL = (outL * limiterGain).coerceIn(-ceiling, ceiling)
                outR = (outR * limiterGain).coerceIn(-ceiling, ceiling)
            } else {
                limiterGain = 1f
                outL = outL.coerceIn(-1f, 1f)
                outR = outR.coerceIn(-1f, 1f)
            }
            pcm[i] = outL
            pcm[i + 1] = outR
        }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        processPre(pcm, size)
        processPost(pcm, size)
    }

    private fun splitInto(input: Float, filters: Array<Pair<Biquad, Biquad>>, scratch: FloatArray) {
        var residual = input
        for (i in 0..3) {
            val lp = filters[i].second.process(filters[i].first.process(residual))
            scratch[i] = lp
            residual -= lp
        }
        scratch[4] = residual
    }

    private fun bandGain(band: Int, left: Float, right: Float): Float {
        val post = dbToLinear(postGains[band])
        if (!compEnabled || ratios[band] <= 1.0001f) return post
        val pre = dbToLinear(preGains[band])
        val level = sqrt(max(1e-12f, ((left * pre).pow(2) + (right * pre).pow(2)) * 0.5f))
        val db = 20f * kotlin.math.log10(level.coerceAtLeast(1e-6f))
        val threshold = thresholds[band]
        val ratio = ratios[band]
        val knee = knees[band]
        val target = if (db <= threshold - knee * 0.5f) 0f else if (knee <= 0.01f || db >= threshold + knee * 0.5f) {
            threshold + (db - threshold) / ratio - db
        } else {
            val x = db - (threshold - knee * 0.5f)
            -x * x * (1f - 1f / ratio) / (2f * knee)
        }
        val coeff = if (target < grDb[band]) {
            exp(-1f / (0.001f * attacks[band] * sampleRate))
        } else {
            exp(-1f / (0.001f * releases[band] * sampleRate))
        }
        grDb[band] += (target - grDb[band]) * (1f - coeff)
        return dbToLinear(grDb[band]) * post
    }

    fun reset() {
        hpfL.reset(); hpfR.reset()
        monoLpL.reset(); monoLpR.reset()
        eqL.forEach { it.reset() }; eqR.forEach { it.reset() }
        splitL.forEach { it.first.reset(); it.second.reset() }
        splitR.forEach { it.first.reset(); it.second.reset() }
        grDb.fill(0f)
        limiterGain = 1f
    }

    private fun finite(v: Float) = if (v.isFinite()) v.coerceIn(-2f, 2f) else 0f
    private fun dbToLinear(db: Float) = 10f.pow(db / 20f)
}
