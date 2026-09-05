package com.zeus.v2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/**
 * Direct PCM output stage for Zeus' future in-app audio route.
 *
 * This class is deliberately source-agnostic: a decoder/player supplies
 * interleaved stereo Float PCM, Zeus processes it, and AudioTrack renders it.
 * It does not claim to replace audio from other apps, which Android does not
 * expose as an arbitrary PCM callback to a normal application.
 */
class PcmAudioEngine {
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 48000
    private val bass = BassEngine(sampleRate.toFloat())

    var enabled: Boolean = true
        set(value) {
            field = value
            bass.enabled = value
        }

    var bassAmount: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            bass.bassAmount = field
        }

    var harmonicAmount: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            bass.harmonicAmount = field
        }

    var punchAmount: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            bass.punchAmount = field
        }

    /** Creates the PCM sink. Safe to call again when the sample rate changes. */
    @Synchronized
    fun start(sampleRate: Int = 48000) {
        val sr = sampleRate.coerceIn(8000, 192000)
        if (audioTrack?.sampleRate == sr && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) audioTrack?.play()
            return
        }
        stop()
        this.sampleRate = sr
        bass.reset()

        val format = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = max(minBytes, sr * 2 * 4 / 10)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
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
        val safeFrames = frames.coerceAtLeast(0).coerceAtMost((samples.size - offset) / 2)
        if (safeFrames == 0) return 0
        bass.processStereo(samples, sampleRate, offset, safeFrames)
        return audioTrack?.write(samples, offset, safeFrames * 2, AudioTrack.WRITE_BLOCKING) ?: 0
    }

    @Synchronized
    fun flush() {
        audioTrack?.pause()
        audioTrack?.flush()
        bass.reset()
    }

    @Synchronized
    fun stop() {
        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
    }
}
