package app.ocarinatuner.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.ocarinatuner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Check for update": reads the latest GitHub Release of [BuildConfig.UPDATE_REPO], compares
 * its build number with this app's versionCode, downloads the APK and hands it to the
 * system installer. Network is only used when the user taps the button.
 *
 * Releases are published by .github/workflows/build.yml with tags like "v0.1.42", where 42 is
 * the build number (= versionCode). Every build is signed with the same key, so an update
 * installs over the existing app and keeps its settings.
 */
class Updater(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val build: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long) : State
        data class Downloading(val progress: Float) : State
        data class Ready(val file: File) : State
        data object NeedsPermission : State
        data class Error(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private val source: String get() = BuildConfig.UPDATE_REPO
    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check() {
        state = State.Checking
        state = withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://api.github.com/repos/$source/releases/latest").openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                when (val code = conn.responseCode) {
                    200 -> {}
                    404 -> return@withContext State.Error(
                        "No releases found at $source. If that repository is private, the app " +
                            "can't see its releases; they need to be published somewhere public."
                    )
                    else -> return@withContext State.Error("GitHub answered $code. Try again later.")
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name")
                val build = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@withContext State.Error("Latest release tag '$tag' has no build number.")
                if (build <= currentBuild) return@withContext State.UpToDate(currentVersion)
                val assets = json.optJSONArray("assets")
                var url: String? = null
                var bytes = 0L
                for (i in 0 until (assets?.length() ?: 0)) {
                    val a = assets!!.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); bytes = a.optLong("size"); break
                    }
                }
                if (url == null) return@withContext State.Error("Release $tag has no APK attached.")
                State.Available(build, json.optString("name", tag), json.optString("body", ""), url, bytes)
            } catch (e: Exception) {
                State.Error("Couldn't reach GitHub (${e.javaClass.simpleName}). Check your connection.")
            }
        }
    }

    suspend fun download(a: State.Available) {
        state = State.Downloading(0f)
        state = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "ocarina-trainer-${a.build}.apk")
                var conn = URL(a.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: a.sizeBytes
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = done.toFloat() / total
                                if (p - lastShown > 0.02f) { lastShown = p; state = State.Downloading(p) }
                            }
                        }
                    }
                }
                State.Ready(out)
            } catch (e: Exception) {
                State.Error("Download failed (${e.javaClass.simpleName}). Try again.")
            }
        }
        if (state is State.Ready && !canInstall()) state = State.NeedsPermission
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for this app. */
    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Call when returning from the permission screen. */
    fun onResume() {
        if (state is State.NeedsPermission && canInstall()) {
            val f = File(context.cacheDir, "updates").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
            state = if (f != null) State.Ready(f) else State.Idle
        }
    }

    fun install(file: File) {
        if (!canInstall()) { state = State.NeedsPermission; return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun reset() { state = State.Idle }
}
