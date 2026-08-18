package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Cálculo de la respuesta de un EQ paramétrico (aproximación visual).
 * Usado en el gráfico y en el Spectrum de MainScreen.
 */
fun calculateBandResponse(freq: Float, band: EqBand): Float {
    val f0 = band.frequency.coerceAtLeast(1f)
    val w = freq / f0
    val gainDb = band.gain
    return when (band.filterType) {
        EqBand.FilterType.PEAK -> {
            val bw = 1f / band.q.coerceAtLeast(0.1f)
            gainDb * (1f - ((ln(w)).pow(2) / (2f * bw * bw))).coerceIn(0f, 1f * sign(gainDb))
        }
        EqBand.FilterType.LOW_SHELF -> {
            if (freq < f0) gainDb * (1f - (freq / f0).pow(2)).coerceIn(0f, 1f) + gainDb * 0.1f
            else gainDb * 0.15f
        }
        EqBand.FilterType.HIGH_SHELF -> {
            if (freq > f0) gainDb * (1f - (f0 / freq).pow(2)).coerceIn(0f, 1f)
            else gainDb * 0.15f
        }
        EqBand.FilterType.LOW_PASS -> {
            val order = (band.q * 2).coerceIn(1f, 8f)
            -20f * log10(1f + (freq / f0).pow(order))
        }
        EqBand.FilterType.HIGH_PASS -> {
            val order = (band.q * 2).coerceIn(1f, 8f)
            -20f * log10(1f + (f0 / freq).pow(order))
        }
        EqBand.FilterType.NOTCH -> {
            gainDb * exp(-((ln(w)).pow(2)) * band.q).coerceAtMost(0f) - 30f
        }
        EqBand.FilterType.BAND_PASS -> {
            gainDb * exp(-((ln(w)).pow(2)) * band.q * 0.5f)
        }
        EqBand.FilterType.BYPASS -> 0f
    }.coerceIn(-30f, 30f)
}

@Composable
fun EqGraph(
    bands: List<EqBand>,
    selectedIndex: Int,
    spectrum: FloatArray,
    onBandSelected: (Int) -> Unit,
    onBandMoved: (Int, frequency: Float, gain: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minFreq = 1f
    val maxFreq = 30000f
    val minGain = -30f
    val maxGain = 30f

    fun freqToX(freq: Float, width: Float): Float {
        val logMin = ln(minFreq); val logMax = ln(maxFreq)
        val logF = ln(freq.coerceIn(minFreq, maxFreq))
        return ((logF - logMin) / (logMax - logMin)) * width
    }
    fun xToFreq(x: Float, width: Float): Float {
        val logMin = ln(minFreq); val logMax = ln(maxFreq)
        val ratio = (x / width).coerceIn(0f, 1f)
        return exp(logMin + ratio * (logMax - logMin))
    }

    val responsePoints = remember(bands) {
        val points = 300
        FloatArray(points) { i ->
            val freq = exp(ln(minFreq) + (i.toFloat() / (points - 1)) * (ln(maxFreq) - ln(minFreq)))
            var total = 0f
            bands.filter { it.enabled }.forEach { total += calculateBandResponse(freq, it) }
            total.coerceIn(minGain, maxGain)
        }
    }

    Box(modifier = modifier.background(Color(0xFF0B0B0C)).padding(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bands, selectedIndex) {
                    detectTapGestures { off ->
                        var closest = -1
                        var md = Float.MAX_VALUE
                        bands.forEachIndexed { idx, band ->
                            val bx = freqToX(band.frequency, size.width.toFloat())
                            val d = abs(bx - off.x)
                            if (d < md) { md = d; closest = idx }
                        }
                        if (closest >= 0) onBandSelected(closest)
                    }
                }
                .pointerInput(bands, selectedIndex) {
                    detectDragGestures(
                        onDragStart = { off ->
                            var closest = -1
                            var md = Float.MAX_VALUE
                            bands.forEachIndexed { idx, band ->
                                val bx = freqToX(band.frequency, size.width.toFloat())
                                val d = abs(bx - off.x)
                                if (d < md) { md = d; closest = idx }
                            }
                            if (closest >= 0) onBandSelected(closest)
                        }
                    ) { change, _ ->
                        val idx = selectedIndex
                        if (idx in bands.indices) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val f = xToFreq(change.position.x, w).coerceIn(1f, 30000f)
                            val g = (maxGain - (change.position.y / h) * (maxGain - minGain))
                                .coerceIn(minGain, maxGain)
                            onBandMoved(idx, f, g)
                        }
                    }
                }
        ) {
            val w = size.width; val h = size.height

            for (i in 0..6) {
                drawLine(Color(0xFF1F1A26), Offset(w * i / 6f, 0f), Offset(w * i / 6f, h), 1f)
            }
            drawLine(Color(0xFF33294A), Offset(0f, h / 2), Offset(w, h / 2), 1f)

            val fillPath = Path(); val linePath = Path()
            spectrum.forEachIndexed { i, v ->
                val x = w * i.toFloat() / (spectrum.size - 1).coerceAtLeast(1)
                val y = h * (1f - v.coerceIn(0f, 1f))
                if (i == 0) {
                    linePath.moveTo(x, y); fillPath.moveTo(x, h); fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y); fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h); fillPath.close()
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x33A040E0), Color(0x00000000)),
                    startY = 0f, endY = h
                )
            )
            drawPath(linePath, Color(0xEEC160FF), style = Stroke(width = 1.6f, cap = StrokeCap.Round))

            val respPath = Path()
            for (i in responsePoints.indices) {
                val x = w * i.toFloat() / (responsePoints.size - 1).coerceAtLeast(1)
                val y = (1f - ((responsePoints[i] - minGain) / (maxGain - minGain))) * h
                if (i == 0) respPath.moveTo(x, y) else respPath.lineTo(x, y)
            }
            drawPath(respPath, Color(0xCC74B9FF), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

            bands.forEachIndexed { idx, b ->
                if (!b.enabled) return@forEachIndexed
                val x = freqToX(b.frequency, w)
                val y = (1f - ((b.gain - minGain) / (maxGain - minGain))) * h
                drawCircle(b.color, radius = if (idx == selectedIndex) 7f else 4f, center = Offset(x, y))
            }
        }
    }
}
