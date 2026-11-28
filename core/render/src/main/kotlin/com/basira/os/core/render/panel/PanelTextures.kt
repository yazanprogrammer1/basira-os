package com.basira.os.core.render.panel

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.basira.os.core.render.gl.GlUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** One window's texture. Fields below the line are owned by the GL thread. */
internal class PanelEntry(
    val id: Int,
    val widthPx: Int,
    val heightPx: Int,
    val widthMeters: Float,
    val heightMeters: Float,
    val onSurface: (Surface) -> Unit,
) {
    val frameAvailable = AtomicBoolean(false)

    // ---- GL thread ----
    var generation = -1
    var texture = 0
    var surfaceTexture: SurfaceTexture? = null
    var surface: Surface? = null
    val texMatrix = FloatArray(16)
    var hasFrame = false
}

/**
 * Registry shared by the UI side ([com.basira.os.core.common.panel.PanelHost] calls) and the GL thread.
 * The GL side creates a texture + SurfaceTexture per panel for each GL context ([generation]) and
 * latches new content only when a frame actually arrived, so a static page costs nothing.
 */
internal class PanelTextures {

    private val entries = ConcurrentHashMap<Int, PanelEntry>()
    private val retired = ConcurrentLinkedQueue<PanelEntry>()
    private val main = Handler(Looper.getMainLooper())

    fun add(entry: PanelEntry) {
        entries.put(entry.id, entry)?.let(retired::add)
    }

    fun remove(id: Int) {
        entries.remove(id)?.let(retired::add)
    }

    val all: Collection<PanelEntry> get() = entries.values

    /** GL thread, once per frame: create textures for new panels / new context, latch new frames. */
    fun update(generation: Int) {
        while (true) {
            val old = retired.poll() ?: break
            releaseGl(old, deleteTexture = old.generation == generation)
        }
        for (entry in entries.values) {
            if (entry.generation != generation) realize(entry, generation)
            if (entry.frameAvailable.getAndSet(false)) {
                entry.surfaceTexture?.let {
                    it.updateTexImage()
                    it.getTransformMatrix(entry.texMatrix)
                    entry.hasFrame = true
                }
            }
        }
    }

    private fun realize(entry: PanelEntry, generation: Int) {
        // The previous context's texture died with it; only the Java objects need releasing.
        releaseGl(entry, deleteTexture = false)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        entry.texture = ids[0]
        GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, entry.texture)
        GLES30.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GlUtil.checkError("panel texture")
        val st = SurfaceTexture(entry.texture).apply {
            setDefaultBufferSize(entry.widthPx, entry.heightPx)
            setOnFrameAvailableListener { entry.frameAvailable.set(true) }
        }
        val surface = Surface(st)
        entry.surfaceTexture = st
        entry.surface = surface
        entry.hasFrame = false
        entry.generation = generation
        main.post { entry.onSurface(surface) }
    }

    private fun releaseGl(entry: PanelEntry, deleteTexture: Boolean) {
        entry.surface?.release()
        entry.surfaceTexture?.release()
        if (deleteTexture && entry.texture != 0) GLES30.glDeleteTextures(1, intArrayOf(entry.texture), 0)
        entry.surface = null
        entry.surfaceTexture = null
        entry.texture = 0
        entry.hasFrame = false
    }
}
