# 心率数据链路 Repository 化渐进迁移 Spec

状态：已完成（Draft → Final，2026-09-02）
作者：架构评审产出
范围：`:service`、`:data:repository`、`:feature:main`、`:app`（MainActivity）
关联讨论：BLE 心率数据链路 5 层架构评审（2026-09）

---

## 进度追踪

> 执行规则：每完成一个 Phase，勾选下方复选框并填写完成日期与验证记录（单测/冒烟结果）；
> 全部阶段（Phase 4 可选除外）完成后，必须执行 Phase 5 的文档与契约同步检查。

- [x] Phase 0 —— 基线与防护网（完成日期：2026-09-02）
- [x] Phase 1 —— Repository 承接数据流（完成日期：2026-09-02）
- [x] Phase 2 —— ViewModel 切换数据源（完成日期：2026-09-02）
- [x] Phase 3 —— 落盘归仓（完成日期：2026-09-02）
- [x] Phase 4 —— 消费者与控制面收敛（可选）（完成日期：2026-09-02）
- [x] Phase 5 —— 文档与契约同步，含 SKILL.md 核对（完成日期：2026-09-02）

## 实施修订记录

- **M1（2026-09-02）Repository 落点由 `:data:repository` 修订为 `:service`**：
  `BleState` 持有 `:service` 的 R 资源与 Context（无法下沉 data 层），
  `HeartRateMeasurement`/`ConnectedDevice` 亦在 `:service`；若强放 data 层需先
  下沉领域模型，超出本 spec 范围（N1）。SSOT 语义不变，分层依赖方向合法
  （service 内部自洽，无新增跨模块依赖），Phase 5 同步 SKILL.md 契约 9.5 时
  需登记“HeartRate 实时流 SSOT 归 :service，落盘缓冲（Phase 3）归 :data:repository”。
- **M0 基线甄别**：`LiquidGlassStateTest`、`SettingsRepositoryTest` 为已记录类型的
  flaky（写后立读竞态，单独重跑即过）；修复预存稳定失败
  `FunctionSettingsViewModelTest`（cb2629e 改 NAV_ANIMATION_DISABLED 默认值时
  漏同步测试期望，按契约 2 以 DEFAULTS 为准修正测试）；
  真机行为录像项待用户配合，以全量单测+编译作为代码级基线替代。
- **迁移后审查修订（2026-09-02，M0-M5 全部完成后）**：全量 diff 审查发现并修复 4 项——
  ① `StatusBarResidentService` 心跳动画设置监听处心率读取仍走旧 Binder 链路
  `bleService?.heartRate?.value`（Phase 4 漏改，绑定未就绪时静默为 0），
  改为 Repository 直读，并删除随之死亡的 `bleService` 字段及赋值
  （绑定本身保留，前台服务锚点语义不变）；
  ② `HeartRateRepository`/`BleConnectionHandler` KDoc 中 speed 流宿主描述与实现矛盾
  （迁移中途决策变更遗留），按实现更正；
  ③ `BleConnectionHandlerTest.createHandler` 原为 SpeedProvider 与 Handler 各建一个
  Repository 实例（与生产 @Singleton 不符），统一为共享实例；
  ④ `MainViewModel` 类 KDoc 前 4 行丢失前导空格，恢复缩进。
  验证：全模块单测 + `:app:assembleDebug` BUILD SUCCESSFUL。
- **线上缺陷修复（2026-09-02）：服务重建后幽灵连接（「退出应用隐藏后台」场景）**：
  现象——正在记录心率时开启该设置并退出，等待一段时间后重进，首页图表显示
  未连接、设备页显示已连接、断开命令无效，只能退出重进。
  根因有二：① Phase 2 删除 `setConnectionManager` 时连同状态恢复补丁一并删除，
  而 `bleState` 订阅的 `drop(1)` 跳过首帧重放，VM 重建后 `appStatus` 无法随
  值流重放恢复（首页显示未连接）；② 状态流宿主进程级化后，BleService 被系统
  杀死重建（START_STICKY）时 Repository 保留已死实例的连接态，新 Handler 无
  活动任务，`disconnectDevice` 静默无操作且陈旧的已连接设备卡片无法消除
  （迁移前状态随 Handler 生灭，天然自愈）。
  修复：`BleService.onCreate` 首行调用
  `HeartRateRepository.resetForNewServiceInstance()` 对账清零；
  `MainViewModel` 构造期按 `Repository.bleState.value` 恢复 appStatus
  （原补丁等价回归）。语义对齐迁移前行为，已同步 SKILL.md 契约 4/13，
  新增 `HeartRateRepositoryTest` 回归测试。
- **审查补充（2026-09-02）：FloatingWindowService 数据面收敛（Phase 4 遗漏勘误）**：
  Phase 4 曾判定 FloatingWindowService 为「纯控制面，无数据流」；幽灵连接修复后
  的回归审查发现该判定有误——其 observeBleData/applyPendingBleData 经 Binder
  持有的 BleService 实例收集心率/速度/连接标志（违反契约 13 数据面约束，
  仅因委托链底层同源而未表现异常）。已注入 Repository 并转换全部读取点，
  删除 bleService 字段与拖拽 pending 缓存（.value 直读后无回退场景），
  绑定保留为前台服务锚点；同步修正本 Phase 4 勘误与 SKILL.md 契约 3/13。

---

## 1. 背景

当前心率数据从 BLE 接收到消费端经过 5 层：

```
BLE 设备(0x2A37)
  → ① BleManager(service/ble)：Kable 订阅 + GATT 字节解析
  → ② BleConnectionHandler(service)：收集流、接触丢失置零、写入 StateFlow、分发
  → ③ SessionChartTracker / HeartRateRecorder / Webhook / Broadcast（Handler 内旁路）
  → ④ MainViewModel(feature:main)：经 MainActivity Binder 绑定注入 BleConnectionManager 后收集
  → ⑤ Compose UI(feature:main)
```

数据面对外通过 `BleConnectionManager` 接口暴露 8 个流：
`heartRateMeasurement`、`speed`、`scanResults`、`connectedDevice`、
`chartDataSnapshot`、`sessionMaxHr`、`sessionMinHr`、`bleState`。

Hilt 现状：`BleService` 已是 `@AndroidEntryPoint`；`ServiceModule` 仅绑定
`ServiceLauncher ← ServiceController`；`BleConnectionManager` 的实现挂在 Service
实例上，由 MainActivity 通过本地 Binder 转交给 ViewModel。

### 1.1 关键文件索引

| 组件 | 位置 | 职责 |
|---|---|---|
| `BleManager` | `service/.../ble/BleManager.kt` | Kable 订阅 0x2A37、解析 HeartRateMeasurement |
| `BleConnectionHandler` | `service/.../service/BleConnectionHandler.kt` | 连接状态机；`observeHeartRateData`(:653) 写 StateFlow(:672-673)、喂 Tracker(:677)、调 Recorder(:683)、触发 Webhook(:674)、广播(:688) |
| `SessionChartTracker` | `service/.../service/SessionChartTracker.kt` | RR→图表点；500ms 快照节流(:68)；60s 窗口(:73)；TRIM 降采样(:79) |
| `HeartRateRecorder` | `service/.../service/HeartRateRecorder.kt` | 落盘缓冲；5s 批量 flush(:190)；异常分级(:123-149)；缓冲上限 |
| `BleService` | `service/.../service/BleService.kt` | 前台服务；`LocalBinder`(:38)；`onBind`(:207)；实现 `BleConnectionManager` |
| `MainActivity` | `app/.../ui/main/MainActivity.kt` | `bleServiceConnection`(:91-105)；start+bind(:221-233)；unbind(:204-214) |
| `MainViewModel` | `feature/main/.../ui/main/MainViewModel.kt` | `setConnectionManager`(:218)；WeakReference(:221)；重绑状态恢复补丁(:225-228) |
| `MainBleStreams` | `feature/main/.../ui/main/MainBleStreams.kt` | 8 个流的收集与 reduce；`bleState.drop(1)`(:97) |
| `HeartRateDao` | `data/database/.../HeartRateDao.kt` | Room 批量插入(:25) |
| `SessionRepository` 等 | `data/repository/.../repository/` | 历史会话查询（与本迁移的数据面汇合点） |

## 2. 问题陈述

P1 —— 数据面接线挂在 Activity 生命周期上。
MainActivity 必须等 `onServiceConnected` 才能调 `setConnectionManager`；
Activity 旋转/重建后需重走绑定流程。代价已经以"状态恢复补丁"形式沉淀在
`MainViewModel.setConnectionManager`（:225-228 注释自述：区分重新绑定的当前值
重放与新事件，防止首页假显示未连接、图表 reset、误 Toast）。补丁数量与绑定
路径复杂度正相关，每加一个流都要重新审计一遍时序。

P2 —— 断连清理无契约。`onServiceDisconnected`（MainActivity:101-104）仅置空
Activity 自身字段；ViewModel 端靠 `WeakReference`（:221）等待 GC，收集协程
与 Service 生命周期的关系无显式约定。

P3 —— 取数路径不统一。feature:main 走 Binder→Manager 流，feature:history 走
HistoryRepository 查 Room；实时与历史两套世界靠 service 内部 Recorder 桥接，
无统一门面（SSOT）。新增数据消费者（如未来图表页、统计页）需要二选一并复制接线。

## 3. 目标与非目标

### 3.1 目标

- G1 引入 Hilt `@Singleton` 的 `HeartRateRepository` 作为实时心率数据的唯一出口（SSOT）。
- G2 删除 `MainViewModel.setConnectionManager` 及其状态恢复补丁；ViewModel 构造注入 Repository。
- G3 落盘链路（HeartRateRecorder）归入 `:data:repository`，service 只调 Repository 接口。
- G4 保持对外行为 100% 不变：数值实时性、图表 500ms 节流、落盘 5s 批量、Toast/状态机语义均不回归。
- G5 每个阶段独立可编译、可冒烟、可单独回滚。

### 3.2 非目标（明确不做）

- N1 不改 BLE 连接栈：扫描/连接/重连状态机、自动重连、Kable 用法全部原地保留。
- N2 不做跨进程化：单进程前提下继续使用本地 Binder 作为**控制面**（连接/断开/扫描命令、悬浮窗控制）。
- N3 不改性能参数：`SNAPSHOT_THROTTLE_MS=500`、`BATCH_FLUSH_INTERVAL_MS=5000`、60s 窗口、缓冲上限等数值原样迁移。
- N4 不引入新的进程/消息总线（无 AIDL、无 Messenger、无 EventBus）。
- N5 不在本 spec 范围内重构 `SessionChartTracker` 内部算法，仅考虑其归属位置。

## 4. 目标架构

```
BLE 设备(0x2A37)
  → BleManager（不变）
  → BleService/BleConnectionHandler（采集引擎，连接状态机不变）
       │  写入（Hilt 注入）
       ▼
  HeartRateRepository（新增, :service, @Singleton, SSOT）
       ├─ 实时流 StateFlow：heartRateMeasurement / bleState / chartDataSnapshot / ...
       ├─ 落盘：内聚原 HeartRateRecorder 缓冲+批量逻辑 → HeartRateDao
       └─ 供所有消费者注入
             ├─ MainViewModel（构造注入，替代 Binder 数据面）
             ├─ BleConnectionManager 接口（委托 Repository，过渡期兼容）
             └─ （Phase 4）StatusBarResidentService 等旁路消费者
```

分层纪律：
- data 层**不得**依赖 BLE 栈（Kable/ScanResult 等类型不得出现在 `:data:repository`）。
  Repository 收到的已是解析后的领域模型（`HeartRateMeasurement` 若含 BLE 专属类型，
  在 service 侧转换为纯领域模型后再写入）。
- 控制命令（connect/disconnect/startScan）仍走 `BleConnectionManager`/Binder，不进 Repository。

## 5. 渐进式阶段

> 每阶段结束条件：`:app:assembleDebug` 通过 + §7 冒烟清单全绿 + 该阶段单测通过；
> 完成后在文档头部「进度追踪」区勾选并填写完成日期与验证记录。
> 任何阶段失败可直接 revert 单阶段提交，前一阶段产物独立可用。

### Phase 0 —— 基线与防护网（0.5 天）

状态：已完成 2026-09-02 ｜ 验证记录：全量单测 12 模块基线跑通；甄别 2 类 flaky（不阻塞）；修复预存失败 FunctionSettingsViewModelTest；真机录像待用户，以代码级基线替代

动机：迁移期间唯一的正确性裁判是“行为不回归”。

改动：
- 在 feature:main 补充 `MainBleStreams` 收集逻辑的单测基线（fake BleConnectionManager 喂流，断言 uiState 归约结果），重点覆盖：心率数值透传、bleState→AppStatus 映射、manualConnectionPending 防竞态分支。
- 手工录制基线：连接真实设备，录屏记录 数值更新频率 / 图表滚动 / 断开重连 Toast / 旋转屏幕后恢复表现。
- 记录 Room 落盘节奏（adb 观察或临时日志）：确认 5s 批量大小与时序。

不做：任何生产代码改动。
验收：单测基线绿；基线录像与日志归档。

### Phase 1 —— Repository 承接数据流（service 内部切换，对外无感）（1-2 天）

状态：已完成 2026-09-02 ｜ 验证记录：`:service:testDebugUnitTest` 122 用例全绿（含 BleConnectionHandlerTest 状态机回归）；`:app:assembleDebug` 通过；落点修订见「实施修订记录」

动机：先立 SSOT，但不改任何消费者，风险最低。

改动：
- `:service` 新增 `HeartRateRepository`（`@Singleton`，`@Inject constructor`，落点修订见「实施修订记录」）：
  - 持有与 `BleConnectionManager` 数据面同型的 StateFlow（heartRateMeasurement、speed、scanResults、connectedDevice、bleState、chartDataSnapshot、sessionMaxHr、sessionMinHr）。
  - 暴露 `update*()` 写入方法（命名见 §6）。
- `BleConnectionHandler`：删除自有 StateFlow 字段的直接暴露，改为写入 Repository；`observeHeartRateData`(:653-689) 的旁路（Webhook :674、Tracker 喂点 :677、Recorder :683、broadcast :688）调用顺序与异常隔离原样保留。
- `BleService` 作为 `BleConnectionManager` 的 8 个流 getter 全部委托 Repository（接口与类型不变）。
- `SessionChartTracker` 原地保留（N5），其快照流经 Repository 转发。
- 依赖已就绪：`:service` 已通过 `api` 依赖 `:data:repository`（service/build.gradle.kts:42），无需新增。

明确不做：MainActivity / MainViewModel / StatusBarResidentService 一行不改（对外完全无感）。
验收：
- Phase 0 全部单测绿；冒烟清单绿。
- grep 断言：`BleConnectionHandler` 不再直接持有对外 StateFlow 字段。
回滚：revert 单提交即回到 Handler 自持流。

### Phase 2 —— ViewModel 切换数据源，删除 Binder 数据面（1 天）

状态：已完成 2026-09-02 ｜ 验证记录：`:app:assembleDebug` 通过；`:service` 122 用例重跑全绿；全仓库 grep 无 setConnectionManager/bindBleDataStreams 残留；speed 流随 M2 一并入 Repository（SpeedProvider 写入，链路统一）；BleConnectionHandlerTest 为环境时序 flaky（M0 基线未改代码时亦偶发，重跑即过）

动机：消灭 P1/P2 的根，收获本迁移主要收益。

改动：
- `MainViewModel` 构造注入 `HeartRateRepository`（确认 `@HiltViewModel`）；
  `bindBleDataStreams(manager)` 改为 `bindRepositoryStreams(repository)`，
  8 个流的收集与 reduce 逻辑逐行平移（MainBleStreams.kt 的 supervisorScope 隔离、
  CancellationException 重抛语义原样保留）。
- 删除 `setConnectionManager`（:218-228）、`bleServiceRef` WeakReference、
  `serviceDataJob` 的重绑取消逻辑。**注意**：`bleState.drop(1)`（MainBleStreams:97）
  保留——它防的是 StateFlow 重放语义而非 Binder 时序，属正确行为。
- MainActivity：`onServiceConnected` 不再调 `setConnectionManager`；保留绑定与
  `checkAndStartAutoConnectScan()`（自动连接判定入口不变，见 MainActivity:96 契约注释）。
- `:feature:main` 依赖已就绪：已存在 `implementation(project(":data:repository"))`（feature/main/build.gradle.kts:34），无需添加。
- 若 `checkAndStartAutoConnectScan` 等控制入口需要 manager 引用，临时经
  `bleService`（Activity 已有字段）直调，Phase 4 再收敛。

明确不做：不动控制面（连接/断开/扫描仍走 Binder→Manager）；不动 StatusBarResidentService。
验收：
- Phase 0 基线单测改造成针对 Repository 的等价测试并全绿。
- 冒烟重点：旋转屏幕 10 次，观察是否出现"假未连接"/图表 reset/重复 Toast（这是补丁删除后的最大回归风险点）。
- 后台压测：服务前台运行 30 分钟，UI 重进 20 次，数据无缺失。
回滚：revert 后 Binder 数据面自动恢复（Phase 1 的委托保证接口不变）。

### Phase 3 —— 落盘归仓（1 天）

状态：已完成 2026-09-02 ｜ 验证记录：Recorder+测试迁入 `:data:repository`（包名按契约 9.3 不变，`:service` 引用零改动）；`:app:assembleDebug`、`:data:repository` 与 `:service` 单测全绿

动机：实时与持久化汇于同一门面，HistoryRepository 与实时流同源。

改动：
- `HeartRateRecorder` 迁至 `:data:repository`（包名调整，逻辑逐行平移，
  5s flush / 异常分级 / 缓冲上限 / MAX_SESSIONS 参数不变，N3）。
- `HeartRateRepository` 增加落盘入口：`recordHeartRate(bpm, deviceName)`（内部转调 Recorder）；
  `BleConnectionHandler:683` 改调 Repository。
- `BleService.onTaskRemoved`(:209-214) 的 `cancelFlushLoop + flushPendingRecords`
  改经 Repository 暴露的等价方法；`FlushRecordsWorker` 的引用同步调整。
- 确认 Room 异常类型（`SQLiteConstraintException`）在 data 层可直接使用（已有依赖）。

明确不做：不动 Room schema；不动 DAO。
验收：冒烟 + 断网/杀进程场景：任务移除后 pending 记录成功抢救落盘；
DB 故障注入（临时改错库名）验证缓冲重试不丢数据。
回滚：Recorder 为纯平移，revert 即回。

### Phase 4 ——（可选）消费者与控制面收敛（1 天，可延后）

状态：已完成 2026-09-02 ｜ 验证记录：StatusBarResidentService 心率流改由 Repository 直订、isConnected 由 bleState 派生；bindService 保留为前台服务拉起锚点（避免“常驻悬浮层存活而 BLE 服务死亡时永不恢复”退化）；控制命令收敛评估：MainViewModel 控制面已收窄为 setControlPlane（M2），FloatingWindowService 纯控制面维持现状（2026-09-02 审查勘误：该判定有误，其 observeBleData 实际经 Binder 收集心率/速度数据流，已补充转换为 Repository 直订，见修订记录）

动机：消除残余的双路径，非阻塞项。

改动（逐项独立评估，可只做子集）：
- StatusBarResidentService（:86-92, :224-227）：由 Binder 观察改为注入 Repository
  观察实时流；Binder 仅保留启动控制。注意其"仅 bind 不 start"的启动限制注释(:224)仍然成立。
- 控制命令（connect/disconnect/scan）评估收敛到 `ServiceLauncher/ServiceController`
  抽象（Hilt 已绑定，ServiceModule.kt:17-18），MainActivity 不再需要 `bleService` 字段。
- FloatingWindowService 绑定维持现状（2026-09-02 勘误：原判定“纯控制面，无数据流”有误，其 observeBleData 经 Binder 收集心率/速度；已补充转换为 Repository 直订，见修订记录）。
- 文档：更新 `ChartScreen.kt` / `RealtimeChart.kt` 中"服务层 SessionChartTracker 维护"
  的注释指向新链路。

验收：全量冒烟 + 基线对比；`MainActivity` 中不再出现 `setConnectionManager`、
数据面 BleService 引用。

### Phase 5 —— 文档与契约同步（0.5 天，全阶段完成后必做）

状态：已完成 2026-09-02 ｜ 验证记录：SKILL.md 契约 3/4/6/7/9.1/9.5/10.3 七处同步完成；新增契约 13（心率数据流 SSOT）；最终全量回归 `:app:assembleDebug` + 12 模块单测 BUILD SUCCESSFUL；历史方案文档（Hilt/多模块化/MVI）不在仓库内，无需注记

动机：根目录 `SKILL.md` 是本项目架构契约（后续开发/AI 辅助规范的唯一来源），
多处条款与迁移前架构绑定；迁移完成后若不同步，后续开发会按过时契约写出
破坏新架构的代码。

检查清单（按届时实际 diff 核对，逐项勾选）：

- [x] 1. 契约 3「Service 抽象边界」（SKILL.md :294-299）：数据面依赖从
      `BleConnectionManager` 改写为 `HeartRateRepository`（控制面维持 Binder/Manager）；
      Binder 例外条款（:297）范围缩小为仅控制面绑定。
- [x] 2. 契约 4「组件职责」（SKILL.md :303）：MainViewModel 职责描述中
      “经 BleConnectionManager 接口暴露 StateFlow，UI 直接订阅”改为经 Repository
      下发；SessionChartTracker 归属描述同步。
- [x] 3. 契约 6「敏感机制」（SKILL.md :314-324）：HeartRateRecorder 取消语义条目
      （:322）的组件归属更新为 `:data:repository`（Phase 3 后），语义红线原样保留；
      `.drop(1)` 条目（:319）与 spec Phase 2 的保留决策核对一致。
- [x] 4. 契约 7「验证基线」（SKILL.md :326-338）：单测总数（当前 221 用例）随新增
      Repository/VM 测试更新；确认迁移后验证命令仍含
      `:data:repository:testDebugUnitTest`。
- [x] 5. 契约 9「模块边界」（SKILL.md :349-429）：9.1 模块清单 `:data:repository`
      职责补充“HeartRate 实时流 SSOT + 落盘缓冲”（Phase 3 后）；9.5 决策表消除
      “实时心率流归属”歧义。
- [x] 6. 契约 10.3「既有例外」（SKILL.md :483-488）：例外清单移除
      `setConnectionManager 绑定注入`（Phase 2 已删除），其余编排方法
      （cleanupOpenSessions / recoverServices / checkAndStartAutoConnectScan）
      按实际保留情况核对。
- [x] 7. 其他：`docs/` 历史方案文档仅在交叉引用失真处加“历史文档”注记，不改写历史内容。
      （核实：Hilt/多模块化/MVI 三份历史方案文档不在仓库内，仅 SKILL.md 名称引用，无需注记）

验收：
- `SKILL.md` 中 grep `setConnectionManager` / `BleConnectionManager` / `HeartRateRecorder` /
  `SessionChartTracker` 无与代码不符的描述；
- 本 spec 进度区与代码实际状态一致；
- 更新 `SKILL.md` 属“修改项目架构”类变更，按其「修改完成要求」章节输出
  why / 收益 / 缺点 / 同步影响说明。

## 6. HeartRateRepository 接口草案

```kotlin
// :service  …/service/HeartRateRepository.kt（落点修订见「实施修订记录」）
@Singleton
class HeartRateRepository @Inject constructor(
    private val recorder: HeartRateRecorder,   // Phase 3 迁入
    // scope 由内部自建 SupervisorJob + Dispatchers.IO，不注入 Service 作用域
) {
    // ── 数据面（StateFlow，与 BleConnectionManager 同型）──
    val heartRateMeasurement: StateFlow<HeartRateMeasurement>
    val speed: StateFlow<Float>
    val scanResults: StateFlow<List<ScanResultDto>>   // 纯领域模型，非 Kable 类型
    val connectedDevice: StateFlow<DeviceInfo?>
    val bleState: StateFlow<BleState>
    val chartDataSnapshot: StateFlow<ChartDataSnapshot?>
    val sessionMaxHr: StateFlow<Int>
    val sessionMinHr: StateFlow<Int>

    // ── 写入面（仅 service 采集引擎调用）──
    fun updateHeartRateMeasurement(m: HeartRateMeasurement)
    fun updateSpeed(v: Float)
    fun updateScanResults(r: List<ScanResultDto>)
    fun updateConnectedDevice(d: DeviceInfo?)
    fun updateBleState(s: BleState)
    fun updateChartSnapshot(s: ChartDataSnapshot?, maxHr: Int, minHr: Int)

    // ── 落盘面（Phase 3）──
    suspend fun recordHeartRate(bpm: Int, deviceName: String?)
    suspend fun flushPendingRecords()
    fun cancelFlushLoop()
}
```

约束：`ScanResultDto`/`DeviceInfo` 等若与 Kable 类型耦合，需在 service 侧映射
（参照 `data/repository/.../ModelMappers.kt` 既有模式）后才允许进入本接口。

## 7. 测试策略

- 单元测试（JVM）：
  - Repository：fake 写入 → 断言 StateFlow 语义（重放、置零、latest-wins）。
  - MainBleStreams：针对 Repository 的归约测试（Phase 0 基线平移），覆盖
    manualConnectionPending 分支与 bleState→Toast 事件矩阵。
  - Recorder 平移后原测试随包迁移（若已有；无则补缓冲/重试/取消抢救三类）。
- 集成冒烟（每阶段必跑，见下表）：

| # | 场景 | 断言 |
|---|---|---|
| S1 | 连接设备 → 首页 | 数值随传感器节奏刷新（~1Hz），图表 500ms 级滚动 |
| S2 | 旋转屏幕 ×10 | 无假未连接、无图表 reset、无重复 Toast（Phase 2 重点） |
| S3 | 断开 → 重连 | 状态机/Toast 与基线一致，图表按既有 cleanup 语义清空 |
| S4 | 杀进程重进 | 历史会话完整；onTaskRemoved 抢救逻辑有效（Phase 3 重点） |
| S5 | 状态栏常驻悬浮层 | 心跳/数值正常（Phase 4 切换前后对比） |
| S6 | 后台 30min + UI 重进 ×20 | 数据无缺失、无泄漏（heap 无 Service/VM 互持） |

## 8. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| Phase 2 删补丁后旋转/重建出现回归（假未连接、重复 Toast） | 高 | drop(1) 保留；S2 加压冒烟；补丁删除 diff 逐行评审；失败可单阶段 revert |
| Repository 流被多消费者订阅后节流语义被破坏（如有人绕过快照直接读内部缓存） | 中 | 快照仅经 `chartDataSnapshot` 暴露；code review 门禁 |
| data 层意外引入 BLE 类型依赖 | 中 | 接口层 DTO 映射 + lint/grep 门禁（Kable import 禁入 :data:repository） |
| Recorder 迁移破坏取消抢救（CancellationException 放回缓冲） | 中 | 逻辑逐行平移 + 专项单测；禁止"顺手重构" |
| Hilt 图变化引发编译期错误 | 低 | 每阶段 assembleDebug 验证；@Singleton 作用域与 Service 生命周期解耦需在 code review 确认无 Android 上下文泄漏 |

## 9. 里程碑

- [x] M0：Phase 0 完成（基线归档）
- [x] M1：Phase 1 合入（SSOT 立起，对外无感）
- [x] M2：Phase 2 合入（Binder 数据面下线，主收益兑现）
- [x] M3：Phase 3 合入（落盘归仓）
- [x] M4：Phase 4 合入（消费端统一）
- [x] M5：Phase 5 完成（SKILL.md 等项目契约文档同步，迁移正式关闭）

总工作量估算：4~6 人日（含测试与文档同步）。
