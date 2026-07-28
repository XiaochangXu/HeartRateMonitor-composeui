<div align="right">
  <a href="./README_EN.md">English</a> | <strong>简体中文</strong>
</div>
                                        - [项目规范](./SKILL.md)
<div align="center">
  <img src="app/src/main/res/drawable/about.png" alt="HeartRateMonitor" width="128" />

  <h1>心率监控器 - HeartRateMonitor</h1>

  <p><strong>让 Android 心率监测更优雅。</strong></p>

  <p>基于 BLE（蓝牙低功耗）技术的 Android 心率监测应用，采用 Material 3 设计规范。</p>

  <p>
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" /></a>
    <img src="https://img.shields.io/badge/platform-Android-green?logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-orange?logo=jetpackcompose&logoColor=white" alt="UI" />
    <img src="https://img.shields.io/badge/Material-3%20Expressive-purple" alt="Material" />
    <img src="https://img.shields.io/badge/minSdk-24-green" alt="minSdk" />
    <img src="https://img.shields.io/badge/targetSdk-37-green" alt="targetSdk" />
    <a href="https://github.com/XiaochangXu/HeartRateMonitor-composeui/releases/latest"><img src="https://img.shields.io/github/v/release/XiaochangXu/HeartRateMonitor-composeui?label=release&color=orange" alt="Latest release" /></a>
    <a href="https://github.com/XiaochangXu/HeartRateMonitor-composeui/commits/main"><img src="https://img.shields.io/github/last-commit/XiaochangXu/HeartRateMonitor-composeui" alt="Last commit" /></a>
  </p>
</div>

-----

## ✨ 功能特性

- 🖥️ **全屏模式**：沉浸式显示心率数据，让手机成为健康监护仪
- 🔵 **蓝牙连接**：扫描并连接支持心率服务的 BLE 设备
- ⭐ **设备管理**：收藏常用设备，支持自动连接与断开重连
- ❤️ **心跳动画**：根据心率跳动频率动态变化
- 📊 **心率历史与图表**：自动记录、历史列表、批量管理、图表分析、横屏查看
- 🎨 **个性化设置**：功能开关、悬浮窗样式自定义
- 📡 **数据接口**：HTTP 服务器、WebSocket 服务器、Webhook 推送
- ​🖼️ **悬浮窗显示心率**：在手机屏幕上显示心率，不受到应用内容的影响
- 📌 **状态栏常驻心率**：状态栏显示实时心率
- 🔔 **心率预警**：结合姿态检测，心率超限自动通知与震动
- 🧠 **公平运行内存**：适配国产厂商内存管理机制
- 🎨 **颜色选择器**：自绘 HSV 色轮
- ✨ **流畅转场动画** 实时模糊缩放
- 🧊 **液态玻璃效果** 实现底部导航栏的液态玻璃效果
- 🎯 **Material 3 动态取色**

-----

## 🖼️ 截图展示

<table>
  <tr>
    <td align="center"><img src="images/1.jpg" width="270"/><br/><sub>实时心率监测主页</sub></td>
    <td align="center"><img src="images/2.jpg" width="270"/><br/><sub>心率历史与图表分析</sub></td>
    <td align="center"><img src="images/3.jpg" width="270"/><br/><sub>个性化设置与悬浮窗</sub></td>
    <td align="center"><img src="images/4.jpg" width="270"/><br/><sub>版本与详细信息</sub></td>
   </tr>
</table>

-----

## 🚀 安装与运行

1. **克隆项目**

    ```bash
    git clone https://github.com/XiaochangXu/HeartRateMonitor-composeui.git
    ```

2. **打开项目**
    - 使用 **Android Studio** 打开项目文件夹
    - 等待 **Gradle** 自动同步依赖

3. **构建并运行**
    - 使用真机或模拟器（API ≥ 24）连接
    - 点击工具栏中的 ▶️ 运行按钮

-----

## 🧭 使用指南

1. **首次权限授予**：允许蓝牙权限与定位权限
2. **连接心率设备**：先在设备上开启心率广播，再点击扫描按钮，选择设备连接
3. **查看历史记录**：点击工具栏历史图标进入历史列表，单击查看图表
4. **使用悬浮窗**：主页工具栏开关，设置中自定义外观
5. **状态栏常驻**：设置 → 状态栏心率，开启后状态栏显示心率
6. **心率预警**：设置 → 心率预警，配置阈值与姿态校准
7. **数据接口**：设置 → 数据与服务，配置 HTTP/WebSocket/Webhook

-----

## 🙏 致谢

**核心依赖**

- UI 框架：[Jetpack Compose](https://developer.android.com/jetpack/compose) · [Material 3](https://m3.material.io/)
- 蓝牙：[Kable](https://github.com/JuulLabs/kable)
- 图表：[Vico](https://github.com/patrykandpatrick/vico)
- 数据库：[Room](https://developer.android.com/training/data-storage/room)
- 动态取色：[MaterialKolor](https://github.com/jordond/MaterialKolor)
- HTTP/WebSocket 服务：[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)
- 权限：[PermissionX](https://github.com/guolindev/PermissionX)
- 液态玻璃：[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- G2圆角：[Capsule](https://github.com/Kyant0/Capsule)

-----

## 🗺️ Roadmap

### ✅ 核心功能

- [x] BLE 心率设备扫描与连接
- [x] 心率历史记录与图表分析
- [x] 悬浮窗 / 状态栏常驻心率
- [x] HTTP / WebSocket / Webhook 数据接口
- [x] 心率预警 + 姿态检测
- [x] Material 3 动态取色
- [x] 自绘 HSV 颜色选择器

-----

## 📄 License

[MIT](./LICENSE) © 2026 XiaochangXu
