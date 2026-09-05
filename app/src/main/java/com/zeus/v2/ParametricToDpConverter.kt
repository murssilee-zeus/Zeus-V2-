package com.zeus.v2

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Converts Zeus' parametric bands into the cutoff/gain staircase used by
 * Android DynamicsProcessing. It keeps the existing DP audio path, but gives
 * it many more intelligently placed bands instead of sampling a fixed log grid.
 *
 * The important part is that enabled parametric-band centres are preserved as
 * anchors. Remaining cutoffs are distributed in the areas where the requested
 * response changes most, so narrow peaks/notches and shelf transitions survive
 * the DP approximation much better.
 */
object ParametricToDpConverter {
    private const val MIN_FREQ = 10f
    private const val MAX_FREQ = 20000f
    private const val GRID_SIZE = 256
    private const val REFINEMENT_ITERS = 24
    private const val SAMPLES_PER_STAIR = 4

    data class ConvertedBands(val cutoffs: FloatArray, val gains: FloatArray)

    fun convert(
        bands: List<EqBand>,
        sampleRate: Float,
        bandCount: Int,
        lowShelfEnabled: Boolean,
        peakEnabled: Boolean,
        highShelfEnabled: Boolean,
        subBoost: Float
    ): ConvertedBands {
        val count = bandCount.coerceAtLeast(1)
        val active = bands.asSequence()
            .filter { it.enabled && it.filterType != EqBand.FilterType.BYPASS }
            .filter {
                when (it.filterType) {
                    EqBand.FilterType.LOW_SHELF, EqBand.FilterType.LOW_PASS -> lowShelfEnabled
                    EqBand.FilterType.HIGH_SHELF, EqBand.FilterType.HIGH_PASS -> highShelfEnabled
                    EqBand.FilterType.PEAK, EqBand.FilterType.NOTCH, EqBand.FilterType.BAND_PASS -> peakEnabled
                    EqBand.FilterType.BYPASS -> false
                }
            }
            .map {
                BiquadFilter(it.frequency, it.gain, it.q, it.filterType, sampleRate)
            }
            .toList()

        fun response(freq: Float): Float {
            var db = 0f
            for (filter in active) db += filter.responseDb(freq)
            if (freq < 90f && subBoost > 0f) {
                val t = (1f - freq / 90f).coerceIn(0f, 1f)
                db += subBoost.coerceIn(0f, 12f) * (0.35f + 0.65f * t * t)
            }
            return db.coerceIn(-30f, 30f)
        }

        val logSpan = ln(MAX_FREQ / MIN_FREQ)
        fun toIndex(freq: Float): Int =
            ((ln(freq.coerceIn(MIN_FREQ, MAX_FREQ) / MIN_FREQ) / logSpan) * (GRID_SIZE - 1))
                .toInt().coerceIn(0, GRID_SIZE - 1)
        fun gridFreq(index: Int): Float =
            (MIN_FREQ * exp(logSpan * index / (GRID_SIZE - 1).toFloat())).toFloat()

        val grid = FloatArray(GRID_SIZE) { response(gridFreq(it)) }
        val candidates = ArrayList<Pair<Float, Boolean>>(count + active.size + 8)

        // Two explicit low-end rungs prevent the first DP stair from swallowing
        // the entire infrasonic/sub-bass region on devices with coarse FFT bins.
        candidates += 5f to false
        candidates += 15f to false
        for (b in bands) {
            if (b.enabled && b.filterType != EqBand.FilterType.BYPASS) {
                val allowed = when (b.filterType) {
                    EqBand.FilterType.LOW_SHELF, EqBand.FilterType.LOW_PASS -> lowShelfEnabled
                    EqBand.FilterType.HIGH_SHELF, EqBand.FilterType.HIGH_PASS -> highShelfEnabled
                    EqBand.FilterType.PEAK, EqBand.FilterType.NOTCH, EqBand.FilterType.BAND_PASS -> peakEnabled
                    EqBand.FilterType.BYPASS -> false
                }
                if (allowed) candidates += b.frequency.coerceIn(MIN_FREQ, MAX_FREQ) to true
            }
        }

        // Start with log spacing, then allow anchors to replace nearby points.
        for (i in 0 until count) {
            val t = if (count == 1) 1f else i.toFloat() / (count - 1)
            candidates += (MIN_FREQ * exp(logSpan * t)).toFloat() to false
        }

        candidates.sortBy { it.first }
        val cut = ArrayList<Float>(count)
        val anchor = ArrayList<Boolean>(count)
        for ((f0, a) in candidates) {
            val f = f0.coerceIn(MIN_FREQ, MAX_FREQ)
            val last = cut.lastOrNull()
            if (last == null || f - last > last * 0.0025f) {
                cut += f
                anchor += a
            } else if (a && !anchor.last()) {
                cut[cut.lastIndex] = f
                anchor[anchor.lastIndex] = true
            }
        }

        while (cut.size > count) {
            var best = -1
            var cheapest = Float.MAX_VALUE
            for (i in 1 until cut.lastIndex) {
                if (anchor[i]) continue
                val left = toIndex(cut[i - 1])
                val right = toIndex(cut[i + 1])
                val cost = if (right > left) {
                    var mn = Float.MAX_VALUE
                    var mx = -Float.MAX_VALUE
                    for (k in left..right) {
                        mn = minOf(mn, grid[k])
                        mx = maxOf(mx, grid[k])
                    }
                    mx - mn
                } else 0f
                if (cost < cheapest) {
                    cheapest = cost
                    best = i
                }
            }
            if (best < 0) break
            cut.removeAt(best)
            anchor.removeAt(best)
        }

        while (cut.size < count) {
            var bestGap = -1
            var bestGapSize = 0f
            for (i in 0 until cut.lastIndex) {
                val gap = ln(cut[i + 1] / cut[i])
                if (gap > bestGapSize) {
                    bestGapSize = gap
                    bestGap = i
                }
            }
            if (bestGap < 0) break
            val mid = sqrt(cut[bestGap] * cut[bestGap + 1])
            cut.add(bestGap + 1, mid)
            anchor.add(bestGap + 1, false)
        }

        // Move non-anchor boundaries toward the steepest remaining response
        // segment. This is intentionally bounded, because human sliders should
        // not turn into a CPU benchmark every time a finger moves 1 px.
        repeat(REFINEMENT_ITERS) {
            var worst = -1
            var worstVariation = 0f
            for (i in 0 until cut.lastIndex) {
                val lo = toIndex(cut[i])
                val hi = toIndex(cut[i + 1])
                if (hi <= lo) continue
                var mn = Float.MAX_VALUE
                var mx = -Float.MAX_VALUE
                for (k in lo..hi) {
                    mn = minOf(mn, grid[k])
                    mx = maxOf(mx, grid[k])
                }
                if (mx - mn > worstVariation) {
                    worstVariation = mx - mn
                    worst = i
                }
            }
            if (worst < 0 || worstVariation < 0.05f) return@repeat

            var remove = -1
            var cheapest = Float.MAX_VALUE
            for (i in 1 until cut.lastIndex) {
                if (anchor[i] || i == worst || i == worst + 1) continue
                val lo = toIndex(cut[i - 1])
                val hi = toIndex(cut[i + 1])
                var mn = Float.MAX_VALUE
                var mx = -Float.MAX_VALUE
                for (k in lo..hi) {
                    mn = minOf(mn, grid[k])
                    mx = maxOf(mx, grid[k])
                }
                val cost = mx - mn
                if (cost < cheapest) {
                    cheapest = cost
                    remove = i
                }
            }
            if (remove < 0) return@repeat
            val mid = sqrt(cut[worst] * cut[worst + 1])
            cut.removeAt(remove)
            anchor.removeAt(remove)
            var insert = 0
            while (insert < cut.size && cut[insert] < mid) insert++
            cut.add(insert, mid)
            anchor.add(insert, false)
        }

        val finalCount = minOf(count, cut.size)
        val cutoffs = FloatArray(finalCount)
        val gains = FloatArray(finalCount)
        for (i in 0 until finalCount) {
            cutoffs[i] = cut[i]
            val low = if (i == 0) MIN_FREQ else cut[i - 1]
            val high = cut[i]
            var sum = 0f
            for (s in 0 until SAMPLES_PER_STAIR) {
                val t = (s + 0.5f) / SAMPLES_PER_STAIR
                val f = (low * exp(ln(high / low) * t)).toFloat()
                sum += response(f)
            }
            gains[i] = (sum / SAMPLES_PER_STAIR).coerceIn(-30f, 30f)
        }
        return ConvertedBands(cutoffs, gains)
    }
}
