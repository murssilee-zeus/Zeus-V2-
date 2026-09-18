package com.zeus.v2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaProjection
import android.media.AudioPlaybackCaptureConfiguration
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Real PCM input/output bridge for Android 10+.
 *
 * Input: AudioPlaybackCapture -> interleaved stereo PCM16.
 * DSP: ZeusAtmosEngine through AudioEngine's PCM bus.
 * Output: app-owned AudioTrack.
 *
 * Important: Android does not expose a public API for replacing another app's
 * mixer output in-place. This route captures eligible playback and replays the
 * processed PCM, so the original source may still be audible. The source cannot
 * be muted safely by Zeus through public APIs.
 */
class ZeusPcmPlaybackEngine(
    private val audioEngine: AudioEngine,
    private val projection: MediaProjection,
    private val sampleRate: Int = 48000
) {
    companion object {
        private const val TAG = "ZeusPcmPlayback"
        private const val FRAMES_PER_BUFFER = 960
    }

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var capturedSamples: Long = 0L
        private set

    @Volatile
    var playedSamples: Long = 0L
        private set

    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "Playback capture requires Android 10+")
            return false
        }
        if (running) return true

        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val outputMask = AudioFormat.CHANNEL_OUT_STEREO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()

        val minRecord = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val minTrack = AudioTrack.getMinBufferSize(
            sampleRate,
            outputMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minRecord <= 0 || minTrack <= 0) {
            Log.e(TAG, "Invalid audio buffer sizes record=$minRecord track=$minTrack")
            return false
        }

        return try {
            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minRecord * 2, FRAMES_PER_BUFFER * 4 * 2))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(outputMask)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minTrack * 2, FRAMES_PER_BUFFER * 4 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED ||
                track.state != AudioTrack.STATE_INITIALIZED
            ) {
                record.release()
                track.release()
                Log.e(TAG, "PCM endpoints failed to initialize")
                return false
            }

            recorder = record
            this.track = track
            audioEngine.enablePcmAtmos(sampleRate)

            record.startRecording()
            track.play()
            running = true

            worker = Thread({ loop(record, track) }, "ZeusPcmIo").also {
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }

            Log.i(TAG, "PCM Atmos route ON sr=$sampleRate")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "PCM route start failed", t)
            stop()
            false
        }
    }

    fun stop() {
        running = false
        try { recorder?.stop() } catch (_: Throwable) {}
        try { track?.pause() } catch (_: Throwable) {}
        try { track?.flush() } catch (_: Throwable) {}
        try { worker?.join(500) } catch (_: InterruptedException) {}
        worker = null
        try { recorder?.release() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}
        recorder = null
        track = null
        audioEngine.disablePcmAtmos()
        projection.stop()
        Log.i(TAG, "PCM Atmos route OFF captured=$capturedSamples played=$playedSamples")
    }

    private fun loop(record: AudioRecord, output: AudioTrack) {
        val input = ShortArray(FRAMES_PER_BUFFER * 2)
        val processed = ShortArray(FRAMES_PER_BUFFER * 2)

        while (running) {
            val read = try {
                record.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
            } catch (t: Throwable) {
                Log.e(TAG, "PCM read failed", t)
                break
            }

            if (read <= 0) continue
            val even = read - (read % 2)
            if (even < 2) continue

            capturedSamples += even.toLong()

            // The engine writes the processed frames into the PCM ring buffer.
            audioEngine.processPcmStereo(input, even)
            var remaining = even
            var offset = 0
            while (remaining > 0 && running) {
                val got = audioEngine.readProcessedPcm(processed, remaining)
                if (got <= 0) {
                    Thread.yield()
                    continue
                }
                val written = try {
                    output.write(processed, 0, got, AudioTrack.WRITE_BLOCKING)
                } catch (t: Throwable) {
                    Log.e(TAG, "PCM write failed", t)
                    0
                }
                if (written <= 0) {
                    running = false
                    break
                }
                offset += written
                remaining -= written
                playedSamples += written.toLong()
            }
        }
    }
}
