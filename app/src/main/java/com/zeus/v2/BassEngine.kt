package com.zeus.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/**
 * PCM bass processor for the future direct-buffer audio path.
 *
 * Order inside this processor:
 *   stereo input -> mono low-bass extraction -> punch -> harmonics -> blend
 *
 * It deliberately does not use clipping as a loudness control. The final
 * soft limiter is only a safety ceiling; normal operation remains below it.
 */
class BassEngine(sampleRate: Float = 48000f) {
    private val sr = sampleRate.coerceIn(8000f, 192000f)

    var enabled: Boolean = true
    var bassAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }
    var harmonicAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }
    var punchAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }

    // 20-90 Hz low-bass extraction. The two pole-like one-pole stages give a
    // smoother slope without introducing a dependency on the DP effect API.
    private var lowL = 0f
    private var lowR = 0f
    private var slowEnv = 0f
    private var fastEnv = 0f
    private var dcHarm = 0f

    fun reset() {
        lowL = 0f
        lowR = 0f
        slowEnv = 0f
        fastEnv = 0f
        dcHarm = 0f
    }

    /** Processes interleaved stereo Float PCM in-place. */
    fun processStereo(buffer: FloatArray, offset: Int = 0, frames: Int = (buffer.size - offset) / 2) {
        if (!enabled || buffer.isEmpty()) return
        val end = min(buffer.size, offset + frames * 2)
        if (offset >= end) return

        val lowA = onePoleCoeff(82f)
        val envFastA = onePoleCoeff(95f)
        val envSlowA = onePoleCoeff(8f)
        val dcA = onePoleCoeff(5f)

        val amount = bassAmount / 100f
        val harmonics = harmonicAmount / 100f
        val punch = punchAmount / 100f

        var i = offset
        while (i + 1 < end) {
            val l = buffer[i].coerceIn(-1f, 1f)
            val r = buffer[i + 1].coerceIn(-1f, 1f)
            val mono = (l + r) * 0.5f

            lowL += lowA * (l - lowL)
            lowR += lowA * (r - lowR)
            val monoBass = (lowL + lowR) * 0.5f

            // Fast/slow envelope difference creates a transient-only punch
            // control instead of a permanent bass-volume boost.
            val absBass = abs(monoBass)
            fastEnv += envFastA * (absBass - fastEnv)
            slowEnv += envSlowA * (absBass - slowEnv)
            val transient = ((fastEnv - slowEnv) * 4.0f).coerceIn(0f, 1f)

            // Gentle dynamic punch around the bass attack. It is capped so a
            // 100% setting cannot turn a normal kick into a clipped square wave.
            val punchGain = 1f + punch * (0.55f * transient)
            val bassBoost = monoBass * amount * (0.18f + 0.82f * punchGain)

            // Symmetric waveshaping produces odd harmonics; a small asymmetric
            // term supplies even harmonics. Both are derived from the existing
            // bass, so no synthetic oscillator is injected into silence.
            val drive = 1.0f + 3.0f * harmonics
            val shaped = tanh((monoBass * drive).toDouble()).toFloat()
            val odd = (shaped - tanh(monoBass.toDouble()).toFloat())
            val evenRaw = monoBass * monoBass
            dcHarm += dcA * (evenRaw - dcHarm)
            val even = evenRaw - dcHarm
            val harmonic = harmonics * (0.20f * odd + 0.06f * even)

            // Keep the injected bass mostly mono below ~90 Hz. Above that, the
            // original stereo signal remains untouched, preserving width.
            val add = bassBoost + harmonic
            val outL = l + add
            val outR = r + add

            buffer[i] = safeSoftLimit(outL)
            buffer[i + 1] = safeSoftLimit(outR)
            i += 2
        }
    }

    private fun onePoleCoeff(cutoff: Float): Float {
        val x = (2f * Math.PI.toFloat() * cutoff / sr).coerceIn(0.0001f, 0.45f)
        return x / (1f + x)
    }

    private fun safeSoftLimit(x: Float): Float {
        // Only engage close to full scale. At ordinary levels this is almost
        // transparent, while pathological combinations remain bounded.
        val ax = abs(x)
        if (ax <= 0.92f) return x
        return (0.92f + 0.08f * tanh(((ax - 0.92f) / 0.08f).toDouble()).toFloat()) * if (x < 0f) -1f else 1f
    }
}
