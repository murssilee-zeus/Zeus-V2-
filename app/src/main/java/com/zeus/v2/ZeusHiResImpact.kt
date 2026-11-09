package com.zeus.v2

import kotlin.math.abs
import kotlin.math.max

/** Low-band transient enhancer used only by the experimental Hi-Res PCM route. */
class ZeusHiResImpact(private val sampleRate: Int) {
    private val bandL = Biquad(sampleRate)
    private val bandR = Biquad(sampleRate)
    private var fastL = 0f
    private var fastR = 0f
    private var slowL = 0f
    private var slowR = 0f
    private var amount = 0f
    private val fastCoeff = 1f - kotlin.math.exp(-1f / (0.001f * 2.5f * sampleRate))
    private val slowCoeff = 1f - kotlin.math.exp(-1f / (0.001f * 85f * sampleRate))

    init {
        bandL.bandPass(92f, 0.72f)
        bandR.bandPass(92f, 0.72f)
    }

    fun configure(punchPercent: Float) {
        amount = punchPercent.coerceIn(0f, 100f) / 100f
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (amount <= 0f) return
        for (i in 0 until n step 2) {
            val l = pcm[i] / 32768f
            val r = pcm[i + 1] / 32768f
            val bl = bandL.process(l)
            val br = bandR.process(r)
            fastL += fastCoeff * (abs(bl) - fastL)
            fastR += fastCoeff * (abs(br) - fastR)
            slowL += slowCoeff * (abs(bl) - slowL)
            slowR += slowCoeff * (abs(br) - slowR)
            pcm[i] = toShort(l + bl * max(0f, fastL - slowL) * amount * 2f)
            pcm[i + 1] = toShort(r + br * max(0f, fastR - slowR) * amount * 2f)
        }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (amount <= 0f) return
        for (i in 0 until n step 2) {
            val l = pcm[i]
            val r = pcm[i + 1]
            val bl = bandL.process(l)
            val br = bandR.process(r)
            fastL += fastCoeff * (abs(bl) - fastL)
            fastR += fastCoeff * (abs(br) - fastR)
            slowL += slowCoeff * (abs(bl) - slowL)
            slowR += slowCoeff * (abs(br) - slowR)
            pcm[i] = (l + bl * max(0f, fastL - slowL) * amount * 2f).coerceIn(-1f, 1f)
            pcm[i + 1] = (r + br * max(0f, fastR - slowR) * amount * 2f).coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        bandL.reset(); bandR.reset()
        fastL = 0f; fastR = 0f
        slowL = 0f; slowR = 0f
    }

    private fun toShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}
