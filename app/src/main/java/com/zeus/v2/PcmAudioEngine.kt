package com.zeus.v2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/** Direct PCM output stage for Zeus' in-app audio route. */
class PcmAudioEngine {
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 48000
    private var dsp = PcmDspChain(sampleRate)
    private var configuredSettings: EqSettings? = null

    var enabled: Boolean = true
        set(value) { field = value; dsp.enabled = value }

    var bassMono: Boolean = true

    var bassAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f); dsp.setBass(field, punchAmount, harmonicAmount) }
    var harmonicAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f); dsp.setBass(bassAmount, punchAmount, field) }
    var punchAmount: Float = 0f
        set(value) { field = value.coerceIn(0f, 100f); dsp.setBass(bassAmount, field, harmonicAmount) }

    @Synchronized
    fun configure(settings: EqSettings) {
        configuredSettings = settings
        bassMono = settings.bassMono
        bassAmount = settings.bassAmount
        punchAmount = settings.bassPunch
        harmonicAmount = settings.bassHarmonics
        dsp.configure(sampleRate, settings)
        dsp.setBass(bassAmount, punchAmount, harmonicAmount)
    }

    /** Creates the PCM sink with a bounded low-latency buffer. */
    @Synchronized
    fun start(sampleRate: Int = 48000) {
        val sr = sampleRate.coerceIn(8000, 192000)
        if (audioTrack?.sampleRate == sr && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) audioTrack?.play()
            return
        }
        stop()
        this.sampleRate = sr
        dsp = PcmDspChain(sr)
        configuredSettings?.let { configure(it) }
        dsp.setBass(bassAmount, punchAmount, harmonicAmount)

        val format = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBytes <= 0) error("AudioTrack no soporta PCM_FLOAT")

        // Target about 20 ms instead of the previous 100 ms. Android may
        // enforce a larger device minimum, but we no longer request a huge
        // latency buffer ourselves.
        val targetBytes = sr * 2 * 4 / 50
        val bufferBytes = max(minBytes, targetBytes)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "No se pudo inicializar AudioTrack PCM" }
        audioTrack = track
        track.play()
    }

    /** Processes and writes interleaved stereo Float PCM. */
    @Synchronized
    fun write(samples: FloatArray, offset: Int = 0, frames: Int = (samples.size - offset) / 2): Int {
        if (!enabled || samples.isEmpty() || audioTrack == null) return 0
        val safeOffset = offset.coerceIn(0, samples.size)
        val safeFrames = frames.coerceAtLeast(0).coerceAtMost((samples.size - safeOffset) / 2)
        if (safeFrames == 0) return 0
        dsp.process(samples, safeOffset, safeFrames)
        return audioTrack?.write(samples, safeOffset, safeFrames * 2, AudioTrack.WRITE_BLOCKING) ?: 0
    }

    @Synchronized fun flush() { audioTrack?.pause(); audioTrack?.flush(); dsp.reset() }

    @Synchronized
    fun stop() {
        audioTrack?.let { track -> runCatching { track.pause() }; runCatching { track.flush() }; runCatching { track.release() } }
        audioTrack = null
        dsp.reset()
    }
}
