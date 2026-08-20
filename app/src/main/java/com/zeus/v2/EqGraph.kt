package com.zeus.v2

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
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
    val q = band.q.coerceAtLeast(0.1f)
    return when (band.filterType) {
        EqBand.FilterType.PEAK -> {
            // Suave y visualmente agradable (aprox. gaussiana en escala log)
            val x = ln(w)
            val sigma = 0.55f / q
            gainDb * exp(-(x * x) / (2f * sigma * sigma))
        }
        EqBand.FilterType.LOW_SHELF -> {
            val t = (freq / f0).coerceIn(0.01f, 100f)
            if (freq <= f0) {
                gainDb * (1f - 0.5f * (t * t).coerceIn(0f, 1f))
            } else {
                gainDb * 0.12f * (f0 / freq).coerceIn(0f, 1f)
            }
        }
        EqBand.FilterType.HIGH_SHELF -> {
            val t = (f0 / freq).coerceIn(0.01f, 100f)
            if (freq >= f0) {
                gainDb * (1f - 0.5f * (t * t).coerceIn(0f, 1f))
            } else {
                gainDb * 0.12f * (freq / f0).coerceIn(0f, 1f)
            }
        }
        EqBand.FilterType.LOW_PASS -> {
            val order = (q * 2).coerceIn(1f, 8f)
            -20f * log10(1f + (freq / f0).pow(order))
        }
        EqBand.FilterType.HIGH_PASS -> {
            val order = (q * 2).coerceIn(1f, 8f)
            -20f * log10(1f + (f0 / freq).pow(order))
        }
        EqBand.FilterType.NOTCH -> {
            val x = ln(w)
            -abs(gainDb).coerceAtLeast(12f) * exp(-(x * x) * q * 1.2f) - 4f
        }
        EqBand.FilterType.BAND_PASS -> {
            val x = ln(w)
            gainDb * exp(-(x * x) * q * 0.6f)
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
    val minFreq = 20f
    val maxFreq = 22000f
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
        val points = 400
        FloatArray(points) { i ->
            val freq = exp(ln(minFreq) + (i.toFloat() / (points - 1)) * (ln(maxFreq) - ln(minFreq)))
            var total = 0f
            bands.filter { it.enabled }.forEach { total += calculateBandResponse(freq, it) }
            total.coerceIn(minGain, maxGain)
        }
    }

    // Rainbow colors for the response curve (low → high freq)
    val rainbowColors = listOf(
        Color(0xFF9B59B6), // purple
        Color(0xFF3498DB), // blue
        Color(0xFF1ABC9C), // teal
        Color(0xFF2ECC71), // green
        Color(0xFFF1C40F), // yellow
        Color(0xFFE67E22), // orange
        Color(0xFFE74C3C)  // red
    )

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
                            val f = xToFreq(change.position.x, w).coerceIn(20f, 22000f)
                            val g = (maxGain - (change.position.y / h) * (maxGain - minGain))
                                .coerceIn(minGain, maxGain)
                            onBandMoved(idx, f, g)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val labelH = 18f // space reserved at bottom for labels

            // Grid vertical
            for (i in 0..8) {
                drawLine(Color(0xFF1F1A26), Offset(w * i / 8f, 0f), Offset(w * i / 8f, h - labelH), 1f)
            }
            // Grid horizontal + center line
            for (i in 0..6) {
                val y = (h - labelH) * i / 6f
                val color = if (i == 3) Color(0xFF4A3A6A) else Color(0xFF1A1520)
                drawLine(color, Offset(0f, y), Offset(w, y), 1f)
            }

            // Subtle live spectrum (background, low opacity)
            if (spectrum.isNotEmpty()) {
                val fillPath = Path()
                val linePath = Path()
                val n = (spectrum.size - 1).coerceAtLeast(1)
                spectrum.forEachIndexed { i, v ->
                    val x = w * i.toFloat() / n
                    val y = (h - labelH) * (1f - v.coerceIn(0f, 1f))
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h - labelH)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, h - labelH)
                fillPath.close()
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x22A040E0), Color(0x00000000)),
                        startY = 0f, endY = h - labelH
                    )
                )
                drawPath(linePath, Color(0x55C160FF), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            }

            // EQ Response curve – rainbow gradient stroke
            val respPath = Path()
            val graphH = h - labelH
            for (i in responsePoints.indices) {
                val x = w * i.toFloat() / (responsePoints.size - 1).coerceAtLeast(1)
                val y = (1f - ((responsePoints[i] - minGain) / (maxGain - minGain))) * graphH
                if (i == 0) respPath.moveTo(x, y) else respPath.lineTo(x, y)
            }

            // Soft glow under the curve
            drawPath(
                respPath,
                color = Color(0x3374B9FF),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )

            // Main rainbow stroke
            drawPath(
                respPath,
                brush = Brush.horizontalGradient(colors = rainbowColors),
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )

            // Band points (colored circles)
            bands.forEachIndexed { idx, b ->
                if (!b.enabled) return@forEachIndexed
                val x = freqToX(b.frequency, w)
                val y = (1f - ((b.gain - minGain) / (maxGain - minGain))) * graphH
                val radius = if (idx == selectedIndex) 8f else 5f
                // outer ring
                drawCircle(Color.White.copy(alpha = 0.35f), radius = radius + 2f, center = Offset(x, y))
                drawCircle(b.color, radius = radius, center = Offset(x, y))
                if (idx == selectedIndex) {
                    drawCircle(Color.White, radius = 2.5f, center = Offset(x, y))
                }
            }

            // Frequency labels at bottom
            val labelPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#888892")
                textSize = 11f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val freqLabels = listOf(
                31f to "31Hz",
                50f to "50Hz",
                80f to "80Hz",
                125f to "125Hz",
                250f to "250Hz",
                500f to "500Hz",
                1_000f to "1kHz",
                2_000f to "2kHz",
                4_000f to "4kHz",
                8_000f to "8kHz",
                16_000f to "16kHz"
            )
            drawContext.canvas.nativeCanvas.apply {
                freqLabels.forEach { (freq, label) ->
                    val x = freqToX(freq, w)
                    if (x in 12f..(w - 12f)) {
                        drawText(label, x, h - 4f, labelPaint)
                    }
                }
            }
        }
    }
}
