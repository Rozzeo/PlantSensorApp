package com.plantsensor.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plantsensor.app.ble.BleManager
import com.plantsensor.app.data.BleDevice
import com.plantsensor.app.data.BleState
import com.plantsensor.app.data.SensorData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleManager(application)

    val bleState: StateFlow<BleState> = bleManager.bleState
    val scannedDevices: StateFlow<List<BleDevice>> = bleManager.scannedDevices

    // Merged sensor data: real BLE OR demo
    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData.asStateFlow()

    private var demoRunning = false

    init {
        // Forward real BLE data
        viewModelScope.launch {
            bleManager.sensorData.collect { data ->
                if (!demoRunning && data != null) _sensorData.value = data
            }
        }
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(address: String) = bleManager.connect(address)
    fun disconnect() = bleManager.disconnect()

    /** Simulate plant sensor readings without real hardware */
    fun startDemoMode() {
        if (demoRunning) return
        demoRunning = true
        viewModelScope.launch {
            var tick = 0
            while (demoRunning) {
                val t = tick.toDouble()
                _sensorData.value = SensorData(
                    moisture    = (50 + 30 * sin(t / 20)).toFloat() + Random.nextFloat() * 2,
                    temperature = (22 + 3  * sin(t / 40)).toFloat() + Random.nextFloat() * 0.5f,
                    light       = ((tick % 100).toFloat()).coerceIn(0f, 100f),
                    battery     = (100f - tick * 0.05f).coerceAtLeast(10f)
                )
                delay(1000)
                tick++
            }
        }
    }

    fun stopDemoMode() {
        demoRunning = false
        _sensorData.value = null
    }

    override fun onCleared() {
        super.onCleared()
        demoRunning = false
        bleManager.disconnect()
    }
}
