package com.example.drivelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import com.example.drivelog.ui.theme.AlertRed
import com.example.drivelog.ui.theme.GridGray
import com.example.drivelog.ui.theme.MicGreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivelog.ui.theme.DisabledFill
import com.example.drivelog.ui.theme.DisabledInk
import com.example.drivelog.ui.theme.Ink
import com.example.drivelog.ui.theme.Paper

val BorderWidth = 3.dp
val ButtonShape = RoundedCornerShape(10.dp)

/** Oversized, thick-bordered button with a minimum 64dp touch target. */
@Composable
fun BigButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Paper,
    content: Color = Ink,
    minHeight: Dp = 72.dp,
    description: String? = null,
    label: @Composable (Color) -> Unit,
) {
    val bg = if (enabled) container else DisabledFill
    val fg = if (enabled) content else DisabledInk
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .background(bg, ButtonShape)
            .border(BorderWidth, if (enabled) Ink else DisabledInk, ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        label(fg)
    }
}

@Composable
fun ButtonText(text: String, color: Color, size: Int = 26) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

/** Sideways triangle. */
@Composable
fun PlayIcon(color: Color, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.06f)
            lineTo(w * 0.94f, h * 0.5f)
            lineTo(w * 0.18f, h * 0.94f)
            close()
        }
        drawPath(path, color)
    }
}

/** Two vertical bars. */
@Composable
fun PauseIcon(color: Color, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val barW = this.size.width * 0.28f
        val h = this.size.height * 0.88f
        val top = (this.size.height - h) / 2f
        drawRect(color, Offset(this.size.width * 0.12f, top), Size(barW, h))
        drawRect(color, Offset(this.size.width * 0.60f, top), Size(barW, h))
    }
}

/** Solid square. */
@Composable
fun StopIcon(color: Color, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val side = this.size.width * 0.78f
        val off = (this.size.width - side) / 2f
        drawRect(color, Offset(off, off), Size(side, side))
    }
}

/**
 * A large read-out cell: small caption above a huge bold value. Optionally interactive: [onClick]
 * (tap) and [onLongClick] (press and hold). A [muted] cell has a grey border (read-only display).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisplayCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    muted: Boolean = false,
    valueSize: Int = 44,
    unit: String = "",
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val bg = if (inverted) Ink else Paper
    val fg = if (inverted) Paper else Ink
    val border = if (muted) GridGray else Ink
    val shape = RoundedCornerShape(8.dp)
    val interactive = onClick != null || onLongClick != null
    Column(
        modifier = modifier
            .background(bg, shape)
            .border(BorderWidth, border, shape)
            .then(
                if (interactive) Modifier.combinedClickable(
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = { onClick?.invoke() },
                ) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            value,
            color = fg,
            fontSize = valueSize.sp,
            lineHeight = (valueSize + 4).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
        )
        if (unit.isNotEmpty()) {
            Text(
                unit,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Microphone status / control. Tap = [onClick], press-and-hold = [onLongClick].
 * Listening: white mic on a green disc. Off: black mic with a prominent red ring and diagonal slash.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MicStatusButton(
    listening: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val description = if (listening) {
        "Microphone on. Tap to turn off, hold for detector settings"
    } else {
        "Microphone off. Tap to turn on, hold for detector settings"
    }
    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        val s = this.size.minDimension
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val glyph = if (listening) Paper else Ink
        if (listening) {
            drawCircle(MicGreen, radius = s / 2f, center = c)
        } else {
            drawCircle(Paper, radius = s / 2f, center = c)
        }

        // Microphone glyph, drawn in a box of ~0.5 * s centred in the disc.
        val unit = s / 100f
        val stroke = 6f * unit
        val bodyW = 22f * unit
        val bodyTop = c.y - 30f * unit
        val bodyH = 38f * unit
        drawRoundRect(
            glyph,
            topLeft = Offset(c.x - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW / 2f),
        )
        // Cradle (U shape around the capsule).
        val cradleW = 36f * unit
        drawArc(
            glyph,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x - cradleW / 2f, bodyTop + bodyH - cradleW / 2f - 6f * unit),
            size = Size(cradleW, cradleW),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val stemTop = bodyTop + bodyH + cradleW / 2f - 6f * unit
        drawLine(glyph, Offset(c.x, stemTop), Offset(c.x, stemTop + 7f * unit), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(
            glyph,
            Offset(c.x - 9f * unit, stemTop + 7f * unit),
            Offset(c.x + 9f * unit, stemTop + 7f * unit),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        if (!listening) {
            val ring = 6f * unit
            drawCircle(AlertRed, radius = s / 2f - ring / 2f, center = c, style = Stroke(width = ring))
            val d = (s / 2f - ring / 2f) * 0.7071f
            drawLine(
                AlertRed,
                Offset(c.x - d, c.y - d),
                Offset(c.x + d, c.y + d),
                strokeWidth = ring,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Bold text input with a thick border and an "n/max" character counter in its caption.
 * Keeps its own cursor state so fast typing never loses characters; follows [value] when it is
 * changed from outside.
 *
 * @param keyboardType system keyboard to show (e.g. [KeyboardType.Number] for a numeric keypad)
 * @param allowed characters accepted from the user; anything else is dropped as it is typed
 * @param autoFocus request focus (and so the keyboard) as soon as the field appears
 * @param selectAll start with the whole initial text selected, so typing replaces it
 * @param onDone invoked when the keyboard's Done key is pressed (default: just dismiss focus)
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    allowed: (Char) -> Boolean = { true },
    autoFocus: Boolean = false,
    selectAll: Boolean = false,
    showCounter: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    var field by remember {
        mutableStateOf(
            TextFieldValue(value, if (selectAll) TextRange(0, value.length) else TextRange(value.length))
        )
    }
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    }
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { requester.requestFocus() }
    }
    val shape = RoundedCornerShape(8.dp)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (showCounter) "$label  ${field.text.length}/$maxLength" else label,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        BasicTextField(
            value = field,
            onValueChange = { new ->
                if (new.text.contains('\n')) {
                    if (onDone != null) onDone() else focus.clearFocus()
                } else {
                    val filtered = if (new.text.all(allowed)) new else {
                        val kept = new.text.filter(allowed)
                        new.copy(text = kept, selection = TextRange(kept.length))
                    }
                    val trimmed = if (filtered.text.length > maxLength) {
                        filtered.copy(
                            text = filtered.text.take(maxLength),
                            selection = TextRange(maxLength),
                        )
                    } else filtered
                    field = trimmed
                    onValueChange(trimmed.text)
                }
            },
            enabled = enabled,
            textStyle = TextStyle(
                color = if (enabled) Ink else DisabledInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            ),
            cursorBrush = SolidColor(Ink),
            singleLine = lines == 1,
            minLines = lines,
            maxLines = lines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else focus.clearFocus() }),
            modifier = Modifier.focusRequester(requester),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(if (enabled) Paper else DisabledFill, shape)
                        .border(BorderWidth, if (enabled) Ink else DisabledInk, shape)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) { inner() }
            },
        )
    }
}
