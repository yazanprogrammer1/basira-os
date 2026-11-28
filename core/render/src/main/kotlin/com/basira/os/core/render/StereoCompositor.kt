package com.basira.os.core.render

import android.opengl.GLSurfaceView
import android.view.Surface
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.coroutines.ApplicationScope
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.panel.PanelHost
import com.basira.os.core.common.panel.WorldAnchors
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.calibration.CalibrationStore
import com.basira.os.core.render.panel.PanelEntry
import com.basira.os.core.render.panel.PanelTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Thread-safe state shared between the UI (setters) and the GL thread ([StereoRenderer]). */
@Singleton
class StereoCompositor @Inject constructor(
    private val frameSource: CameraFrameSource,
    private val anchors: WorldAnchors,
    calibrationStore: CalibrationStore,
    @ApplicationScope scope: CoroutineScope,
    private val logger: Logger,
) : Compositor, PanelHost {

    private val stats = MutableStateFlow(FrameStats())
    override val frameStats: StateFlow<FrameStats> = stats.asStateFlow()

    @Volatile override var qualityLevel: QualityLevel = QualityLevel.NORMAL
        private set

    @Volatile override var reprojectionEnabled: Boolean = true
        private set

    @Volatile internal var renderMode: RenderMode = RenderMode.PASSTHROUGH
        private set

    @Volatile private var storedProfile: ViewerProfile = ViewerProfile.DEFAULT

    @Volatile private var previewProfile: ViewerProfile? = null

    @Volatile internal var xdpi: Float = DEFAULT_DPI
        private set

    @Volatile internal var ydpi: Float = DEFAULT_DPI
        private set

    @Volatile private var statsResetRequested = false

    init {
        scope.launch { calibrationStore.profile.collect { storedProfile = it } }
    }

    override fun setQualityLevel(level: QualityLevel) {
        if (level != qualityLevel) logger.d(TAG, "Quality $qualityLevel -> $level")
        qualityLevel = level
    }

    override fun setRenderMode(mode: RenderMode) {
        renderMode = mode
    }

    override fun setReprojectionEnabled(enabled: Boolean) {
        reprojectionEnabled = enabled
    }

    override fun setViewerProfilePreview(profile: ViewerProfile?) {
        previewProfile = profile
    }

    override fun setScreenDensity(xdpi: Float, ydpi: Float) {
        if (xdpi > 0f) this.xdpi = xdpi
        if (ydpi > 0f) this.ydpi = ydpi
    }

    override fun onRenderingResumed() {
        statsResetRequested = true
    }

    override fun createRenderer(): GLSurfaceView.Renderer = StereoRenderer(this, frameSource, anchors, logger)

    internal val panels = PanelTextures()

    override fun createPanel(
        id: Int,
        widthPx: Int,
        heightPx: Int,
        widthMeters: Float,
        heightMeters: Float,
        onSurface: (Surface) -> Unit,
    ) {
        panels.add(PanelEntry(id, widthPx, heightPx, widthMeters, heightMeters, onSurface))
    }

    override fun releasePanel(id: Int) = panels.remove(id)

    internal fun currentProfile(): ViewerProfile = previewProfile ?: storedProfile

    internal fun consumeStatsReset(): Boolean {
        if (!statsResetRequested) return false
        statsResetRequested = false
        return true
    }

    internal fun publish(snapshot: FrameStats) {
        stats.value = snapshot
    }

    private companion object {
        const val TAG = "Compositor"
        const val DEFAULT_DPI = 400f
    }
}
