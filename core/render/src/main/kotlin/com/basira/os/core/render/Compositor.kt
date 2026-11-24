package com.basira.os.core.render

import com.basira.os.core.common.quality.QualityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Rolling frame statistics, read by the developer overlay. */
data class FrameStats(
    val fps: Float = 0f,
    val frameTimeMsAvg: Float = 0f,
    val frameTimeMsP95: Float = 0f,
    val droppedFrames: Long = 0L,
)

/**
 * Draws both eyes every frame. It knows nothing about ARCore or WebView:
 * it receives textures and poses only (see CLAUDE.md, module rules).
 */
interface Compositor {
    val frameStats: StateFlow<FrameStats>

    fun setQualityLevel(level: QualityLevel)
}

/** Phase 0 stub. Phase 1 adds the GL thread, camera quad, stereo split and distortion pass. */
class StubCompositor @Inject constructor() : Compositor {
    override val frameStats: StateFlow<FrameStats> = MutableStateFlow(FrameStats()).asStateFlow()

    override fun setQualityLevel(level: QualityLevel) = Unit
}
