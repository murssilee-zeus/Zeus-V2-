package com.zeus.v2

import java.util.concurrent.atomic.AtomicInteger

/**
 * Stereo float32 ring buffer for Zeus DSP.
 * Format: interleaved normalized float samples L/R/L/R in [-1, 1].
 */
class ZeusFloatPcmBuffer(capacitySamples: Int = 16384) {
    private val capacity = capacitySamples.coerceAtLeast(1024).let { it - (it % 2) }
    private val data = FloatArray(capacity)
    private val readPos = AtomicInteger(0)
    private val writePos = AtomicInteger(0)

    fun clear() {
        readPos.set(0)
        writePos.set(0)
    }

    private fun availableSamples(): Int {
        val w = writePos.get()
        val r = readPos.get()
        return if (w >= r) w - r else capacity - r + w
    }

    private fun freeSamples(): Int = capacity - availableSamples() - 2

    fun write(input: FloatArray, offset: Int = 0, size: Int = input.size - offset): Int {
        var count = size.coerceIn(0, input.size - offset)
        count -= count % 2
        if (count <= 0) return 0
        val writable = freeSamples().coerceAtLeast(0)
        if (count > writable) {
            var drop = (count - writable + 1).coerceIn(2, count)
            drop += drop % 2
            val available = availableSamples()
            drop = minOf(drop, available - (available % 2))
            if (drop > 0) readPos.set((readPos.get() + drop) % capacity)
        }
        val actual = minOf(count, freeSamples().coerceAtLeast(0))
        val evenCount = actual - actual % 2
        if (evenCount <= 0) return 0
        val w = writePos.get()
        for (i in 0 until evenCount) data[(w + i) % capacity] = input[offset + i]
        writePos.set((w + evenCount) % capacity)
        return evenCount
    }

    fun read(output: FloatArray, offset: Int = 0, size: Int = output.size - offset): Int {
        var count = minOf(size.coerceAtLeast(0), availableSamples())
        count -= count % 2
        if (count <= 0) return 0
        val r = readPos.get()
        for (i in 0 until count) output[offset + i] = data[(r + i) % capacity]
        readPos.set((r + count) % capacity)
        return count
    }
}
