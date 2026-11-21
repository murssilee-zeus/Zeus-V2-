package com.zeus.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val ZEUS_DSP_SAMPLE = """# Zeus DSP Lab: parametros permitidos
# Valores seguros para comenzar; ajusta y compara.
set epicenter.amount = 6
set epicenter.drive = 25
set epicenter.depth = 30
set epicenter.harmonics = 15
set epicenter.frequency = 45
set epicenter.sweep = 80
set bass.amount = 35
set bass.frequency = 45
set bass.harmonics = 15
"""

@Composable
fun ZeusDspScriptLabDialog(punch: PunchViewModel, onDismiss: () -> Unit) {
    var script by remember { mutableStateOf(ZEUS_DSP_SAMPLE) }
    var feedback by remember { mutableStateOf("Listo. Revisa el ejemplo y pulsa Aplicar.") }
    var applied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ZEUS DSP LAB", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                Text("Editor seguro de parametros en vivo", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "El lenguaje solo permite cambiar parametros autorizados. No ejecuta Kotlin, Lua ni codigo arbitrario.",
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it; applied = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 330.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    minLines = 9,
                    maxLines = 16,
                    label = { Text("Script Zeus") }
                )
                Text("MONITOR DSP", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeterValue("EPICENTER", punch.meterEpicenterRmsDbfs, Modifier.weight(1f))
                    MeterValue("SUB / SISMO", punch.meterSubRmsDbfs, Modifier.weight(1f))
                }
                MeterValue("MASTER RMS", punch.meterMasterRmsDbfs, Modifier.fillMaxWidth())
                Text(
                    feedback,
                    color = if (applied) androidx.compose.ui.graphics.Color(0xFF24E58A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = ZeusDspScriptEngine.apply(script, punch)
                applied = result.success
                feedback = if (result.success) {
                    "Aplicado: " + result.applied.joinToString(" · ")
                } else {
                    result.error ?: "No se pudo aplicar el script."
                }
            }) { Text("APLICAR") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { script = ZEUS_DSP_SAMPLE; feedback = "Ejemplo restaurado."; applied = false }) {
                    Text("EJEMPLO")
                }
                TextButton(onClick = onDismiss) { Text("CERRAR") }
            }
        }
    )
}

@Composable
private fun MeterValue(label: String, dbfs: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (dbfs <= -119f) "−∞ dBFS" else "%.1f dBFS".format(dbfs),
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
