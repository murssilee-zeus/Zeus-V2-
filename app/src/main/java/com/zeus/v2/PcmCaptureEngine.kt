package com.zeus.v2

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental real PCM capture path for Android 10+.
 * Captures another app's playback with MediaProjection/AudioPlaybackCapture,
 * then feeds Float PCM directly into PcmAudioEngine.
 */
class PcmCaptureEngine(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        const val CHANNEL_COUNT = 2
        const val BLOCK_FRAMES = 960 // 20 ms at 48 kHz
    }

    @Volatile var running: Boolean = false
        private set
    @Volatile var lastFramesRead: Int = 0
        private set
    @Volatile var lastError: String? = null
        private set

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val stopRequested = AtomicBoolean(false)
    private val pcmOutput = PcmAudioEngine()

    @Synchronized
    fun start(resultData: Intent, settings: EqSettings): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            lastError = "La captura PCM requiere Android 10 o superior"
            return false
        }
        if (running) return true
        lastError = null
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val mp = manager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)
                ?: throw IllegalStateException("MediaProjection no disponible")
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stop()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))

            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK)
                .build()
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (minBuffer <= 0) throw IllegalStateException("AudioRecord no soporta PCM_FLOAT")
            val bufferBytes = maxOf(minBuffer, BLOCK_FRAMES * CHANNEL_COUNT * 4 * 4)
            val ar = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                throw IllegalStateException("No se pudo inicializar AudioRecord PCM")
            }

            pcmOutput.configure(settings)
            pcmOutput.start(SAMPLE_RATE)
            recorder = ar
            stopRequested.set(false)
            running = true
            worker = Thread({ captureLoop(ar) }, "ZeusPcmCapture")
            worker?.start()
            true
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "error desconocido")
            stop()
            false
        }
    }

    private fun captureLoop(ar: AudioRecord) {
        val samples = FloatArray(BLOCK_FRAMES * CHANNEL_COUNT)
        try {
            ar.startRecording()
            while (!stopRequested.get()) {
                val read = ar.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    lastFramesRead = read / CHANNEL_COUNT
                    pcmOutput.write(samples, 0, read / CHANNEL_COUNT)
                } else if (read < 0) {
                    lastError = "AudioRecord.read() = $read"
                    break
                }
            }
        } catch (t: Throwable) {
            if (!stopRequested.get()) {
                lastError = t.javaClass.simpleName + ": " + (t.message ?: "error de captura")
            }
        } finally {
            runCatching { ar.stop() }
        }
    }

    @Synchronized
    fun configure(settings: EqSettings) {
        pcmOutput.configure(settings)
    }

    @Synchronized
    fun stop() {
        stopRequested.set(true)
        running = false
        recorder?.let { ar ->
            runCatching { ar.stop() }
            runCatching { ar.release() }
        }
        recorder = null
        projection?.let { mp -> runCatching { mp.stop() } }
        projection = null
        pcmOutput.stop()
        worker = null
    }
}
