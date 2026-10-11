package com.zeus.v2

import kotlin.math.sin
import kotlin.math.PI

/**
 * Generador digital de sub-armónicas por división de octava (Frecuencia / 2).
 * Toma el bajo analizado y genera una señal una octava inferior balanceada por energía.
 */
class SubHarmonic(private val sampleRate: Int) {
    private var phase = 0f
    private var lastSample = 0f
    private var subTargetFreq = 30f
    
    // Filtro pasa-bajas elíptico o biquad integrado para limpiar la sub-armónica
    private val lpFilter = Biquad(sampleRate)

    fun configure(targetFrequencyHz: Float) {
        // Ajustamos el corte del filtro para que barra solo la zona sísmica pura
        this.subTargetFreq = targetFrequencyHz.coerceIn(20f, 50f)
        // El filtro pasa-bajas limpia cualquier residuo agudo o distorsión de la onda cuadrada generada
        lpFilter.lowPass(subTargetFreq, 1.2f) 
    }

    fun process(bassSample: Float): Float {
        // 1. Detección de cruce por cero o cambio de polaridad
        // Esto replica el comportamiento de los circuitos integrados de flip-flop analógicos
        if ((bassSample > 0f && lastSample <= 0f) || (bassSample < 0f && lastSample >= 0f)) {
            // Avanzamos la fase de la sub-armónica a la mitad de la velocidad original
            phase += PI.toFloat()
            if (phase >= 2f * PI.toFloat()) {
                phase -= 2f * PI.toFloat()
            }
        }
        lastSample = bassSample

        // 2. Síntesis de la nueva onda fundamental (Sub-octava)
        // Multiplicamos el seno de nuestra fase ralentizada por la amplitud absoluta del bajo original
        // Esto asegura que la sub-armónica mantenga la misma dinámica y decaimiento que la música
        val rawSub = sin(phase.toDouble()).toFloat() * kotlin.math.abs(bassSample)

        // 3. Purificación de la señal
        // Pasamos la señal sintetizada por el filtro pasa-bajas para transformarla en vibración pura
        return lpFilter.process(rawSub)
    }
}
