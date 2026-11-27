package com.basira.os.core.tracking.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.basira.os.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-rate head orientation from GAME_ROTATION_VECTOR (gyro + accel, no magnetometer jumps),
 * on its own thread. Used for camera reprojection now and the 3DoF fallback in phase 2.
 * Sensor timestamps share the camera's clock (elapsedRealtimeNanos) on ARCore devices.
 */
@Singleton
class OrientationTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: Logger,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val history = OrientationHistory()
    private val wxyz = FloatArray(4)
    private var thread: HandlerThread? = null

    val isAvailable: Boolean get() = sensor != null

    fun start() {
        val s = sensor ?: return
        if (thread != null) return
        val t = HandlerThread("basira-orientation").apply { start() }
        thread = t
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, Handler(t.looper))
        logger.d(TAG, "Orientation from ${s.name} (${s.stringType})")
    }

    fun stop() {
        val t = thread ?: return
        sensorManager.unregisterListener(this)
        t.quitSafely()
        thread = null
        synchronized(history) { history.clear() }
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getQuaternionFromVector(wxyz, event.values) // (w, x, y, z)
        synchronized(history) { history.add(event.timestamp, wxyz[1], wxyz[2], wxyz[3], wxyz[0]) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Orientation at [timestampNs] (elapsedRealtimeNanos base): interpolated in the past,
     * extrapolated up to 50 ms into the future. Any thread.
     */
    fun sampleAt(timestampNs: Long, out: FloatArray): Boolean =
        synchronized(history) { history.sampleAt(timestampNs, out) }

    private companion object {
        const val TAG = "Orientation"
    }
}
