package com.example.drivelog.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/** Where files go and whether that is the real shared Documents folder. */
data class StorageLocation(
    val dir: File,
    /** True when [dir] is the public shared Documents folder. */
    val isSharedDocuments: Boolean,
)

object Storage {
    const val PRESETS_FILE_NAME = "pile_notes_presets.txt"
    const val LOG_SUBDIR = "DriveLog"

    val DEFAULT_PRESETS = listOf(
        "New cushion",
        "Stop for weather",
        "Stop for equipment problem",
        "Stop to align hammer",
    )

    /** Whether the app may read/write the shared Documents folder directly. */
    fun hasSharedAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> false // scoped storage, no legacy opt-out
        else -> context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * The shared Documents folder when accessible, otherwise the app's own Documents folder
     * (so logging never fails because of a missing permission).
     */
    fun resolve(context: Context): StorageLocation {
        if (hasSharedAccess(context)) {
            val shared = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if ((shared.exists() || shared.mkdirs()) && shared.canWrite()) {
                return StorageLocation(shared, true)
            }
        }
        val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        fallback.mkdirs()
        return StorageLocation(fallback, false)
    }

    /**
     * Reads the preset notes file, creating it with [DEFAULT_PRESETS] if it does not exist
     * (or cannot be read). Each non-blank line is one preset note.
     */
    fun loadPresets(context: Context): List<String> {
        val dir = resolve(context).dir
        val file = File(dir, PRESETS_FILE_NAME)
        if (!file.exists()) {
            try {
                file.writeText(DEFAULT_PRESETS.joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {
                return DEFAULT_PRESETS
            }
        }
        return try {
            file.readLines()
                .map { it.trim().removePrefix("\uFEFF").trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { DEFAULT_PRESETS }
        } catch (_: Exception) {
            DEFAULT_PRESETS
        }
    }
}
