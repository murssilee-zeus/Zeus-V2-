package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Four-branch Zeus Bass PCM chain.
 * Input cleanup/EQ/mono -> LR4 crossover -> SUB Epicenter -> SUB/Sismo
 * -> subsonic protection -> four linked compressors -> sum -> output gain
 * -> mandatory linked-stereo lookahead limiter.
 */
class ZeusBassDspPipeline(private val sampleRate: Int) {
    private val hpfL = Biquad(sampleRate)
    private val hpfR = Biquad(sampleRate)
    private val monoLpL = Biquad(sampleRate)
    private val monoLpR = Biquad(sampleRate)
    private val subProtectL = Biquad(sampleRate)
    private val subProtectR = Biquad(sampleRate)
    private val highLimitL = Biquad(sampleRate)
    private val highLimitR = Biquad(sampleRate)
    private val eqL = Array(16) { Biquad(sampleRate) }
    private val eqR = Array(16) { Biquad(sampleRate) }
    private var eqCount = 0

    @Volatile private var splitterL = ZeusLr4BandSplitter(sampleRate, 180f, 2400f, 7200f)
    @Volatile private var splitterR = ZeusLr4BandSplitter(sampleRate, 180f, 2400f, 7200f)
    private var crossover1 = 180f
    private var crossover2 = 2400f
    private var crossover3 = 7200f
    private var highLimitHz = 20000f

    private var branchLeft = Array(4) { FloatArray(2048) }
    private var branchRight = Array(4) { FloatArray(2048) }
    private var bassEpicenter: ZeusEpicenter? = null
    private var bassSubSismi: ZeusSubSismi? = null

    private val thresholds = floatArrayOf(-18f, -20f, -22f, -24f)
    private val ratios = floatArrayOf(2.5f, 2f, 2f, 1.5f)
    private val knees = floatArrayOf(3f, 3f, 3f, 3f)
    private val attacks = floatArrayOf(25f, 15f, 5f, 3f)
    private val releases = floatArrayOf(180f, 120f, 80f, 60f)
    private val preGains = FloatArray(4)
    private val postGains = FloatArray(4)
    private val grDb = FloatArray(4)

    private var bassMono = true
    private var outputGain = 1f
    private var compEnabled = true
    private var headroomGain = dbToLinear(-9f)

    // Five milliseconds of stereo lookahead. The gain detector sees incoming
    // peaks while the corresponding audio is still in the delay line.
    private val lookaheadFrames = (sampleRate.coerceAtLeast(8000) * 0.005f).toInt().coerceAtLeast(1)
    private val delayL = FloatArray(lookaheadFrames + 1)
    private val delayR = FloatArray(lookaheadFrames + 1)
    private var delayIndex = 0
    private var limiterGain = 1f
    private val ceiling = dbToLinear(-1f)
    private val limiterReleaseCoeff = 1f - exp(-1f / (0.080f * sampleRate.coerceAtLeast(8000)))

    init {
        configureSubsonic(30f)
        configureBassMono(true)
        configureCrossover(180f, 2400f, 7200f, 20000f)
        configureHighLimit(20000f)
    }

    fun setBassBranchProcessors(epicenter: ZeusEpicenter?, subSismi: ZeusSubSismi?) {
        bassEpicenter = epicenter
        bassSubSismi = subSismi
    }

    fun configureSubsonic(hz: Float) {
        val safe = hz.coerceIn(20f, 32f)
        hpfL.highPass(safe, 0.707f)
        hpfR.highPass(safe, 0.707f)
    }

    private fun configureSubBranchProtection(hz: Float = 30f) {
        val safe = hz.coerceIn(28f, 32f)
        subProtectL.highPass(safe, 0.707f)
        subProtectR.highPass(safe, 0.707f)
    }

    fun configureBassMono(enabled: Boolean, cutoffHz: Float = 120f) {
        bassMono = enabled
        val cutoff = cutoffHz.coerceIn(80f, 160f)
        monoLpL.lowPass(cutoff, 0.707f)
        monoLpR.lowPass(cutoff, 0.707f)
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

    /** Three crossover points define four branches; the top band ceiling is independent. */
    fun configureCrossover(c1Hz: Float, c2Hz: Float, c3Hz: Float, upperHz: Float = 20000f) {
        val nyquistSafe = (sampleRate * 0.45f).coerceAtLeast(100f)
        val c1 = c1Hz.coerceIn(80f, minOf(500f, nyquistSafe - 150f))
        val c2 = c2Hz.coerceIn(c1 + 100f, minOf(6000f, nyquistSafe - 100f))
        val c3 = c3Hz.coerceIn(c2 + 100f, nyquistSafe)
        val topMax = minOf(20000f, nyquistSafe)\n        val top = upperHz.coerceIn(minOf(c3 + 100f, topMax), topMax)
        if (abs(c1 - crossover1) > 0.01f || abs(c2 - crossover2) > 0.01f ||
            abs(c3 - crossover3) > 0.01f) {
            crossover1 = c1
            crossover2 = c2
            crossover3 = c3
            // Publish complete splitter instances instead of mutating filters on the audio thread.
            splitterL = ZeusLr4BandSplitter(sampleRate, c1, c2, c3)
            splitterR = ZeusLr4BandSplitter(sampleRate, c1, c2, c3)
        }
        configureHighLimit(top)
    }

    private fun configureHighLimit(hz: Float) {
        val maxAllowed = minOf(20000f, sampleRate * 0.45f).coerceAtLeast(7300f)\n        highLimitHz = hz.coerceIn(minOf(7300f, maxAllowed), maxAllowed)
        highLimitL.lowPass(highLimitHz, 0.707f)
        highLimitR.lowPass(highLimitHz, 0.707f)
    }

    /** Compatibility overload for older callers; the fourth value is the top-band ceiling. */
    fun configureCrossover(frequencies: FloatArray) {
        configureCrossover(
            frequencies.getOrElse(0) { 180f },
            frequencies.getOrElse(1) { 2400f },
            frequencies.getOrElse(2) { 7200f },
            frequencies.getOrElse(3) { 20000f }
        )
    }

    fun configureHeadroom(bands: List<EqBand>, epicenterDrivePercent: Float) {
        val positiveEqBoost = bands.filter { it.enabled && it.filterType != EqBand.FilterType.BYPASS }
            .sumOf { it.gain.coerceAtLeast(0f).toDouble() }.toFloat()
        val driveReserve = epicenterDrivePercent.coerceIn(0f, 100f) * 0.06f
        // Keep at least the agreed 9 dB reserve; add further attenuation for
        // unusually large EQ boosts or Epicenter drive, plus a 3 dB margin.
        val attenuationDb = max(9f, positiveEqBoost + driveReserve + 3f).coerceAtMost(24f)
        headroomGain = dbToLinear(-attenuationDb)
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
        for (i in 0..3) {
            thresholds[i] = thresholdDb.getOrElse(i) { thresholds[i] }.coerceIn(-60f, 0f)
            ratios[i] = ratio.getOrElse(i) { ratios[i] }.coerceIn(1f, 24f)
            knees[i] = kneeDb.getOrElse(i) { knees[i] }.coerceIn(0f, 20f)
            attacks[i] = attackMs.getOrElse(i) { attacks[i] }.coerceIn(0.5f, 200f)
            releases[i] = releaseMs.getOrElse(i) { releases[i] }.coerceIn(10f, 1000f)
            preGains[i] = preGainDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            postGains[i] = postGainDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
        }
    }

    /** Output gain is before the fixed -1 dBFS final limiter. Limiter cannot be disabled. */
    fun configureOutput(gainDb: Float, limiter: Boolean = true, ceilingDbfs: Float = -1f) {
        outputGain = dbToLinear(gainDb.coerceIn(-24f, 12f))
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
            pcm[i] = l * headroomGain
            pcm[i + 1] = r * headroomGain
        }
    }

    fun processPost(pcm: FloatArray, size: Int = pcm.size) {
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (n < 2) return
        ensureBranchCapacity(n)
        val leftSplitter = splitterL
        val rightSplitter = splitterR

        for (i in 0 until n step 2) {
            val lb = leftSplitter.process(pcm[i])
            val rb = rightSplitter.process(pcm[i + 1])
            branchLeft[0][i] = lb.low
            branchRight[0][i] = rb.low
            branchLeft[1][i] = lb.lowMid
            branchRight[1][i] = rb.lowMid
            branchLeft[2][i] = lb.highMid
            branchRight[2][i] = rb.highMid
            branchLeft[3][i] = highLimitL.process(lb.high)
            branchRight[3][i] = highLimitR.process(rb.high)
        }

        // SUB-only order is intentional: Epicenter first, Sub/Sismo second,
        // then the 28-32 Hz protective high-pass and B1 compressor.
        val sub = interleaveBranch(0, n)
        bassEpicenter?.let {
            it.enabled = true
            it.processStereo(sub, n)
        }
        bassSubSismi?.processStereo(sub, n)
        for (i in 0 until n step 2) {
            branchLeft[0][i] = sub[i]
            branchRight[0][i] = sub[i + 1]
            branchLeft[0][i] = subProtectL.process(branchLeft[0][i])
            branchRight[0][i] = subProtectR.process(branchRight[0][i])
        }

        var delayWrite = delayIndex
        for (i in 0 until n step 2) {
            var outL = 0f
            var outR = 0f
            for (b in 0..3) {
                val l = if (b == 0) branchLeft[b][i] else branchLeft[b][i]
                val r = if (b == 0) branchRight[b][i] else branchRight[b][i]
                val gain = bandGain(b, l, r)
                outL += l * gain
                outR += r * gain
            }
            outL *= outputGain
            outR *= outputGain

            val peak = max(abs(outL), abs(outR))
            val targetGain = if (peak > ceiling) ceiling / peak else 1f
            limiterGain = if (targetGain < limiterGain) targetGain
                else limiterGain + limiterReleaseCoeff * (targetGain - limiterGain)

            val delayedL = delayL[delayWrite]
            val delayedR = delayR[delayWrite]
            delayL[delayWrite] = outL
            delayR[delayWrite] = outR
            pcm[i] = (delayedL * limiterGain).coerceIn(-ceiling, ceiling)
            pcm[i + 1] = (delayedR * limiterGain).coerceIn(-ceiling, ceiling)
            delayWrite++
            if (delayWrite >= delayL.size) delayWrite = 0
        }
        delayIndex = delayWrite
    }

    private fun interleaveBranch(branch: Int, n: Int): FloatArray {
        // Reuse the left branch as a compact interleaved scratch buffer.
        val target = branchLeft[branch]
        for (i in 0 until n step 2) {
            target[i + 1] = branchRight[branch][i]
        }
        return target
    }

    private fun ensureBranchCapacity(n: Int) {
        if (branchLeft[0].size >= n) return
        val capacity = n.coerceAtLeast(2048)
        branchLeft = Array(4) { FloatArray(capacity) }
        branchRight = Array(4) { FloatArray(capacity) }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        processPre(pcm, size)
        processPost(pcm, size)
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
        val target = if (db <= threshold - knee * 0.5f) 0f
        else if (knee <= 0.01f || db >= threshold + knee * 0.5f) {
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
        subProtectL.reset(); subProtectR.reset()
        highLimitL.reset(); highLimitR.reset()
        eqL.forEach { it.reset() }; eqR.forEach { it.reset() }
        splitterL.reset(); splitterR.reset()
        grDb.fill(0f)
        delayL.fill(0f); delayR.fill(0f)
        delayIndex = 0
        limiterGain = 1f
        bassEpicenter?.reset()
        bassSubSismi?.reset()
    }

    private fun finite(v: Float) = if (v.isFinite()) v.coerceIn(-2f, 2f) else 0f
    private fun dbToLinear(db: Float) = 10f.pow(db / 20f)
}
