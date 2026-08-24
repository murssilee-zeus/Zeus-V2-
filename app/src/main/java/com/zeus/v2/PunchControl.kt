package com.zeus.v2

/**
 * Controlled punch enhancement centered on 35-65 Hz.
 * The gain is deliberately moderate; final headroom is handled by the engine.
 */
object PunchControl {
    fun lowShelfGain(punch: Float): Float = (punch.coerceIn(0f, 100f) / 100f) * 2.5f
    fun midBassGain(punch: Float): Float = (punch.coerceIn(0f, 100f) / 100f) * 3.0f
    fun punchCenter(punch: Float): Float = 45f + (punch.coerceIn(0f, 100f) / 100f) * 10f
}
