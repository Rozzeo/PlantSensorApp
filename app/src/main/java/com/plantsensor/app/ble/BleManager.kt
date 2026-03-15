package com.plantsensor.app.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.plantsensor.app.data.BleDevice
import com.plantsensor.app.data.BleState
import com.plantsensor.app.data.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Handles BLE scanning, connection, and GATT communication.
 *
 * Plant Sensor Service UUID (customize to match your hardware):
 *   Service:      0000181A-0000-1000-8000-00805F9B34FB  (Environmental Sensing)
 *   Moisture:     00002A6F-0000-1000-8000-00805F9B34FB
 *   Temperature:  00002A6E-0000-1000-8000-00805F9B34FB
 *   Light:        00002A77-0000-1000-8000-00805F9B34FB
 *   Battery:      00002A19-0000-1000-8000-00805F9B34FB
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000181A-0000-1000-8000-00805F9B34FB")
        val MOISTURE_UUID: UUID = UUID.fromString("00002A6F-0000-1000-8000-00805F9B34FB")
        val TEMPERATURE_UUID: UUID = UUID.fromString("00002A6E-0000-1000-8000-00805F9B34FB")
        val LIGHT_UUID: UUID = UUID.fromString("00002A77-0000-1000-8000-00805F9B34FB")
        val BATTERY_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private const val SCAN_PERIOD_MS = 10_000L
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _bleState = MutableStateFlow<BleState>(BleState.Disconnected)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var currentMoisture = 0f
    private var currentTemperature = 0f
    private var currentLight = 0f
    private var currentBattery = 100f

    // ────────────────────────────── Scanning ──────────────────────────────

    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            _bleState.value = BleState.Error("Bluetooth scan permission denied")
            return
        }
        _scannedDevices.value = emptyList()
        _bleState.value = BleState.Scanning

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(filters, settings, scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            bleScanner?.stopScan(scanCallback)
        }
        if (_bleState.value is BleState.Scanning) {
            _bleState.value = BleState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            val device = result.device
            val name = device.name ?: "Unknown Plant Sensor"
            val address = device.address
            val rssi = result.rssi

            val current = _scannedDevices.value.toMutableList()
            if (current.none { it.address == address }) {
                current.add(BleDevice(name, address, rssi))
                _scannedDevices.value = current
            }
            _bleState.value = BleState.DeviceFound(name, address)
        }

        override fun onScanFailed(errorCode: Int) {
            _bleState.value = BleState.Error("Scan failed: error $errorCode")
        }
    }

    // ────────────────────────────── Connection ──────────────────────────────

    fun connect(address: String) {
        stopScan()
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _bleState.value = BleState.Error("Bluetooth connect permission denied")
            return
        }
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            _bleState.value = BleState.Error("Device not found: $address")
            return
        }
        _bleState.value = BleState.Connecting(address)
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _bleState.value = BleState.Disconnected
        _sensorData.value = null
    }

    // ────────────────────────────── GATT Callback ──────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected, discovering services…")
                    _bleState.value = BleState.Connected(
                        gatt.device.name ?: "Plant Sensor",
                        gatt.device.address
                    )
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected")
                    _bleState.value = BleState.Disconnected
                    gatt.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _bleState.value = BleState.Error("Service discovery failed: $status")
                return
            }
            enableNotifications(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseCharacteristic(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                parseCharacteristic(characteristic.uuid, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseCharacteristic(characteristic.uuid, value)
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val service = gatt.getService(SERVICE_UUID) ?: run {
            _bleState.value = BleState.Error("Plant Sensor service not found")
            return
        }
        listOf(MOISTURE_UUID, TEMPERATURE_UUID, LIGHT_UUID, BATTERY_UUID).forEach { uuid ->
            service.getCharacteristic(uuid)?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                char.getDescriptor(CCCD_UUID)?.let { desc ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(desc)
                    }
                }
            }
        }
    }

    private fun parseCharacteristic(uuid: UUID, value: ByteArray) {
        when (uuid) {
            MOISTURE_UUID    -> currentMoisture    = (value.toUInt16() / 100f).coerceIn(0f, 100f)
            TEMPERATURE_UUID -> currentTemperature = value.toInt16() / 100f
            LIGHT_UUID       -> currentLight       = (value.toUInt16() / 100f).coerceIn(0f, 100f)
            BATTERY_UUID     -> currentBattery     = (value[0].toInt() and 0xFF).toFloat()
        }
        _sensorData.value = SensorData(
            moisture = currentMoisture,
            temperature = currentTemperature,
            light = currentLight,
            battery = currentBattery
        )
    }

    // ────────────────────────────── Helpers ──────────────────────────────

    private fun ByteArray.toUInt16(): Int =
        if (size >= 2) ((this[1].toInt() and 0xFF) shl 8) or (this[0].toInt() and 0xFF)
        else this[0].toInt() and 0xFF

    private fun ByteArray.toInt16(): Int {
        val raw = toUInt16()
        return if (raw > 32767) raw - 65536 else raw
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val isBluetoothEnabled get() = bluetoothAdapter?.isEnabled == true
}
