package com.basira.os.core.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.basira.os.core.common.coroutines.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw platform thermal readings, shared by every collector (overlay, governor, perf log).
 * The platform listener runs only while someone collects, and replays the latest reading.
 */
interface ThermalStatusSource {
    val snapshots: Flow<ThermalSnapshot>

    /** True when the platform reports headroom (API 30+ and a supported thermal HAL). */
    val supportsHeadroom: Boolean
}

@Singleton
class PowerManagerThermalStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) : ThermalStatusSource {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    override val supportsHeadroom: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override val snapshots: Flow<ThermalSnapshot> = callbackFlow {
        val status = AtomicReference(ThermalStatus.fromPlatform(powerManager.currentThermalStatus))

        fun emit() {
            trySend(ThermalSnapshot(status.get(), readHeadroom(), System.currentTimeMillis(), readBatteryTempC()))
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
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private fun readHeadroom(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeUnless { it.isNaN() }
    }

    /** Sticky ACTION_BATTERY_CHANGED: no receiver is registered, so nothing leaks. Tenths of a degree. */
    private fun readBatteryTempC(): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10f
    }

    private companion object {
        const val HEADROOM_POLL_MS = 2_000L
        const val STOP_TIMEOUT_MS = 2_000L
        const val HEADROOM_FORECAST_SECONDS = 10
    }
}
