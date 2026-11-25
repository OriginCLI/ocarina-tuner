package app.ocarinatuner.core

/**
 * Picks the value left on screen after a note stops.
 *
 * - It is the median of the note's last [windowNs], so the wobble of a release doesn't stick.
 * - A sound shorter than [minNoteNs] (a squeak, a click) is not a note: the note before it
 *   comes back.
 *
 * Feed every voiced analysis frame to [add]; call [release] when the sound has stopped.
 * Allocation-free after construction.
 */
class NoteHold(
    private val gapNs: Long = 60_000_000L,
    private val minNoteNs: Long = 80_000_000L,
    private val windowNs: Long = 200_000_000L,
    capacity: Int = 128,
) {
    private val times = LongArray(capacity)
    private val values = DoubleArray(capacity)
    private val scratch = DoubleArray(capacity)
    private var count = 0
    private var startNs = 0L
    private var lastNs = 0L

    /** Fractional MIDI of the note left on screen; NaN before the first note. */
    var held = Double.NaN
        private set

    fun reset() { count = 0 }

    /** One voiced reading, as fractional MIDI. A gap longer than [gapNs] starts a new note. */
    fun add(timeNs: Long, midi: Double) {
        if (count == 0 || timeNs - lastNs > gapNs) { count = 0; startNs = timeNs }
        val i = count % times.size
        times[i] = timeNs
        values[i] = midi
        count++
        lastNs = timeNs
    }

    /** The note just stopped: returns the value to show (also kept in [held]). */
    fun release(): Double {
        if (count == 0) return held
        if (lastNs - startNs < minNoteNs && !held.isNaN()) { count = 0; return held }
        val n = minOf(count, times.size)
        var k = 0
        for (j in 0 until n) {
            val i = (count - 1 - j) % times.size
            if (lastNs - times[i] > windowNs) break
            scratch[k++] = values[i]
        }
        java.util.Arrays.sort(scratch, 0, k)
        held = scratch[k / 2]
        count = 0
        return held
    }
}
