package app.ocarinatuner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.core.BreathAdvice
import app.ocarinatuner.core.Music
import app.ocarinatuner.core.breathAdvice
import app.ocarinatuner.ui.components.BreathBar
import app.ocarinatuner.ui.components.CardHeader
import app.ocarinatuner.ui.components.CentsGauge
import app.ocarinatuner.ui.components.GlassCard
import app.ocarinatuner.ui.components.LiveChip
import app.ocarinatuner.ui.components.OcarinaView
import app.ocarinatuner.ui.components.PitchTrace
import app.ocarinatuner.ui.components.ThumbHoles
import app.ocarinatuner.ui.components.rememberSkinBitmap
import app.ocarinatuner.ui.theme.Palette
import app.ocarinatuner.ui.theme.Type
import java.util.Locale

@Composable
fun TunerScreen(c: TunerController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TunerCard(c)
        BreathCard(c)
        TraceCard(c, height = 150)
    }
}

@Composable
private fun TunerCard(c: TunerController) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        Row(Modifier.fillMaxWidth()) {
            Column {
                Text("Reference", style = Type.Label)
                Text("A4 = ${fmt(c.a4)} Hz", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Instrument", style = Type.Label)
                Text("STL 12-Hole Tenor C", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.TopCenter) {
            CentsGauge({ c.cents }, { c.voiced }, { c.tolerance }, Modifier.fillMaxWidth().height(230.dp), held = { c.hasReading })
            Column(Modifier.offset(y = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                NoteName(c)
                HzLabel(c)
                Spacer(Modifier.height(6.dp))
                CentsPill(c)
            }
        }
        val bmp = rememberSkinBitmap(c.assets, c.skin)
        Box(Modifier.fillMaxWidth()) {
            OcarinaView(c.skin, bmp, { c.fingering }, Modifier.padding(horizontal = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        FingeringStepper(c)
    }
}

@Composable
private fun NoteName(c: TunerController) {
    val midi = c.midi
    // The last note stays on screen after the sound stops, dimmed, until the next note.
    val alpha = when { c.voiced -> 1f; c.hasReading -> 0.5f; else -> 0.25f }
    Text(
        if (!c.hasReading) AnnotatedString("–") else buildAnnotatedString {
            append(Music.pitchClass(midi))
            withStyle(SpanStyle(fontSize = 52.sp, letterSpacing = 0.sp)) { append(Music.octave(midi).toString()) }
        },
        style = Type.Note,
        modifier = Modifier.alpha(alpha),
    )
}

@Composable
private fun HzLabel(c: TunerController) {
    Text(
        when {
            c.voiced -> "${String.format(Locale.US, "%.1f", c.hz)} Hz"
            c.hasReading -> "last note · ${String.format(Locale.US, "%.1f", c.hz)} Hz"
            else -> "Play a note"
        },
        color = Palette.TextDim, fontSize = 18.sp, style = Type.Numbers,
    )
}

@Composable
private fun CentsPill(c: TunerController) {
    val on = c.voiced
    val held = !on && c.hasReading
    val cents = c.cents
    val color = if (on || held) Palette.forCents(cents, c.tolerance) else Palette.TextFaint
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .alpha(if (held) 0.5f else 1f)
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.5.dp, color.copy(alpha = 0.8f), shape)
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        Text(
            if (on || held) "${Music.signed(cents.toDouble(), 1)} cents" else "– cents",
            color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = Type.Numbers,
        )
    }
}

@Composable
private fun FingeringStepper(c: TunerController) {
    val f = c.fingering
    val shown = c.shownMidi
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ThumbHoles(f)
        Spacer(Modifier.weight(1f))
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier.clip(shape).background(Palette.Bg.copy(alpha = 0.5f)).border(1.dp, Palette.Stroke, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Filled.ChevronLeft) { c.stepFingering(-1) }
            Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (f != null) "Fingering" else "No fingering", color = if (f != null) Palette.TextDim else Palette.Sharp, fontSize = 12.sp)
                Text(Music.name(shown), color = if (f != null) Palette.Text else Palette.TextDim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (f == null) Text("add it in Settings", color = Palette.TextFaint, fontSize = 10.sp)
            }
            RoundIcon(Icons.Filled.ChevronRight) { c.stepFingering(1) }
        }
    }
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Palette.CardHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Palette.Text) }
}

@Composable
fun BreathCard(c: TunerController, big: Boolean = false) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Air, null, tint = Palette.Accent, modifier = Modifier.size(if (big) 40.dp else 34.dp))
            Spacer(Modifier.width(10.dp))
            Text("Breath\nControl", color = Color(0xFFCFC3FF), fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(40.dp).background(Palette.Stroke))
            BreathAdviceText(c, big)
        }
        Spacer(Modifier.height(12.dp))
        BreathBar({ c.cents }, { c.voiced }, { c.tolerance }, Modifier.fillMaxWidth().height(if (big) 40.dp else 30.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
            Text(" Softer", color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("Ideal", color = Palette.InTune, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("Harder ", color = Palette.TextDim, fontSize = 13.sp)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BreathAdviceText(c: TunerController, big: Boolean) {
    // derivedStateOf: recomposes only when the advice category changes, not every frame.
    val advice by remember(c) { derivedStateOf { if (c.voiced) breathAdvice(c.cents, c.tolerance) else null } }
    val (title, sub, color, icon) = when (advice) {
        BreathAdvice.SOFTER -> Quad("Blow Softer", "Air is a bit too strong for this note.", Palette.Sharp, Icons.Filled.ArrowDownward)
        BreathAdvice.HARDER -> Quad("Blow Harder", "Give this note a little more air.", Palette.Flat, Icons.Filled.ArrowUpward)
        BreathAdvice.STEADY -> Quad("Steady", "Right in tune. Hold that air.", Palette.InTune, Icons.Filled.Check)
        null -> Quad("Listening…", "Play a note to see your air.", Palette.TextFaint, null)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = color, fontSize = if (big) 30.sp else 24.sp, fontWeight = FontWeight.Bold)
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.padding(start = 6.dp).size(26.dp))
        }
        Text(sub, color = Palette.TextDim, fontSize = 13.sp)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun TraceCard(c: TunerController, height: Int, targetMidi: () -> Int? = { null }) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14) {
        CardHeader(Icons.AutoMirrored.Filled.ShowChart, "Pitch Trace (5 s)") { LiveChip(c.running) }
        Spacer(Modifier.height(10.dp))
        PitchTrace(c.history, { c.frameTick }, { c.tolerance }, Modifier.fillMaxWidth().height(height.dp), targetMidi = targetMidi)
    }
}

fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
