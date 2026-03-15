package com.plantsensor.app.data

data class SensorData(
    val moisture: Float,      // 0–100 %
    val temperature: Float,   // °C
    val light: Float,         // 0–100 % (lux mapped)
    val battery: Float,       // 0–100 %
    val timestamp: Long = System.currentTimeMillis()
)

sealed class BleState {
    object Disconnected : BleState()
    object Scanning : BleState()
    data class DeviceFound(val name: String, val address: String) : BleState()
    data class Connecting(val address: String) : BleState()
    data class Connected(val name: String, val address: String) : BleState()
    data class Error(val message: String) : BleState()
}

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)
