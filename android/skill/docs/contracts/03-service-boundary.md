# 契约 3：Service 抽象边界

违反即视为破坏架构。

## 数据面

实时心率/测量/速度/扫描/连接状态/图表流：ViewModel/UI 一律依赖 `HeartRateRepository`（:service 进程级 @Singleton SSOT，2026-09 迁移，见契约 13），构造注入直出 StateFlow。

**禁止**经 Binder/Service 实例获取数据流。

## 控制面

扫描/连接/断开命令与服务启停：依赖 `BleConnectionManager` 与 `ServiceLauncher` 接口。

**禁止**依赖具体 `BleService` / `ServiceController` 类。

## 例外

Activity/Service 通过 Binder 绑定具体 Service 属绑定机制，允许保留具体类型：
- `MainViewModel.setControlPlane` 注入控制命令通道
- StatusBarResidentService / FloatingWindowService 绑定仅为确保前台服务存在，数据已从 Repository 直订

## BleService 职责

`BleService` 仅承担生命周期编排：
- 连接状态机逻辑归 `BleConnectionHandler`
- 前台通知归 `BleNotificationManager`
- 新增同类逻辑应放入对应组件而非 BleService

## 服务启停

统一经注入的 `ServiceLauncher`（Hilt 绑定 ServiceController）。

**禁止**在 UI 层直接 `startService(Intent(...))`。
