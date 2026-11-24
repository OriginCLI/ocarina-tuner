package app.ocarinatuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Arc of cent ticks from -50 to +50 with a glowing marker at the current offset.
 * State is read through lambdas inside the draw block, so a new reading only redraws.
 */
@Composable
fun CentsGauge(
    cents: () -> Float,
    voiced: () -> Boolean,
    tolerance: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Palette.TextDim, fontSize = 14.sp, fontFeatureSettings = "tnum") }
    Canvas(modifier) {
        val span = 150f
        val start = 270f - span / 2
        val radius = min(size.width * 0.44f, size.height * 0.88f)
        val center = Offset(size.width / 2, radius + size.height * 0.06f)
        val c = cents()
        val on = voiced()
        val tol = tolerance()
        val color = Palette.forCents(c, tol)

        fun point(angleDeg: Float, r: Float): Offset {
            val a = Math.toRadians(angleDeg.toDouble())
            return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        }
        fun angleOf(v: Float) = start + (v + 50f) / 100f * span

        // Ticks every 2 cents.
        for (i in -25..25) {
            val v = i * 2f
            val major = i % 25 == 0 || i == 0
            val mid = i % 5 == 0
            val len = when { i == 0 -> 30.dp.toPx(); major -> 22.dp.toPx(); mid -> 16.dp.toPx(); else -> 10.dp.toPx() }
            val ang = angleOf(v)
            var tick = if (abs(v) <= tol) Palette.InTune.copy(alpha = 0.35f) else Palette.TextFaint.copy(alpha = 0.55f)
            if (on) {
                val d = abs(v - c)
                if (d < 8f) tick = color.copy(alpha = 0.35f + 0.65f * (1f - d / 8f))
            }
            drawLine(tick, point(ang, radius - len), point(ang, radius),
                strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
        }

        // Current-offset marker with a soft glow.
        if (on) {
            val ang = angleOf(c.coerceIn(-50f, 50f))
            val a = point(ang, radius - 40.dp.toPx())
            val b = point(ang, radius + 8.dp.toPx())
            drawLine(color.copy(alpha = 0.18f), a, b, strokeWidth = 18.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color.copy(alpha = 0.35f), a, b, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, a, b, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }

        // Labels under the arc ends and at +/-25.
        for (v in listOf(-50, -25, 25, 50)) {
            val text = if (v > 0) "+$v" else "$v"
            val layout = measurer.measure(text, labelStyle)
            val p = point(angleOf(v.toFloat()), radius + 6.dp.toPx())
            val y = if (abs(v) == 50) p.y + 6.dp.toPx() else p.y + 4.dp.toPx()
            val x = p.x - layout.size.width / 2f + (if (v < 0) -8.dp.toPx() else 8.dp.toPx())
            drawText(layout, topLeft = Offset(x, y))
        }
    }
}
