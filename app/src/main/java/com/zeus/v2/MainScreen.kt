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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

private val LOW_COLOR = Color(0xFF4FC3F7)
private val LOMID_COLOR = Color(0xFF66BB6A)
private val HIMID_COLOR = Color(0xFFFFCA28)
private val HIGH_COLOR = Color(0xFFAB47BC)

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
                .padding(bottom = 6.dp),
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
                IconButton(onClick = { viewModel.previousSection() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TXT_MUTED)
                }
                Text(
                    text = viewModel.sectionTitle(),
                    color = TXT_PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = { viewModel.nextSection() }, modifier = Modifier.size(32.dp)) {
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
                        .background(if (viewModel.isEngineRunning) Color(0xFF2ECC71) else Color(0xFF333344))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        when (viewModel.currentSection) {
            EqSection.EQUALIZER, EqSection.PIPELINE -> EqualizerScreen(viewModel, Modifier.weight(1f))
            EqSection.CROSSOVER -> CrossoverScreen(viewModel, Modifier.weight(1f))
            EqSection.LIMITER -> LimiterScreen(viewModel, Modifier.weight(1f))
        }
    }
}

// ============================================================
// EQUALIZER
// ============================================================
@Composable
private fun EqualizerScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    val band = viewModel.selectedBand()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpectrumCard(viewModel.spectrum, Modifier.fillMaxWidth().weight(1f))
        FilterTypeRow(viewModel)
        if (band != null) BandControlsCard(viewModel, band)
        PreampRow(viewModel)
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
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..5) {
                val y = h * i / 5f
                drawLine(GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            if (spectrum.isNotEmpty()) {
                val path = Path()
                val n = (spectrum.size - 1).coerceAtLeast(1)
                spectrum.forEachIndexed { i, v ->
                    val x = w * i / n.toFloat()
                    val y = h * (1f - v.coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, SPECTRUM, style = Stroke(width = 2f, cap = StrokeCap.Round))
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
                Text(name, color = if (active) Color.Black else TXT_PRIMARY, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
        EditableValueRow("Freq", band.frequency, 1f..30000f, "Hz",
            { if (it >= 1000f) String.format("%.2fk", it / 1000f) else String.format("%.1f", it) }) {
            viewModel.updateSelectedBand(frequency = it)
        }
        EditableValueRow("Gain", band.gain, -30f..30f, "dB", { String.format("%+.1f", it) }) {
            viewModel.updateSelectedBand(gain = it)
        }
        EditableValueRow("Q", band.q, 0.1f..40f, "", { String.format("%.2f", it) }) {
            viewModel.updateSelectedBand(q = it)
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
            colors = SliderDefaults.colors(thumbColor = PINK_ACCENT, activeTrackColor = PINK_ACCENT)
        )
        Text(String.format("%.1f dB", viewModel.preamp), color = TXT_PRIMARY, fontSize = 13.sp, modifier = Modifier.width(70.dp))
    }
}

@Composable
private fun BandSelectorRow(viewModel: EqViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                Text("${index + 1}", color = if (selected) Color.Black else TXT_PRIMARY, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A3A2A))
                .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(8.dp))
                .clickable { viewModel.addBand() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color(0xFF2ECC71), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================
// COMPRESOR MULTIBANDA – 4 columnas independientes
// ============================================================
@Composable
private fun CrossoverScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Compresor Multibanda", color = TXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activado", color = TXT_MUTED, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = viewModel.compressorMultibandEnabled,
                    onCheckedChange = { viewModel.compressorMultibandEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PINK_ACCENT,
                        checkedTrackColor = PINK_ACCENT.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // Crossovers
        Card(
            colors = CardDefaults.cardColors(containerColor = SURFACE),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Crossovers", color = TXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                EditableValueRow("Cross 1", viewModel.crossoverFrequencies[0], 40f..1000f, "Hz",
                    { String.format("%.0f", it) }) { viewModel.crossoverFrequencies[0] = it }
                EditableValueRow("Cross 2", viewModel.crossoverFrequencies[1], 200f..8000f, "Hz",
                    { String.format("%.0f", it) }) { viewModel.crossoverFrequencies[1] = it }
                EditableValueRow("Cross 3", viewModel.crossoverFrequencies[2], 1000f..18000f, "Hz",
                    { String.format("%.0f", it) }) { viewModel.crossoverFrequencies[2] = it }
            }
        }

        // 4 columnas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompBandColumn(
                title = "LOW",
                color = LOW_COLOR,
                threshold = viewModel.compMbThLow,
                ratio = viewModel.compMbRatioLow,
                knee = viewModel.compMbKneeLow,
                attack = viewModel.compMbAttackLow,
                release = viewModel.compMbReleaseLow,
                postGain = viewModel.compMbPostGainLow,
                onThreshold = { viewModel.compMbThLow = it },
                onRatio = { viewModel.compMbRatioLow = it },
                onKnee = { viewModel.compMbKneeLow = it },
                onAttack = { viewModel.compMbAttackLow = it },
                onRelease = { viewModel.compMbReleaseLow = it },
                onPostGain = { viewModel.compMbPostGainLow = it },
                modifier = Modifier.weight(1f)
            )
            CompBandColumn(
                title = "LO-MID",
                color = LOMID_COLOR,
                threshold = viewModel.compMbThLoMid,
                ratio = viewModel.compMbRatioLoMid,
                knee = viewModel.compMbKneeLoMid,
                attack = viewModel.compMbAttackLoMid,
                release = viewModel.compMbReleaseLoMid,
                postGain = viewModel.compMbPostGainLoMid,
                onThreshold = { viewModel.compMbThLoMid = it },
                onRatio = { viewModel.compMbRatioLoMid = it },
                onKnee = { viewModel.compMbKneeLoMid = it },
                onAttack = { viewModel.compMbAttackLoMid = it },
                onRelease = { viewModel.compMbReleaseLoMid = it },
                onPostGain = { viewModel.compMbPostGainLoMid = it },
                modifier = Modifier.weight(1f)
            )
            CompBandColumn(
                title = "HI-MID",
                color = HIMID_COLOR,
                threshold = viewModel.compMbThHiMid,
                ratio = viewModel.compMbRatioHiMid,
                knee = viewModel.compMbKneeHiMid,
                attack = viewModel.compMbAttackHiMid,
                release = viewModel.compMbReleaseHiMid,
                postGain = viewModel.compMbPostGainHiMid,
                onThreshold = { viewModel.compMbThHiMid = it },
                onRatio = { viewModel.compMbRatioHiMid = it },
                onKnee = { viewModel.compMbKneeHiMid = it },
                onAttack = { viewModel.compMbAttackHiMid = it },
                onRelease = { viewModel.compMbReleaseHiMid = it },
                onPostGain = { viewModel.compMbPostGainHiMid = it },
                modifier = Modifier.weight(1f)
            )
            CompBandColumn(
                title = "HIGH",
                color = HIGH_COLOR,
                threshold = viewModel.compMbThHigh,
                ratio = viewModel.compMbRatioHigh,
                knee = viewModel.compMbKneeHigh,
                attack = viewModel.compMbAttackHigh,
                release = viewModel.compMbReleaseHigh,
                postGain = viewModel.compMbPostGainHigh,
                onThreshold = { viewModel.compMbThHigh = it },
                onRatio = { viewModel.compMbRatioHigh = it },
                onKnee = { viewModel.compMbKneeHigh = it },
                onAttack = { viewModel.compMbAttackHigh = it },
                onRelease = { viewModel.compMbReleaseHigh = it },
                onPostGain = { viewModel.compMbPostGainHigh = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompBandColumn(
    title: String,
    color: Color,
    threshold: Float,
    ratio: Float,
    knee: Float,
    attack: Float,
    release: Float,
    postGain: Float,
    onThreshold: (Float) -> Unit,
    onRatio: (Float) -> Unit,
    onKnee: (Float) -> Unit,
    onAttack: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onPostGain: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SURFACE),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

            ValueBox("Thresh", String.format("%.1f", threshold), "dB", -40f..0f, threshold, onThreshold, color)
            ValueBox("Ratio", String.format("%.1f", ratio), "", 1f..20f, ratio, onRatio, color)
            ValueBox("Knee", String.format("%.1f", knee), "dB", 0f..20f, knee, onKnee, color)
            ValueBox("Attack", String.format("%.0f", attack), "ms", 1f..200f, attack, onAttack, color)
            ValueBox("Release", String.format("%.0f", release), "ms", 10f..500f, release, onRelease, color)
            ValueBox("Post", String.format("%+.1f", postGain), "dB", -12f..12f, postGain, onPostGain, color)
        }
    }
}

@Composable
private fun ValueBox(
    label: String,
    display: String,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(display) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TXT_MUTED, fontSize = 10.sp)
        if (isEditing) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                modifier = Modifier.width(70.dp).height(40.dp),
                textStyle = LocalTextStyle.current.copy(color = TXT_PRIMARY, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = CARD_BORDER,
                    focusedTextColor = TXT_PRIMARY,
                    unfocusedTextColor = TXT_PRIMARY,
                    cursorColor = accent
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions(onDone = {
                    textValue.toFloatOrNull()?.let {
                        onValueChange(it.coerceIn(range.start, range.endInclusive))
                    }
                    isEditing = false
                })
            )
        } else {
            Text(
                text = if (unit.isEmpty()) display else "$display $unit",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A20))
                    .clickable {
                        textValue = display
                        isEditing = true
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

// ============================================================
// LIMITER
// ============================================================
@Composable
private fun LimiterScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
        EditableValueRow("Threshold", viewModel.limiterThreshold, -20f..0f, "dB", { String.format("%.1f", it) }) { viewModel.limiterThreshold = it }
        EditableValueRow("Attack", viewModel.limiterAttack, 0.5f..50f, "ms", { String.format("%.1f", it) }) { viewModel.limiterAttack = it }
        EditableValueRow("Release", viewModel.limiterRelease, 20f..500f, "ms", { String.format("%.0f", it) }) { viewModel.limiterRelease = it }
        EditableValueRow("Ratio", viewModel.limiterRatio, 1f..50f, "", { String.format("%.1f", it) }) { viewModel.limiterRatio = it }
        EditableValueRow("Post Gain", viewModel.limiterPostGain, -12f..12f, "dB", { String.format("%+.1f", it) }) { viewModel.limiterPostGain = it }

        Divider(color = CARD_BORDER, modifier = Modifier.padding(vertical = 8.dp))
        Text("Filtros activos", color = TXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun EditableValueRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(format(value)) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = PINK_ACCENT, activeTrackColor = PINK_ACCENT)
        )
        if (isEditing) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                modifier = Modifier.width(80.dp),
                textStyle = LocalTextStyle.current.copy(color = TXT_PRIMARY, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PINK_ACCENT,
                    unfocusedBorderColor = CARD_BORDER,
                    focusedTextColor = TXT_PRIMARY,
                    unfocusedTextColor = TXT_PRIMARY,
                    cursorColor = PINK_ACCENT
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions(onDone = {
                    textValue.toFloatOrNull()?.let {
                        onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive))
                    }
                    isEditing = false
                })
            )
        } else {
            Text(
                text = format(value) + if (unit.isNotEmpty()) " $unit" else "",
                color = PINK_ACCENT,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(80.dp).clickable {
                    textValue = format(value)
                    isEditing = true
                }.padding(4.dp)
            )
        }
    }
}
