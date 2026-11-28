package com.basira.os.core.windows

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.Pose
import com.basira.os.core.common.math.Ray
import com.basira.os.core.common.panel.PanelHost
import com.basira.os.core.common.panel.WorldAnchors
import com.basira.os.core.windows.web.WebPanel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@JvmInline
value class WindowId(val value: Int)

/** What a window shows. Only web content or our own UI (CLAUDE.md hard constraint). */
sealed interface WindowContent {
    data class Web(val url: String, val desktopUserAgent: Boolean = false) : WindowContent
    data class System(val route: String) : WindowContent
}

data class SpatialWindow(
    val id: WindowId,
    val title: String,
    val content: WindowContent,
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

    /** Moves the window back in front of the viewer. */
    fun recenter(id: WindowId)

    fun close(id: WindowId)

    fun raycast(ray: Ray): WindowHit?
}

/**
 * Phase 2: web windows on private VirtualDisplays, textures owned by the renderer ([PanelHost]),
 * placement kept in the room by [WorldAnchors]. Gaze input arrives in phase 3.
 */
@Singleton
class WebSpatialWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val panelHost: PanelHost,
    private val anchors: WorldAnchors,
    private val logger: Logger,
) : SpatialWindowManager {

    private val mutableWindows = MutableStateFlow(emptyList<SpatialWindow>())
    override val windows: StateFlow<List<SpatialWindow>> = mutableWindows.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val panels = mutableMapOf<Int, WebPanel>()
    private var nextId = 1

    override fun open(content: WindowContent, pose: Pose?): WindowId? {
        val url = when (content) {
            is WindowContent.Web -> content.url
            is WindowContent.System -> return null // system UI windows arrive in phase 4
        }
        val id = nextId++
        val panel = WebPanel(context, id, PANEL_WIDTH_PX, PANEL_HEIGHT_PX, PANEL_DENSITY_DPI, url, logger)
        panels[id] = panel
        panelHost.createPanel(id, PANEL_WIDTH_PX, PANEL_HEIGHT_PX, PANEL_WIDTH_M, PANEL_HEIGHT_M) { surface ->
            main.post { panels[id]?.attach(surface) }
        }
        anchors.placeInFront(id, DEFAULT_DISTANCE_M)
        mutableWindows.update { it + SpatialWindow(WindowId(id), url, content, PANEL_WIDTH_M, PANEL_HEIGHT_M) }
        return WindowId(id)
    }

    override fun recenter(id: WindowId) {
        anchors.placeInFront(id.value, DEFAULT_DISTANCE_M)
    }

    override fun close(id: WindowId) {
        panels.remove(id.value)?.release()
        panelHost.releasePanel(id.value)
        anchors.remove(id.value)
        mutableWindows.update { list -> list.filterNot { it.id == id } }
    }

    override fun raycast(ray: Ray): WindowHit? = null // phase 3

    companion object {
        /**
         * 1280x800 px at 240 dpi = an 853 CSS-px-wide page; on 1.4 m at 1.5 m (about 50 degrees),
         * 16 CSS px body text is about 1 degree tall: readable through phone viewer lenses.
         */
        const val PANEL_WIDTH_PX = 1280
        const val PANEL_HEIGHT_PX = 800
        const val PANEL_DENSITY_DPI = 240
        const val PANEL_WIDTH_M = 1.4f
        const val PANEL_HEIGHT_M = 0.875f
        const val DEFAULT_DISTANCE_M = 1.5f

        const val WELCOME_URL = "file:///android_asset/panel/welcome.html"
    }
}
