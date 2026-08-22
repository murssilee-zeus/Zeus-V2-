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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln

private val BG = Color(0xFF0B0B0C)
private val SURFACE = Color(0xFF131316)
private val CARD_BORDER = Color(0xFF26262A)
private val PINK_ACCENT = Color(0xFFFF6B9E)
private val TXT_PRIMARY = Color(0xFFECECEE)
private val TXT_MUTED = Color(0xFF888892)

private val LOW_COLOR = Color(0xFF4FC3F7)
private val LOMID_COLOR = Color(0xFF66BB6A)
private val HIMID_COLOR = Color(0xFFFFCA28)
private val HIGH_COLOR = Color(0xFFAB47BC)

private val LIM_PURPLE = Color(0xFFB56BFF)
private val LIM_PURPLE_DIM = Color(0xFF6B3FA0)
private val LIM_KNOB_BG = Color(0xFF1A1520)
private val LIM_PANEL = Color(0xFF141018)

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

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.15f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqGraph(
                bands = viewModel.bands,
                selectedIndex = viewModel.selectedBandIndex,
                spectrum = viewModel.spectrum,
                onBandSelected = { viewModel.selectBand(it) },
                onBandMoved = { idx, freq, gain ->
                    viewModel.selectBand(idx)
                    viewModel.updateSelectedBand(frequency = freq, gain = gain)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            BandSelectorRow(viewModel)
        }

        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTypeRow(viewModel)
            if (band != null) {
                BandControlsCard(viewModel, band, Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SURFACE)
                        .border(1.dp, CARD_BORDER, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Selecciona una banda", color = TXT_MUTED, fontSize = 13.sp)
                }
            }
            PreampRow(viewModel)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Text("Filter Type", color = TXT_MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        name,
                        color = if (active) Color.Black else TXT_PRIMARY,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BandControlsCard(
    viewModel: EqViewModel,
    band: EqBand,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Banda ${viewModel.selectedBandIndex + 1}",
            color = band.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        EditableValueRow(
            "Freq", band.frequency, 1f..30000f, "Hz",
            { if (it >= 1000f) String.format("%.2fk", it / 1000f) else String.format("%.1f", it) }
        ) { viewModel.updateSelectedBand(frequency = it) }
        EditableValueRow(
            "Gain", band.gain, -30f..30f, "dB",
            { String.format("%+.1f", it) }
        ) { viewModel.updateSelectedBand(gain = it) }
        EditableValueRow(
            "Q", band.q, 0.1f..40f, "",
            { String.format("%.2f", it) }
        ) { viewModel.updateSelectedBand(q = it) }
    }
}

@Composable
private fun PreampRow(viewModel: EqViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SURFACE)
            .border(1.dp, CARD_BORDER, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Preamp", color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(60.dp))
        Slider(
            value = viewModel.preamp,
            onValueChange = { viewModel.preamp = it },
            valueRange = -30f..12f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = PINK_ACCENT, activeTrackColor = PINK_ACCENT)
        )
        Text(
            String.format("%.1f dB", viewModel.preamp),
            color = TXT_PRIMARY,
            fontSize = 13.sp,
            modifier = Modifier.width(64.dp)
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
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) band.color else SURFACE)
                    .border(1.dp, if (selected) band.color else CARD_BORDER, RoundedCornerShape(8.dp))
                    .clickable { viewModel.selectBand(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    color = if (selected) Color.Black else TXT_PRIMARY,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A3A2A))
                .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(8.dp))
                .clickable { viewModel.addBand() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color(0xFF2ECC71), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================
// COMPRESOR — barras = frecuencias de corte
// ============================================================
@Composable
private fun CrossoverScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    var selectedCompBand by remember { mutableIntStateOf(0) }

    val colors = listOf(LOW_COLOR, LOMID_COLOR, HIMID_COLOR, HIGH_COLOR)
    val titles = listOf("LOW", "LO-MID", "HI-MID", "HIGH")

    val thresholds = listOf(
        viewModel.compMbThLow, viewModel.compMbThLoMid,
        viewModel.compMbThHiMid, viewModel.compMbThHigh
    )
    val ratios = listOf(
        viewModel.compMbRatioLow, viewModel.compMbRatioLoMid,
        viewModel.compMbRatioHiMid, viewModel.compMbRatioHigh
    )
    val knees = listOf(
        viewModel.compMbKneeLow, viewModel.compMbKneeLoMid,
        viewModel.compMbKneeHiMid, viewModel.compMbKneeHigh
    )
    val attacks = listOf(
        viewModel.compMbAttackLow, viewModel.compMbAttackLoMid,
        viewModel.compMbAttackHiMid, viewModel.compMbAttackHigh
    )
    val releases = listOf(
        viewModel.compMbReleaseLow, viewModel.compMbReleaseLoMid,
        viewModel.compMbReleaseHiMid, viewModel.compMbReleaseHigh
    )
    val posts = listOf(
        viewModel.compMbPostGainLow, viewModel.compMbPostGainLoMid,
        viewModel.compMbPostGainHiMid, viewModel.compMbPostGainHigh
    )
    val preamps = listOf(
        viewModel.compMbPreGainLow,
        viewModel.compMbPreGainLoMid,
        viewModel.compMbPreGainHiMid,
        viewModel.compMbPreGainHigh
    )

    // Frecuencias mostradas en cada columna (corte superior de la banda)
    val crossFreqs = listOf(
        viewModel.crossoverFrequencies[0],
        viewModel.crossoverFrequencies[1],
        viewModel.crossoverFrequencies[2],
        20000f
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Compresor Multibanda", color = TXT_PRIMARY, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activado", color = TXT_MUTED, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            titles.forEachIndexed { i, title ->
                val sel = selectedCompBand == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) colors[i].copy(alpha = 0.25f) else SURFACE)
                        .border(1.dp, if (sel) colors[i] else CARD_BORDER, RoundedCornerShape(8.dp))
                        .clickable { selectedCompBand = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (sel) colors[i] else TXT_MUTED,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Panel lateral
            Card(
                colors = CardDefaults.cardColors(containerColor = SURFACE),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        titles[selectedCompBand],
                        color = colors[selectedCompBand],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CompValueEdit("Thresh", thresholds[selectedCompBand], "dB", -40f..0f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbThLow = it
                            1 -> viewModel.compMbThLoMid = it
                            2 -> viewModel.compMbThHiMid = it
                            3 -> viewModel.compMbThHigh = it
                        }
                    }
                    CompValueEdit("Ratio", ratios[selectedCompBand], "", 1f..20f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbRatioLow = it
                            1 -> viewModel.compMbRatioLoMid = it
                            2 -> viewModel.compMbRatioHiMid = it
                            3 -> viewModel.compMbRatioHigh = it
                        }
                    }
                    CompValueEdit("Knee", knees[selectedCompBand], "dB", 0f..20f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbKneeLow = it
                            1 -> viewModel.compMbKneeLoMid = it
                            2 -> viewModel.compMbKneeHiMid = it
                            3 -> viewModel.compMbKneeHigh = it
                        }
                    }
                    CompValueEdit("Attack", attacks[selectedCompBand], "ms", 1f..200f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbAttackLow = it
                            1 -> viewModel.compMbAttackLoMid = it
                            2 -> viewModel.compMbAttackHiMid = it
                            3 -> viewModel.compMbAttackHigh = it
                        }
                    }
                    CompValueEdit("Release", releases[selectedCompBand], "ms", 10f..500f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbReleaseLow = it
                            1 -> viewModel.compMbReleaseLoMid = it
                            2 -> viewModel.compMbReleaseHiMid = it
                            3 -> viewModel.compMbReleaseHigh = it
                        }
                    }
                    CompValueEdit("Post", posts[selectedCompBand], "dB", -12f..12f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbPostGainLow = it
                            1 -> viewModel.compMbPostGainLoMid = it
                            2 -> viewModel.compMbPostGainHiMid = it
                            3 -> viewModel.compMbPostGainHigh = it
                        }
                    }
                    CompValueEdit("Preamp", preamps[selectedCompBand], "dB", -12f..12f, colors[selectedCompBand]) {
                        when (selectedCompBand) {
                            0 -> viewModel.compMbPreGainLow = it
                            1 -> viewModel.compMbPreGainLoMid = it
                            2 -> viewModel.compMbPreGainHiMid = it
                            3 -> viewModel.compMbPreGainHigh = it
                        }
                    }
                }
            }

            // 4 columnas: slider = frecuencia de corte
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // LOW → cross1
                CompFreqColumn(
                    title = "LOW",
                    color = colors[0],
                    selected = selectedCompBand == 0,
                    freq = viewModel.crossoverFrequencies[0],
                    range = 40f..1000f,
                    onSelect = { selectedCompBand = 0 },
                    onFreq = { viewModel.setCrossover(0, it) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // LO-MID → cross2
                CompFreqColumn(
                    title = "LO-MID",
                    color = colors[1],
                    selected = selectedCompBand == 1,
                    freq = viewModel.crossoverFrequencies[1],
                    range = 200f..6000f,
                    onSelect = { selectedCompBand = 1 },
                    onFreq = { viewModel.setCrossover(1, it) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // HI-MID → cross3
                CompFreqColumn(
                    title = "HI-MID",
                    color = colors[2],
                    selected = selectedCompBand == 2,
                    freq = viewModel.crossoverFrequencies[2],
                    range = 2000f..16000f,
                    onSelect = { selectedCompBand = 2 },
                    onFreq = { viewModel.setCrossover(2, it) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // HIGH → fijo 20 kHz (solo visual / seleccionar)
                CompFreqColumn(
                    title = "HIGH",
                    color = colors[3],
                    selected = selectedCompBand == 3,
                    freq = 20000f,
                    range = 20000f..20000f,
                    onSelect = { selectedCompBand = 3 },
                    onFreq = { },
                    enabled = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        CrossoverBandsGraph(
            cross1 = viewModel.crossoverFrequencies[0],
            cross2 = viewModel.crossoverFrequencies[1],
            cross3 = viewModel.crossoverFrequencies[2],
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SURFACE)
                .border(1.dp, CARD_BORDER, RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun CompFreqColumn(
    title: String,
    color: Color,
    selected: Boolean,
    freq: Float,
    range: ClosedFloatingPointRange<Float>,
    onSelect: () -> Unit,
    onFreq: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    fun formatFreq(f: Float): String {
        return if (f >= 1000f) String.format("%.1f kHz", f / 1000f)
        else String.format("%.0f Hz", f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SURFACE),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = if (selected) color else TXT_MUTED,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatFreq(freq),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (enabled && range.endInclusive > range.start) {
                Slider(
                    value = freq.coerceIn(range.start, range.endInclusive),
                    onValueChange = onFreq,
                    valueRange = range,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = color.copy(alpha = 0.2f)
                    )
                )
            } else {
                Spacer(Modifier.weight(1f))
                Text("fijo", color = TXT_MUTED, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CompValueEdit(
    label: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(String.format("%.1f", value)) }

    Column {
        Text(label, color = TXT_MUTED, fontSize = 10.sp)
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                textStyle = LocalTextStyle.current.copy(color = TXT_PRIMARY, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = color,
                    unfocusedBorderColor = CARD_BORDER,
                    focusedTextColor = TXT_PRIMARY,
                    unfocusedTextColor = TXT_PRIMARY,
                    cursorColor = color
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions(onDone = {
                    text.toFloatOrNull()?.let {
                        onValueChange(it.coerceIn(range.start, range.endInclusive))
                    }
                    editing = false
                })
            )
        } else {
            Text(
                text = if (unit.isEmpty()) String.format("%.1f", value)
                else String.format("%.1f %s", value, unit),
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A20))
                    .clickable {
                        text = String.format("%.1f", value)
                        editing = true
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CrossoverBandsGraph(
    cross1: Float,
    cross2: Float,
    cross3: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val minF = 20f
    val maxF = 20000f
    fun xOf(f: Float, w: Float): Float {
        val t = (ln(f.coerceIn(minF, maxF)) - ln(minF)) / (ln(maxF) - ln(minF))
        return t * w
    }

    Canvas(modifier = modifier.padding(8.dp)) {
        val w = size.width
        val h = size.height
        val cuts = listOf(minF, cross1, cross2, cross3, maxF)

        for (i in 0 until 4) {
            val x0 = xOf(cuts[i], w)
            val x1 = xOf(cuts[i + 1], w)
            drawRect(
                color = colors[i].copy(alpha = 0.35f),
                topLeft = Offset(x0, h * 0.25f),
                size = Size((x1 - x0).coerceAtLeast(1f), h * 0.5f)
            )
            val mid = (x0 + x1) / 2f
            val path = Path()
            path.moveTo(x0, h * 0.5f)
            path.quadraticBezierTo(mid, h * 0.15f, x1, h * 0.5f)
            drawPath(path, colors[i], style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
        listOf(cross1, cross2, cross3).forEach { c ->
            val x = xOf(c, w)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
        }
    }
}

// ============================================================
// LIMITER
// ============================================================
@Composable
private fun LimiterScreen(viewModel: EqViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Limiter", color = TXT_PRIMARY, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activado", color = TXT_MUTED, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = viewModel.limiterEnabled,
                    onCheckedChange = { viewModel.limiterEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LIM_PURPLE,
                        checkedTrackColor = LIM_PURPLE.copy(alpha = 0.45f),
                        uncheckedThumbColor = Color(0xFF555555),
                        uncheckedTrackColor = Color(0xFF333333)
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LimiterKnobCard(
                label = "THRESHOLD",
                value = viewModel.limiterThreshold,
                unit = "dB",
                range = -20f..0f,
                format = { String.format("%.1f", it) },
                accent = LIM_PURPLE,
                onValueChange = { viewModel.limiterThreshold = it },
                modifier = Modifier.weight(1f)
            )
            LimiterKnobCard(
                label = "RATIO",
                value = viewModel.limiterRatio,
                unit = "",
                range = 1f..50f,
                format = { String.format("%.1f", it) },
                accent = LIM_PURPLE,
                onValueChange = { viewModel.limiterRatio = it },
                modifier = Modifier.weight(1f)
            )
            LimiterKnobCard(
                label = "POST GAIN",
                value = viewModel.limiterPostGain,
                unit = "dB",
                range = -12f..12f,
                format = { String.format("%+.1f", it) },
                accent = LIM_PURPLE,
                onValueChange = { viewModel.limiterPostGain = it },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LimiterKnobCard(
                label = "ATTACK",
                value = viewModel.limiterAttack,
                unit = "ms",
                range = 0.5f..50f,
                format = { String.format("%.1f", it) },
                accent = LIM_PURPLE_DIM,
                onValueChange = { viewModel.limiterAttack = it },
                modifier = Modifier.weight(1f)
            )
            LimiterKnobCard(
                label = "RELEASE",
                value = viewModel.limiterRelease,
                unit = "ms",
                range = 20f..500f,
                format = { String.format("%.0f", it) },
                accent = LIM_PURPLE_DIM,
                onValueChange = { viewModel.limiterRelease = it },
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = LIM_PANEL),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("FILTROS ACTIVOS", color = LIM_PURPLE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LimiterSwitchRow("Pipeline", viewModel.pipelineEnabled) { viewModel.pipelineEnabled = it }
                LimiterSwitchRow("Low Shelf", viewModel.lowShelfEnabled) { viewModel.lowShelfEnabled = it }
                LimiterSwitchRow("Peak Bands", viewModel.peakBandsEnabled) { viewModel.peakBandsEnabled = it }
                LimiterSwitchRow("High Shelf", viewModel.highShelfEnabled) { viewModel.highShelfEnabled = it }
            }
        }
    }
}

@Composable
private fun LimiterKnobCard(
    label: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(format(value)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LIM_PANEL),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, color = TXT_MUTED, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LIM_KNOB_BG)
                    .border(2.dp, accent.copy(alpha = 0.7f), CircleShape)
                    .clickable {
                        text = format(value)
                        editing = true
                    },
                contentAlignment = Alignment.Center
            ) {
                if (editing) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.width(64.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accent
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        keyboardActions = KeyboardActions(onDone = {
                            text.toFloatOrNull()?.let {
                                onValueChange(it.coerceIn(range.start, range.endInclusive))
                            }
                            editing = false
                        })
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(format(value), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (unit.isNotEmpty()) {
                            Text(unit, color = accent, fontSize = 10.sp)
                        }
                    }
                }
            }

            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun LimiterSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LIM_PURPLE,
                checkedTrackColor = LIM_PURPLE.copy(alpha = 0.45f)
            )
        )
    }
}

// ============================================================
// REUTILIZABLES
// ============================================================
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = TXT_MUTED, fontSize = 13.sp, modifier = Modifier.width(70.dp))
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
                modifier = Modifier
                    .width(80.dp)
                    .clickable {
                        textValue = format(value)
                        isEditing = true
                    }
                    .padding(4.dp)
            )
        }
    }
}
