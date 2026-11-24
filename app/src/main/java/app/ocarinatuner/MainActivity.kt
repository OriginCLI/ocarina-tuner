package app.ocarinatuner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.ocarinatuner.audio.NativeEngine
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.core.Music
import app.ocarinatuner.data.Updater
import app.ocarinatuner.ui.OcarinaApp
import app.ocarinatuner.ui.theme.OcarinaTheme
import java.util.Locale
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    private lateinit var controller: TunerController
    private lateinit var updater: Updater
    private var hasMic by mutableStateOf(false)
    private var toneReceiver: BroadcastReceiver? = null

    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (granted) controller.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        // Keep the screen on while the tuner is open (it is always listening while visible).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        controller = TunerController(applicationContext)
        updater = Updater(applicationContext)
        if (BuildConfig.DEBUG) registerTestTone()
        hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) micRequest.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            OcarinaTheme {
                OcarinaApp(controller, updater, hasMic, requestMic = { micRequest.launch(Manifest.permission.RECORD_AUDIO) })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasMic) controller.start()
    }

    override fun onResume() {
        super.onResume()
        updater.onResume()
    }

    override fun onStop() {
        controller.stop()
        super.onStop()
    }

    override fun onDestroy() {
        toneReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    /**
     * Debug builds only: lets a test (e.g. on an emulator, which has no real mic) play a tone
     * into the engine.
     *   adb shell am broadcast -a app.ocarinatuner.TEST_TONE -p app.ocarinatuner --es note A4
     * Optional extras: --ef cents -12, --ef db -20 (RMS dBFS, default -20), --ef hz 452.
     * --es note off stops the tone.
     */
    private fun registerTestTone() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                val note = i.getStringExtra("note")
                val db = i.getFloatExtra("db", -20f)
                val cents = i.getFloatExtra("cents", 0f)
                val base: Double? = when {
                    i.hasExtra("hz") -> i.getFloatExtra("hz", 0f).toDouble()
                    note == null || note.equals("off", ignoreCase = true) -> null
                    else -> Music.parse(note)?.let { Music.hzOf(it.toDouble(), controller.a4.toDouble()) }
                        ?: run { Log.w(TONE_TAG, "unknown note '$note'"); return }
                }
                val hz = base?.let { it * 2.0.pow(cents / 1200.0) }?.toFloat() ?: 0f
                NativeEngine.setTestTone(hz, db)
                Log.i(TONE_TAG, if (hz > 0) String.format(Locale.US, "tone %s %.2f Hz (%+.1f cents) at %.0f dB", note ?: "", hz, cents, db) else "tone off")
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter("app.ocarinatuner.TEST_TONE"), ContextCompat.RECEIVER_EXPORTED)
        toneReceiver = r
    }

    /** Ask for the display's fastest mode at the current resolution (e.g. 120 Hz). */
    private fun requestHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    private companion object { const val TONE_TAG = "OcarinaTone" }
}
