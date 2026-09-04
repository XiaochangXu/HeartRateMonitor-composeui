# 契约 6：敏感机制（修改前必须理解，不得"顺手简化"）

以下机制有深层原因，修改前必须阅读对应代码注释，不得凭直觉"优化"。

## BLE 纪元机制（connectEpoch）

防止旧自动重连误取消新连接，跨组件传递时不得省略纪元校验。

## 心率新鲜度自适应超时（HeartRateFreshnessTracker）

- SUSPECT 暂停预警
- STALE 全链路清零
- 降级链路依赖 rate <= 0 约定

## SettingsRepository 写后立读

依赖「构造期 runBlocking 预热内存快照 + setter 乐观 CAS 更新（`prefsState.update`，不得改回非原子读改写）」。
- 主题冷启动等首帧前同步读依赖此预热，不得改为异步加载
- `AppSettings` 快照（`settings` StateFlow）与 `prefsState` 同源同步更新，不得单独异步构建
- DataStore edit 串行化导致旧快照发射瞬态回退乐观值的限制已在类注释声明，属预期行为

## StateFlow 收集模拟原 listener 语义

先 `.drop(1)` 跳过初始发射。

## KillStateSaver.save 的 runBlocking 同步落盘

是 KILL 场景的硬约束（进程随时被杀，异步 launch 会丢数据）。

**不得改为 `set()` 即发即忘。**

## FairMemoryReceiver TRIM/KILL 回调

落盘与缓存释放顺序不得调整。

## HeartRateRecorder.flushPendingRecords 的取消语义

2026-09 迁移后组件位于 :data:repository，包名保持不变，语义红线不变：
- `CancellationException` 必须「记录回放缓冲区 + 重抛」
- **禁止**并入普通 `Exception` 分支吞掉——否则与 onDestroy「先 drainPendingRecords 入队 Worker、后 serviceScope.cancel()」的顺序叠加，落盘中的批次会被静默丢弃
- flush 循环同样不得吞取消

## BleBroadcastManager 的 200ms 节流

只允许作用于高频心率包。
- 连接/断开迁移、状态文案变化、心率清零等终态事件必须直发
- 否则断开广播落在节流窗口内被丢弃后，WS 客户端永久停留在 connected=true 的陈旧状态

## SettingsRepository.set/remove 落盘协程的异常防护

IO 失败记录日志、不炸进程，不得移除。
- 内存乐观快照与磁盘值的瞬态分叉属已声明限制
- 磁盘写失败不得让进程崩溃
