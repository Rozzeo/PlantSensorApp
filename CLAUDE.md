# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Open in Android Studio → **File → Open** → select `PlantSensorApp/`.

```bash
# Assemble debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lint

# Clean build
./gradlew clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

Single-module Android app (Kotlin, View Binding, MVVM + StateFlow).

```
ble/BleManager.kt          — BLE scan, GATT connect/read/notify
data/SensorData.kt         — SensorData, BleState, BleDevice data classes
viewmodel/SensorViewModel.kt — merges real BLE data + demo simulation
ui/MainActivity.kt         — BLE scan screen, device list
ui/DashboardActivity.kt    — 4-sensor dashboard (moisture/temp/light/battery)
ui/DeviceListAdapter.kt    — RecyclerView for scanned BLE devices
```

**Data flow:** `BleManager` exposes `StateFlow<SensorData?>` → `SensorViewModel` forwards it (or injects demo data) → Activities collect via `lifecycleScope.launch`.

**BLE UUIDs** (Environmental Sensing Service, customise to match hardware):
- Service: `0000181A-...`
- Moisture: `00002A6F`, Temperature: `00002A6E`, Light: `00002A77`, Battery: `00002A19`

## Key Conventions

- **Demo mode**: `SensorViewModel.startDemoMode()` simulates sine-wave sensor readings every 1 s — no hardware needed. Launched from MainActivity via `EXTRA_DEMO_MODE = true`.
- **Permissions**: BLE scan/connect permissions are requested at runtime in `MainActivity.checkPermissionsAndScan()`. Android 12+ uses `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`; older versions use `ACCESS_FINE_LOCATION`.
- **View Binding** is enabled; all layout IDs are accessed through typed binding objects — never `findViewById`.
- **Theme**: Material3 Light, green palette (`primary = #2E7D32`), defined in `res/values/themes.xml` and `colors.xml`.
- `gradle.properties` must keep `android.useAndroidX=true` and `android.enableJetifier=true`.
