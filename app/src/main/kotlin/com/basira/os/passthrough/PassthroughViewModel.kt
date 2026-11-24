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
import com.basira.os.perf.PerfRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
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
)

@HiltViewModel
class PassthroughViewModel @Inject constructor(
    val compositor: Compositor,
    val sessions: ArCoreSessionManager,
    val frameSource: CameraFrameSource,
    thermalSource: ThermalStatusSource,
    private val governor: ThermalGovernor,
    private val perfRecorder: PerfRecorder,
) : ViewModel() {

    val overlay: StateFlow<OverlayState> = combine(
        compositor.frameStats,
        thermalSource.snapshots.onStart<ThermalSnapshot?> { emit(null) },
        governor.qualityLevel,
        sessions.state,
    ) { stats, thermal, quality, session -> OverlayState(stats, thermal, quality, session) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlayState())

    /** Runs while the passthrough screen is on screen: thermal budget -> renderer, plus the perf CSV. */
    suspend fun runWhileVisible() = coroutineScope {
        compositor.setRenderMode(RenderMode.PASSTHROUGH)
        compositor.setViewerProfilePreview(null)
        launch { governor.qualityLevel.collect(compositor::setQualityLevel) }
        launch { perfRecorder.record() }
    }
}
