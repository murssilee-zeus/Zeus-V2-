package com.zeus.v2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.math.log10

/**
 * Dynamic Epicenter experiment.
 *
 * Creates a restrained mono low-frequency layer from the detected bass envelope.
 * It is intentionally independent from ZeusEpicenter and Sub/Sismi so it can be
 * A/B tested and removed without changing those processors.
 *
 * No allocations occur in the sample-processing loops.
 */
class ZeusDynamicEpicenter(sampleRate: Int) {
    private val sr = sampleRate.coerceIn(8000, 192000).toFloat()
    private var enabled = true
    private var amount = 0f
    private var headphoneMode = false

    private var detectorLp = 0f
    private var detectorHpPrevIn = 0f
    private var detectorHp = 0f
    private var detectorEnv = 0f
    private var diffEnv = 0f
    private var subEnv = 0f
    private var flip = false
    private var previousDetector = 0f
    private var holdSamples = 0
    private var subLp = 0f
    private var subHpPrevIn = 0f
    private var subHp = 0f
    private var rmsEnergy = 0.0
    private var rmsCount = 0
    private var pcmFloatScratch = FloatArray(16384)

    @Volatile var lastSubRmsDbfs: Float = -120f
        private set

    fun configure(epicenterPercent: Float, active: Boolean, headphones: Boolean) {
        amount = epicenterPercent.coerceIn(0f, 100f) / 100f
        enabled = active && amount > 0f
        if (headphoneMode != headphones) {
            headphoneMode = headphones
            reset()
        }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        val attack = 1f - kotlin.math.exp(-1f / (sr * 0.007f))
        val release = 1f - kotlin.math.exp(-1f / (sr * 0.145f))
        val diffRelease = 1f - kotlin.math.exp(-1f / (sr * 0.160f))
        val subRelease = 1f - kotlin.math.exp(-1f / (sr * 0.190f))
        val lpCoeff = 1f - kotlin.math.exp(-2f * Math.PI.toFloat() * 62f / sr)
        val hpCoeff = kotlin.math.exp(-2f * Math.PI.toFloat() * 23f / sr)
        val holdLength = (sr * (0.035f + amount * 0.075f)).toInt()

        rmsEnergy = 0.0
        rmsCount = 0
        for (i in 0 until n step 2) {
            val l = pcm[i]
            val r = pcm[i + 1]
            val mono = (l + r) * 0.5f
            val diff = abs(l - r) * 0.5f

            detectorLp += lpCoeff * (mono - detectorLp)
            val hpInput = detectorLp
            detectorHp = hpCoeff * (detectorHp + hpInput - detectorHpPrevIn)
            detectorHpPrevIn = hpInput
            val detector = detectorHp
            val detectorAbs = abs(detector)
            detectorEnv += (if (detectorAbs > detectorEnv) attack else release) * (detectorAbs - detectorEnv)
            diffEnv += diffRelease * (diff - diffEnv)

            if (previousDetector <= 0f && detector > 0f) {
                flip = !flip
                holdSamples = holdLength
            }
            previousDetector = detector

            val activity = (detectorEnv * 12f).coerceIn(0f, 1f)
            val stereoRatio = (diffEnv / (detectorEnv + 1e-5f)).coerceIn(0f, 1f)
            val monoGate = (1f - stereoRatio * 0.72f).coerceIn(0.18f, 1f)
            val holdGate = if (holdSamples > 0) {
                holdSamples--
                1f
            } else 0.55f
            val target = activity * monoGate * holdGate
            subEnv += (if (target > subEnv) attack else subRelease) * (target - subEnv)

            // Flip-flop-style low component, band-limited to the sub region.
            val raw = if (flip) detectorEnv else -detectorEnv
            subLp += lpCoeff * (raw - subLp)
            val hpIn = subLp
            subHp = hpCoeff * (subHp + hpIn - subHpPrevIn)
            subHpPrevIn = hpIn

            val profileGain = if (headphoneMode) 0.20f else 0.30f
            val generated = (subHp * subEnv * amount * profileGain * 2.2f)
                .coerceIn(-0.42f, 0.42f)
            rmsEnergy += generated.toDouble() * generated
            rmsCount++

            // Preserve the stereo program; add the generated component equally to L/R.
            val voicePresence = (diffEnv * 3.5f).coerceIn(0f, 0.55f)
            val voiceProtection = (1f - voicePresence * 0.45f).coerceIn(0.72f, 1f)
            pcm[i] = tanh((l + generated * voiceProtection).toDouble()).toFloat()
            pcm[i + 1] = tanh((r + generated * voiceProtection).toDouble()).toFloat()
        }
        lastSubRmsDbfs = if (rmsCount > 0 && rmsEnergy > 0.0)
            (20.0 * log10(sqrt(rmsEnergy / rmsCount))).toFloat().coerceIn(-120f, 0f)
        else -120f
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (pcmFloatScratch.size < n) pcmFloatScratch = FloatArray(n)
        for (i in 0 until n) pcmFloatScratch[i] = pcm[i] / 32768f
        processStereo(pcmFloatScratch, n)
        for (i in 0 until n) {
            val v = (pcmFloatScratch[i] * 32768f).toInt()
            pcm[i] = v.coerceIn(-32768, 32767).toShort()
        }
    }

    fun reset() {
        detectorLp = 0f
        detectorHpPrevIn = 0f
        detectorHp = 0f
        detectorEnv = 0f
        diffEnv = 0f
        subEnv = 0f
        flip = false
        previousDetector = 0f
        holdSamples = 0
        subLp = 0f
        subHpPrevIn = 0f
        subHp = 0f
        lastSubRmsDbfs = -120f
    }

}
