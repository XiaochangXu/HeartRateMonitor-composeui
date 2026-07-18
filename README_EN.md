# ❤️ Heart Rate Monitor

![Platform](https://img.shields.io/badge/platform-Android-green)
![Language](https://img.shields.io/badge/language-Kotlin-blue)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-orange)
![Material](https://img.shields.io/badge/Material-3%20Expressive-purple)

[中文文档](README.md) | **English**

> An Android heart rate monitoring app based on BLE (Bluetooth Low Energy) technology, following Material 3 design guidelines.

-----

## ✨ Features

- 🔵 **Bluetooth Connection**: Scan and connect to BLE devices that support heart rate services
- ⭐ **Device Management**: Favorite frequently used devices, with auto-connect and disconnect-reconnect support
- ❤️ **Heartbeat Animation**: Dynamically changes based on heart rate
- 📊 **Heart Rate History & Charts**: Auto-recording, history list, batch management, chart analysis, landscape view
- 🎨 **Personalization**: Feature toggles, floating window style customization
- 📡 **Data Interfaces**: HTTP server, WebSocket server, Webhook push
- 📌 **Status Bar Heart Rate**: Display real-time heart rate in the status bar with automatic background color detection
- 🔔 **Heart Rate Alert**: Posture detection combined with threshold-based notifications and vibration
- 🧠 **Fair Memory Management**: Adapted to domestic vendor memory management mechanisms
- 🎨 **Color Picker**: Self-drawn HSV color wheel
- ✨ **Smooth Transition Animations** real-time blur scaling
- 🎯 **Material 3 Dynamic Color**

-----

## 🖼️ Screenshots

<div style="display: flex; justify-content: center; gap: 12px; flex-wrap: wrap;">
  <img src="images/1.jpg" width="270"/>
  <img src="images/2.jpg" width="270"/>
  <img src="images/3.jpg" width="270"/>
  <img src="images/4.jpg" width="270"/>
  <img src="images/5.jpg" width="270"/>
</div>

-----

## 🚀 Installation & Running

1. **Clone the project**

    ```bash
    git clone https://github.com/XiaochangXu/HeartRateMonitor-composeui.git
    ```

2. **Open the project**
    - Open the project folder with **Android Studio**
    - Wait for **Gradle** to automatically sync dependencies

3. **Build and run**
    - Connect a real device or emulator (API ≥ 27)
    - Click the ▶️ Run button in the toolbar

-----

## 🧭 Usage Guide

1. **Grant permissions**: Allow Bluetooth and location permissions on first launch
2. **Connect a heart rate device**: Tap the scan button on the home page and select a device
3. **View history**: Tap the history icon in the toolbar to open the list, then tap an entry to view charts
4. **Use the floating window**: Toggle from the home toolbar, customize appearance in Settings
5. **Status bar persistence**: Settings → Status Bar Heart Rate, enable to show heart rate in the status bar
6. **Heart rate alerts**: Settings → Heart Rate Alert, configure thresholds and posture calibration
7. **Data interfaces**: Settings → Data & Services, configure HTTP/WebSocket/Webhook

-----

## 🙏 Acknowledgements

- Chart library: [Vico](https://github.com/patrykandpatrick/vico)
- Bluetooth library: [Kable](https://github.com/JuulLabs/kable)
