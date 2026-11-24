package app.ocarinatuner.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocarinatuner.core.AttackDetector
import app.ocarinatuner.core.FingeringChart
import app.ocarinatuner.core.LongToneDrill
import app.ocarinatuner.core.Music
import app.ocarinatuner.core.PitchHistory
import app.ocarinatuner.core.ScaleDrill
import app.ocarinatuner.data.Assets
import app.ocarinatuner.data.Prefs
import kotlin.math.roundToInt

/**
 * Owns the audio engine and all live state. [onFrame] is called once per display frame
 * (from withFrameNanos, i.e. right before the frame is drawn): it drains every analysis
 * frame produced since the last display frame, feeds the history and drills, and publishes
 * the newest reading. Values are Compose snapshot state; components that only draw read
 * them inside draw lambdas so a new reading redraws without recomposing.
 */
class TunerController(context: Context) {
    val prefs = Prefs(context)
    val assets = Assets(context)

    // ---- settings ----
    var a4 by mutableFloatStateOf(prefs.a4); private set
    var gateDb by mutableFloatStateOf(prefs.gateDb); private set
    var tolerance by mutableFloatStateOf(prefs.tolerance); private set
    var showDebug by mutableStateOf(prefs.showDebug); private set

    var chart by mutableStateOf(assets.loadFingerings()); private set
    val skins = mutableStateListOf<Assets.SkinEntry>()
    var skin by mutableStateOf<Assets.SkinEntry?>(null); private set

    // ---- engine state ----
    var running by mutableStateOf(false); private set
    var startError by mutableStateOf<Int?>(null); private set

    // ---- live reading ----
    /** True while a note is sounding right now. */
    var voiced by mutableStateOf(false); private set
    /** True once any note has been heard; the last note stays on screen after it stops. */
    var hasReading by mutableStateOf(false); private set
    var midi by mutableIntStateOf(69); private set
    var cents by mutableFloatStateOf(0f); private set
    var hz by mutableFloatStateOf(0f); private set
    var level by mutableFloatStateOf(-120f); private set

    /** Bumped when new analysis frames arrive; the trace redraws on change. */
    var frameTick by mutableLongStateOf(0L); private set
    val history = PitchHistory(4096)

    /**
     * Note whose fingering is shown (ocarina + stepper). Follows what you play; the stepper
     * arrows browse from there. May be a note with no fingering in the chart.
     */
    var shownMidi by mutableIntStateOf(69)

    // ---- attack check ----
    private var attackDetector = AttackDetector(a4.toDouble())
    val attacks = mutableStateListOf<AttackDetector.Attack>()

    // ---- drills ----
    var longTone by mutableStateOf<LongToneDrill?>(null); private set
    var scale by mutableStateOf<ScaleDrill?>(null); private set
    var drillTick by mutableLongStateOf(0L); private set

    // ---- debug ----
    var stats by mutableStateOf<EngineStats?>(null); private set
    var frameIntervalMs by mutableFloatStateOf(0f); private set
    var readingAgeMs by mutableFloatStateOf(0f); private set
    var framesPerDisplay by mutableIntStateOf(0); private set
    private var lastFrameNanos = 0L
    private var lastStatsNanos = 0L
    private var lastVoicedNs = 0L
    private var latestAnalysisNs = 0L

    init {
        val (list, default) = assets.loadSkins()
        skins.addAll(list)
        skin = list.firstOrNull { it.skin.id == prefs.skinId }
            ?: list.firstOrNull { it.skin.id == default } ?: list.firstOrNull()
    }

    fun start() {
        if (running) return
        NativeEngine.setGate(gateDb, 0.85f)
        val r = NativeEngine.start()
        running = r == 0
        startError = if (r == 0) null else r
        history.clear()
    }

    fun stop() {
        NativeEngine.stop()
        running = false
        voiced = false
    }

    fun onFrame(frameNanos: Long) {
        if (lastFrameNanos != 0L) frameIntervalMs = (frameNanos - lastFrameNanos) / 1e6f
        lastFrameNanos = frameNanos
        if (!running) return

        val n = NativeEngine.drain()
        framesPerDisplay = n
        if (n > 0) {
            val a = a4.toDouble()
            for (i in 0 until n) {
                val t = NativeEngine.times[i]
                val f = NativeEngine.hz[i]
                val m = if (f.isNaN()) Float.NaN else Music.midiOf(f.toDouble(), a).toFloat()
                history.add(t, m)
                attackDetector.feed(t, f)?.let {
                    attacks.add(0, it)
                    if (attacks.size > 20) attacks.removeAt(attacks.lastIndex)
                }
                longTone?.feed(t, f)
                scale?.feed(t, f)
            }
            if (longTone != null || scale != null) drillTick++

            val last = n - 1
            val f = NativeEngine.hz[last]
            latestAnalysisNs = NativeEngine.times[last]
            level = NativeEngine.rmsDb[last]
            if (!f.isNaN()) {
                val m = Music.midiOf(f.toDouble(), a)
                val note = m.roundToInt()
                midi = note
                cents = ((m - note) * 100).toFloat()
                hz = f
                voiced = true
                hasReading = true
                lastVoicedNs = latestAnalysisNs
                shownMidi = note
            } else if (voiced && latestAnalysisNs - lastVoicedNs > 60_000_000L) {
                // Short hold (60 ms) bridges single gated frames without delaying new notes.
                voiced = false
            }
            frameTick++
        }
        val now = System.nanoTime()
        readingAgeMs = if (latestAnalysisNs == 0L) 0f else (now - latestAnalysisNs) / 1e6f

        if (showDebug && now - lastStatsNanos > 250_000_000L) {
            lastStatsNanos = now
            NativeEngine.refreshStats()
            stats = EngineStats(NativeEngine.stats)
        }
    }

    // ---- settings mutations ----
    fun updateA4(v: Float) {
        a4 = (v * 2).roundToInt() / 2f
        prefs.a4 = a4
        attackDetector = AttackDetector(a4.toDouble())
    }

    fun updateGate(db: Float) {
        gateDb = db
        prefs.gateDb = db
        NativeEngine.setGate(db, 0.85f)
    }

    fun updateTolerance(c: Float) { tolerance = c; prefs.tolerance = c }
    fun updateShowDebug(v: Boolean) { showDebug = v; prefs.showDebug = v }

    fun selectSkin(e: Assets.SkinEntry) { skin = e; prefs.skinId = e.skin.id }

    /** Imports a user picture as a new skin and selects it. Throws with a readable message. */
    fun importSkin(uri: android.net.Uri, name: String): Assets.SkinEntry {
        val e = assets.importSkin(uri, name)
        skins.add(e)
        selectSkin(e)
        return e
    }

    fun updateSkinHoles(e: Assets.SkinEntry) {
        if (!e.user && e.skin.holes == e.defaultHoles) assets.clearHoleOverride(e) else assets.saveSkinHoles(e)
        val i = skins.indexOfFirst { it.dir == e.dir }
        if (i >= 0) skins[i] = e
        if (skin?.dir == e.dir) skin = e
    }

    fun deleteUserSkin(e: Assets.SkinEntry) {
        assets.deleteUserSkin(e)
        skins.removeAll { it.dir == e.dir }
        if (skin?.dir == e.dir) skins.firstOrNull()?.let { selectSkin(it) }
    }

    fun reloadFingerings() { chart = assets.loadFingerings() }

    // ---- drills ----
    fun startLongTone(target: Int, seconds: Float = 8f) {
        scale = null
        longTone = LongToneDrill(target, a4.toDouble(), tolerance, seconds)
    }
    fun stopLongTone() { longTone = null }

    fun startScale() {
        longTone = null
        scale = ScaleDrill(chart.naturals, a4.toDouble())
    }
    fun stopScale() { scale = null }
    fun skipScaleNote() { scale?.skip(); drillTick++ }

    fun clearAttacks() = attacks.clear()

    val fingering get() = chart.forMidi(shownMidi)

    fun stepFingering(delta: Int) { shownMidi = chart.step(shownMidi, delta) }
    val estimatedLatencyMs: Double?
        get() {
            val s = stats ?: return null
            if (s.sampleRate == 0 || s.inputLatencyMs < 0) return null
            // mic -> callback, + half the analysis window (where a new note's evidence sits),
            // + wait until the UI picks it up, + one display frame to reach the glass.
            val windowMs = s.window * 1000.0 / s.sampleRate
            return s.inputLatencyMs + windowMs / 2 + readingAgeMs + frameIntervalMs
        }
}
