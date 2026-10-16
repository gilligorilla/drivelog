package com.example.drivelog.audio

/**
 * User-tunable parameters for the hammer impact detector.
 *
 * @property thresholdDb  Minimum short-time energy (dB relative to digital full scale, so always
 *                        negative) a frame must reach to be considered an impact.
 * @property delaySec     Lock-out window after a trigger during which further triggers are ignored.
 * @property highPassHz   High-pass cutoff in Hz. [FILTER_OFF] (0) disables the filter.
 * @property lowPassHz    Low-pass cutoff in Hz. A value >= [LOW_PASS_OFF_HZ] disables the filter.
 */
data class DetectorSettings(
    val thresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val delaySec: Float = DEFAULT_DELAY_SEC,
    val highPassHz: Int = FILTER_OFF,
    val lowPassHz: Int = LOW_PASS_OFF_HZ,
) {
    val highPassEnabled: Boolean get() = highPassHz > FILTER_OFF
    val lowPassEnabled: Boolean get() = lowPassHz < LOW_PASS_OFF_HZ

    companion object {
        const val FILTER_OFF = 0
        const val LOW_PASS_OFF_HZ = 12_000

        const val THRESHOLD_MIN_DB = -80f
        const val THRESHOLD_MAX_DB = -5f
        const val DEFAULT_THRESHOLD_DB = -30f

        const val DELAY_MIN_SEC = 0.2f
        const val DELAY_MAX_SEC = 5.0f
        const val DEFAULT_DELAY_SEC = 0.8f

        const val HIGH_PASS_MAX_HZ = 2_000
        const val LOW_PASS_MIN_HZ = 300
    }
}
