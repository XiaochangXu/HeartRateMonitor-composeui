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
- `MainActivity` 绑定 `BleService` 后将实例注册进 `BleControlPlaneRegistry`
- StatusBarResidentService / FloatingWindowService 绑定仅为确保前台服务存在，数据已从 Repository 直订

## 控制面获取（BleControlPlaneRegistry，2026-09 多 Activity 迁移新增）

`@Singleton BleControlPlaneRegistry` 进程级持有活服务引用（`StateFlow<BleConnectionManager?>`）：

- `BleService.onCreate` 必须 `register(this)`、`onDestroy` 必须 `unregister(this)`，**不得移除**——多 Activity 下各 `MainViewModel` 实例经此获取控制面，注册缺失 = 扫描/连接/断开静默失效
- ViewModel/UI 侧经 `registry.manager.value` 取 `BleConnectionManager`，仍禁止依赖具体 `BleService` 类
- `MainViewModel.setControlPlane` 弱引用注入**已删除**（多 Activity 下双实例各自持弱引用，命令会发往死实例）

## BleService 职责

`BleService` 仅承担生命周期编排：
- 连接状态机逻辑归 `BleConnectionHandler`
- 前台通知归 `BleNotificationManager`
- 新增同类逻辑应放入对应组件而非 BleService

## 服务启停

统一经注入的 `ServiceLauncher`（Hilt 绑定 ServiceController）。

**禁止**在 UI 层直接 `startService(Intent(...))`。
