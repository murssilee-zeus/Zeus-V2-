package com.zeus.v2

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

/** PCM bass processor for the direct-buffer audio path. */
class BassEngine(sampleRate: Float = 48000f) {
    private var currentSampleRate = (if (sampleRate.isFinite()) sampleRate else 48000f).coerceIn(8000f, 192000f)

    var enabled: Boolean = true
    var bassMono: Boolean = true
    var bassAmount: Float = 0f
        set(value) { field = (if (value.isFinite()) value else 0f).coerceIn(0f, 100f)
    var harmonicAmount: Float = 0f
        set(value) { field = (if (value.isFinite()) value else 0f).coerceIn(0f, 100f)
    var punchAmount: Float = 0f
        set(value) { field = (if (value.isFinite()) value else 0f).coerceIn(0f, 100f)

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
    fun processStereo(
        buffer: FloatArray,
        sampleRate: Int = currentSampleRate.toInt(),
        offset: Int = 0,
        frames: Int = (buffer.size - offset) / 2
    ) {
        if (!enabled || buffer.isEmpty()) return
        val newSr = (if (sampleRate > 0) sampleRate.toFloat() else currentSampleRate).coerceIn(8000f, 192000f)
        if (newSr != currentSampleRate) {
            currentSampleRate = newSr
            reset()
        }
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeFrames = frames.coerceAtLeast(0).coerceAtMost((buffer.size - safeOffset) / 2)
        val end = safeOffset + safeFrames * 2
        if (safeFrames == 0) return

        val lowA = onePoleCoeff(82f)
        val envFastA = onePoleCoeff(95f)
        val envSlowA = onePoleCoeff(8f)
        val dcA = onePoleCoeff(5f)
        val amount = bassAmount / 100f
        val harmonics = harmonicAmount / 100f
        val punch = punchAmount / 100f

        var i = safeOffset
        while (i + 1 < end) {
            val rawL = buffer[i]
            val rawR = buffer[i + 1]
            val l = if (rawL.isFinite()) rawL else 0f
            val r = if (rawR.isFinite()) rawR else 0f
            lowL += lowA * (l - lowL)
            lowR += lowA * (r - lowR)
            val monoBass = (lowL + lowR) * 0.5f

            val absBass = abs(monoBass)
            fastEnv += envFastA * (absBass - fastEnv)
            slowEnv += envSlowA * (absBass - slowEnv)
            val transient = ((fastEnv - slowEnv) * 4f).coerceIn(0f, 1f)
            val punchGain = 1f + punch * (0.55f * transient)
            val bassBoost = monoBass * amount * (0.18f + 0.82f * punchGain)

            val drive = 1f + 3f * harmonics
            val shaped = tanh((monoBass * drive).toDouble()).toFloat()
            val odd = shaped - tanh(monoBass.toDouble()).toFloat()
            val evenRaw = monoBass * monoBass
            dcHarm += dcA * (evenRaw - dcHarm)
            val even = evenRaw - dcHarm
            val harmonic = harmonics * (0.20f * odd + 0.06f * even)

            if (bassMono) {
                buffer[i] = l + bassBoost + harmonic
                buffer[i + 1] = r + bassBoost + harmonic
            } else {
                buffer[i] = l + lowL * amount * (0.18f + 0.82f * punchGain) + harmonic
                buffer[i + 1] = r + lowR * amount * (0.18f + 0.82f * punchGain) + harmonic
            }
            i += 2
        }
    }

    private fun onePoleCoeff(cutoff: Float): Float {
        val x = (2f * Math.PI.toFloat() * cutoff / currentSampleRate).coerceIn(0.0001f, 0.45f)
        return x / (1f + x)
    }
}
