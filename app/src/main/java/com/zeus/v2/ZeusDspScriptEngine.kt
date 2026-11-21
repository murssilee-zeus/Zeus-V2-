package com.zeus.v2

/**
 * Safe, deliberately small Zeus DSP parameter language.
 *
 * This is not Kotlin/Lua execution: scripts can only set whitelisted parameters.
 * Parsing and validation happen on the UI thread; the existing Compose state
 * observers publish the resulting parameter snapshot to AudioEngine.
 */
object ZeusDspScriptEngine {
    data class Result(
        val success: Boolean,
        val applied: List<String> = emptyList(),
        val error: String? = null
    )

    private data class Change(val key: String, val value: Float)

    private val allowed = mapOf(
        "epicenter.amount" to (0f..12f),
        "epicenter.drive" to (0f..100f),
        "epicenter.depth" to (0f..100f),
        "epicenter.harmonics" to (0f..100f),
        "epicenter.frequency" to (18f..65f),
        "epicenter.sweep" to (40f..140f),
        "bass.amount" to (0f..100f),
        "bass.frequency" to (25f..120f),
        "bass.harmonics" to (0f..100f)
    )

    fun apply(script: String, punch: PunchViewModel): Result {
        if (script.length > 4096) return Result(false, error = "El script supera 4096 caracteres.")
        val lines = script.lineSequence().toList()
        if (lines.size > 40) return Result(false, error = "Máximo 40 líneas por script.")

        val changes = mutableListOf<Change>()
        for ((index, raw) in lines.withIndex()) {
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty()) continue
            val match = Regex("^set\\s+([a-zA-Z.]+)\\s*=\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$").matchEntire(line)
                ?: return Result(false, error = "Línea ${index + 1}: usa «set modulo.parametro = valor».")
            val key = match.groupValues[1].lowercase()
            val range = allowed[key] ?: return Result(false, error = "Línea ${index + 1}: parámetro no permitido: $key")
            val value = match.groupValues[2].toFloatOrNull()
                ?: return Result(false, error = "Línea ${index + 1}: valor numérico inválido.")
            if (!value.isFinite() || value !in range) {
                return Result(false, error = "Línea ${index + 1}: $key debe estar entre ${range.start} y ${range.endInclusive}.")
            }
            changes += Change(key, value)
        }
        if (changes.isEmpty()) return Result(false, error = "No hay instrucciones para aplicar.")

        // Apply only after every line has passed validation: no partial updates.
        changes.forEach { change ->
            when (change.key) {
                "epicenter.amount" -> punch.updateEpicenterAmount(change.value)
                "epicenter.drive" -> punch.updateEpicenterDrive(change.value)
                "epicenter.depth" -> punch.updateEpicenterDepth(change.value)
                "epicenter.harmonics" -> punch.updateEpicenterHarmonics(change.value)
                "epicenter.frequency" -> punch.updateEpicenterFrequency(change.value)
                "epicenter.sweep" -> punch.updateEpicenterSweep(change.value)
                "bass.amount" -> punch.updateBassAmount(change.value)
                "bass.frequency" -> punch.updateBassFrequency(change.value)
                "bass.harmonics" -> punch.updateBassHarmonics(change.value)
            }
        }
        return Result(true, applied = changes.map { "${it.key} = ${it.value}" })
    }
}
