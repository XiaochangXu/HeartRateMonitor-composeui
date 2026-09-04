# 契约 4：组件职责与体量上限

## MainViewModel 职责

仅 BLE 状态订阅 + 组件编排：
- 含自 MainActivity 迁入的启动编排：自动连接判定/服务恢复/悬浮窗切换/会话清理/BLE Toast 联动
- 对外 StateFlow
- 数据面订阅在构造期从 `HeartRateRepository` 直出，并按 `Repository.bleState.value` 同步恢复 appStatus（原 setConnectionManager 状态恢复补丁的等价物：bleState 订阅 drop(1) 跳过首帧重放，值流重放恢复不了 appStatus，见契约 13）
- 图表数据管道（RR→Point→Snapshot→窗口）归服务层 `SessionChartTracker`（内聚于 `HeartRateRepository`，UI 直接订阅 Repository）
- 历史记录开关的图表 reset/clear 联动由 `BleSettingsListener` 在服务端接管
- "删除收藏并恢复最近"逻辑归 `FavoriteDeviceRepository.deleteAndRestoreLatest()`

## 体量上限

- 单个 Composable 文件建议 ≤ 350 行
- 单个子组件建议 ≤ 150 行
- 超限时按职责拆为同包新文件（`internal` 可见性），状态通过参数/回调提升传递

## 页面结构模式

Screen 主文件只做状态收集与编排，子区域提取为独立 Composable 文件（参考 ui/alarm、ui/settings 现有拆分）。
