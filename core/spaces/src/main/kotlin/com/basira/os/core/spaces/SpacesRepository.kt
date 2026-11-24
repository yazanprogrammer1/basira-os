package com.basira.os.core.spaces

import com.basira.os.core.common.math.Pose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class SavedWindow(
    val url: String,
    val appId: String?,
    /** Pose relative to the room origin the user set with a look + pinch. */
    val poseRelativeToOrigin: Pose,
    val widthMeters: Float,
    val heightMeters: Float,
    val zOrder: Int,
)

data class Space(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val windows: List<SavedWindow>,
)

/** Room memory. Phase 7 backs this with Room. */
interface SpacesRepository {
    fun observeSpaces(): Flow<List<Space>>

    suspend fun save(space: Space): Long

    suspend fun delete(id: Long)
}

class StubSpacesRepository @Inject constructor() : SpacesRepository {
    override fun observeSpaces(): Flow<List<Space>> = flowOf(emptyList())
    override suspend fun save(space: Space): Long = -1L
    override suspend fun delete(id: Long) = Unit
}
