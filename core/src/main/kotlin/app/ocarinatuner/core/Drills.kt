package app.ocarinatuner.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Frames are spaced by one analysis hop (~2.7 ms). Gaps longer than this are not counted. */
private const val MAX_STEP_NS = 50_000_000L
private const val NS = 1e9

/** Running mean and standard deviation (Welford). */
class RunningStats {
    var count = 0; private set
    var mean = 0.0; private set
    private var m2 = 0.0
    fun add(x: Double) {
        count++
        val d = x - mean
        mean += d / count
        m2 += d * (x - mean)
    }
    val stdDev: Double get() = if (count < 2) 0.0 else sqrt(m2 / (count - 1))
    fun reset() { count = 0; mean = 0.0; m2 = 0.0 }
}

/**
 * Long-tone drill: hold [targetMidi] for [goalSeconds] of sounding time. Scores the share of
 * that time spent within +/-[tolerance] cents and how steady the pitch was.
 */
class LongToneDrill(
    val targetMidi: Int,
    private val a4: Double = 440.0,
    val tolerance: Float = 10f,
    val goalSeconds: Float = 8f,
) {
    private var lastT = 0L
    var onTargetSeconds = 0.0; private set
    var inTuneSeconds = 0.0; private set
    val stats = RunningStats()
    var lastCents = Float.NaN; private set

    val progress: Float get() = (onTargetSeconds / goalSeconds).toFloat().coerceIn(0f, 1f)
    val finished: Boolean get() = onTargetSeconds >= goalSeconds
    val inTuneShare: Float get() = if (onTargetSeconds <= 0) 0f else (inTuneSeconds / onTargetSeconds).toFloat()

    /** 0 to 100. 60% time in tune, 40% steadiness (20 cents of wobble scores zero). */
    val steadiness: Float get() = max(0.0, 1.0 - stats.stdDev / 20.0).toFloat()
    val score: Int get() = (100 * (0.6f * inTuneShare + 0.4f * steadiness)).roundToInt()

    fun feed(timeNs: Long, hz: Float) {
        val dt = if (lastT == 0L) 0L else (timeNs - lastT).coerceIn(0, MAX_STEP_NS)
        lastT = timeNs
        if (finished || hz.isNaN()) { lastCents = Float.NaN; return }
        val c = Music.centsFrom(hz.toDouble(), targetMidi, a4)
        if (abs(c) > 50) { lastCents = Float.NaN; return }  // a different note
        lastCents = c.toFloat()
        val s = dt / NS
        onTargetSeconds += s
        if (abs(c) <= tolerance) inTuneSeconds += s
        stats.add(c)
    }
}

/**
 * Scale drill: play each note of [notes] in turn and hold it for [holdSeconds]. The first
 * [settleSeconds] of every attempt are ignored so the attack doesn't skew the average.
 * Records the average offset per note, which tells you how much air each note wants.
 */
class ScaleDrill(
    val notes: List<Int>,
    private val a4: Double = 440.0,
    val holdSeconds: Float = 1.5f,
    val settleSeconds: Float = 0.15f,
) {
    data class NoteResult(val midi: Int, val meanCents: Float, val stdDevCents: Float)

    var index = 0; private set
    val results = mutableListOf<NoteResult>()
    private var lastT = 0L
    private var runSeconds = 0.0
    private var heldSeconds = 0.0
    private val stats = RunningStats()

    val finished: Boolean get() = index >= notes.size
    val target: Int? get() = notes.getOrNull(index)
    val noteProgress: Float get() = (heldSeconds / holdSeconds).toFloat().coerceIn(0f, 1f)

    /** Returns the result when a note was just completed, else null. */
    fun feed(timeNs: Long, hz: Float): NoteResult? {
        val dt = if (lastT == 0L) 0L else (timeNs - lastT).coerceIn(0, MAX_STEP_NS)
        lastT = timeNs
        val t = target ?: return null
        if (hz.isNaN()) { runSeconds = 0.0; return null }
        val c = Music.centsFrom(hz.toDouble(), t, a4)
        if (abs(c) > 50) { runSeconds = 0.0; return null }
        runSeconds += dt / NS
        if (runSeconds < settleSeconds) return null
        heldSeconds += dt / NS
        stats.add(c)
        if (heldSeconds >= holdSeconds) {
            val r = NoteResult(t, stats.mean.toFloat(), stats.stdDev.toFloat())
            results += r
            index++
            heldSeconds = 0.0
            runSeconds = 0.0
            stats.reset()
            return r
        }
        return null
    }

    fun skip() {
        if (finished) return
        index++
        heldSeconds = 0.0; runSeconds = 0.0; stats.reset()
    }
}

/**
 * Attack check: watches for note onsets (sound after a short silence) and reports whether the
 * first ~60 ms were sharp or flat compared with where the note settled.
 */
class AttackDetector(
    private val a4: Double = 440.0,
    private val minSilenceSeconds: Float = 0.10f,
    private val attackSeconds: Float = 0.06f,
    private val settleFrom: Float = 0.20f,
    private val settleTo: Float = 0.45f,
) {
    data class Attack(
        val timeNs: Long,
        val midi: Int,
        /** Attack pitch minus settled pitch, in cents. Positive = started sharp. */
        val attackCents: Float,
        /** Where the note settled relative to its equal-tempered pitch. */
        val settledCents: Float,
    )

    private val maxFrames = 1024
    private val t = LongArray(maxFrames)
    private val m = FloatArray(maxFrames)
    private var n = 0
    private var inNote = false
    private var emitted = false
    private var onsetT = 0L
    private var silenceStart = 0L
    private var lastT = 0L

    fun feed(timeNs: Long, hz: Float): Attack? {
        lastT = timeNs
        if (hz.isNaN()) {
            if (silenceStart == 0L) silenceStart = timeNs
            if (inNote && (timeNs - silenceStart) / NS >= 0.06) {
                // Note ended. Report it if it lasted long enough to have settled a bit.
                val res = if (!emitted && (silenceStart - onsetT) / NS >= settleFrom + 0.05) evaluate() else null
                inNote = false
                return res
            }
            return null
        }
        val silentFor = if (silenceStart == 0L) Double.MAX_VALUE else (timeNs - silenceStart) / NS
        silenceStart = 0L
        val midi = Music.midiOf(hz.toDouble(), a4).toFloat()
        if (!inNote) {
            if (silentFor < minSilenceSeconds) return null  // a blip inside a phrase
            inNote = true; emitted = false; onsetT = timeNs; n = 0
        } else if (n > 0 && abs(midi - m[n - 1]) > 0.7f) {
            // Slurred to a new note without silence: treat as a new onset.
            val res = if (!emitted && (timeNs - onsetT) / NS >= settleFrom + 0.05) evaluate() else null
            onsetT = timeNs; n = 0; emitted = false
            push(timeNs, midi)
            return res
        }
        push(timeNs, midi)
        if (!emitted && (timeNs - onsetT) / NS >= settleTo) return evaluate()
        return null
    }

    private fun push(time: Long, midi: Float) {
        if (n < maxFrames) { t[n] = time; m[n] = midi; n++ }
    }

    private fun evaluate(): Attack? {
        emitted = true
        val attack = FloatArray(n)
        val settle = FloatArray(n)
        var na = 0
        var ns = 0
        for (i in 0 until n) {
            val s = (t[i] - onsetT) / NS
            if (s <= attackSeconds) attack[na++] = m[i]
            if (s in settleFrom.toDouble()..settleTo.toDouble()) settle[ns++] = m[i]
        }
        if (na == 0 || ns < 3) return null
        settle.sort(0, ns)
        val settled = settle[ns / 2]
        var sum = 0.0
        for (i in 0 until na) sum += attack[i]
        val attackMean = (sum / na).toFloat()
        val note = settled.roundToInt()
        return Attack(onsetT, note, (attackMean - settled) * 100f, (settled - note) * 100f)
    }
}
