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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF15131A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFB56BFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PUNCH", color = Color(0xFFB56BFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("35–65 Hz · post-MBC", color = Color(0xFF888892), fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("%.0f%%".format(viewModel.amount), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = viewModel.amount,
                onValueChange = {
                    viewModel.setAmount(it)
                    text = "%.0f".format(it)
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFB56BFF),
                    activeTrackColor = Color(0xFFB56BFF)
                )
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.filter { it.isDigit() }.take(3)
                    text.toFloatOrNull()?.let { viewModel.setAmount(it) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("%", fontSize = 11.sp) },
                modifier = Modifier.width(64.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
            )
        }
    }
}
