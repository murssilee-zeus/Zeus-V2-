package com.zeus.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

private val ZBG = Color(0xFF08090D)
private val ZCARD = Color(0xFF111218)
private val ZBORDER = Color(0xFF30303A)
private val ZPINK = Color(0xFFE55BFF)
private val ZPURPLE = Color(0xFFB56BFF)
private val ZTEXT = Color(0xFFF0EFF4)
private val ZMUTED = Color(0xFF92919C)

@Composable
fun ZeusStudioScreen(
    viewModel: EqViewModel,
    punchViewModel: PunchViewModel,
    onToggleEngine: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(ZBG).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("‹   Equalizer", color = ZTEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C4936)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) { Text("Guardar") }
                Button(
                    onClick = onToggleEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C978)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(if (viewModel.isEngineRunning) "⏻" else "▶") }
            }
        }

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            EqStudioPanel(viewModel, Modifier.width(520.dp).fillMaxHeight())
            PunchLimiterPanel(viewModel, punchViewModel, Modifier.width(245.dp).fillMaxHeight())
            CompressorPipelinePanel(viewModel, Modifier.width(320.dp).fillMaxHeight())
        }

        StudioBandStrip(viewModel)
    }
}

@Composable
private fun EqStudioPanel(viewModel: EqViewModel, modifier: Modifier) {
    val band = viewModel.selectedBand()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardPanel(Modifier.weight(1f)) {
            EqGraph(
                bands = viewModel.bands,
                selectedIndex = viewModel.selectedBandIndex,
                spectrum = viewModel.spectrum,
                onBandSelected = viewModel::selectBand,
                onBandMoved = { idx, freq, gain ->
                    viewModel.selectBand(idx)
                    viewModel.updateSelectedBand(frequency = freq, gain = gain)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("PEAK", "LOW SHELF", "HIGH SHELF", "LPF", "HPF", "BYPASS").forEachIndexed { i, name ->
                val active = when (i) {
                    0 -> band?.filterType == EqBand.FilterType.PEAK
                    1 -> band?.filterType == EqBand.FilterType.LOW_SHELF
                    2 -> band?.filterType == EqBand.FilterType.HIGH_SHELF
                    3 -> band?.filterType == EqBand.FilterType.LOW_PASS
                    4 -> band?.filterType == EqBand.FilterType.HIGH_PASS
                    else -> band?.filterType == EqBand.FilterType.BYPASS
                }
                SmallButton(name, active) {
                    val type = when (i) {
                        0 -> EqBand.FilterType.PEAK
                        1 -> EqBand.FilterType.LOW_SHELF
                        2 -> EqBand.FilterType.HIGH_SHELF
                        3 -> EqBand.FilterType.LOW_PASS
                        4 -> EqBand.FilterType.HIGH_PASS
                        else -> EqBand.FilterType.BYPASS
                    }
                    viewModel.updateSelectedBand(filterType = type)
                }
            }
        }
        CardPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EditableStudio("FREQ", band?.frequency ?: 50f, "Hz", 1f..30000f) { viewModel.updateSelectedBand(frequency = it) }
                EditableStudio("PREAMP", viewModel.preamp, "dB", -30f..12f) { viewModel.preamp = it }
                EditableStudio("Q", band?.q ?: 1f, "", 0.1f..40f) { viewModel.updateSelectedBand(q = it) }
                EditableStudio("GAIN", band?.gain ?: 0f, "dB", -30f..30f) { viewModel.updateSelectedBand(gain = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Flat", "Infrabass", "Bass Boost", "Vocal").forEach { name ->
                SmallButton(name, name == "Infrabass") {
                    when (name) {
                        "Flat" -> viewModel.applyPresetFlat()
                        "Infrabass" -> viewModel.applyPresetZeusInfrabass()
                        "Bass Boost" -> viewModel.applyPresetBassBoost()
                        "Vocal" -> viewModel.applyPresetVocalClear()
                    }
                }
            }
        }
    }
}

@Composable
private fun PunchLimiterPanel(viewModel: EqViewModel, punch: PunchViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardPanel {
            Text("PUNCH (35Hz – 65Hz)", color = ZPURPLE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("post-MBC · 18 Hz stays independent", color = ZMUTED, fontSize = 10.sp)
            Spacer(Modifier.height(3.dp))
            SliderValue("AMOUNT", punch.amount, 0f..100f, "%") { punch.updatePunchAmount(it) }
            SliderValue("CENTER", PunchControl.punchCenter(punch.amount), 35f..65f, "Hz") { }
            SliderValue("Q", PunchControl.punchQ(punch.amount), 1f..1.5f, "") { }
            Text("Headroom protected · controlled low-end impact", color = ZMUTED, fontSize = 9.sp)
        }
        CardPanel(Modifier.weight(1f)) {
            Text("LIMITER", color = ZTEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            SliderValue("THRESHOLD", viewModel.limiterThreshold, -20f..0f, "dB") { viewModel.limiterThreshold = it }
            SliderValue("CEILING", -0.5f, -6f..0f, "dB") { }
            SliderValue("ATTACK", viewModel.limiterAttack, 0.5f..50f, "ms") { viewModel.limiterAttack = it }
            SliderValue("RELEASE", viewModel.limiterRelease, 20f..500f, "ms") { viewModel.limiterRelease = it }
            SliderValue("RATIO", viewModel.limiterRatio, 1f..50f, ":1") { viewModel.limiterRatio = it }
            SliderValue("POST", viewModel.limiterPostGain, -12f..12f, "dB") { viewModel.limiterPostGain = it }
            Spacer(Modifier.height(4.dp))
            Text("FINAL PEAK PROTECTION", color = ZPURPLE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompressorPipelinePanel(viewModel: EqViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardPanel(Modifier.weight(1f)) {
            Text("COMPRESSOR / MULTIBAND", color = ZTEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            SliderValue("THRESHOLD", viewModel.compMbThLow, -40f..0f, "dB") { viewModel.compMbThLow = it }
            SliderValue("RATIO", viewModel.compMbRatioLow, 1f..20f, ":1") { viewModel.compMbRatioLow = it }
            SliderValue("ATTACK", viewModel.compMbAttackLow, 1f..200f, "ms") { viewModel.compMbAttackLow = it }
            SliderValue("RELEASE", viewModel.compMbReleaseLow, 10f..500f, "ms") { viewModel.compMbReleaseLow = it }
            SliderValue("MAKEUP", viewModel.compMbPostGainLow, -12f..12f, "dB") { viewModel.compMbPostGainLow = it }
            Spacer(Modifier.height(4.dp))
            Text("LOW  •  LO-MID  •  HI-MID  •  HIGH", color = ZMUTED, fontSize = 9.sp)
            Text("Crossovers: ${viewModel.crossoverFrequencies.joinToString(" / ") { "%.0f".format(it) }} Hz", color = ZPINK, fontSize = 10.sp)
        }
        CardPanel {
            Text("AUDIO EFFECTS PIPELINE", color = ZPURPLE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Procesamiento multietapa", color = ZMUTED, fontSize = 10.sp)
            PipelineStep("1", "MBC", "Control dinámico por bandas")
            PipelineStep("2", "PUNCH 35–65 Hz", "Realce controlado de subgraves")
            PipelineStep("3", "LIMITER", "Protección final de picos")
            Text("DIRECT OUTPUT  ·  ${if (viewModel.pipelineEnabled) "ACTIVO" else "BYPASS"}", color = if (viewModel.pipelineEnabled) Color(0xFF35D98B) else ZMUTED, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PipelineStep(number: String, title: String, subtitle: String) {
    CardPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = ZPURPLE, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Column {
                Text(title, color = ZTEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ZMUTED, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StudioBandStrip(viewModel: EqViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        viewModel.bands.forEachIndexed { index, band ->
            val selected = index == viewModel.selectedBandIndex
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) band.color.copy(alpha = 0.9f) else ZCARD),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.width(68.dp).clickable { viewModel.selectBand(index) }
            ) {
                Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${index + 1}", color = if (selected) Color.Black else ZTEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (band.frequency >= 1000f) "%.1fk".format(band.frequency / 1000f) else "%.0f".format(band.frequency), color = if (selected) Color.Black else ZMUTED, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun CardPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().background(ZCARD, RoundedCornerShape(10.dp)).border(1.dp, ZBORDER, RoundedCornerShape(10.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content
    )
}

@Composable
private fun SmallButton(text: String, active: Boolean, onClick: () -> Unit) {
    Text(text, color = if (active) Color.Black else ZTEXT, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(if (active) ZPINK else ZCARD, RoundedCornerShape(7.dp)).border(1.dp, if (active) ZPINK else ZBORDER, RoundedCornerShape(7.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 7.dp))
}

@Composable
private fun RowScope.EditableStudio(label: String, value: Float, unit: String, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, color = ZMUTED, fontSize = 9.sp)
        Text("${if (value >= 1000f) "%.1fk".format(value / 1000f) else "%.2f".format(value)} $unit", color = ZPINK, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, colors = SliderDefaults.colors(thumbColor = ZPINK, activeTrackColor = ZPINK))
    }
}

@Composable
private fun SliderValue(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ZMUTED, fontSize = 9.sp)
            Text("${"%.1f".format(value)} $unit", color = ZTEXT, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, colors = SliderDefaults.colors(thumbColor = ZPURPLE, activeTrackColor = ZPURPLE, inactiveTrackColor = ZPURPLE.copy(alpha = 0.2f)))
    }
}
