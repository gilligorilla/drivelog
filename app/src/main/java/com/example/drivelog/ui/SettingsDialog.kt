package com.example.drivelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.drivelog.audio.AudioMonitor
import com.example.drivelog.audio.DetectorSettings
import com.example.drivelog.session.PileSession
import com.example.drivelog.session.RunState
import com.example.drivelog.ui.theme.AlertRed
import com.example.drivelog.ui.theme.GridGray
import com.example.drivelog.ui.theme.Ink
import com.example.drivelog.ui.theme.Paper
import com.example.drivelog.ui.theme.SignalYellow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Detector tuning with live feedback. Changes apply immediately to the running detector.
 * When no pile is being logged, the dialog runs its own listen-only microphone so the sliders can
 * be tuned against site noise without creating a CSV.
 */
@Composable
fun SettingsDialog(micGranted: Boolean, onDismiss: () -> Unit) {
    val settings by PileSession.detectorSettings.collectAsState()
    val session by PileSession.state.collectAsState()
    val level by PileSession.levelDb.collectAsState()
    val hits by PileSession.hitCounter.collectAsState()
    val context = LocalContext.current

    val idle = session.runState == RunState.IDLE
    val listening = session.runState == RunState.RUNNING

    // Listen-only test monitor while idle.
    if (idle && micGranted) {
        DisposableEffect(Unit) {
            val monitor = AudioMonitor(
                context = context,
                settings = { PileSession.detectorSettings.value },
                onImpact = PileSession::onTestImpact,
                onLevel = PileSession::reportLevel,
                onError = PileSession::reportError,
            )
            monitor.start()
            onDispose {
                monitor.stop()
                PileSession.reportLevel(-100f)
            }
        }
    }

    val hitsAtOpen = remember { hits }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(hits) {
        if (hits != hitsAtOpen) {
            flash = true
            delay(180)
            flash = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 720.dp)
                .background(Paper, RoundedCornerShape(12.dp))
                .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "IMPACT DETECTOR",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                BigButton(
                    onClick = onDismiss,
                    container = Ink,
                    content = Paper,
                    minHeight = 64.dp,
                    modifier = Modifier.widthIn(min = 140.dp),
                ) { ButtonText("DONE", it) }
            }

            // Live feedback.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DisplayCell(
                    label = "CURRENT BPF",
                    value = "%03d".format(session.currentBpf),
                    unit = "blows",
                    modifier = Modifier.weight(1f),
                    inverted = flash,
                )
                DisplayCell(
                    label = "HITS SEEN",
                    value = "%03d".format(((hits - hitsAtOpen).coerceAtLeast(0)) % 1000),
                    unit = if (idle) "listen-only" else "since opened",
                    modifier = Modifier.weight(1f),
                    inverted = flash,
                )
            }
            LevelMeter(levelDb = level, thresholdDb = settings.thresholdDb, flash = flash)
            if (idle && !micGranted) {
                Text(
                    "Microphone permission needed for live level. Toggle PLAY once to grant it.",
                    color = AlertRed, fontSize = 16.sp, fontWeight = FontWeight.Black
                )
            } else if (session.runState == RunState.PAUSED) {
                Text(
                    "MICROPHONE OFF - close this and tap the mic icon to turn it back on.",
                    color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black
                )
            } else if (listening) {
                Text(
                    "Live: changes apply to the running detector immediately.",
                    color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black
                )
            }

            SettingSlider(
                label = "ENERGY THRESHOLD",
                valueText = "${settings.thresholdDb.roundToInt()} dB",
                hint = "Higher (toward 0) = only louder hits count",
                value = settings.thresholdDb,
                range = DetectorSettings.THRESHOLD_MIN_DB..DetectorSettings.THRESHOLD_MAX_DB,
                step = 1f,
                onChange = { v -> PileSession.updateDetector { it.copy(thresholdDb = v) } },
            )
            SettingSlider(
                label = "DELAY (LOCK-OUT)",
                valueText = "%.1f s".format(settings.delaySec),
                hint = "Ignore triggers this long after a hit (echo / rebound)",
                value = settings.delaySec,
                range = DetectorSettings.DELAY_MIN_SEC..DetectorSettings.DELAY_MAX_SEC,
                step = 0.1f,
                onChange = { v -> PileSession.updateDetector { it.copy(delaySec = v) } },
            )
            SettingSlider(
                label = "HIGH-PASS CUTOFF",
                valueText = if (settings.highPassEnabled) "${settings.highPassHz} Hz" else "OFF",
                hint = "Cuts engine rumble below this frequency",
                value = settings.highPassHz.toFloat(),
                range = 0f..DetectorSettings.HIGH_PASS_MAX_HZ.toFloat(),
                step = 50f,
                onChange = { v -> PileSession.updateDetector { it.copy(highPassHz = v.roundToInt()) } },
            )
            SettingSlider(
                label = "LOW-PASS CUTOFF",
                valueText = if (settings.lowPassEnabled) "${settings.lowPassHz} Hz" else "OFF",
                hint = "Cuts hiss / alarms above this frequency (max = off)",
                value = settings.lowPassHz.toFloat(),
                range = DetectorSettings.LOW_PASS_MIN_HZ.toFloat()..DetectorSettings.LOW_PASS_OFF_HZ.toFloat(),
                step = 100f,
                onChange = { v -> PileSession.updateDetector { it.copy(lowPassHz = v.roundToInt()) } },
            )
        }
    }
}

@Composable
private fun LevelMeter(levelDb: Float, thresholdDb: Float, flash: Boolean) {
    val minDb = DetectorSettings.THRESHOLD_MIN_DB
    fun frac(db: Float) = ((db - minDb) / (0f - minDb)).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LIVE LEVEL (STE)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("${levelDb.roundToInt()} dB", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(if (flash) SignalYellow else Paper, RoundedCornerShape(6.dp))
                .border(BorderWidth, Ink, RoundedCornerShape(6.dp))
        ) {
            val barW = size.width * frac(levelDb)
            drawRect(Ink, Offset.Zero, Size(barW, size.height))
            val tx = size.width * frac(thresholdDb)
            drawRect(
                if (levelDb >= thresholdDb) SignalYellow else AlertRed,
                Offset(tx - 3.dp.toPx(), 0f),
                Size(6.dp.toPx(), size.height)
            )
        }
        Text(
            "Black bar = sound level. Red/yellow marker = threshold.",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    fun snap(v: Float): Float {
        val snapped = (v / step).roundToInt() * step
        return snapped.coerceIn(range.start, range.endInclusive)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(valueText, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigButton(
                onClick = { onChange(snap(value - step)) },
                minHeight = 56.dp,
                modifier = Modifier.size(56.dp),
            ) { ButtonText("\u2212", it, size = 30) }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { onChange(snap(it)) },
                valueRange = range,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Ink,
                    activeTrackColor = Ink,
                    inactiveTrackColor = GridGray,
                ),
                thumb = {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Ink)
                            .border(3.dp, Paper, CircleShape)
                    )
                },
            )
            BigButton(
                onClick = { onChange(snap(value + step)) },
                minHeight = 56.dp,
                modifier = Modifier.size(56.dp),
            ) { ButtonText("+", it, size = 30) }
        }
        Text(hint, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}
