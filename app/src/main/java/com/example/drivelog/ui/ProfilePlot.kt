package com.example.drivelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivelog.session.ProfilePoint
import com.example.drivelog.ui.theme.GridGray
import com.example.drivelog.ui.theme.Ink
import com.example.drivelog.ui.theme.Paper
import kotlin.math.ceil
import kotlin.math.max

/** A "nice" axis: [top] is a multiple of [step] and covers the data. */
data class Axis(val top: Float, val step: Float)

private val STEPS = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f, 200f, 250f, 500f)

/** Picks the smallest tidy step giving at most [maxTicks] intervals, and rounds the top up to it. */
fun niceAxis(dataMax: Float, minTop: Float, maxTicks: Int): Axis {
    val target = max(dataMax, minTop)
    val step = STEPS.firstOrNull { target / it <= maxTicks } ?: STEPS.last()
    val top = ceil(target / step) * step
    return Axis(top, step)
}

/**
 * One quantity plotted against depth: its X axis [label], which value of a [ProfilePoint] it uses,
 * and the [color] shared by its tick labels, axis label and curve. Give it a [fixedAxis] for a
 * constant range, otherwise the range grows with the data but never shrinks below [minTop].
 */
class PlotSeries(
    val label: String,
    val xValue: (ProfilePoint) -> Int,
    val color: Color = Ink,
    val fixedAxis: Axis? = null,
    val minTop: Float = 20f,
    val lineWidth: Dp = 3.dp,
    val markerRadius: Dp = 5.dp,
)

/**
 * Profile plot with depth on the Y axis, increasing DOWNWARD (0 at the top), as in a driving log.
 *
 * The [primary] series uses the X axis along the top edge (next to depth 0), with its label
 * centered above the tick numbers, and owns the vertical grid. The optional [secondary] series has
 * its own X axis along the bottom edge, drawn in its own color, with its label centered below.
 * [yLabel] is rotated and centered along the left edge.
 *
 * All margins are measured from the text, so larger fonts never get clipped.
 */
@Composable
fun ProfilePlot(
    points: List<ProfilePoint>,
    primary: PlotSeries,
    secondary: PlotSeries?,
    yLabel: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontSize = TICK_FONT_SP.sp, fontWeight = FontWeight.Black)
    val axisStyle = TextStyle(fontSize = AXIS_FONT_SP.sp, fontWeight = FontWeight.Black)

    Canvas(modifier.background(Paper)) {
        fun axisOf(s: PlotSeries) = s.fixedAxis ?: niceAxis(
            dataMax = (points.maxOfOrNull(s.xValue) ?: 0).toFloat(), minTop = s.minTop, maxTicks = 5
        )
        val yAxis = niceAxis(
            dataMax = (points.maxOfOrNull { it.depth } ?: 0).toFloat(), minTop = 10f, maxTicks = 8
        )
        val topAxis = axisOf(primary)
        val bottomAxis = secondary?.let(::axisOf)

        // Measure once to size the margins.
        val gap = 4.dp.toPx()
        val tick = 3.dp.toPx() // length of the small tick marks on the frame
        val topTitle = measurer.measure(primary.label, axisStyle.copy(color = primary.color))
        val bottomTitle = secondary?.let { measurer.measure(it.label, axisStyle.copy(color = it.color)) }
        val yTitle = measurer.measure(yLabel, axisStyle.copy(color = Ink))
        val widestY = measurer.measure(yAxis.top.toInt().toString(), tickStyle)
        val widestTopX = measurer.measure(topAxis.top.toInt().toString(), tickStyle)
        val widestBottomX = bottomAxis?.let { measurer.measure(it.top.toInt().toString(), tickStyle) }
        val tickH = widestTopX.size.height

        val left = yTitle.size.height + widestY.size.width + gap * 4
        val top = topTitle.size.height + tickH + gap * 3
        val right = size.width - max(widestTopX.size.width, widestBottomX?.size?.width ?: 0) / 2f - gap
        // With a bottom axis the room below the frame holds its tick labels and title; otherwise
        // just enough for the last depth label (centered on the bottom line).
        val bottom = if (bottomTitle != null) {
            size.height - (gap * 2 + tickH + gap + bottomTitle.size.height + gap)
        } else {
            size.height - widestY.size.height / 2f - gap
        }
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return@Canvas

        fun px(x: Float, axis: Axis) = left + x / axis.top * w
        fun py(y: Float) = top + y / yAxis.top * h // depth 0 at the top, growing downward

        // Horizontal grid + depth labels (right-aligned, with room so they never touch the
        // bottom axis' first label).
        var y = 0f
        while (y <= yAxis.top + 0.001f) {
            val yy = py(y)
            drawLine(GridGray, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
            val tl = measurer.measure(y.toInt().toString(), tickStyle.copy(color = Ink))
            drawText(tl, topLeft = Offset(left - gap * 2 - tl.size.width, yy - tl.size.height / 2f))
            y += yAxis.step
        }

        // Primary X axis: grid, tick labels along the top, label centered above them.
        drawXTicks(measurer, tickStyle, primary.color, topAxis, left, w, size.width) { xx, text ->
            drawLine(GridGray, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
            drawLine(primary.color, Offset(xx, top - tick), Offset(xx, top), strokeWidth = 2.dp.toPx())
            Offset(xx - text.size.width / 2f, top - gap - text.size.height)
        }
        drawText(topTitle, topLeft = Offset(left + w / 2f - topTitle.size.width / 2f, 0f))

        // Secondary X axis: tick marks and labels along the bottom, label centered below.
        if (secondary != null && bottomAxis != null && bottomTitle != null) {
            drawXTicks(measurer, tickStyle, secondary.color, bottomAxis, left, w, size.width) { xx, text ->
                drawLine(secondary.color, Offset(xx, bottom), Offset(xx, bottom + tick), strokeWidth = 2.dp.toPx())
                Offset(xx - text.size.width / 2f, bottom + gap * 2)
            }
            drawText(
                bottomTitle,
                topLeft = Offset(
                    left + w / 2f - bottomTitle.size.width / 2f,
                    bottom + gap * 2 + tickH + gap,
                ),
            )
        }

        // Y axis title: rotated, centered on the left.
        val cx = yTitle.size.height / 2f
        val cy = top + h / 2f
        rotate(degrees = -90f, pivot = Offset(cx, cy)) {
            drawText(
                yTitle,
                topLeft = Offset(cx - yTitle.size.width / 2f, cy - yTitle.size.height / 2f),
            )
        }

        // Curves: secondary underneath, primary on top.
        if (secondary != null && bottomAxis != null) {
            drawCurve(points, secondary, bottomAxis, ::py) { x, a -> px(x, a) }
        }
        drawCurve(points, primary, topAxis, ::py) { x, a -> px(x, a) }

        // Plot frame on top of the data.
        drawRect(
            Ink,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Walks the ticks of [axis] and draws each label. [placeTick] draws any per-tick decoration for the
 * tick at x-position `xx` and returns where the label's top-left corner goes.
 */
private fun DrawScope.drawXTicks(
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
    axis: Axis,
    left: Float,
    plotWidth: Float,
    canvasWidth: Float,
    placeTick: DrawScope.(xx: Float, label: androidx.compose.ui.text.TextLayoutResult) -> Offset,
) {
    var x = 0f
    while (x <= axis.top + 0.001f) {
        val xx = left + x / axis.top * plotWidth
        val label = measurer.measure(x.toInt().toString(), style.copy(color = color))
        val at = placeTick(xx, label)
        drawText(
            label,
            topLeft = Offset(at.x.coerceIn(0f, canvasWidth - label.size.width), at.y),
        )
        x += axis.step
    }
}

private fun DrawScope.drawCurve(
    points: List<ProfilePoint>,
    series: PlotSeries,
    axis: Axis,
    py: (Float) -> Float,
    px: (Float, Axis) -> Float,
) {
    if (points.size > 1) {
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(px(series.xValue(p).toFloat(), axis), py(p.depth.toFloat()))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(
            path, series.color,
            style = Stroke(width = series.lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    points.forEach { p ->
        val o = Offset(px(series.xValue(p).toFloat(), axis), py(p.depth.toFloat()))
        drawCircle(series.color, radius = series.markerRadius.toPx(), center = o)
        drawCircle(Paper, radius = series.markerRadius.toPx() * 0.4f, center = o)
    }
}

private const val TICK_FONT_SP = 18
private const val AXIS_FONT_SP = 24
