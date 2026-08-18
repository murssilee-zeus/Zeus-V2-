package com.zeus.v2

import androidx.compose.ui.graphics.Color

data class EqBand(
    val id: Int,
    var frequency: Float = 1000f,
    var gain: Float = 0f,
    var q: Float = 1.0f,
    var enabled: Boolean = true,
    var filterType: FilterType = FilterType.PEAK,
    val color: Color
) {
    enum class FilterType {
        LOW_SHELF, HIGH_SHELF, PEAK, LOW_PASS, HIGH_PASS, NOTCH, BAND_PASS, BYPASS
    }

    fun copyValuesFrom(other: EqBand) {
        frequency = other.frequency.coerceIn(1f, 30000f)
        gain = other.gain.coerceIn(-30f, 30f)
        q = other.q.coerceIn(0.1f, 40f)
        enabled = other.enabled
        filterType = other.filterType
    }
}

private val BAND_COLORS = listOf(
    Color(0xFFE0566B), Color(0xFFFF9F43), Color(0xFFFFEAA7), Color(0xFF55EFC4),
    Color(0xFF74B9FF), Color(0xFFA29BFE), Color(0xFFFF6B9E), Color(0xFF00CEC9),
    Color(0xFFE17055), Color(0xFF6C5CE7), Color(0xFFFF7675), Color(0xFFFDCB6E),
    Color(0xFF00B894), Color(0xFF0984E3), Color(0xFFC160FF), Color(0xFFE84393),
    Color(0xFF2D3436), Color(0xFFD63031)
)

/**
 * 16 bandas por defecto alineadas con la imagen de referencia.
 * Subgrave profundo: las primeras bandas con empuje positivo.
 */
fun createDefaultBands(): List<EqBand> {
    val defaults = listOf(
        Triple(31f, 5.0f, 0.7f),   // SUB (LSHELF)
        Triple(62f, 4.0f, 1.0f),
        Triple(125f, 2.5f, 1.2f),
        Triple(250f, 1.0f, 1.0f),
        Triple(500f, 0f, 1.0f),
        Triple(1000f, 0f, 1.0f),
        Triple(2000f, 0f, 1.2f),
        Triple(4000f, 0f, 1.5f),
        Triple(8000f, 0f, 1.0f),
        Triple(16000f, -1.0f, 0.8f)  // HSHELF
    )

    val initial = ArrayList<EqBand>(16)
    for (i in 0 until 16) {
        val (freq, gain, q) = if (i < defaults.size) {
            Triple(defaults[i].first, defaults[i].second, defaults[i].third)
        } else {
            Triple(1000f + (i - 10) * 1500f, 0f, 1.0f)
        }
        initial += EqBand(
            id = i,
            frequency = freq,
            gain = gain,
            q = q,
            enabled = true,
            filterType = when (i) {
                0 -> EqBand.FilterType.LOW_SHELF
                15 -> EqBand.FilterType.HIGH_SHELF
                else -> EqBand.FilterType.PEAK
            },
            color = BAND_COLORS[i % BAND_COLORS.size]
        )
    }
    return initial
}

fun createNewBand(id: Int, frequency: Float = 1000f): EqBand {
    return EqBand(
        id = id,
        frequency = frequency.coerceIn(1f, 30000f),
        gain = 0f,
        q = 1.0f,
        enabled = true,
        filterType = EqBand.FilterType.PEAK,
        color = BAND_COLORS[id % BAND_COLORS.size]
    )
}
