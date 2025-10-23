package com.example.arwalking.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.max

/**
 * Lightweight step sensor manager that listens for Android step sensors and reports
 * step deltas to a callback. Designed as a fallback when ARCore movement is minimal
 * (e.g., phone held steady) so route progression can still advance.
 */
class StepSensorManager(
    private val onSteps: (deltaSteps: Int) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "StepSensorManager"
        private const val STEP_DETECTOR_VALUE = 1.0f
    }

    private var sensorManager: SensorManager? = null
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null

    private var lastCounterValue: Int? = null
    private var isStarted = false

    fun start(context: Context) {
        if (isStarted) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        var registered = false
        // Prefer TYPE_STEP_COUNTER (cumulative, orientation agnostic)
        stepCounter?.let { sensor ->
            registered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            Log.d(TAG, "Registered TYPE_STEP_COUNTER=${registered}")
        }
        // Also listen to TYPE_STEP_DETECTOR as event-based backup
        stepDetector?.let { sensor ->
            val ok = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            Log.d(TAG, "Registered TYPE_STEP_DETECTOR=${ok}")
            registered = registered || ok
        }

        if (!registered) {
            stop()
            throw IllegalStateException("No step sensors available on device")
        }
        isStarted = true
    }

    fun stop() {
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        sensorManager = null
        stepCounter = null
        stepDetector = null
        lastCounterValue = null
        isStarted = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> handleStepCounter(event)
            Sensor.TYPE_STEP_DETECTOR -> handleStepDetector(event)
        }
    }

    private fun handleStepCounter(event: SensorEvent) {
        // Cumulative steps since boot; compute delta
        val value = event.values.firstOrNull()?.toInt() ?: return
        val last = lastCounterValue
        lastCounterValue = value
        if (last != null) {
            val delta = max(0, value - last)
            if (delta > 0) {
                Log.v(TAG, "TYPE_STEP_COUNTER delta=$delta (total=$value)")
                onSteps(delta)
            }
        }
    }

    private fun handleStepDetector(event: SensorEvent) {
        // One event per step with value == 1.0
        val v = event.values.firstOrNull() ?: return
        if (v == STEP_DETECTOR_VALUE) {
            Log.v(TAG, "TYPE_STEP_DETECTOR +1")
            onSteps(1)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}
