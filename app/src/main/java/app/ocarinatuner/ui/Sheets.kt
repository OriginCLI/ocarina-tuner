package app.ocarinatuner.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.core.HolePos
import app.ocarinatuner.data.Assets
import app.ocarinatuner.data.Updater
import app.ocarinatuner.ui.components.OcarinaView
import app.ocarinatuner.ui.components.rememberSkinBitmap
import app.ocarinatuner.ui.screens.fmt
import app.ocarinatuner.ui.theme.Palette
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(c: TunerController, updater: Updater, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        val err = c.assets.importFingerings(text)
        if (err == null) { c.reloadFingerings(); Toast.makeText(ctx, "Fingerings loaded", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(c.assets.fingeringText().toByteArray()) }
        Toast.makeText(ctx, "Saved. Edit it, then Import.", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Settings", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            SettingSlider("Reference pitch", "A4 = ${fmt(c.a4)} Hz", c.a4, 430f..450f, 39) { c.updateA4(it) }
            SettingSlider("In-tune window", "±${c.tolerance.roundToInt()} cents", c.tolerance, 3f..25f, 21) { c.updateTolerance(it.roundToInt().toFloat()) }
            SettingSlider("Mic sensitivity", "ignore sound below ${c.gateDb.roundToInt()} dB", c.gateDb, -70f..-25f, 44) { c.updateGate(it.roundToInt().toFloat()) }
            Text("Raise it if background noise shows up as notes; lower it if quiet notes are missed.",
                color = Palette.TextFaint, fontSize = 12.sp)

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Debug overlay", color = Palette.Text, fontSize = 16.sp)
                    Text("Buffer size, analysis time, latency estimate", color = Palette.TextDim, fontSize = 13.sp)
                }
                Switch(c.showDebug, { c.updateShowDebug(it) })
            }

            Section("Fingerings")
            Text(
                (if (c.assets.hasCustomFingerings) "Using your imported chart" else "Using the built-in chart") +
                    " (${c.chart.fingerings.size} notes). Export it, edit it in any text editor (e.g. add sharps), then import it.",
                color = Palette.TextDim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("fingerings.json") }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("Import") }
                if (c.assets.hasCustomFingerings) TextButton(onClick = { c.assets.resetFingerings(); c.reloadFingerings() }) { Text("Reset") }
            }

            Section("App update")
            UpdateSection(updater) { scope.launch { it() } }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = Palette.Stroke)
    Spacer(Modifier.height(14.dp))
    Text(title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingSlider(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.Accent, fontSize = 14.sp)
    }
    Slider(v, onChange, valueRange = range, steps = steps)
}

@Composable
private fun UpdateSection(u: Updater, run: (suspend () -> Unit) -> Unit) {
    Text("Installed: version ${u.currentVersion} (build ${u.currentBuild})", color = Palette.TextDim, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    val accent = ButtonDefaults.buttonColors(containerColor = Palette.Accent)
    when (val s = u.state) {
        Updater.State.Idle -> Button(onClick = { run { u.check() } }, colors = accent) { Text("Check for update") }
        Updater.State.Checking -> Text("Checking GitHub…", color = Palette.TextDim)
        is Updater.State.UpToDate -> {
            Text("You're on the latest version.", color = Palette.InTune)
            TextButton(onClick = { run { u.check() } }) { Text("Check again") }
        }
        is Updater.State.Available -> {
            Text("New version available: ${s.name}", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            if (s.notes.isNotBlank()) Text(s.notes.take(400), color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { run { u.download(s) } }, colors = accent) {
                Text("Download" + if (s.sizeBytes > 0) " (${String.format(Locale.US, "%.1f", s.sizeBytes / 1e6)} MB)" else "")
            }
        }
        is Updater.State.Downloading -> {
            Text("Downloading… ${(s.progress * 100).roundToInt()}%", color = Palette.TextDim)
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Updater.State.NeedsPermission -> {
            Text("One-time step: allow this app to install updates. Tap below, switch on \"Allow from this source\", then come back here.",
                color = Palette.Text, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { u.openInstallPermission() }, colors = accent) { Text("Open permission setting") }
        }
        is Updater.State.Ready -> {
            Text("Downloaded. Tap Install, then confirm \"Update\" on the next screen. Your settings and skins are kept.",
                color = Palette.Text, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { u.install(s.file) }, colors = accent) { Text("Install update") }
        }
        is Updater.State.Error -> {
            Text(s.message, color = Palette.Sharp, fontSize = 14.sp)
            TextButton(onClick = { run { u.check() } }) { Text("Try again") }
        }
    }
}

// ------------------------------------------------------------------ Skins

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(c: TunerController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Assets.SkinEntry?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var askName by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { askName = uri; pendingName = "" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("Ocarina skin", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Pick a look, or import your own picture. Imported skins stay on this phone.",
                color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.height(460.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(c.skins, key = { it.key }) { e ->
                    SkinTile(c, e, selected = c.skin?.key == e.key,
                        onClick = { c.selectSkin(e) }, onEdit = { editing = e })
                }
                item {
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.fillMaxWidth().height(116.dp).clip(shape).border(1.dp, Palette.Accent.copy(alpha = 0.6f), shape)
                            .clickable { picker.launch(arrayOf("image/png", "image/webp", "image/*")) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = Palette.Accent)
                        Text("Import your own", color = Palette.Text, fontSize = 14.sp)
                        Text("PNG, hole-free, top-down", color = Palette.TextFaint, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    askName?.let { uri ->
        AlertDialog(
            onDismissRequest = { askName = null },
            title = { Text("Name your skin") },
            text = { OutlinedTextField(pendingName, { pendingName = it }, singleLine = true, placeholder = { Text("My ocarina") }) },
            confirmButton = {
                TextButton(onClick = {
                    askName = null
                    try { editing = c.importSkin(uri, pendingName) }
                    catch (e: Exception) { Toast.makeText(ctx, e.message ?: "Import failed", Toast.LENGTH_LONG).show() }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { askName = null }) { Text("Cancel") } },
            containerColor = Palette.CardHi,
        )
    }
    editing?.let { e -> SkinEditor(c, e) { editing = null } }
}

@Composable
private fun SkinTile(c: TunerController, e: Assets.SkinEntry, selected: Boolean, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    val shape = RoundedCornerShape(16.dp)
    val bmp = rememberSkinBitmap(c.assets, e, maxWidth = 400)
    Column(
        Modifier.fillMaxWidth().height(116.dp).clip(shape).background(Palette.Bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.Stroke, shape)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OcarinaView(e, bmp, { c.chart.forMidi(76) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.skin.name, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
            if (onEdit != null) Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, "Adjust holes", tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Drag holes into place on an imported skin. */
@Composable
fun SkinEditor(c: TunerController, entry: Assets.SkinEntry, onClose: () -> Unit) {
    val holes = remember(entry) { mutableStateMapOf<String, HolePos>().apply { putAll(entry.skin.holes) } }
    val original = remember(entry) { entry.defaultHoles }
    var sizeScale by remember(entry) { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val bmp = rememberSkinBitmap(c.assets, entry)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Bg).padding(16.dp)) {
            Text("Line up the holes", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Drag each circle to where that hole is on your ocarina. Reset puts them back.",
                color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(entry.skin.aspect)
                    .pointerInput(entry) {
                        detectDragGestures(
                            onDragStart = { p ->
                                dragging = holes.entries.minByOrNull { (_, h) ->
                                    hypot(h.x * size.width - p.x, h.y * size.height - p.y)
                                }?.takeIf { (_, h) ->
                                    hypot(h.x * size.width - p.x, h.y * size.height - p.y) < h.r * sizeScale * size.height + 40.dp.toPx()
                                }?.key
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                        ) { change, drag ->
                            val id = dragging ?: return@detectDragGestures
                            change.consume()
                            val h = holes[id] ?: return@detectDragGestures
                            holes[id] = h.copy(
                                x = (h.x + drag.x / size.width).coerceIn(0f, 1f),
                                y = (h.y + drag.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    },
            ) {
                bmp?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero) }
                for ((id, h) in holes) {
                    val ctr = Offset(h.x * size.width, h.y * size.height)
                    val r = h.r * sizeScale * size.height
                    val active = id == dragging
                    drawCircle(Palette.Accent.copy(alpha = if (active) 0.9f else 0.65f), r, ctr)
                    drawCircle(Color.Black.copy(alpha = 0.6f), r + 1.5f, ctr, style = Stroke(3f))
                    drawCircle(Color.White, r, ctr, style = Stroke(if (active) 4f else 2f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Hole size", color = Palette.Text)
            Slider(sizeScale, { sizeScale = it }, valueRange = 0.6f..1.6f)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { holes.clear(); holes.putAll(original); sizeScale = 1f }) { Text("Reset") }
                if (entry.user) TextButton(onClick = { c.deleteUserSkin(entry); onClose() }) { Text("Delete", color = Palette.Sharp) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                Button(onClick = {
                    val scaled = holes.mapValues { (_, h) -> h.copy(r = h.r * sizeScale) }
                    c.updateSkinHoles(entry.copy(skin = entry.skin.copy(holes = scaled)))
                    onClose()
                }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent)) { Text("Save") }
            }
        }
    }
}

// ------------------------------------------------------------------ Debug overlay

@Composable
fun DebugOverlay(c: TunerController, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val s = c.stats
    val total = c.estimatedLatencyMs
    val totalColor = if (total == null) Color.White else if (total < 50) Palette.InTune else Palette.Sharp
    val shape = RoundedCornerShape(12.dp)
    if (!expanded) {
        // Collapsed: a small chip with the latency estimate. Tap to see details, × to hide.
        Row(
            modifier.clip(shape).background(Color(0xE0000000)).border(1.dp, Palette.Stroke, shape)
                .clickable { expanded = true }.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("latency ", color = Color(0xFF9A9A9A), fontSize = 11.sp)
            Text(total?.let { "≈${f1(it)} ms" } ?: "…", color = totalColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(28.dp).clickable { c.updateShowDebug(false) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Hide debug", tint = Color(0xFF9A9A9A), modifier = Modifier.size(16.dp))
            }
        }
        return
    }
    Column(
        modifier.width(220.dp).clip(shape).background(Color(0xF0000000)).border(1.dp, Palette.Stroke, shape)
            .clickable { expanded = false }.padding(10.dp),
    ) {
        @Composable
        fun line(k: String, v: String, color: Color = Color(0xFFE0E0E0)) = Row {
            Text(k, color = Color(0xFF9A9A9A), fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(v, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DEBUG · tap to shrink", color = Palette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clickable { c.updateShowDebug(false) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Hide debug", tint = Color(0xFF9A9A9A), modifier = Modifier.size(16.dp))
            }
        }
        if (s == null || s.sampleRate == 0) { line("engine", if (c.running) "starting" else "stopped"); return@Column }
        line("api", "${s.api}${if (s.exclusive) " excl" else " shared"}")
        line("input preset", s.preset)
        line("sample rate", "${s.sampleRate} Hz")
        line("burst / buffer", "${s.framesPerBurst} / ${s.bufferSize} (cap ${s.bufferCapacity})")
        line("callback", "${s.callbackFrames} fr, ${f1(s.callbackIntervalMs)} ms")
        line("window / hop", "${s.window} / ${s.hop} (${f1(s.window * 1000.0 / s.sampleRate)} ms)")
        line("analysis", "${f1(s.analysisAvgUs)} µs avg, ${f1(s.analysisMaxUs)} max")
        line("input latency", if (s.inputLatencyMs >= 0) "${f1(s.inputLatencyMs)} ms" else "n/a")
        line("reading age", "${f1(c.readingAgeMs.toDouble())} ms")
        line("display frame", "${f1(c.frameIntervalMs.toDouble())} ms (${if (c.frameIntervalMs > 0) (1000 / c.frameIntervalMs).roundToInt() else 0} Hz)")
        line("frames/draw", "${c.framesPerDisplay}")
        line("xruns / dropped", "${s.xruns} / ${s.dropped}")
        line("sound→screen ≈", total?.let { "${f1(it)} ms" } ?: "n/a", totalColor)
    }
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
