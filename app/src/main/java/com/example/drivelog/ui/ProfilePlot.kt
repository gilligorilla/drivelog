package com.example.drivelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
 * Scatter + line profile with depth on the Y axis, increasing DOWNWARD (0 at the top), as in a
 * driving log. The X axis tick labels sit along the top edge next to depth 0, with [xLabel]
 * centered above them; [yLabel] is rotated and centered along the left edge.
 *
 * The margins around the plot are measured from the text so larger fonts never get clipped.
 *
 * @param xValue which quantity of a [ProfilePoint] goes on the X axis
 * @param fixedXAxis a fixed X axis, or null to size it from the data
 * @param minXTop smallest X-axis range when sizing from the data
 */
@Composable
fun ProfilePlot(
    points: List<ProfilePoint>,
    xValue: (ProfilePoint) -> Int,
    fixedXAxis: Axis?,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier,
    minXTop: Float = 20f,
) {
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = Ink, fontSize = TICK_FONT_SP.sp, fontWeight = FontWeight.Black)
    val axisStyle = TextStyle(color = Ink, fontSize = AXIS_FONT_SP.sp, fontWeight = FontWeight.Black)

    Canvas(modifier.background(Paper)) {
        val xAxis = fixedXAxis ?: niceAxis(
            dataMax = (points.maxOfOrNull(xValue) ?: 0).toFloat(), minTop = minXTop, maxTicks = 5
        )
        val yAxis = niceAxis(
            dataMax = (points.maxOfOrNull { it.depth } ?: 0).toFloat(), minTop = 10f, maxTicks = 8
        )

        // Measure once to size the margins.
        val xTitle = measurer.measure(xLabel, axisStyle)
        val yTitle = measurer.measure(yLabel, axisStyle)
        val widestY = measurer.measure(yAxis.top.toInt().toString(), tickStyle)
        val widestX = measurer.measure(xAxis.top.toInt().toString(), tickStyle)
        val gap = 4.dp.toPx()

        val left = yTitle.size.height + widestY.size.width + gap * 3
        val top = xTitle.size.height + widestX.size.height + gap * 3
        val right = size.width - widestX.size.width / 2f - gap
        val bottom = size.height - widestY.size.height / 2f - gap
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return@Canvas

        fun px(x: Float) = left + x / xAxis.top * w
        fun py(y: Float) = top + y / yAxis.top * h // depth 0 at the top, growing downward

        // Grid + Y tick labels (right-aligned against the plot).
        var y = 0f
        while (y <= yAxis.top + 0.001f) {
            val yy = py(y)
            drawLine(GridGray, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
            val tl = measurer.measure(y.toInt().toString(), tickStyle)
            drawText(tl, topLeft = Offset(left - gap - tl.size.width, yy - tl.size.height / 2f))
            y += yAxis.step
        }
        // Grid + X tick labels (along the top edge).
        var x = 0f
        while (x <= xAxis.top + 0.001f) {
            val xx = px(x)
            drawLine(GridGray, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
            val tl = measurer.measure(x.toInt().toString(), tickStyle)
            val tx = (xx - tl.size.width / 2f).coerceIn(0f, size.width - tl.size.width.toFloat())
            drawText(tl, topLeft = Offset(tx, top - gap - tl.size.height))
            x += xAxis.step
        }

        // Axis titles: X centered above the tick labels, Y rotated and centered on the left.
        drawText(
            xTitle,
            topLeft = Offset(left + w / 2f - xTitle.size.width / 2f, 0f),
        )
        val cx = yTitle.size.height / 2f
        val cy = top + h / 2f
        rotate(degrees = -90f, pivot = Offset(cx, cy)) {
            drawText(
                yTitle,
                topLeft = Offset(cx - yTitle.size.width / 2f, cy - yTitle.size.height / 2f),
            )
        }

        // Data: connecting line, then markers.
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = Offset(px(xValue(p).toFloat()), py(p.depth.toFloat()))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(
                path, Ink,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        points.forEach { p ->
            val o = Offset(px(xValue(p).toFloat()), py(p.depth.toFloat()))
            drawCircle(Ink, radius = 5.dp.toPx(), center = o)
            drawCircle(Paper, radius = 2.dp.toPx(), center = o)
        }

        // Plot frame on top of the data.
        drawRect(
            Ink,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private const val TICK_FONT_SP = 18
private const val AXIS_FONT_SP = 24
