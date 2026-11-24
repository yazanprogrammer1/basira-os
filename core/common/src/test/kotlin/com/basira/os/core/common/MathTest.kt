package com.basira.os.core.common

import com.basira.os.core.common.math.Pose
import com.basira.os.core.common.math.Quaternion
import com.basira.os.core.common.math.Ray
import com.basira.os.core.common.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class MathTest {

    private fun assertVec(expected: Vec3, actual: Vec3, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test
    fun `yaw 90 degrees left turns forward into left`() {
        val q = Quaternion.fromAxisAngle(Vec3.UP, (PI / 2).toFloat())
        assertVec(Vec3(-1f, 0f, 0f), q.rotate(Vec3.FORWARD))
    }

    @Test
    fun `pose inverse undoes the transform`() {
        val pose = Pose(Vec3(1f, 2f, 3f), Quaternion.fromAxisAngle(Vec3(1f, 1f, 0f), 0.7f))
        val p = Vec3(0.3f, -1.2f, 4f)
        assertVec(p, pose.inverse().transformPoint(pose.transformPoint(p)))
    }

    @Test
    fun `gaze ray points along head forward with unit direction`() {
        val head = Pose(Vec3(0f, 1.6f, 0f), Quaternion.IDENTITY)
        val ray = Ray.gaze(head)
        assertEquals(1f, ray.direction.length, 1e-6f)
        assertVec(Vec3(0f, 1.6f, -1.5f), ray.pointAt(1.5f))
    }

    @Test
    fun `normalizing a zero vector stays zero`() {
        assertVec(Vec3.ZERO, Vec3.ZERO.normalized())
    }
}
