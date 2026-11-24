package app.ocarinatuner.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The 12 holes of a 12-hole ocarina. */
enum class Hole(val label: String, val onBack: Boolean = false, val small: Boolean = false) {
    L_PINKY("Left pinky", small = true),
    L_RING("Left ring"),
    L_MIDDLE("Left middle"),
    L_INDEX("Left index"),
    L_SUB("Left subhole", small = true),
    R_INDEX("Right index"),
    R_MIDDLE("Right middle"),
    R_RING("Right ring"),
    R_PINKY("Right pinky", small = true),
    R_SUB("Right subhole", small = true),
    L_THUMB("Left thumb", onBack = true),
    R_THUMB("Right thumb", onBack = true);
}

data class Fingering(val midi: Int, val name: String, val open: Set<Hole>) {
    fun isOpen(h: Hole) = h in open
}

class FingeringChart(val instrument: String, fingerings: List<Fingering>) {
    val fingerings: List<Fingering> = fingerings.sortedBy { it.midi }
    private val byMidi = this.fingerings.associateBy { it.midi }

    fun forMidi(midi: Int): Fingering? = byMidi[midi]
    val lowest: Int get() = fingerings.first().midi
    val highest: Int get() = fingerings.last().midi

    /** Next / previous note that has a fingering (for the stepper). */
    fun step(midi: Int, delta: Int): Int {
        val idx = fingerings.indexOfFirst { it.midi >= midi }.let { if (it < 0) fingerings.lastIndex else it }
        val target = (idx + delta).coerceIn(0, fingerings.lastIndex)
        return fingerings[target].midi
    }

    /** Natural notes only (the C major scale), used by the scale drill. */
    val naturals: List<Int> get() = fingerings.map { it.midi }.filter { Music.pitchClass(it).length == 1 }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        @Serializable
        private data class Entry(val note: String, val open: List<String> = emptyList())

        @Serializable
        private data class FileModel(val instrument: String = "", val notes: List<Entry>)

        /** Parses fingerings.json. Throws IllegalArgumentException with a readable message. */
        fun parse(text: String): FingeringChart {
            val model = try {
                json.decodeFromString(FileModel.serializer(), text)
            } catch (e: Exception) {
                throw IllegalArgumentException("Fingering file is not valid JSON: ${e.message}")
            }
            require(model.notes.isNotEmpty()) { "Fingering file has no notes" }
            val list = model.notes.map { e ->
                val midi = Music.parse(e.note) ?: throw IllegalArgumentException("Unknown note '${e.note}'")
                val holes = e.open.map { id ->
                    Hole.entries.firstOrNull { it.name.equals(id.trim(), ignoreCase = true) }
                        ?: throw IllegalArgumentException("Unknown hole '$id' in ${e.note}")
                }.toSet()
                Fingering(midi, e.note, holes)
            }
            val dupes = list.groupBy { it.midi }.filter { it.value.size > 1 }.keys
            require(dupes.isEmpty()) { "Duplicate notes: ${dupes.joinToString { Music.name(it) }}" }
            return FingeringChart(model.instrument, list)
        }
    }
}
