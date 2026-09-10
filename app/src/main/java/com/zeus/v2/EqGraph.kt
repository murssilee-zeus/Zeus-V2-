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
    fun dbToY(db: Float, height: Float): Float = height - ((db.coerceIn(-30f, 30f) + 30f) / 60f * height)
    fun yToDb(y: Float, height: Float): Float = 30f - (y / height).coerceIn(0f, 1f) * 60f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(bands, selectedBandIndex) {
                detectTapGestures { pos ->
                    val hit = bands.indices.minByOrNull { i -> abs(freqToX(bands[i].frequency, size.width.toFloat()) - pos.x) }
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
        drawRect(Color(0xFF07090D))

        // Reference-style technical grid: dark, thin and deliberately secondary to the audio data.
        val dbLines = intArrayOf(-30, -24, -18, -12, -6, 0, 6, 12)
        dbLines.forEach { db ->
            val y = dbToY(db.toFloat(), h)
            drawLine(if (db == 0) Color(0xFF46505D) else Color(0xFF18212B), Offset(0f, y), Offset(w, y), if (db == 0) 1.4f else 1f)
        }
        val frequencies = floatArrayOf(18f, 31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f, 20000f)
        frequencies.forEach { frequency ->
            val x = freqToX(frequency, w)
            drawLine(Color(0xFF151D26), Offset(x, 0f), Offset(x, h), 1f)
        }

        // Colored band envelopes, kept soft so they explain the EQ without hiding the RTA.
        bands.forEach { band ->
            if (!band.enabled || abs(band.gain) < .05f) return@forEach
            val zone = Path()
            val samples = 120
            for (index in 0 until samples) {
                val t = index.toFloat() / (samples - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val ratio = ln((frequency / band.frequency).coerceAtLeast(0.0001f))
                val width = (1f / band.q.coerceAtLeast(0.1f)).coerceAtMost(3f)
                val gain = band.gain * exp(-(ratio * ratio) / (2f * width * width))
                val point = Offset(freqToX(frequency, w), dbToY(gain, h))
                if (index == 0) zone.moveTo(point.x, dbToY(0f, h))
                zone.lineTo(point.x, point.y)
            }
            zone.lineTo(w, dbToY(0f, h))
            zone.close()
            drawPath(zone, band.color.copy(alpha = .11f))
        }

        // Real-time RTA. The incoming spectrum is displayed as a neutral trace so it never competes with the EQ curve.
        if (spectrum.size > 1) {
            val raw = ArrayList<Offset>(spectrum.size)
            spectrum.forEachIndexed { index, value ->
                val t = index.toFloat() / (spectrum.size - 1)
                val frequency = 18f * (20000f / 18f).pow(t)
                val x = freqToX(frequency, w)
                val displayDb = ((value + 72f) * 0.55f - 30f).coerceIn(-30f, 30f)
                raw.add(Offset(x, dbToY(displayDb, h)))
            }

            // Catmull-like quadratic smoothing. The RTA remains responsive but loses the harsh digital sawtooth look.
            val spectrumPath = Path()
            spectrumPath.moveTo(raw.first().x, raw.first().y)
            for (i in 1 until raw.size) {
                val previous = raw[i - 1]
                val current = raw[i]
                val midpoint = Offset((previous.x + current.x) * .5f, (previous.y + current.y) * .5f)
                spectrumPath.quadraticBezierTo(previous.x, previous.y, midpoint.x, midpoint.y)
            }
            val last = raw.last()
            spectrumPath.lineTo(last.x, last.y)

            val fill = Path().apply {
                addPath(spectrumPath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(
                0f to Color(0xFF788493).copy(alpha = .12f),
                h * .55f to Color(0xFF4B5563).copy(alpha = .045f),
                h to Color.Transparent
            ))
            drawPath(spectrumPath, Color(0xFF89939F).copy(alpha = .15f), style = Stroke(width = 4.8f, cap = StrokeCap.Round))
            drawPath(spectrumPath, Color(0xFF87919D).copy(alpha = .78f), style = Stroke(width = 1.15f, cap = StrokeCap.Round))
        }

        if (targetCurve.size > 1) {
            val targetPath = Path()
            targetCurve.forEachIndexed { index, point ->
                val p = Offset(freqToX(point.frequency, w), dbToY(point.gain, h))
                if (index == 0) targetPath.moveTo(p.x, p.y) else targetPath.lineTo(p.x, p.y)
            }
            drawPath(targetPath, Color(0xFFFFC857).copy(alpha = .78f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }

        // Master EQ curve: bright, smooth and visually dominant like the supplied references.
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
        drawPath(response, Color(0xFFD06CFF).copy(alpha = .18f), style = Stroke(width = 10f, cap = StrokeCap.Round))
        drawPath(response, Color(0xFFD06CFF), style = Stroke(width = 2.8f, cap = StrokeCap.Round))
        drawLine(Color(0xFF65707D).copy(alpha = .50f), Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), 1f)

        val labels = listOf(18f to "18", 31f to "31", 62f to "62", 125f to "125", 250f to "250", 500f to "500", 1000f to "1k", 2000f to "2k", 4000f to "4k", 8000f to "8k", 16000f to "16k", 20000f to "20k")
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(130, 142, 158)
            textSize = 10f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        labels.forEach { (freq, label) -> drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, freqToX(freq, w), h - 5f, paint) } }
        paint.textAlign = android.graphics.Paint.Align.LEFT
        dbLines.forEach { db -> drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(if (db > 0) "+$db" else db.toString(), 5f, dbToY(db.toFloat(), h) - 4f, paint) } }

        bands.forEachIndexed { index, band ->
            if (!band.enabled) return@forEachIndexed
            val point = Offset(freqToX(band.frequency, w), dbToY(band.gain, h))
            val selected = index == selectedBandIndex
            if (selected) {
                drawCircle(band.color.copy(alpha = .22f), 16f, point)
                drawCircle(Color.White.copy(alpha = .90f), 10f, point, style = Stroke(width = 1.5f))
            }
            drawCircle(band.color, if (selected) 7.5f else 6f, point)
        }
    }
}
