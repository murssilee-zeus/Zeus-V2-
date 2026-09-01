package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    fun dbToY(db: Float, height: Float): Float =
        height - ((db.coerceIn(-30f, 30f) + 30f) / 60f * height)
    fun yToDb(y: Float, height: Float): Float =
        30f - (y / height).coerceIn(0f, 1f) * 60f

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

        drawRect(Color(0xFF070A0F))

        // Professional grid: logarithmic frequency, clean dB reference lines.
        val dbLines = intArrayOf(-30, -24, -18, -12, -6, 0, 6, 12)
        dbLines.forEach { db ->
            val y = dbToY(db.toFloat(), h)
            drawLine(
                if (db == 0) Color(0xFF4A5261) else Color(0xFF1B2632),
                Offset(0f, y), Offset(w, y),
                if (db == 0) 1.4f else 1f
            )
        }

        val frequencies = floatArrayOf(
            18f, 31f, 62f, 125f, 250f, 500f,
            1000f, 2000f, 4000f, 8000f, 16000f, 20000f
        )
        frequencies.forEach { frequency ->
            val x = freqToX(frequency, w)
            drawLine(Color(0xFF18222D), Offset(x, 0f), Offset(x, h), 1f)
        }

        // Real-time RTA. The FFT is supplied by AudioEngine; only visualization is
        // transformed here, never the audio signal.
        if (spectrum.size > 1) {
            val spectrumPath = Path()
            val zeroY = dbToY(0f, h)
            spectrum.forEachIndexed { index, value ->
                val t = index.toFloat() / (spectrum.size - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val x = freqToX(frequency, w)
                val displayDb = ((value + 72f) * 0.55f - 30f).coerceIn(-30f, 30f)
                val y = dbToY(displayDb, h)
                if (index == 0) spectrumPath.moveTo(x, y) else spectrumPath.lineTo(x, y)
            }

            // Fill below the live spectrum for the visual depth shown in the reference.
            val fill = Path().apply {
                addPath(spectrumPath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    0f to Color(0xFF1268B8).copy(alpha = .30f),
                    h * .65f to Color(0xFF0A477E).copy(alpha = .10f),
                    h to Color.Transparent
                )
            )

            // Subtle blue glow + crisp live trace.
            drawPath(
                spectrumPath,
                Color(0xFF168CE8).copy(alpha = .18f),
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
            drawPath(
                spectrumPath,
                Color(0xFF1598F5),
                style = Stroke(width = 1.8f, cap = StrokeCap.Round)
            )

            // Baseline gives the spectrum a stable visual anchor.
            drawLine(
                Color(0xFF18B8D0).copy(alpha = .72f),
                Offset(0f, zeroY), Offset(w, zeroY), 1.2f
            )
        }

        // Parametric EQ response, rendered above the live RTA.
        val response = Path()
        val samples = 360
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
            Color(0xFFB45CFF).copy(alpha = .22f),
            style = Stroke(width = 9f, cap = StrokeCap.Round)
        )
        drawPath(
            response,
            Color(0xFFD06CFF),
            style = Stroke(width = 2.8f, cap = StrokeCap.Round)
        )

        // Frequency markers make the professional log scale immediately readable.
        val labels = listOf(
            18f to "18", 31f to "31", 62f to "62", 125f to "125",
            250f to "250", 500f to "500", 1000f to "1k", 2000f to "2k",
            4000f to "4k", 8000f to "8k", 16000f to "16k", 20000f to "20k"
        )
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(130, 142, 158)
            textSize = 10f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        labels.forEach { (freq, label) ->
            drawContext.canvas.nativeCanvas.drawText(
                label, freqToX(freq, w), h - 5f, paint
            )
        }

        // dB labels on the left edge.
        paint.textAlign = android.graphics.Paint.Align.LEFT
        dbLines.forEach { db ->
            drawContext.canvas.nativeCanvas.drawText(
                if (db > 0) "+$db" else db.toString(),
                5f, dbToY(db.toFloat(), h) - 4f, paint
            )
        }

        // Band nodes remain the interactive controls.
        bands.forEachIndexed { index, band ->
            if (!band.enabled) return@forEachIndexed
            val point = Offset(freqToX(band.frequency, w), dbToY(band.gain, h))
            val selected = index == selectedBandIndex
            if (selected) {
                drawCircle(band.color.copy(alpha = .20f), 15f, point)
                drawCircle(Color.White.copy(alpha = .90f), 10f, point, style = Stroke(width = 1.5f))
            }
            drawCircle(band.color, if (selected) 7.5f else 6f, point)
        }
    }
}
