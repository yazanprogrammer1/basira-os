package com.basira.os.core.common.camera

/**
 * Latest camera frame for the renderer. One instance is reused every frame, so the GL thread never allocates.
 * Read it only on the GL thread, right after [CameraFrameSource.update].
 */
class CameraFrame {
    /** Capture time of the camera image (ns, same base as `SystemClock.elapsedRealtimeNanos`). */
    var timestampNs: Long = 0L
        internal set

    /** True when this frame carries a camera image that was not drawn before. */
    var isNew: Boolean = false
        internal set

    var isTracking: Boolean = false
        internal set

    /** Camera texture size and intrinsics in pixels (sensor orientation). */
    var imageWidth: Int = 0
        internal set
    var imageHeight: Int = 0
        internal set
    var focalLengthX: Float = 0f
        internal set
    var focalLengthY: Float = 0f
        internal set

    /**
     * Texture coordinates for the display corners in NDC order:
     * bottom-left, bottom-right, top-left, top-right (x, y pairs). Already accounts for display rotation.
     */
    val uv: FloatArray = FloatArray(8)

    /** Increments whenever [uv] changes, so the renderer only re-uploads then. */
    var geometryVersion: Int = 0
        internal set

    val hasIntrinsics: Boolean get() = focalLengthX > 0f && focalLengthY > 0f && imageWidth > 0 && imageHeight > 0

    /** Half field of view as tan-angles, assuming the principal point is centred. */
    val halfTanX: Float get() = imageWidth / (2f * focalLengthX)
    val halfTanY: Float get() = imageHeight / (2f * focalLengthY)

    /** Writer API for [CameraFrameSource] implementations in other modules. */
    fun set(
        timestampNs: Long,
        isTracking: Boolean,
    ) {
        isNew = timestampNs != this.timestampNs
        this.timestampNs = timestampNs
        this.isTracking = isTracking
    }

    fun setIntrinsics(width: Int, height: Int, fx: Float, fy: Float) {
        imageWidth = width
        imageHeight = height
        focalLengthX = fx
        focalLengthY = fy
    }

    fun clearIntrinsics() {
        focalLengthX = 0f
        focalLengthY = 0f
    }

    fun markUvChanged() {
        geometryVersion++
    }
}

/**
 * Supplies camera images as an external OES texture. Implemented with ARCore in `:core:tracking`,
 * consumed by `:core:render`, which never sees ARCore types.
 */
interface CameraFrameSource {
    /** GL thread: the renderer created [cameraTextureId] (GL_TEXTURE_EXTERNAL_OES) for camera images. */
    fun onGlSurfaceCreated(cameraTextureId: Int)

    /** Any thread: current `Surface.ROTATION_*` of the display. */
    fun setDisplayRotation(rotation: Int)

    /** GL thread, once per frame. Never blocks on the camera. Null when no session is running. */
    fun update(): CameraFrame?
}
