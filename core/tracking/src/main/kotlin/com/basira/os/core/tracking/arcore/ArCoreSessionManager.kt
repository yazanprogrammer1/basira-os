package com.basira.os.core.tracking.arcore

import android.content.Context
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.tracking.orientation.OrientationTracker
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

enum class ArSessionError { NOT_INSTALLED, UNSUPPORTED, NO_CAMERA_PERMISSION, CAMERA_UNAVAILABLE, UNKNOWN }

sealed interface ArSessionState {
    data object Idle : ArSessionState
    data class Running(val cameraConfig: String) : ArSessionState
    data object Paused : ArSessionState
    data class Failed(val error: ArSessionError, val detail: String?) : ArSessionState
}

/**
 * Owns the ARCore [Session]. resume/pause/close run on the main thread, in step with the GLSurfaceView:
 * resume the session before the view, pause it after the view (GLSurfaceView.onPause waits for the GL thread).
 * Configured for passthrough: newest camera image without blocking, no planes, light or depth (heat).
 */
@Singleton
class ArCoreSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orientation: OrientationTracker,
    private val logger: Logger,
) {
    @Volatile var session: Session? = null
        private set

    @Volatile var isResumed: Boolean = false
        private set

    private val mutableState = MutableStateFlow<ArSessionState>(ArSessionState.Idle)
    val state: StateFlow<ArSessionState> = mutableState.asStateFlow()

    private var cameraConfigDescription = ""

    fun resume(): Boolean {
        if (isResumed) return true
        val current = session ?: create() ?: return false
        return try {
            current.resume()
            orientation.start()
            isResumed = true
            mutableState.value = ArSessionState.Running(cameraConfigDescription)
            true
        } catch (e: CameraNotAvailableException) {
            fail(ArSessionError.CAMERA_UNAVAILABLE, e)
        } catch (e: SecurityException) {
            fail(ArSessionError.NO_CAMERA_PERMISSION, e)
        }
    }

    fun pause() {
        if (!isResumed) return
        isResumed = false
        session?.pause()
        orientation.stop()
        mutableState.value = ArSessionState.Paused
    }

    fun close() {
        pause()
        session?.close()
        session = null
        mutableState.value = ArSessionState.Idle
    }

    private fun create(): Session? = try {
        Session(context).also { created ->
            configure(created)
            session = created
        }
    } catch (e: UnavailableArcoreNotInstalledException) {
        fail(ArSessionError.NOT_INSTALLED, e).let { null }
    } catch (e: UnavailableApkTooOldException) {
        fail(ArSessionError.NOT_INSTALLED, e).let { null }
    } catch (e: UnavailableSdkTooOldException) {
        fail(ArSessionError.UNKNOWN, e).let { null }
    } catch (e: UnavailableDeviceNotCompatibleException) {
        fail(ArSessionError.UNSUPPORTED, e).let { null }
    } catch (e: SecurityException) {
        fail(ArSessionError.NO_CAMERA_PERMISSION, e).let { null }
    } catch (e: Exception) {
        fail(ArSessionError.UNKNOWN, e).let { null }
    }

    private fun configure(session: Session) {
        val config = pickCameraConfig(session)
        if (config != null) {
            session.cameraConfig = config
            val texture = config.textureSize
            val image = config.imageSize
            cameraConfigDescription =
                "gpu ${texture.width}x${texture.height}, cpu ${image.width}x${image.height}, " +
                "${config.fpsRange.lower}-${config.fpsRange.upper} fps"
        }
        session.configure(
            Config(session).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.DISABLED
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                depthMode = Config.DepthMode.DISABLED
                instantPlacementMode = Config.InstantPlacementMode.DISABLED
            },
        )
        logger.d(TAG, "Session configured: $cameraConfigDescription")
    }

    /** 60 fps camera when the device offers it (lower latency), otherwise 30. ARCore lists preferred first. */
    private fun pickCameraConfig(session: Session): CameraConfig? {
        fun configs(fps: CameraConfig.TargetFps) = session.getSupportedCameraConfigs(
            CameraConfigFilter(session)
                .setFacingDirection(CameraConfig.FacingDirection.BACK)
                .setTargetFps(EnumSet.of(fps)),
        )
        return configs(CameraConfig.TargetFps.TARGET_FPS_60).firstOrNull()
            ?: configs(CameraConfig.TargetFps.TARGET_FPS_30).firstOrNull()
    }

    private fun fail(error: ArSessionError, e: Exception): Boolean {
        logger.w(TAG, "ARCore session failed: $error", e)
        isResumed = false
        mutableState.value = ArSessionState.Failed(error, e.message)
        return false
    }

    private companion object {
        const val TAG = "ArSession"
    }
}
