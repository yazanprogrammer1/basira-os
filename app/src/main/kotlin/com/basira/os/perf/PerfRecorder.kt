package com.basira.os.perf

import android.content.Context
import android.os.SystemClock
import com.basira.os.core.common.coroutines.DispatcherProvider
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.render.Compositor
import com.basira.os.core.render.FrameStats
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One CSV row per second. Pure formatting, so it is unit-tested. */
object PerfCsv {
    const val HEADER =
        "t_s,fps,frame_ms_avg,frame_ms_p95,janky_total,frames_total,camera_age_ms,tracking," +
            "thermal_status,headroom,battery_c,quality"

    fun row(tSeconds: Long, stats: FrameStats, thermal: ThermalSnapshot?, quality: QualityLevel): String =
        listOf(
            tSeconds.toString(),
            fmt(stats.fps, 1),
            fmt(stats.frameTimeMsAvg, 2),
            fmt(stats.frameTimeMsP95, 2),
            stats.jankyFrames.toString(),
            stats.totalFrames.toString(),
            if (stats.cameraAgeMs >= 0f) fmt(stats.cameraAgeMs, 1) else "",
            stats.tracking.toString(),
            thermal?.status?.name.orEmpty(),
            thermal?.headroom?.let { fmt(it, 3) }.orEmpty(),
            thermal?.batteryTempC?.let { fmt(it, 1) }.orEmpty(),
            quality.name,
        ).joinToString(",")

    private fun fmt(value: Float, decimals: Int) = "%.${decimals}f".format(Locale.US, value)
}

/**
 * Writes `Android/data/com.basira.os/files/perf/session-<time>.csv` while [record] runs,
 * and logs every 5th row as `Basira/Perf` for `adb logcat -s Basira/Perf`.
 */
@Singleton
class PerfRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositor: Compositor,
    private val thermalSource: ThermalStatusSource,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) {
    /** Suspends until cancelled (i.e. while the rendering screen is visible). */
    suspend fun record() = withContext(dispatchers.io) {
        val dir = File(context.getExternalFilesDir(null), "perf").apply { mkdirs() }
        val name = "session-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".csv"
        val file = File(dir, name)
        logger.d(TAG, "Recording to ${file.absolutePath}")

        coroutineScope {
            var latestThermal: ThermalSnapshot? = null
            val thermalJob = launch { thermalSource.snapshots.collect { latestThermal = it } }
            try {
                file.bufferedWriter().use { out ->
                    out.appendLine(PerfCsv.HEADER)
                    val start = SystemClock.elapsedRealtime()
                    var rows = 0L
                    while (isActive) {
                        delay(ROW_INTERVAL_MS)
                        val row = PerfCsv.row(
                            tSeconds = (SystemClock.elapsedRealtime() - start) / 1000,
                            stats = compositor.frameStats.value,
                            thermal = latestThermal,
                            quality = compositor.qualityLevel,
                        )
                        out.appendLine(row)
                        out.flush()
                        if (rows++ % LOG_EVERY_ROWS == 0L) logger.d(TAG, row)
                    }
                }
            } finally {
                thermalJob.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "Perf"
        const val ROW_INTERVAL_MS = 1_000L
        const val LOG_EVERY_ROWS = 5L
    }
}
