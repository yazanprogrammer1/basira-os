package com.basira.os

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basira.os.core.tracking.ArCoreInstallResult
import com.basira.os.core.tracking.ArCoreSupport
import com.basira.os.diagnostics.DiagnosticsScreen
import com.basira.os.diagnostics.DiagnosticsViewModel
import com.basira.os.ui.system.theme.BasiraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single entry Activity. Landscape, fullscreen, screen always on (the phone sits in a viewer).
 * Phase 0 shows the diagnostics screen; phase 1 adds the stereo GL view behind a toggle.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var arCoreSupport: ArCoreSupport

    private val viewModel: DiagnosticsViewModel by viewModels()

    /** Set when we launched the Play Store install flow, so onResume re-checks once. */
    private var arCoreInstallPending = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCameraPermission(granted)
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
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

        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            BasiraTheme {
                DiagnosticsScreen(
                    state = state,
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onInstallArCore = ::installArCore,
                    onRecheckArCore = viewModel::recheckArCore,
                    onCopyReport = { copyToClipboard(state.report()) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onCameraPermission(
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
        if (arCoreInstallPending) {
            arCoreInstallPending = false
            viewModel.recheckArCore()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun installArCore() {
        when (arCoreSupport.requestInstall(this, userRequested = true)) {
            ArCoreInstallResult.INSTALL_REQUESTED -> arCoreInstallPending = true
            ArCoreInstallResult.INSTALLED,
            ArCoreInstallResult.DECLINED,
            ArCoreInstallResult.FAILED -> viewModel.recheckArCore()
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
}
