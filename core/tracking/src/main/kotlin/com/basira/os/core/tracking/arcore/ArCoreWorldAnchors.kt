package com.basira.os.core.tracking.arcore

import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.panel.WorldAnchors
import com.basira.os.core.tracking.pose.Placement
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Window placements as ARCore anchors, so ARCore keeps correcting them as its map of the room improves.
 * New windows go onto the surface at the centre of the view (feature points or planes found by a hit
 * test), sized to keep the same angular size; without a surface they float [placeInFront]'s distance
 * ahead. Requests arrive from any thread; anchors are created and read on the GL thread in [onFrame].
 */
@Singleton
class ArCoreWorldAnchors @Inject constructor(
    private val logger: Logger,
) : WorldAnchors {

    private val requests = ConcurrentHashMap<Int, Float>()
    private val removals = ConcurrentLinkedQueue<Int>()

    // ---- GL thread ----
    private val anchors = HashMap<Int, Anchor>()
    private val poses = HashMap<Int, FloatArray>()
    private val scales = HashMap<Int, Float>()
    private val distances = HashMap<Int, Float>()
    private val placement = FloatArray(Mat4.POSE_SIZE)
    private val rotation = FloatArray(4)

    override fun placeInFront(id: Int, distanceMeters: Float) {
        requests[id] = distanceMeters
    }

    override fun remove(id: Int) {
        removals.add(id)
    }

    override fun pose(id: Int, out: FloatArray): Boolean {
        val p = poses[id] ?: return false
        p.copyInto(out)
        return true
    }

    override fun scale(id: Int): Float = scales[id] ?: 1f

    /**
     * GL thread, after session.update(). [viewPose] is the display-time head pose;
     * ([centerX], [centerY]) is the view centre in display-geometry pixels, for the hit test.
     */
    fun onFrame(session: Session, frame: Frame, tracking: Boolean, viewPose: FloatArray, centerX: Float, centerY: Float) {
        while (true) {
            val id = removals.poll() ?: break
            anchors.remove(id)?.detach()
            poses.remove(id)
            scales.remove(id)
            distances.remove(id)
            requests.remove(id)
        }
        if (tracking && requests.isNotEmpty()) {
            val iterator = requests.entries.iterator()
            while (iterator.hasNext()) {
                val (id, fallbackDistance) = iterator.next()
                iterator.remove()
                val scale = place(frame, viewPose, centerX, centerY, fallbackDistance)
                rotation[0] = placement[3]; rotation[1] = placement[4]; rotation[2] = placement[5]; rotation[3] = placement[6]
                val anchor = session.createAnchor(Pose(floatArrayOf(placement[0], placement[1], placement[2]), rotation))
                anchors.put(id, anchor)?.detach()
                distances[id] = fallbackDistance
                scales[id] = scale
                poses[id] = placement.copyOf()
            }
        }
        for ((id, anchor) in anchors) {
            if (anchor.trackingState != TrackingState.TRACKING) continue // keep the last known pose
            val out = poses.getOrPut(id) { FloatArray(Mat4.POSE_SIZE) }
            val p = anchor.pose
            out[0] = p.tx(); out[1] = p.ty(); out[2] = p.tz()
            p.getRotationQuaternion(out, 3)
        }
    }

    /** Fills [placement]; returns the size multiplier. */
    private fun place(frame: Frame, viewPose: FloatArray, centerX: Float, centerY: Float, fallbackDistance: Float): Float {
        val hit = frame.hitTest(centerX, centerY).firstOrNull { hit ->
            val onSurface = when (val t = hit.trackable) {
                is Plane -> t.isPoseInPolygon(hit.hitPose)
                is Point -> true
                else -> false
            }
            onSurface && hit.distance in MIN_SURFACE_M..MAX_SURFACE_M
        }
        if (hit == null) {
            Placement.inFront(viewPose, fallbackDistance, placement)
            logger.d(TAG, "No surface ahead: floating at $fallbackDistance m")
            return 1f
        }
        val p = hit.hitPose
        val distance = Placement.onSurface(viewPose, p.tx(), p.ty(), p.tz(), SURFACE_OFFSET_M, placement)
        val scale = (distance / REFERENCE_DISTANCE_M).coerceIn(MIN_SCALE, MAX_SCALE)
        logger.d(TAG, "On ${hit.trackable.javaClass.simpleName} at %.2f m, scale %.2f".format(distance, scale))
        return scale
    }

    /** A new ARCore session has a new world: re-place every window in front of the viewer. */
    fun onSessionChanged() {
        for ((id, distance) in distances) requests.putIfAbsent(id, distance)
        anchors.clear()
        poses.clear()
    }

    private companion object {
        const val TAG = "Anchors"
        const val MIN_SURFACE_M = 0.5f
        const val MAX_SURFACE_M = 5f
        const val SURFACE_OFFSET_M = 0.02f

        /** The window's designed size (1.4 m wide) is for 1.5 m away. */
        const val REFERENCE_DISTANCE_M = 1.5f
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
    }
}
