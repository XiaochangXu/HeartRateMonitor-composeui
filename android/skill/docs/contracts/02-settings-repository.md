# 契约 2：配置读写统一走 SettingsRepository（DataStore 存储层）

违反即视为破坏架构。

## 存储层

设置持久化层为 Preferences DataStore（`data/settings/SettingsDataStore.kt` 顶层委托全进程单例，含 SharedPreferencesMigration 老数据无损迁移）。

**禁止**：任何组件直接调用 `context.getSharedPreferences()`、注册 `OnSharedPreferenceChangeListener` 或自行构造 DataStore 实例。

## 仅有的两个例外（不得新增）

1. `KillStateSaver.save`（进程死亡路径，runBlocking 同步落盘，不可改回即发即忘）
2. `ServiceBootInitializer`（ContentProvider 早于 Application.onCreate，Hilt 组件尚未初始化，永远不能走注入）

## API 用法

- 读：`settingsRepository.get(key)`（读预热内存快照，同步零 IO）
- 监听：`settingsRepository.observe(key)` 返回的 StateFlow（配合 `.drop(1)` 可保持"仅响应变化"语义）
- 写：`settingsRepository.set(key, value)`（异步落盘 + 乐观同步更新缓存，写后立读）
- 可空字符串用 `getNullable` / `observeNullable`
- 多键批量同步读用 `settingsRepository.settings`（`StateFlow<AppSettings>` 类型化全量快照）
- Service 通过 Hilt 注入的 `SettingsRepository` 实例获取（`@Inject lateinit var`，见契约 8/9）

## 键管理

- 键一律使用 `data/settings/SettingsKeys.kt` 的类型化 `Preferences.Key`，禁止字符串字面量与运行时拼键
- 默认值唯一来源是 `AppSettings.DEFAULTS`，禁止在调用点重复声明默认值（仅历史分歧点保留显式默认值重载）
- 新增键必须同时在 `SettingsKeys` 与 `AppSettings`（字段 + DEFAULTS + from()）登记
- 键名字符串禁止改动（老数据迁移依赖）
- 类型化键从结构上杜绝了同名异型写入：异型键编译期即不可用；测试也不得对生产 key 写异型值
