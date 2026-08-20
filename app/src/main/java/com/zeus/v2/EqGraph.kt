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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Respuesta visual aproximada de una banda.
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

/** Color arcoíris según posición 0..1 en el eje de frecuencia */
private fun spectrumColor(t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return when {
        x < 0.2f -> lerp(Color(0xFF7B2CBF), Color(0xFF4361EE), x / 0.2f)
        x < 0.4f -> lerp(Color(0xFF4361EE), Color(0xFF4CC9F0), (x - 0.2f) / 0.2f)
        x < 0.55f -> lerp(Color(0xFF4CC9F0), Color(0xFF2EC4B6), (x - 0.4f) / 0.15f)
        x < 0.7f -> lerp(Color(0xFF2EC4B6), Color(0xFF90BE6D), (x - 0.55f) / 0.15f)
        x < 0.85f -> lerp(Color(0xFF90BE6D), Color(0xFFF9C74F), (x - 0.7f) / 0.15f)
        else -> lerp(Color(0xFFF9C74F), Color(0xFFF94144), (x - 0.85f) / 0.15f)
    }
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
    val maxFreq = 20000f
    val minGain = -30f
    val maxGain = 30f

    fun freqToX(freq: Float, width: Float): Float {
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        val logF = ln(freq.coerceIn(minFreq, maxFreq))
        return ((logF - logMin) / (logMax - logMin)) * width
    }

    fun xToFreq(x: Float, width: Float): Float {
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        val ratio = (x / width).coerceIn(0f, 1f)
        return exp(logMin + ratio * (logMax - logMin))
    }

    // Curva de respuesta del EQ
    val responsePoints = remember(bands) {
        val points = 320
        FloatArray(points) { i ->
            val t = i.toFloat() / (points - 1)
            val freq = exp(ln(minFreq) + t * (ln(maxFreq) - ln(minFreq)))
            var total = 0f
            bands.filter { it.enabled }.forEach { total += calculateBandResponse(freq, it) }
            total.coerceIn(minGain, maxGain)
        }
    }

    // Spectrum amplificado y suavizado para que se vea más
    val displaySpectrum = remember(spectrum) {
        if (spectrum.isEmpty()) return@remember FloatArray(0)
        val boosted = FloatArray(spectrum.size) { i ->
            val v = spectrum[i].coerceIn(0f, 1f)
            sqrt(v) * 1.65f
        }
        val smooth = FloatArray(boosted.size)
        for (i in boosted.indices) {
            val a = boosted.getOrElse(i - 1) { boosted[i] }
            val b = boosted[i]
            val c = boosted.getOrElse(i + 1) { boosted[i] }
            smooth[i] = (a * 0.25f + b * 0.5f + c * 0.25f).coerceIn(0f, 1f)
        }
        smooth
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
                            if (d < md) {
                                md = d
                                closest = idx
                            }
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
                                if (d < md) {
                                    md = d
                                    closest = idx
                                }
                            }
                            if (closest >= 0) onBandSelected(closest)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = selectedIndex
                            if (idx in bands.indices) {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val f = xToFreq(change.position.x, w).coerceIn(20f, 20000f)
                                val g = (maxGain - (change.position.y / h) * (maxGain - minGain))
                                    .coerceIn(minGain, maxGain)
                                onBandMoved(idx, f, g)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            // Grid
            for (i in 0..6) {
                drawLine(Color(0xFF1F1A26), Offset(w * i / 6f, 0f), Offset(w * i / 6f, h), 1f)
            }
            for (i in 0..4) {
                drawLine(Color(0xFF1A1520), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f)
            }
            // Línea 0 dB
            drawLine(Color(0xFF33294A), Offset(0f, h / 2), Offset(w, h / 2), 1.2f)

            // ===== SPECTRUM (fondo) =====
            if (displaySpectrum.isNotEmpty()) {
                val fillPath = Path()
                val linePath = Path()
                val n = (displaySpectrum.size - 1).coerceAtLeast(1)
                displaySpectrum.forEachIndexed { i, v ->
                    val x = w * i / n.toFloat()
                    val y = h * (1f - v.coerceIn(0f, 1f) * 0.85f)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, h)
                fillPath.close()

                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x44A040E0), Color(0x00000000)),
                        startY = 0f,
                        endY = h
                    )
                )
                drawPath(
                    linePath,
                    color = Color(0xAAC160FF),
                    style = Stroke(width = 1.4f, cap = StrokeCap.Round)
                )
            }

            // ===== CURVA EQ MULTICOLOR =====
            if (responsePoints.isNotEmpty()) {
                val n = responsePoints.size - 1
                for (i in 0 until n) {
                    val t0 = i.toFloat() / n
                    val t1 = (i + 1).toFloat() / n
                    val x0 = w * t0
                    val x1 = w * t1
                    val y0 = (1f - ((responsePoints[i] - minGain) / (maxGain - minGain))) * h
                    val y1 = (1f - ((responsePoints[i + 1] - minGain) / (maxGain - minGain))) * h
                    val col = spectrumColor((t0 + t1) / 2f)
                    drawLine(
                        color = col,
                        start = Offset(x0, y0),
                        end = Offset(x1, y1),
                        strokeWidth = 2.8f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // ===== PUNTOS DE BANDAS =====
            bands.forEachIndexed { idx, b ->
                if (!b.enabled) return@forEachIndexed
                val x = freqToX(b.frequency, w)
                val y = (1f - ((b.gain - minGain) / (maxGain - minGain))) * h
                val r = if (idx == selectedIndex) 8f else 5f
                drawCircle(b.color.copy(alpha = 0.25f), radius = r + 4f, center = Offset(x, y))
                drawCircle(b.color, radius = r, center = Offset(x, y))
                if (idx == selectedIndex) {
                    drawCircle(Color.White, radius = 2.2f, center = Offset(x, y))
                }
            }
        }
    }
}
