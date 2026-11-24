package com.basira.os.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basira.os.BuildConfig
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.thermal.ThermalPolicy
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatusSource
import com.basira.os.core.tracking.ArCoreStatus
import com.basira.os.core.tracking.ArCoreSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val device: DeviceInfo,
    val arCore: ArCoreStatus? = null,
    val thermal: ThermalSnapshot? = null,
    /** What the phase 1 governor will choose for this reading. */
    val quality: QualityLevel = QualityLevel.NORMAL,
    val cameraGranted: Boolean = false,
    val bindings: List<ModuleBinding> = emptyList(),
    val llmConfigured: Boolean = false,
    val sttConfigured: Boolean = false,
) {
    fun report(): String = DiagnosticsReport.build(
        appVersion = BuildConfig.VERSION_NAME,
        device = device,
        arCore = arCore,
        thermal = thermal,
        quality = quality,
        cameraGranted = cameraGranted,
        bindings = bindings,
    )
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    deviceInfoReader: DeviceInfoReader,
    moduleProbe: ModuleProbe,
    thermalSource: ThermalStatusSource,
    private val arCoreSupport: ArCoreSupport,
) : ViewModel() {

    private val arCore = MutableStateFlow<ArCoreStatus?>(null)
    private val cameraGranted = MutableStateFlow(false)

    private val base = DiagnosticsUiState(
        device = deviceInfoReader.read(),
        bindings = moduleProbe.bindings,
        llmConfigured = moduleProbe.llmConfigured,
        sttConfigured = moduleProbe.sttConfigured,
    )

    val state: StateFlow<DiagnosticsUiState> = combine(
        arCore,
        cameraGranted,
        thermalSource.snapshots.onStart<ThermalSnapshot?> { emit(null) },
    ) { arCoreStatus, camera, thermal ->
        base.copy(
            arCore = arCoreStatus,
            cameraGranted = camera,
            thermal = thermal,
            quality = thermal?.let(ThermalPolicy::qualityFor) ?: QualityLevel.NORMAL,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base)

    init {
        recheckArCore()
    }

    fun recheckArCore() {
        viewModelScope.launch {
            arCore.value = null
            arCore.value = arCoreSupport.check()
        }
    }

    fun onCameraPermission(granted: Boolean) {
        cameraGranted.value = granted
    }
}
