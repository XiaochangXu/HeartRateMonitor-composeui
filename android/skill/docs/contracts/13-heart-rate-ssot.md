# 契约 13：心率数据流 SSOT（2026-09 Repository 化迁移，禁止回退）

违反即视为破坏架构。

## 单一事实来源

实时心率/测量/速度/扫描/连接状态/图表快照流一律经 `HeartRateRepository`（`:service`，`@Singleton`）暴露；落盘缓冲经 `HeartRateRecorder`（`:data:repository`，包名保持不变）。

**禁止**：
- 新建第二条实时数据通道
- 从 `BleService`/`BleConnectionHandler` 实例直接收集数据流

## 写入面收敛

只有以下组件可写 Repository，消费者一律只读：
- 采集引擎（`BleConnectionHandler`）
- `SpeedProvider`
- `BleService.onCreate` 的对账调用（`resetForNewServiceInstance`，见下）

## 分层落点

- Repository 在 `:service`（因 `BleState` 持有 service 资源与 Context，下沉 data 层需先迁移领域模型，见 spec 修订记录）
- 落盘在 `:data:repository`
- 控制命令仍走 `BleConnectionManager`/Binder（契约 3）

## 敏感语义原样保留

图表 500ms 快照节流、落盘 5s 批量 + 异常分级重试、缓冲上限、Tracker 随连接生灭的 reset/clear 调用点——调参或调整调用顺序前必须先读 `SessionChartTracker` / `HeartRateRecorder` 内注释。

## 性能基线不变

- 数值即时（StateFlow 直连）
- 图表 ≤500ms
- 落盘 ≤5s

**不得**在 Repository 层新增轮询或全量拷贝转发。

## 服务重建对账（不得移除）

状态宿主进程级化后，Service 重建不再自然自愈，必须显式对账：

1. `BleService.onCreate` 必须调用 `heartRateRepository.resetForNewServiceInstance()` 清零上一实例残留的连接态。

   服务被杀重启后若不清零，UI 将展示幽灵连接：首页图表显示未连接、设备页显示已连接、断开命令因新 Handler 无活动任务而静默落空。

2. `BleService.onCreate` 必须调用 `bleControlPlaneRegistry.register(this)`、`onDestroy` 调用 `unregister(this)`（控制面引用随服务重建更新，见契约 3）。

3. `MainViewModel` 构造期必须按 `Repository.bleState.value` 恢复 appStatus（bleState 订阅 drop(1) 跳过首帧重放，值流重放无法恢复它）。
