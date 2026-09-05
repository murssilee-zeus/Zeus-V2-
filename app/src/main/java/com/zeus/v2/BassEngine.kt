package com.zeus.v2

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

/** PCM bass processor for the direct-buffer audio path. */
class BassEngine(sampleRate: Float = 48000f) {
    private var currentSampleRate = sampleRate.coerceIn(8000f, 192000f)

    var enabled: Boolean = true
    var bassAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }
    var harmonicAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }
    var punchAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f) }

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
        val newSr = sampleRate.toFloat().coerceIn(8000f, 192000f)
        if (newSr != currentSampleRate) {
            currentSampleRate = newSr
            reset()
        }
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

            val add = bassBoost + harmonic
            buffer[i] = safeSoftLimit(l + add)
            buffer[i + 1] = safeSoftLimit(r + add)
            i += 2
        }
    }

    private fun onePoleCoeff(cutoff: Float): Float {
        val x = (2f * Math.PI.toFloat() * cutoff / currentSampleRate).coerceIn(0.0001f, 0.45f)
        return x / (1f + x)
    }

    private fun safeSoftLimit(x: Float): Float {
        val ax = abs(x)
        if (ax <= 0.92f) return x
        return (0.92f + 0.08f * tanh(((ax - 0.92f) / 0.08f).toDouble()).toFloat()) * if (x < 0f) -1f else 1f
    }
}
