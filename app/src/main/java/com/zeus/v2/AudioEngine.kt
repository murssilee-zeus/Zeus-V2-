package com.zeus.v2

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

/**
 * Zeus EQ — Motor de audio mejorado (v19 "Curve Mapper").
 *
 * Pipeline nativo (DynamicsProcessing, API 28+):
 *   1) PreEq   : hasta 128 bandas Peak internas que aproximan
 *                la curva real calculada con biquads (LPF/HPF/Shelf/Peak)
 *   2) MBC     : compresor multibanda 4 bandas / 3 cortes + knee
 *   3) PostEq  : refuerzo subgrave
 *   4) Limiter : hard-knee final
 *
 * El usuario sigue editando pocas bandas paramétricas. El motor calcula
 * la respuesta combinada real y la distribuye en muchas bandas Peak
 * de DynamicsProcessing para obtener LPF/HPF/Shelves mucho más precisos.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "ZeusAudioEngine"

        // Intentamos 128 bandas primero (máximo de la API). Si el dispositivo no lo soporta bajamos.
        private const val TARGET_PRE_EQ_BANDS = 128
        private const val FALLBACK_PRE_EQ_BANDS = 64
        private const val MIN_PRE_EQ_BANDS = 32

        private const val CHANNEL_COUNT = 2
        private const val MBC_BANDS = 4
        private const val POSTEQ_BANDS = 4
        private const val MAX_FREQ = 20000f
        private const val MIN_FREQ = 20f
        private const val SAMPLE_RATE = 48000f
    }

    var settings = EqSettings()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = 0
    private var mbcBandCount = MBC_BANDS
    private var preEqBandCount = TARGET_PRE_EQ_BANDS

    private var pipelineEnabled = true
    private var lowShelfTag = true
    private var peakTag = true
    private var highShelfTag = true

    // Frecuencias logarítmicas de las bandas internas de DynamicsProcessing
    private var internalFreqs: FloatArray = FloatArray(0)

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

        // Intentamos de más bandas a menos
        val candidates = listOf(TARGET_PRE_EQ_BANDS, FALLBACK_PRE_EQ_BANDS, MIN_PRE_EQ_BANDS, 18)
        var lastError: Exception? = null

        for (bands in candidates) {
            var mbcCount = MBC_BANDS
            var mbcUse = true
            while (true) {
                try {
                    val config = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        CHANNEL_COUNT,
                        true, bands,          // preEq
                        mbcUse, mbcCount,     // mbc
                        true, POSTEQ_BANDS,   // postEq
                        true                  // limiter
                    ).build()

                    val dp = DynamicsProcessing(0, sessionId, config)
                    dynamicsProcessing = dp
                    preEqBandCount = bands
                    mbcBandCount = if (mbcUse) mbcCount else 0
                    buildInternalFrequencies(bands)
                    applyAll()
                    startVisualizer()
                    dp.enabled = true
                    isEnabled = true
                    Log.i(TAG, "Engine ON → PreEq=$bands bands, MBC=$mbcCount, session=$sessionId")
                    return true
                } catch (e: Exception) {
                    lastError = e
                    if (mbcUse && mbcCount > 1) {
                        mbcCount -= 1
                        continue
                    }
                    if (mbcUse) {
                        mbcUse = false
                        mbcCount = 1
                        continue
                    }
                    break // probar siguiente cantidad de bandas PreEq
                }
            }
        }

        Log.e(TAG, "Fallo al inicializar: ${lastError?.message}")
        release()
        return false
    }

    private fun buildInternalFrequencies(count: Int) {
        // Distribución logarítmica de 20 Hz a 20 kHz
        internalFreqs = FloatArray(count) { i ->
            val t = i.toFloat() / (count - 1).coerceAtLeast(1)
            (MIN_FREQ * (MAX_FREQ / MIN_FREQ).toDouble().pow(t.toDouble())).toFloat()
        }
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
        try {
            dynamicsProcessing?.release()
        } catch (_: Exception) {}
        dynamicsProcessing = null
        isEnabled = false
    }

    fun setEnabled(enabled: Boolean) {
        try {
            dynamicsProcessing?.enabled = enabled
            isEnabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "setEnabled: ${e.message}")
        }
    }

    fun setPreGain(v: Float) {
        settings.preGain = v
        applyInputGain()
    }

    fun setSubBoost(v: Float) {
        settings.subBoost = v
        applyEq()
        applyPostEq()
    }

    fun setBands(list: List<EqBand>) {
        settings.bands = list
        applyEq()
    }

    fun setPipelineTags(pipeline: Boolean, lowShelf: Boolean, peak: Boolean, highShelf: Boolean) {
        pipelineEnabled = pipeline
        lowShelfTag = lowShelf
        peakTag = peak
        highShelfTag = highShelf
        applyEq()
    }

    fun setLimiter(
        enabled: Boolean, threshold: Float, attack: Float,
        release: Float, ratio: Float, postGain: Float
    ) {
        settings.limiterEnabled = enabled
        settings.limiterThreshold = threshold
        settings.limiterAttack = attack
        settings.limiterRelease = release
        settings.limiterRatio = ratio
        settings.limiterPostGain = postGain
        applyLimiter()
    }

    fun setCompressor(
        enabled: Boolean,
        cross1: Float, cross2: Float, cross3: Float,
        thLow: Float, thLoMid: Float, thHiMid: Float, thHigh: Float,
        ratio: Float, knee: Float, attack: Float, release: Float, postGain: Float
    ) {
        settings.compEnabled = enabled
        settings.cross1 = cross1; settings.cross2 = cross2; settings.cross3 = cross3
        settings.compThLow = thLow; settings.compThLoMid = thLoMid
        settings.compThHiMid = thHiMid; settings.compThHigh = thHigh
        settings.compRatio = ratio; settings.compKnee = knee
        settings.compAttack = attack; settings.compRelease = release
        settings.compPostGain = postGain
        applyMbc()
    }

    fun applyAll() {
        applyInputGain()
        applyEq()
        applyMbc()
        applyPostEq()
        applyLimiter()
    }

    private fun applyInputGain() {
        val dp = dynamicsProcessing ?: return
        try {
            dp.setInputGainAllChannelsTo(settings.preGain.coerceIn(-30f, 30f))
        } catch (e: Exception) {
            Log.e(TAG, "inputGain: ${e.message}")
        }
    }

    /**
     * Núcleo mejorado:
     * 1. Calcula la respuesta en dB de todos los filtros paramétricos del usuario
     *    usando biquads reales.
     * 2. Muestrea esa curva en las frecuencias internas de DynamicsProcessing.
     * 3. Aplica esas ganancias a las bandas Peak de PreEq.
     */
    private fun applyEq() {
        val dp = dynamicsProcessing ?: return
        if (internalFreqs.isEmpty()) return

        try {
            val s = settings
            val activeFilters = mutableListOf<BiquadFilter>()

            // Construir biquads solo de las bandas activas y permitidas por tags
            for (b in s.bands) {
                if (!b.enabled || b.filterType == EqBand.FilterType.BYPASS) continue
                if (!tagAllowed(b)) continue

                val gain = when (b.filterType) {
                    // Para LPF/HPF el gain del usuario casi no se usa;
                    // el filtro en sí ya corta. Le damos un pequeño ajuste.
                    EqBand.FilterType.LOW_PASS, EqBand.FilterType.HIGH_PASS -> 0f
                    else -> b.gain
                }

                activeFilters += BiquadFilter(
                    frequency = b.frequency,
                    gainDb = gain,
                    q = b.q,
                    type = b.filterType,
                    sampleRate = SAMPLE_RATE
                )
            }

            // Sub-boost extra en las frecuencias más bajas (opcional)
            val subBoost = s.subBoost.coerceIn(0f, 12f)

            for (i in internalFreqs.indices) {
                val freq = internalFreqs[i]
                var totalDb = 0f

                for (filter in activeFilters) {
                    totalDb += filter.responseDb(freq)
                }

                // Refuerzo de subgrave solo en la zona muy baja
                if (freq < 80f && subBoost > 0f) {
                    val amount = subBoost * (1f - (freq / 80f)).coerceIn(0f, 1f)
                    totalDb += amount
                }

                val gain = totalDb.coerceIn(-30f, 30f)
                val band = DynamicsProcessing.EqBand(true, freq, gain)
                dp.setPreEqBandAllChannelsTo(i, band)
            }
        } catch (e: Exception) {
            Log.e(TAG, "eq: ${e.message}")
        }
    }

    private fun tagAllowed(b: EqBand): Boolean = when (b.filterType) {
        EqBand.FilterType.LOW_SHELF, EqBand.FilterType.LOW_PASS -> lowShelfTag
        EqBand.FilterType.HIGH_SHELF, EqBand.FilterType.HIGH_PASS -> highShelfTag
        EqBand.FilterType.PEAK, EqBand.FilterType.NOTCH, EqBand.FilterType.BAND_PASS -> peakTag
        EqBand.FilterType.BYPASS -> false
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
            val ths = listOf(s.compThLow, s.compThLoMid, s.compThHiMid, s.compThHigh)
            val atk = s.compAttack.coerceIn(1f, 200f)
            val rel = s.compRelease.coerceIn(10f, 1000f)
            val ratio = s.compRatio.coerceIn(1f, 24f)
            val knee = s.compKnee.coerceIn(0f, 20f)

            for (i in 0 until mbcBandCount) {
                val band = DynamicsProcessing.MbcBand(
                    active,
                    cuts[i],
                    atk, rel,
                    ratio,
                    ths[i].coerceIn(-60f, 0f),
                    knee,
                    -80f,
                    1f,
                    0f,
                    s.compPostGain.coerceIn(-12f, 12f)
                )
                dp.setMbcBandAllChannelsTo(i, band)
            }
        } catch (e: Exception) {
            Log.e(TAG, "mbc: ${e.message}")
        }
    }

    private fun applyPostEq() {
        val dp = dynamicsProcessing ?: return
        try {
            val sub = settings.subBoost.coerceIn(0f, 12f)
            dp.setPostEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(sub > 0f, 31.5f, sub))
            dp.setPostEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(sub > 0f, 63f, sub * 0.5f))
            dp.setPostEqBandAllChannelsTo(2, DynamicsProcessing.EqBand(false, 250f, 0f))
            dp.setPostEqBandAllChannelsTo(3, DynamicsProcessing.EqBand(false, 1000f, 0f))
        } catch (e: Exception) {
            Log.e(TAG, "postEq: ${e.message}")
        }
    }

    private fun applyLimiter() {
        val dp = dynamicsProcessing ?: return
        try {
            val s = settings
            val lim = DynamicsProcessing.Limiter(
                true,
                s.limiterEnabled,
                0,
                s.limiterAttack.coerceIn(0.5f, 80f),
                s.limiterRelease.coerceIn(20f, 1000f),
                s.limiterRatio.coerceIn(1f, 50f),   // ← máximo 50
                s.limiterThreshold.coerceIn(-30f, 0f),
                s.limiterPostGain.coerceIn(-12f, 12f)
            )
            dp.setLimiterAllChannelsTo(lim)
        } catch (e: Exception) {
            Log.e(TAG, "limiter: ${e.message}")
        }
    }

    private fun startVisualizer() {
        try {
            val v = Visualizer(audioSessionId)
            v.captureSize = 1024
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) {}

                    override fun onFftDataCapture(
                        v: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 2) return
                        val n = fft.size / 2
                        val out = FloatArray(128)
                        for (i in 0 until 128) {
                            val idx = (i.toFloat() / 127f * (n - 1)).toInt().coerceIn(0, n - 1)
                            val re = ((fft[idx * 2].toInt() and 0xFF) - 128).toFloat()
                            val im = ((fft[idx * 2 + 1].toInt() and 0xFF) - 128).toFloat()
                            out[i] = (sqrt(re * re + im * im) / 128f).coerceIn(0f, 1f)
                        }
                        spectrumData = out
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false, true
            )
            v.enabled = true
            visualizer = v
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer no disponible: ${e.message}")
        }
    }
}
