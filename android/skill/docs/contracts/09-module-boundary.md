# 契约 9：模块边界契约（多模块化，禁止破坏）

模块化重构依据《Android多模块化渐进式重构方案.md》（Phase 1~8 已完成）。

## 9.1 模块清单与职责

```
:app               应用壳：HeartRateApp（@HiltAndroidApp + Configuration.Provider）/
                   MainActivity / AppRoot / AppNavHost / AppTabPager /
                   AppBottomNavBar / AppLifecycleEffects / AppChangelogState / NavGuard /
                   AppTheme / AppModule（@AppScope 作用域、() -> Intent 等 :app 专属绑定）/ Manifest / 签名 / 混淆
:core:model        领域模型（data.model、data.Webhook/WebhookTrigger），零模块依赖
                  （禁止依赖任何项目内模块；允许外部库，如 immutable / serialization）
:core:designsystem 主题视觉（Color/Type/ThemeConfig/ThemeState/LiquidGlassState/CustomSchemeCache/
                   无状态主题函数/ThemePresetSeeds 之外），唯一依赖 :data:settings
:core:ui           通用 UI（widgets/animation/util/Screen 路由/SettingsComponents/SoundManager/
                   通用字符串与图标）
:data:settings     DataStore 存储 + SettingsRepository + settingsDataStore 单例（契约 2 例外）
:data:database     Room（Entity/DAO/AppDatabase/schemas），KSP 在此模块
:data:repository   仓储层 + webhook/network/sensor/system 封装 + ModelMappers（契约 1 映射归口）+
                   HeartRateRecorder 落盘缓冲（2026-09 迁入，包名不变）
:service           BLE/常驻/悬浮窗/预警/局域网服务 + ble + init + LanTransferSharedState +
                   HeartRateRepository（实时数据流 SSOT，契约 3/13）+ ServiceModule（ServiceLauncher @Binds）
:feature:*         main/settings/history/alarm/server/webhook/favorite 页面
:baselineprofile   现有模块，不动
```

## 9.2 依赖方向（编译期强制）

- 自上而下：`app → feature → (core | data | service)`；`service → (core | data)`；`data → core`
- `:core:designsystem → :data:settings` 是唯一 core→data 特例

**禁止**：
- 任何模块依赖 `:app`
- feature 之间互依
- data 依赖 UI/service
- core 依赖 feature/service

新增依赖前先核对目标模块是否在允许方向内；跨 feature 共享代码一律下沉 `:core:ui`/`:core:designsystem`。

## 9.3 namespace 与包名

### 模块 namespace

- `:core:model` → `...data.model`
- `:core:designsystem` → `...ui.theme`
- `:core:ui` → `...ui.widgets`
- `:data:settings` → `...data.settings`
- `:data:database` → `...data.db`
- `:data:repository` → `...data.repository`
- `:service` → `...service`
- `:feature:*` → `...feature.<name>`（唯一，AGP 要求）

### Kotlin 包名

保持迁移前路径不变（`com.github.heartratemonitor_compose.*`），跨模块同包类型无需 import；namespace 只决定各模块 R 类。

### 跨模块引用资源

`import <目标模块 namespace>.R` 或全限定 `xxx.R.string.yyy`。

**禁止引用 `:app` 的 R**（非传递 RClass 已开启）。

## 9.4 依赖注入模式（Hilt，禁止手动 DI cast / Koin）

### 组合根

`:app` 的 `@HiltAndroidApp HeartRateApp`（`SingletonComponent` 宿主）+ 各模块 `@Module @InstallIn(SingletonComponent::class)` 装配类：
- `:data:database` DatabaseModule
- `:data:settings` SettingsModule + `@AppScope` Qualifier
- `:service` ServiceModule
- `:app` AppModule

### 使用点取依赖（三选一，按优先级）

1. **构造注入**：`@Inject constructor`（Repository/Provider/单例服务类），`Context` 参数标 `@ApplicationContext`
2. **ViewModel**：`@HiltViewModel` + `@Inject constructor`，Composable 用 `hiltViewModel()`（import `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`）
3. **Android 组件**：`@AndroidEntryPoint` + `@Inject lateinit var` 字段注入（Activity / Service / Application）

### Composable 依赖

一律经 hiltViewModel() 的 ViewModel 构造注入后下发（含一次性展示依赖，如 `ThemeSettingsViewModel.themePreviewCache`）。

`@EntryPoint` 窄接口 + `EntryPointAccessors.fromApplication` 模式仅作兜底手段保留（既有 MainDependencies / ServerDependencies / SettingsDependencies 已全部移除，当前无使用方）。
- 新增 Composable 依赖不得再走 EntryPoint
- 接口方法必须是无参抽象属性

### Worker

`@HiltWorker` + `@AssistedInject`（`HeartRateApp` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`）。

### 新模块接入流程

加 `hilt-android` + `ksp(hilt-compiler)`：
- 含 `@AndroidEntryPoint` 的模块另加 hilt Gradle 插件
- 含 `@HiltWorker` 的另加 hilt-work + `ksp(hilt-androidx-compiler)`
- ViewModel 模块另加 hilt-navigation-compose

→ 类加 `@Inject`/`@Provides`/`@EntryPoint` → 使用点注入。

### 禁止

- `application as XxxDependencies` 之类运行时 cast 取依赖
- 手写 ViewModel Factory / 手写单例容器
- 模块内 import `:app` 的 `HeartRateApp` / `data.di.*`（Hilt 绑定一律在模块自身或 `:app` AppModule 提供）

## 9.5 新代码放哪个模块（决策表）

| 代码类型 | 归属 |
|---|---|
| 领域模型/纯数据类 | `:core:model` |
| 颜色/字体/主题函数/主题状态 | `:core:designsystem` |
| 通用 Composable/动画/UI 工具/路由常量/通用字符串图标 | `:core:ui` |
| 设置读写/DataStore | `:data:settings` |
| Entity/DAO/数据库 | `:data:database` |
| Repository/网络/传感器/系统封装/落盘缓冲（HeartRateRecorder） | `:data:repository` |
| BLE 实时数据流 SSOT（HeartRateRepository，契约 13） | `:service` |
| BLE/服务/通知/局域网服务 | `:service` |
| 某功能页面及其 ViewModel | 对应 `:feature:*` |
| 跨 feature 共享的页面组件 | 下沉 `:core:ui`（禁止 feature 互依） |
| 组合根装配/Activity/Manifest/签名/混淆 | `:app` |

## 9.6 资源与依赖声明

### 资源

- 资源随代码走：feature/模块独有资源随迁，跨模块共享资源下沉 `:core:ui`
- `values`/`values-zh` 成对迁移
- 字符串 key 全仓库唯一（禁止两处定义）

### 模块间依赖

- 默认 `implementation`
- 类型出现在模块公开 API（方法返回/构造参数）时用 `api`（如 `:data:database` 的 DAO/Entity、`:data:repository` 对三个数据模块）

### 版本管理（强制）

版本一律走 `gradle/libs.versions.toml`，禁止模块内硬编码版本。

- **所有外部依赖**（含测试依赖）必须通过 `libs.versions.toml` 的 `version.ref` 统一管理，禁止在 `build.gradle.kts` 中出现 `"group:name:version"` 硬编码字符串
- **toml 内禁止内联版本号**：`[libraries]` 区所有库声明的版本必须引用 `[versions]` 区的变量（`version.ref = "xxx"`），禁止写 `version = "1.0.0"` 内联字面量
- **新增依赖时**：先在 `[versions]` 区添加版本变量，再在 `[libraries]` 区添加库声明，最后在 `build.gradle.kts` 中用 `libs.xxx` 引用；三步缺一不可
- **共享版本号提取为一个变量**：同组库共享同一版本时（如 `sqlite-bundled` 与 `sqlite-framework` 共用 `androidxSqlite`），提取为单个 `version.ref`，避免多处分散维护
- 仓库根目录 `.github/dependabot.yml` 已配置 Dependabot 自动更新 toml 中的版本，禁止在模块内绕过 toml 手动指定版本以规避 Dependabot 覆盖

### 混淆

`:app` 的 `proguard-rules.pro` 保留全部 keep 规则（R8 最终打包生效）。

### 模块移动注意

敏感机制（契约 6）与映射归口（契约 1）在模块移动后保持原样，禁止借模块化重构业务逻辑。
