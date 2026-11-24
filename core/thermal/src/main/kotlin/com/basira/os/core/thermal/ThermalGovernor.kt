package com.basira.os.core.thermal

import com.basira.os.core.common.coroutines.ApplicationScope
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.quality.QualityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the current quality budget. Every heavy subsystem observes this. */
interface ThermalGovernor {
    val qualityLevel: StateFlow<QualityLevel>
}

/**
 * Platform readings -> [ThermalPolicy] -> [QualityHysteresis].
 * Listens only while someone collects (plus 5 s), so it costs nothing on screens that do not render.
 */
@Singleton
class PolicyThermalGovernor @Inject constructor(
    source: ThermalStatusSource,
    @ApplicationScope scope: CoroutineScope,
    logger: Logger,
) : ThermalGovernor {

    private val hysteresis = QualityHysteresis()

    override val qualityLevel: StateFlow<QualityLevel> = source.snapshots
        .map { snapshot -> hysteresis.update(ThermalPolicy.qualityFor(snapshot), snapshot.timestampMs) }
        .distinctUntilChanged()
        .onEach { logger.d(TAG, "Quality level: $it") }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), QualityLevel.NORMAL)

    private companion object {
        const val TAG = "Thermal"
    }
}
