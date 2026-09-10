package com.zeus.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PunchControlPanel(
    viewModel: PunchViewModel,
    modifier: Modifier = Modifier
) {
    var text by remember(viewModel.amount) { mutableStateOf("%.0f".format(viewModel.amount)) }
    var bassText by remember(viewModel.bassAmount) { mutableStateOf("%.0f".format(viewModel.bassAmount)) }
    var bassFreqText by remember(viewModel.bassFrequencyHz) { mutableStateOf("%.0f".format(viewModel.bassFrequencyHz)) }
    var harmonicText by remember(viewModel.bassHarmonics) { mutableStateOf("%.0f".format(viewModel.bassHarmonics)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF15131A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB56BFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BASS", color = Color(0xFFB56BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("Audio Framework · real EQ path", color = Color(0xFF888892), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(if (viewModel.bassMono) "MONO" else "STEREO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        BassSliderRow("AMOUNT", viewModel.bassAmount, bassText, 0f..100f, "%", {
            viewModel.updateBassAmount(it)
            bassText = "%.0f".format(it)
        }, { raw ->
            bassText = raw.filter { it.isDigit() }.take(3)
            bassText.toFloatOrNull()?.let(viewModel::updateBassAmount)
        })

        BassSliderRow("FREQ", viewModel.bassFrequencyHz, bassFreqText, 25f..120f, "Hz", {
            viewModel.updateBassFrequency(it)
            bassFreqText = "%.0f".format(it)
        }, { raw ->
            bassFreqText = raw.filter { it.isDigit() }.take(3)
            bassFreqText.toFloatOrNull()?.let(viewModel::updateBassFrequency)
        })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BASS MONO", color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.bassMono,
                onCheckedChange = viewModel::updateBassMono,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFB56BFF),
                    checkedTrackColor = Color(0xFFB56BFF).copy(alpha = .45f)
                )
            )
        }

        BassSliderRow("HARMONICS", viewModel.bassHarmonics, harmonicText, 0f..100f, "%", {
            viewModel.updateBassHarmonics(it)
            harmonicText = "%.0f".format(it)
        }, { raw ->
            harmonicText = raw.filter { it.isDigit() }.take(3)
            harmonicText.toFloatOrNull()?.let(viewModel::updateBassHarmonics)
        })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PUNCH", color = Color(0xFFB56BFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("35–65 Hz · post-MBC", color = Color(0xFF888892), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("%.0f%%".format(viewModel.amount), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = viewModel.amount,
                onValueChange = viewModel::updatePunchAmount,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color(0xFFB56BFF), activeTrackColor = Color(0xFFB56BFF))
            )
            Spacer(Modifier.width(5.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.filter { it.isDigit() }.take(3)
                    text.toFloatOrNull()?.let(viewModel::updatePunchAmount)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("%", fontSize = 10.sp) },
                modifier = Modifier.width(60.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White)
            )
        }
    }
}

@Composable
private fun BassSliderRow(
    label: String,
    value: Float,
    text: String,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onChange: (Float) -> Unit,
    onText: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.width(70.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color(0xFFB56BFF), activeTrackColor = Color(0xFFB56BFF))
        )
        Spacer(Modifier.width(5.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text(suffix, fontSize = 9.sp) },
            modifier = Modifier.width(65.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White)
        )
    }
}
