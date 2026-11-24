package app.ocarinatuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.ocarinatuner.ui.theme.Palette
import kotlin.math.max
import kotlin.math.min

/**
 * Air-pressure bar: left = too little air (flat), right = too much air (sharp).
 * The fill runs from "Ideal" in the centre to where you are now.
 */
@Composable
fun BreathBar(cents: () -> Float, voiced: () -> Boolean, tolerance: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color(0xFF0F0D17), size = size, cornerRadius = r)
        drawRoundRect(Palette.Stroke, size = size, cornerRadius = r, style = Stroke(1.dp.toPx()))
        val mid = size.width / 2
        val tol = tolerance()
        val px = (size.width / 2 - h / 2) / 50f

        // Ideal zone.
        drawRect(Palette.InTune.copy(alpha = 0.12f), Offset(mid - tol * px, 0f), Size(2 * tol * px, h))

        if (voiced()) {
            val c = cents().coerceIn(-50f, 50f)
            val x = mid + c * px
            val color = Palette.forCents(c, tol)
            val left = min(mid, x)
            val right = max(mid, x)
            val brush = if (c >= 0) Brush.horizontalGradient(listOf(color.copy(alpha = 0.25f), color), left, right + h / 2)
            else Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.25f)), left - h / 2, right)
            drawRoundRect(brush, Offset(left - (if (c < 0) h / 2 else 0f), 2.dp.toPx()),
                Size(right - left + h / 2, h - 4.dp.toPx()), cornerRadius = CornerRadius(h / 2, h / 2))
        }
        drawLine(Color.White, Offset(mid, -3.dp.toPx()), Offset(mid, h + 3.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
}
