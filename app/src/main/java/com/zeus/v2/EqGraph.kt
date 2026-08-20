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

fun calculateBandResponse(freq: Float, band: EqBand): Float {
    val f0 = band.frequency.coerceAtLeast(1f)
    val w = freq / f0
    val gainDb = band.gain
    return when (band.filterType) {
        EqBand.FilterType.PEAK -> {
            val bw = 1f / band.q.coerceAtLeast(0.1f)
            val x = (ln(w)).pow(2) / (2f * bw * bw)
            gainDb * (1f - x).coerceIn(0f, 1f) * if (gainDb >= 0f) 1f else 1f
        }
        EqBand.FilterType.LOW_SHELF -> {
            if (freq <= f0) gainDb else gainDb * 0.15f
        }
        EqBand.FilterType.HIGH_SHELF -> {
            if (freq >= f0) gainDb else gainDb * 0.15f
        }
        EqBand.FilterType.LOW_PASS -> {
            val order = (band.q * 2f).coerceIn(1f, 6f)
            -12f * log10(1f + (freq / f0).pow(order))
        }
        EqBand.FilterType.HIGH_PASS -> {
            val order = (band.q * 2f).coerceIn(1f, 6f)
            -12f * log10(1f + (f0 / freq).pow(order))
        }
        EqBand.FilterType.NOTCH -> {
            -30f * exp(-((ln(w)).pow(2)) * band.q)
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

    val responsePoints = remember(bands) {
        val points = 200
        FloatArray(points) { i ->
            val t = i.toFloat() / (points - 1).coerceAtLeast(1)
            val freq = exp(ln(minFreq) + t * (ln(maxFreq) - ln(minFreq)))
            var total = 0f
            for (b in bands) {
                if (b.enabled) total += calculateBandResponse(freq, b)
            }
            total.coerceIn(minGain, maxGain)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF12141A))
            .padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bands) {
                    detectTapGestures { off ->
                        var closest = -1
                        var best = Float.MAX_VALUE
                        bands.forEachIndexed { idx, b ->
                            val bx = freqToX(b.frequency, size.width.toFloat())
                            val d = abs(bx - off.x)
                            if (d < best) {
                                best = d
                                closest = idx
                            }
                        }
                        if (closest >= 0 && best < 80f) onBandSelected(closest)
                    }
                }
                .pointerInput(selectedIndex, bands) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val idx = selectedIndex
                        if (idx !in bands.indices) return@detectDragGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val f = xToFreq(change.position.x, w).coerceIn(20f, 20000f)
                        val g = (maxGain - (change.position.y / h) * (maxGain - minGain))
                            .coerceIn(minGain, maxGain)
                        onBandMoved(idx, f, g)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Grid horizontal
            for (i in 0..6) {
                val y = h * i / 6f
                drawLine(Color(0xFF2A2E38), Offset(0f, y), Offset(w, y), 1f)
            }
            // Grid vertical
            for (i in 0..8) {
                val x = w * i / 8f
                drawLine(Color(0xFF2A2E38), Offset(x, 0f), Offset(x, h), 1f)
            }
            // 0 dB
            drawLine(Color(0xFF4A5568), Offset(0f, midY), Offset(w, midY), 1.5f)

            // Spectrum suave de fondo (opcional, ligero)
            if (spectrum.isNotEmpty()) {
                val n = (spectrum.size - 1).coerceAtLeast(1)
                val spPath = Path()
                spectrum.forEachIndexed { i, v ->
                    val x = w * i / n.toFloat()
                    val y = h * (1f - (v.coerceIn(0f, 1f) * 0.5f))
                    if (i == 0) spPath.moveTo(x, y) else spPath.lineTo(x, y)
                }
                drawPath(
                    spPath,
                    color = Color(0x3360A5FA),
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round)
                )
            }

            // Curva de respuesta + relleno
            if (responsePoints.isNotEmpty()) {
                val n = responsePoints.size - 1
                val linePath = Path()
                val fillPath = Path()

                for (i in responsePoints.indices) {
                    val x = w * i / n.toFloat()
                    val y = (1f - ((responsePoints[i] - minGain) / (maxGain - minGain))) * h
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, midY)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, midY)
                fillPath.close()

                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x553B82F6),
                            Color(0x223B82F6),
                            Color(0x00000000)
                        )
                    )
                )
                drawPath(
                    linePath,
                    color = Color.White,
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                )
            }

            // Puntos de bandas
            bands.forEachIndexed { idx, b ->
                if (!b.enabled) return@forEachIndexed
                val x = freqToX(b.frequency, w)
                val y = (1f - ((b.gain - minGain) / (maxGain - minGain))) * h
                val selected = idx == selectedIndex
                val r = if (selected) 9f else 6f

                if (selected) {
                    drawCircle(b.color.copy(alpha = 0.3f), radius = r + 6f, center = Offset(x, y))
                }
                drawCircle(b.color, radius = r, center = Offset(x, y))
                if (selected) {
                    drawCircle(Color.White, radius = 2.5f, center = Offset(x, y))
                }
            }
        }
    }
}
