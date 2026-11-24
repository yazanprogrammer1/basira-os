package com.basira.os.core.tracking

import android.app.Activity
import android.content.Context
import com.basira.os.core.common.coroutines.DispatcherProvider
import com.basira.os.core.common.logging.Logger
import com.google.ar.core.ArCoreApk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ArCoreStatus {
    /** Device is on the ARCore list and Google Play Services for AR is installed and current. */
    SUPPORTED_INSTALLED,
    SUPPORTED_NOT_INSTALLED,
    SUPPORTED_APK_TOO_OLD,

    /** Not on the ARCore list: we run in 3DoF mode. */
    UNSUPPORTED,

    /** Still unknown after retrying (usually no network on first check). */
    UNKNOWN,
    ;

    val isSupported: Boolean
        get() = this == SUPPORTED_INSTALLED || this == SUPPORTED_NOT_INSTALLED || this == SUPPORTED_APK_TOO_OLD
}

enum class ArCoreInstallResult { INSTALLED, INSTALL_REQUESTED, DECLINED, FAILED }

interface ArCoreSupport {
    suspend fun check(): ArCoreStatus

    /**
     * Asks Play Store to install or update ARCore. Call from `onResume`.
     * Returns [ArCoreInstallResult.INSTALL_REQUESTED] when the Play Store flow was launched.
     */
    fun requestInstall(activity: Activity, userRequested: Boolean): ArCoreInstallResult
}

class ArCoreApkSupport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : ArCoreSupport {

    override suspend fun check(): ArCoreStatus = withContext(dispatchers.default) {
        // The first query can be transient while ARCore asks the server about this device.
        repeat(MAX_TRANSIENT_RETRIES) {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (!availability.isTransient) return@withContext map(availability)
            delay(RETRY_DELAY_MS)
        }
        ArCoreStatus.UNKNOWN
    }

    override fun requestInstall(activity: Activity, userRequested: Boolean): ArCoreInstallResult =
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequested)) {
                ArCoreApk.InstallStatus.INSTALLED -> ArCoreInstallResult.INSTALLED
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArCoreInstallResult.INSTALL_REQUESTED
            }
        } catch (e: com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException) {
            ArCoreInstallResult.DECLINED
        } catch (e: Exception) {
            logger.w(TAG, "ARCore install request failed", e)
            ArCoreInstallResult.FAILED
        }

    private fun map(availability: ArCoreApk.Availability): ArCoreStatus = when (availability) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreStatus.SUPPORTED_INSTALLED
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCoreStatus.SUPPORTED_NOT_INSTALLED
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArCoreStatus.SUPPORTED_APK_TOO_OLD
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArCoreStatus.UNSUPPORTED
        else -> ArCoreStatus.UNKNOWN
    }

    private companion object {
        const val TAG = "ArCoreSupport"
        const val MAX_TRANSIENT_RETRIES = 25
        const val RETRY_DELAY_MS = 200L
    }
}
