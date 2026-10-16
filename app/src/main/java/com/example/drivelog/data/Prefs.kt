package com.example.drivelog.data

import android.content.Context
import android.content.SharedPreferences
import com.example.drivelog.audio.DetectorSettings

/** Persists the text fields that survive resets, and the detector tuning. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("drivelog", Context.MODE_PRIVATE)

    var projectName: String
        get() = sp.getString(K_PROJECT, "") ?: ""
        set(v) = sp.edit().putString(K_PROJECT, v).apply()

    var hammerDescription: String
        get() = sp.getString(K_HAMMER, "") ?: ""
        set(v) = sp.edit().putString(K_HAMMER, v).apply()

    var pileDescription: String
        get() = sp.getString(K_PILE_DESC, "") ?: ""
        set(v) = sp.edit().putString(K_PILE_DESC, v).apply()

    var detector: DetectorSettings
        get() = DetectorSettings(
            thresholdDb = sp.getFloat(K_THRESHOLD, DetectorSettings.DEFAULT_THRESHOLD_DB),
            delaySec = sp.getFloat(K_DELAY, DetectorSettings.DEFAULT_DELAY_SEC),
            highPassHz = sp.getInt(K_HP, DetectorSettings.FILTER_OFF),
            lowPassHz = sp.getInt(K_LP, DetectorSettings.LOW_PASS_OFF_HZ),
        )
        set(v) {
            sp.edit()
                .putFloat(K_THRESHOLD, v.thresholdDb)
                .putFloat(K_DELAY, v.delaySec)
                .putInt(K_HP, v.highPassHz)
                .putInt(K_LP, v.lowPassHz)
                .apply()
        }

    private companion object {
        const val K_PROJECT = "project"
        const val K_HAMMER = "hammer"
        const val K_PILE_DESC = "pile_desc"
        const val K_THRESHOLD = "threshold_db"
        const val K_DELAY = "delay_sec"
        const val K_HP = "high_pass_hz"
        const val K_LP = "low_pass_hz"
    }
}
