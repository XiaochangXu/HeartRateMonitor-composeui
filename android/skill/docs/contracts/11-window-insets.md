# 契约 11：Jetpack Compose WindowInsets 避坑（禁止违反）

## 问题：LazyColumn 上使用 windowInsetsPadding 导致系统手势条区域出现色块

### 症状

Tab 页面底部导航栏下方的系统手势条区域出现一块与页面背景色一致的长方形色块，而二级页面（全屏沉浸）无此问题。

### 根因

在 `LazyColumn` 的 `modifier` 上使用 `windowInsetsPadding(WindowInsets.navigationBars)` 会**缩小 LazyColumn 的布局区域**——LazyColumn 本体不再延伸到屏幕底部，底部留出的空白 padding 区域被 Scaffold 的 `containerColor` 填充，形成与页面背景色一致的色块。

叠加 `AppRoot` 底部渐变层后，该色块在 Tab 页面可见。

而二级页面（非 Tab）没有 `AppRoot` 底部渐变层覆盖，且自身 `LazyColumn` 的 `windowInsetsPadding` 留白与系统手势条沉浸区域视觉一致，所以"看起来没问题"。

## 正确做法：contentPadding 方式（Tab 页面强制，二级页面建议）

`LazyColumn` / `Column`（含 `verticalScroll`）等可滚动容器避让系统手势条时，**Tab 页面必须用 `contentPadding` 的 `bottom` 消化 inset，不得在 `modifier` 上加 `windowInsetsPadding`**。

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

## 适用范围

本规则适用于所有 Tab 页面（`HomeScreen`、`HistoryScreen`、`FavoriteDevicesScreen`、`SettingsScreen`）及未来新增的 Tab 页面。

二级页面（非 Tab）可按需选择方式，但为保持一致性建议同样遵循 `contentPadding` 方式。

## 涉及的组件联动

- `AppRoot` 底部渐变层（`bottomGradientHeightDp`）的高度计算已包含 `navBarInsetPx + FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_MARGIN`，覆盖系统导航条 inset + 悬浮应用导航条区域。Tab 页面 `LazyColumn` 必须用 `contentPadding` 方式，使渐变层正确覆盖到底部而不出现色块。
- `HomeSpeedDialFab` 的 FAB 悬浮位置通过 `navBarInset: Dp` 参数计算 `Modifier.padding(bottom = navBarInset + ...)`，不得改用 `windowInsetsPadding`。
