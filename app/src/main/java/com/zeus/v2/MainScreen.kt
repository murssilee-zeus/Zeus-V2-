package com.zeus.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG = Color(0xFF0B0B0C)
private val SURFACE = Color(0xFF131316)
private val CARD = Color(0xFF16161B)
private val CARD_BORDER = Color(0xFF26262A)
private val GRID = Color(0xFF2A2236)
private val SPECTRUM = Color(0xFFC160FF)
private val PINK_ACCENT = Color(0xFFFF6B9E)
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
        // ========== TOP BAR ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                IconButton(
                    onClick = { viewModel.previousSection() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = TXT_MUTED
                    )
                }
                Text(
                    text = viewModel.sectionTitle(),
                    color = TXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(
                    onClick = { viewModel.nextSection() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TXT_MUTED
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Guardar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E4D3A))
                        .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(16.dp))
                        .clickable { onSave() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                IconButton(
                    onClick = onToggleEngine,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (viewModel.isEngineRunning) Color(0xFF2ECC71)
                            else Color(0xFF333344)
                        )
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ========== CONTENIDO SEGÚN SECCIÓN ==========
        when (viewModel.currentSection) {
            EqSection.EQUALIZER, EqSection.PIPELINE -> {
                EqualizerScreen(viewModel, Modifier.weight(1f))
            }
            EqSection.CROSSOVER -> {
                CrossoverScreen(viewModel, Modifier.weight(1f))
            }
            EqSection.LIMITER -> {
                LimiterScreen(viewModel, Modifier.weight(1f))
            }
        }
    }
}

// ============================================================
// PANTALLA 1: EQUALIZER
// ============================================================
@Composable
private fun EqualizerScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    val band = viewModel.selectedBand()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Spectrum
        SpectrumCard(
            spectrum = viewModel.spectrum,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Tipos de filtro
        FilterTypeRow(viewModel)

        // Controles de la banda seleccionada
        if (band != null) {
            BandControlsCard(viewModel, band)
        }

        // Preamp
        PreampRow(viewModel)

        // Selector de bandas + botón agregar
        BandSelectorRow(viewModel)
    }
}

@Composable
private fun SpectrumCard(spectrum: FloatArray, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Grid
            for (i in 0..5) {
                val y = h * i / 5f
                drawLine(GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Spectrum
            if (spectrum.isNotEmpty()) {
                val path = Path()
                val n = (spectrum.size - 1).coerceAtLeast(1)
                spectrum.forEachIndexed { i, v ->
                    val x = w * i / n.toFloat()
                    val y = h * (1f - v.coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    color = SPECTRUM,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun FilterTypeRow(viewModel: EqViewModel) {
    val band = viewModel.selectedBand()
    val types = listOf(
        EqBand.FilterType.PEAK to "PEAK",
        EqBand.FilterType.LOW_SHELF to "LSHELF",
        EqBand.FilterType.HIGH_SHELF to "HSHELF",
        EqBand.FilterType.LOW_PASS to "LPF",
        EqBand.FilterType.HIGH_PASS to "HPF",
        EqBand.FilterType.BYPASS to "BYPASS"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        types.forEach { (type, name) ->
            val active = band?.filterType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) PINK_ACCENT else Color(0xFF1A1A20))
                    .clickable { viewModel.updateSelectedBand(filterType = type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = if (active) Color.Black else TXT_PRIMARY,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BandControlsCard(viewModel: EqViewModel, band: EqBand) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Frecuencia
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Freq", color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(50.dp))
            Slider(
                value = band.frequency,
                onValueChange = { viewModel.updateSelectedBand(frequency = it) },
                valueRange = 20f..20000f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PINK_ACCENT,
                    activeTrackColor = PINK_ACCENT
                )
            )
            Text(
                text = if (band.frequency >= 1000f)
                    String.format("%.1fk", band.frequency / 1000f)
                else
                    String.format("%.0f", band.frequency),
                color = TXT_PRIMARY,
                fontSize = 13.sp,
                modifier = Modifier.width(55.dp)
            )
        }

        // Gain (±30 dB)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gain", color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(50.dp))
            Slider(
                value = band.gain,
                onValueChange = { viewModel.updateSelectedBand(gain = it) },
                valueRange = -30f..30f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PINK_ACCENT,
                    activeTrackColor = PINK_ACCENT
                )
            )
            Text(
                text = String.format("%+.1f", band.gain),
                color = TXT_PRIMARY,
                fontSize = 13.sp,
                modifier = Modifier.width(55.dp)
            )
        }

        // Q (0.1 – 20)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Q", color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(50.dp))
            Slider(
                value = band.q,
                onValueChange = { viewModel.updateSelectedBand(q = it) },
                valueRange = 0.1f..20f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PINK_ACCENT,
                    activeTrackColor = PINK_ACCENT
                )
            )
            Text(
                text = String.format("%.2f", band.q),
                color = TXT_PRIMARY,
                fontSize = 13.sp,
                modifier = Modifier.width(55.dp)
            )
        }
    }
}

@Composable
private fun PreampRow(viewModel: EqViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Preamp", color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(70.dp))
        Slider(
            value = viewModel.preamp,
            onValueChange = { viewModel.preamp = it },
            valueRange = -30f..12f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = PINK_ACCENT,
                activeTrackColor = PINK_ACCENT
            )
        )
        Text(
            text = String.format("%.1f dB", viewModel.preamp),
            color = TXT_PRIMARY,
            fontSize = 13.sp,
            modifier = Modifier.width(70.dp)
        )
    }
}

@Composable
private fun BandSelectorRow(viewModel: EqViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        viewModel.bands.forEachIndexed { index, band ->
            val selected = index == viewModel.selectedBandIndex
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) band.color else SURFACE)
                    .border(1.dp, if (selected) band.color else CARD_BORDER, RoundedCornerShape(8.dp))
                    .clickable { viewModel.selectBand(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (selected) Color.Black else TXT_PRIMARY,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Botón agregar banda
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A3A2A))
                .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(8.dp))
                .clickable { viewModel.addBand() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color(0xFF2ECC71),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// PANTALLA 2: CROSSOVER / COMPRESSOR
// ============================================================
@Composable
private fun CrossoverScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compresor Multibanda", color = TXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activado", color = TXT_MUTED, modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.compressorMultibandEnabled,
                onCheckedChange = { viewModel.compressorMultibandEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PINK_ACCENT,
                    checkedTrackColor = PINK_ACCENT.copy(alpha = 0.5f)
                )
            )
        }

        SliderRow("Cross 1", viewModel.crossoverFrequencies[0], 40f..1000f, "Hz") {
            viewModel.crossoverFrequencies[0] = it
        }
        SliderRow("Cross 2", viewModel.crossoverFrequencies[1], 200f..8000f, "Hz") {
            viewModel.crossoverFrequencies[1] = it
        }
        SliderRow("Cross 3", viewModel.crossoverFrequencies[2], 1000f..18000f, "Hz") {
            viewModel.crossoverFrequencies[2] = it
        }

        Divider(color = CARD_BORDER)

        SliderRow("Th Low", viewModel.compMbThLow, -40f..0f, "dB") { viewModel.compMbThLow = it }
        SliderRow("Th LoMid", viewModel.compMbThLoMid, -40f..0f, "dB") { viewModel.compMbThLoMid = it }
        SliderRow("Th HiMid", viewModel.compMbThHiMid, -40f..0f, "dB") { viewModel.compMbThHiMid = it }
        SliderRow("Th High", viewModel.compMbThHigh, -40f..0f, "dB") { viewModel.compMbThHigh = it }

        SliderRow("Ratio", viewModel.compMbRatio, 1f..20f, "") { viewModel.compMbRatio = it }
        SliderRow("Knee", viewModel.compMbKnee, 0f..20f, "dB") { viewModel.compMbKnee = it }
        SliderRow("Attack", viewModel.compMbAttack, 1f..100f, "ms") { viewModel.compMbAttack = it }
        SliderRow("Release", viewModel.compMbRelease, 10f..500f, "ms") { viewModel.compMbRelease = it }
        SliderRow("Post Gain", viewModel.compMbPostGain, -12f..12f, "dB") { viewModel.compMbPostGain = it }
    }
}

// ============================================================
// PANTALLA 3: LIMITER + PIPELINE
// ============================================================
@Composable
private fun LimiterScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Limiter", color = TXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activado", color = TXT_MUTED, modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.limiterEnabled,
                onCheckedChange = { viewModel.limiterEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PINK_ACCENT,
                    checkedTrackColor = PINK_ACCENT.copy(alpha = 0.5f)
                )
            )
        }

        SliderRow("Threshold", viewModel.limiterThreshold, -20f..0f, "dB") { viewModel.limiterThreshold = it }
        SliderRow("Attack", viewModel.limiterAttack, 0.5f..50f, "ms") { viewModel.limiterAttack = it }
        SliderRow("Release", viewModel.limiterRelease, 20f..500f, "ms") { viewModel.limiterRelease = it }
        SliderRow("Ratio", viewModel.limiterRatio, 1f..30f, "") { viewModel.limiterRatio = it }
        SliderRow("Post Gain", viewModel.limiterPostGain, -12f..12f, "dB") { viewModel.limiterPostGain = it }

        Divider(color = CARD_BORDER, modifier = Modifier.padding(vertical = 8.dp))

        Text("Audio Effects Pipeline", color = TXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        SwitchRow("Pipeline", viewModel.pipelineEnabled) { viewModel.pipelineEnabled = it }
        SwitchRow("Low Shelf", viewModel.lowShelfEnabled) { viewModel.lowShelfEnabled = it }
        SwitchRow("Peak Bands", viewModel.peakBandsEnabled) { viewModel.peakBandsEnabled = it }
        SwitchRow("High Shelf", viewModel.highShelfEnabled) { viewModel.highShelfEnabled = it }
    }
}

// ============================================================
// COMPONENTES REUTILIZABLES
// ============================================================
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = PINK_ACCENT,
                activeTrackColor = PINK_ACCENT
            )
        )
        Text(
            text = if (unit.isEmpty()) String.format("%.1f", value)
            else String.format("%.1f %s", value, unit),
            color = TXT_PRIMARY,
            fontSize = 12.sp,
            modifier = Modifier.width(70.dp)
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TXT_MUTED, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PINK_ACCENT,
                checkedTrackColor = PINK_ACCENT.copy(alpha = 0.5f)
            )
        )
    }
}
