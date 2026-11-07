package com.zeus.v2

import kotlin.math.roundToInt

/**
 * Tonal contour inspired by the Rumble/Punch split:
 * - Rumble adds controlled low-frequency body.
 * - Punch adds upper-bass impact while trimming boxy low mids.
 *
 * This is intentionally independent from SubHarmonic and ZeusEpicenter.
 * It is active only in the normal Zeus Bass PCM route.
 */
class ZeusBassEvolution(sampleRate: Int) {
    private val rumbleL = RumbleBank(sampleRate)
    private val rumbleR = RumbleBank(sampleRate)
    private val punchL = PunchBank(sampleRate)
    private val punchR = PunchBank(sampleRate)

    @Volatile private var enabled = false

    fun configure(rumblePercent: Float, punchPercent: Float, enabled: Boolean) {
        val rumble = rumblePercent.coerceIn(0f, 100f) / 100f
        val punch = punchPercent.coerceIn(0f, 100f) / 100f
        this.enabled = enabled && (rumble > 0f || punch > 0f)
        rumbleL.configure(rumble)
        rumbleR.configure(rumble)
        punchL.configure(punch)
        punchR.configure(punch)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            val l = processLeft(pcm[i] / 32768f)
            val r = processRight(pcm[i + 1] / 32768f)
            pcm[i] = (l * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
            pcm[i + 1] = (r * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled) return
        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        for (i in 0 until n step 2) {
            pcm[i] = processLeft(pcm[i])
            pcm[i + 1] = processRight(pcm[i + 1])
        }
    }

    private fun processLeft(input: Float): Float =
        punchL.process(rumbleL.process(input))

    private fun processRight(input: Float): Float =
        punchR.process(rumbleR.process(input))

    fun reset() {
        rumbleL.reset(); rumbleR.reset()
        punchL.reset(); punchR.reset()
    }

    private class RumbleBank(sampleRate: Int) {
        private val lowBody = Biquad(sampleRate)
        private val midBody = Biquad(sampleRate)
        private val upperBody = Biquad(sampleRate)
        private val mudControl = Biquad(sampleRate)

        fun configure(amount: Float) {
            lowBody.peak(38f, 2.2f * amount, 0.85f)
            midBody.peak(62f, 1.5f * amount, 0.90f)
            upperBody.peak(120f, 0.8f * amount, 0.85f)
            mudControl.peak(240f, -1.2f * amount, 1.0f)
        }

        fun process(x: Float): Float {
            var y = lowBody.process(x)
            y = midBody.process(y)
            y = upperBody.process(y)
            return mudControl.process(y)
        }

        fun reset() {
            lowBody.reset(); midBody.reset()
            upperBody.reset(); mudControl.reset()
        }
    }

    private class PunchBank(sampleRate: Int) {
        private val impact = Biquad(sampleRate)
        private val knock = Biquad(sampleRate)
        private val boxControl = Biquad(sampleRate)
        private val honkControl = Biquad(sampleRate)

        fun configure(amount: Float) {
            impact.peak(108f, 2.0f * amount, 0.95f)
            knock.peak(176f, 1.3f * amount, 1.0f)
            boxControl.peak(290f, -1.0f * amount, 1.05f)
            honkControl.peak(470f, -0.8f * amount, 1.0f)
        }

        fun process(x: Float): Float {
            var y = impact.process(x)
            y = knock.process(y)
            y = boxControl.process(y)
            return honkControl.process(y)
        }

        fun reset() {
            impact.reset(); knock.reset()
            boxControl.reset(); honkControl.reset()
        }
    }
}
