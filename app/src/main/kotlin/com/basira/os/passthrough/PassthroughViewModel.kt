package com.basira.os.passthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.render.Compositor
import com.basira.os.core.render.FrameStats
import com.basira.os.core.render.RenderMode
import com.basira.os.core.thermal.ThermalGovernor
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatusSource
import com.basira.os.core.tracking.arcore.ArCoreSessionManager
import com.basira.os.core.tracking.arcore.ArSessionState
import com.basira.os.core.windows.SpatialWindowManager
import com.basira.os.core.windows.WebSpatialWindowManager
import com.basira.os.core.windows.WindowContent
import com.basira.os.perf.PerfRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverlayState(
    val stats: FrameStats = FrameStats(),
    val thermal: ThermalSnapshot? = null,
    val quality: QualityLevel = QualityLevel.NORMAL,
    val session: ArSessionState = ArSessionState.Idle,
    val reprojectionEnabled: Boolean = true,
    val visible: Boolean = true,
    val windowCount: Int = 0,
)

@HiltViewModel
class PassthroughViewModel @Inject constructor(
    val compositor: Compositor,
    val sessions: ArCoreSessionManager,
    val frameSource: CameraFrameSource,
    thermalSource: ThermalStatusSource,
    private val governor: ThermalGovernor,
    private val perfRecorder: PerfRecorder,
    private val windowManager: SpatialWindowManager,
) : ViewModel() {

    private val reprojection = MutableStateFlow(compositor.reprojectionEnabled)
    private val overlayVisible = MutableStateFlow(true)

    val overlay: StateFlow<OverlayState> = combine(
        compositor.frameStats,
        thermalSource.snapshots.onStart<ThermalSnapshot?> { emit(null) },
        governor.qualityLevel,
        sessions.state,
        combine(reprojection, overlayVisible, windowManager.windows) { reproj, visible, windows -> Triple(reproj, visible, windows.size) },
    ) { stats, thermal, quality, session, (reproj, visible, windowCount) ->
        OverlayState(stats, thermal, quality, session, reproj, visible, windowCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlayState())

    /** Volume up inside the viewer: A/B the camera reprojection. */
    fun toggleReprojection() {
        reprojection.value = !reprojection.value
        compositor.setReprojectionEnabled(reprojection.value)
    }

    /**
     * A tap or the viewer button: opens the first window, then brings it back in front of you.
     * (Gaze + pinch arrive in phase 3.)
     */
    fun onViewerButton() {
        val first = windowManager.windows.value.firstOrNull()
        if (first == null) {
            windowManager.open(WindowContent.Web(WebSpatialWindowManager.WELCOME_URL))
        } else {
            windowManager.recenter(first.id)
        }
    }

    /** Volume down: hide/show the developer overlay. */
    fun toggleOverlay() {
        overlayVisible.value = !overlayVisible.value
    }

    /** Runs while the passthrough screen is on screen: thermal budget -> renderer, plus the perf CSV. */
    suspend fun runWhileVisible() = coroutineScope {
        compositor.setRenderMode(RenderMode.PASSTHROUGH)
        compositor.setViewerProfilePreview(null)
        launch { governor.qualityLevel.collect(compositor::setQualityLevel) }
        launch { perfRecorder.record() }
    }
}
