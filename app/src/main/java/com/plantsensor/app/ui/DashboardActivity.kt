package com.plantsensor.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plantsensor.app.data.BleState
import com.plantsensor.app.data.SensorData
import com.plantsensor.app.databinding.ActivityDashboardBinding
import com.plantsensor.app.viewmodel.SensorViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE_NAME    = "device_name"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEMO_MODE      = "demo_mode"
    }

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: SensorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val demoMode    = intent.getBooleanExtra(EXTRA_DEMO_MODE, false)
        val deviceName  = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Demo Mode"
        val address     = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""

        setupToolbar(deviceName, demoMode)
        setupCardLabels()
        setupDisconnectButton(demoMode)
        observeSensorData()
        observeBleState(demoMode)

        if (demoMode) {
            viewModel.startDemoMode()
        } else if (address.isNotEmpty()) {
            viewModel.connect(address)
        }
    }

    private fun setupCardLabels() {
        binding.cardMoisture.tvIcon.text    = "💧"
        binding.cardMoisture.tvLabel.text   = "Moisture"
        binding.cardTemperature.tvIcon.text = "🌡️"
        binding.cardTemperature.tvLabel.text = "Temperature"
        binding.cardLight.tvIcon.text       = "☀️"
        binding.cardLight.tvLabel.text      = "Light"
        binding.cardBattery.tvIcon.text     = "🔋"
        binding.cardBattery.tvLabel.text    = "Battery"
    }

    private fun setupToolbar(deviceName: String, demoMode: Boolean) {
        binding.toolbar.title = if (demoMode) "Demo — Plant Sensor" else deviceName
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDisconnectButton(demoMode: Boolean) {
        binding.btnDisconnect.text = if (demoMode) "Stop Demo" else "Disconnect"
        binding.btnDisconnect.setOnClickListener {
            if (demoMode) viewModel.stopDemoMode()
            else viewModel.disconnect()
            finish()
        }
    }

    // ────────────────────────────── Observers ──────────────────────────────

    private fun observeSensorData() {
        lifecycleScope.launch {
            viewModel.sensorData.collect { data ->
                if (data != null) {
                    binding.groupNoData.visibility = View.GONE
                    binding.groupCards.visibility  = View.VISIBLE
                    updateCards(data)
                } else {
                    binding.groupNoData.visibility = View.VISIBLE
                    binding.groupCards.visibility  = View.GONE
                }
            }
        }
    }

    private fun observeBleState(demoMode: Boolean) {
        if (demoMode) return
        lifecycleScope.launch {
            viewModel.bleState.collect { state ->
                when (state) {
                    is BleState.Disconnected -> {
                        binding.tvConnectionStatus.text = "Disconnected"
                        binding.ivConnectionDot.setColorFilter(Color.RED)
                    }
                    is BleState.Connected -> {
                        binding.tvConnectionStatus.text = "Connected"
                        binding.ivConnectionDot.setColorFilter(Color.parseColor("#4CAF50"))
                    }
                    is BleState.Error -> {
                        binding.tvConnectionStatus.text = "Error"
                        binding.ivConnectionDot.setColorFilter(Color.RED)
                    }
                    else -> {}
                }
            }
        }
    }

    // ────────────────────────────── UI Update ──────────────────────────────

    private fun updateCards(data: SensorData) {
        // Moisture
        val moisture = data.moisture.roundToInt()
        binding.cardMoisture.tvValue.text   = "$moisture%"
        binding.cardMoisture.progressBar.progress = moisture
        binding.cardMoisture.tvStatus.text  = moistureStatus(moisture)
        binding.cardMoisture.tvStatus.setTextColor(moistureColor(moisture))

        // Temperature
        val temp = "%.1f°C".format(data.temperature)
        binding.cardTemperature.tvValue.text  = temp
        binding.cardTemperature.progressBar.progress = ((data.temperature + 10) / 50 * 100).roundToInt().coerceIn(0, 100)
        binding.cardTemperature.tvStatus.text = tempStatus(data.temperature)
        binding.cardTemperature.tvStatus.setTextColor(tempColor(data.temperature))

        // Light
        val light = data.light.roundToInt()
        binding.cardLight.tvValue.text   = "$light%"
        binding.cardLight.progressBar.progress = light
        binding.cardLight.tvStatus.text  = lightStatus(light)
        binding.cardLight.tvStatus.setTextColor(lightColor(light))

        // Battery
        val battery = data.battery.roundToInt()
        binding.cardBattery.tvValue.text   = "$battery%"
        binding.cardBattery.progressBar.progress = battery
        binding.cardBattery.tvStatus.text  = batteryStatus(battery)
        binding.cardBattery.tvStatus.setTextColor(batteryColor(battery))

        // Last update
        binding.tvLastUpdate.text = "Last updated: just now"
    }

    // ────────────────────────────── Status helpers ──────────────────────────────

    private fun moistureStatus(v: Int) = when {
        v < 20  -> "Too Dry"
        v < 40  -> "Dry"
        v < 70  -> "Optimal"
        v < 85  -> "Moist"
        else    -> "Overwatered"
    }
    private fun moistureColor(v: Int) = when {
        v < 20  -> Color.parseColor("#F44336")
        v < 40  -> Color.parseColor("#FF9800")
        v < 70  -> Color.parseColor("#4CAF50")
        v < 85  -> Color.parseColor("#2196F3")
        else    -> Color.parseColor("#9C27B0")
    }

    private fun tempStatus(v: Float) = when {
        v < 10  -> "Too Cold"
        v < 16  -> "Cool"
        v < 28  -> "Optimal"
        v < 34  -> "Warm"
        else    -> "Too Hot"
    }
    private fun tempColor(v: Float) = when {
        v < 10  -> Color.parseColor("#2196F3")
        v < 16  -> Color.parseColor("#03A9F4")
        v < 28  -> Color.parseColor("#4CAF50")
        v < 34  -> Color.parseColor("#FF9800")
        else    -> Color.parseColor("#F44336")
    }

    private fun lightStatus(v: Int) = when {
        v < 10  -> "Dark"
        v < 30  -> "Low Light"
        v < 70  -> "Good Light"
        v < 90  -> "Bright"
        else    -> "Very Bright"
    }
    private fun lightColor(v: Int) = when {
        v < 10  -> Color.parseColor("#9E9E9E")
        v < 30  -> Color.parseColor("#FF9800")
        v < 70  -> Color.parseColor("#4CAF50")
        v < 90  -> Color.parseColor("#FFC107")
        else    -> Color.parseColor("#FF5722")
    }

    private fun batteryStatus(v: Int) = when {
        v < 10  -> "Critical"
        v < 25  -> "Low"
        v < 50  -> "Medium"
        v < 80  -> "Good"
        else    -> "Full"
    }
    private fun batteryColor(v: Int) = when {
        v < 10  -> Color.parseColor("#F44336")
        v < 25  -> Color.parseColor("#FF9800")
        v < 50  -> Color.parseColor("#FFC107")
        else    -> Color.parseColor("#4CAF50")
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopDemoMode()
    }
}
