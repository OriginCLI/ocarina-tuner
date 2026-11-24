package app.ocarinatuner.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Position of a hole on a skin image, as fractions (r is a fraction of image height). */
@Serializable
data class HolePos(val x: Float, val y: Float, val r: Float)

@Serializable
data class Skin(
    val id: String,
    val name: String,
    val image: String,
    val aspect: Float,
    val group: String = "original",
    val order: Int = 999,
    val holes: Map<String, HolePos> = emptyMap(),
) {
    fun hole(h: Hole): HolePos? = holes[h.name]
}

@Serializable
data class SkinManifest(val skins: List<Skin>, val default: String? = null) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): SkinManifest = json.decodeFromString(serializer(), text)
    }
}

/**
 * Default 12-hole layout, expressed relative to the silhouette so it adapts to different
 * ocarina outlines. Mirrors tools/prepare_skins.py.
 *   u: position along the body (0 = left end, 1 = tip)
 *   d: distance below the top edge at that u, in image heights
 *   r: radius in image heights
 */
object HoleLayout {
    data class Spec(val u: Float, val d: Float, val r: Float)

    val specs: Map<Hole, Spec> = mapOf(
        Hole.L_PINKY to Spec(0.095f, 0.45f, 0.030f),
        Hole.L_RING to Spec(0.185f, 0.25f, 0.050f),
        Hole.L_MIDDLE to Spec(0.295f, 0.22f, 0.054f),
        Hole.L_INDEX to Spec(0.405f, 0.21f, 0.050f),
        Hole.L_SUB to Spec(0.300f, 0.42f, 0.024f),
        Hole.R_INDEX to Spec(0.520f, 0.20f, 0.048f),
        Hole.R_MIDDLE to Spec(0.630f, 0.185f, 0.044f),
        Hole.R_RING to Spec(0.735f, 0.165f, 0.038f),
        Hole.R_PINKY to Spec(0.835f, 0.145f, 0.030f),
        Hole.R_SUB to Spec(0.635f, 0.355f, 0.022f),
    )

    /**
     * Places holes on an already-cropped silhouette. [opaque] is row-major, true where the
     * ocarina is. Returns positions as fractions of the image.
     */
    fun autoPlace(opaque: BooleanArray, width: Int, height: Int): Map<String, HolePos> {
        val tops = IntArray(width) { height }
        for (x in 0 until width) {
            for (y in 0 until height) if (opaque[y * width + x]) { tops[x] = y; break }
        }
        return specs.entries.associate { (hole, s) ->
            val x = s.u * width
            val xi = x.toInt().coerceIn(0, width - 1)
            val lo = (xi - 6).coerceAtLeast(0)
            val hi = (xi + 7).coerceAtMost(width)
            val span = tops.copyOfRange(lo, hi).sorted()
            val top = span[span.size / 2].toFloat()
            val y = top + s.d * height
            hole.name to HolePos(x / width, (y / height).coerceIn(0f, 1f), s.r)
        }
    }
}
