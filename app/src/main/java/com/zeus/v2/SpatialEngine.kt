package com.zeus.v2

import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Lightweight spatial enhancement for the existing audio-session architecture.
 * It uses Android's stereo virtualizer rather than pretending to be Dolby Atmos.
 */
class SpatialEngine(private val audioSessionId: Int) {
    companion object { private const val TAG = "ZeusSpatial" }

    private var virtualizer: Virtualizer? = null
    var enabled: Boolean = false
        private set
    var width: Float = 35f
        private set

    fun initialize(): Boolean {
        release()
        return try {
            val v = Virtualizer(0, audioSessionId)
            if (!v.strengthSupported) {
                v.release()
                Log.w(TAG, "Virtualizer strength not supported")
                false
            } else {
                virtualizer = v
                apply()
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spatializer unavailable: ${e.message}")
            virtualizer = null
            false
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        apply()
    }

    fun setWidth(value: Float) {
        width = value.coerceIn(0f, 100f)
        apply()
    }

    private fun apply() {
        try {
            virtualizer?.let {
                it.setStrength((width * 10f).toInt().coerceIn(0, 1000).toShort())
                it.enabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spatial apply: ${e.message}")
        }
    }

    fun release() {
        try { virtualizer?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        virtualizer = null
    }
}
