package com.zeus.v2

/** Independent mono-centered sub-octave stage for the experimental Hi-Res route. */
class ZeusSubSismi(sampleRate: Int) {
    private val sub = SubHarmonic(sampleRate)
    private var mix = 0f

    fun configure(amountPercent: Float, frequencyHz: Float, mono: Boolean) {
        // The sub path is intentionally mono for stable center imaging.
        mix = (amountPercent.coerceIn(0f, 100f) / 100f) * 0.32f
        sub.configure(frequencyHz.coerceIn(25f, 48f))
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (mix <= 0f) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val l = pcm[i] / 32768f
            val r = pcm[i + 1] / 32768f
            val generated = sub.process((l + r) * 0.5f) * mix
            pcm[i] = toShort(l + generated)
            pcm[i + 1] = toShort(r + generated)
        }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (mix <= 0f) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val l = pcm[i]
            val r = pcm[i + 1]
            val generated = sub.process((l + r) * 0.5f) * mix
            pcm[i] = (l + generated).coerceIn(-1f, 1f)
            pcm[i + 1] = (r + generated).coerceIn(-1f, 1f)
        }
    }

    fun reset() = sub.reset()
    private fun toShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}
