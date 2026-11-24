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
 * Default 12-hole layout, relative to the silhouette so it adapts to different outlines.
 * Mirrors tools/prepare_skins.py. Measured from a photo of an STL 12-hole: left-hand holes
 * along the far (upper) side of the wide end, right-hand holes along the near (lower) side
 * toward the tip, subholes between the rows.
 *   u: position along the body (0 = left end, 1 = tip)
 *   v: position across the body (0 = far/top edge, 1 = near/bottom edge, mouthpiece ignored)
 *   r: radius in image heights
 */
object HoleLayout {
    data class Spec(val u: Float, val v: Float, val r: Float)

    val specs: Map<Hole, Spec> = mapOf(
        Hole.L_PINKY to Spec(0.075f, 0.36f, 0.040f),
        Hole.L_RING to Spec(0.170f, 0.32f, 0.054f),
        Hole.L_MIDDLE to Spec(0.290f, 0.33f, 0.056f),
        Hole.L_INDEX to Spec(0.420f, 0.24f, 0.054f),
        Hole.L_SUB to Spec(0.170f, 0.53f, 0.024f),
        Hole.R_INDEX to Spec(0.455f, 0.78f, 0.050f),
        Hole.R_MIDDLE to Spec(0.585f, 0.70f, 0.036f),
        Hole.R_RING to Spec(0.710f, 0.64f, 0.042f),
        Hole.R_PINKY to Spec(0.845f, 0.60f, 0.030f),
        Hole.R_SUB to Spec(0.580f, 0.47f, 0.022f),
    )

    /**
     * Top and bottom edge of the body per column. The mouthpiece (columns reaching below 80%
     * of the height, plus a margin for its flare) is bridged with a straight line.
     */
    fun bodyEdges(opaque: BooleanArray, width: Int, height: Int): Pair<FloatArray, FloatArray> {
        val top = FloatArray(width) { Float.NaN }
        val bot = FloatArray(width) { Float.NaN }
        for (x in 0 until width) {
            var first = -1
            var last = -1
            for (y in 0 until height) if (opaque[y * width + x]) { if (first < 0) first = y; last = y }
            if (first >= 0) { top[x] = first.toFloat(); bot[x] = last.toFloat() }
        }
        val deep = (0 until width).filter { !bot[it].isNaN() && bot[it] > 0.8f * height }
        if (deep.isNotEmpty()) {
            val a = (deep.first() - 0.06f * width).toInt().coerceAtLeast(0)
            val b = (deep.last() + 0.08f * width).toInt().coerceAtMost(width - 1)
            val ya = if (a > 0) bot[a] else (0 until deep.first().coerceAtLeast(1)).map { bot[it] }.filter { !it.isNaN() }.minOrNull() ?: bot[a]
            val yb = bot[b]
            for (x in a..b) {
                val t = (x - a).toFloat() / (b - a).coerceAtLeast(1)
                bot[x] = ya + (yb - ya) * t
            }
        }
        return top to bot
    }

    /**
     * Places holes on an already-cropped silhouette. [opaque] is row-major, true where the
     * ocarina is. Returns positions as fractions of the image.
     */
    fun autoPlace(opaque: BooleanArray, width: Int, height: Int): Map<String, HolePos> {
        val (top, bot) = bodyEdges(opaque, width, height)
        fun medianAround(arr: FloatArray, xi: Int): Float {
            val vals = (maxOf(0, xi - 6) until minOf(width, xi + 7)).map { arr[it] }.filter { !it.isNaN() }.sorted()
            return if (vals.isEmpty()) height / 2f else vals[vals.size / 2]
        }
        return specs.entries.associate { (hole, s) ->
            val x = s.u * width
            val xi = x.toInt().coerceIn(0, width - 1)
            val t = medianAround(top, xi)
            val b = medianAround(bot, xi)
            val y = t + s.v * (b - t)
            hole.name to HolePos(x / width, (y / height).coerceIn(0f, 1f), s.r)
        }
    }
}
