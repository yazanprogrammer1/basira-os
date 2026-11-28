package com.basira.os.core.tracking.arcore

import android.os.SystemClock
import android.view.Surface
import com.basira.os.core.common.camera.CameraFrame
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.Mat4
import com.basira.os.core.tracking.pose.HeadPoseFilter
import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.tracking.orientation.OrientationTracker
import com.basira.os.core.tracking.orientation.ViewFrame
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
    private val orientation: OrientationTracker,
    private val anchors: ArCoreWorldAnchors,
    private val logger: Logger,
) : CameraFrameSource {

    private val headFilter = HeadPoseFilter()
    private val capturePose = FloatArray(Mat4.POSE_SIZE)
    private var sensorNowValid = false

    private val frame = CameraFrame()
    private val capture = FloatArray(QuatMath.SIZE)
    private val now = FloatArray(QuatMath.SIZE)
    private val deviceToView = FloatArray(QuatMath.SIZE)
    private val scratchA = FloatArray(QuatMath.SIZE)
    private val scratchB = FloatArray(QuatMath.SIZE)
    private var viewFrameRotation = -1
    private var cameraTextureId = -1
    private var attachedSession: Session? = null

    @Volatile private var displayRotation = Surface.ROTATION_90
    private var appliedRotation = -1

    /** False until the NDC -> texture transform has been computed for the current session and rotation. */
    private var uvValid = false

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
            uvValid = false
            frame.clearIntrinsics()
            headFilter.reset()
            anchors.onSessionChanged()
        }
        val rotation = displayRotation
        if (rotation != appliedRotation) {
            val size = session.cameraConfig.textureSize
            session.setDisplayGeometry(rotation, max(size.width, size.height), min(size.width, size.height))
            appliedRotation = rotation
            uvValid = false
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

        // hasDisplayGeometryChanged() fires on one frame only, which may come before the first frame we
        // use; computing on our own first frame as well avoids drawing with all-zero coordinates.
        if (arFrame.hasDisplayGeometryChanged() || !uvValid) {
            arFrame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                NDC_CORNERS,
                Coordinates2d.TEXTURE_NORMALIZED,
                frame.uv,
            )
            frame.markUvChanged()
            uvValid = true
        }
        if (!frame.hasIntrinsics) {
            val intrinsics = camera.textureIntrinsics
            val focal = intrinsics.focalLength
            val size = intrinsics.imageDimensions
            frame.setIntrinsics(size[0], size[1], focal[0], focal[1])
            logger.d(TAG, "Intrinsics ${size[0]}x${size[1]} f=(${focal[0]}, ${focal[1]})")
        }
        if (rotation != viewFrameRotation) {
            ViewFrame.deviceToView(rotation, deviceToView)
            viewFrameRotation = rotation
        }
        updateReprojection(timestamp)
        updateHeadPose(camera, session)
        return frame
    }

    /** Display-time head pose for world-locked windows, and anchor upkeep. */
    private fun updateHeadPose(camera: com.google.ar.core.Camera, session: Session) {
        val tracking = camera.trackingState == TrackingState.TRACKING
        if (tracking) {
            val p = camera.displayOrientedPose
            capturePose[0] = p.tx(); capturePose[1] = p.ty(); capturePose[2] = p.tz()
            p.getRotationQuaternion(capturePose, 3)
        }
        val state = headFilter.update(
            tracking = tracking,
            capturePose = capturePose,
            captureToNow = if (frame.reprojectionValid) frame.reprojection else null,
            sensorNow = if (sensorNowValid) now else null,
            deviceToView = deviceToView,
            nowNs = SystemClock.elapsedRealtimeNanos(),
            out = frame.viewPose,
        )
        frame.setHeadTracking(state)
        anchors.onFrame(session, tracking, frame.viewPose)
    }

    /**
     * Head rotation between the camera capture and the predicted display time, from the high-rate
     * orientation sensor (ARCore's own pose is only as fresh as the 30 fps camera).
     */
    private fun updateReprojection(captureNs: Long) {
        val displayNs = SystemClock.elapsedRealtimeNanos() + DISPLAY_PREDICTION_NS
        sensorNowValid = orientation.sampleAt(displayNs, now)
        // A camera clock on another time base would show up as a negative or huge image age.
        val imageAge = displayNs - captureNs
        if (imageAge < 0L || imageAge > MAX_IMAGE_AGE_NS || !sensorNowValid ||
            !orientation.sampleAt(captureNs, capture)
        ) {
            frame.setReprojection(valid = false, degrees = 0f)
            return
        }
        ViewFrame.captureToNowInView(capture, now, deviceToView, scratchA, scratchB, frame.reprojection)
        frame.setReprojection(valid = true, degrees = QuatMath.angleDegrees(frame.reprojection))
    }

    private companion object {
        const val TAG = "ArFrames"

        /** About one frame: the image reaches the eye roughly one vsync after we draw it. */
        const val DISPLAY_PREDICTION_NS = 16_000_000L

        const val MAX_IMAGE_AGE_NS = 500_000_000L

        /** bottom-left, bottom-right, top-left, top-right */
        val NDC_CORNERS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    }
}
