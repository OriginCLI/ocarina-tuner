package app.ocarinatuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.ocarinatuner.audio.TunerController
import app.ocarinatuner.data.Updater
import app.ocarinatuner.ui.OcarinaApp
import app.ocarinatuner.ui.theme.OcarinaTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: TunerController
    private lateinit var updater: Updater
    private var hasMic by mutableStateOf(false)

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
}
