package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
@Composable
fun EqGraph(
    bands: List<EqBand>,
    selectedBandIndex: Int,
    spectrum: FloatArray,
    targetCurve: List<TargetPoint> = emptyList(),
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
    fun dbToY(db: Float, height: Float): Float = height - ((db.coerceIn(-30f, 12f) + 30f) / 42f * height)
    fun yToDb(y: Float, height: Float): Float = 12f - (y / height).coerceIn(0f, 1f) * 42f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(bands, selectedBandIndex) {
                detectTapGestures { pos ->
                    val hit = bands.indices.minByOrNull { i ->
                        abs(freqToX(bands[i].frequency, size.width.toFloat()) - pos.x)
                    }
                    if (hit != null && abs(freqToX(bands[hit].frequency, size.width.toFloat()) - pos.x) < 48f) onSelect(hit)
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
                            onMove(i, xToFreq(change.position.x, size.width.toFloat()), yToDb(change.position.y, size.height.toFloat()))
                            change.consume()
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val zeroY = dbToY(0f, h)

        drawRect(Color(0xFF050608))

        // Reference analyzer grid: dense logarithmic frequency divisions with a restrained technical look.
        val majorFreqs = floatArrayOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f, 40000f)
        val minorFreqs = mutableListOf<Float>()
        val decades = listOf(10f, 100f, 1000f, 10000f)
        for (base in decades) for (m in 2..9) {
            val f = base * m
            if (f in 18f..20000f) minorFreqs += f
        }
        minorFreqs.forEach { f ->
            val x = freqToX(f, w)
            drawLine(Color(0xFF171B20), Offset(x, 0f), Offset(x, h), 0.8f)
        }
        majorFreqs.filter { it in 18f..20000f }.forEach { f ->
            val x = freqToX(f, w)
            drawLine(Color(0xFF2A3037), Offset(x, 0f), Offset(x, h), 1.15f)
        }

        val dbLines = intArrayOf(-30, -24, -18, -12, -6, 0, 6, 12)
        dbLines.forEach { db ->
            val y = dbToY(db.toFloat(), h)
            drawLine(
                if (db == 0) Color(0xFF5D6670) else Color(0xFF20262D),
                Offset(0f, y), Offset(w, y),
                if (db == 0) 1.5f else 0.9f
            )
        }

        // Individual EQ-band envelopes.
        bands.forEach { band ->
            if (!band.enabled || abs(band.gain) < .05f) return@forEach
            val zone = Path()
            val samples = 150
            for (index in 0 until samples) {
                val t = index.toFloat() / (samples - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val ratio = ln((frequency / band.frequency).coerceAtLeast(0.0001f))
                val width = (1f / band.q.coerceAtLeast(0.1f)).coerceAtMost(3f)
                val gain = band.gain * exp(-(ratio * ratio) / (2f * width * width))
                val point = Offset(freqToX(frequency, w), dbToY(gain, h))
                if (index == 0) zone.moveTo(point.x, zeroY) else zone.lineTo(point.x, point.y)
            }
            zone.lineTo(w, zeroY)
            zone.close()
            drawPath(zone, band.color.copy(alpha = .075f))
        }

        // Reference-style multi-frequency EQ response visualizer.
        // One line belongs to each configured EQ band. Unlike the previous
        // animated lane visualizer, these curves are the actual response of
        // that band's configured filter, frequency, gain and Q.
        bands.filter { it.enabled }.forEach { band ->
            val filter = BiquadFilter(
                frequency = band.frequency,
                gainDb = band.gain,
                q = band.q,
                type = band.filterType
            )
            val path = Path()
            val samples = 320

            for (sample in 0 until samples) {
                val t = sample.toFloat() / (samples - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val responseDb = filter.responseDb(frequency)
                val point = Offset(
                    freqToX(frequency, w),
                    dbToY(responseDb, h)
                )
                if (sample == 0) path.moveTo(point.x, point.y)
                else path.lineTo(point.x, point.y)
            }

            // The curve itself is driven only by the band's actual settings.
            // The live spectrum only controls a subtle glow so the visual
            // remains faithful to the EQ configuration.
            val liveLevel = if (spectrum.size > 1) {
                val lo = ln(18f)
                val hi = ln(20000f)
                val normalized = ((ln(band.frequency.coerceIn(18f, 20000f)) - lo) / (hi - lo))
                    .coerceIn(0f, 1f)
                val index = (normalized * (spectrum.size - 1)).roundToInt()
                    .coerceIn(0, spectrum.lastIndex)
                ((spectrum[index] + 72f) / 72f).coerceIn(0f, 1f)
            } else 0f

            val glowAlpha = 0.10f + liveLevel * 0.16f
            val lineAlpha = if (abs(band.gain) < 0.01f) 0.28f else 0.88f

            drawPath(
                path,
                band.color.copy(alpha = glowAlpha),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
            drawPath(
                path,
                band.color.copy(alpha = lineAlpha),
                style = Stroke(width = 1.7f, cap = StrokeCap.Round)
            )
        }

        // Live RTA spectrum: the actual FFT data supplied by AudioEngine.
        // It is drawn independently from the static EQ/filter curves so the
        // frequency response remains visible while the music moves underneath it.
        if (spectrum.size > 1) {
            val spectrumPath = Path()
            val spectrumSamples = spectrum.size

            for (i in 0 until spectrumSamples) {
                val t = i.toFloat() / (spectrumSamples - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val db = spectrum[i].coerceIn(-30f, 6f)
                val point = Offset(freqToX(frequency, w), dbToY(db, h))
                if (i == 0) spectrumPath.moveTo(point.x, point.y)
                else spectrumPath.lineTo(point.x, point.y)
            }

            // Soft glow makes the live movement easy to see without hiding
            // the individual filter curves beneath it.
            drawPath(
                spectrumPath,
                Color(0xFF28D7FF).copy(alpha = .18f),
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )
            drawPath(
                spectrumPath,
                Color(0xFF54E7FF).copy(alpha = .85f),
                style = Stroke(width = 2.1f, cap = StrokeCap.Round)
            )
        }

        if (targetCurve.size > 1) {
            val targetPath = Path()
            targetCurve.forEachIndexed { index, point ->
                val p = Offset(freqToX(point.frequency, w), dbToY(point.gain, h))
                if (index == 0) targetPath.moveTo(p.x, p.y) else targetPath.lineTo(p.x, p.y)
            }
            drawPath(targetPath, Color(0xFFFFC857).copy(alpha = .75f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        }

        // Master EQ response: purple neon glow inspired by the supplied analyzer reference.
        val response = Path()
        val samples = 420
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
        drawPath(response, Color(0xFFD85CFF).copy(alpha = .16f), style = Stroke(width = 12f, cap = StrokeCap.Round))
        drawPath(response, Color(0xFFE36AFF).copy(alpha = .30f), style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawPath(response, Color(0xFFD66BFF), style = Stroke(width = 2.6f, cap = StrokeCap.Round))

        // Reference-style status legend. These describe the displayed analyzer, not extra audio paths.
        val legendPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(78, 187, 255)
            textSize = 12f
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("RTA", w - 10f, 18f, legendPaint)
            legendPaint.color = android.graphics.Color.rgb(150, 158, 168)
            canvas.nativeCanvas.drawText("OUT", w - 10f, 36f, legendPaint)
        }

        // Frequency and dB labels.
        val labels = listOf(
            20f to "20", 50f to "50", 100f to "100", 200f to "200", 500f to "500",
            1000f to "1k", 2000f to "2k", 5000f to "5k", 10000f to "10k", 20000f to "20k"
        )
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(142, 150, 160)
            textSize = 10f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        labels.forEach { (freq, label) ->
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, freqToX(freq, w), h - 5f, paint) }
        }
        paint.textAlign = android.graphics.Paint.Align.LEFT
        dbLines.forEach { db ->
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(if (db > 0) "+$db" else db.toString(), 5f, dbToY(db.toFloat(), h) - 4f, paint)
            }
        }

        // Band handles with numbered markers, matching the reference's compact analyzer controls.
        bands.forEachIndexed { index, band ->
            if (!band.enabled) return@forEachIndexed
            val point = Offset(freqToX(band.frequency, w), dbToY(band.gain, h))
            val selected = index == selectedBandIndex
            if (selected) {
                drawCircle(band.color.copy(alpha = .20f), 17f, point)
                drawCircle(Color.White.copy(alpha = .90f), 10f, point, style = Stroke(width = 1.3f))
            }
            drawCircle(band.color, if (selected) 7.5f else 6f, point)
            if (index < 8) {
                val marker = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 8f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText((index + 1).toString(), point.x, point.y + 3f, marker) }
            }
        }
    }
}
