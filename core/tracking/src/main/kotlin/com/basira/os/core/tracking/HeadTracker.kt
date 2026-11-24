package com.basira.os.core.tracking

import com.basira.os.core.common.math.Pose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

enum class TrackingMode {
    /** ARCore world tracking: windows stay in the room. */
    SIX_DOF,

    /** Rotation-vector sensor only: windows follow the head. */
    THREE_DOF,

    /** Not started, or tracking lost. */
    NONE,
}

data class HeadPose(
    val pose: Pose,
    val mode: TrackingMode,
    val timestampNs: Long,
)

/** Head tracking for the renderer. Implementations pick ARCore or the 3DoF fallback at runtime. */
interface HeadTracker {
    val mode: StateFlow<TrackingMode>
    val headPose: Flow<HeadPose>
}

/** Phase 0 stub. Phase 1 brings the ARCore session, phase 2 the 3DoF fallback. */
class StubHeadTracker @Inject constructor() : HeadTracker {
    override val mode: StateFlow<TrackingMode> = MutableStateFlow(TrackingMode.NONE).asStateFlow()
    override val headPose: Flow<HeadPose> = emptyFlow()
}
