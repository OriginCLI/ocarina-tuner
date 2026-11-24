package app.ocarinatuner.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Note names, MIDI numbers and cents. A4 = MIDI 69. */
object Music {
    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val LETTER_SEMITONE = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val LN2 = ln(2.0)

    fun pitchClass(midi: Int): String = SHARP_NAMES[((midi % 12) + 12) % 12]
    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1
    fun name(midi: Int): String = pitchClass(midi) + octave(midi)

    /** Parses "A4", "C#5", "Bb4", "F♯5". Returns null when the text is not a note. */
    fun parse(text: String): Int? {
        val s = text.trim()
        if (s.isEmpty()) return null
        val base = LETTER_SEMITONE[s[0].uppercaseChar()] ?: return null
        var i = 1
        var acc = 0
        while (i < s.length && s[i] in "#b♯♭") {
            acc += if (s[i] == '#' || s[i] == '♯') 1 else -1
            i++
        }
        val oct = s.substring(i).toIntOrNull() ?: return null
        return (oct + 1) * 12 + base + acc
    }

    /** Fractional MIDI number of a frequency. */
    fun midiOf(hz: Double, a4: Double = 440.0): Double = 69.0 + 12.0 * ln(hz / a4) / LN2

    fun hzOf(midi: Double, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)

    /** Cents of [hz] relative to the equal-tempered pitch of [midi]. */
    fun centsFrom(hz: Double, midi: Int, a4: Double = 440.0): Double = (midiOf(hz, a4) - midi) * 100.0

    /** Nearest note and the offset from it, -50..+50 cents. */
    fun nearest(hz: Double, a4: Double = 440.0): NoteReading {
        val m = midiOf(hz, a4)
        val n = m.roundToInt()
        return NoteReading(n, ((m - n) * 100.0).toFloat())
    }
}

data class NoteReading(val midi: Int, val cents: Float) {
    val name: String get() = Music.name(midi)
}

enum class BreathAdvice { SOFTER, HARDER, STEADY }

/**
 * On an ocarina more air pushes pitch sharp and less air pulls it flat, so the cents
 * offset doubles as a breath-pressure meter.
 */
fun breathAdvice(cents: Float, tolerance: Float = 10f): BreathAdvice = when {
    abs(cents) <= tolerance -> BreathAdvice.STEADY
    cents > 0 -> BreathAdvice.SOFTER
    else -> BreathAdvice.HARDER
}
