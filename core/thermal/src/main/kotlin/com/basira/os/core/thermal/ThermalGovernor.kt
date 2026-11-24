package com.basira.os.core.thermal

import com.basira.os.core.common.quality.QualityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Single source of truth for the current quality budget. Every heavy subsystem observes this. */
interface ThermalGovernor {
    val qualityLevel: StateFlow<QualityLevel>
}

/**
 * Phase 0 stub: always NORMAL.
 * Phase 1 replaces it with a governor driven by [ThermalStatusSource] + [ThermalPolicy] with hysteresis.
 */
class StubThermalGovernor @Inject constructor() : ThermalGovernor {
    private val level = MutableStateFlow(QualityLevel.NORMAL)
    override val qualityLevel: StateFlow<QualityLevel> = level.asStateFlow()
}
