package com.basira.os

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.basira.os.calibration.CalibrationScreen
import com.basira.os.calibration.CalibrationViewModel
import com.basira.os.core.common.viewer.ScreenOrientation
import com.basira.os.core.render.calibration.CalibrationStore
import com.basira.os.core.tracking.ArCoreInstallResult
import com.basira.os.core.tracking.ArCoreSupport
import com.basira.os.diagnostics.DiagnosticsScreen
import com.basira.os.diagnostics.DiagnosticsViewModel
import com.basira.os.passthrough.PassthroughScreen
import com.basira.os.passthrough.PassthroughViewModel
import com.basira.os.ui.system.theme.BasiraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

enum class AppScreen { DIAGNOSTICS, PASSTHROUGH, CALIBRATION }

/**
 * Single entry Activity. Landscape, fullscreen, screen always on (the phone sits in a viewer),
 * display pinned to 60 Hz while rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var arCoreSupport: ArCoreSupport

    @Inject lateinit var calibrationStore: CalibrationStore

    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val passthroughViewModel: PassthroughViewModel by viewModels()
    private val calibrationViewModel: CalibrationViewModel by viewModels()

    private var screen by mutableStateOf(AppScreen.DIAGNOSTICS)

    /** Where to go once the camera permission is granted. */
    private var pendingScreen: AppScreen? = null

    /** Set when we launched the Play Store install flow, so onResume re-checks once. */
    private var arCoreInstallPending = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        diagnosticsViewModel.onCameraPermission(granted)
        val target = pendingScreen
        pendingScreen = null
        if (granted && target != null) {
            navigate(target)
        } else if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Permanently denied: the only way back is app settings.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        pinDisplayTo60Hz()
        followViewerOrientation()

        setContent {
            BasiraTheme {
                when (screen) {
                    AppScreen.DIAGNOSTICS -> {
                        val state = diagnosticsViewModel.state.collectAsStateWithLifecycle().value
                        DiagnosticsScreen(
                            state = state,
                            onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                            onInstallArCore = ::installArCore,
                            onRecheckArCore = diagnosticsViewModel::recheckArCore,
                            onCopyReport = { copyToClipboard(state.report()) },
                            onStartPassthrough = { navigate(AppScreen.PASSTHROUGH) },
                            onOpenCalibration = { navigate(AppScreen.CALIBRATION) },
                        )
                    }
                    AppScreen.PASSTHROUGH -> PassthroughScreen(passthroughViewModel)
                    AppScreen.CALIBRATION -> CalibrationScreen(calibrationViewModel)
                }
                BackHandler(enabled = screen != AppScreen.DIAGNOSTICS) { navigate(AppScreen.DIAGNOSTICS) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        diagnosticsViewModel.onCameraPermission(hasCameraPermission())
        if (arCoreInstallPending) {
            arCoreInstallPending = false
            diagnosticsViewModel.recheckArCore()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * The volume keys are the only buttons inside a viewer without one.
     * Passthrough: volume up opens or recenters the window; volume down toggles the overlay,
     * and a long press on it toggles camera reprojection (A/B tests).
     * Calibration: they tune the selected value.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screen == AppScreen.PASSTHROUGH && event.keyCode in VOLUME_KEYS) {
            handlePassthroughKey(event)
            return true
        }
        if (screen == AppScreen.CALIBRATION) {
            val steps = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> 1
                KeyEvent.KEYCODE_VOLUME_DOWN -> -1
                else -> 0
            }
            if (steps != 0) {
                if (event.action == KeyEvent.ACTION_DOWN) calibrationViewModel.adjust(steps)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Set when the current volume-down press already fired its long-press action. */
    private var volumeDownLongPressed = false

    private fun handlePassthroughKey(event: KeyEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP ->
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) passthroughViewModel.onViewerButton()
            KeyEvent.KEYCODE_VOLUME_DOWN -> when (event.action) {
                KeyEvent.ACTION_DOWN -> when {
                    event.repeatCount == 0 -> volumeDownLongPressed = false
                    event.isLongPress -> {
                        volumeDownLongPressed = true
                        passthroughViewModel.toggleReprojection()
                    }
                }
                KeyEvent.ACTION_UP -> if (!volumeDownLongPressed) passthroughViewModel.toggleOverlay()
            }
        }
    }

    private fun navigate(target: AppScreen) {
        if (target != AppScreen.DIAGNOSTICS && !hasCameraPermission()) {
            pendingScreen = target
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        screen = target
        val rendering = target != AppScreen.DIAGNOSTICS
        val power = getSystemService(PowerManager::class.java)
        if (power.isSustainedPerformanceModeSupported) window.setSustainedPerformanceMode(rendering)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** A 120 Hz panel doubles GPU work for nothing in a headset; pick the 60 Hz mode at the same resolution. */
    private fun pinDisplayTo60Hz() {
        val display = ContextCompat.getDisplayOrDefault(this)
        val current = display.mode
        val mode = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { abs(it.refreshRate - TARGET_REFRESH_HZ) }
            ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
    }

    /** The viewer decides which landscape: the back camera must end up uncovered. */
    private fun followViewerOrientation() {
        lifecycleScope.launch {
            calibrationStore.profile.collect { profile ->
                requestedOrientation = when (profile.orientation) {
                    ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ScreenOrientation.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }
        }
    }

    private fun installArCore() {
        when (arCoreSupport.requestInstall(this, userRequested = true)) {
            ArCoreInstallResult.INSTALL_REQUESTED -> arCoreInstallPending = true
            ArCoreInstallResult.INSTALLED,
            ArCoreInstallResult.DECLINED,
            ArCoreInstallResult.FAILED -> diagnosticsViewModel.recheckArCore()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Basira diagnostics", text))
        Toast.makeText(this, R.string.diag_report_copied, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TARGET_REFRESH_HZ = 60f
        val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
    }
}
