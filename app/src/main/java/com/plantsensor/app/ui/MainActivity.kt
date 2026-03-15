package com.plantsensor.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.plantsensor.app.data.BleDevice
import com.plantsensor.app.data.BleState
import com.plantsensor.app.databinding.ActivityMainBinding
import com.plantsensor.app.viewmodel.SensorViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SensorViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter

    // ────────────── Permission launcher ──────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) viewModel.startScan()
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) checkPermissionsAndScan()
        else Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show()
    }

    // ────────────────────────────── Lifecycle ──────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        observeState()
    }

    // ────────────────────────────── Setup ──────────────────────────────

    private fun setupRecyclerView() {
        deviceAdapter = DeviceListAdapter { device -> onDeviceClicked(device) }
        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            if (!viewModel.bleManager.isBluetoothEnabled) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                checkPermissionsAndScan()
            }
        }

        binding.btnStopScan.setOnClickListener {
            viewModel.stopScan()
        }

        binding.btnDemo.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java).apply {
                putExtra(DashboardActivity.EXTRA_DEMO_MODE, true)
            })
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.bleState.collect { state ->
                updateUiForState(state)
            }
        }
        lifecycleScope.launch {
            viewModel.scannedDevices.collect { devices ->
                deviceAdapter.submitList(devices)
                binding.tvNoDevices.visibility =
                    if (devices.isEmpty() && viewModel.bleState.value is BleState.Scanning)
                        View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateUiForState(state: BleState) {
        when (state) {
            is BleState.Disconnected -> {
                binding.tvStatus.text = "Ready to scan"
                binding.btnScan.isEnabled = true
                binding.btnStopScan.isEnabled = false
                binding.progressScan.visibility = View.GONE
            }
            is BleState.Scanning -> {
                binding.tvStatus.text = "Scanning for plant sensors…"
                binding.btnScan.isEnabled = false
                binding.btnStopScan.isEnabled = true
                binding.progressScan.visibility = View.VISIBLE
            }
            is BleState.DeviceFound -> {
                binding.tvStatus.text = "Found: ${state.name}"
            }
            is BleState.Connecting -> {
                binding.tvStatus.text = "Connecting…"
                binding.progressScan.visibility = View.VISIBLE
            }
            is BleState.Connected -> {
                binding.progressScan.visibility = View.GONE
            }
            is BleState.Error -> {
                binding.tvStatus.text = "Error: ${state.message}"
                binding.progressScan.visibility = View.GONE
                binding.btnScan.isEnabled = true
                binding.btnStopScan.isEnabled = false
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ────────────────────────────── Actions ──────────────────────────────

    private fun onDeviceClicked(device: BleDevice) {
        viewModel.stopScan()
        startActivity(Intent(this, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_DEVICE_NAME, device.name)
            putExtra(DashboardActivity.EXTRA_DEVICE_ADDRESS, device.address)
        })
    }

    private fun checkPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isEmpty()) viewModel.startScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScan()
    }
}
