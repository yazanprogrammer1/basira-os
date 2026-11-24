package com.basira.os.core.windows

import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.Pose
import com.basira.os.core.common.math.Ray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@JvmInline
value class WindowId(val value: Long)

/** What a window shows. Only web content or our own UI (CLAUDE.md hard constraint). */
sealed interface WindowContent {
    data class Web(val url: String, val desktopUserAgent: Boolean = false) : WindowContent
    data class System(val route: String) : WindowContent
}

data class SpatialWindow(
    val id: WindowId,
    val title: String,
    val content: WindowContent,
    val pose: Pose,
    val widthMeters: Float,
    val heightMeters: Float,
)

/** Result of a gaze or hand ray hitting a window, in window-local texture coordinates [0, 1]. */
data class WindowHit(val windowId: WindowId, val u: Float, val v: Float, val distanceMeters: Float)

/**
 * Owns the floating windows. Named "Spatial" to avoid clashing with `android.view.WindowManager`.
 */
interface SpatialWindowManager {
    val windows: StateFlow<List<SpatialWindow>>

    fun open(content: WindowContent, pose: Pose? = null): WindowId?

    fun close(id: WindowId)

    fun raycast(ray: Ray): WindowHit?
}

/** Phase 0 stub: holds nothing, opens nothing. Phase 2 hosts a real WebView panel. */
class StubSpatialWindowManager @Inject constructor(
    private val logger: Logger,
) : SpatialWindowManager {

    override val windows: StateFlow<List<SpatialWindow>> =
        MutableStateFlow(emptyList<SpatialWindow>()).asStateFlow()

    override fun open(content: WindowContent, pose: Pose?): WindowId? {
        logger.d(TAG, "open($content) ignored: window hosting arrives in phase 2")
        return null
    }

    override fun close(id: WindowId) = Unit

    override fun raycast(ray: Ray): WindowHit? = null

    private companion object {
        const val TAG = "Windows"
    }
}
