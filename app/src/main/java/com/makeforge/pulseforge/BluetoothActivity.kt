package com.makeforge.pulseforge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID

class BluetoothActivity : AppCompatActivity() {

    private val btAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val found = HashMap<String, BluetoothDevice>()

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var deviceList: LinearLayout

    private val HR_SERVICE = uuid16("180D")
    private val HR_MEAS = uuid16("2A37")
    private val BATT_SERVICE = uuid16("180F")
    private val BATT_LEVEL = uuid16("2A19")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun uuid16(s: String) = UUID.fromString("0000$s-0000-1000-8000-00805f9b34fb")

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            if (r.values.all { it }) startScan() else toast("Bluetooth permission is needed")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        status.text = if (btAdapter?.isEnabled == true)
            "Tap Scan to find your watch." else "Turn on Bluetooth, then tap Scan."
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply { text = "Connect Watch (Bluetooth)"; textSize = 22f; setPadding(0, 0, 0, 24) }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, 16) }
        val scanBtn = Button(this).apply { text = "Scan for Watches"; setOnClickListener { requestThenScan() } }
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 16) }
        log = TextView(this).apply { textSize = 13f; setPadding(0, 16, 0, 0) }
        root.addView(title); root.addView(status); root.addView(scanBtn); root.addView(deviceList); root.addView(log)
        scroll.addView(root)
        return scroll
    }

    private fun neededPerms() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPerms() = neededPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestThenScan() {
        if (btAdapter == null) { toast("No Bluetooth on this phone"); return }
        if (btAdapter?.isEnabled != true) { toast("Please turn on Bluetooth first"); return }
        if (hasPerms()) startScan() else permLauncher.launch(neededPerms())
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val scanner = btAdapter?.bluetoothLeScanner ?: return
        found.clear(); deviceList.removeAllViews(); scanning = true
        status.text = "Scanning... tap your watch when it appears."
        scanner.startScan(scanCallback)
        deviceList.postDelayed({
            if (scanning) {
                scanning = false
                try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
                status.text = "Scan done. Tap a device, or Scan again."
            }
        }, 12000)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, result: ScanResult) {
            val d = result.device
            if (!found.containsKey(d.address)) {
                found[d.address] = d
                deviceList.addView(Button(this@BluetoothActivity).apply {
                    text = "${d.name ?: "(unknown)"}\n${d.address}"
                    setOnClickListener { connect(d) }
                })
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(d: BluetoothDevice) {
        if (scanning) {
            scanning = false
            try { btAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }
        appendLog("Connecting to ${d.name ?: d.address}...")
        gatt = d.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, s: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("Connected \u2705 Reading info...")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog("Disconnected.")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, s: Int) {
            appendLog("Services this watch exposes:")
            for (svc in g.services) appendLog("\u2022 ${svc.uuid}")
            g.getService(BATT_SERVICE)?.getCharacteristic(BATT_LEVEL)?.let { g.readCharacteristic(it) }
                ?: appendLog("No standard battery service.")
            val hr = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEAS)
            if (hr != null) {
                g.setCharacteristicNotification(hr, true)
                hr.getDescriptor(CCCD)?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(it)
                }
                appendLog("Listening for heart rate...")
            } else {
                appendLog("No standard heart-rate service (watch may use a private format).")
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, s: Int) {
            if (ch.uuid == BATT_LEVEL) appendLog("Battery: ${ch.value?.getOrNull(0)?.toInt() ?: -1}%")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == HR_MEAS) appendLog("Heart rate: ${parseHr(ch.value ?: return)} bpm")
        }
    }

    private fun parseHr(d: ByteArray): Int {
        if (d.isEmpty()) return -1
        return if (d[0].toInt() and 0x01 == 0) (d.getOrNull(1)?.toInt()?.and(0xFF) ?: -1)
        else ((d.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8) or (d.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
    }

    private fun appendLog(line: String) { runOnUiThread { log.text = "${log.text}\n$line" } }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        try { gatt?.close() } catch (_: Exception) {}
    }
}
