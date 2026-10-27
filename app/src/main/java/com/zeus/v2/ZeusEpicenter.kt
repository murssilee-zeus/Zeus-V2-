package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tanh
import kotlin.math.log10

/**
 * Zeus Bass low-frequency reconstruction stage - SEISMIC EDITION.
 *
 * Modified to aggressively reconstruct subharmonics, compress bass peaks,
 * and push physical acoustic pressure without melting the mid-bass.
 */
class ZeusEpicenter(
    private val sampleRate: Int
) {
    var enabled = true

    /** RMS del subgrave sintetizado en el último bloque PCM, en dBFS. */
    @Volatile var lastSubRmsDbfs: Float = -120f
        private set

    /** En audífonos, centra el subgrave sin reducir su nivel. */
    @Volatile
    var headphoneMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                subL.reset()
                subR.reset()
            }
        }

    private val lowL = Biquad(sampleRate)
    private val lowR = Biquad(sampleRate)
    private val punchL = Biquad(sampleRate)
    private val punchR = Biquad(sampleRate)

    private val h2L = Biquad(sampleRate)
    private val h2R = Biquad(sampleRate)
    private val h3L = Biquad(sampleRate)
    private val h3R = Biquad(sampleRate)

    private val bassBandL = Biquad(sampleRate)
    private val bassBandR = Biquad(sampleRate)
    
    private val resonatorL = Biquad(sampleRate)
    private val resonatorR = Biquad(sampleRate)
    private var resonatorMix = 0f
    private var dynamicBassAmount = 0f
    private var bassEnvelopeL = 0f
    private var bassEnvelopeR = 0f

    private val subL = SubHarmonic(sampleRate)
    private val subR = SubHarmonic(sampleRate)

    private var epicenterAmount = 0f
    private var exciterDrive = 1f
    private var exciterMix = 0f
    private var subMix = 0f

    fun configure(
        epicenterPercent: Float,
        drivePercent: Float,
        depthPercent: Float,
        harmonicsPercent: Float,
        frequencyHz: Float,
        sweepHz: Float
    ) {
        epicenterAmount = epicenterPercent.coerceIn(0f, 100f) / 100f
        val drive = drivePercent.coerceIn(0f, 100f) / 100f
        val depth = depthPercent.coerceIn(0f, 100f) / 100f
        val harmonics = harmonicsPercent.coerceIn(0f, 100f) / 100f
        
        // Mapeo sísmico clásico: Forzamos el rango objetivo a la zona más física (24Hz a 55Hz)
        val target = frequencyHz.coerceIn(18f, 55f)
        val sweep = sweepHz.coerceIn(50f, 120f)

        if (epicenterAmount <= 0f) {
            exciterDrive = 1f
            exciterMix = 0f
            subMix = 0f
            resonatorMix = 0f
            dynamicBassAmount = 0f
        } else {
            // CAMBIO 1: Agresividad de mezcla. Subimos los topes para inyectar más sub-bajo puro.
            exciterDrive = 1f + drive * 8f + epicenterAmount * 4.0f
            exciterMix = (epicenterAmount * (0.10f + drive * .25f) + harmonics * .15f).coerceAtMost(.45f)
            
            // Permitimos que el subMix llegue hasta 1.8f para duplicar la ganancia de la señal sintetizada si se desea
            subMix = (epicenterAmount * (.60f + depth * 1.20f)).coerceAtMost(1.8f)
            
            // El resonador ahora tiene más peso en el cuerpo del bajo analizado
            resonatorMix = (epicenterAmount * (.20f + depth * .40f)).coerceAtMost(0.65f)
            dynamicBassAmount = (epicenterAmount * depth * .50f).coerceAtMost(0.50f)
        }

        val center = target.coerceIn(25f, 80f)
        val subTarget = if (headphoneMode) target.coerceIn(18f, 55f) else target.coerceIn(18f, 50f)
        
        // CAMBIO 2: Mayor empuje de ecualización en bajas frecuencias (+6dB máx)
        lowL.lowShelf(subTarget, (epicenterAmount * depth * 6f).coerceAtMost(6f))
        lowR.lowShelf(subTarget, (epicenterAmount * depth * 6f).coerceAtMost(6f))
        
        // CAMBIO 3: Incrementamos la resonancia (Q) de .85f a 1.6f para un golpe (punch) hiper-enfocado
        punchL.peak(center, (epicenterAmount * drive * 5f).coerceAtMost(5f), 1.6f)
        punchR.peak(center, (epicenterAmount * drive * 5f).coerceAtMost(5f), 1.6f)

        // CAMBIO 4: Bajamos drásticamente el corte de H2 y H3. No queremos chirridos en los medios,
        // queremos engrosar el bajo sismico. El tope de H2 baja de 320Hz a 160Hz.
        val h2Gain = (epicenterAmount * drive * 3f + harmonics * 4.0f).coerceAtMost(8f)
        val h3Gain = (harmonics * 2.0f + epicenterAmount * drive * 1.5f).coerceAtMost(5f)
        
        h2L.peak((center * 2f).coerceIn(50f, 160f), h2Gain, 1.3f)
        h2R.peak((center * 2f).coerceIn(50f, 160f), h2Gain, 1.3f)
        h3L.peak((center * 3f).coerceIn(75f, 240f), h3Gain, 1.1f)
        h3R.peak((center * 3f).coerceIn(75f, 240f), h3Gain, 1.1f)

        // Filtros de análisis
        bassBandL.bandPass(sweep, 1.2f) // Q más cerrado para analizar la frecuencia de golpe exacta
        bassBandR.bandPass(sweep, 1.2f)
        resonatorL.bandPass(sweep, 1.0f + depth * 2.0f)
        resonatorR.bandPass(sweep, 1.0f + depth * 2.0f)

        subL.configure(subTarget)
        subR.configure(subTarget)
    }

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled || epicenterAmount <= 0f) { lastSubRmsDbfs = -120f; return }

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        var subEnergy = 0.0
        var subSamples = 0
        for (i in 0 until n step 2) {
            val inL = pcm[i] / 32768f
            val inR = pcm[i + 1] / 32768f

            var outL = lowL.process(inL)
            var outR = lowR.process(inR)

            outL = punchL.process(outL)
            outR = punchR.process(outR)

            // Bajo filtrado para el análisis del flip-flop.
            val bassL = bassBandL.process(inL)
            val bassR = bassBandR.process(inR)

            // 2. Cuerpo del resonador en paralelo.
            outL += resonatorL.process(inL) * resonatorMix
            outR += resonatorR.process(inR) * resonatorMix

            // 3. Generación sísmica directa: sin atenuación por bassEnvelope.
            val subInput = if (headphoneMode) (bassL + bassR) * 0.5f else 0f
            val subGeneratedL = subL.process(if (headphoneMode) subInput else bassL) * subMix * 3.5f
            val subGeneratedR = subR.process(if (headphoneMode) subInput else bassR) * subMix * 3.5f
            subEnergy += subGeneratedL.toDouble() * subGeneratedL + subGeneratedR.toDouble() * subGeneratedR
            subSamples += 2

            // 4. Soft-clipping independiente del subgrave antes de sumarlo.
            outL += tanh(subGeneratedL.toDouble()).toFloat()
            outR += tanh(subGeneratedR.toDouble()).toFloat()

            // 5. Excitador de armónicos secundarios para textura en medios-bajos.
            val shapedL = tanh((bassL * exciterDrive).toDouble()).toFloat()
            val shapedR = tanh((bassR * exciterDrive).toDouble()).toFloat()

            val excitedL = h2L.process(shapedL * shapedL) * exciterMix +
                h3L.process(shapedL * shapedL * shapedL) * (exciterMix * 0.5f)
            val excitedR = h2R.process(shapedR * shapedR) * exciterMix +
                h3R.process(shapedR * shapedR * shapedR) * (exciterMix * 0.5f)

            outL += excitedL
            outR += excitedR

            // 6. Limitación suave de la mezcla completa antes de la salida Short.
            pcm[i] = toShort(tanh(outL.toDouble()).toFloat())
            pcm[i + 1] = toShort(tanh(outR.toDouble()).toFloat())
        }
        lastSubRmsDbfs = if (subSamples > 0 && subEnergy > 0.0) (20.0 * log10(sqrt(subEnergy / subSamples))).toFloat().coerceIn(-120f, 0f) else -120f
    }

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled || epicenterAmount <= 0f) { lastSubRmsDbfs = -120f; return }

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        var subEnergy = 0.0
        var subSamples = 0
        for (i in 0 until n step 2) {
            val inL = pcm[i]
            val inR = pcm[i + 1]

            var outL = lowL.process(inL)
            var outR = lowR.process(inR)

            outL = punchL.process(outL)
            outR = punchR.process(outR)

            // Bajo filtrado para el análisis del flip-flop.
            val bassL = bassBandL.process(inL)
            val bassR = bassBandR.process(inR)

            // Cuerpo del resonador en paralelo.
            outL += resonatorL.process(inL) * resonatorMix
            outR += resonatorR.process(inR) * resonatorMix

            // Generación sísmica directa, sin atenuación por bassEnvelope.
            val subInput = if (headphoneMode) (bassL + bassR) * 0.5f else 0f
            val subGeneratedL = subL.process(if (headphoneMode) subInput else bassL) * subMix * 3.5f
            val subGeneratedR = subR.process(if (headphoneMode) subInput else bassR) * subMix * 3.5f
            subEnergy += subGeneratedL.toDouble() * subGeneratedL + subGeneratedR.toDouble() * subGeneratedR
            subSamples += 2

            // Soft-clipping independiente del subgrave antes de sumarlo.
            outL += tanh(subGeneratedL.toDouble()).toFloat()
            outR += tanh(subGeneratedR.toDouble()).toFloat()

            // Excitador de armónicos secundarios.
            val shapedL = tanh((bassL * exciterDrive).toDouble()).toFloat()
            val shapedR = tanh((bassR * exciterDrive).toDouble()).toFloat()

            val excitedL = h2L.process(shapedL * shapedL) * exciterMix +
                h3L.process(shapedL * shapedL * shapedL) * (exciterMix * 0.5f)
            val excitedR = h2R.process(shapedR * shapedR) * exciterMix +
                h3R.process(shapedR * shapedR * shapedR) * (exciterMix * 0.5f)

            outL += excitedL
            outR += excitedR

            // Limitación suave de la mezcla completa antes de la salida Float32.
            pcm[i] = tanh(outL.toDouble()).toFloat()
            pcm[i + 1] = tanh(outR.toDouble()).toFloat()
        }
        lastSubRmsDbfs = if (subSamples > 0 && subEnergy > 0.0) (20.0 * log10(sqrt(subEnergy / subSamples))).toFloat().coerceIn(-120f, 0f) else -120f
    }

    /** Limpia estados internos al detener o reiniciar la ruta PCM. */
    fun reset() {
        lowL.reset()
        lowR.reset()
        punchL.reset()
        punchR.reset()
        h2L.reset()
        h2R.reset()
        h3L.reset()
        h3R.reset()
        bassBandL.reset()
        bassBandR.reset()
        resonatorL.reset()
        resonatorR.reset()
        subL.reset()
        subR.reset()
        lastSubRmsDbfs = -120f
        bassEnvelopeL = 0f
        bassEnvelopeR = 0f
    }

    private fun toShort(sample: Float): Short {
        val s = sample * 32768f
        return when {
            s > 32767f -> 32767
            s < -32768f -> -32768
            else -> s.toInt().toShort()
        }
    }
}
