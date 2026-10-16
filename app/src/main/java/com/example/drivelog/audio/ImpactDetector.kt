package com.example.drivelog.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Second-order IIR section (RBJ cookbook), direct form I, double precision.
 */
internal class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun setLowPass(sampleRate: Int, cutoffHz: Double, q: Double) {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = (1.0 - cosW) / 2.0 / a0
        b1 = (1.0 - cosW) / a0
        b2 = b0
        a1 = -2.0 * cosW / a0
        a2 = (1.0 - alpha) / a0
    }

    fun setHighPass(sampleRate: Int, cutoffHz: Double, q: Double) {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = (1.0 + cosW) / 2.0 / a0
        b1 = -(1.0 + cosW) / a0
        b2 = b0
        a1 = -2.0 * cosW / a0
        a2 = (1.0 - alpha) / a0
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

/**
 * Hammer impact detector based on Short-Time Energy (STE).
 *
 * Pipeline per sample: optional 4th-order Butterworth high-pass -> optional 4th-order Butterworth
 * low-pass -> accumulate squared samples into fixed-size frames. Per frame:
 *
 *  1. STE = mean(x^2); reported in dB re full scale ([lastLevelDb]).
 *  2. An impact is registered when ALL of these hold:
 *     - STE >= threshold,
 *     - STE is at least [ONSET_MARGIN_DB] above a slowly adapting background estimate. This
 *       rejects sustained loud noise (engines, backup alarms) that sits above the threshold but
 *       has no sharp onset,
 *     - the lock-out ("delay") window since the previous impact has elapsed. This rejects
 *       echoes and secondary rebounds of the same blow.
 *
 * Timing is derived from the number of samples processed (not wall-clock time) so behaviour is
 * deterministic and unit-testable. Not thread safe; use from a single audio thread.
 */
class ImpactDetector(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val frameSize: Int = DEFAULT_FRAME_SIZE,
) {
    private val highPass = arrayOf(Biquad(), Biquad())
    private val lowPass = arrayOf(Biquad(), Biquad())
    private var appliedHighPassHz = Int.MIN_VALUE
    private var appliedLowPassHz = Int.MIN_VALUE

    private var frameCount = 0
    private var frameAccumulator = 0.0

    private var sampleClock = 0L
    private var lastTriggerClock = Long.MIN_VALUE / 2
    private var framesSinceReset = 0
    private var backgroundPower = 1e-10

    /** STE of the most recently completed frame, in dB re full scale. */
    @Volatile
    var lastLevelDb: Float = SILENCE_DB
        private set

    private val frameSeconds = frameSize.toDouble() / sampleRate

    /** Clears filter memory and background estimate, e.g. when listening resumes after a pause. */
    fun reset() {
        highPass.forEach { it.reset() }
        lowPass.forEach { it.reset() }
        frameCount = 0
        frameAccumulator = 0.0
        framesSinceReset = 0
        backgroundPower = 1e-10
        lastLevelDb = SILENCE_DB
        // Keep sampleClock/lastTriggerClock so a pause shorter than the lock-out does not
        // allow an immediate double count; time is only counted while listening, which is fine.
    }

    /**
     * Feed 16-bit PCM samples. Returns true if at least one impact was detected in this buffer.
     */
    fun process(samples: ShortArray, count: Int, settings: DetectorSettings): Boolean {
        updateFilters(settings)
        val useHp = settings.highPassEnabled
        val useLp = settings.lowPassEnabled
        var impact = false

        for (i in 0 until count) {
            var x = samples[i] / 32768.0
            if (useHp) {
                x = highPass[1].process(highPass[0].process(x))
            }
            if (useLp) {
                x = lowPass[1].process(lowPass[0].process(x))
            }
            frameAccumulator += x * x
            sampleClock++
            if (++frameCount == frameSize) {
                val power = frameAccumulator / frameSize
                frameAccumulator = 0.0
                frameCount = 0
                if (onFrame(power, settings)) impact = true
            }
        }
        return impact
    }

    private fun onFrame(power: Double, settings: DetectorSettings): Boolean {
        val levelDb = (10.0 * log10(power + 1e-10)).toFloat()
        lastLevelDb = levelDb
        framesSinceReset++

        var triggered = false
        if (framesSinceReset > WARMUP_FRAMES) {
            val aboveThreshold = levelDb >= settings.thresholdDb
            val backgroundDb = 10.0 * log10(backgroundPower + 1e-10)
            val sharpOnset = levelDb - backgroundDb >= ONSET_MARGIN_DB
            val lockoutSamples = (settings.delaySec * sampleRate).toLong()
            val lockoutOver = sampleClock - lastTriggerClock >= lockoutSamples
            if (aboveThreshold && sharpOnset && lockoutOver) {
                triggered = true
                lastTriggerClock = sampleClock
            }
        }

        // Update the background estimate AFTER the decision so a spike cannot mask itself.
        // Fast convergence during warm-up, then rise slowly / fall quickly.
        val tau = when {
            framesSinceReset <= WARMUP_FRAMES -> WARMUP_TAU_SEC
            power > backgroundPower -> BACKGROUND_RISE_TAU_SEC
            else -> BACKGROUND_FALL_TAU_SEC
        }
        val alpha = (frameSeconds / tau).coerceAtMost(1.0)
        backgroundPower += (power - backgroundPower) * alpha

        return triggered
    }

    private fun updateFilters(settings: DetectorSettings) {
        if (settings.highPassHz != appliedHighPassHz) {
            appliedHighPassHz = settings.highPassHz
            if (settings.highPassEnabled) {
                val fc = settings.highPassHz.toDouble().coerceAtMost(sampleRate * 0.45)
                highPass[0].setHighPass(sampleRate, fc, BUTTERWORTH4_Q1)
                highPass[1].setHighPass(sampleRate, fc, BUTTERWORTH4_Q2)
            }
            highPass.forEach { it.reset() }
        }
        if (settings.lowPassHz != appliedLowPassHz) {
            appliedLowPassHz = settings.lowPassHz
            if (settings.lowPassEnabled) {
                val fc = settings.lowPassHz.toDouble().coerceAtMost(sampleRate * 0.45)
                lowPass[0].setLowPass(sampleRate, fc, BUTTERWORTH4_Q1)
                lowPass[1].setLowPass(sampleRate, fc, BUTTERWORTH4_Q2)
            }
            lowPass.forEach { it.reset() }
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val DEFAULT_FRAME_SIZE = 512
        const val SILENCE_DB = -100f

        /** An impact must exceed the background by at least this much (dB). */
        const val ONSET_MARGIN_DB = 6.0

        private const val WARMUP_FRAMES = 40
        private const val WARMUP_TAU_SEC = 0.1
        private const val BACKGROUND_RISE_TAU_SEC = 2.0
        private const val BACKGROUND_FALL_TAU_SEC = 0.3

        // Q values of the two cascaded sections of a 4th-order Butterworth filter.
        private const val BUTTERWORTH4_Q1 = 0.5412
        private const val BUTTERWORTH4_Q2 = 1.3066
    }
}
