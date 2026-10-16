package com.example.drivelog.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.drivelog.data.InchesFormat
import com.example.drivelog.session.PileSession
import com.example.drivelog.session.StorageStatus
import com.example.drivelog.ui.theme.AlertRed
import com.example.drivelog.ui.theme.DisabledFill
import com.example.drivelog.ui.theme.DisabledInk
import com.example.drivelog.ui.theme.GridGray
import com.example.drivelog.ui.theme.Ink
import com.example.drivelog.ui.theme.Paper
import com.example.drivelog.ui.theme.PlayGreen
import com.example.drivelog.ui.theme.SignalYellow
import com.example.drivelog.ui.theme.StopRed
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------ shared bits

/** High-contrast dialog card: title, then [content]. Scrolls when taller than the window. */
@Composable
private fun DialogCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 560.dp)
            .background(Paper, RoundedCornerShape(12.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
        content()
    }
}

@Composable
private fun DialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    cancelText: String = "CANCEL",
    confirmContainer: Color = Ink,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton(onClick = onCancel, minHeight = 64.dp, modifier = Modifier.weight(1f)) {
            ButtonText(cancelText, it, size = 22)
        }
        BigButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            container = confirmContainer,
            content = Paper,
            minHeight = 64.dp,
            modifier = Modifier.weight(1f),
        ) { ButtonText(confirmText, it, size = 22) }
    }
}

private val DialogProps = DialogProperties(usePlatformDefaultWidth = false)

// ------------------------------------------------------------------------------------ session

/**
 * Shown when Play is toggled on. Collects the four header fields; the first three are pre-filled
 * with the previous pile's values (they persist), the pile name starts empty and has the focus.
 */
@Composable
fun SessionStartDialog(
    initialProject: String,
    initialHammer: String,
    initialPileDescription: String,
    storage: StorageStatus,
    onGrantStorage: () -> Unit,
    onStart: (project: String, hammer: String, pileDescription: String, pileName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var project by remember { mutableStateOf(initialProject) }
    var hammer by remember { mutableStateOf(initialHammer) }
    var pileDescription by remember { mutableStateOf(initialPileDescription) }
    var pileName by remember { mutableStateOf("") }
    val canStart = pileName.isNotBlank()
    val submit = { if (canStart) onStart(project, hammer, pileDescription, pileName.trim()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        DialogCard("NEW PILE") {
            LabeledField("PROJECT NAME", project, PileSession.MAX_PROJECT, { project = it })
            LabeledField("HAMMER DESCRIPTION", hammer, PileSession.MAX_HAMMER, { hammer = it })
            LabeledField(
                "PILE DESCRIPTION", pileDescription, PileSession.MAX_PILE_DESC,
                { pileDescription = it }, lines = 2,
            )
            LabeledField(
                "PILE NAME", pileName, PileSession.MAX_PILE_NAME, { pileName = it },
                autoFocus = true, onDone = { submit() },
            )
            DialogButtons(
                confirmText = "START",
                confirmContainer = PlayGreen,
                confirmEnabled = canStart,
                onConfirm = { submit() },
                onCancel = onDismiss,
            )
            StorageNote(storage, onGrantStorage)
        }
    }
}

/** Where logs go, and (Android 11+) a prompt for "All files access" if they are going to private storage. */
@Composable
private fun StorageNote(storage: StorageStatus, onGrantStorage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!storage.isSharedDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BigButton(
                onClick = onGrantStorage,
                container = SignalYellow,
                minHeight = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "TAP TO ALLOW ACCESS TO SHARED DOCUMENTS FOLDER",
                    color = it, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (storage.path.isNotEmpty()) {
            Text(
                "Notes file + CSV logs: ${storage.path}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink,
            )
        }
    }
}

@Composable
fun FinalizeConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        DialogCard("FINALIZE PILE") {
            Text(
                "Are you sure you want to finalize this pile?",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink,
            )
            DialogButtons(
                confirmText = "FINALIZE",
                confirmContainer = StopRed,
                onConfirm = onConfirm,
                onCancel = onDismiss,
                cancelText = "NO",
            )
        }
    }
}

// ------------------------------------------------------------------------------------ numbers

/**
 * Numeric-keypad entry limited to [maxDigits] digits and (optionally) a maximum value.
 * The field starts with the current value selected so typing replaces it.
 */
@Composable
fun NumberEntryDialog(
    title: String,
    label: String,
    initial: Int,
    maxDigits: Int,
    maxValue: Int,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val tooBig = value != null && value > maxValue
    val valid = value != null && !tooBig
    val submit = { if (valid) onSubmit(value!!) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        DialogCard(title) {
            LabeledField(
                label, text, maxDigits, { text = it },
                keyboardType = KeyboardType.Number,
                allowed = { it in '0'..'9' },
                autoFocus = true,
                selectAll = true,
                showCounter = false,
                onDone = { submit() },
            )
            if (tooBig) {
                Text(
                    "Maximum is $maxValue",
                    fontSize = 16.sp, fontWeight = FontWeight.Black, color = AlertRed,
                )
            }
            DialogButtons(
                confirmText = "SET",
                confirmEnabled = valid,
                onConfirm = { submit() },
                onCancel = onDismiss,
            )
        }
    }
}

// ------------------------------------------------------------------------------------ notes

/**
 * Full-screen NOTE page: a custom note box (standard keyboard) on top, the file-driven presets
 * below. Either choice calls [onSubmit] and closes the page.
 */
@Composable
fun NoteDialog(
    presets: List<String>,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val canAdd = custom.isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
                .safeDrawingPadding()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOTE", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Ink,
                    modifier = Modifier.weight(1f),
                )
                BigButton(onClick = onDismiss, minHeight = 56.dp, modifier = Modifier.widthIn(min = 130.dp)) {
                    ButtonText("CANCEL", it, size = 20)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    "CUSTOM NOTE", custom, PileSession.MAX_NOTE, { custom = it },
                    modifier = Modifier.weight(1f),
                    onDone = { if (canAdd) onSubmit(custom) },
                )
                BigButton(
                    onClick = { onSubmit(custom) },
                    enabled = canAdd,
                    container = Ink,
                    content = Paper,
                    minHeight = 56.dp,
                    modifier = Modifier.widthIn(min = 100.dp),
                ) { ButtonText("LOG", it, size = 20) }
            }
            Text("PRESETS - TAP TO LOG", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(presets) { _, note -> NoteRow(note) { onSubmit(note) } }
            }
        }
    }
}

@Composable
private fun NoteRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        color = Ink,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(Paper, shape)
            .border(BorderWidth, Ink, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

// ------------------------------------------------------------------------------------ inches

/**
 * Engineering inches picker: a wheel for whole inches 0-11 and a wheel for eighth-inch fractions,
 * previewed as e.g. "8-3/4". Logs the formatted string.
 */
@Composable
fun InchesDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var whole by remember { mutableIntStateOf(0) }
    var fraction by remember { mutableIntStateOf(0) }
    val formatted = InchesFormat.format(whole, fraction)

    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        DialogCard("INCHES") {
            Text(
                "$formatted in",
                fontSize = 44.sp, fontWeight = FontWeight.Black, color = Ink,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("INCHES", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                    Wheel(InchesFormat.WHOLES.map { it.toString() }, whole, { whole = it })
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FRACTION", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                    Wheel(InchesFormat.FRACTIONS, fraction, { fraction = it })
                }
            }
            DialogButtons(
                confirmText = "LOG",
                onConfirm = { onSubmit(formatted) },
                onCancel = onDismiss,
            )
        }
    }
}

private val WheelItemHeight = 60.dp

/**
 * Snapping vertical wheel showing three rows; the middle row is the selection. Rows can be
 * flicked or tapped. Blank spacer rows at both ends let the first/last entry reach the middle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(
    items: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { WheelItemHeight.roundToPx() }

    // With one spacer row above the list, the top visible lazy row index equals the real index
    // of the row sitting in the middle.
    val current by remember(items.size) {
        derivedStateOf {
            val i = state.firstVisibleItemIndex
            val past = state.firstVisibleItemScrollOffset > itemPx / 2
            (if (past) i + 1 else i).coerceIn(0, items.size - 1)
        }
    }
    LaunchedEffect(state) { snapshotFlow { current }.collect { onSelected(it) } }

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(WheelItemHeight * 3)
            .background(Paper, shape)
            .border(BorderWidth, Ink, shape),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(SignalYellow)
        )
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            item(key = "top") { Box(Modifier.height(WheelItemHeight)) }
            itemsIndexed(items, key = { i, _ -> i }) { i, text ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    val on = i == current
                    Text(
                        text,
                        fontSize = if (on) 36.sp else 26.sp,
                        fontWeight = FontWeight.Black,
                        color = if (on) Ink else DisabledInk,
                    )
                }
            }
            item(key = "bottom") { Box(Modifier.height(WheelItemHeight)) }
        }
    }
}

// ------------------------------------------------------------------------------------ splice

private fun isSpliceChar(c: Char) = c == ' ' || (c.code < 128 && c.isLetterOrDigit())

/** Free-text splice description: ASCII letters, digits (and spaces), up to 20 characters. */
@Composable
fun SpliceDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val canLog = text.isNotBlank()
    val submit = { if (canLog) onSubmit(text.trim()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProps) {
        DialogCard("SPLICE") {
            LabeledField(
                "SPLICE DESCRIPTION", text, PileSession.MAX_SPLICE, { text = it },
                allowed = ::isSpliceChar,
                autoFocus = true,
                onDone = { submit() },
            )
            Text(
                "Letters and numbers only",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink,
            )
            DialogButtons(
                confirmText = "LOG",
                confirmEnabled = canLog,
                onConfirm = { submit() },
                onCancel = onDismiss,
            )
        }
    }
}
