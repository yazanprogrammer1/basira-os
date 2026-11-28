package com.basira.os.core.common.panel

import android.view.Surface

/**
 * Implemented by `:core:render`: owns the GL textures that window content draws into.
 * `:core:windows` asks for a panel and draws its WebView into the returned [Surface];
 * the renderer only ever sees a texture, a size in metres and a pose.
 */
interface PanelHost {
    /**
     * Requests a panel texture. [onSurface] runs on the main thread, possibly more than once:
     * the GL context can be recreated, and the content must then switch to the new Surface.
     */
    fun createPanel(id: Int, widthPx: Int, heightPx: Int, widthMeters: Float, heightMeters: Float, onSurface: (Surface) -> Unit)

    fun releasePanel(id: Int)
}

/**
 * Implemented by `:core:tracking`: keeps window placements fixed in the room.
 * Poses are packed (tx, ty, tz, qx, qy, qz, qw), world_from_panel, panel facing +z.
 */
interface WorldAnchors {
    /** Any thread. Places (or re-places) [id] [distanceMeters] in front of the viewer at eye height. */
    fun placeInFront(id: Int, distanceMeters: Float)

    fun remove(id: Int)

    /** GL thread. False until the anchor exists (e.g. no 6DoF tracking yet): draw head-locked instead. */
    fun pose(id: Int, out: FloatArray): Boolean
}
