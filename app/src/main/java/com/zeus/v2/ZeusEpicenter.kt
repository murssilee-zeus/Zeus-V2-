package com.zeus.v2

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ZeusEpicenter(
    private val sampleRate: Int
) {
    var enabled = true

    private val lowL = Biquad(sampleRate)
    private val lowR = Biquad(sampleRate)
    private val punchL = Biquad(sampleRate)
    private val punchR = Biquad(sampleRate)
    private val h2L = Biquad(sampleRate)
    private val h2R = Biquad(sampleRate)
    private val h3L = Biquad(sampleRate)
    private val h3R = Biquad(sampleRate)

    fun configure(
        amountPercent: Float,
        punchPercent: Float,
        harmonicsPercent: Float,
        bassFrequencyHz: Float,
        subFrequencyHz: Float
    ) {
        val amount = amountPercent.coerceIn(0f, 100f) / 100f
        val punch = punchPercent.coerceIn(0f, 100f)
        val harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        val bass = bassFrequencyHz.coerceIn(25f, 120f)
        val sub = subFrequencyHz.coerceIn(18f, 50f)

        val subGain = (amount * 10f).coerceAtMost(12f)
        val punchGain = (PunchControl.midBassGain(punch) * 1.9f).coerceAtMost(10f)
        val h2Gain = (amount * 2f + harmonics * 4f).coerceAtMost(9f)
        val h3Gain = (harmonics * 1.8f).coerceAtMost(5f)

        lowL.lowShelf(sub, subGain)
        lowR.lowShelf(sub, subGain)
        punchL.peak(bass, punchGain, .85f)
        punchR.peak(bass, punchGain, .85f)
        h2L.peak((bass * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h2R.peak((bass * 2f).coerceIn(80f, 320f), h2Gain, .90f)
        h3L.peak((bass * 3f).coerceIn(240f, 1200f), h3Gain, .95f)
        h3R.peak((bass * 3f).coerceIn(240f, 1200f), h3Gain, .95f)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val l = pcm[i] / 32768f
            val r = pcm[i + 1] / 32768f
            pcm[i] = toShort(h3L.process(h2L.process(punchL.process(lowL.process(l)))))
            pcm[i + 1] = toShort(h3R.process(h2R.process(punchR.process(lowR.process(r)))))
        }
    }

    fun reset() {
        lowL.reset(); lowR.reset()
        punchL.reset(); punchR.reset()
        h2L.reset(); h2R.reset()
        h3L.reset(); h3R.reset()
    }

    private fun toShort(x: Float): Short =
        (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private class Biquad(private val sr: Int) {
        private var b0=1f; private var b1=0f; private var b2=0f
        private var a1=0f; private var a2=0f
        private var z1=0f; private var z2=0f

        fun lowShelf(freq: Float, gainDb: Float) {
            set(freq, gainDb, .7071f, true)
        }

        fun peak(freq: Float, gainDb: Float, q: Float) {
            set(freq, gainDb, q, false)
        }

        private fun set(freqIn: Float, gainDb: Float, q: Float, shelf: Boolean) {
            val f = freqIn.coerceIn(18f, sr * .45f)
            val w = 2.0 * Math.PI * f / sr
            val c = cos(w); val s = sin(w)
            val alpha = s / (2.0 * q)
            val a = kotlin.math.pow(10.0, gainDb / 40.0)
            val sa = sqrt(a)

            val rb0: Double
            val rb1: Double
            val rb2: Double
            val ra0: Double
            val ra1: Double
            val ra2: Double

            if (shelf) {
                rb0 = a*((a+1)-(a-1)*c+2*sa*alpha)
                rb1 = 2*a*((a-1)-(a+1)*c)
                rb2 = a*((a+1)-(a-1)*c-2*sa*alpha)
                ra0 = (a+1)+(a-1)*c+2*sa*alpha
                ra1 = -2*((a-1)+(a+1)*c)
                ra2 = (a+1)+(a-1)*c-2*sa*alpha
            } else {
                rb0 = 1+alpha*a
                rb1 = -2*c
                rb2 = 1-alpha*a
                ra0 = 1+alpha/a
                ra1 = -2*c
                ra2 = 1-alpha/a
            }

            b0=(rb0/ra0).toFloat(); b1=(rb1/ra0).toFloat(); b2=(rb2/ra0).toFloat()
            a1=(ra1/ra0).toFloat(); a2=(ra2/ra0).toFloat()
        }

        fun process(x: Float): Float {
            val y=b0*x+z1
            z1=b1*x-a1*y+z2
            z2=b2*x-a2*y
            return y
        }

        fun reset() { z1=0f; z2=0f }
    }
}
