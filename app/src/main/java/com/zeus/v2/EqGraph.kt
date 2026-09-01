package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun EqGraph(
    bands: List<EqBand>,
    selectedBandIndex: Int,
    spectrum: FloatArray,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    fun freqToX(freq: Float, width: Float): Float {
        val lo = ln(18f)
        val hi = ln(20000f)
        return ((ln(freq.coerceIn(18f, 20000f)) - lo) / (hi - lo) * width).coerceIn(0f, width)
    }
    fun xToFreq(x: Float, width: Float): Float {
        val lo = ln(18f)
        val hi = ln(20000f)
        return exp(lo + (x / width).coerceIn(0f, 1f) * (hi - lo))
    }
    fun dbToY(db: Float, height: Float): Float {
        return height - ((db.coerceIn(-30f, 30f) + 30f) / 60f * height)
    }
    fun yToDb(y: Float, height: Float): Float {
        return 30f - (y / height).coerceIn(0f, 1f) * 60f
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(bands, selectedBandIndex) {
                detectTapGestures { pos ->
                    val hit = bands.indices.minByOrNull { i ->
                        abs(freqToX(bands[i].frequency, size.width.toFloat()) - pos.x)
                    }
                    if (hit != null &&
                        abs(freqToX(bands[hit].frequency, size.width.toFloat()) - pos.x) < 48f
                    ) onSelect(hit)
                }
            }
            .pointerInput(bands, selectedBandIndex) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val hit = bands.indices.minByOrNull { i ->
                            val dx = freqToX(bands[i].frequency, size.width.toFloat()) - pos.x
                            val dy = dbToY(bands[i].gain, size.height.toFloat()) - pos.y
                            dx * dx + dy * dy
                        }
                        if (hit != null) onSelect(hit)
                    },
                    onDrag = { change, _ ->
                        val i = selectedBandIndex
                        if (i in bands.indices) {
                            onMove(
                                i,
                                xToFreq(change.position.x, size.width.toFloat()),
                                yToDb(change.position.y, size.height.toFloat())
                            )
                            change.consume()
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        drawRect(Color(0xFF090B10))

        // dB grid
        for (db in -30..30 step 10) {
            val y = dbToY(db.toFloat(), h)
            drawLine(Color(0xFF242832), Offset(0f, y), Offset(w, y), 1f)
        }

        // Log-frequency grid
        val frequencies = floatArrayOf(
            18f, 31.5f, 63f, 125f, 250f, 500f,
            1000f, 2000f, 4000f, 8000f, 16000f, 20000f
        )
        frequencies.forEach { frequency ->
            val x = freqToX(frequency, w)
            drawLine(Color(0xFF242832), Offset(x, 0f), Offset(x, h), 1f)
        }

        // Zero dB reference
        val zeroY = dbToY(0f, h)
        drawLine(Color(0xFF596273), Offset(0f, zeroY), Offset(w, zeroY), 1.5f)

        // Real-time FFT spectrum. The source values are dB; boost visibility around the
        // useful display range without changing the underlying audio signal.
        if (spectrum.size > 1) {
            val path = Path()
            spectrum.forEachIndexed { index, value ->
                val t = index.toFloat() / (spectrum.size - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val x = freqToX(frequency, w)
                val displayDb = ((value + 72f) * 0.55f - 30f).coerceIn(-30f, 30f)
                val y = dbToY(displayDb, h)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                Color(0xFF2EDB86),
                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
            )
        }

        // Approximate parametric EQ response from the actual band values.
        val response = Path()
        val samples = 320
        for (index in 0 until samples) {
            val t = index.toFloat() / (samples - 1)
            val frequency = 18f * (20000f / 18f).pow(t)
            var gain = 0f

            bands.forEach { band ->
                if (!band.enabled) return@forEach
                val ratio = ln((frequency / band.frequency).coerceAtLeast(0.0001f))
                val width = (1f / band.q.coerceAtLeast(0.1f)).coerceAtMost(3f)
                gain += band.gain * exp(-(ratio * ratio) / (2f * width * width))
            }

            val point = Offset(freqToX(frequency, w), dbToY(gain, h))
            if (index == 0) response.moveTo(point.x, point.y) else response.lineTo(point.x, point.y)
        }
        drawPath(
            response,
            Color(0xFFB45CFF),
            style = Stroke(width = 3.2f, cap = StrokeCap.Round)
        )

        // Band nodes
        bands.forEachIndexed { index, band ->
            if (!band.enabled) return@forEachIndexed
            val point = Offset(freqToX(band.frequency, w), dbToY(band.gain, h))
            val selected = index == selectedBandIndex
            drawCircle(band.color, if (selected) 8f else 6f, point)
            if (selected) {
                drawCircle(Color.White, 11f, point, style = Stroke(width = 1.5f))
            }
        }
    }
}
