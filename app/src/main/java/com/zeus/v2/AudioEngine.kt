package com.zeus.v2

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

class AudioEngine(private val context: Context) {
    companion object {
        private const val TAG = "ZeusAudioEngine"
        private const val TARGET_PRE_EQ_BANDS = 128
        private const val FALLBACK_PRE_EQ_BANDS = 96
        private const val MIN_PRE_EQ_BANDS = 64
        private const val CHANNEL_COUNT = 2
        private const val MBC_BANDS = 4
        private const val POSTEQ_BANDS = 4
        private const val MAX_FREQ = 20000f
    }

    var settings = EqSettings()
    var punch: Float = PunchPreset.DEFAULT
        private set

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var visualizer: Visualizer? = null
    private var audioSessionId = 0
    private var mbcBandCount = MBC_BANDS
    private var preEqBandCount = TARGET_PRE_EQ_BANDS
    private var pipelineEnabled = true
    private var lowShelfTag = true
    private var peakTag = true
    private var highShelfTag = true
    private var deviceSampleRate = 48000f

    @Volatile
    var spectrumData: FloatArray = FloatArray(128) { 0f }
        private set
    @Volatile
    var isEnabled = false
        private set

    fun attachToMediaSession(sessionId: Int = 0): Boolean {
        audioSessionId = sessionId
        return initialize(sessionId)
    }

    fun initialize(sessionId: Int = 0): Boolean {
        release()
        audioSessionId = sessionId
        deviceSampleRate = resolveSampleRate()
        val candidates = listOf(TARGET_PRE_EQ_BANDS, FALLBACK_PRE_EQ_BANDS, MIN_PRE_EQ_BANDS, 48, 32, 18)
        var lastError: Exception? = null

        for (bands in candidates) {
            var mbcCount = MBC_BANDS
            var mbcUse = true
            while (true) {
                try {
                    val config = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        CHANNEL_COUNT,
                        true, bands,
                        mbcUse, mbcCount,
                        true, POSTEQ_BANDS,
                        true
                    ).build()
                    val dp = DynamicsProcessing(0, sessionId, config)
                    dynamicsProcessing = dp
                    preEqBandCount = bands
                    mbcBandCount = if (mbcUse) mbcCount else 0
                    applyAll()
                    startVisualizer()
                    dp.enabled = true
                    isEnabled = true
                    Log.i(TAG, "Engine ON PreEq=$bands MBC=$mbcCount sr=${deviceSampleRate.toInt()} session=$sessionId")
                    return true
                } catch (e: Exception) {
                    lastError = e
                    try { dynamicsProcessing?.release() } catch (_: Exception) {}
                    dynamicsProcessing = null
                    if (mbcUse && mbcCount > 1) { mbcCount--; continue }
                    if (mbcUse) { mbcUse = false; mbcCount = 1; continue }
                    break
                }
            }
        }
        Log.e(TAG, "Fallo al inicializar: ${lastError?.message ?: "unknown"}")
        release()
        return false
    }

    private fun resolveSampleRate(): Float = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        (sr?.toFloat() ?: 48000f).coerceIn(44100f, 192000f)
    } catch (_: Exception) { 48000f }

    fun release() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        try { dynamicsProcessing?.release() } catch (_: Exception) {}
        dynamicsProcessing = null
        isEnabled = false
    }

    fun setEnabled(enabled: Boolean) {
        try { dynamicsProcessing?.enabled = enabled; isEnabled = enabled }
        catch (e: Exception) { Log.e(TAG, "setEnabled: ${e.message}") }
    }

    fun setPreGain(v: Float) { settings.preGain = v; applyInputGain() }
    fun setSubBoost(v: Float) { settings.subBoost = v; applyEq(); applyPostEq() }
    fun setPunch(v: Float) { punch = v.coerceIn(0f, 100f); applyInputGain(); applyPostEq() }
    fun setBands(list: List<EqBand>) { settings.bands = list; applyEq(); applyPostEq() }

    fun setPipelineTags(pipeline: Boolean, lowShelf: Boolean, peak: Boolean, highShelf: Boolean) {
        pipelineEnabled = pipeline
        lowShelfTag = lowShelf
        peakTag = peak
        highShelfTag = highShelf
        applyEq(); applyMbc(); applyPostEq()
    }

    fun setLimiter(enabled: Boolean, threshold: Float, attack: Float, release: Float, ratio: Float, postGain: Float) {
        settings.limiterEnabled = enabled
        settings.limiterThreshold = threshold
        settings.limiterAttack = attack
        settings.limiterRelease = release
        settings.limiterRatio = ratio
        settings.limiterPostGain = postGain
        applyLimiter()
    }

    fun setCompressor(
        enabled: Boolean, cross1: Float, cross2: Float, cross3: Float,
        thLow: Float, thLoMid: Float, thHiMid: Float, thHigh: Float,
        ratioLow: Float, ratioLoMid: Float, ratioHiMid: Float, ratioHigh: Float,
        kneeLow: Float, kneeLoMid: Float, kneeHiMid: Float, kneeHigh: Float,
        attackLow: Float, attackLoMid: Float, attackHiMid: Float, attackHigh: Float,
        releaseLow: Float, releaseLoMid: Float, releaseHiMid: Float, releaseHigh: Float,
        postGainLow: Float, postGainLoMid: Float, postGainHiMid: Float, postGainHigh: Float,
        preGainLow: Float = 0f, preGainLoMid: Float = 0f, preGainHiMid: Float = 0f, preGainHigh: Float = 0f
    ) {
        settings.compEnabled = enabled
        settings.cross1 = cross1; settings.cross2 = cross2; settings.cross3 = cross3
        settings.compThLow = thLow; settings.compThLoMid = thLoMid; settings.compThHiMid = thHiMid; settings.compThHigh = thHigh
        settings.compRatioLow = ratioLow; settings.compRatioLoMid = ratioLoMid; settings.compRatioHiMid = ratioHiMid; settings.compRatioHigh = ratioHigh
        settings.compKneeLow = kneeLow; settings.compKneeLoMid = kneeLoMid; settings.compKneeHiMid = kneeHiMid; settings.compKneeHigh = kneeHigh
        settings.compAttackLow = attackLow; settings.compAttackLoMid = attackLoMid; settings.compAttackHiMid = attackHiMid; settings.compAttackHigh = attackHigh
        settings.compReleaseLow = releaseLow; settings.compReleaseLoMid = releaseLoMid; settings.compReleaseHiMid = releaseHiMid; settings.compReleaseHigh = releaseHigh
        settings.compPostGainLow = postGainLow; settings.compPostGainLoMid = postGainLoMid; settings.compPostGainHiMid = postGainHiMid; settings.compPostGainHigh = postGainHigh
        settings.compPreGainLow = preGainLow; settings.compPreGainLoMid = preGainLoMid; settings.compPreGainHiMid = preGainHiMid; settings.compPreGainHigh = preGainHigh
        applyMbc(); applyPostEq()
    }

    fun applyAll() { applyInputGain(); applyEq(); applyMbc(); applyPostEq(); applyLimiter() }

    private fun applyInputGain() {
        val dp = dynamicsProcessing ?: return
        try {
            val reserve = PunchControl.midBassGain(punch) * 0.65f
            dp.setInputGainAllChannelsTo((settings.preGain - reserve).coerceIn(-30f, 12f))
        } catch (e: Exception) { Log.e(TAG, "inputGain: ${e.message}") }
    }

    /**
     * Phase 2 curve mapper. The parametric curve is evaluated with real RBJ
     * responses, then converted to an adaptive 128/96/64-band DP staircase.
     * Band centres are anchors, while the remaining cutoffs are concentrated
     * around steep response changes. This is much closer to the requested
     * Peak/Shelf/LP/HP/Notch/Band-Pass curve than a fixed log grid.
     */
    private fun applyEq() {
        val dp = dynamicsProcessing ?: return
        try {
            val converted = ParametricToDpConverter.convert(
                bands = settings.bands,
                sampleRate = deviceSampleRate,
                bandCount = preEqBandCount,
                lowShelfEnabled = lowShelfTag,
                peakEnabled = peakTag,
                highShelfEnabled = highShelfTag,
                subBoost = settings.subBoost
            )
            val n = minOf(preEqBandCount, converted.cutoffs.size, converted.gains.size)
            for (i in 0 until n) {
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, converted.cutoffs[i], converted.gains[i])
                )
            }
            for (i in n until preEqBandCount) {
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(false, 1000f, 0f))
            }
        } catch (e: Exception) { Log.e(TAG, "eq mapper: ${e.message}") }
    }

    private fun applyMbc() {
        val dp = dynamicsProcessing ?: return
        if (mbcBandCount == 0) return
        try {
            val s = settings
            val active = s.compEnabled && pipelineEnabled
            val c1 = s.cross1.coerceIn(40f, 1000f)
            val c2 = s.cross2.coerceIn(c1 + 50f, 8000f)
            val c3 = s.cross3.coerceIn(c2 + 50f, 19500f)
            val cuts = listOf(c1, c2, c3, MAX_FREQ)
            val thresholds = listOf(s.compThLow, s.compThLoMid, s.compThHiMid, s.compThHigh)
            val ratios = listOf(s.compRatioLow, s.compRatioLoMid, s.compRatioHiMid, s.compRatioHigh)
            val knees = listOf(s.compKneeLow, s.compKneeLoMid, s.compKneeHiMid, s.compKneeHigh)
            val attacks = listOf(s.compAttackLow, s.compAttackLoMid, s.compAttackHiMid, s.compAttackHigh)
            val releases = listOf(s.compReleaseLow, s.compReleaseLoMid, s.compReleaseHiMid, s.compReleaseHigh)
            val postGains = listOf(s.compPostGainLow, s.compPostGainLoMid, s.compPostGainHiMid, s.compPostGainHigh)
            val preGains = listOf(s.compPreGainLow, s.compPreGainLoMid, s.compPreGainHiMid, s.compPreGainHigh)
            for (i in 0 until mbcBandCount) {
                dp.setMbcBandAllChannelsTo(i, DynamicsProcessing.MbcBand(
                    active, cuts[i], attacks[i].coerceIn(1f, 200f), releases[i].coerceIn(10f, 1000f),
                    ratios[i].coerceIn(1f, 24f), thresholds[i].coerceIn(-60f, 0f), knees[i].coerceIn(0f, 20f),
                    -80f, 1f, preGains[i].coerceIn(-12f, 12f), postGains[i].coerceIn(-12f, 12f)
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "mbc: ${e.message}") }
    }

    private fun applyPostEq() {
        val dp = dynamicsProcessing ?: return
        try {
            val sub = settings.subBoost.coerceIn(0f, 12f)
            val center = PunchControl.punchCenter(punch)
            val q = PunchControl.punchQ(punch)
            val sigma = 0.55f / q
            fun punchAt(freq: Float): Float {
                val x = ln((freq / center).coerceAtLeast(0.001f)).toFloat()
                return PunchControl.midBassGain(punch) * exp(-(x * x) / (2f * sigma * sigma))
            }
            val g31 = sub * 0.55f + punchAt(31.5f) * 0.55f
            val g63 = sub * 0.35f + punchAt(63f)
            dp.setPostEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(g31 > 0.1f, 31.5f, g31))
            dp.setPostEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(g63 > 0.1f, 63f, g63))
            dp.setPostEqBandAllChannelsTo(2, DynamicsProcessing.EqBand(false, 250f, 0f))
            dp.setPostEqBandAllChannelsTo(3, DynamicsProcessing.EqBand(false, 1000f, 0f))
            if (punch <= 0f && sub <= 0f) {
                dp.setPostEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(false, 31.5f, 0f))
                dp.setPostEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(false, 63f, 0f))
            }
        } catch (e: Exception) { Log.e(TAG, "postEq: ${e.message}") }
    }

    private fun applyLimiter() {
        val dp = dynamicsProcessing ?: return
        try {
            val s = settings
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(
                true, s.limiterEnabled, 0,
                s.limiterAttack.coerceIn(0.01f, 100f),
                s.limiterRelease.coerceIn(20f, 1000f),
                s.limiterRatio.coerceIn(1f, 50f),
                s.limiterThreshold.coerceIn(-30f, 0f),
                s.limiterPostGain.coerceIn(-12f, 12f)
            ))
        } catch (e: Exception) { Log.e(TAG, "limiter: ${e.message}") }
    }

    private fun startVisualizer() {
        try {
            val v = Visualizer(audioSessionId)
            v.captureSize = 1024
            val envelope = FloatArray(128) { -80f }
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.size < 4) return
                    val bins = fft.size / 2
                    val sampleRateHz = (samplingRate / 1000f).coerceAtLeast(1000f)
                    val raw = FloatArray(bins) { -80f }
                    for (b in 1 until bins) {
                        val re = fft[b * 2].toInt()
                        val im = fft[b * 2 + 1].toInt()
                        val mag = sqrt((re * re + im * im).toFloat()).coerceAtLeast(1f)
                        raw[b] = (20f * log10(mag / 128f)).coerceIn(-80f, 6f)
                    }
                    val out = FloatArray(128)
                    for (i in out.indices) {
                        val t = i.toFloat() / out.lastIndex
                        val freq = 18.0 * Math.pow(20000.0 / 18.0, t.toDouble())
                        val bin = (freq / sampleRateHz * (bins * 2)).toInt().coerceIn(1, bins - 1)
                        val db = raw[bin]
                        envelope[i] = if (db > envelope[i]) envelope[i] + (db - envelope[i]) * 0.70f else envelope[i] + (db - envelope[i]) * 0.14f
                        out[i] = envelope[i].coerceIn(-80f, 6f)
                    }
                    spectrumData = out
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
        } catch (e: Exception) { Log.w(TAG, "Visualizer no disponible: ${e.message}") }
    }
}
