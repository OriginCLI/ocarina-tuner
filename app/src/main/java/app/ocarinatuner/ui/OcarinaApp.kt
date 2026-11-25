package app.ocarinatuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.data.Updater
import app.ocarinatuner.ui.screens.BreathScreen
import app.ocarinatuner.ui.screens.PracticeScreen
import app.ocarinatuner.ui.screens.TunerScreen
import app.ocarinatuner.ui.theme.Palette
import kotlinx.coroutines.isActive

@Composable
fun OcarinaApp(
    c: TunerController,
    updater: Updater,
    hasMicPermission: Boolean,
    requestMic: () -> Unit,
) {
    // Per-frame pump: drain the audio engine right before each frame is drawn.
    LaunchedEffect(c) {
        while (isActive) withFrameNanos { c.onFrame(it) }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSkins by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg, Palette.Bg)))
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(onSettings = { showSettings = true }, onSkins = { showSkins = true })
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                // Inline at the top rather than floating, so it never covers the note or meters.
                if (c.showDebug) DebugOverlay(c, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                if (!hasMicPermission) MicPermissionCard(requestMic)
                else if (c.startError != null) EngineErrorCard(c.startError!!) { c.stop(); c.start() }
                when (tab) {
                    0 -> TunerScreen(c)
                    1 -> BreathScreen(c)
                    else -> PracticeScreen(c)
                }
            }
            BottomBar(tab) { tab = it }
        }
    }

    if (showSettings) SettingsSheet(c, updater) { showSettings = false }
    if (showSkins) SkinSheet(c) { showSkins = false }
}

@Composable
private fun Header(onSettings: () -> Unit, onSkins: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OcarinaGlyph(Modifier.size(width = 46.dp, height = 30.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Ocarina Trainer", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Tuner • Breath • Practice", color = Palette.TextDim, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Outlined.Settings, onSettings)
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Outlined.Palette, onSkins)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(46.dp).clip(shape).background(Palette.Card).border(1.dp, Palette.Stroke, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Palette.Text) }
}

/** Small white ocarina used as the app mark in the header. */
@Composable
fun OcarinaGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.05f, h * 0.45f)
            cubicTo(w * 0.05f, h * 0.05f, w * 0.45f, h * 0.0f, w * 0.97f, h * 0.30f)
            cubicTo(w * 1.02f, h * 0.36f, w * 0.95f, h * 0.45f, w * 0.85f, h * 0.47f)
            lineTo(w * 0.36f, h * 0.65f)
            cubicTo(w * 0.33f, h * 0.80f, w * 0.30f, h * 1.0f, w * 0.20f, h * 0.98f)
            cubicTo(w * 0.12f, h * 0.95f, w * 0.18f, h * 0.75f, w * 0.13f, h * 0.68f)
            cubicTo(w * 0.07f, h * 0.62f, w * 0.05f, h * 0.55f, w * 0.05f, h * 0.45f)
            close()
        }
        drawPath(p, Color(0xFFF1ECE6))
        val r = h * 0.07f
        for ((x, y) in listOf(0.25f to 0.32f, 0.38f to 0.26f, 0.51f to 0.27f, 0.64f to 0.30f, 0.77f to 0.33f)) {
            drawCircle(Palette.AccentDeep, r, Offset(w * x, h * y))
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(TabItem("Tuner", Icons.Outlined.Tune), TabItem("Breath", Icons.Outlined.Air), TabItem("Practice", Icons.Outlined.MusicNote))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (on) Palette.AccentDeep else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, null, tint = if (on) Color(0xFFD8CCFF) else Palette.TextDim, modifier = Modifier.size(26.dp))
                Text(item.label, color = if (on) Palette.Text else Palette.TextDim, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MicPermissionCard(request: () -> Unit) {
    app.ocarinatuner.ui.components.GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Microphone needed", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("The tuner listens to your ocarina. Audio never leaves your phone.", color = Palette.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = request, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) { Text("Allow microphone") }
    }
}

@Composable
private fun EngineErrorCard(code: Int, retry: () -> Unit) {
    app.ocarinatuner.ui.components.GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Couldn't open the microphone", color = Palette.Sharp, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Audio error $code. Another app may be using the mic.", color = Palette.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) { Text("Try again") }
    }
}
