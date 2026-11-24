package com.basira.os.core.tracking.arcore

import android.view.Surface
import com.basira.os.core.common.camera.CameraFrame
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.logging.Logger
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Camera frames from ARCore for the renderer. All methods except [setDisplayRotation] run on the GL thread.
 *
 * Display geometry is set to the camera texture's own aspect ratio (not the screen's), so the
 * NDC -> texture transform covers the full image without cropping; the renderer then draws it at
 * its true angular size.
 */
@Singleton
class ArCoreCameraFrameSource @Inject constructor(
    private val sessions: ArCoreSessionManager,
    private val logger: Logger,
) : CameraFrameSource {

    private val frame = CameraFrame()
    private var cameraTextureId = -1
    private var attachedSession: Session? = null

    @Volatile private var displayRotation = Surface.ROTATION_90
    private var appliedRotation = -1

    override fun onGlSurfaceCreated(cameraTextureId: Int) {
        this.cameraTextureId = cameraTextureId
        attachedSession = null // texture names belong to the new context
    }

    override fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
    }

    override fun update(): CameraFrame? {
        val session = sessions.session ?: return null
        if (!sessions.isResumed || cameraTextureId < 0) return null

        if (attachedSession !== session) {
            session.setCameraTextureName(cameraTextureId)
            attachedSession = session
            appliedRotation = -1
            frame.clearIntrinsics()
        }
        val rotation = displayRotation
        if (rotation != appliedRotation) {
            val size = session.cameraConfig.textureSize
            session.setDisplayGeometry(rotation, max(size.width, size.height), min(size.width, size.height))
            appliedRotation = rotation
        }

        val arFrame = try {
            session.update()
        } catch (e: Exception) {
            // Paused between the isResumed check and update(), or the camera was taken by another app.
            return null
        }
        val timestamp = arFrame.timestamp
        if (timestamp == 0L) return null

        val camera = arFrame.camera
        frame.set(timestampNs = timestamp, isTracking = camera.trackingState == TrackingState.TRACKING)

        if (arFrame.hasDisplayGeometryChanged()) {
            arFrame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                NDC_CORNERS,
                Coordinates2d.TEXTURE_NORMALIZED,
                frame.uv,
            )
            frame.markUvChanged()
        }
        if (!frame.hasIntrinsics) {
            val intrinsics = camera.textureIntrinsics
            val focal = intrinsics.focalLength
            val size = intrinsics.imageDimensions
            frame.setIntrinsics(size[0], size[1], focal[0], focal[1])
            logger.d(TAG, "Intrinsics ${size[0]}x${size[1]} f=(${focal[0]}, ${focal[1]})")
        }
        return frame
    }

    private companion object {
        const val TAG = "ArFrames"

        /** bottom-left, bottom-right, top-left, top-right */
        val NDC_CORNERS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    }
}
