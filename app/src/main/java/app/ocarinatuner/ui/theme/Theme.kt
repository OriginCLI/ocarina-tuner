package app.ocarinatuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0B0A12)
    val BgTop = Color(0xFF141126)
    val Card = Color(0xFF14121E)
    val CardHi = Color(0xFF1B1828)
    val Stroke = Color(0xFF2A2639)
    val Text = Color(0xFFF4F1FA)
    val TextDim = Color(0xFFA9A3BC)
    val TextFaint = Color(0xFF6C6680)
    val Accent = Color(0xFF8B6CFF)      // violet, covered holes / selection
    val AccentDeep = Color(0xFF3A2D6E)
    val InTune = Color(0xFF3EE07A)      // green
    val Sharp = Color(0xFFFF8A4C)       // too much air
    val Flat = Color(0xFF5AA9FF)        // too little air
    val Hole = Color(0xFF15111D)

    val NoteBrush = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD9CCFF)))

    /** Green inside tolerance, fading to orange (sharp) / blue (flat) outside it. */
    fun forCents(c: Float, tol: Float): Color {
        val a = kotlin.math.abs(c)
        if (a <= tol) return InTune
        val t = ((a - tol) / 15f).coerceIn(0f, 1f)
        val target = if (c > 0) Sharp else Flat
        return lerp(InTune, target, t)
    }

    private fun lerp(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t, 1f,
    )
}

object Type {
    // Tabular figures so changing numbers don't jitter sideways.
    val Numbers = TextStyle(fontFeatureSettings = "tnum")
    val Note = TextStyle(fontSize = 92.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp, brush = Palette.NoteBrush)
    val Label = TextStyle(fontSize = 13.sp, color = Palette.TextDim, fontWeight = FontWeight.Medium)
    val Title = TextStyle(fontSize = 17.sp, color = Palette.Text, fontWeight = FontWeight.SemiBold)
}

@Composable
fun OcarinaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            secondary = Palette.InTune,
            background = Palette.Bg,
            surface = Palette.Card,
            surfaceContainer = Palette.CardHi,
            surfaceContainerHigh = Palette.CardHi,
            surfaceContainerLow = Palette.Card,
            onSurface = Palette.Text,
            onSurfaceVariant = Palette.TextDim,
            outline = Palette.Stroke,
        ),
        content = content,
    )
}
