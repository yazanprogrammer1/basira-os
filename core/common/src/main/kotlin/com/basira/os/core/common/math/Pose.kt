package com.basira.os.core.common.math

/** Rigid transform: rotate, then translate. Mirrors ARCore's Pose without depending on it. */
data class Pose(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Quaternion = Quaternion.IDENTITY,
) {
    fun transformPoint(p: Vec3): Vec3 = rotation.rotate(p) + position

    fun rotateVector(v: Vec3): Vec3 = rotation.rotate(v)

    val forward: Vec3 get() = rotateVector(Vec3.FORWARD)

    fun compose(other: Pose): Pose =
        Pose(transformPoint(other.position), (rotation * other.rotation).normalized())

    fun inverse(): Pose {
        val inv = rotation.conjugate()
        return Pose(inv.rotate(-position), inv)
    }

    companion object {
        val IDENTITY = Pose()
    }
}
