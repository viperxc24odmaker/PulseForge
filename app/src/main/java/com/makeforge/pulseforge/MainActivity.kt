package com.makeforge.pulseforge

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.makeforge.pulseforge.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hc: HealthConnectManager
    private var stepCounter: StepCounter? = null

    private val hcPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(hc.permissions)) {
                toast("Health access granted \u2705")
                refresh()
            } else {
                toast("Some health permissions were denied")
            }
        }

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startStepCounter()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hc = HealthConnectManager(this)
        Notifications.ensureChannel(this)

        binding.btnGrant.setOnClickListener { requestHealthAccess() }
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnTestNotif.setOnClickListener {
            Notifications.fireTest(this, "PulseForge test \uD83D\uDD14", "See this on your watch? Bridging works!")
            toast("Notification fired \u2014 check phone + watch")
        }
        binding.btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        requestRuntimePermissions()
        updateHcStatus()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        runtimePermissionLauncher.launch(perms.toTypedArray())
    }

    private fun startStepCounter() {
        stepCounter?.stop()
        stepCounter = StepCounter(this) { total ->
            runOnUiThread { binding.phoneSteps.text = "Phone step sensor: $total (since boot)" }
        }
        if (stepCounter?.isPresent == true) {
            stepCounter?.start()
        } else {
            binding.phoneSteps.text = "Phone step sensor: not available on this device"
        }
    }

    private fun updateHcStatus() {
        binding.hcStatus.text = when (hc.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> "Health Connect: available \u2705"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Health Connect: needs update in Play Store"
            else -> "Health Connect: not installed on this device"
        }
    }

    private fun requestHealthAccess() {
        if (!hc.isAvailable()) {
            toast("Health Connect isn't available on this device")
            updateHcStatus()
            return
        }
        lifecycleScope.launch {
            if (hc.hasAllPermissions()) {
                toast("Already granted \u2705")
                refresh()
            } else {
                hcPermissionLauncher.launch(hc.permissions)
            }
        }
    }

    private fun refresh() {
        if (!hc.isAvailable()) { updateHcStatus(); return }
        lifecycleScope.launch {
            try {
                if (!hc.hasAllPermissions()) {
                    binding.summary.text = "Grant health access to see watch data"
                    return@launch
                }
                val s = hc.readToday()
                binding.steps.text = "Steps today: ${s.steps}"
                binding.heartRate.text = s.latestHeartRate?.let { "Heart rate: $it bpm" }
                    ?: "Heart rate: no recent reading"
                binding.calories.text = "Calories: ${"%.0f".format(s.calories)} kcal"
                binding.distance.text = "Distance: ${"%.2f".format(s.distanceMeters / 1000)} km"
                binding.summary.text = "Synced from Health Connect \u2705"
            } catch (e: Exception) {
                binding.summary.text = "Read failed: ${e.message}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        stepCounter?.start()
        updateHcStatus()
    }

    override fun onPause() {
        super.onPause()
        stepCounter?.stop()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
