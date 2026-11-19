package com.zeus.v2

import kotlin.math.max
import kotlin.math.min

/**
 * Converts source-audio metrics into bounded, smoothed control recommendations.
 * It never writes DSP parameters. evaluate() is intended for a control thread.
 */
class ZeusAiController {
    enum class Mode { BASS, HI_RES, SPATIAL, INACTIVE }

    data class Adjustments(
        val bassAmountScale: Float = 1f,
        val punchScale: Float = 1f,
        val harmonicsScale: Float = 1f,
        val epicenterScale: Float = 1f,
        val compressorScale: Float = 1f,
        val confidence: Float = 0f,
        val active: Boolean = false
    )

    @Volatile
    var adjustments: Adjustments = Adjustments()
        private set

    private var bass = 1f
    private var punch = 1f
    private var harmonics = 1f
    private var epicenter = 1f
    private var compressor = 1f

    fun reset() {
        bass = 1f
        punch = 1f
        harmonics = 1f
        epicenter = 1f
        compressor = 1f
        adjustments = Adjustments()
    }

    fun evaluate(
        metrics: ZeusAiAnalyzer.Metrics,
        mode: Mode,
        userBassAmount: Float,
        userPunch: Float,
        userHarmonics: Float,
        userEpicenter: Float,
        userCompressor: Float,
        enabled: Boolean = true
    ): Adjustments {
        if (!enabled || mode != Mode.BASS || metrics.framesAnalyzed <= 0) {
            reset()
            return adjustments
        }

        val headroom = metrics.headroomDb.coerceIn(0f, 120f)
        val bassDb = metrics.bassRmsDbfs
        val subDb = metrics.subRmsDbfs
        val transient = metrics.transientActivity.coerceIn(0f, 100f)
        val crest = metrics.crestDb.coerceIn(0f, 60f)

        var bassTarget = 1f
        var epicenterTarget = 1f
        var punchTarget = 1f
        var harmonicTarget = 1f
        var compressorTarget = 1f

        // Conservative headroom protection. It only recommends attenuation.
        if (headroom < 8f) {
            val risk = ((8f - headroom) / 8f).coerceIn(0f, 1f)
            bassTarget -= 0.22f * risk
            epicenterTarget -= 0.30f * risk
            punchTarget -= 0.18f * risk
            harmonicTarget -= 0.12f * risk
            compressorTarget += 0.15f * risk
        }

        // Small sub-bass compensation, gated by the user's Epicenter setting.
        if (userEpicenter > 0.01f && subDb < bassDb - 8f && headroom > 6f) {
            val deficit = ((bassDb - subDb - 8f) / 18f).coerceIn(0f, 1f)
            epicenterTarget += 0.12f * deficit
        }

        // Dense/transient material gets less extra punch to reduce pumping.
        if (transient > 35f || crest > 14f) {
            val activityRisk = max(
                ((transient - 35f) / 65f).coerceIn(0f, 1f),
                ((crest - 14f) / 20f).coerceIn(0f, 1f)
            )
            punchTarget -= 0.12f * activityRisk
            compressorTarget += 0.08f * activityRisk
        }

        // Gently suggest harmonics when bass content is weak, if user allows it.
        if (userHarmonics > 0.01f && bassDb < -30f && headroom > 8f) {
            harmonicTarget += 0.08f
        }

        bassTarget = bassTarget.coerceIn(0.78f, 1.08f)
        punchTarget = punchTarget.coerceIn(0.82f, 1.05f)
        harmonicTarget = harmonicTarget.coerceIn(0.88f, 1.08f)
        epicenterTarget = epicenterTarget.coerceIn(0.70f, 1.12f)
        compressorTarget = compressorTarget.coerceIn(0.95f, 1.18f)

        // Control-rate smoothing; no abrupt recommendations.
        val smoothing = 0.08f
        bass += smoothing * (bassTarget - bass)
        punch += smoothing * (punchTarget - punch)
        harmonics += smoothing * (harmonicTarget - harmonics)
        epicenter += smoothing * (epicenterTarget - epicenter)
        compressor += smoothing * (compressorTarget - compressor)

        val confidence = if (metrics.framesAnalyzed >= 256) {
            (0.35f + min(1f, metrics.framesAnalyzed / 2048f) * 0.65f)
        } else 0f

        adjustments = Adjustments(
            bassAmountScale = bass,
            punchScale = punch,
            harmonicsScale = harmonics,
            epicenterScale = epicenter,
            compressorScale = compressor,
            confidence = confidence,
            active = confidence > 0f
        )
        return adjustments
    }
}
