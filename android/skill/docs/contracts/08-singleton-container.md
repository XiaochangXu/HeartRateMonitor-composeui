# 契约 8：全局单例容器化（禁止新增全局可变单例）

## Hilt 单例组件管理

进程级共享组件一律由 **Hilt 单例组件（SingletonComponent）** 管理：
- 可构造注入的类加 `@Inject constructor`
- 需要装配的用模块内 `@Module @InstallIn(SingletonComponent::class)` `@Provides`
- 进程级单例语义由 `@Singleton` 保证

迁移依据《Hilt渐进式迁移方案.md》Phase 1~7 已完成，`AppContainer`/`AppContainerExt`/`XxxDependencies` cast 链已删除。

## @Singleton 注解必须真实存在

**`@Inject constructor` 的进程级共享状态类必须标注 `@Singleton`**。

Hilt 对无作用域绑定会在每个注入点各建一个新实例。2026-08 曾系统性漏注 18 个类（ThemeState / LiquidGlassState / KillStateSaver / LanTransferSharedState / FairMemoryReceiver / CustomSchemeCache 等），症状是「设置页修改不生效、开关无响应」（写的是设置页自己那份实例的 StateFlow，UI 收集的是另一份）。

- 凡 KDoc 声称「Hilt 单例」的类，注解必须真实存在
- `@Binds` 抽象方法的作用域写在实现类上

## 禁止新增的单例模式

- 禁止新增持有可变状态的顶层 `object`
- 禁止新增 `getInstance()` / DCL / `INSTANCE` 手写单例

### 仅有的两处存量例外（不得新增）

1. `AppDatabase.getDatabase()` DCL（作为 DatabaseModule 的构建函数保留，运行时唯一实例由 Hilt 管理）
2. `Context.settingsDataStore` 顶层属性委托（DataStore 官方硬性要求全进程单实例，访问面见契约 2）

## 仅允许无状态常量对象

`SettingsKeys`、`BleConstants`、`LanTransferProtocol`、`ThemeSource`、`ThemeMode`。

## 状态暴露规范

- 对外可变状态一律 `MutableStateFlow` 私有 + `asStateFlow()` 暴露
- 多线程写的 setter 保持原子更新（CAS）语义

## 初始化顺序

需早初始化的组件（FairMemory 链、主题/液态玻璃配置）由 `HeartRateApp.onCreate` **显式触发**注入字段，顺序：

```
themeState → liquidGlassState → fairMemoryReceiver.initialize → fairMemoryNotifier.initialize → memoryDiagnostics.initialize → themePreviewCache.preload(appScope) → appForegroundMonitor.observe()
```

保证 Composable 读取前就绪。**初始化顺序不得随意调整**（契约 6 红线）。
