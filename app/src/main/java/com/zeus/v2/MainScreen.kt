package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

private val BG = Color(0xFF0B0B0C)
private val SURFACE = Color(0xFF131316)
private val CARD = Color(0xFF16161B)
private val CARD_BORDER = Color(0xFF26262A)
private val GRID = Color(0xFF2A2236)
private val SPECTRUM = Color(0xFFC160FF)
private val SPECTRUM_FILL = Color(0x33A040E0)
private val RED_ACCENT = Color(0xFFE0566B)
private val PINK_ACCENT = Color(0xFFFF6B9E)
private val GOLD = Color(0xFFFFEAA7)
private val LSHELF_BG = Color(0xFFCBCAD6)
private val TXT_PRIMARY = Color(0xFFECECEE)
private val TXT_MUTED = Color(0xFF888892)

@Composable
fun MainScreen(
    viewModel: EqViewModel,
    onToggleEngine: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(SURFACE)
                    .border(1.dp, CARD_BORDER, RoundedCornerShape(22.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = { viewModel.previousSection() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TXT_MUTED)
                }
                Text(
                    viewModel.sectionTitle(),
                    color = TXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = { viewModel.nextSection() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TXT_MUTED)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Guardar",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E4D3A))
                        .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(16.dp))
                        .clickable { onSave() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
                IconButton(
                    onClick = onToggleEngine,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isEngineRunning) Color(0xFF2ECC71) else Color(0xFF333344))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        when (viewModel.currentSection) {
            EqSection.PIPELINE -> MainEqualizerLayout(viewModel, Modifier.fillMaxWidth().weight(1f, false))
            EqSection.EQUALIZER -> MainEqualizerLayout(viewModel, Modifier.fillMaxWidth().weight(1f, false))
            EqSection.CROSSOVER -> CrossoverSection(viewModel, Modifier.fillMaxWidth().weight(1f, false))
            EqSection.LIMITER -> LimiterScreen(viewModel, Modifier.fillMaxWidth().weight(1f, false))
        }

        Spacer(Modifier.height(8.dp))
        SixteenKnobRow(viewModel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MainEqualizerLayout(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.7f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpectrumCard(
                spectrum = viewModel.spectrum,
                bands = viewModel.bands,
                selectedIndex = viewModel.selectedBandIndex,
                onBandSelected = { viewModel.selectBand(it) },
                onBandMoved = { idx, freq, gain ->
                    viewModel.selectBand(idx)
                    viewModel.updateSelectedBand(frequency = freq, gain = gain)
                },
                modifier = Modifier.weight(1.4f).fillMaxWidth()
            )
            EqControlsBar(viewModel, Modifier.fillMaxWidth())
            PreampAndSubRow(viewModel, Modifier.fillMaxWidth())
        }
        Column(
            modifier = Modifier.weight(1.3f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompressorMultibandaCard(viewModel, Modifier.fillMaxWidth().weight(1f))
            LimitadorCard(viewModel, Modifier.fillMaxWidth().weight(1.1f))
            PipelineCard(viewModel, Modifier.fillMaxWidth().weight(1.1f))
        }
    }
}

@Composable
private fun SpectrumCard(
    spectrum: FloatArray,
    bands: List<EqBand>,
    selectedIndex: Int,
    onBandSelected: (Int) -> Unit,
    onBandMoved: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BG)
                .clip(RoundedCornerShape(4.dp))
        ) {
            SpectrumCanvas(spectrum, bands)
        }
    }
}

@Composable
private fun SpectrumCanvas(spectrum: FloatArray, bands: List<EqBand>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in -48 downTo -140 step 12) {
            val y = h * (-i - 48f) / (140f - 48f)
            drawLine(Color(0xFF1A1620), Offset(0f, y), Offset(w, y), 1f)
        }
        for (i in 0..6) {
            val x = w * i / 6f
            drawLine(Color(0xFF1A1620), Offset(x, 0f), Offset(x, h), 1f)
        }
        drawLine(GRID, Offset(0f, h / 2), Offset(w, h / 2), 1f)

        val respPath = Path()
        for (i in 0..200) {
            val t = i / 200f
            val freq = exp((1f - t) * ln(20f) + t * ln(20000f))
            var g = 0f
            bands.filter { it.enabled }.forEach { b -> g += calculateBandResponse(freq, b) }
            val ng = (g + 30f) / 60f
            val y = (1f - ng.coerceIn(0f, 1f)) * h
            val x = t * w
            if (i == 0) respPath.moveTo(x, y) else respPath.lineTo(x, y)
        }
        drawPath(respPath, Color(0xCB74B9FF), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        val path = Path()
        val fillPath = Path()
        val n = (spectrum.size - 1).coerceAtLeast(1)
        spectrum.forEachIndexed { i, v ->
            val x = w * i.toFloat() / n
            val y = h * (1f - v.coerceIn(0f, 1f))
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(SPECTRUM_FILL, Color(0x00000000)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(path, Color(0xEEB070F0), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    }
}

@Composable
private fun EqControlsBar(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    val band = viewModel.selectedBand()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val labels = listOf(
            EqBand.FilterType.PEAK to "PEAK",
            EqBand.FilterType.LOW_SHELF to "LSHELF",
            EqBand.FilterType.HIGH_SHELF to "HSHELF",
            EqBand.FilterType.LOW_PASS to "LPF",
            EqBand.FilterType.HIGH_PASS to "HPF",
            EqBand.FilterType.BYPASS to "BYPASS"
        )
        labels.forEach { (type, name) ->
            val active = band?.filterType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) LSHELF_BG else Color(0xFF1A1A20))
                    .border(1.dp, if (active) Color(0xFF888899) else CARD_BORDER, RoundedCornerShape(4.dp))
                    .clickable { viewModel.updateSelectedBand(filterType = type) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    color = if (active) Color.Black else TXT_PRIMARY,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PreampAndSubRow(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    val band = viewModel.selectedBand()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (band != null) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("Freq", color = TXT_MUTED, fontSize = 10.sp, modifier = Modifier.width(60.dp))
                    EditableParam(
                        "", band.frequency, "Hz", 1f, 30000f,
                        { v -> if (v >= 1000f) String.format("%.2fk", v / 1000f) else String.format("%.0f", v) },
                        { viewModel.updateSelectedBand(frequency = it) }, SPECTRUM
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("Gain", color = TXT_MUTED, fontSize = 10.sp, modifier = Modifier.width(60.dp))
                    EditableParam(
                        "", band.gain, "dB", -30f, 30f,
                        { v -> String.format("%+.0f", v) },
                        { viewModel.updateSelectedBand(gain = it) }, PINK_ACCENT
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Q", color = TXT_MUTED, fontSize = 10.sp, modifier = Modifier.width(60.dp))
                    EditableParam(
                        "", band.q, "", 0.1f, 40f,
                        { v -> String.format("%.2f", v) },
                        { viewModel.updateSelectedBand(q = it) }, GOLD
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F13))
                .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = String.format("%+.2f", viewModel.preamp),
                color = RED_ACCENT,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Preamp (db)", color = TXT_MUTED, fontSize = 10.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "−",
                    color = TXT_PRIMARY,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { viewModel.preamp = (viewModel.preamp - 0.5f).coerceIn(-30f, 30f) }
                        .padding(horizontal = 6.dp)
                )
                Text(
                    "+",
                    color = TXT_PRIMARY,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { viewModel.preamp = (viewModel.preamp + 0.5f).coerceIn(-30f, 30f) }
                        .padding(horizontal = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text("SUB 20–40", color = Color(0xFF55EFC4), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    String.format("+%.1f dB", viewModel.subBoost),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SixteenKnobRow(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        viewModel.bands.take(16).forEachIndexed { idx, b ->
            val selected = idx == viewModel.selectedBandIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) SURFACE else Color(0xFF0F0F13))
                    .border(1.dp, if (selected) b.color else CARD_BORDER, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectBand(idx) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${idx + 1}",
                    color = if (selected) Color.Black else TXT_PRIMARY,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (selected) b.color else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Box(modifier = Modifier.padding(top = 2.dp).size(width = 24.dp, height = 16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val path = Path()
                        when (b.filterType) {
                            EqBand.FilterType.LOW_SHELF -> {
                                path.moveTo(0f, h)
                                for (i in 0..20) path.lineTo(i * w / 20f, h * 0.45f)
                                for (i in 0..20) {
                                    val x = w - i * w / 20f
                                    path.lineTo(x, h * (0.45f + i / 20f * 0.5f))
                                }
                            }
                            EqBand.FilterType.HIGH_SHELF -> {
                                path.moveTo(0f, h * 0.45f)
                                for (i in 0..20) {
                                    val x = i * w / 20f
                                    path.lineTo(x, h * (0.45f + i / 20f * 0.5f))
                                }
                            }
                            else -> {
                                path.moveTo(0f, h / 2)
                                for (i in 1..20) {
                                    val x = i * w / 20f
                                    val y = h / 2 - sin((i / 20f) * PI.toFloat()) * h * 0.35f * sign(b.gain)
                                    path.lineTo(x, y)
                                }
                            }
                        }
                        drawPath(
                            path,
                            if (b.enabled) b.color else Color(0xFF666677),
                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressorMultibandaCard(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CARD)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Compresor Multibanda",
                color = TXT_PRIMARY,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Switch(
                checked = viewModel.compressorMultibandEnabled,
                onCheckedChange = { viewModel.compressorMultibandEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PINK_ACCENT,
                    checkedTrackColor = Color(0xFF553344)
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                McSlider("THRESH (S)", viewModel.compMbThLow, "dB", PINK_ACCENT, -40f..0f) {
                    viewModel.compMbThLow = it
                }
                McSlider("THRESH (LM)", viewModel.compMbThLoMid, "dB", SPECTRUM, -40f..0f) {
                    viewModel.compMbThLoMid = it
                }
                McSlider("THRESH (HM)", viewModel.compMbThHiMid, "dB", Color(0xFF55EFC4), -40f..0f) {
                    viewModel.compMbThHiMid = it
                }
                McSlider("THRESH (H)", viewModel.compMbThHigh, "dB", Color(0xFF74B9FF), -40f..0f) {
                    viewModel.compMbThHigh = it
                }
                McSlider("KNEE", viewModel.compMbKnee, "dB", GOLD, 0f..20f) {
                    viewModel.compMbKnee = it
                }
                McSlider("POST GAIN", viewModel.compMbPostGain, "dB", Color(0xFF00B894), -12f..12f) {
                    viewModel.compMbPostGain = it
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp)
                    .background(BG)
                    .border(1.dp, Color(0xFF1F1820), RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                McVisualizer(viewModel)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            McSlider("RATIO", viewModel.compMbRatio, ":1", Color(0xFFFF9F43), 1f..24f, compact = true) {
                viewModel.compMbRatio = it
            }
            viewModel.crossoverFrequencies.forEachIndexed { idx, freq ->
                EditableParam(
                    label = "CROSS ${idx + 1}",
                    value = freq,
                    unit = "Hz",
                    min = 20f,
                    max = 20000f,
                    format = { v -> if (v >= 1000f) String.format("%.1fk", v / 1000f) else String.format("%.0f", v) },
                    onValueChange = { viewModel.crossoverFrequencies[idx] = it },
                    accent = Color(0xFF74B9FF)
                )
            }
        }
    }
}

@Composable
private fun McSlider(
    label: String,
    value: Float,
    unit: String,
    color: Color,
    range: ClosedFloatingPointRange<Float>,
    compact: Boolean = false,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Text(
            label,
            color = TXT_MUTED,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(if (compact) 46.dp else 70.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color(0xFF26262A)
            ),
            modifier = Modifier.weight(1f).height(18.dp)
        )
        Text(
            String.format("%.1f %s", value, unit),
            color = TXT_PRIMARY,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(if (compact) 42.dp else 50.dp)
        )
    }
}

@Composable
private fun McVisualizer(viewModel: EqViewModel) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cuts = viewModel.crossoverFrequencies.toMutableList()
        while (cuts.size < 3) cuts.add(listOf(180f, 1800f, 8000f)[cuts.size])
        fun f2x(f: Float): Float =
            w * (ln(f.coerceAtLeast(20f)) - ln(20f)) / (ln(20000f) - ln(20f))

        drawLine(Color(0xFF333344), Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val active = viewModel.compressorMultibandEnabled
        val colors = listOf(
            Color(0xFFFF6B6B), Color(0xFFFD79A8), Color(0xFF74B9FF), Color(0xFF55EFC4)
        )
        val cutsX = listOf(f2x(cuts[0]), f2x(cuts[1]), f2x(cuts[2]))
        val xs = listOf(0f, cutsX[0], cutsX[1], cutsX[2], w)
        for (band in 0..3) {
            val x0 = xs[band]
            val x1 = xs[band + 1]
            val dir = if (band == 0) -1 else 1
            val path = Path()
            path.moveTo(x0, h * 0.5f)
            for (i in 1..30) {
                val frac = i / 30f
                val x = x0 + (x1 - x0) * frac
                val dip = if (active) 0.42f * (frac * frac) else 0f
                path.lineTo(x, h * (0.5f - dir * dip))
            }
            drawPath(path, colors[band], style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
        cutsX.forEach { x ->
            drawLine(Color(0xFF444444), Offset(x, 0f), Offset(x, h), 1f)
        }
    }
}

@Composable
private fun LimitadorCard(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CARD)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            Text(
                "Limitador",
                color = TXT_PRIMARY,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Switch(
                checked = viewModel.limiterEnabled,
                onCheckedChange = { viewModel.limiterEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RED_ACCENT,
                    checkedTrackColor = Color(0xFF553344)
                )
            )
            Spacer(Modifier.height(4.dp))
            LimiterWaveform()
            VuMeterPair(enabled = viewModel.limiterEnabled)
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            McSlider("THRESH (db)", viewModel.limiterThreshold, "dB", RED_ACCENT, -30f..0f) {
                viewModel.limiterThreshold = it
            }
            McSlider("RATIO", viewModel.limiterRatio, ":1", RED_ACCENT, 1f..24f) {
                viewModel.limiterRatio = it
            }
            McSlider("ATTACK TIME", viewModel.limiterAttack, "ms", RED_ACCENT, 0.1f..100f) {
                viewModel.limiterAttack = it
            }
            McSlider("RELEASE TIME", viewModel.limiterRelease, "ms", RED_ACCENT, 20f..1000f) {
                viewModel.limiterRelease = it
            }
            McSlider("POST GAIN", viewModel.limiterPostGain, "dB", RED_ACCENT, -12f..12f) {
                viewModel.limiterPostGain = it
            }
        }
    }
}

@Composable
private fun LimiterWaveform(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(70.dp)
            .height(36.dp)
            .background(BG)
            .border(1.dp, Color(0xFF22222A), RoundedCornerShape(3.dp))
    ) {
        val w = size.width
        val h = size.height
        val path = Path()
        for (i in 0..80) {
            val x = i * w / 80f
            val y = h / 2 + sin(i * 0.3f) * h * 0.3f * (0.4f + 0.6f * (i % 7) / 7f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, SPECTRUM, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun VuMeterPair(enabled: Boolean, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.height(80.dp)
    ) {
        VuMeterSingle(enabled, modifier = Modifier.weight(1f))
        VuMeterSingle(enabled, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VuMeterSingle(enabled: Boolean, modifier: Modifier = Modifier) {
    val tick by remember { mutableFloatStateOf(0.62f) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0A0A0D))
            .border(1.dp, Color(0xFF26262A), RoundedCornerShape(3.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF11111A), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))
            val active = if (enabled) tick else 0f
            val segmentColors = listOf(
                Color(0xFF2ECC71), Color(0xFF2ECC71), Color(0xFF2ECC71),
                Color(0xFFE1C16E), Color(0xFFE1C16E), Color(0xFFE0566B)
            )
            val segH = h / segmentColors.size
            val filled = active * segmentColors.size
            segmentColors.forEachIndexed { idx, c ->
                val top = idx * segH
                val alpha = if (idx < filled) 1f else 0.15f
                drawRect(
                    c.copy(alpha = alpha),
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(w, segH - 1f)
                )
            }
            if (enabled) {
                val yy = h - (active * h)
                drawLine(Color.White, Offset(0f, yy), Offset(w, yy), 2f)
            }
        }
    }
}

@Composable
private fun PipelineCard(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CARD)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            "Audio Effects Pipeline",
            color = TXT_PRIMARY,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reduce audio for less CPU usage", color = TXT_PRIMARY, fontSize = 10.sp)
                Text("This doesn't affect audio quality", color = TXT_MUTED, fontSize = 8.sp)
            }
            Switch(
                checked = viewModel.audioSessionEnabled,
                onCheckedChange = { viewModel.audioSessionEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RED_ACCENT,
                    checkedTrackColor = Color(0xFF553344)
                )
            )
        }
        var profileExpanded by remember { mutableStateOf(false) }
        ExposedSelector(
            label = "Profile",
            selected = "0: LOAD - Audio TX Output (Float)",
            expanded = profileExpanded,
            onExpandedChange = { profileExpanded = it }
        )
        var speakersExpanded by remember { mutableStateOf(false) }
        ExposedSelector(
            label = "Speakers",
            selected = "0: Speakers",
            expanded = speakersExpanded,
            onExpandedChange = { speakersExpanded = it }
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Processing",
                color = TXT_MUTED,
                fontSize = 10.sp,
                modifier = Modifier.background(SURFACE).padding(horizontal = 4.dp, vertical = 1.dp)
            )
            Switch(
                checked = viewModel.pipelineEnabled,
                onCheckedChange = { viewModel.pipelineEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFC160FF)
                )
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("Master dynamics processing engine to run", color = TXT_MUTED, fontSize = 9.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Output device", color = TXT_PRIMARY, fontSize = 10.sp)
                Text("1497: Mi Monitor (Built-in)", color = TXT_MUTED, fontSize = 9.sp)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MiniTag("BYPASS LSHELF", !viewModel.lowShelfEnabled) {
                viewModel.lowShelfEnabled = !viewModel.lowShelfEnabled
            }
            MiniTag("BYPASS PEAK", !viewModel.peakBandsEnabled) {
                viewModel.peakBandsEnabled = !viewModel.peakBandsEnabled
            }
            MiniTag("BYPASS HSHELF", !viewModel.highShelfEnabled) {
                viewModel.highShelfEnabled = !viewModel.highShelfEnabled
            }
        }
    }
}

@Composable
private fun ExposedSelector(
    label: String,
    selected: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(label, color = TXT_MUTED, fontSize = 10.sp, modifier = Modifier.width(70.dp))
        Surface(
            modifier = Modifier.weight(1f).height(22.dp).clickable { onExpandedChange(!expanded) },
            color = SURFACE,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CARD_BORDER)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selected, color = TXT_PRIMARY, fontSize = 9.sp, maxLines = 1)
                Text(if (expanded) "▲" else "▼", color = TXT_MUTED, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun MiniTag(label: String, active: Boolean, onChange: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.Black else TXT_PRIMARY,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) SPECTRUM else SURFACE)
            .border(1.dp, if (active) SPECTRUM else CARD_BORDER, RoundedCornerShape(4.dp))
            .clickable { onChange() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
fun EditableParam(
    label: String,
    value: Float,
    unit: String,
    min: Float,
    max: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    accent: Color
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(format(value)) }
    val focusManager = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) {
            Text(
                label,
                color = TXT_MUTED,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    text.toFloatOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                    editing = false
                    focusManager.clearFocus()
                }),
                singleLine = true,
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .width(74.dp)
                    .background(BG, RoundedCornerShape(4.dp))
                    .border(1.dp, accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        } else {
            Text(
                text = if (unit.isEmpty()) format(value) else "${format(value)} $unit",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        text = value.toString()
                        editing = true
                    }
                    .background(BG)
                    .border(1.dp, CARD_BORDER, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun CrossoverSection(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Crossover Multibanda", color = TXT_PRIMARY, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("3 cortes · 4 bandas de compresión", color = Color(0xFF55EFC4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        viewModel.crossoverFrequencies.forEachIndexed { idx, freq ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Corte ${idx + 1}", color = TXT_PRIMARY, modifier = Modifier.width(80.dp), fontSize = 11.sp)
                EditableParam(
                    label = "Hz",
                    value = freq,
                    unit = "Hz",
                    min = 20f,
                    max = 20000f,
                    format = { v -> if (v >= 1000f) String.format("%.1fk", v / 1000f) else String.format("%.0f", v) },
                    onValueChange = { viewModel.crossoverFrequencies[idx] = it },
                    accent = Color(0xFF74B9FF)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditableParam("TH-LO", viewModel.compMbThLow, "dB", -40f, 0f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbThLow = it }, PINK_ACCENT)
            EditableParam("TH-LM", viewModel.compMbThLoMid, "dB", -40f, 0f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbThLoMid = it }, SPECTRUM)
            EditableParam("TH-HM", viewModel.compMbThHiMid, "dB", -40f, 0f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbThHiMid = it }, Color(0xFF55EFC4))
            EditableParam("TH-HI", viewModel.compMbThHigh, "dB", -40f, 0f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbThHigh = it }, Color(0xFF74B9FF))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EditableParam("RATIO", viewModel.compMbRatio, ":1", 1f, 24f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbRatio = it }, Color(0xFFFF9F43))
            EditableParam("KNEE", viewModel.compMbKnee, "dB", 0f, 20f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbKnee = it }, GOLD)
            EditableParam("ATTACK", viewModel.compMbAttack, "ms", 1f, 200f,
                { v -> String.format("%.1f", v) }, { viewModel.compMbAttack = it }, Color(0xFF00CEC9))
            EditableParam("RELEASE", viewModel.compMbRelease, "ms", 10f, 1000f,
                { v -> String.format("%.0f", v) }, { viewModel.compMbRelease = it }, Color(0xFFA29BFE))
        }
        Text(
            "Limitador nativo DynamicsProcessing · soft-knee",
            color = TXT_MUTED,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun LimiterScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Limitador", color = TXT_PRIMARY, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Protección anti-distorsión", color = TXT_MUTED, fontSize = 11.sp)
            }
            Switch(
                checked = viewModel.limiterEnabled,
                onCheckedChange = { viewModel.limiterEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = RED_ACCENT)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditableParam("THRESH", viewModel.limiterThreshold, "dB", -30f, 0f,
                { v -> String.format("%.1f", v) }, { viewModel.limiterThreshold = it }, RED_ACCENT)
            EditableParam("ATTACK", viewModel.limiterAttack, "ms", 0.5f, 80f,
                { v -> String.format("%.1f", v) }, { viewModel.limiterAttack = it }, RED_ACCENT)
            EditableParam("RELEASE", viewModel.limiterRelease, "ms", 20f, 1000f,
                { v -> String.format("%.0f", v) }, { viewModel.limiterRelease = it }, RED_ACCENT)
            EditableParam("RATIO", viewModel.limiterRatio, ":1", 1f, 24f,
                { v -> String.format("%.1f", v) }, { viewModel.limiterRatio = it }, RED_ACCENT)
            EditableParam("POST", viewModel.limiterPostGain, "dB", -12f, 12f,
                { v -> String.format("%+.1f", v) }, { viewModel.limiterPostGain = it }, RED_ACCENT)
        }
        Text(
            "Limiter nativo DynamicsProcessing · hard-knee final",
            color = TXT_MUTED,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
