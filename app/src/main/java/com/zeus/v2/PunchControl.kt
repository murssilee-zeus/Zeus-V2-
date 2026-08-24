package com.zeus.v2

/**
 * Controlled punch enhancement centered on the 35-65 Hz impact region.
 * Kept independent from the 18 Hz infrasonic foundation.
 */
object PunchControl {
    fun amount(punch: Float): Float = punch.coerceIn(0f, 100f) / 100f
    fun lowShelfGain(punch: Float): Float = amount(punch) * 1.8f
    fun midBassGain(punch: Float): Float = amount(punch) * 2.4f
    fun punchCenter(punch: Float): Float = 42f + amount(punch) * 18f
    fun punchQ(punch: Float): Float = 1.05f + amount(punch) * 0.35f
}
