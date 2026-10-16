package com.example.drivelog

import com.example.drivelog.audio.DetectorSettings
import com.example.drivelog.audio.ImpactDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ImpactDetectorTest {
    private val fs = ImpactDetector.DEFAULT_SAMPLE_RATE

    /** Background noise plus decaying-sine "hammer" impacts at the given times (seconds). */
    private fun signal(
        seconds: Double,
        impactsAt: List<Double>,
        noiseAmp: Double = 0.002,
        impactAmp: Double = 0.5,
        impactHz: Double = 400.0,
        sustained: ((Int) -> Double)? = null,
    ): ShortArray {
        val rnd = Random(42)
        val out = ShortArray((seconds * fs).toInt())
        for (i in out.indices) {
            val t = i.toDouble() / fs
            var v = (rnd.nextDouble() * 2 - 1) * noiseAmp
            for (at in impactsAt) {
                val dt = t - at
                if (dt >= 0) v += impactAmp * exp(-dt * 40) * sin(2 * PI * impactHz * dt)
            }
            sustained?.let { v += it(i) }
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun count(samples: ShortArray, settings: DetectorSettings): Int {
        val detector = ImpactDetector(fs)
        var hits = 0
        var pos = 0
        val chunk = 1024
        while (pos < samples.size) {
            val n = minOf(chunk, samples.size - pos)
            val buf = samples.copyOfRange(pos, pos + n)
            if (detector.process(buf, n, settings)) hits++
            pos += n
        }
        return hits
    }

    private val settings = DetectorSettings(thresholdDb = -30f, delaySec = 0.5f)

    @Test
    fun countsEachSeparatedImpactOnce() {
        val s = signal(8.0, listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
        assertEquals(6, count(s, settings))
    }

    @Test
    fun silenceProducesNoImpacts() {
        assertEquals(0, count(signal(5.0, emptyList()), settings))
    }

    @Test
    fun lockoutSuppressesEchoWithinDelayWindow() {
        // Second spike 0.2 s after the first is inside the 0.5 s lock-out -> counted once.
        val s = signal(4.0, listOf(1.0, 1.2))
        assertEquals(1, count(s, settings))
        // With a short lock-out both register.
        assertEquals(2, count(s, settings.copy(delaySec = 0.2f)))
    }

    @Test
    fun thresholdRejectsQuietImpacts() {
        val quiet = signal(4.0, listOf(1.0, 2.0, 3.0), impactAmp = 0.01)
        assertEquals(0, count(quiet, settings.copy(thresholdDb = -30f)))
        assertEquals(3, count(quiet, settings.copy(thresholdDb = -60f)))
    }

    @Test
    fun sustainedLoudToneIsNotCounted() {
        // 1 kHz alarm-like tone, well above threshold, but with no sharp onset after warm-up.
        val tone = signal(6.0, emptyList(), sustained = { i -> 0.3 * sin(2 * PI * 1000.0 * i / fs) })
        assertEquals(0, count(tone, settings))
    }

    @Test
    fun highPassRemovesLowFrequencyRumble() {
        // Impacts riding on strong 40 Hz rumble that has a slow envelope pulse.
        val rumbleOnly = signal(
            5.0, emptyList(),
            sustained = { i -> 0.3 * sin(2 * PI * 40.0 * i / fs) * (if ((i.toDouble() / fs) % 1.0 < 0.1) 1.0 else 0.0) }
        )
        val withoutFilter = count(rumbleOnly, settings)
        val withFilter = count(rumbleOnly, settings.copy(highPassHz = 500))
        assertEquals(0, withFilter)
        // Sanity: without the filter the rumble pulses do look like impacts.
        assert(withoutFilter > 0)
    }

    @Test
    fun lowPassRemovesHighFrequencyHiss() {
        val hiss = signal(
            5.0, emptyList(),
            sustained = { i -> 0.3 * sin(2 * PI * 8000.0 * i / fs) * (if ((i.toDouble() / fs) % 1.0 < 0.05) 1.0 else 0.0) }
        )
        assert(count(hiss, settings) > 0)
        assertEquals(0, count(hiss, settings.copy(lowPassHz = 1000)))
    }
}
