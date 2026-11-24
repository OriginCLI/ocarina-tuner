package app.ocarinatuner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicTest {
    @Test fun names() {
        assertEquals("A4", Music.name(69))
        assertEquals("C5", Music.name(72))
        assertEquals("F6", Music.name(89))
        assertEquals("C#5", Music.name(73))
    }

    @Test fun parse() {
        assertEquals(69, Music.parse("A4"))
        assertEquals(73, Music.parse("C#5"))
        assertEquals(70, Music.parse("Bb4"))
        assertEquals(78, Music.parse("F♯5"))
        assertNull(Music.parse("H2"))
        assertNull(Music.parse("A"))
    }

    @Test fun nearestAndCents() {
        val r = Music.nearest(440.0)
        assertEquals(69, r.midi); assertEquals(0f, r.cents, 1e-4f)
        val sharp = Music.nearest(440.0 * Math.pow(2.0, 12.0 / 1200))
        assertEquals(69, sharp.midi); assertEquals(12f, sharp.cents, 1e-3f)
        assertEquals(89, Music.nearest(1396.91).midi)
        // Reference pitch shifts the reading.
        assertEquals(-3.93f, Music.nearest(440.0, a4 = 441.0).cents, 0.01f)
        assertEquals(1396.91, Music.hzOf(89.0), 0.01)
    }

    @Test fun breath() {
        assertEquals(BreathAdvice.STEADY, breathAdvice(9f))
        assertEquals(BreathAdvice.SOFTER, breathAdvice(15f))
        assertEquals(BreathAdvice.HARDER, breathAdvice(-15f))
    }
}
