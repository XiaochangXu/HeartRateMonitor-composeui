# 契约 10：纯 UDF / MVI 状态（禁止回退）

以下约束固化纯 UDF 迁移与 MVI 迁移（依据《Android MVI 渐进式迁移方案.md》，2026-08 完成）的成果，违反即视为破坏架构。

## 10.0 MVI 三元组（feature 页面 ViewModel 强制）

### 1. 单一 UiState

所有 feature ViewModel 继承 `:core:ui` 的 `MviViewModel<S, I>`，状态经唯一 `uiState: StateFlow<XxxUiState>` 下行。

UI 对每个状态只允许一行 `collectAsStateWithLifecycle()`（后台页配 `collectWhenActive()`）。

**豁免**：UI 直订 Hilt 单例 Repository 暴露的只读流（契约 4/13 的 `HeartRateRepository` 实时心率与图表快照）不受"单一收集源"限制，但仍禁止在 UI 层持有其镜像副本。

### 2. sealed Intent

用户意图封装为 `sealed interface XxxIntent`，经唯一 `dispatch(intent)` 上行。

**禁止**：
- VM 再新增公开业务 setter 方法
- 万能 Intent（`Update(field, value)`）绕过类型系统

### 3. 单一归约点

状态变化汇入 `setState`（CAS 更新，联动字段一次 copy）。

- 持久化设置只写 `SettingsRepository`，UiState 为真源派生投影（禁双写）
- 纯变换提取为可独立单测的函数
- 副作用在 handleIntent 中执行，不得绕过归约点直改状态

### 4. 一次性事件

Toast/导航/一次性弹窗不进 UiState，按迁移方案 §3.4 三选一：
1. 默认 VM 回调/返回值
2. 状态内可空字段 + Consume Intent
3. Channel 限多事件源页且须注释理由

**禁止 SharedFlow 事件总线。**

### 5. Composable 调用约束

Composable 对 VM 的调用仅剩 `uiState` 收集与 `dispatch`（豁免见 10.3）。

新增页面一律按 MVI 形态实现。

### 6. UiState 集合字段必须使用不可变集合

使用 `kotlinx.collections.immutable` 的不可变集合类型（`ImmutableList`/`ImmutableMap`/`ImmutableSet`）。

**禁止**在 UiState 中使用 `List`/`Map`/`Set`。

**原因**：Compose 编译器将标准库集合接口推断为不稳定类型，导致接收这些参数的 Composable 永远不可跳过重组。

- ViewModel `setState` 时使用 `.toImmutableList()` / `.toImmutableMap()` / `.toImmutableSet()` 转换
- 空集合默认值使用 `persistentListOf()` / `persistentMapOf()` / `persistentSetOf()`
- Composable 的集合参数同样应使用 Immutable 类型（如 `MiniChart(samples: ImmutableList<Int>)`）
- Domain Model 中含 `List` 字段的类（如 `Webhook.triggers`）也必须稳定化——在 `:core:model` 添加 immutable 依赖后将字段替换为 `ImmutableList`
- `Webhook.triggers` 与 `PostureCalibration.sittingSamples/standingSamples` 已于 2026-08 完成替换

## 10.1 判定标准（四条全部满足）

1. **单一事实来源**：业务状态（持久化、跨页面共享、从 Repository 派生）有且仅有一份，存在于 ViewModel（`@HiltViewModel`）或 Hilt 单例中；UI 层禁止持有镜像副本
2. **状态下行**：UI 一律通过 `collectAsStateWithLifecycle()` 收集只读 `StateFlow`
3. **事件上行**：用户操作只调用 ViewModel 方法；写入由 ViewModel 完成
4. **瞬时状态边界**：仅"未持久化、不跨页面、非 Repository 派生"的纯交互状态（对话框显隐、输入框草稿、选色目标键）允许留在 Composable 内，其余一律上提

## 10.2 禁止模式（feature/** 与 :app 的 Composable/Activity）

- ❌ Composable / Activity 直接调用 `settings.set / get / remove / getNullable`（读写归 ViewModel；进程死亡与 ContentProvider 两处契约 2 既有例外除外）
- ❌ Composable 调用 ViewModel 的公开业务方法替代 dispatch（豁免见 10.3）
- ❌ `LaunchedEffect(本地状态) { settings.set(...) }` 状态驱动副作用式回写（双向同步语义）
- ❌ Composable 内 `remember { mutableStateOf(settings.get(...)) }` 镜像持久化值
- ❌ UI 层内联业务流程（扫描、配对、重试、服务恢复判定等归 ViewModel 或其注入组件）
- ❌ 一次性事件进 StateFlow 产生重放；需要 Activity 上下文的一次性行为用 VM 方法返回值/回调（参考 `MainViewModel.toggleFloatingWindow(): Boolean` 与 `bleToastListener`），禁止引入 SharedFlow 事件总线

## 10.3 既有例外（不得扩大）

- `MainViewModel` 的 Activity 生命周期编排方法（cleanupOpenSessions / recoverServices / checkAndStartAutoConnectScan / setControlPlane 控制面注入）保持公开方法形态（非 UI 用户意图）；数据面订阅已改在构造期从 `HeartRateRepository` 直出（2026-09 迁移），不再经 Binder 注入；`toggleFloatingWindow(): Boolean` 返回值与 `bleToastListener` 回调属 §3.4 方案 1 豁免；`ThemeSettingsViewModel.themePreviewCache` 为色卡预览一次性展示依赖（只读缓存，非业务状态）
- 更新日志检测归 `ChangelogNotifier` Hilt 单例（`:app`，替代旧 rememberChangelogState）；`MainActivity` / `AppRoot` 不持有 `SettingsRepository`，不得再向 UI 层透传
- 设置页 VM 的 `stateIn` 初值一律引用 `AppSettings.DEFAULTS`（默认值唯一来源），不得硬编码
- 滑块 `onValueChange` 每拍经 VM 调 `settings.set`，与直写同路径，不得额外加节流（契约 6 写后立读）
- `FullScreenHeartRate` 的 `hrState`/`beepPaused` 经定性属瞬时态（与组合作用域内 soundManager 生命周期绑定，每次进入重置），保留 UI 层，理由已存档于代码注释；纯展示页与主题页（ThemeState 等 Hilt 单例为单一事实来源）不要求 ViewModel
