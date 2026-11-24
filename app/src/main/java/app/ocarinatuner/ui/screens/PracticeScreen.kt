package app.ocarinatuner.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.core.Music
import app.ocarinatuner.core.ScaleDrill
import app.ocarinatuner.ui.components.CardHeader
import app.ocarinatuner.ui.components.GlassCard
import app.ocarinatuner.ui.components.OcarinaView
import app.ocarinatuner.ui.components.StatTile
import app.ocarinatuner.ui.components.ThumbHoles
import app.ocarinatuner.ui.components.rememberSkinBitmap
import app.ocarinatuner.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PracticeScreen(c: TunerController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segmented(listOf("Long tone", "Scale", "Attack"), tab) { tab = it }
        when (tab) {
            0 -> LongTonePanel(c)
            1 -> ScalePanel(c)
            else -> AttackFeed(c, limit = 20)
        }
    }
}

@Composable
private fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Palette.Card).border(1.dp, Palette.Stroke, shape).padding(4.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.AccentDeep else Palette.Card)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s, color = if (on) Palette.Text else Palette.TextDim, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun NoteChips(c: TunerController, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in c.chart.fingerings) {
            val on = f.midi == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.clip(shape).background(if (on) Palette.Accent else Palette.CardHi)
                    .border(1.dp, if (on) Palette.Accent else Palette.Stroke, shape)
                    .clickable { onSelect(f.midi) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(f.name, color = Palette.Text, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- Long tone

@Composable
private fun LongTonePanel(c: TunerController) {
    var target by rememberSaveable { mutableIntStateOf(c.chart.forMidi(76)?.midi ?: c.chart.lowest) }
    val drill = c.longTone
    c.drillTick // recompose as the drill advances
    val bmp = rememberSkinBitmap(c.assets, c.skin)

    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        CardHeader(Icons.Outlined.Timer, "Long tone")
        Text("Hold the note within ±${c.tolerance.roundToInt()}¢ for 8 seconds of sound. Pauses don't count against you.",
            color = Palette.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        NoteChips(c, drill?.targetMidi ?: target) { target = it; if (drill != null) c.startLongTone(it) }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = drill?.progress ?: 0f,
                cents = drill?.lastCents ?: Float.NaN,
                tol = c.tolerance,
                label = Music.name(drill?.targetMidi ?: target),
                modifier = Modifier.size(150.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                OcarinaView(c.skin, bmp, { c.chart.forMidi(drill?.targetMidi ?: target) })
                ThumbHoles(c.chart.forMidi(drill?.targetMidi ?: target), Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (drill != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("In tune", "${(drill.inTuneShare * 100).roundToInt()}%", Modifier.weight(1f))
                StatTile("Wobble", "±${drill.stats.stdDev.roundToInt()}¢", Modifier.weight(1f))
                StatTile("Score", if (drill.finished) "${drill.score}" else "…", Modifier.weight(1f),
                    if (drill.finished) Palette.InTune else Palette.TextDim)
            }
            if (drill.finished) {
                Spacer(Modifier.height(8.dp))
                Text(longToneVerdict(drill.stats.mean, drill.stats.stdDev, c.tolerance), color = Palette.Text, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { c.startLongTone(drill?.targetMidi ?: target) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
            ) { Text(if (drill == null) "Start" else "Restart") }
            if (drill != null) OutlinedButton(onClick = { c.stopLongTone() }) { Text("Stop") }
        }
    }
    TraceCard(c, height = 150, targetMidi = { c.longTone?.targetMidi })
}

private fun longToneVerdict(mean: Double, sd: Double, tol: Float): String {
    val air = when {
        mean > tol -> "You ran sharp on average (${mean.roundToInt()}¢): use a little less air on this note."
        mean < -tol -> "You ran flat on average (${mean.roundToInt()}¢): this note wants a little more air."
        else -> "Your average pitch was right on."
    }
    val steady = if (sd <= 5) " Very steady." else if (sd <= 12) " Some wobble; keep the air stream even." else " Lots of wobble; focus on steady breath support."
    return air + steady
}

@Composable
private fun ProgressRing(progress: Float, cents: Float, tol: Float, label: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val big = remember { TextStyle(color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
    val small = remember { TextStyle(color = Palette.TextDim, fontSize = 14.sp, fontFeatureSettings = "tnum") }
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Palette.CardHi, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val color = if (cents.isNaN()) Palette.Accent else Palette.forCents(cents, tol)
        drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val l1 = measurer.measure(label, big)
        drawText(l1, topLeft = Offset((size.width - l1.size.width) / 2, size.height / 2 - l1.size.height + 4.dp.toPx()))
        val txt = if (cents.isNaN()) "${(progress * 8).roundToInt()} / 8 s" else String.format(Locale.US, "%+.0f¢", cents)
        val l2 = measurer.measure(txt, small.copy(color = if (cents.isNaN()) Palette.TextDim else color))
        drawText(l2, topLeft = Offset((size.width - l2.size.width) / 2, size.height / 2 + 6.dp.toPx()))
    }
}

// ---------------------------------------------------------------- Scale

@Composable
private fun ScalePanel(c: TunerController) {
    val drill = c.scale
    c.drillTick
    val bmp = rememberSkinBitmap(c.assets, c.skin)
    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        CardHeader(Icons.Outlined.Stairs, "Scale climb")
        Text("Climb from ${Music.name(c.chart.naturals.first())} to ${Music.name(c.chart.naturals.last())}. Hold each note for 1.5 s; " +
            "you'll see how far off each one sits so you learn how much air it needs.",
            color = Palette.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (drill == null) {
            Button(onClick = { c.startScale() }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) { Text("Start scale") }
        } else if (!drill.finished) {
            val t = drill.target!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Play", color = Palette.TextDim, fontSize = 13.sp)
                    Text(Music.name(t), color = Palette.Text, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    LiveOffset(c, t)
                }
                Column(Modifier.weight(1f)) {
                    OcarinaView(c.skin, bmp, { c.chart.forMidi(t) })
                    ThumbHoles(c.chart.forMidi(t), Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            HoldBar(drill.noteProgress)
            Spacer(Modifier.height(8.dp))
            ScaleDots(drill)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { c.skipScaleNote() }) { Text("Skip note") }
                OutlinedButton(onClick = { c.stopScale() }) { Text("Stop") }
            }
        } else {
            Text("Done! Average offset per note:", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ScaleChart(drill.results, c.tolerance, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(8.dp))
            Text("Above the line = sharp, so that note wants less air. Below = flat, give it more air.",
                color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { c.startScale() }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) { Text("Again") }
        }
        if (drill != null && drill.results.isNotEmpty() && !drill.finished) {
            Spacer(Modifier.height(12.dp))
            ScaleChart(drill.results, c.tolerance, Modifier.fillMaxWidth().height(150.dp))
        }
    }
}

@Composable
private fun LiveOffset(c: TunerController, target: Int) {
    val on = c.voiced && abs(Music.centsFrom(c.hz.toDouble(), target, c.a4.toDouble())) <= 50
    val cents = if (on) Music.centsFrom(c.hz.toDouble(), target, c.a4.toDouble()).toFloat() else Float.NaN
    Text(
        if (on) String.format(Locale.US, "%+.0f¢", cents) else if (c.voiced) "→ ${Music.name(c.midi)}" else "…",
        color = if (on) Palette.forCents(cents, c.tolerance) else Palette.TextFaint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HoldBar(p: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.Bg)) {
        Box(Modifier.fillMaxWidth(p).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.InTune))
    }
}

@Composable
private fun ScaleDots(d: ScaleDrill) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        d.notes.forEachIndexed { i, _ ->
            val color = when {
                i < d.index -> Palette.InTune
                i == d.index -> Palette.Accent
                else -> Palette.CardHi
            }
            Box(Modifier.size(if (i == d.index) 12.dp else 9.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun ScaleChart(results: List<ScaleDrill.NoteResult>, tol: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = remember { TextStyle(color = Palette.TextDim, fontSize = 11.sp) }
    val value = remember { TextStyle(color = Palette.Text, fontSize = 10.sp, fontFeatureSettings = "tnum") }
    Canvas(modifier) {
        val bottom = 18.dp.toPx()
        val h = size.height - bottom
        val mid = h / 2
        val maxC = 40f
        drawRect(Palette.InTune.copy(alpha = 0.08f), Offset(0f, mid - tol / maxC * mid), Size(size.width, 2 * tol / maxC * mid))
        drawLine(Palette.TextFaint, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val n = 13.coerceAtLeast(results.size)
        val slot = size.width / n
        results.forEachIndexed { i, r ->
            val x = slot * i + slot / 2
            val v = r.meanCents.coerceIn(-maxC, maxC)
            val y = mid - v / maxC * mid
            val color = Palette.forCents(r.meanCents, tol)
            drawLine(color, Offset(x, mid), Offset(x, y), (slot * 0.55f), StrokeCap.Butt)
            val t = measurer.measure(Music.name(r.midi), label)
            drawText(t, topLeft = Offset(x - t.size.width / 2, h + 2.dp.toPx()))
            val vt = measurer.measure(r.meanCents.roundToInt().toString(), value)
            val vy = if (v >= 0) y - vt.size.height - 1.dp.toPx() else y + 1.dp.toPx()
            drawText(vt, topLeft = Offset(x - vt.size.width / 2, vy.coerceIn(0f, h - vt.size.height)))
        }
    }
}
