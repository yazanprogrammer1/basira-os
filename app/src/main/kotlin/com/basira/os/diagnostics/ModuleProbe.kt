package com.basira.os.diagnostics

import com.basira.os.core.agent.LlmClient
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.render.calibration.CalibrationStore
import com.basira.os.core.common.coroutines.DispatcherProvider
import com.basira.os.core.hands.HandTracker
import com.basira.os.core.render.Compositor
import com.basira.os.core.spaces.SpacesRepository
import com.basira.os.core.thermal.ThermalGovernor
import com.basira.os.core.thermal.ThermalStatusSource
import com.basira.os.core.tracking.ArCoreSupport
import com.basira.os.core.tracking.HeadTracker
import com.basira.os.core.voice.SpeechToText
import com.basira.os.core.voice.TextToSpeech
import com.basira.os.core.voice.WakeWordDetector
import com.basira.os.core.windows.SpatialWindowManager
import javax.inject.Inject

data class ModuleBinding(
    val module: String,
    val contract: String,
    val implementation: String,
) {
    val isStub: Boolean get() = implementation.startsWith("Stub")
}

/**
 * Injects one binding from every :core:* module. If this class can be constructed,
 * the whole Hilt graph is wired; the screen shows which implementations are still stubs.
 */
class ModuleProbe @Inject constructor(
    dispatchers: DispatcherProvider,
    compositor: Compositor,
    headTracker: HeadTracker,
    arCoreSupport: ArCoreSupport,
    windowManager: SpatialWindowManager,
    handTracker: HandTracker,
    wakeWord: WakeWordDetector,
    speechToText: SpeechToText,
    textToSpeech: TextToSpeech,
    llmClient: LlmClient,
    spaces: SpacesRepository,
    thermalGovernor: ThermalGovernor,
    thermalSource: ThermalStatusSource,
    cameraFrames: CameraFrameSource,
    calibration: CalibrationStore,
) {
    val bindings: List<ModuleBinding> = listOf(
        binding(":core:common", "DispatcherProvider", dispatchers),
        binding(":core:render", "Compositor", compositor),
        binding(":core:render", "CalibrationStore", calibration),
        binding(":core:tracking", "CameraFrameSource", cameraFrames),
        binding(":core:tracking", "HeadTracker", headTracker),
        binding(":core:tracking", "ArCoreSupport", arCoreSupport),
        binding(":core:windows", "SpatialWindowManager", windowManager),
        binding(":core:hands", "HandTracker", handTracker),
        binding(":core:voice", "WakeWordDetector", wakeWord),
        binding(":core:voice", "SpeechToText", speechToText),
        binding(":core:voice", "TextToSpeech", textToSpeech),
        binding(":core:agent", "LlmClient", llmClient),
        binding(":core:spaces", "SpacesRepository", spaces),
        binding(":core:thermal", "ThermalGovernor", thermalGovernor),
        binding(":core:thermal", "ThermalStatusSource", thermalSource),
    )

    val llmConfigured: Boolean = llmClient.isConfigured
    val sttConfigured: Boolean = speechToText.isConfigured

    private fun binding(module: String, contract: String, impl: Any) =
        ModuleBinding(module, contract, impl::class.java.simpleName)
}
