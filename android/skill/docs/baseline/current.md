# 单测基线当前状态

> 本文件记录动态变化的基线数据，每次用例数变化时须同步更新。
> 验证命令与串行要求见 `docs/contracts/07-verification-baseline.md`。

## 当前基线

**全部通过（229 用例，0 失败）**

## 逐模块用例分布

| 模块 | 用例数 |
|---|---|
| :service | 107 |
| :data:settings | 26 |
| :data:repository | 22 |
| :data:database | 20 |
| :feature:settings | 17 |
| :feature:server | 13 |
| :core:designsystem | 11 |
| :core:model | 8 |
| :feature:alarm | 3 |
| :core:ui | 2 |
| **合计** | **229** |

- `:app` 无单测
- 共 28 个测试类

## 历史基线记录

### 2026-09 基线复核（221→229，+8）

差额来自 221 基线之后数个提交对 `SessionChartTrackerTest`、`BleSettingsListenerTest`、`HeartRateRecorderTest`、`BleConnectionHandlerTest`、`FunctionSettingsViewModelTest`、`HeartRateRepositoryTest` 的用例增删。

### 2026-09 HeartRateRepository 迁移

- 修复预存失败 `FunctionSettingsViewModelTest`（cb2629e 改 NAV_ANIMATION_DISABLED 默认值时漏同步测试，以 DEFAULTS 为准修正）
- 新增已知 flaky：`BleConnectionHandlerTest` 的 `successful connect then link loss triggers auto reconnect`（真实时间轮询 + Robolectric 蓝牙 shadow 状态，重跑即过，非回归）
- `HeartRateRecorderTest` 已随组件迁至 `:data:repository` 验证通过

### 2026-08 i18n 数字系统整改

159 文件，规范见契约 12。验证时补录 `:core:model` 的 WebhookTest ×8（213→221，基线命令此前遗漏该模块）。

### 2026-08 MVI 迁移

完成后新增 MviViewModel 基类测试 ×2 与阈值 clamp 纯归约测试 ×2。

### 2026-08 纯 UDF 迁移

新增 29 个设置页 ViewModel 往返一致性/配对状态机用例。

### 历史基线问题修复

AppDatabaseTest / HeartRateRecorderTest 的 Room 3→4 迁移缺失导致的 28 个预存失败已于 2026-08 修复——测试改用 Room 默认驱动（RoomOpenHelper 自行建表与版本管理），不再手工提供 SupportSQLiteOpenHelper + 空 onCreate Callback。

## 更新规则

用例数变化时须同步更新：
1. 本文件的合计数字
2. 逐模块分布表
3. 如有新增模块，同步更新契约 7 中的验证命令
