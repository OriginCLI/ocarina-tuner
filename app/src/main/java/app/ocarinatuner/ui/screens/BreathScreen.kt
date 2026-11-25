package app.ocarinatuner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.core.AttackDetector
import app.ocarinatuner.core.Music
import app.ocarinatuner.core.RunningStats
import app.ocarinatuner.ui.components.CardHeader
import app.ocarinatuner.ui.components.GlassCard
import app.ocarinatuner.ui.components.StatTile
import app.ocarinatuner.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BreathScreen(c: TunerController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BreathCard(c, big = true)
        TraceCard(c, height = 230)
        WobbleStats(c)
        AttackFeed(c, limit = 5)
    }
}

@Composable
private fun WobbleStats(c: TunerController) {
    // Recomputed 4x a second from the history, not every frame.
    val stats by produceState(Triple(0.0, 0.0, 0.0), c) {
        while (true) {
            val s = RunningStats()
            var voicedNs = 0L
            var lastT = 0L
            val since = System.nanoTime() - 2_000_000_000L
            c.history.forEachSince(since) { t, m ->
                if (!m.isNaN()) {
                    s.add(((m - m.roundToInt()) * 100f).toDouble())
                    if (lastT != 0L) voicedNs += (t - lastT).coerceAtMost(50_000_000L)
                }
                lastT = t
            }
            value = Triple(s.mean, s.stdDev, voicedNs / 1e9)
            delay(250)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val (mean, sd, secs) = stats
        val has = secs > 0.2
        StatTile("Wobble (2 s)", if (has) "±${sd.roundToInt()}¢" else "–", Modifier.weight(1f),
            if (!has) Palette.TextFaint else if (sd <= 5) Palette.InTune else if (sd <= 12) Palette.Text else Palette.Sharp)
        StatTile("Average", if (has) "${Music.signed(mean)}¢" else "–", Modifier.weight(1f),
            if (has) Palette.forCents(mean.toFloat(), c.tolerance) else Palette.TextFaint)
        StatTile("Sounding", String.format(Locale.US, "%.1f s", secs), Modifier.weight(1f))
    }
}

@Composable
fun AttackFeed(c: TunerController, limit: Int) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        CardHeader(Icons.Outlined.Bolt, "Attack check")
        Spacer(Modifier.height(4.dp))
        Text("How each note starts compared with where it settles.", color = Palette.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (c.attacks.isEmpty()) {
            Text("Tongue a few separate notes to see your attacks.", color = Palette.TextFaint, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 10.dp))
        }
        for (a in c.attacks.take(limit)) AttackRow(a, c.tolerance)
    }
}

@Composable
private fun AttackRow(a: AttackDetector.Attack, tol: Float) {
    val color = Palette.forCents(a.attackCents, tol)
    val verdict = when {
        abs(a.attackCents) <= tol -> "Clean start"
        a.attackCents > 0 -> "Started sharp: tongue softer"
        else -> "Started flat: support the start"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Music.name(a.midi), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(verdict, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("settled ${Music.signed(a.settledCents.toDouble())}¢", color = Palette.TextFaint, fontSize = 12.sp)
        }
        // Mini bar: attack offset, centre = settled pitch.
        Box(Modifier.width(90.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Bg)) {
            val frac = (a.attackCents / 50f).coerceIn(-1f, 1f)
            val w = 45.dp * abs(frac)
            Box(
                Modifier.padding(start = if (frac >= 0) 45.dp else 45.dp - w).width(w).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(color),
            )
            Box(Modifier.padding(start = 44.dp).width(2.dp).height(8.dp).background(Palette.Text))
        }
        Text("${Music.signed(a.attackCents.toDouble())}¢", color = color, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp).padding(start = 8.dp))
    }
}
