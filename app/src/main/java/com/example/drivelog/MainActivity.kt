package com.example.drivelog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.drivelog.session.PileSession
import com.example.drivelog.ui.MainScreen
import com.example.drivelog.ui.theme.DrivelogTheme

class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)
    private var playAfterPermission = false
    private var showStartDialog by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshPermissions()
            PileSession.refreshStorageAndPresets()
            val wantsPlay = playAfterPermission
            playAfterPermission = false
            if (wantsPlay) {
                if (micGranted) {
                    showStartDialog = true
                } else {
                    Toast.makeText(
                        this, "Microphone permission is required to count blows", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()
        requestMissingPermissions()

        setContent {
            DrivelogTheme {
                MainScreen(
                    micGranted = micGranted,
                    showStartDialog = showStartDialog,
                    onRequestStart = ::onRequestStart,
                    onStartDialogDismiss = { showStartDialog = false },
                    onGrantStorage = ::openAllFilesAccessSettings,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions / "All files access" may have changed while we were away; also re-read the
        // preset notes file so edits made in a file manager show up without a restart.
        refreshPermissions()
        PileSession.refreshStorageAndPresets()
    }

    /** Play toggled on: make sure the microphone is allowed, then ask for the pile details. */
    private fun onRequestStart() {
        if (PileSession.state.value.isActive) return
        if (micGranted) {
            PileSession.refreshStorageAndPresets()
            showStartDialog = true
        } else {
            playAfterPermission = true
            requestMissingPermissions()
        }
    }

    private fun refreshPermissions() {
        micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMissingPermissions() {
        val missing = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    /** "All files access" is what allows reading/writing the shared Documents folder (API 30+). */
    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
