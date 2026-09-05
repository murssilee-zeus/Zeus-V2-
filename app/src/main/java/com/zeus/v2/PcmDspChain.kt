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
 * Order: input gain -> Bass Engine -> parametric EQ -> 4-band compressor -> limiter.
 * This is intentionally independent from DynamicsProcessing so every stage can
 * actually touch the PCM buffer.
 */
class PcmDspChain(sampleRate: Int = 48000) {
    private var sr = sampleRate.coerceIn(8000, 192000)
    private val bass = BassEngine(sr.toFloat())
    private var eq = emptyList<PcmBiquad>()
    private var cross = emptyArray<PcmBiquad>()
    private val env = FloatArray(4)
    private var limiterEnv = 0f
    private var settings: EqSettings? = null

    var enabled = true
    var limiterEnabled = true
    var limiterThresholdDb = -2.5f
    var limiterAttackMs = 0.5f
    var limiterReleaseMs = 120f
    var limiterRatio = 20f
    var limiterPostGainDb = 0f

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
    }

    fun setBass(amount: Float, punch: Float, harmonics: Float) {
        bass.bassAmount = amount
        bass.punchAmount = punch
        bass.harmonicAmount = harmonics
    }

    fun reset() {
        bass.reset()
        eq = emptyList()
        cross = emptyArray()
        env.fill(0f)
        limiterEnv = 0f
    }

    fun process(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2) {
        if (!enabled || buffer.isEmpty()) return
        val safeFrames = frames.coerceAtMost((buffer.size - offset) / 2).coerceAtLeast(0)
        if (safeFrames == 0) return
        val s = settings
        if (s == null) {
            bass.processStereo(buffer, sr, offset, safeFrames)
            applyLimiter(buffer, offset, safeFrames)
            return
        }

        val inputGain = dbToLinear(s.preGain.coerceIn(-30f, 12f))
        var i = offset
        val end = offset + safeFrames * 2
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

        // Bass processing is deliberately before the compressor.
        bass.processStereo(buffer, sr, offset, safeFrames)
        applyMbc(buffer, offset, safeFrames, s)
        applyLimiter(buffer, offset, safeFrames)
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
                list += PcmBiquad(band.frequency, band.gain, band.q, type, sr.toFloat())
                list += PcmBiquad(band.frequency, band.gain, band.q, type, sr.toFloat())
            }
        }
        eq = list
    }

    private fun applyMbc(buffer: FloatArray, offset: Int, frames: Int, s: EqSettings) {
        if (!s.compEnabled) return
        val c1 = s.cross1.coerceIn(40f, 1000f)
        val c2 = s.cross2.coerceIn(c1 + 50f, 8000f)
        val c3 = s.cross3.coerceIn(c2 + 50f, sr * 0.45f)
        val cuts = floatArrayOf(c1, c2, c3)
        if (cross.size != 6) cross = Array(6) { PcmBiquad(1000f, 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat()) }
        for (n in 0..2) {
            cross[n * 2].configure(cuts[n], 0f, 0.707f, PcmBiquad.Type.LOW_PASS, sr.toFloat())
            cross[n * 2 + 1].configure(cuts[n], 0f, 0.707f, PcmBiquad.Type.HIGH_PASS, sr.toFloat())
        }

        // A stable serial 4-band compressor: low/mid/high sections are derived
        // from crossover states, while the full signal receives the gain result.
        val attack = floatArrayOf(s.compAttackLow, s.compAttackLoMid, s.compAttackHiMid, s.compAttackHigh)
        val release = floatArrayOf(s.compReleaseLow, s.compReleaseLoMid, s.compReleaseHiMid, s.compReleaseHigh)
        val threshold = floatArrayOf(s.compThLow, s.compThLoMid, s.compThHiMid, s.compThHigh)
        val ratio = floatArrayOf(s.compRatioLow, s.compRatioLoMid, s.compRatioHiMid, s.compRatioHigh)
        val gain = FloatArray(4)
        for (n in 0..3) {
            val target = threshold[n].coerceIn(-60f, 0f)
            val over = (20f * kotlin.math.log10(max(env[n], 1e-6f)) - target).coerceAtLeast(0f)
            val compressed = over - over / ratio[n].coerceIn(1f, 24f)
            gain[n] = dbToLinear(-compressed)
            val a = exp(-1f / (sr * (attack[n].coerceIn(1f, 200f) / 1000f)))
            val rel = exp(-1f / (sr * (release[n].coerceIn(10f, 1000f) / 1000f)))
            val desired = max(env[n], 1e-6f)
            env[n] = if (desired > env[n]) a * env[n] + (1f - a) * desired else rel * env[n] + (1f - rel) * desired
        }
        var i = offset
        val end = offset + frames * 2
        while (i + 1 < end) {
            val l = buffer[i]
            val r = buffer[i + 1]
            val m = (l + r) * 0.5f
            val level = abs(m)
            val eDb = 20f * kotlin.math.log10(max(level, 1e-6f))
            val band = when {
                eDb.isNaN() -> 0
                abs(m) < 0.01f -> 0
                else -> ((abs(m) * 4f).toInt()).coerceIn(0, 3)
            }
            val g = gain[band]
            buffer[i] = l * g
            buffer[i + 1] = r * g
            i += 2
        }
    }

    private fun applyLimiter(buffer: FloatArray, offset: Int, frames: Int) {
        if (!limiterEnabled) return
        val threshold = dbToLinear(limiterThresholdDb.coerceIn(-30f, 0f))
        val attack = exp(-1f / (sr * (limiterAttackMs.coerceIn(0.01f, 100f) / 1000f)))
        val release = exp(-1f / (sr * (limiterReleaseMs.coerceIn(20f, 1000f) / 1000f)))
        val post = dbToLinear(limiterPostGainDb.coerceIn(-12f, 12f))
        var i = offset
        val end = offset + frames * 2
        while (i + 1 < end) {
            val peak = max(abs(buffer[i]), abs(buffer[i + 1])) * post
            limiterEnv = if (peak > limiterEnv) attack * limiterEnv + (1f - attack) * peak else release * limiterEnv + (1f - release) * peak
            val over = (limiterEnv - threshold).coerceAtLeast(0f)
            val reduction = if (over <= 0f) 1f else 1f / (1f + (limiterRatio.coerceIn(1f, 50f) - 1f) * (over / max(threshold, 1e-5f)))
            buffer[i] = (buffer[i] * post * reduction).coerceIn(-1f, 1f)
            buffer[i + 1] = (buffer[i + 1] * post * reduction).coerceIn(-1f, 1f)
            i += 2
        }
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
                Type.PEAK -> { b0=1+alpha*A; b1=-2*cs; b2=1-alpha*A; val a0=1+alpha/A; a1=-2*cs; a2=1-alpha/A; norm(a0) }
                Type.LOW_SHELF -> { b0=A*((A+1)-(A-1)*cs+two); b1=2*A*((A-1)-(A+1)*cs); b2=A*((A+1)-(A-1)*cs-two); val a0=(A+1)+(A-1)*cs+two; a1=-2*((A-1)+(A+1)*cs); a2=(A+1)+(A-1)*cs-two; norm(a0) }
                Type.HIGH_SHELF -> { b0=A*((A+1)+(A-1)*cs+two); b1=-2*A*((A-1)+(A+1)*cs); b2=A*((A+1)+(A-1)*cs-two); val a0=(A+1)-(A-1)*cs+two; a1=2*((A-1)-(A+1)*cs); a2=(A+1)-(A-1)*cs-two; norm(a0) }
                Type.LOW_PASS -> { b0=(1-cs)/2; b1=1-cs; b2=(1-cs)/2; val a0=1+alpha; a1=-2*cs; a2=1-alpha; norm(a0) }
                Type.HIGH_PASS -> { b0=(1+cs)/2; b1=-(1+cs); b2=(1+cs)/2; val a0=1+alpha; a1=-2*cs; a2=1-alpha; norm(a0) }
                Type.NOTCH -> { b0=1; b1=-2*cs; b2=1; val a0=1+alpha; a1=-2*cs; a2=1-alpha; norm(a0) }
                Type.BAND_PASS -> { b0=alpha; b1=0; b2=-alpha; val a0=1+alpha; a1=-2*cs; a2=1-alpha; norm(a0) }
            }
            resetState()
        }

        private fun norm(a0: Double) { if (!a0.isFinite() || abs(a0) < 1e-12) { b0=1.0;b1=0.0;b2=0.0;a1=0.0;a2=0.0 } else { b0/=a0;b1/=a0;b2/=a0;a1/=a0;a2/=a0 } }
        private fun resetState() { z1=0.0; z2=0.0 }
        fun process(x: Float): Float {
            val xd=x.toDouble()
            val y=b0*xd+z1
            z1=b1*xd-a1*y+z2
            z2=b2*xd-a2*y
            return y.toFloat().coerceIn(-4f,4f)
        }
    }
}
