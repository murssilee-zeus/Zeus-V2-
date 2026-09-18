package com.zeus.v2

import java.util.concurrent.atomic.AtomicInteger

/**
 * Lock-free-ish stereo PCM ring buffer for the Zeus custom DSP path.
 *
 * Format: interleaved signed 16-bit PCM, L/R/L/R...
 * Capacity is expressed in samples, not frames.
 */
class ZeusPcmBuffer(
    capacitySamples: Int = 16384
) {
    private val capacity = capacitySamples.coerceAtLeast(1024).let { it - (it % 2) }
    private val data = ShortArray(capacity)
    private val readPos = AtomicInteger(0)
    private val writePos = AtomicInteger(0)

    fun clear() {
        readPos.set(0)
        writePos.set(0)
    }

    fun availableSamples(): Int {
        val w = writePos.get()
        val r = readPos.get()
        return if (w >= r) w - r else capacity - r + w
    }

    fun availableFrames(): Int = availableSamples() / 2

    fun freeSamples(): Int = capacity - availableSamples() - 2

    /**
     * Writes interleaved stereo PCM. If the producer outruns the consumer,
     * the oldest complete frames are discarded instead of blocking the audio thread.
     */
    fun write(input: ShortArray, offset: Int = 0, size: Int = input.size - offset): Int {
        var count = size.coerceIn(0, input.size - offset)
        count -= count % 2
        if (count <= 0) return 0

        val writable = freeSamples()
        if (count > writable) {
            val drop = (count - writable + 1).coerceIn(2, count)
            advanceRead(drop + (drop % 2))
        }

        val w = writePos.get()
        for (i in 0 until count) {
            data[(w + i) % capacity] = input[offset + i]
        }
        writePos.set((w + count) % capacity)
        return count
    }

    /**
     * Reads up to size samples into output. Always returns an even sample count.
     */
    fun read(output: ShortArray, offset: Int = 0, size: Int = output.size - offset): Int {
        var count = minOf(size.coerceAtLeast(0), availableSamples())
        count -= count % 2
        if (count <= 0) return 0

        val r = readPos.get()
        for (i in 0 until count) {
            output[offset + i] = data[(r + i) % capacity]
        }
        readPos.set((r + count) % capacity)
        return count
    }

    private fun advanceRead(samples: Int) {
        val available = availableSamples()
        var n = minOf(samples, available)
        n -= n % 2
        if (n > 0) readPos.set((readPos.get() + n) % capacity)
    }
}
