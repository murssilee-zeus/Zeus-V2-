package com.zeus.v2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zeus Epicenter v3.1.2
 *
 * - Mono low-frequency detector and a period tracker.
 * - PLL-anchored sub-octave oscillator (input pitch / 2).
 * - Independent mono SUB and harmonic paths.
 * - No generated tone without valid low-frequency tracking.
 * - Clean tracking-parity reset after tracking timeout.
 * - Original PCM is preserved; only the generated delta is added.
 *
 * This class keeps the public API used by AudioEngine and ZeusBassDspPipeline.
 */
class ZeusEpicenter(private val sampleRate: Int) {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var lastSubRmsDbfs: Float = -120f
        private set

    /** Percentage of processed frames where the safety headroom reduced the generated SUB. */
    @Volatile
    var lastClampPercent: Float = 0f
        private set

    @Volatile
    var headphoneMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateGains()
                reset()
            }
        }

    // -------------------------------------------------
    // FILTERS
    // -------------------------------------------------

    private val trackLp1 = Biquad(sampleRate)
    private val trackLp2 = Biquad(sampleRate)
    private val trackHp = Biquad(sampleRate)

    private val subLp1 = Biquad(sampleRate)
    private val subLp2 = Biquad(sampleRate)
    private val harmLp = Biquad(sampleRate)

    // -------------------------------------------------
    // PARAMETERS
    // frequencyHz and sweepHz are GENERATED SUB frequencies.
    // The detector therefore tracks the corresponding input octave.
    // -------------------------------------------------

    private var amount = 0f
    private var drive = 0f
    private var depth = 0f
    private var harmonics = 0f

    private var targetHz = 36f
    private var sweepHz = 80f
    private var inputMinHz = 72f
    private var inputMaxHz = 160f

    private var detectorThreshold = 0.008f

    // -------------------------------------------------
    // TRACKING
    // -------------------------------------------------

    private var crossingArmed = false
    private var samplesSinceCross = 0

    private var trackedHz = 72f
    private var trackingValidity = 0
    private var crossingParity = false

    private var trackTimeout = 0
    private var minPeriod = 0
    private var maxPeriod = 0

    // -------------------------------------------------
    // PLL
    // -------------------------------------------------

    private var phase = 0.0
    private val phaseLock = 0.25
    private val dcBlockerPole = 0.995f
    private val twoPi = 2.0 * PI

    // -------------------------------------------------
    // ENVELOPES
    // -------------------------------------------------

    private var env = 0f
    private var subEnv = 0f
    private var holdSamples = 0

    private var attackCoeff = 0f
    private var envReleaseCoeff = 0f
    private var subReleaseCoeff = 0f
    private var holdLength = 0

    // -------------------------------------------------
    // GAINS
    // -------------------------------------------------

    private var subGain = 0f
    private var harmGain = 0f

    // -------------------------------------------------
    // DC BLOCKER
    // -------------------------------------------------

    private var dcX1 = 0f
    private var dcY1 = 0f

    // -------------------------------------------------
    // BLOCK METERS
    // -------------------------------------------------

    private var blockEnergy = 0.0
    private var blockCount = 0
    private var clampCount = 0

    // -------------------------------------------------
    // CONFIGURATION
    // -------------------------------------------------

    fun configure(
        epicenterPercent: Float,
        drivePercent: Float,
        depthPercent: Float,
        harmonicsPercent: Float,
        frequencyHz: Float,
        sweepHz: Float
    ) {
        amount = finiteOr(epicenterPercent, 0f).coerceIn(0f, 100f) / 100f
        drive = finiteOr(drivePercent, 0f).coerceIn(0f, 100f) / 100f
        depth = finiteOr(depthPercent, 0f).coerceIn(0f, 100f) / 100f
        harmonics = finiteOr(harmonicsPercent, 0f).coerceIn(0f, 100f) / 100f

        // These controls describe the generated SUB range, not the input pitch.
        targetHz = finiteOr(frequencyHz, 36f).coerceIn(18f, 65f)
        this.sweepHz = finiteOr(sweepHz, 80f).coerceIn(targetHz + 2f, 140f)

        val sr = safeSampleRate().toDouble()

        // Drive changes detector sensitivity only, never output gain.
        detectorThreshold = 0.020f - drive * 0.019f

        // The source pitch is one octave above the generated SUB.
        inputMinHz = targetHz * 2f
        inputMaxHz = this.sweepHz * 2f

        minPeriod = (sr / inputMaxHz).toInt().coerceAtLeast(1)
        maxPeriod = (sr / inputMinHz).toInt().coerceAtLeast(minPeriod + 1)

        trackTimeout = (sr * 0.090).toInt().coerceAtLeast(1)

        attackCoeff = (1.0 - exp(-1.0 / (sr * 0.006))).toFloat()
        envReleaseCoeff = (1.0 - exp(-1.0 / (sr * 0.045))).toFloat()
        subReleaseCoeff = (1.0 - exp(
            -1.0 / (sr * (0.090 + depth * 0.160))
        )).toFloat()

        holdLength = (sr * (0.050 + depth * 0.050)).toInt()

        // Dynamic tracking cutoff keeps the full selected input-octave range visible.
        val trackCutoff = (inputMaxHz * 1.25f)
            .coerceIn(150f, minOf(500f, sampleRate.coerceAtLeast(8000) * 0.35f))

        trackLp1.lowPass(trackCutoff, 0.707f)
        trackLp2.lowPass(trackCutoff, 0.707f)
        trackHp.highPass(12f, 0.707f)

        // Keep the generated fundamental inside the selected SUB region.
        val subCutoff = (this.sweepHz * 1.50f).coerceIn(90f, 220f)
            .coerceAtMost(sampleRate.coerceAtLeast(8000) * 0.35f)
        subLp1.lowPass(subCutoff, 0.707f)
        subLp2.lowPass(subCutoff, 0.707f)

        harmLp.lowPass(190f, 0.707f)

        updateGains()
    }

    private fun updateGains() {
        val amountCurve = amount.toDouble().pow(0.70).toFloat()
        subGain = amountCurve * if (headphoneMode) 0.72f else 1.0f
        harmGain = amountCurve * harmonics * 0.22f
    }

    // -------------------------------------------------
    // FLOAT PCM
    // -------------------------------------------------

    fun processStereo(pcm: FloatArray, size: Int = pcm.size) {
        if (!enabled || amount <= 0f) {
            lastSubRmsDbfs = -120f
            lastClampPercent = 0f
            return
        }

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (n < 2) return

        blockEnergy = 0.0
        blockCount = 0
        clampCount = 0

        for (i in 0 until n step 2) {
            val packed = processFrame(pcm[i], pcm[i + 1])
            pcm[i] = java.lang.Float.intBitsToFloat((packed shr 32).toInt())
            pcm[i + 1] = java.lang.Float.intBitsToFloat(packed.toInt())
        }

        finishBlock()
    }

    // -------------------------------------------------
    // SHORT PCM
    // -------------------------------------------------

    fun processStereo(pcm: ShortArray, size: Int = pcm.size) {
        if (!enabled || amount <= 0f) {
            lastSubRmsDbfs = -120f
            lastClampPercent = 0f
            return
        }

        val n = size.coerceIn(0, pcm.size - pcm.size % 2)
        if (n < 2) return

        blockEnergy = 0.0
        blockCount = 0
        clampCount = 0

        for (i in 0 until n step 2) {
            val left = pcm[i] / 32768f
            val right = pcm[i + 1] / 32768f
            val packed = processFrame(left, right)

            val outL = java.lang.Float.intBitsToFloat((packed shr 32).toInt())
            val outR = java.lang.Float.intBitsToFloat(packed.toInt())

            pcm[i] = (outL.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort()
            pcm[i + 1] = (outR.coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort()
        }

        finishBlock()
    }

    // -------------------------------------------------
    // ONE STEREO FRAME
    // -------------------------------------------------

    private fun processFrame(leftIn: Float, rightIn: Float): Long {
        val sr = safeSampleRate().toDouble()

        val left = finiteOr(leftIn, 0f).coerceIn(-1f, 1f)
        val right = finiteOr(rightIn, 0f).coerceIn(-1f, 1f)
        val mono = (left + right) * 0.5f

        // 1. Mono low-frequency detector.
        val tracking = trackHp.process(
            trackLp2.process(
                trackLp1.process(mono)
            )
        )

        // Hysteresis: arm below zero, accept only the next positive crossing.
        if (tracking <= -0.0015f) {
            crossingArmed = true
        }

        if (crossingArmed && tracking >= 0.0015f) {
            val measuredPeriod = samplesSinceCross

            if (measuredPeriod in minPeriod..maxPeriod) {
                val measuredHz = (sr / measuredPeriod).toFloat()

                if (measuredHz in inputMinHz..inputMaxHz) {
                    trackedHz += 0.35f * (measuredHz - trackedHz)
                    trackingValidity = trackTimeout

                    // Every full input cycle advances the f/2 oscillator by half a turn.
                    crossingParity = !crossingParity
                    val targetPhase = if (crossingParity) PI else 0.0

                    var phaseError = (targetPhase - phase) % twoPi
                    if (phaseError > PI) phaseError -= twoPi
                    if (phaseError < -PI) phaseError += twoPi

                    phase += phaseLock * phaseError
                }
            }

            samplesSinceCross = 0
            crossingArmed = false
        } else if (samplesSinceCross < maxPeriod + trackTimeout) {
            samplesSinceCross++
        }

        if (trackingValidity > 0) {
            trackingValidity--
        } else {
            // Approved re-entry fix: stale parity must not survive lost pitch lock.
            crossingParity = false
        }

        // 2. Dynamic envelope with a real zero target after tracking expires.
        val signalLevel = abs(tracking)
        val envCoeff = if (signalLevel > env) attackCoeff else envReleaseCoeff
        env += envCoeff * (signalLevel - env)

        val gateRange = (0.100f - detectorThreshold).coerceAtLeast(0.001f)
        val gated = ((env - detectorThreshold) / gateRange).coerceIn(0f, 1f)
        val shaped = gated * (0.40f + 0.60f * gated)

        if (gated > 0.025f && trackingValidity > 0) {
            holdSamples = holdLength
        } else if (holdSamples > 0) {
            holdSamples--
        }

        val validPitch = trackingValidity > 0 && holdSamples > 0
        val targetEnvelope = if (validPitch) shaped else 0f
        val releaseCoeff = if (targetEnvelope > subEnv) attackCoeff else subReleaseCoeff
        subEnv += releaseCoeff * (targetEnvelope - subEnv)

        // 3. PLL oscillator: tracked source pitch divided by two.
        val oscHz = (trackedHz * 0.5f).coerceIn(targetHz, sweepHz)
        phase += twoPi * oscHz / sr
        if (phase >= twoPi) phase -= twoPi

        val fundamental = sin(phase).toFloat() * subEnv

        // 4. Independent mono SUB path.
        val body = subLp2.process(subLp1.process(fundamental)) * subGain

        // 5. Harmonics are tied to the same oscillator and remain independently controlled.
        val h2 = sin(phase * 2.0).toFloat()
        val h4 = sin(phase * 4.0).toFloat()
        val harmonicSignal = (h2 * 0.32f + h4 * 0.12f) * subEnv
        val harmonic = harmLp.process(harmonicSignal) * harmGain

        // 6. DC blocker. Keep the simulator-approved pole at 0.995.
        val add = body + harmonic
        val dc = add - dcX1 + dcBlockerPole * dcY1
        dcX1 = add
        dcY1 = dc

        // Preserve the dry PCM exactly. Only the generated mono delta is added.
        val positiveHeadroom = minOf(0.995f - left, 0.995f - right).coerceAtLeast(0f)
        val negativeHeadroom = maxOf(-0.995f - left, -0.995f - right).coerceAtMost(0f)
        val safeAdd = dc.coerceIn(negativeHeadroom, positiveHeadroom)

        if (abs(safeAdd - dc) > 1e-7f) clampCount++

        val outL = (left + safeAdd).coerceIn(-1f, 1f)
        val outR = (right + safeAdd).coerceIn(-1f, 1f)

        blockEnergy += safeAdd.toDouble() * safeAdd
        blockCount++

        return (
            java.lang.Float.floatToRawIntBits(outL).toLong() shl 32
        ) or (
            java.lang.Float.floatToRawIntBits(outR).toLong() and 0xFFFFFFFFL
        )
    }

    // -------------------------------------------------
    // BLOCK METERS
    // -------------------------------------------------

    private fun finishBlock() {
        lastSubRmsDbfs = if (blockCount > 0 && blockEnergy > 0.0) {
            (20.0 * log10(sqrt(blockEnergy / blockCount)))
                .toFloat().coerceIn(-120f, 0f)
        } else {
            -120f
        }

        lastClampPercent = if (blockCount > 0) {
            100f * clampCount.toFloat() / blockCount.toFloat()
        } else {
            0f
        }
    }

    // -------------------------------------------------
    // RESET
    // -------------------------------------------------

    fun reset() {
        trackLp1.reset()
        trackLp2.reset()
        trackHp.reset()
        subLp1.reset()
        subLp2.reset()
        harmLp.reset()

        crossingArmed = false
        samplesSinceCross = 0
        trackedHz = targetHz * 2f
        trackingValidity = 0
        crossingParity = false
        phase = 0.0

        env = 0f
        subEnv = 0f
        holdSamples = 0

        dcX1 = 0f
        dcY1 = 0f

        blockEnergy = 0.0
        blockCount = 0
        clampCount = 0

        lastSubRmsDbfs = -120f
        lastClampPercent = 0f
    }

    private fun safeSampleRate(): Int = sampleRate.coerceIn(8000, 192000)

    private fun finiteOr(value: Float, fallback: Float): Float =
        if (value.isFinite()) value else fallback
}
