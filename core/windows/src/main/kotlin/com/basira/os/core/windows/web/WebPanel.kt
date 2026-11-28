package com.basira.os.core.windows.web

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.view.Surface
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.basira.os.core.common.logging.Logger

/**
 * A WebView that renders into a Surface owned by the renderer, through our own private VirtualDisplay
 * (no permission needed: it only shows this app's content). Main thread only.
 */
internal class WebPanel(
    private val context: Context,
    private val id: Int,
    private val widthPx: Int,
    private val heightPx: Int,
    private val densityDpi: Int,
    private val pageUrl: String,
    private val logger: Logger,
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    var webView: WebView? = null
        private set

    /** First call creates the display and page; later calls (new GL context) only swap the Surface. */
    fun attach(surface: Surface) {
        val existing = virtualDisplay
        if (existing != null) {
            existing.surface = surface
            return
        }
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager.createVirtualDisplay(
            "basira-panel-$id",
            widthPx,
            heightPx,
            densityDpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        virtualDisplay = display
        presentation = PanelPresentation(context, display).also { it.show() }
        logger.d(TAG, "Panel $id: ${widthPx}x$heightPx @ $densityDpi dpi -> $pageUrl")
    }

    fun release() {
        presentation?.dismiss()
        presentation = null
        webView?.destroy()
        webView = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private inner class PanelPresentation(outer: Context, display: VirtualDisplay) :
        Presentation(outer, display.display) {

        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val view = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.rgb(14, 18, 16))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(pageUrl)
            }
            webView = view
            setContentView(view)
        }
    }

    private companion object {
        const val TAG = "WebPanel"
    }
}
