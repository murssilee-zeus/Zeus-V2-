package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time Float PCM DSP chain used by PcmAudioEngine.
 * Order: input gain -> Bass Engine -> parametric EQ -> complementary 4-band
 * crossover -> independent band compression -> sum -> limiter.
 * No per-sample allocations are used in the processing loop.
 */
class PcmDspChain(sampleRate: Int = 48000) {
    private var sr = sampleRate.coerceIn(8000, 192000)
    private val bass = BassEngine(sr.toFloat())
    private var eq = emptyList<PcmBiquad>()

    // Per-channel complementary crossover: LP(c1), HP(c1), LP(c2), HP(c2), LP(c3), HP(c3).
    private val crossL = Array(6) { PcmBiquad(1000f, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat()) }
    private val crossR = Array(6) { PcmBiquad(1000f, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat()) }

    // Independent linked-stereo envelopes and smoothed gains for L/LM/HM/H.
    private val env = FloatArray(4)
    private val compGain = floatArrayOf(1f, 1f, 1f, 1f)
    private val attackCoeff = FloatArray(4)
    private val releaseCoeff = FloatArray(4)
    private val thresholdDb = FloatArray(4)
    private val ratio = FloatArray(4)
    private val kneeDb = FloatArray(4)
    private val preGain = FloatArray(4)
    private val postGain = FloatArray(4)

    private var limiterGain = 1f
    private var limiterThresholdDb = -2.5f
    private var limiterAttackCoeff = 0f
    private var limiterReleaseCoeff = 0f
    private var limiterRatio = 20f
    private var limiterPost = 1f
    private var settings: EqSettings? = null
    private var configured = false

    var enabled = true
    var limiterEnabled = true
    var limiterAttackMs = 0.5f
    var limiterReleaseMs = 120f

    fun configure(sampleRate: Int, source: EqSettings) {
        val newSr = sampleRate.coerceIn(8000, 192000)
        if (newSr != sr) {
            sr = newSr
            reset()
        }
        settings = source
        bass.bassAmount = source.subBoost.coerceIn(0f, 100f)
        bass.harmonicAmount = source.subBoost.coerceIn(0f, 100f)
        rebuildEq(source)
        configureDynamics(source)
        configured = true
    }

    fun setBass(amount: Float, punch: Float, harmonics: Float) {
        bass.bassAmount = amount
        bass.punchAmount = punch
        bass.harmonicAmount = harmonics
    }

    fun reset() {
        bass.reset()
        eq = emptyList()
        var n = 0
        while (n < 6) {
            crossL[n].reset()
            crossR[n].reset()
            n++
        }
        env.fill(0f)
        compGain[0] = 1f; compGain[1] = 1f; compGain[2] = 1f; compGain[3] = 1f
        limiterGain = 1f
        configured = false
    }

    fun process(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2) {
        if (!enabled || buffer.isEmpty()) return
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeFrames = frames.coerceAtMost((buffer.size - safeOffset) / 2).coerceAtLeast(0)
        if (safeFrames == 0) return

        val s = settings
        if (s == null || !configured) {
            bass.processStereo(buffer, sr, safeOffset, safeFrames)
            applyLimiter(buffer, safeOffset, safeFrames)
            return
        }

        val inputGain = dbToLinear(s.preGain.coerceIn(-30f, 12f))
        var i = safeOffset
        val end = safeOffset + safeFrames * 2
        while (i + 1 < end) {
            var l = buffer[i] * inputGain
            var r = buffer[i + 1] * inputGain
            var b = 0
            while (b < eq.size) {
                l = eq[b].process(l)
                r = eq[b + 1].process(r)
                b += 2
            }
            buffer[i] = l
            buffer[i + 1] = r
            i += 2
        }

        // Bass is intentionally before multiband dynamics. The EQ is also before
        // the crossover so its curve is evaluated before the compressor splits bands.
        bass.processStereo(buffer, sr, safeOffset, safeFrames)
        applyMbc(buffer, safeOffset, safeFrames)
        applyLimiter(buffer, safeOffset, safeFrames)
    }

    private fun rebuildEq(s: EqSettings) {
        val list = ArrayList<PcmBiquad>()
        for (band in s.bands) {
            if (!band.enabled || band.filterType == EqBand.FilterType.BYPASS) continue
            val type = when (band.filterType) {
                EqBand.FilterType.LOW_SHELF -> PcmBiquad.Type.LOW_SHELF
                EqBand.FilterType.HIGH_SHELF -> PcmBiquad.Type.HIGH_SHELF
                EqBand.FilterType.PEAK -> PcmBiquad.Type.PEAK
                EqBand.FilterType.LOW_PASS -> PcmBiquad.Type.LOW_PASS
                EqBand.FilterType.HIGH_PASS -> PcmBiquad.Type.HIGH_PASS
                EqBand.FilterType.NOTCH -> PcmBiquad.Type.NOTCH
                EqBand.FilterType.BAND_PASS -> PcmBiquad.Type.BAND_PASS
                EqBand.FilterType.BYPASS -> null
            }
            if (type != null) {
                list.add(PcmBiquad(band.frequency, band.gain, band.q, type, sr.toFloat()))
                list.add(PcmBiquad(band.frequency, band.gain, band.q, type, sr.toFloat()))
            }
        }
        eq = list
    }

    private fun configureDynamics(s: EqSettings) {
        val c1 = s.cross1.coerceIn(40f, 1000f)
        val c2 = s.cross2.coerceIn(c1 + 50f, 8000f)
        val c3 = s.cross3.coerceIn(c2 + 50f, sr * 0.45f)
        configureCrossPair(crossL, c1, c2, c3)
        configureCrossPair(crossR, c1, c2, c3)

        thresholdDb[0] = s.compThLow.coerceIn(-60f, 0f)
        thresholdDb[1] = s.compThLoMid.coerceIn(-60f, 0f)
        thresholdDb[2] = s.compThHiMid.coerceIn(-60f, 0f)
        thresholdDb[3] = s.compThHigh.coerceIn(-60f, 0f)
        ratio[0] = s.compRatioLow.coerceIn(1f, 24f)
        ratio[1] = s.compRatioLoMid.coerceIn(1f, 24f)
        ratio[2] = s.compRatioHiMid.coerceIn(1f, 24f)
        ratio[3] = s.compRatioHigh.coerceIn(1f, 24f)
        kneeDb[0] = s.compKneeLow.coerceIn(0f, 24f)
        kneeDb[1] = s.compKneeLoMid.coerceIn(0f, 24f)
        kneeDb[2] = s.compKneeHiMid.coerceIn(0f, 24f)
        kneeDb[3] = s.compKneeHigh.coerceIn(0f, 24f)

        preGain[0] = dbToLinear(s.compPreGainLow.coerceIn(-24f, 24f))
        preGain[1] = dbToLinear(s.compPreGainLoMid.coerceIn(-24f, 24f))
        preGain[2] = dbToLinear(s.compPreGainHiMid.coerceIn(-24f, 24f))
        preGain[3] = dbToLinear(s.compPreGainHigh.coerceIn(-24f, 24f))
        postGain[0] = dbToLinear(s.compPostGainLow.coerceIn(-24f, 24f))
        postGain[1] = dbToLinear(s.compPostGainLoMid.coerceIn(-24f, 24f))
        postGain[2] = dbToLinear(s.compPostGainHiMid.coerceIn(-24f, 24f))
        postGain[3] = dbToLinear(s.compPostGainHigh.coerceIn(-24f, 24f))

        attackCoeff[0] = timeCoeff(s.compAttackLow)
        attackCoeff[1] = timeCoeff(s.compAttackLoMid)
        attackCoeff[2] = timeCoeff(s.compAttackHiMid)
        attackCoeff[3] = timeCoeff(s.compAttackHigh)
        releaseCoeff[0] = timeCoeff(s.compReleaseLow)
        releaseCoeff[1] = timeCoeff(s.compReleaseLoMid)
        releaseCoeff[2] = timeCoeff(s.compReleaseHiMid)
        releaseCoeff[3] = timeCoeff(s.compReleaseHigh)

        limiterThresholdDb = s.limiterThreshold.coerceIn(-30f, 0f)
        limiterAttackMs = s.limiterAttack.coerceIn(0.01f, 100f)
        limiterReleaseMs = s.limiterRelease.coerceIn(20f, 1000f)
        limiterRatio = s.limiterRatio.coerceIn(1f, 50f)
        limiterPost = dbToLinear(s.limiterPostGain.coerceIn(-12f, 12f))
        limiterAttackCoeff = timeCoeff(limiterAttackMs)
        limiterReleaseCoeff = timeCoeff(limiterReleaseMs)
    }

    private fun configureCrossPair(filters: Array<PcmBiquad>, c1: Float, c2: Float, c3: Float) {
        filters[0].configure(c1, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat())
        filters[1].configure(c1, 0f, 0.707f, PcmBiquad.Type.HIGH_PASS, sr.toFloat())
        filters[2].configure(c2, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat())
        filters[3].configure(c2, 0f, 0.707f, PcmBiquad.Type.HIGH_PASS, sr.toFloat())
        filters[4].configure(c3, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat())
        filters[5].configure(c3, 0f, 0.707f, PcmBiquad.Type.HIGH_PASS, sr.toFloat())
    }

    private fun applyMbc(buffer: FloatArray, offset: Int, frames: Int) {
        val s = settings ?: return
        if (!s.compEnabled) return

        var i = offset
        val end = offset + frames * 2
        while (i + 1 < end) {
            val inL = buffer[i]
            val inR = buffer[i + 1]

            // Complementary serial split. Each band covers the remaining spectrum
            // without classifying a sample by amplitude or allocating buffers.
            val lowL = crossL[0].process(inL)
            val lowR = crossR[0].process(inR)

            val hp1L = crossL[1].process(inL)
            val hp1R = crossR[1].process(inR)
            val loMidL = crossL[2].process(hp1L)
            val loMidR = crossR[2].process(hp1R)

            val hp2L = crossL[3].process(hp1L)
            val hp2R = crossR[3].process(hp1R)
            val hiMidL = crossL[4].process(hp2L)
            val hiMidR = crossR[4].process(hp2R)

            val highL = crossL[5].process(hp2L)
            val highR = crossR[5].process(hp2R)

            val e0 = linkedLevel(lowL, lowR) * preGain[0]
            val e1 = linkedLevel(loMidL, loMidR) * preGain[1]
            val e2 = linkedLevel(hiMidL, hiMidR) * preGain[2]
            val e3 = linkedLevel(highL, highR) * preGain[3]

            env[0] = follow(env[0], e0, attackCoeff[0], releaseCoeff[0])
            env[1] = follow(env[1], e1, attackCoeff[1], releaseCoeff[1])
            env[2] = follow(env[2], e2, attackCoeff[2], releaseCoeff[2])
            env[3] = follow(env[3], e3, attackCoeff[3], releaseCoeff[3])

            val target0 = compressionGainDb(env[0], thresholdDb[0], ratio[0], kneeDb[0])
            val target1 = compressionGainDb(env[1], thresholdDb[1], ratio[1], kneeDb[1])
            val target2 = compressionGainDb(env[2], thresholdDb[2], ratio[2], kneeDb[2])
            val target3 = compressionGainDb(env[3], thresholdDb[3], ratio[3], kneeDb[3])

            compGain[0] = smoothGain(compGain[0], target0, attackCoeff[0], releaseCoeff[0])
            compGain[1] = smoothGain(compGain[1], target1, attackCoeff[1], releaseCoeff[1])
            compGain[2] = smoothGain(compGain[2], target2, attackCoeff[2], releaseCoeff[2])
            compGain[3] = smoothGain(compGain[3], target3, attackCoeff[3], releaseCoeff[3])

            buffer[i] = lowL * compGain[0] * postGain[0] + loMidL * compGain[1] * postGain[1] + hiMidL * compGain[2] * postGain[2] + highL * compGain[3] * postGain[3]
            buffer[i + 1] = lowR * compGain[0] * postGain[0] + loMidR * compGain[1] * postGain[1] + hiMidR * compGain[2] * postGain[2] + highR * compGain[3] * postGain[3]
            i += 2
        }
    }

    private fun linkedLevel(l: Float, r: Float): Float = max(abs(l), abs(r)).coerceAtLeast(1e-7f)

    private fun follow(current: Float, input: Float, attack: Float, release: Float): Float {
        return if (input > current) attack * current + (1f - attack) * input
        else release * current + (1f - release) * input
    }

    private fun compressionGainDb(level: Float, threshold: Float, ratio: Float, knee: Float): Float {
        val levelDb = 20f * kotlin.math.log10(level.coerceAtLeast(1e-7f))
        val over = levelDb - threshold
        val reductionDb = if (knee <= 0f) {
            if (over > 0f) over - over / ratio else 0f
        } else {
            val lower = -knee * 0.5f
            val upper = knee * 0.5f
            when {
                over <= lower -> 0f
                over >= upper -> over - over / ratio
                else -> {
                    val x = over - lower
                    x * x * (1f / ratio - 1f) / (2f * knee)
                }
            }
        }
        return dbToLinear(-reductionDb)
    }

    private fun smoothGain(current: Float, target: Float, attack: Float, release: Float): Float {
        return if (target < current) attack * current + (1f - attack) * target
        else release * current + (1f - release) * target
    }

    private fun applyLimiter(buffer: FloatArray, offset: Int, frames: Int) {
        if (!limiterEnabled) return
        var i = offset
        val end = offset + frames * 2
        while (i + 1 < end) {
            val dryL = buffer[i] * limiterPost
            val dryR = buffer[i + 1] * limiterPost
            val peak = max(abs(dryL), abs(dryR)).coerceAtLeast(1e-7f)
            val levelDb = 20f * kotlin.math.log10(peak)
            val overDb = levelDb - limiterThresholdDb
            val reductionDb = if (overDb > 0f) overDb - overDb / limiterRatio else 0f
            val target = dbToLinear(-reductionDb)
            limiterGain = if (target < limiterGain) {
                limiterAttackCoeff * limiterGain + (1f - limiterAttackCoeff) * target
            } else {
                limiterReleaseCoeff * limiterGain + (1f - limiterReleaseCoeff) * target
            }
            buffer[i] = dryL * limiterGain
            buffer[i + 1] = dryR * limiterGain
            i += 2
        }
    }

    private fun timeCoeff(ms: Float): Float {
        val seconds = (ms.coerceIn(0.01f, 2000f) / 1000f).toDouble()
        return exp(-1.0 / (sr.toDouble() * seconds)).toFloat()
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    private class PcmBiquad(
        frequency: Float,
        gainDb: Float,
        q: Float,
        type: Type,
        sampleRate: Float
    ) {
        enum class Type { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH, BAND_PASS }
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        init { configure(frequency, gainDb, q, type, sampleRate) }

        fun configure(frequency: Float, gainDb: Float, q: Float, type: Type, sampleRate: Float) {
            val srD = sampleRate.coerceIn(8000f, 192000f).toDouble()
            val fc = frequency.coerceIn(1f, sampleRate * 0.49f).toDouble()
            val omega = 2.0 * Math.PI * fc / srD
            val sn = kotlin.math.sin(omega)
            val cs = kotlin.math.cos(omega)
            val qv = q.coerceIn(0.1f, 40f).toDouble()
            val alpha = sn / (2.0 * qv)
            val A = 10.0.pow(gainDb.coerceIn(-60f, 60f) / 40.0)
            val sqrtA = sqrt(A)
            val S = qv.coerceIn(0.1, 10.0)
            val sa = sn / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
            val two = 2.0 * sqrtA * sa
            when (type) {
                Type.PEAK -> { b0=1.0+alpha*A; b1=-2.0*cs; b2=1.0-alpha*A; val a0=1.0+alpha/A; a1=-2.0*cs; a2=1.0-alpha/A; norm(a0) }
                Type.LOW_SHELF -> { b0=A*((A+1.0)-(A-1.0)*cs+two); b1=2.0*A*((A-1.0)-(A+1.0)*cs); b2=A*((A+1.0)-(A-1.0)*cs-two); val a0=(A+1.0)+(A-1.0)*cs+two; a1=-2.0*((A-1.0)+(A+1.0)*cs); a2=(A+1.0)+(A-1.0)*cs-two; norm(a0) }
                Type.HIGH_SHELF -> { b0=A*((A+1.0)+(A-1.0)*cs+two); b1=-2.0*A*((A-1.0)+(A+1.0)*cs); b2=A*((A+1.0)+(A-1.0)*cs-two); val a0=(A+1.0)-(A-1.0)*cs+two; a1=2.0*((A-1.0)-(A+1.0)*cs); a2=(A+1.0)-(A-1.0)*cs-two; norm(a0) }
                Type.LOW_PASS -> { b0=(1.0-cs)/2.0; b1=1.0-cs; b2=(1.0-cs)/2.0; val a0=1.0+alpha; a1=-2.0*cs; a2=1.0-alpha; norm(a0) }
                Type.HIGH_PASS -> { b0=(1.0+cs)/2.0; b1=-(1.0+cs); b2=(1.0+cs)/2.0; val a0=1.0+alpha; a1=-2.0*cs; a2=1.0-alpha; norm(a0) }
                Type.NOTCH -> { b0=1.0; b1=-2.0*cs; b2=1.0; val a0=1.0+alpha; a1=-2.0*cs; a2=1.0-alpha; norm(a0) }
                Type.BAND_PASS -> { b0=alpha; b1=0.0; b2=-alpha; val a0=1.0+alpha; a1=-2.0*cs; a2=1.0-alpha; norm(a0) }
            }
            reset()
        }

        private fun norm(a0: Double) {
            if (!a0.isFinite() || abs(a0) < 1e-12) {
                b0=1.0; b1=0.0; b2=0.0; a1=0.0; a2=0.0
            } else {
                b0/=a0; b1/=a0; b2/=a0; a1/=a0; a2/=a0
            }
        }

        fun reset() { z1=0.0; z2=0.0 }

        fun process(x: Float): Float {
            val xd = x.toDouble()
            val y = b0 * xd + z1
            z1 = b1 * xd - a1 * y + z2
            z2 = b2 * xd - a2 * y
            return y.toFloat()
        }
    }
}
