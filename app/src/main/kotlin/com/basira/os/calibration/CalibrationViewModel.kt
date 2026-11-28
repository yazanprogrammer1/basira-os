package com.basira.os.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.viewer.ScreenOrientation
import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.Compositor
import com.basira.os.core.render.RenderMode
import com.basira.os.core.render.calibration.CalibrationStore
import com.basira.os.core.tracking.arcore.ArCoreSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalibrationUiState(
    val profile: ViewerProfile = ViewerProfile.DEFAULT,
    val selected: CalibrationParam = CalibrationParam.IPD,
    val showCamera: Boolean = false,
    val panelVisible: Boolean = true,
    val loaded: Boolean = false,
)

/**
 * Every change previews live on the GL thread and is saved right away (DataStore writes are tiny),
 * so leaving the screen never loses a value.
 */
@HiltViewModel
class CalibrationViewModel @Inject constructor(
    val compositor: Compositor,
    val sessions: ArCoreSessionManager,
    val frameSource: CameraFrameSource,
    private val store: CalibrationStore,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = store.profile.first()
            mutableState.update { it.copy(profile = stored, loaded = true) }
        }
    }

    fun onEnter() {
        compositor.setRenderMode(if (state.value.showCamera) RenderMode.PASSTHROUGH else RenderMode.TEST_PATTERN)
        compositor.setViewerProfilePreview(state.value.profile.takeIf { state.value.loaded })
    }

    fun onExit() {
        compositor.setViewerProfilePreview(null)
        compositor.setRenderMode(RenderMode.PASSTHROUGH)
    }

    fun select(param: CalibrationParam) = mutableState.update { it.copy(selected = param) }

    fun selectNext() = mutableState.update { it.copy(selected = it.selected.next()) }

    /** Volume keys: +1 / -1 step of the selected parameter. */
    fun adjust(steps: Int) = apply(state.value.selected.adjust(state.value.profile, steps))

    fun setValue(param: CalibrationParam, value: Float) = apply(param.set(state.value.profile, value))

    fun toggleOrientation() {
        val p = state.value.profile
        val flipped = if (p.orientation == ScreenOrientation.LANDSCAPE) ScreenOrientation.REVERSE_LANDSCAPE else ScreenOrientation.LANDSCAPE
        apply(p.copy(orientation = flipped))
    }

    fun toggleCamera() {
        mutableState.update { it.copy(showCamera = !it.showCamera) }
        compositor.setRenderMode(if (state.value.showCamera) RenderMode.PASSTHROUGH else RenderMode.TEST_PATTERN)
    }

    fun togglePanel() = mutableState.update { it.copy(panelVisible = !it.panelVisible) }

    /** Starts from a known viewer, keeping the orientation and size the user already chose. */
    fun applyPreset(preset: ViewerProfile) {
        val current = state.value.profile
        apply(preset.copy(orientation = current.orientation, passthroughScale = current.passthroughScale))
    }

    fun reset() {
        viewModelScope.launch { store.reset() }
        apply(ViewerProfile.DEFAULT, save = false)
    }

    private fun apply(profile: ViewerProfile, save: Boolean = true) {
        if (!state.value.loaded) return
        mutableState.update { it.copy(profile = profile) }
        compositor.setViewerProfilePreview(profile)
        if (save) viewModelScope.launch { store.save(profile) }
    }
}
