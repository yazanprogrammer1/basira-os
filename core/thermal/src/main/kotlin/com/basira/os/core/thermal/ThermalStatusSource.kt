package com.basira.os.core.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/** Raw platform thermal readings. Cold flow: listening starts on collect and stops on cancel. */
interface ThermalStatusSource {
    val snapshots: Flow<ThermalSnapshot>

    /** True when the platform reports headroom (API 30+ and a supported thermal HAL). */
    val supportsHeadroom: Boolean
}

class PowerManagerThermalStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThermalStatusSource {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    override val supportsHeadroom: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override val snapshots: Flow<ThermalSnapshot> = callbackFlow {
        val status = AtomicReference(ThermalStatus.fromPlatform(powerManager.currentThermalStatus))

        fun emit() {
            trySend(ThermalSnapshot(status.get(), readHeadroom(), System.currentTimeMillis()))
        }

        val listener = PowerManager.OnThermalStatusChangedListener {
            status.set(ThermalStatus.fromPlatform(it))
            emit()
        }
        powerManager.addThermalStatusListener(context.mainExecutor, listener)

        // Headroom has no listener, so poll it. The platform rate-limits it to about once per second.
        val poller = launch {
            while (isActive) {
                emit()
                delay(HEADROOM_POLL_MS)
            }
        }

        awaitClose {
            poller.cancel()
            powerManager.removeThermalStatusListener(listener)
        }
    }.conflate()

    private fun readHeadroom(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeUnless { it.isNaN() }
    }

    private companion object {
        const val HEADROOM_POLL_MS = 2_000L
        const val HEADROOM_FORECAST_SECONDS = 10
    }
}
