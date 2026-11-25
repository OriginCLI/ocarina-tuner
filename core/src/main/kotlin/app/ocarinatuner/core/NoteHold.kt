package app.ocarinatuner.core

import kotlin.math.abs

/**
 * Picks the value left on screen after a note stops.
 *
 * - It is the median of the note's last [windowNs], so the wobble of a release doesn't stick.
 * - A sound shorter than [minNoteNs] (a squeak, a click) is not a note: the note before it
 *   stays.
 * - A note ends at a gap longer than [gapNs] or, when playing legato, at a pitch jump of more
 *   than [jumpSemitones] that lasts [jumpFrames] readings.
 *
 * Feed every voiced analysis frame to [add]; call [release] when the sound has stopped.
 * Allocation-free after construction.
 */
class NoteHold(
    private val gapNs: Long = 60_000_000L,
    private val minNoteNs: Long = 80_000_000L,
    private val windowNs: Long = 200_000_000L,
    private val jumpSemitones: Double = 0.6,
    private val jumpFrames: Int = 3,
    capacity: Int = 128,
) {
    private val times = LongArray(capacity)
    private val values = DoubleArray(capacity)
    private val scratch = DoubleArray(capacity)
    private var count = 0
    private var startNs = 0L
    private var lastNs = 0L
    private var ref = 0.0      // slowly follows the pitch of the current note
    private var jumpRun = 0
    private val carryT = LongArray(jumpFrames)
    private val carryM = DoubleArray(jumpFrames)

    /** Fractional MIDI of the note left on screen; NaN before the first note. */
    var held = Double.NaN
        private set

    fun reset() { count = 0; jumpRun = 0 }

    /** True while a note has readings that [release] hasn't finished yet. */
    val pending: Boolean get() = count > 0

    /** One voiced reading, as fractional MIDI. */
    fun add(timeNs: Long, midi: Double) {
        if (count > 0) {
            if (timeNs - lastNs > gapNs) {
                finish()
            } else if (abs(midi - ref) > jumpSemitones) {
                if (++jumpRun >= jumpFrames) splitAtJump()
            } else {
                jumpRun = 0
            }
        }
        if (count == 0) { startNs = timeNs; ref = midi; jumpRun = 0 }
        val i = count % times.size
        times[i] = timeNs
        values[i] = midi
        count++
        lastNs = timeNs
        if (jumpRun == 0) ref += 0.05 * (midi - ref)
    }

    /**
     * A legato pitch jump is confirmed. The earlier readings of the jump already sit in the
     * old note; move them to the new one so its length (and the blip test) starts at the jump.
     */
    private fun splitAtJump() {
        val k = minOf(jumpRun - 1, count - 1)
        for (j in 0 until k) {
            val i = (count - k + j) % times.size
            carryT[j] = times[i]; carryM[j] = values[i]
        }
        count -= k
        lastNs = times[(count - 1) % times.size]
        finish()
        for (j in 0 until k) {
            if (j == 0) { startNs = carryT[0]; ref = carryM[0] }
            val i = count % times.size
            times[i] = carryT[j]; values[i] = carryM[j]
            count++
            lastNs = carryT[j]
        }
    }

    /** The sound stopped: returns the value to show (also kept in [held]). */
    fun release(): Double { finish(); return held }

    /** Ends the current note; it becomes [held] unless it was only a blip. */
    private fun finish() {
        if (count == 0) return
        if (lastNs - startNs >= minNoteNs || held.isNaN()) {
            val n = minOf(count, times.size)
            var k = 0
            for (j in 0 until n) {
                val i = (count - 1 - j) % times.size
                if (lastNs - times[i] > windowNs) break
                scratch[k++] = values[i]
            }
            java.util.Arrays.sort(scratch, 0, k)
            held = scratch[k / 2]
        }
        count = 0
        jumpRun = 0
    }
}
