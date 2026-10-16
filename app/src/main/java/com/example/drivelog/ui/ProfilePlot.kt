package com.example.drivelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * driving log. The X axis labels sit along the top edge next to depth 0.
 *
 * @param xValue which quantity of a [ProfilePoint] goes on the X axis
 * @param fixedXAxis a fixed X axis, or null to size it from the data
 * @param minXTop smallest X-axis range when sizing from the data
 */
@Composable
fun ProfilePlot(
    title: String,
    points: List<ProfilePoint>,
    xValue: (ProfilePoint) -> Int,
    fixedXAxis: Axis?,
    modifier: Modifier = Modifier,
    minXTop: Float = 20f,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)

    Column(
        modifier = modifier
            .background(Paper, RoundedCornerShape(8.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Text(
            title,
            color = Ink,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val xAxis = fixedXAxis ?: niceAxis(
                dataMax = (points.maxOfOrNull(xValue) ?: 0).toFloat(), minTop = minXTop, maxTicks = 5
            )
            val yAxis = niceAxis(
                dataMax = (points.maxOfOrNull { it.depth } ?: 0).toFloat(), minTop = 10f, maxTicks = 8
            )

            val left = 40.dp.toPx()
            val top = 22.dp.toPx()
            val right = size.width - 14.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val w = right - left
            val h = bottom - top
            if (w <= 0f || h <= 0f) return@Canvas

            fun px(x: Float) = left + x / xAxis.top * w
            fun py(y: Float) = top + y / yAxis.top * h // depth 0 at the top, growing downward

            // Grid + tick labels.
            var y = 0f
            while (y <= yAxis.top + 0.001f) {
                val yy = py(y)
                drawLine(GridGray, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
                val text = y.toInt().toString()
                val tl = measurer.measure(text, labelStyle)
                drawText(
                    tl,
                    topLeft = Offset(left - 4.dp.toPx() - tl.size.width, yy - tl.size.height / 2f)
                )
                y += yAxis.step
            }
            var x = 0f
            while (x <= xAxis.top + 0.001f) {
                val xx = px(x)
                drawLine(GridGray, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
                val text = x.toInt().toString()
                val tl = measurer.measure(text, labelStyle)
                val tx = (xx - tl.size.width / 2f).coerceIn(left - tl.size.width / 2f, size.width - tl.size.width.toFloat())
                drawText(tl, topLeft = Offset(tx, top - 4.dp.toPx() - tl.size.height))
                x += xAxis.step
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

            // Frame on top.
            drawRect(
                Ink,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(w, h),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
