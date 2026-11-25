package app.ocarinatuner.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.ocarinatuner.core.HoleLayout
import app.ocarinatuner.core.HolePos
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.ocarinatuner.core.FingeringChart
import app.ocarinatuner.core.Skin
import app.ocarinatuner.core.SkinManifest
import java.io.File

/** Loads fingerings and skins from assets (or the user's imported fingering file). */
class Assets(private val context: Context) {
    private val customFingerings get() = File(context.filesDir, "fingerings.json")

    val hasCustomFingerings: Boolean get() = customFingerings.exists()

    fun fingeringText(): String =
        if (customFingerings.exists()) customFingerings.readText()
        else context.assets.open("fingerings.json").bufferedReader().use { it.readText() }

    fun loadFingerings(): FingeringChart = try {
        FingeringChart.parse(fingeringText())
    } catch (e: IllegalArgumentException) {
        // A broken custom file must never brick the app: fall back to the built-in chart.
        FingeringChart.parse(context.assets.open("fingerings.json").bufferedReader().use { it.readText() })
    }

    /** Validates and stores an imported fingering file. Returns an error message or null. */
    fun importFingerings(text: String): String? = try {
        FingeringChart.parse(text)
        customFingerings.writeText(text)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "Invalid fingering file"
    }

    fun resetFingerings() { customFingerings.delete() }

    /** A skin plus where its image lives: an asset folder, or a user folder on the device. */
    data class SkinEntry(
        val skin: Skin,
        val dir: String,
        val user: Boolean = false,
        /** Hole positions before any adjustment in the editor ("Reset" goes back to these). */
        val defaultHoles: Map<String, HolePos> = skin.holes,
    ) {
        /** Unique per skin. Bundled skins all share one asset folder, so [dir] alone is not. */
        val key: String get() = "$dir/${skin.id}"
    }

    private val holeOverrideRoot get() = File(context.filesDir, "skin_holes").apply { mkdirs() }
    private val holesSerializer = MapSerializer(String.serializer(), HolePos.serializer())

    private val userSkinRoot get() = File(context.filesDir, "skins").apply { mkdirs() }

    fun loadSkins(): Pair<List<SkinEntry>, String?> {
        val out = mutableListOf<SkinEntry>()
        var default: String? = null
        try {
            val m = SkinManifest.parse(context.assets.open("skins/skins.json").bufferedReader().use { it.readText() })
            default = m.default
            m.skins.sortedBy { it.order }.forEach { s ->
                // Hole positions the user adjusted for a bundled skin are kept on the device.
                val o = File(holeOverrideRoot, "${s.id}.json")
                val holes = if (o.exists()) try { skinJson.decodeFromString(holesSerializer, o.readText()) } catch (e: Exception) { null } else null
                out += SkinEntry(if (holes != null) s.copy(holes = holes) else s, "skins", defaultHoles = s.holes)
            }
        } catch (e: Exception) { /* no bundled skins */ }
        userSkinRoot.listFiles()?.sortedBy { it.lastModified() }?.forEach { dir ->
            val f = File(dir, "skin.json")
            if (f.exists()) try {
                val skin = skinJson.decodeFromString(Skin.serializer(), f.readText())
                // The auto-placed holes from import, so Reset still works after edits.
                val auto = File(dir, AUTO_HOLES).takeIf { it.exists() }?.let {
                    try { skinJson.decodeFromString(holesSerializer, it.readText()) } catch (e: Exception) { null }
                }
                out += SkinEntry(skin, dir.absolutePath, user = true, defaultHoles = auto ?: skin.holes)
            } catch (e: Exception) { /* skip broken skin */ }
        }
        return out to default
    }

    private val skinJson = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val AUTO_HOLES = "auto_holes.json"

    /**
     * Imports a hole-free ocarina picture (PNG/WebP with transparency works best): crops it to
     * the body, scales it to 1200 px wide, and places the holes automatically. The user can
     * then fine-tune hole positions in the editor. Stays on this device.
     */
    fun importSkin(uri: Uri, name: String): SkinEntry {
        // Read the size first and subsample huge pictures so decoding can't exhaust memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Couldn't read that image")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1200 && bounds.outHeight / (sample * 2) >= 300) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Couldn't read that image")
        val cropped = cropToOpaque(src)
        val scale = 1200f / cropped.width
        val img = if (cropped.width > 1200) Bitmap.createScaledBitmap(cropped, 1200, (cropped.height * scale).toInt().coerceAtLeast(1), true) else cropped
        val w = img.width; val h = img.height
        val px = IntArray(w * h)
        img.getPixels(px, 0, w, 0, 0, w, h)
        val hasAlpha = px.any { (it ushr 24) < 250 }
        // Without transparency, treat pixels that differ from the corner colour as the body.
        val bg = px[0]
        val opaque = BooleanArray(w * h) { i ->
            if (hasAlpha) (px[i] ushr 24) > 128 else colorDistance(px[i], bg) > 40
        }
        val holes = HoleLayout.autoPlace(opaque, w, h)
        val id = "user_" + System.currentTimeMillis()
        val dir = File(userSkinRoot, id).apply { mkdirs() }
        File(dir, "image.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val skin = Skin(id, name.ifBlank { "My ocarina" }, "image.png", w.toFloat() / h, "user", 1000, holes)
        File(dir, "skin.json").writeText(skinJson.encodeToString(Skin.serializer(), skin))
        File(dir, AUTO_HOLES).writeText(skinJson.encodeToString(holesSerializer, holes))
        return SkinEntry(skin, dir.absolutePath, user = true)
    }

    /** Saves edited hole positions: into the skin itself for imported skins, as an override otherwise. */
    fun saveSkinHoles(e: SkinEntry) {
        if (e.user) File(e.dir, "skin.json").writeText(skinJson.encodeToString(Skin.serializer(), e.skin))
        else File(holeOverrideRoot, "${e.skin.id}.json").writeText(skinJson.encodeToString(holesSerializer, e.skin.holes))
    }

    fun clearHoleOverride(e: SkinEntry) { if (!e.user) File(holeOverrideRoot, "${e.skin.id}.json").delete() }

    fun deleteUserSkin(e: SkinEntry) {
        if (e.user) File(e.dir).deleteRecursively()
        synchronized(bitmapCache) { bitmapCache.keys.removeAll { it.startsWith(e.dir) } }
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))

    private fun cropToOpaque(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 32) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return b
        return Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private val bitmapCache = HashMap<String, ImageBitmap>()

    fun skinBitmap(e: SkinEntry, maxWidth: Int = 1200): ImageBitmap? = synchronized(bitmapCache) {
        val key = "${e.dir}/${e.skin.image}@$maxWidth"
        bitmapCache[key]?.let { return it }
        val opts = BitmapFactory.Options().apply { inSampleSize = if (maxWidth <= 400) 2 else 1 }
        val bmp = try {
            if (e.user) BitmapFactory.decodeFile(File(e.dir, e.skin.image).absolutePath, opts)
            else context.assets.open("${e.dir}/${e.skin.image}").use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (ex: Exception) { null } ?: return null
        return bmp.asImageBitmap().also { bitmapCache[key] = it }
    }
}
