# 🌱 Plant Sensor App

An Android app written in **Kotlin** that connects to a BLE plant sensor and displays real-time readings on a dashboard — moisture, temperature, light, and battery level.

---

## Features

| Feature | Description |
|---|---|
| 📡 **BLE Scan** | Discovers nearby plant sensors via Bluetooth Low Energy |
| 🔗 **Auto-connect** | Connects to a device and reads GATT characteristics |
| 💧 **Moisture** | Soil moisture % with status label (Too Dry / Optimal / Overwatered) |
| 🌡️ **Temperature** | Air temperature in °C with status (Too Cold / Optimal / Too Hot) |
| ☀️ **Light** | Light intensity % with status (Dark / Good Light / Very Bright) |
| 🔋 **Battery** | Sensor battery % with status (Critical / Low / Full) |
| 🎮 **Demo Mode** | Simulates live sensor data without any hardware |

---

## Screenshots

| BLE Scan Screen | Sensor Dashboard |
|---|---|
| Lists nearby BLE devices | 4 real-time sensor cards |

---

## Tech Stack

- **Kotlin** — primary language
- **Material Design 3** — UI components and theming
- **BLE (Bluetooth Low Energy)** — sensor communication via GATT
- **MVVM + StateFlow** — architecture pattern
- **View Binding** — type-safe UI access
- **Coroutines** — async data handling

---

## Project Structure

```
PlantSensorApp/
├── app/src/main/
│   ├── java/com/plantsensor/app/
│   │   ├── ble/
│   │   │   └── BleManager.kt          # BLE scanning, GATT connect, characteristic parsing
│   │   ├── data/
│   │   │   └── SensorData.kt          # Data classes: SensorData, BleState, BleDevice
│   │   ├── ui/
│   │   │   ├── MainActivity.kt        # Scan screen + device list
│   │   │   ├── DashboardActivity.kt   # 4-card sensor dashboard
│   │   │   └── DeviceListAdapter.kt   # RecyclerView adapter for BLE devices
│   │   └── viewmodel/
│   │       └── SensorViewModel.kt     # Merges real BLE + demo data streams
│   └── res/
│       ├── layout/                    # XML layouts
│       ├── values/                    # Colors, strings, theme
│       └── drawable/                  # Icons and shapes
├── gradle/
│   └── libs.versions.toml             # Centralized dependency versions
└── gradle.properties                  # Build config (AndroidX enabled)
```

---

## Getting Started

### Prerequisites
- Android Studio (latest stable) — [download here](https://developer.android.com/studio)
- Android device or emulator running API 26+

### Run the app

1. Clone the repo:
```bash
git clone https://github.com/Rozzeo/PlantSensorApp.git
```

2. Open in Android Studio:
```
File → Open → select PlantSensorApp/
```

3. Let Gradle sync, then hit ▶️ **Run**

4. No hardware? Tap **"Try Demo Mode"** on the scan screen.

### Build APK

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## BLE Protocol

The app connects to devices advertising the **Environmental Sensing Service**:

| Characteristic | UUID |
|---|---|
| Service | `0000181A-0000-1000-8000-00805F9B34FB` |
| Moisture | `00002A6F-0000-1000-8000-00805F9B34FB` |
| Temperature | `00002A6E-0000-1000-8000-00805F9B34FB` |
| Light | `00002A77-0000-1000-8000-00805F9B34FB` |
| Battery | `00002A19-0000-1000-8000-00805F9B34FB` |

Data format: `uint16`, little-endian, value × 100 (e.g. `5050` = 50.50%)

---

## Permissions

Requested at runtime:

- **Android 12+:** `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- **Android 8–11:** `ACCESS_FINE_LOCATION` (required for BLE scanning)

---

## Database (Planned)

Currently sensor readings are **in-memory only** — data is lost when the app closes.

Planned addition: **Room database** to store historical readings and enable:
- 📊 Weekly moisture/temperature charts
- 🔔 Low moisture notifications
- 📅 Full reading history

---

## How It Works

```
Plant Sensor  →  Bluetooth  →  Android Phone  →  This App
(ESP32/Arduino)    (BLE)        (API 26+)       (Dashboard)
```

`BleManager` scans and connects → `SensorViewModel` holds the data stream → `DashboardActivity` renders the UI every second.

---

*Made with ❤️ for plants*
