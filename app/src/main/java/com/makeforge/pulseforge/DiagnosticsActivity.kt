package com.makeforge.pulseforge

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import com.makeforge.pulseforge.databinding.ActivityDiagnosticsBinding
import kotlinx.coroutines.launch

/** "Test features to see if it works" screen: lists every sensor + live values,
 *  shows Health Connect status/permissions, and fires a test notification. */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private lateinit var hc: HealthConnectManager
    private var stepCounter: StepCounter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hc = HealthConnectManager(this)

        listSensors()
        checkHealthConnect()

        binding.btnFireNotif.setOnClickListener {
            Notifications.fireTest(this, "Diagnostics test \u2705", "Notification pipeline works.")
        }

        stepCounter = StepCounter(this) { total ->
            runOnUiThread { binding.liveSteps.text = "Live step counter: $total steps (since boot)" }
        }
    }

    private fun listSensors() {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val hasStep = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasHr = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null

        val sb = StringBuilder()
        sb.append("Step counter sensor: ${if (hasStep) "YES \u2705" else "NO \u274C"}\n")
        sb.append("Heart rate sensor (on phone): ${if (hasHr) "YES \u2705" else "NO \u274C"}\n")
        sb.append("Total sensors on device: ${sensors.size}\n\n")
        sb.append("All sensors:\n")
        sensors.forEach { sb.append("\u2022 ${it.name} (${it.vendor})\n") }
        binding.sensorList.text = sb.toString()
    }

    private fun checkHealthConnect() {
        val status = when (hc.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> "available \u2705"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update required"
            else -> "not installed"
        }
        binding.hcDiag.text = "Health Connect SDK: $status"

        if (hc.isAvailable()) {
            lifecycleScope.launch {
                val granted = hc.hasAllPermissions()
                binding.hcPerms.text =
                    "Health permissions granted: ${if (granted) "YES \u2705" else "NO \u2014 grant on the main screen"}"
            }
        } else {
            binding.hcPerms.text = "Health permissions: N/A"
        }
    }

    override fun onResume() {
        super.onResume()
        if (stepCounter?.isPresent == true) stepCounter?.start()
        else binding.liveSteps.text = "Live step counter: sensor not present"
    }

    override fun onPause() {
        super.onPause()
        stepCounter?.stop()
    }
}
