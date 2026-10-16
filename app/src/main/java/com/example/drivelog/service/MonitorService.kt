package com.example.drivelog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.drivelog.MainActivity
import com.example.drivelog.R
import com.example.drivelog.audio.AudioMonitor
import com.example.drivelog.session.PileSession
import com.example.drivelog.session.RunState
import com.example.drivelog.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the microphone while a pile is being logged, so impacts keep
 * being detected, counted and written to the CSV with the screen off or the app in the
 * background. A persistent notification shows the active pile name and current blow count.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitor: AudioMonitor? = null
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(PileSession.state.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            PileSession.reportError("Background monitoring failed to start: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitor == null) {
            acquireWakeLock()
            monitor = AudioMonitor(
                context = this,
                settings = { PileSession.detectorSettings.value },
                onImpact = PileSession::onImpact,
                onLevel = PileSession::reportLevel,
                onError = PileSession::reportError,
            ).also {
                it.listening = PileSession.state.value.runState == RunState.RUNNING
                it.start()
            }
            observer = scope.launch {
                PileSession.state.collect { state ->
                    if (state.runState == RunState.IDLE) {
                        stopSelf()
                    } else {
                        monitor?.listening = state.runState == RunState.RUNNING
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                    }
                }
            }
        }
        // Do not auto-restart: a restarted service would have no pile/CSV context.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        monitor?.stop()
        monitor = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun buildNotification(state: SessionState): Notification {
        val pile = state.pileName.ifBlank { "(unnamed)" }
        val status = when (state.runState) {
            RunState.RUNNING -> "LISTENING"
            RunState.PAUSED -> "PAUSED"
            RunState.IDLE -> "STOPPING"
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pile)
            .setContentTitle("Pile $pile  \u2022  $status")
            .setContentText("Blows: ${state.currentBpf}   Depth: ${state.depth} ft   Stroke: ${state.strokeInches}\"")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pile monitoring", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active pile and blow count while the microphone is monitored"
                setShowBadge(false)
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "drivelog:monitor").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private companion object {
        const val CHANNEL_ID = "pile_monitor"
        const val NOTIFICATION_ID = 1
        const val MAX_WAKE_LOCK_MS = 16L * 60 * 60 * 1000 // a long shift, then auto-release
    }
}
