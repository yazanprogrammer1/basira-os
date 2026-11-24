package com.basira.os.core.hands

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/** Pinch states with hysteresis, see docs/ARCHITECTURE.md section 3. */
enum class PinchState { OPEN, NEAR, PINCHED, DRAGGING }

sealed interface HandEvent {
    val timestampMs: Long

    data class PinchStateChanged(val state: PinchState, override val timestampMs: Long) : HandEvent

    /** Hand moved while pinched, in normalised image coordinates. */
    data class Drag(val dx: Float, val dy: Float, override val timestampMs: Long) : HandEvent

    /** Open palm held for 0.5 s: go Home. */
    data class PalmHold(override val timestampMs: Long) : HandEvent

    data class HandLost(override val timestampMs: Long) : HandEvent
}

/**
 * Hand tracking off the GL thread. Runs at the fps allowed by the current QualityLevel,
 * and is fully off at HOT.
 */
interface HandTracker {
    val events: Flow<HandEvent>

    fun start(targetFps: Int)

    fun stop()
}

/** Phase 0 stub. Phase 3 wires MediaPipe HandLandmarker (LIVE_STREAM) and PinchStateMachine. */
class StubHandTracker @Inject constructor() : HandTracker {
    override val events: Flow<HandEvent> = emptyFlow()

    override fun start(targetFps: Int) = Unit

    override fun stop() = Unit
}
