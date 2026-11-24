package app.ocarinatuner.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FingeringsTest {
    private fun shipped(): String {
        // Tests run with the module dir as working dir.
        val f = listOf("../app/src/main/assets/fingerings.json", "app/src/main/assets/fingerings.json")
            .map(::File).first { it.exists() }
        return f.readText()
    }

    @Test fun shippedChartMatchesSpec() {
        val c = FingeringChart.parse(shipped())
        assertEquals(13, c.fingerings.size)
        assertEquals(69, c.lowest); assertEquals(89, c.highest)
        assertTrue(c.forMidi(69)!!.open.isEmpty(), "A4 is all covered")
        assertEquals(setOf(Hole.L_SUB), c.forMidi(71)!!.open)
        assertEquals(setOf(Hole.L_SUB, Hole.R_SUB), c.forMidi(72)!!.open)
        // Each note from C5 up lifts exactly one more finger, in the order given.
        val order = listOf(Hole.R_PINKY, Hole.R_RING, Hole.R_MIDDLE, Hole.R_INDEX, Hole.L_RING, Hole.L_MIDDLE,
            Hole.L_INDEX, Hole.L_THUMB, Hole.R_THUMB, Hole.L_PINKY)
        val naturals = c.naturals
        assertEquals(listOf(69, 71, 72, 74, 76, 77, 79, 81, 83, 84, 86, 88, 89), naturals)
        for (i in order.indices) {
            val prev = c.forMidi(naturals[i + 2])!!.open
            val cur = c.forMidi(naturals[i + 3])!!.open
            assertEquals(prev + order[i], cur, "step to ${Music.name(naturals[i + 3])}")
        }
        assertEquals(Hole.entries.toSet(), c.forMidi(89)!!.open, "F6 is all open")
    }

    @Test fun stepper() {
        val c = FingeringChart.parse(shipped())
        assertEquals(71, c.step(69, 1))
        assertEquals(69, c.step(69, -1))
        assertEquals(89, c.step(89, 1))
        assertEquals(74, c.step(73, 0)) // C#5 is not in the chart, snaps to D5
    }

    @Test fun sharpsCanBeAdded() {
        val c = FingeringChart.parse("""{"notes":[{"note":"A4"},{"note":"A#4","open":["L_SUB","R_PINKY"]}]}""")
        assertEquals(setOf(Hole.L_SUB, Hole.R_PINKY), c.forMidi(70)!!.open)
        assertEquals(listOf(69), c.naturals)
    }

    @Test fun badFilesGiveReadableErrors() {
        assertFailsWith<IllegalArgumentException> { FingeringChart.parse("""{"notes":[{"note":"Q4"}]}""") }
        assertFailsWith<IllegalArgumentException> { FingeringChart.parse("""{"notes":[{"note":"A4","open":["NOSE"]}]}""") }
        assertFailsWith<IllegalArgumentException> { FingeringChart.parse("not json") }
        assertFailsWith<IllegalArgumentException> { FingeringChart.parse("""{"notes":[{"note":"A4"},{"note":"A4"}]}""") }
    }

    @Test fun shippedSkinsHaveAllTopHoles() {
        for (path in listOf("../app/src/main/assets/skins/skins.json", "../extra-skins/skins.json")) {
            val m = SkinManifest.parse(File(path).readText())
            assertTrue(m.skins.isNotEmpty())
            for (s in m.skins) for (h in Hole.entries.filter { !it.onBack }) {
                val p = s.hole(h)
                kotlin.test.assertNotNull(p, "${s.id} missing $h")
                assertTrue(p.x in 0f..1f && p.y in 0f..1f && p.r > 0f, "${s.id} $h out of range")
            }
        }
    }
}

class AutoPlaceTest {
    @Test fun followsTheSilhouette() {
        // A wedge: top edge slopes down from y=0 at the left to y=40 at the right.
        val w = 200; val h = 100
        val mask = BooleanArray(w * h) { i -> val x = i % w; val y = i / w; y >= x / 5 && y < 90 }
        val holes = HoleLayout.autoPlace(mask, w, h)
        assertEquals(10, holes.size)
        val li = holes.getValue("L_INDEX"); val rp = holes.getValue("R_PINKY")
        assertTrue(rp.y > li.y, "holes follow the falling top edge")
        assertEquals(0.405f, li.x, 0.001f)
        val expectedY = ((0.405f * w).toInt() / 5 + 0.21f * h) / h
        assertEquals(expectedY, li.y, 0.02f)
    }
}
