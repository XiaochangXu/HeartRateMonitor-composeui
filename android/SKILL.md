# 项目规则

- 禁止未经用户允许就将代码提交到远程仓库
- 禁止排除android目录下的.key文件夹，用户就是要把密钥提交到仓库

## 通用原则

始终优先考虑：

1. 官方文档
2. 官方最佳实践
3. 稳定版本发布文档
4. 可维护性优先于开发速度
5. 简洁性优先于不必要的复杂设计


如果这些规则与最新官方稳定指导冲突，请遵循官方指导。


禁止编造：

- API
- 文档内容
- 版本信息
- 性能数据
- 项目事实


如果不确定 API、版本或推荐实践：

- 首先查阅官方文档
- 如果仍然无法确认，明确说明无法确认该信息



---

# 架构规范

- 保持架构模块化
- 每个模块应具有单一职责
- 保持低耦合、高内聚
- 避免循环依赖
- 集中管理共享功能
- 不要重复已有实现
- 除非明确要求，否则不要改变项目架构



---

# 分层职责

保持清晰的职责分离。


## UI 层

负责：

- UI 渲染
- 用户交互


## 业务层

负责：

- 业务逻辑
- 状态管理
- 工作流程协调


## 数据层

负责：

- Repository
- 网络请求
- 数据库
- 缓存


禁止绕过分层结构。



---

# 代码风格

优先：

- 清晰的命名
- 小型函数
- 小型类
- 不可变数据
- 可读代码
- 单一职责原则


避免：

- 超长方法
- 巨大的类
- 重复代码
- 过深的嵌套
- 魔法数字
- 不必要的全局状态


新增代码必须保持与现有项目风格一致。



---

# 状态管理

- 使用单向数据流
- 保持状态集中管理
- UI 必须由状态驱动
- 避免维护重复状态



---

# 数据访问

- 使用统一的数据访问层
- 业务逻辑不得直接访问具体数据源
- 使用统一的错误处理方式
- 明确处理异常
- 禁止静默忽略错误



---

# 并发处理

- 遵循项目现有异步编程方式
- 永远不要阻塞主线程
- 避免共享可变状态
- 确保线程安全



---

# 配置管理

禁止硬编码：

- URL
- 版本号
- 配置参数


在适用情况下支持多环境配置。



---

# 注释规范

只允许添加解释以下内容的注释：

- 为什么存在该设计
- 业务规则
- 兼容性处理
- 性能考虑
- 安全考虑


不要添加仅仅描述代码表面行为的注释。



---

# 代码修改规范

默认采用：

> 最小化修改原则


禁止：

- 修改无关文件
- 修改无关代码
- 进行不必要的重构
- 未经请求改变架构


只有以下情况才建议重构：

- 存在 Bug
- 可维护性较差
- 性能受到明显影响
- 用户明确要求重构



---

# 技术决策

- 优先使用项目已有技术
- 没有充分理由不要引入新框架


当存在多个方案时，需要说明：

- 优点
- 缺点
- 对当前项目的影响


不要将某一个方案描述为唯一正确方案。



---

# 代码质量

新增代码应该：

- 易读
- 易维护
- 遵循现有架构
- 在适用情况下保持向后兼容
- 尽可能具备可测试性


优先：

> 渐进式改进，而不是大规模重写。



---

# 修改完成要求

当修改以下内容时：

- 公共 API
- 核心业务逻辑
- 项目架构


必须说明：

- 为什么进行该修改
- 修改带来的收益
- 可能的缺点
- 是否需要同步修改相关组件



---

# 本项目架构契约（禁止破坏）

以下约束来自已完成的渐进式重构，修改代码时必须遵守，违反即视为破坏架构。

## 1. Room Entity 不得泄漏到 UI/ViewModel 层

- `data/db/` 下的 Entity（HeartRateSession / HeartRateRecord / FavoriteDeviceEntity 等）只允许出现在 `data/db`、`data/repository`、`service` 落盘组件与 DAO 测试中。
- UI/ViewModel 一律使用 `data/model/` 的 Domain Model（HeartRateSessionInfo / HeartRateRecordInfo / FavoriteDeviceInfo / SessionStatsInfo），映射函数 `toInfo()` / `toEntity()` 在 Repository 层完成。
- 新增表/字段时：先加 Entity，再在 `data/model/` 补对应 Info 类与映射，Repository 对外只返回 Info 类型。

## 2. 配置读写统一走 SettingsRepository（DataStore 存储层）

- 设置持久化层为 Preferences DataStore（`data/settings/SettingsDataStore.kt` 顶层委托全进程单例，含 SharedPreferencesMigration 老数据无损迁移）。任何组件禁止直接调用 `context.getSharedPreferences()`、注册 `OnSharedPreferenceChangeListener` 或自行构造 DataStore 实例。
- 仅有的两个直连 `settingsDataStore` 的例外（不得新增）：`KillStateSaver.save`（进程死亡路径，runBlocking 同步落盘，不可改回即发即忘）、`ServiceBootInitializer`（ContentProvider 早于 Application.onCreate，Hilt 组件尚未初始化，永远不能走注入）。
- 读：`settingsRepository.get(key)`（读预热内存快照，同步零 IO）；监听：`settingsRepository.observe(key)` 返回的 StateFlow（配合 `.drop(1)` 可保持"仅响应变化"语义）；写：`settingsRepository.set(key, value)`（异步落盘 + 乐观同步更新缓存，写后立读）；可空字符串用 `getNullable` / `observeNullable`；多键批量同步读用 `settingsRepository.settings`（`StateFlow<AppSettings>` 类型化全量快照）。
- Service 通过 Hilt 注入的 `SettingsRepository` 实例获取（`@Inject lateinit var`，见第 8/9 条）。
- 键一律使用 `data/settings/SettingsKeys.kt` 的类型化 `Preferences.Key`，禁止字符串字面量与运行时拼键；默认值唯一来源是 `AppSettings.DEFAULTS`，禁止在调用点重复声明默认值（仅历史分歧点保留显式默认值重载）；新增键必须同时在 `SettingsKeys` 与 `AppSettings`（字段 + DEFAULTS + from()）登记，键名字符串禁止改动（老数据迁移依赖）。
- 类型化键从结构上杜绝了同名异型写入：异型键编译期即不可用；测试也不得对生产 key 写异型值。

## 3. Service 抽象边界

- ViewModel/UI 依赖 `BleConnectionManager`（BLE 扫描/连接/状态流）与 `ServiceLauncher`（服务启停）接口，禁止依赖具体 `BleService` / `ServiceController` 类。
- 例外：Activity/Service 通过 Binder 绑定具体 Service 属绑定机制，允许保留具体类型。
- `BleService` 仅承担生命周期编排，连接状态机逻辑归 `BleConnectionHandler`，前台通知归 `BleNotificationManager`；新增同类逻辑应放入对应组件而非 BleService。
- 服务启停统一经注入的 `ServiceLauncher`（Hilt 绑定 ServiceController），禁止在 UI 层直接 `startService(Intent(...))`。

## 4. 组件职责与体量上限

- `MainViewModel`：仅 BLE 状态订阅 + 组件编排（含自 MainActivity 迁入的启动编排：自动连接判定/服务恢复/悬浮窗切换/会话清理/BLE Toast 联动）+ 对外 StateFlow；图表数据管道（RR→Point→Snapshot→窗口）归 `ChartDataManager`；"删除收藏并恢复最近"逻辑归 `FavoriteDeviceRepository.deleteAndRestoreLatest()`。
- 单个 Composable 文件建议 ≤ 350 行，单个子组件建议 ≤ 150 行；超限时按职责拆为同包新文件（`internal` 可见性），状态通过参数/回调提升传递。
- 页面结构模式：Screen 主文件只做状态收集与编排，子区域提取为独立 Composable 文件（参考 ui/alarm、ui/settings 现有拆分）。

## 5. 公共工具复用（禁止重复造轮子）

- ModalBottomSheet 弹出：一律用 `ui/util/SheetUtils.kt` 的 `rememberExpandedSheetState()`，禁止手写 `rememberBottomSheetState(Hidden) + LaunchedEffect(expand())`。
- 后台页面暂停收集 Flow：用 `ui/util/FlowUtils.kt` 的 `collectWhenActive()`。
- 设置项容器：用 `ui/settings/SettingsComponents.kt`（SettingsGroupCard / SettingsItem / SettingsSwitch / SettingsLink / DragSlider）。
- Webhook 触发必须经 `WebhookRepository.triggerWebhooks()`（内置 5s 节流），禁止绕过自建 HTTP 请求。

## 6. 敏感机制（修改前必须理解，不得"顺手简化"）

- BLE 纪元机制（connectEpoch）：防止旧自动重连误取消新连接，跨组件传递时不得省略纪元校验。
- 心率新鲜度自适应超时（HeartRateFreshnessTracker）：SUSPECT 暂停预警、STALE 全链路清零，降级链路依赖 rate <= 0 约定。
- SettingsRepository 的写后立读依赖「构造期 runBlocking 预热内存快照 + setter 乐观 CAS 更新（`prefsState.update`，不得改回非原子读改写）」，主题冷启动等首帧前同步读依赖此预热，不得改为异步加载；`AppSettings` 快照（`settings` StateFlow）与 `prefsState` 同源同步更新，不得单独异步构建；DataStore edit 串行化导致旧快照发射瞬态回退乐观值的限制已在类注释声明，属预期行为。
- StateFlow 收集模拟原 listener 语义时先 `.drop(1)` 跳过初始发射。
- KillStateSaver.save 的 runBlocking 同步落盘是 KILL 场景的硬约束（进程随时被杀，异步 launch 会丢数据），不得改为 `set()` 即发即忘。
- FairMemoryReceiver TRIM/KILL 回调的落盘与缓存释放顺序不得调整。
- HeartRateRecorder.flushPendingRecords 的取消语义：`CancellationException` 必须「记录回放缓冲区 + 重抛」，禁止并入普通 `Exception` 分支吞掉——否则与 onDestroy「先 drainPendingRecords 入队 Worker、后 serviceScope.cancel()」的顺序叠加，落盘中的批次会被静默丢弃；flush 循环同样不得吞取消。
- BleBroadcastManager 的 200ms 节流只允许作用于高频心率包：连接/断开迁移、状态文案变化、心率清零等终态事件必须直发（否则断开广播落在节流窗口内被丢弃后，WS 客户端永久停留在 connected=true 的陈旧状态）。
- SettingsRepository.set/remove 落盘协程的异常防护（IO 失败记录日志、不炸进程）不得移除；内存乐观快照与磁盘值的瞬态分叉属已声明限制，磁盘写失败不得让进程崩溃。

## 7. 验证基线

- 改动后必须执行：

  ```
  gradlew :app:assembleDebug
  gradlew :app:testDebugUnitTest :service:testDebugUnitTest :data:database:testDebugUnitTest :data:settings:testDebugUnitTest :data:repository:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:ui:testDebugUnitTest :feature:alarm:testDebugUnitTest :feature:server:testDebugUnitTest :feature:settings:testDebugUnitTest
  ```

  （新增模块或新增含单测的模块后，验证命令须含各模块自身的 `:X:testDebugUnitTest`。）
- 当前单测基线：**全部通过（213 用例，0 失败）**；其中 29 个为纯 UDF 迁移（2026-08）新增的设置页 ViewModel 往返一致性/配对状态机用例；MVI 迁移（2026-08）完成后新增 MviViewModel 基类测试 ×2 与阈值 clamp 纯归约测试 ×2，验收存档见 `baseline/mvi-baseline.md`。历史基线问题（AppDatabaseTest / HeartRateRecorderTest 的 Room 3→4 迁移缺失导致的 28 个预存失败）已于 2026-08 修复——测试改用 Room 默认驱动（RoomOpenHelper 自行建表与版本管理），不再手工提供 SupportSQLiteOpenHelper + 空 onCreate Callback；出现新失败必须修复。
- 涉及 BLE 连接/断开/重连、设置热更新的改动需提示用户真机回归。

## 8. 全局单例容器化（禁止新增全局可变单例）

- 进程级共享组件一律由 **Hilt 单例组件（SingletonComponent）** 管理：可构造注入的类加 `@Inject constructor`，需要装配的用模块内 `@Module @InstallIn(SingletonComponent::class)` `@Provides`；进程级单例语义由 `@Singleton` 保证（迁移依据《Hilt渐进式迁移方案.md》Phase 1~7 已完成，`AppContainer`/`AppContainerExt`/`XxxDependencies` cast 链已删除）。
- **`@Inject constructor` 的进程级共享状态类必须标注 `@Singleton`**：Hilt 对无作用域绑定会在每个注入点各建一个新实例。2026-08 曾系统性漏注 18 个类（ThemeState / LiquidGlassState / KillStateSaver / LanTransferSharedState / FairMemoryReceiver / CustomSchemeCache 等），症状是「设置页修改不生效、开关无响应」（写的是设置页自己那份实例的 StateFlow，UI 收集的是另一份）。凡 KDoc 声称「Hilt 单例」的类，注解必须真实存在；`@Binds` 抽象方法的作用域写在实现类上。
- 禁止新增持有可变状态的顶层 `object`；禁止新增 `getInstance()` / DCL / `INSTANCE` 手写单例（存量 `AppDatabase.getDatabase()` DCL 作为 DatabaseModule 的构建函数保留，运行时唯一实例由 Hilt 管理）。
- 仅允许无状态常量对象：`SettingsKeys`、`BleConstants`、`LanTransferProtocol`、`ThemeSource`、`ThemeMode`。
- 对外可变状态一律 `MutableStateFlow` 私有 + `asStateFlow()` 暴露；多线程写的 setter 保持原子更新（CAS）语义。
- 需早初始化的组件（FairMemory 链、主题/液态玻璃配置）由 `HeartRateApp.onCreate` **显式触发**注入字段（顺序：themeState → liquidGlassState → fairMemoryReceiver.initialize → fairMemoryNotifier.initialize → memoryDiagnostics.initialize → themePreviewCache.preload(appScope)），保证 Composable 读取前就绪，**初始化顺序不得随意调整**（契约 6 红线）。

## 9. 模块边界契约（多模块化，禁止破坏）

模块化重构依据《Android多模块化渐进式重构方案.md》（Phase 1~8 已完成）。

### 9.1 模块清单与职责

```
:app               应用壳：HeartRateApp（@HiltAndroidApp + Configuration.Provider）/
                   MainActivity / AppRoot / AppNavHost / AppTabPager /
                   AppBottomNavBar / AppLifecycleEffects / AppChangelogState / NavGuard /
                   AppTheme / AppModule（@AppScope 作用域、() -> Intent 等 :app 专属绑定）/ Manifest / 签名 / 混淆
:core:model        领域模型（data.model、data.Webhook/WebhookTrigger），零依赖
:core:designsystem 主题视觉（Color/Type/ThemeConfig/ThemeState/LiquidGlassState/CustomSchemeCache/
                   无状态主题函数/ThemePresetSeeds 之外），唯一依赖 :data:settings（3.3.3 特例）
:core:ui           通用 UI（widgets/animation/util/Screen 路由/SettingsComponents/SoundManager/
                   通用字符串与图标）
:data:settings     DataStore 存储 + SettingsRepository + settingsDataStore 单例（契约 2 例外）
:data:database     Room（Entity/DAO/AppDatabase/schemas），KSP 在此模块
:data:repository   仓储层 + webhook/network/sensor/system 封装 + ModelMappers（契约 1 映射归口）
:service           BLE/常驻/悬浮窗/预警/局域网服务 + ble + init + LanTransferSharedState +
                   ServiceModule（ServiceLauncher @Binds）
:feature:*         main/settings/history/alarm/server/webhook/favorite 页面
:baselineprofile   现有模块，不动
```

### 9.2 依赖方向（编译期强制）

- 自上而下：`app → feature → (core | data | service)`；`service → (core | data)`；`data → core`。
- `:core:designsystem → :data:settings` 是唯一 core→data 特例（3.3.3）。
- **禁止**：任何模块依赖 `:app`；feature 之间互依；data 依赖 UI/service；core 依赖 feature/service。
- 新增依赖前先核对目标模块是否在允许方向内；跨 feature 共享代码一律下沉 `:core:ui`/`:core:designsystem`。

### 9.3 namespace 与包名

- 模块 namespace：`:core:model`→`...data.model`、`:core:designsystem`→`...ui.theme`、`:core:ui`→`...ui.widgets`、`:data:settings`→`...data.settings`、`:data:database`→`...data.db`、`:data:repository`→`...data.repository`、`:service`→`...service`、`:feature:*`→`...feature.<name>`（唯一，AGP 要求）。
- **Kotlin 包名保持迁移前路径不变**（`com.github.heartratemonitor_compose.*`），跨模块同包类型无需 import；namespace 只决定各模块 R 类。
- 跨模块引用资源：`import <目标模块 namespace>.R` 或全限定 `xxx.R.string.yyy`；**禁止引用 `:app` 的 R**（非传递 RClass 已开启）。

### 9.4 依赖注入模式（Hilt，禁止手动 DI cast / Koin）

- 组合根：`:app` 的 `@HiltAndroidApp HeartRateApp`（`SingletonComponent` 宿主）+ 各模块 `@Module @InstallIn(SingletonComponent::class)` 装配类（`:data:database` DatabaseModule、`:data:settings` SettingsModule + `@AppScope` Qualifier、`:service` ServiceModule、`:app` AppModule）。
- 使用点取依赖（三选一，按优先级）：
  1. 构造注入：`@Inject constructor`（Repository/Provider/单例服务类），`Context` 参数标 `@ApplicationContext`；
  2. ViewModel：`@HiltViewModel` + `@Inject constructor`，Composable 用 `hiltViewModel()`（import `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`）；
  3. Android 组件：`@AndroidEntryPoint` + `@Inject lateinit var` 字段注入（Activity / Service / Application）。
- Composable 所需依赖一律经 hiltViewModel() 的 ViewModel 构造注入后下发（含一次性展示依赖，
  如 `ThemeSettingsViewModel.themePreviewCache`）；`@EntryPoint` 窄接口 +
  `EntryPointAccessors.fromApplication` 模式仅作兜底手段保留（既有 MainDependencies /
  ServerDependencies / SettingsDependencies 已全部移除，当前无使用方），
  新增 Composable 依赖不得再走 EntryPoint；接口方法必须是无参抽象属性。
- Worker：`@HiltWorker` + `@AssistedInject`（`HeartRateApp` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`）。
- 新模块接入流程：加 `hilt-android` + `ksp(hilt-compiler)`（含 `@AndroidEntryPoint` 的模块另加 hilt Gradle 插件；含 `@HiltWorker` 的另加 hilt-work + `ksp(hilt-androidx-compiler)`；ViewModel 模块另加 hilt-navigation-compose）→ 类加 `@Inject`/`@Provides`/`@EntryPoint` → 使用点注入。
- **禁止**：`application as XxxDependencies` 之类运行时 cast 取依赖；手写 ViewModel Factory / 手写单例容器；模块内 import `:app` 的 `HeartRateApp` / `data.di.*`（Hilt 绑定一律在模块自身或 `:app` AppModule 提供）。

### 9.5 新代码放哪个模块（决策表）

| 代码类型 | 归属 |
|---|---|
| 领域模型/纯数据类 | `:core:model` |
| 颜色/字体/主题函数/主题状态 | `:core:designsystem` |
| 通用 Composable/动画/UI 工具/路由常量/通用字符串图标 | `:core:ui` |
| 设置读写/DataStore | `:data:settings` |
| Entity/DAO/数据库 | `:data:database` |
| Repository/网络/传感器/系统封装 | `:data:repository` |
| BLE/服务/通知/局域网服务 | `:service` |
| 某功能页面及其 ViewModel | 对应 `:feature:*` |
| 跨 feature 共享的页面组件 | 下沉 `:core:ui`（禁止 feature 互依） |
| 组合根装配/Activity/Manifest/签名/混淆 | `:app` |

### 9.6 资源与依赖声明

- 资源随代码走：feature/模块独有资源随迁，跨模块共享资源下沉 `:core:ui`；`values`/`values-zh` 成对迁移；字符串 key 全仓库唯一（禁止两处定义）。
- 模块间依赖默认 `implementation`；类型出现在模块公开 API（方法返回/构造参数）时用 `api`（如 `:data:database` 的 DAO/Entity、`:data:repository` 对三个数据模块）。
- 版本一律走 `gradle/libs.versions.toml`，禁止模块内硬编码版本。
- `:app` 的 `proguard-rules.pro` 保留全部 keep 规则（R8 最终打包生效）。
- 敏感机制（契约 6）与映射归口（契约 1）在模块移动后保持原样，禁止借模块化重构业务逻辑。

## 10. 纯 UDF / MVI 状态（禁止回退）

以下约束固化纯 UDF 迁移与 MVI 迁移（依据《Android MVI 渐进式迁移方案.md》，2026-08 完成）的成果，违反即视为破坏架构：

### 10.0 MVI 三元组（feature 页面 ViewModel 强制）

1. **单一 UiState**：所有 feature ViewModel 继承 `:core:ui` 的 `MviViewModel<S, I>`，
   状态经唯一 `uiState: StateFlow<XxxUiState>` 下行；UI 收集只允许一行
   `collectAsStateWithLifecycle()`（后台页配 `collectWhenActive()`）。
2. **sealed Intent**：用户意图封装为 `sealed interface XxxIntent`，经唯一
   `dispatch(intent)` 上行；禁止 VM 再新增公开业务 setter 方法，禁止万能
   Intent（`Update(field, value)`）绕过类型系统。
3. **单一归约点**：状态变化汇入 `setState`（CAS 更新，联动字段一次 copy）；
   持久化设置只写 `SettingsRepository`，UiState 为真源派生投影（禁双写）；
   纯变换提取为可独立单测的函数；副作用在 handleIntent 中执行，不得绕过归约点直改状态。
4. **一次性事件**：Toast/导航/一次性弹窗不进 UiState，按迁移方案 §3.4 三选一
   （默认 VM 回调/返回值；状态内可空字段 + Consume Intent；Channel 限多事件源页且须注释理由），
   禁止 SharedFlow 事件总线。
5. Composable 对 VM 的调用仅剩 `uiState` 收集与 `dispatch`；豁免见 10.3。
   新增页面一律按 MVI 形态实现。

### 10.1 判定标准（四条全部满足）

1. **单一事实来源**：业务状态（持久化、跨页面共享、从 Repository 派生）有且仅有一份，
   存在于 ViewModel（`@HiltViewModel`）或 Hilt 单例中；UI 层禁止持有镜像副本。
2. **状态下行**：UI 一律通过 `collectAsStateWithLifecycle()` 收集只读 `StateFlow`。
3. **事件上行**：用户操作只调用 ViewModel 方法；写入由 ViewModel 完成。
4. **瞬时状态边界**：仅"未持久化、不跨页面、非 Repository 派生"的纯交互状态
   （对话框显隐、输入框草稿、选色目标键）允许留在 Composable 内，其余一律上提。

### 10.2 禁止模式（feature/** 与 :app 的 Composable/Activity）

- ❌ Composable / Activity 直接调用 `settings.set / get / remove / getNullable`（读写归 ViewModel；
  进程死亡与 ContentProvider 两处契约 2 既有例外除外）。
- ❌ Composable 调用 ViewModel 的公开业务方法替代 dispatch（豁免见 10.3）。
- ❌ `LaunchedEffect(本地状态) { settings.set(...) }` 状态驱动副作用式回写（双向同步语义）。
- ❌ Composable 内 `remember { mutableStateOf(settings.get(...)) }` 镜像持久化值。
- ❌ UI 层内联业务流程（扫描、配对、重试、服务恢复判定等归 ViewModel 或其注入组件）。
- ❌ 一次性事件进 StateFlow 产生重放；需要 Activity 上下文的一次性行为用 VM 方法返回值/回调
  （参考 `MainViewModel.toggleFloatingWindow(): Boolean` 与 `bleToastListener`），禁止引入 SharedFlow 事件总线。

### 10.3 既有例外（不得扩大）

- `MainViewModel` 的 Activity 生命周期编排方法（cleanupOpenSessions / recoverServices /
  checkAndStartAutoConnectScan / setConnectionManager 绑定注入）保持公开方法形态（非 UI 用户意图）；
  `toggleFloatingWindow(): Boolean` 返回值与 `bleToastListener` 回调属 §3.4 方案 1 豁免；
  `ThemeSettingsViewModel.themePreviewCache` 为色卡预览一次性展示依赖（只读缓存，非业务状态）。
- 更新日志检测归 `ChangelogNotifier` Hilt 单例（`:app`，替代旧 rememberChangelogState）；
  `MainActivity` / `AppRoot` 不持有 `SettingsRepository`，不得再向 UI 层透传。
- 设置页 VM 的 `stateIn` 初值一律引用 `AppSettings.DEFAULTS`（默认值唯一来源），不得硬编码。
- 滑块 `onValueChange` 每拍经 VM 调 `settings.set`，与直写同路径，不得额外加节流（契约 6 写后立读）。
- `FullScreenHeartRate` 的 `hrState`/`beepPaused` 经定性属瞬时态（与组合作用域内
  soundManager 生命周期绑定，每次进入重置），保留 UI 层，理由已存档于代码注释；
  纯展示页与主题页（ThemeState 等 Hilt 单例为单一事实来源）不要求 ViewModel。



---

# 11. Jetpack Compose WindowInsets 避坑（禁止违反）

## 问题：LazyColumn 上使用 windowInsetsPadding 导致系统手势条区域出现色块

### 症状

Tab 页面底部导航栏下方的系统手势条区域出现一块与页面背景色一致的长方形色块，
而二级页面（全屏沉浸）无此问题。

### 根因

在 `LazyColumn` 的 `modifier` 上使用 `windowInsetsPadding(WindowInsets.navigationBars)`
会**缩小 LazyColumn 的布局区域**——LazyColumn 本体不再延伸到屏幕底部，底部留出的
空白 padding 区域被 Scaffold 的 `containerColor` 填充，形成与页面背景色一致的色块。
叠加 `AppRoot` 底部渐变层后，该色块在 Tab 页面可见。

而二级页面（非 Tab）没有 `AppRoot` 底部渐变层覆盖，且自身 `LazyColumn` 的
`windowInsetsPadding` 留白与系统手势条沉浸区域视觉一致，所以"看起来没问题"。

### 正确做法：contentPadding 方式（本项目强制）

`LazyColumn` / `Column` 等可滚动容器避让系统手势条时，**必须用 `contentPadding`
的 `bottom` 消化 inset，不得在 `modifier` 上加 `windowInsetsPadding`**：

```kotlin
// ✅ 正确：LazyColumn 延伸到屏幕底部，内容通过 contentPadding 避让
val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
LazyColumn(
    modifier = Modifier.fillMaxSize(),           // ← 延伸到底部，无空白区域
    contentPadding = PaddingValues(
        top = padding.calculateTopPadding() + 16.dp,
        bottom = 8.dp + navBarInset               // ← 内容不滚入手势条区域
    )
)

// ❌ 错误：windowInsetsPadding 缩小布局区域，底部留出色块
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.navigationBars),  // ← 禁止！
    contentPadding = PaddingValues(bottom = 8.dp)
)
```

### 适用范围

本规则适用于所有 Tab 页面（`HomeScreen`、`HistoryScreen`、`FavoriteDevicesScreen`、
`SettingsScreen`）及未来新增的 Tab 页面。二级页面（非 Tab）可按需选择方式，
但为保持一致性建议同样遵循 `contentPadding` 方式。

### 涉及的组件联动

- `AppRoot` 底部渐变层（`bottomGradientHeightDp`）的高度计算已包含
  `navBarInsetPx + FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_MARGIN`，
  覆盖系统导航条 inset + 悬浮应用导航条区域。Tab 页面 `LazyColumn` 必须用
  `contentPadding` 方式，使渐变层正确覆盖到底部而不出现色块。
- `HomeSpeedDialFab` 的 FAB 悬浮位置通过 `navBarInset: Dp` 参数计算
  `Modifier.padding(bottom = navBarInset + ...)`，不得改用 `windowInsetsPadding`。
