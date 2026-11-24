package app.ocarinatuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.core.Fingering
import app.ocarinatuner.core.Hole
import app.ocarinatuner.core.HolePos
import app.ocarinatuner.data.Assets
import app.ocarinatuner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberSkinBitmap(assets: Assets, entry: Assets.SkinEntry?, maxWidth: Int = 1200): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, entry, maxWidth) {
        value = entry?.let { e -> withContext(Dispatchers.IO) { assets.skinBitmap(e, maxWidth) } }
    }
    return bmp
}

/**
 * Top-down ocarina (the selected skin) with its 10 top holes. Covered holes are filled
 * violet; open holes are dark. Every hole has a light inner rim and dark outer rim so it
 * stays readable on busy glazes.
 */
@Composable
fun OcarinaView(
    entry: Assets.SkinEntry?,
    bitmap: ImageBitmap?,
    fingering: () -> Fingering?,
    modifier: Modifier = Modifier,
    dimmed: () -> Boolean = { false },
) {
    val aspect = entry?.skin?.aspect ?: 1.9f
    Canvas(modifier.fillMaxWidth().aspectRatio(aspect)) {
        val f = fingering()
        val dim = dimmed()
        if (bitmap != null) {
            drawImage(
                bitmap,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                dstOffset = IntOffset.Zero,
                alpha = if (dim) 0.85f else 1f,
            )
        }
        val skin = entry?.skin ?: return@Canvas
        for (h in Hole.entries) {
            if (h.onBack) continue
            val p = skin.hole(h) ?: continue
            val covered = f != null && !f.isOpen(h)
            drawHole(p, covered, f == null)
        }
    }
}

private fun DrawScope.drawHole(p: HolePos, covered: Boolean, unknown: Boolean) {
    val c = Offset(p.x * size.width, p.y * size.height)
    val r = p.r * size.height
    if (covered) {
        drawCircle(Palette.Accent.copy(alpha = 0.35f), r * 1.35f, c)  // glow
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFB39CFF), Palette.Accent, Color(0xFF5B3FD0)),
                center = c + Offset(-r * 0.3f, -r * 0.35f), radius = r * 1.4f,
            ),
            r, c,
        )
    } else {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF000000), Palette.Hole, Color(0xFF2B2338)),
                center = c + Offset(r * 0.15f, r * 0.2f), radius = r * 1.1f,
            ),
            r, c,
            alpha = if (unknown) 0.6f else 1f,
        )
    }
    // Outer dark rim and inner light rim: contrast on light and dark glazes alike.
    drawCircle(Color.Black.copy(alpha = 0.55f), r + 1.2.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
    drawCircle(Color.White.copy(alpha = if (covered) 0.85f else 0.55f), r, c, style = Stroke(1.2.dp.toPx()))
}

/** The two thumb holes on the back of the ocarina. */
@Composable
fun ThumbHoles(fingering: Fingering?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Thumbs", color = Palette.TextDim, fontSize = 12.sp)
        for ((h, label) in listOf(Hole.L_THUMB to "L", Hole.R_THUMB to "R")) {
            val covered = fingering != null && !fingering.isOpen(h)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (covered) Palette.Accent else Palette.Hole)
                        .border(1.5.dp, if (covered) Color.White.copy(alpha = 0.8f) else Palette.TextFaint, CircleShape),
                )
                Text(label, color = Palette.TextFaint, fontSize = 10.sp)
            }
        }
    }
}
