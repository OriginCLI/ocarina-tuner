package app.ocarinatuner.core

/**
 * Fixed-size ring of recent pitch frames, stored as primitives so feeding it every audio
 * hop (~375 per second) allocates nothing.
 */
class PitchHistory(val capacity: Int = 4096) {
    private val times = LongArray(capacity)
    private val midis = FloatArray(capacity)  // fractional MIDI, NaN when unvoiced
    private var head = 0
    var size = 0
        private set

    /** Bumped on every add, so a UI can redraw only when something changed. */
    var version = 0L
        private set

    fun add(timeNs: Long, midi: Float) {
        times[head] = timeNs
        midis[head] = midi
        head = (head + 1) % capacity
        if (size < capacity) size++
        version++
    }

    fun clear() { size = 0; head = 0; version++ }

    val latestTime: Long get() = if (size == 0) 0L else times[(head - 1 + capacity) % capacity]

    /** Visits frames oldest-first whose time is >= [sinceNs]. */
    inline fun forEachSince(sinceNs: Long, block: (timeNs: Long, midi: Float) -> Unit) {
        for (i in 0 until size) {
            val idx = indexFromOldest(i)
            val t = timeAt(idx)
            if (t >= sinceNs) block(t, midiAt(idx))
        }
    }

    fun indexFromOldest(i: Int): Int = (head - size + i + capacity * 2) % capacity
    fun timeAt(idx: Int): Long = times[idx]
    fun midiAt(idx: Int): Float = midis[idx]
}
