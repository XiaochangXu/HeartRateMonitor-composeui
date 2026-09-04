# 契约 7：验证基线

## 验证命令（较大改动或用户要求时执行）

较大改动或用户要求时执行（判断标准见 SKILL.md「验证基线」节）。小改动可跳过：

```bash
gradlew :app:assembleDebug
gradlew --max-workers=1 :app:testDebugUnitTest :service:testDebugUnitTest :data:database:testDebugUnitTest :data:settings:testDebugUnitTest :data:repository:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:ui:testDebugUnitTest :core:model:testDebugUnitTest :feature:alarm:testDebugUnitTest :feature:server:testDebugUnitTest :feature:settings:testDebugUnitTest
```

新增模块或新增含单测的模块后，验证命令须含各模块自身的 `:X:testDebugUnitTest`。

## 单测必须串行

`--max-workers=1` 是硬性要求。

**原因**：并行跑时多模块 Robolectric 测试共用同一 app 数据目录的 `settingsDataStore` 文件，会随机在不同模块产出环境性假失败（每次挂的用例都不一样），掩盖真实回归。串行全量跑（可加 `--continue` 保证跑完所有模块）才是可信基线。

## 当前基线状态

见 `docs/baseline/current.md`（用例数、分布、历史记录均在该文件维护）。

出现新失败必须修复。

## 已知 flaky（非回归，勿按新失败处理）

### SettingsRepositoryTest 相关

- `int negative values`
- `observe int emits current value and updates`
- `nullable string set and get`

在全量并行跑时偶发「Int 写入后立读返回默认值 0」或「写入后立读返回 null」——属 SettingsRepository KDoc 已声明的「瞬态回退窗口」（DataStore 落盘发射与 Unconfined 乐观缓存的对账时序竞态，测试内 `awaitDiskReconciled` 无法完全消除）。

### 外溢到其他模块的偶发失败

同一根因（各模块共用 `settingsDataStore`，Robolectric app 数据目录）还会导致：
- `LiquidGlassStateTest` 的 `setBlur and setDistortion update state flow and persist`
- `FloatingWindowSettingsViewModelTest` 的 `icon switches write and flow back`（`awaitUiState` 5s 未收敛）
- `BleConnectionHandlerTest` 的 `successful connect then link loss triggers auto reconnect`（真实时间轮询 + Robolectric 蓝牙 shadow 状态）

**特征**：每次失败的模块/用例不固定，串行或单模块 `--rerun-tasks` 重跑必过。

### 处置方式

1. 全量验证加 `--max-workers=1` 串行跑
2. 若仍偶发失败，单独 `--rerun-tasks` 重跑该模块
3. 通过即视为环境性失败，无需改代码

## 真机回归提示

涉及 BLE 连接/断开/重连、设置热更新的改动需提示用户真机回归。
