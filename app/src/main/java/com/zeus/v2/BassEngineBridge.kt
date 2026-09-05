package com.zeus.v2

/**
 * PCM bridge for the future in-player/source audio route.
 * DynamicsProcessing cannot inject arbitrary PCM processing into another app's
 * playback stream, so this class keeps BassEngine integration explicit rather
 * than pretending that a system-wide hook exists.
 */
class BassEngineBridge(
    private val engine: BassEngine = BassEngine()
) {
    @Volatile
    var enabled: Boolean = true

    fun reset() = engine.reset()

    fun processStereo(samples: FloatArray, sampleRate: Int) {
        if (!enabled || samples.isEmpty()) return
        engine.processStereo(samples, sampleRate)
    }
}
