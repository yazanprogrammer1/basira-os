package com.basira.os.core.tracking.arcore

import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.panel.WorldAnchors
import com.basira.os.core.tracking.pose.Placement
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Window placements as ARCore anchors, so ARCore keeps correcting them as its map of the room improves.
 * Requests arrive from any thread; anchors are created and read on the GL thread in [onFrame].
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

    /** GL thread, after session.update(). [viewPose] is the display-time head pose. */
    fun onFrame(session: Session, tracking: Boolean, viewPose: FloatArray) {
        while (true) {
            val id = removals.poll() ?: break
            anchors.remove(id)?.detach()
            poses.remove(id)
            distances.remove(id)
            requests.remove(id)
        }
        if (tracking && requests.isNotEmpty()) {
            val iterator = requests.entries.iterator()
            while (iterator.hasNext()) {
                val (id, distance) = iterator.next()
                iterator.remove()
                Placement.inFront(viewPose, distance, placement)
                rotation[0] = placement[3]; rotation[1] = placement[4]; rotation[2] = placement[5]; rotation[3] = placement[6]
                val anchor = session.createAnchor(Pose(floatArrayOf(placement[0], placement[1], placement[2]), rotation))
                anchors.put(id, anchor)?.detach()
                distances[id] = distance
                poses[id] = placement.copyOf()
                logger.d(TAG, "Anchor $id at ${placement.take(3)}")
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

    /** A new ARCore session has a new world: re-place every window in front of the viewer. */
    fun onSessionChanged() {
        for ((id, distance) in distances) requests.putIfAbsent(id, distance)
        anchors.clear()
        poses.clear()
    }

    private companion object {
        const val TAG = "Anchors"
    }
}
