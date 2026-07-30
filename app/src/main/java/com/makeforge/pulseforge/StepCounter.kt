package com.makeforge.pulseforge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Reads the phone's OWN built-in step counter. Works instantly with no watch,
 *  so it's the easiest thing to test right after sideloading the APK. */
class StepCounter(context: Context, private val onSteps: (Long) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isPresent: Boolean get() = stepSensor != null

    fun start() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            onSteps(event.values[0].toLong())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
