package com.basira.os.stereo

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.render.Compositor
import com.basira.os.core.render.pacing.VsyncRenderDriver
import com.basira.os.core.tracking.arcore.ArCoreSessionManager

/**
 * The stereo GL view. Owns the start/stop order that ARCore and GLSurfaceView need:
 * start = session.resume -> view.onResume -> vsync driver; stop = the reverse.
 */
@Composable
fun StereoSurface(
    compositor: Compositor,
    sessions: ArCoreSessionManager,
    frameSource: CameraFrameSource,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val display = remember(context) { ContextCompat.getDisplayOrDefault(context) }

    val view = remember(context) {
        val metrics = context.resources.displayMetrics
        compositor.setScreenDensity(metrics.xdpi, metrics.ydpi)
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            setRenderer(compositor.createRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    val driver = remember(view) { VsyncRenderDriver(view, compositor) { display.refreshRate } }

    DisposableEffect(lifecycleOwner, view) {
        var running = false
        fun start() {
            if (running) return
            running = true
            frameSource.setDisplayRotation(display.rotation)
            sessions.resume()
            view.onResume()
            driver.start()
        }
        fun stop() {
            if (!running) return
            running = false
            driver.stop()
            view.onPause()
            sessions.pause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * Lays the same content over each eye's half of the screen (left half = left eye regardless of RTL),
 * vertically near the lens centre so it is inside the visible field.
 */
@Composable
fun PerEyeOverlay(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = alignment,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(Modifier.fillMaxSize(), contentAlignment = alignment) { content() }
                    }
                }
            }
        }
    }
}
