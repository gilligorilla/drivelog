package com.example.drivelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivelog.session.PileSession
import com.example.drivelog.session.SessionState
import com.example.drivelog.ui.theme.AlertRed
import com.example.drivelog.ui.theme.Ink
import com.example.drivelog.ui.theme.Paper
import com.example.drivelog.ui.theme.PlayGreen
import com.example.drivelog.ui.theme.StopRed

/** Which popup (if any) is currently open over the main screen. */
private enum class Overlay { NONE, DETECTOR, DEPTH, BPF, STROKE, NOTE, INCHES, SPLICE, FINALIZE }

/**
 * Main screen. Portrait: header / displays / action buttons, with the BPF-vs-depth chart filling
 * the rest. Landscape: the same controls on the left, chart on the right.
 *
 * All editing is done through popups: the start-session dialog (shown when Play is toggled on,
 * driven by [showStartDialog]), numeric entry from long-pressing the displays, and the NOTE /
 * INCHES / SPLICE pages.
 */
@Composable
fun MainScreen(
    micGranted: Boolean,
    showStartDialog: Boolean,
    onRequestStart: () -> Unit,
    onStartDialogDismiss: () -> Unit,
    onGrantStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PileSession.state.collectAsState()
    val presets by PileSession.presets.collectAsState()
    val storage by PileSession.storage.collectAsState()
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Paper)
            .padding(8.dp)
    ) {
        val landscape = maxWidth > maxHeight
        // Short screens (landscape phones) get tighter controls.
        val compact = maxHeight < 520.dp
        val controlPanel: @Composable (Modifier) -> Unit = { m ->
            ControlPanel(
                state = state,
                compact = compact,
                onToggleSession = {
                    if (state.isActive) overlay = Overlay.FINALIZE else onRequestStart()
                },
                onMicTap = { PileSession.toggleMic() },
                onMicLongPress = { overlay = Overlay.DETECTOR },
                onDepthLongPress = { overlay = Overlay.DEPTH },
                onBpfLongPress = { overlay = Overlay.BPF },
                onStrokeLongPress = { overlay = Overlay.STROKE },
                onInches = { overlay = Overlay.INCHES },
                onSplice = { overlay = Overlay.SPLICE },
                onNote = { overlay = Overlay.NOTE },
                modifier = m,
            )
        }
        val chart: @Composable (Modifier) -> Unit = { m ->
            ProfilePlot(
                title = "BPF vs DEPTH (ft)",
                points = state.points,
                xValue = { it.bpf },
                fixedXAxis = null,
                minXTop = 100f,
                modifier = m,
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                controlPanel(Modifier.weight(1f).fillMaxHeight())
                chart(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                controlPanel(Modifier.fillMaxWidth())
                chart(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    // ---- Popups
    val close = { overlay = Overlay.NONE }
    when (overlay) {
        Overlay.NONE -> Unit
        Overlay.DETECTOR -> SettingsDialog(micGranted = micGranted, onDismiss = close)
        Overlay.DEPTH -> NumberEntryDialog(
            title = "DEPTH", label = "DEPTH (FT)", initial = state.depth,
            maxDigits = 3, maxValue = PileSession.MAX_DEPTH,
            onSubmit = { PileSession.setDepth(it); close() },
            onDismiss = close,
        )
        Overlay.BPF -> NumberEntryDialog(
            title = "CURRENT BPF", label = "BLOWS THIS FOOT", initial = state.currentBpf,
            maxDigits = 3, maxValue = PileSession.MAX_BPF,
            onSubmit = { PileSession.setBpf(it); close() },
            onDismiss = close,
        )
        Overlay.STROKE -> NumberEntryDialog(
            title = "STROKE", label = "STROKE (IN, 0-${PileSession.STROKE_MAX})",
            initial = state.strokeInches,
            maxDigits = 2, maxValue = PileSession.STROKE_MAX,
            onSubmit = { PileSession.setStroke(it); close() },
            onDismiss = close,
        )
        Overlay.NOTE -> NoteDialog(
            presets = presets,
            onSubmit = { PileSession.logNote(it); close() },
            onDismiss = close,
        )
        Overlay.INCHES -> InchesDialog(
            onSubmit = { PileSession.logInches(it); close() },
            onDismiss = close,
        )
        Overlay.SPLICE -> SpliceDialog(
            onSubmit = { PileSession.logSplice(it); close() },
            onDismiss = close,
        )
        Overlay.FINALIZE -> FinalizeConfirmDialog(
            onConfirm = { PileSession.stop(); close() },
            onDismiss = close,
        )
    }

    if (showStartDialog && !state.isActive) {
        SessionStartDialog(
            initialProject = state.projectName,
            initialHammer = state.hammerDescription,
            initialPileDescription = state.pileDescription,
            storage = storage,
            onGrantStorage = onGrantStorage,
            onStart = { project, hammer, desc, name ->
                PileSession.beginSession(project, hammer, desc, name)
                onStartDialogDismiss()
            },
            onDismiss = onStartDialogDismiss,
        )
    }
}

@Composable
private fun ControlPanel(
    state: SessionState,
    compact: Boolean,
    onToggleSession: () -> Unit,
    onMicTap: () -> Unit,
    onMicLongPress: () -> Unit,
    onDepthLongPress: () -> Unit,
    onBpfLongPress: () -> Unit,
    onStrokeLongPress: () -> Unit,
    onInches: () -> Unit,
    onSplice: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier,
) {
    val active = state.isActive
    val valueSize = if (compact) 34 else 44
    val buttonH = if (compact) 56.dp else 72.dp

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
        // ---- Header: Play/Stop toggle, active pile name, microphone status
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BigButton(
                onClick = onToggleSession,
                container = if (active) StopRed else PlayGreen,
                content = Paper,
                description = if (active) "Stop" else "Play",
                minHeight = buttonH,
                modifier = Modifier.width(112.dp),
            ) { color -> if (active) StopIcon(color) else PlayIcon(color) }
            Text(
                text = state.pileName,
                color = Ink,
                fontSize = pileNameSize(state.pileName.length, compact).sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            MicStatusButton(
                listening = state.micOn,
                onClick = onMicTap,
                onLongClick = onMicLongPress,
                size = if (compact) 56.dp else 68.dp,
            )
        }

        // ---- Read-outs. Tap / long-press act on them while a pile is active.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DisplayCell(
                "DEPTH", "%03d".format(state.depth), Modifier.weight(1f), valueSize = valueSize,
                onClick = if (active) ({ PileSession.addDepth() }) else null,
                onLongClick = if (active) onDepthLongPress else null,
            )
            DisplayCell(
                "PREV BPF", "%03d".format(state.previousBpf), Modifier.weight(1f),
                muted = true, valueSize = valueSize,
            )
            DisplayCell(
                "CURR BPF", "%03d".format(state.currentBpf), Modifier.weight(1f),
                inverted = true, valueSize = valueSize,
                onClick = if (active) ({ PileSession.incrementBpf() }) else null,
                onLongClick = if (active) onBpfLongPress else null,
            )
            DisplayCell(
                "STROKE", "%02d".format(state.strokeInches), Modifier.weight(1f), valueSize = valueSize,
                onLongClick = if (active) onStrokeLongPress else null,
            )
        }

        // ---- Logging pages
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(onClick = onInches, enabled = active, minHeight = buttonH, modifier = Modifier.weight(1f)) {
                ButtonText("INCHES", it, size = 20)
            }
            BigButton(onClick = onSplice, enabled = active, minHeight = buttonH, modifier = Modifier.weight(1f)) {
                ButtonText("SPLICE", it, size = 20)
            }
            BigButton(onClick = onNote, enabled = active, minHeight = buttonH, modifier = Modifier.weight(1f)) {
                ButtonText("NOTE", it, size = 20)
            }
        }

        StatusLine(state)
    }
}

/** Shrinks long pile names so a full 10-character name still fits beside the buttons. */
private fun pileNameSize(length: Int, compact: Boolean): Int {
    val base = when {
        length <= 6 -> 40
        length <= 8 -> 34
        else -> 28
    }
    return if (compact) base - 6 else base
}

/** Shown only when there is something to say (file name, saved confirmation, errors). */
@Composable
private fun StatusLine(state: SessionState) {
    if (state.statusMessage.isEmpty()) return
    Text(
        state.statusMessage,
        color = if (state.statusIsError) AlertRed else Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}
