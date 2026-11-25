package app.ocarinatuner.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteHoldTest {
    private val frameNs = 2_666_667L // hop 128 at 48 kHz

    private fun NoteHold.play(fromNs: Long, ms: Int, midi: (Int) -> Double): Long {
        val frames = (ms * 1_000_000L / frameNs).toInt()
        for (i in 0 until frames) add(fromNs + i * frameNs, midi(i))
        return fromNs + frames * frameNs
    }

    @Test fun releaseGlitchDoesNotStick() {
        val h = NoteHold()
        // A5 steady at 0 cents for 1 s, then the last 10 frames drift +4 cents as it stops.
        h.play(0, 1000) { i -> if (i >= 365) 81.04 else 81.0 }
        assertEquals(81.0, h.release(), 1e-9)
    }

    @Test fun shortBlipBringsBackThePreviousNote() {
        val h = NoteHold()
        var t = h.play(0, 800) { 69.02 }
        assertEquals(69.02, h.release(), 1e-9)
        t += 200_000_000L
        h.play(t, 40) { 80.0 } // a 40 ms squeak
        assertEquals(69.02, h.release(), 1e-9)
    }

    @Test fun shortFirstNoteIsStillShown() {
        val h = NoteHold()
        h.play(0, 40) { 72.0 }
        assertEquals(72.0, h.release(), 1e-9)
    }

    @Test fun newNoteAfterAGapReplacesTheOldOne() {
        val h = NoteHold()
        var t = h.play(0, 500) { 69.0 }
        h.release()
        t += 300_000_000L
        h.play(t, 500) { 74.1 }
        assertEquals(74.1, h.release(), 1e-9)
        assertTrue(!h.held.isNaN())
    }

    @Test fun shortLegatoLastNoteIsShown() {
        val h = NoteHold()
        val t = h.play(0, 500) { 69.0 }
        h.play(t, 90) { 72.0 } // C5 straight after A4, no gap, only 90 ms
        assertEquals(72.0, h.release(), 1e-9)
    }

    @Test fun legatoSqueakAtTheEndKeepsTheNoteBeforeIt() {
        val h = NoteHold()
        val t = h.play(0, 500) { 69.0 }
        h.play(t, 40) { 88.0 } // squeak, no gap
        assertEquals(69.0, h.release(), 1e-9)
    }

    @Test fun wobbleNearANoteBoundaryStaysOneNote() {
        val h = NoteHold()
        // C#5 with a wide 5 Hz wobble of +-45 cents: still one note, held near its centre.
        h.play(0, 600) { i -> 73.0 + 0.45 * kotlin.math.sin(2 * Math.PI * 5.0 * i * frameNs / 1e9) }
        assertEquals(73.0, h.release(), 0.15)
    }

    @Test fun signedNeverShowsMinusZero() {
        assertEquals("0.0", Music.signed(-0.04, 1))
        assertEquals("0", Music.signed(-0.4))
        assertEquals("+3", Music.signed(2.6))
        assertEquals("-12.5", Music.signed(-12.5, 1))
    }
}
