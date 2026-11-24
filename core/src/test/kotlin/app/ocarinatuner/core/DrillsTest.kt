package app.ocarinatuner.core

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HOP_NS = 2_666_667L  // 128 samples at 48 kHz

private fun hz(midi: Int, cents: Double) = (440.0 * 2.0.pow((midi - 69 + cents / 100) / 12)).toFloat()

class DrillsTest {
    @Test fun longTonePerfect() {
        val d = LongToneDrill(72, goalSeconds = 2f)
        var t = 1L
        while (!d.finished) { d.feed(t, hz(72, 2.0)); t += HOP_NS }
        assertEquals(1f, d.inTuneShare, 0.01f)
        assertTrue(d.score >= 95, "score ${d.score}")
    }

    @Test fun longToneWobbly() {
        val d = LongToneDrill(72, goalSeconds = 2f)
        var t = 1L; var i = 0
        while (!d.finished) {
            val c = 20.0 * kotlin.math.sin(i * 0.02)  // +/-20 cent wobble
            d.feed(t, hz(72, c)); t += HOP_NS; i++
        }
        assertTrue(d.inTuneShare in 0.2f..0.6f, "in tune ${d.inTuneShare}")
        assertTrue(d.score < 60, "score ${d.score}")
    }

    @Test fun longToneIgnoresOtherNotesAndSilence() {
        val d = LongToneDrill(72, goalSeconds = 1f)
        var t = 1L
        repeat(500) { d.feed(t, hz(74, 0.0)); t += HOP_NS }
        repeat(500) { d.feed(t, Float.NaN); t += HOP_NS }
        assertEquals(0.0, d.onTargetSeconds, 1e-9)
    }

    @Test fun scaleDrillRecordsOffsets() {
        val notes = listOf(69, 71, 72)
        val offsets = listOf(-15.0, 4.0, 22.0)
        val d = ScaleDrill(notes, holdSeconds = 0.5f)
        var t = 1L
        for ((k, midi) in notes.withIndex()) {
            // Attack starts sharp, then settles; with silence between notes.
            repeat(20) { d.feed(t, hz(midi, offsets[k] + 30)); t += HOP_NS }
            while (d.index == k) { d.feed(t, hz(midi, offsets[k])); t += HOP_NS }
            repeat(50) { d.feed(t, Float.NaN); t += HOP_NS }
        }
        assertTrue(d.finished)
        for (k in notes.indices) assertEquals(offsets[k].toFloat(), d.results[k].meanCents, 1.5f)
    }

    @Test fun attackSharpThenSettles() {
        val a = AttackDetector()
        var t = 1L
        var result: AttackDetector.Attack? = null
        repeat(100) { a.feed(t, Float.NaN); t += HOP_NS }
        // 60 ms at +25 cents, then 0.5 s at -5 cents.
        var elapsed = 0.0
        while (elapsed < 0.6) {
            val c = if (elapsed < 0.06) 25.0 else -5.0
            a.feed(t, hz(76, c))?.let { result = it }
            t += HOP_NS; elapsed += HOP_NS / 1e9
        }
        val r = assertNotNull(result)
        assertEquals(76, r.midi)
        assertEquals(30f, r.attackCents, 3f)
        assertEquals(-5f, r.settledCents, 1f)
    }

    @Test fun attackIgnoresBlipsInsideANote() {
        val a = AttackDetector()
        var t = 1L
        var count = 0
        repeat(100) { a.feed(t, Float.NaN); t += HOP_NS }
        repeat(400) { if (a.feed(t, hz(74, 0.0)) != null) count++; t += HOP_NS }
        repeat(3) { a.feed(t, Float.NaN); t += HOP_NS }  // 8 ms dropout
        repeat(400) { if (a.feed(t, hz(74, 0.0)) != null) count++; t += HOP_NS }
        assertEquals(1, count)
        assertNull(a.feed(t, Float.NaN))
    }

    @Test fun historyRing() {
        val h = PitchHistory(4)
        for (i in 1..6) h.add(i.toLong(), i.toFloat())
        val seen = mutableListOf<Float>()
        h.forEachSince(0) { _, m -> seen += m }
        assertEquals(listOf(3f, 4f, 5f, 6f), seen)
        assertEquals(6L, h.latestTime)
    }
}
