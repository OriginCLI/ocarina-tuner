package app.ocarinatuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.core.PitchHistory
import app.ocarinatuner.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scrolling cents trace over the last [seconds]. Each point is the offset from the nearest
 * note (or from [targetMidi] when practising one note), so wobble is visible at any pitch.
 */
@Composable
fun PitchTrace(
    history: PitchHistory,
    tick: () -> Long,
    tolerance: () -> Float,
    modifier: Modifier = Modifier,
    seconds: Float = 5f,
    targetMidi: () -> Int? = { null },
) {
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(color = Palette.TextDim, fontSize = 11.sp, fontFeatureSettings = "tnum") }
    val line = remember { Path() }
    val fill = remember { Path() }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
    Canvas(modifier) {
        tick()  // subscribe to new frames
        val leftPad = 40.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val w = size.width - leftPad
        val h = size.height - bottomPad
        val yOf = { c: Float -> h / 2 - c / 50f * (h / 2 - 4.dp.toPx()) }
        val tol = tolerance()

        // Tolerance band and grid.
        drawRect(Palette.InTune.copy(alpha = 0.07f), Offset(leftPad, yOf(tol)),
            androidx.compose.ui.geometry.Size(w, yOf(-tol) - yOf(tol)))
        for (c in listOf(50, 25, 0, -25, -50)) {
            val y = yOf(c.toFloat())
            drawLine(if (c == 0) Palette.TextFaint else Palette.Stroke, Offset(leftPad, y), Offset(size.width, y),
                1.dp.toPx(), pathEffect = if (c == 0) null else dash)
            val label = (if (c > 0) "+$c" else "$c") + "¢"
            val l = measurer.measure(label, style)
            drawText(l, topLeft = Offset(leftPad - l.size.width - 6.dp.toPx(), y - l.size.height / 2))
        }
        val secs = seconds.roundToInt()
        for (s in 0..secs) {
            val x = leftPad + w * (1 - s / seconds)
            drawLine(Palette.Stroke, Offset(x, 0f), Offset(x, h), 1.dp.toPx(), pathEffect = dash)
            val l = measurer.measure(if (s == 0) "Now" else "-$s s", style)
            drawText(l, topLeft = Offset((x - l.size.width / 2).coerceIn(leftPad - 10.dp.toPx(), size.width - l.size.width), h + 4.dp.toPx()))
        }

        // Trace.
        val now = System.nanoTime()
        val since = now - (seconds * 1e9).toLong()
        val target = targetMidi()
        line.reset(); fill.reset()
        var penDown = false
        var segStartX = 0f
        var lastX = 0f
        var sumAbs = 0f; var count = 0
        fun closeFill() {
            if (!penDown) return
            fill.lineTo(lastX, yOf(0f)); fill.lineTo(segStartX, yOf(0f)); fill.close()
        }
        history.forEachSince(since) { t, m ->
            val x = leftPad + w * (1f - (now - t) / (seconds * 1e9f))
            if (m.isNaN()) { closeFill(); penDown = false; return@forEachSince }
            val c = if (target != null) (m - target) * 100f else (m - m.roundToInt()) * 100f
            if (abs(c) > 60f) { closeFill(); penDown = false; return@forEachSince }
            val y = yOf(c.coerceIn(-50f, 50f))
            if (!penDown) {
                line.moveTo(x, y); fill.moveTo(x, yOf(0f)); fill.lineTo(x, y); segStartX = x; penDown = true
            } else { line.lineTo(x, y); fill.lineTo(x, y) }
            lastX = x
            sumAbs += abs(c); count++
        }
        closeFill()

        val avg = if (count > 0) sumAbs / count else 0f
        val color = Palette.forCents(avg, tol)
        clipRect(leftPad, 0f, size.width, h) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent, color.copy(alpha = 0.22f)), 0f, h))
            drawPath(line, color.copy(alpha = 0.25f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
