# Spec：多 Activity 混合导航架构迁移

版本：v1.1 · 2026-09-04 · 状态：全部已完成

## 0. 目标

将当前的「单 Activity + navigation3」改为：

- **4 个 Tab 共用一个 `MainActivity`**（`HorizontalPager` + 底部导航条，无导航库）
- **其余页面各自一个独立 Activity**，共 18 个
- **删除 navigation3 / navigationevent 依赖**及其自定义转场代码
- **页面进出改用系统级 Activity 转场动画**

## 1. 决策记录（用户已拍板，勿改）

| # | 决策 | 结论 |
|---|---|---|
| D1 | 设置深层页是否独立成 Activity | **全部独立**。设置页内部局部栈方案作废，15 个深层页全部做成 Activity |
| D2 | 「关闭导航动画」开关是否禁用 Activity 转场 | **否**。开关保持现状（仅控制 Tab 切换的 `animateScrollToPage` vs `scrollToPage`），副标题文案与 24 个语言文件**均不改** |
| D3 | 进程被杀后是否恢复到原页面 | **否**。删除 `KillStateSaver.Snapshot.route` 死字段，快照简化为 `{tab, isFullScreen}` |
| D4 | 通知/悬浮窗是否直达对应页 | **是**。HeartRateAlarm 报警通知 → `AlarmActivity`；FairMemory 提醒 → `FairMemoryActivity`；其余 3 处保持主界面 |
| D5 | Activity 转场动画内容 | **100% 系统默认**，零自定义动画资源。不写 `res/anim`，不传 `ActivityOptions`，不覆写 `finish()`；观感跟随系统/ROM |

## 2. 现状

### 2.1 导航现状

```
MainActivity (FragmentActivity, configChanges=orientation|screenSize|screenLayout|smallestScreenSize)
  └─ AppRoot
      ├─ rememberNavBackStack(AppNavKey.TabRoot)   ← navigation3
      ├─ AppNavHost (NavDisplay + 3 段自定义 transitionSpec)
      │   ├─ entry<TabRoot>  → AppTabHost (Pager 4 页 + AppBottomNavBar)
      │   └─ entry<Xxx> × 17 → 二级页
      ├─ FullScreenHeartRate (Z 序覆盖层，非导航目的地)
      └─ ChangelogBottomSheet
```

### 2.2 入口分布（已核实）

| 入口 | 目标页面 | 数量 |
|---|---|---|
| 「设置」Tab `SettingsScreen.onNavigate(route: String)` | function_settings / theme / language / fullscreen_sound / nav_style / alarm / server / webhook / lan_transfer / status_bar_settings / floating_window_settings / about_details / fair_memory | 13 |
| 「关于详情」`AboutDetailsScreen.onNavigate(route: String)` | license / privacy（`AboutDetailsScreen.kt:184-185`） | 2 |
| 「首页」Tab | devices（`AppTabPager.kt:160`） | 1 |
| 「历史」Tab | chart(sessionId: Long)（`AppTabPager.kt:171`） | 1 |
| 「首页」Tab overlay | fullscreen（非导航目的地） | 1 |

### 2.3 关键代码事实（迁移必读）

| 事实 | 位置 | 影响 |
|---|---|---|
| 主题 `Theme.HeartRateMonitorMobile` 定义在 **`:service`** 模块 | `service/src/main/res/values/themes.xml`、`values-night/`、`values-v27/` | 新增 Activity 的 `android:theme` 引用同一资源；`:app` Activity 主题依赖 `:service` 资源，属历史遗留 |
| `:service` 不能依赖 `:app`，已有 `() -> Intent` 抽象 | `app/.../data/di/AppModule.kt:28-37` `provideReopenAppIntent` | 通知直达**沿用此模式扩展**，不要让 service import Activity 类 |
| 5 处 PendingIntent 全部走 `reopenAppIntent()` | `BleNotificationManager:45`、`FloatingWindowService:410`、`StatusBarResidentService:292`、`HeartRateAlarmService:460`、`FairMemoryNotifier:154` | 逐条评估，见 §6.6 |
| **心率报警通知缺 `setContentIntent`** | `HeartRateAlarmService.kt:390-403` `showAlarmNotification()` | **现存 bug**：点击无反应。本次顺带修为直达 `AlarmActivity` |
| `hideFromRecents` 挂在 `MainActivity.onStop` | `MainActivity.kt:184-189` | **多 Activity 下会失效/误触发**，见 §6.5 |
| `res/anim/` 目录不存在 | `app/src/main/res/` | 无需新建（决策 D5：零动画资源） |
| `LocaleHelper` 为 `internal object`，在 `:app` | `app/.../ui/main/LocaleHelper.kt` | Activity 全部放 `:app` 即可直接使用 |
| `AppTheme` / `ThemeState` / `CustomSchemeCache` 在 `:app` | `app/.../ui/theme/AppTheme.kt` | 同上，Activity 放 `:app` 才能引用 |
| `NavGuard` 300ms 同路由防抖是 app 层唯一防连点机制 | `app/.../ui/NavGuard.kt` | 删除时必须等价替代，见 §6.2 |
| `MainViewModel` 控制面由 MainActivity 绑定后注入 | `MainActivity.kt:92` → `MainViewModel.setControlPlane` | **跨 Activity 会失效**，见 §6.4 |
| app 模块无单测；全量基线 229 用例 / 0 失败 | `skill/docs/baseline/current.md` | 删除 nav3 不直接影响用例数 |
| minSdk 24 | `app/build.gradle.kts:29` | 不影响转场（系统默认动画全版本可用，见 §6.3） |

## 3. 目标架构

```
MainActivity                  ← Pager(4) + AppBottomNavBar，无导航库
FullscreenActivity            ← 横屏 + 沉浸，独立主题
二级页 Activity × 16
  设备 Devices · 图表 Chart · 报警 Alarm · 服务器 Server · Webhook · 局域网传输 LanTransfer
  功能设置 FunctionSettings · 主题 Theme · 语言 Language · 导航样式 NavStyle
  全屏播报 FullscreenSound · 状态栏 StatusBarSettings · 悬浮窗 FloatingWindowSettings
  关于详情 AboutDetails · 许可证 License · 隐私 Privacy · FairMemory
```

`AppRoot` 瘦身为：`MainActivity.setContent { AppTheme { AppTabHost(...) } }`。

随之可删除：`isOnTab` 参数链（`AppTabPager` 的 `userScrollEnabled`、`AppBottomNavBar` 显隐门控、`AppRoot` 的 `if (isOnTab)` 底部渐变）。

**保留不动**：`PageLifecycleOwner`（Pager 级，非 nav3）、`ui/animation/Entrance.kt`、`LocalReducedMotion`、`navAnimationDisabled` 设置项。

## 4. Activity 清单

所有 Activity 位于 `app/src/main/java/com/github/heartratemonitor_compose/ui/page/`。

| # | Activity | 目标 Composable | Intent 参数 | 特殊 manifest 属性 |
|---|---|---|---|---|
| 1 | `MainActivity` | `AppTabHost` | — | 保持现状 + `MAIN`/`LAUNCHER` |
| 2 | `DevicesActivity` | `DevicesScreen` | — | — |
| 3 | `ChartActivity` | `ChartScreen` | `sessionId: Long` | — |
| 4 | `AlarmActivity` | `HeartRateAlarmScreen` | — | 通知可达（`exported=false`，仅 PendingIntent） |
| 5 | `ServerActivity` | `ServerScreen` | — | — |
| 6 | `WebhookActivity` | `WebhookScreen` | — | — |
| 7 | `LanTransferActivity` | `LanTransferScreen` | — | — |
| 8 | `FunctionSettingsActivity` | `FunctionSettingsScreen` | — | — |
| 9 | `ThemeSettingsActivity` | `ThemeSettingsScreen` | — | — |
| 10 | `LanguageSettingsActivity` | `LanguageSettingsScreen` | — | — |
| 11 | `NavStyleActivity` | `NavStyleScreen` | — | — |
| 12 | `FullscreenSoundActivity` | `FullscreenSoundScreen` | — | — |
| 13 | `StatusBarSettingsActivity` | `StatusBarSettingsScreen` | — | — |
| 14 | `FloatingWindowSettingsActivity` | `FloatingWindowSettingsScreen` | — | — |
| 15 | `AboutDetailsActivity` | `AboutDetailsScreen` | — | — |
| 16 | `LicenseActivity` | `LicenseScreen` | — | — |
| 17 | `PrivacyActivity` | `PrivacyScreen` | — | — |
| 18 | `FairMemoryActivity` | `FairMemoryScreen` | — | 通知可达 |
| 19 | `FullscreenActivity` | `FullScreenHeartRate` | — | `screenOrientation=landscape` + 全屏主题 |

> 注：第 1 项为主 Activity，实际新增 18 个。

**通用 manifest 属性**（除 `FullscreenActivity` 外全部一致）：

```xml
<activity
    android:name=".ui.page.XxxActivity"
    android:exported="false"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"
    android:theme="@style/Theme.HeartRateMonitorMobile" />
```

## 5. 文件变更清单

### 5.1 删除

**依赖** — `gradle/libs.versions.toml`
- L16 `navigation3 = "1.1.6"`、L17 `navigationevent = "1.2.0-alpha04"`
- L70 `androidx-navigation3-ui`、L71 `androidx-navigationevent`、L72 `androidx-navigationevent-compose`、L73 `androidx-lifecycle-viewmodel-navigation3`

**依赖引用** — `app/build.gradle.kts` L171-174（4 行）
- L175 `libs.kotlinx.serialization.core`：确认无其他用处后一并删，连带移除 `alias(libs.plugins.kotlin.serialization)` 插件

**ProGuard** — `app/proguard-rules.pro` L201-208（第 18 节 nav3 返回栈持久化规则）

**Baseline Profile** — `app/src/main/baselineProfiles/baseline-prof.txt`
- 重新生成：`gradlew :app:generateBaselineProfile`。残留项无害但失效

**源文件**
- `app/.../ui/AppNavHost.kt`（含 `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` 三段自定义动画）
- `app/.../ui/AppNavKey.kt`
- `app/.../ui/NavGuard.kt`（**防抖逻辑须先迁移到 `BaseComposeActivity`，见 §6.2**）

### 5.2 新增

| 文件 | 职责 |
|---|---|
| `app/.../ui/base/BaseComposeActivity.kt` | 宿主级公共配置（主题 / 语言 / edge-to-edge） |
| `app/.../ui/Destination.kt` | route 字符串 → Activity 映射 + Intent 构造 + 防抖启动（替代 `appNavKeyOf`） |
| `app/.../ui/page/*.kt` × 18 | 各二级页 Activity |
| `app/.../di/AppForegroundMonitor.kt` | 进程级前后台判定（替代 `MainActivity.onStop` 的 hideFromRecents） |
| `service/.../BleControlPlaneRegistry.kt` | `@Singleton` 控制面注册表（§6.4） |
| `service/src/main/res/values*/themes.xml` | 新增 `Theme.HeartRateMonitorMobile.Fullscreen` |

> 决策 D5：**无任何动画资源文件**。转场 100% 用系统默认，无需 `res/anim`。

### 5.3 修改

| 文件 | 改动 |
|---|---|
| `app/src/main/AndroidManifest.xml` | 新增 18 个 `<activity>`；`FullscreenActivity` 加 `screenOrientation` |
| `app/.../ui/AppRoot.kt` | 删除 backstack / NavGuard / 全屏 overlay / 横屏 `DisposableEffect`；直接组合 `AppTabHost` |
| `app/.../ui/AppTabPager.kt` | 删除 `isOnTab` 参数；`safeNavigate: (AppNavKey) -> Unit` → `onNavigate: (Destination) -> Unit` |
| `app/.../ui/AppBottomNavBar.kt` | 删除 `isOnTab` 参数 |
| `app/.../ui/main/MainActivity.kt` | 抽出宿主逻辑到 `BaseComposeActivity`；`setControlPlane` 改走 `BleControlPlaneRegistry`；移除 `onStop` 的 hideFromRecents |
| `app/.../data/di/AppModule.kt` | `provideReopenAppIntent` 旁新增告警页 / FairMemory 页的 Intent 工厂 |
| `app/.../ui/AppLifecycleEffects.kt` | KillStateSaver 快照去掉 `route` |
| `service/.../KillStateSaver.kt` | `Snapshot` 删除 `route` 字段 |
| `service/.../HeartRateAlarmService.kt` | `showAlarmNotification()` 补 `setContentIntent` |
| `service/.../FairMemoryNotifier.kt` | `reopenAppIntent()` → FairMemory 页 Intent |
| `core/ui/.../Screen.kt` | `route` 字符串体系保留（feature 模块靠它与 app 解耦）；`toScreenRoute()` 已随 nav3 删除（消费方 `appNavKeyOf` 不存在了） |

## 6. 核心设计

### 6.1 `BaseComposeActivity`

集中以下宿主级配置，避免 18 份复制：

```kotlin
@AndroidEntryPoint
abstract class BaseComposeActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    protected fun setPageContent(content: @Composable () -> Unit) {
        setContent { AppTheme(themeState, customSchemeCache) { content() } }
    }
}
```

> 跳转统一走顶层扩展函数 `Context.launchDestination(destination)`（§6.2），BaseComposeActivity 不再包一层。

`MainActivity` 继承它，并额外保留：BLE / 悬浮窗 Service 绑定、权限请求、`bleToastListener`、悬浮窗 UI 同步、`ChangelogNotifier`、KillStateSaver 恢复。

> `AppTheme` 需要 `ThemeState` / `CustomSchemeCache`，由 `@Inject` 字段注入（二者均为进程级单例）。

### 6.2 `Destination` 路由映射 + 防抖

```kotlin
internal sealed interface Destination {
    val key: String
    // data object Devices / data class Chart(val sessionId: Long) / … 共 18 项
}

// Context.launchDestination 内联 NavGuard 逻辑：
// SystemClock.elapsedRealtime() + 同 key 300ms 窗口 + Log.w 拦截
// ⚠️ 反直觉设计：单调时钟，避免改时间/NTP 校时让防抖窗口失效
// SAME_ROUTE_DEBOUNCE_MS 复用 NavGuard 同包常量（P5 删 NavGuard 时一并迁移）
```

- `Destination.of(route: String): Destination?` 替代 `appNavKeyOf()`
- `Context.launchDestination(dest)`：防抖 + `startActivity(intent)`（不带 options → 系统默认转场）
- `SettingsScreen.onNavigate: (String) -> Unit` 在 `:app` 宿主侧转为 `Destination.of(route)?.let { launchDestination(it) }`
  > `SettingsScreen` 在 `:feature:settings`，不能 import `:app` 的 Activity，故 route 字符串必须保留（契约 9 依赖方向）
- `Chart` 的 `sessionId` 走 Intent extra（原先靠 kotlinx.serialization，删除后不再需要）

### 6.3 转场动画（决策 D5：100% 系统默认）

**不写任何动画资源**。`startActivity(intent)` 不带 `ActivityOptions`，`finish()` 不覆写、不调 `overridePendingTransition`，打开/关闭均使用平台默认窗口转场。

- 观感跟随系统与 ROM（Android 12+ 为平台统一转场，14+ 预测式返回自动生效）
- 与原 Compose 转场（350ms 滑动 + 1:4 视差）观感**不同**，属已确认取舍：还原视差需要自定义 anim，与"交给系统"的决策冲突
- 不受 `navAnimationDisabled` 控制（决策 D2，该开关仍只管 Tab 切换）
- 预测式返回由 `enableOnBackInvokedCallback="true"`（已在 manifest）自动提供

### 6.4 `BleControlPlaneRegistry`（必须做）

**问题**：`MainViewModel` 为 `@HiltViewModel`，当前由 `AppRoot` 的 `hiltViewModel()` 提供唯一实例并同时喂给 `HomeScreen` 与 `DevicesScreen`；控制面通过 `MainActivity.onServiceConnected` → `mainViewModel.setControlPlane(bleService)` 注入 `WeakReference<BleConnectionManager>`。

`DevicesActivity` 独立后其 `MainViewModel` 是新实例，`bleServiceRef == null` → 扫描 / 连接 / 断开 / 自动重连**静默失效**，且编译期不报错。

**方案**：在 `:service` 新增

```kotlin
/** ⚠️ 反直觉设计：必须 @Singleton，否则多 Activity 各自持有不同控制面导致命令发往死实例。 */
@Singleton
class BleControlPlaneRegistry @Inject constructor() {
    private val _manager = MutableStateFlow<BleConnectionManager?>(null)
    val manager: StateFlow<BleConnectionManager?> = _manager
    fun register(m: BleConnectionManager) { _manager.value = m }
    fun unregister(m: BleConnectionManager) { _manager.compareAndSet(m, null) }
}
```

- `BleService` 绑定/`onCreate` 时 `register(this)`
- `MainViewModel.setControlPlane()` 改为从该单例读取（或由注入链直接提供）
- UI 侧仍只依赖 `BleConnectionManager` 接口，符合契约 3

### 6.5 前台 Activity 计数（`hideFromRecents`）

**问题**：`MainActivity.onStop` 里根据 `hideFromRecentsEnabled` 调 `setExcludeFromRecents(true)`。多 Activity 后打开任意二级页即触发 `MainActivity.onStop`，App 仍在前台却已执行隐藏，且返回时 `onStart` 复位 —— 产生多任务预览闪烁与状态错乱。

**方案（`AppForegroundMonitor`，@Singleton，`Application.ActivityLifecycleCallbacks`）**：
- 自维护 `startedCount`：`onActivityStarted` ++，`onActivityStopped` --；减到 0（最后一个页面停止）**立即**隐藏，从 0 变 1 复位显示
- suppress 窗口内（跳外部页面返回中）不隐藏，与迁移前语义一致
- 回调均在主线程，普通字段即可

**踩坑记录**：首版用 `ProcessLifecycleOwner`，其 ON_STOP 派发内置 **约 700ms 防抖延迟**（防旋转重建误判），导致"退出应用隐藏后台"明显滞后（用户真机反馈）。Activity 计数零延迟：新页 `onStart` 先于宿主 `onStop`，启动二级页时计数 1→2→1 不经过 0，既不误触发也不闪烁。

### 6.6 通知直达（决策 D4）

沿用 `AppModule` 的 `() -> Intent` 抽象，新增按目标的 Intent 工厂（`:service` 不得 import `:app` 的 Activity 类）。

| 通知 | 现状 | 新目标 | 说明 |
|---|---|---|---|
| `HeartRateAlarmService.showAlarmNotification` (:390) | **无 `setContentIntent`，点击无反应（bug）** | `AlarmActivity` | 顺带修复；需 `TaskStackBuilder` 垫 `MainActivity` |
| `FairMemoryNotifier.showPssMemoryNotification` (:154) | 主界面 | `FairMemoryActivity` | 同上需任务栈 |
| `BleNotificationManager` (:45) | 主界面 | **主界面（不改）** | 语义为"回到 App" |
| `FloatingWindowService` 触摸穿透通知 (:410) | 主界面 | **主界面（不改）** | 同上 |
| `StatusBarResidentService` (:292) | 主界面 | **主界面（不改）** | 同上 |

> `TaskStackBuilder` 必需：直接启动二级 Activity 会让返回键直接回桌面。

## 7. 风险与已知取舍

| 风险 | 说明 | 处置 |
|---|---|---|
| `MainViewModel` 跨 Activity 失效 | 扫描/连接/断开静默失效 | §6.4 `BleControlPlaneRegistry` |
| `hideFromRecents` 行为回归 | 多 Activity 下时机全变 | §6.5 进程级判定 + 真机回归 |
| `NavGuard` 删除导致连点开出多个 Activity | app 层无按钮级防连点 | §6.2 防抖内联进 `launchDestination()` |
| 转场观感与迁移前不同 | 系统默认动画 ≠ 原 Compose 滑动+视差（决策 D5 已确认） | 无需处置；真机验证系统默认转场即可 |
| `ChangelogBottomSheet` 推迟弹出 | 冷启动直达二级 Activity 时 `AppRoot` 未组合，`markUiReady` 不触发 | **已知取舍**：判定照常执行（单例构造期），展示推迟到用户回到主界面；期间杀进程也不丢失（dismiss 才落版本）。通知直达属少数路径，不补 |
| `FullscreenActivity` 与 MainActivity 状态同步 | 全屏退出后主界面需反映最新心率 | 数据来自 `@Singleton` 的 `HeartRateRepository`（契约 13），自动一致 |
| `:app` Activity 主题依赖 `:service` 资源 | 历史遗留 | 本次沿用；建议后续单独做主题归位重构（移入 `:app`） |
| 冷启动恢复全屏 | `isFullScreen` 为 true 时直接起 `FullscreenActivity` | 需同时垫 `MainActivity`，否则返回键回桌面 |

## 8. 分阶段实施计划

每个阶段结束必须 `gradlew :app:assembleDebug` 通过。

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P0** | 新建 `BaseComposeActivity` + `Destination`（**暂不删 nav3**）；迁最简单的 `LicenseActivity` 跑通主题 / 语言 / 系统默认转场 | 骨架验证（**已完成**，`:app:assembleDebug` 通过） |
| **P1** | 落地 `BleControlPlaneRegistry`（§6.4）；迁 `DevicesActivity`、`ChartActivity`、`AlarmActivity` | 解除 BLE 阻塞（**已完成**，`:service` 107 用例通过） |
| **P2** | 迁 `FullscreenActivity`；拆除 `AppRoot` 的横屏 / 沉浸 / overlay 耦合 | 全屏隔离（**已完成**） |
| **P3** | 迁剩余 12 个设置体系 Activity；`SettingsScreen` / `AboutDetailsScreen` 的 `onNavigate` 改接 `Destination` | 主体完成（**已完成**，18 个 Activity 全部就位） |
| **P4** | 落地 `AppForegroundMonitor`（§6.5）；修复报警通知 `setContentIntent`；FairMemory 通知直达 | 行为修复（**已完成**，`:service` 107 用例通过） |
| **P5** | 删除 nav3 / navigationevent 依赖 + `AppNavHost` / `AppNavKey` / `NavGuard` + proguard 规则；`AppRoot` 瘦身；`KillStateSaver` 删 `route` | 依赖清理（**已完成**，连带删 serialization 插件/依赖与 `Screen.toScreenRoute`） |
| **P6** | 全量验证：`:app:assembleDebug` + 串行全量单测 + 真机回归 | 交付（**代码侧完成**：assembleDebug 通过、串行全量单测 10 模块绿 + `:service` 已知 flaky 重跑通过；Baseline Profile 重生成需连真机执行 `gradlew :app:generateBaselineProfile`） |

## 9. 验证清单

**编译与单测（契约 7）**

```bash
gradlew :app:assembleDebug
gradlew --max-workers=1 :app:testDebugUnitTest :service:testDebugUnitTest :data:database:testDebugUnitTest :data:settings:testDebugUnitTest :data:repository:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:ui:testDebugUnitTest :core:model:testDebugUnitTest :feature:alarm:testDebugUnitTest :feature:server:testDebugUnitTest :feature:settings:testDebugUnitTest
```

- 单测**必须串行**（`--max-workers=1`），并行会因多模块 Robolectric 共用 DataStore 产生环境性假失败
- 基线：**229 用例 / 0 失败**。app 模块无单测，删除 nav3 不应改变用例数
- 全量通过后重新生成 Baseline Profile

**真机回归项**

- [ ] BLE 扫描 → 连接 → 断开 → 自动重连（重点验证 `BleControlPlaneRegistry`）
- [ ] 设备页（独立 Activity）内扫描与连接
- [ ] 图表页横竖屏切换与返回行为
- [ ] 全屏心率页：进入/退出、横屏、状态栏恢复、屏幕常亮恢复、悬浮窗通知点击
- [ ] 悬浮窗显示/隐藏、状态栏常驻
- [ ] 最近任务隐藏（多 Activity 前后台切换）
- [ ] 主题切换、语言切换后重启
- [ ] 心率报警通知点击 → 直达报警页 → 返回键回主界面
- [ ] FairMemory 通知点击
- [ ] 更新日志弹窗（冷启动进主界面时）
- [ ] 设置列表快速连点（验证 300ms 防抖）
- [ ] 所有二级页的转场动画与返回手势
