# 契约 5：公共工具复用（禁止重复造轮子）

## ModalBottomSheet 弹出

一律用 `ui/util/SheetUtils.kt` 的 `rememberExpandedSheetState()`。

**禁止**手写 `rememberBottomSheetState(Hidden) + LaunchedEffect(expand())`。

## 后台页面暂停收集 Flow

用 `ui/util/FlowUtils.kt` 的 `collectWhenActive()`。

## 设置项容器

用 `ui/settings/SettingsComponents.kt`：
- SettingsGroupCard
- SettingsItem
- SettingsSwitch
- SettingsLink
- DragSlider

## Webhook 触发

必须经 `WebhookRepository.triggerWebhooks()`（内置 5s 节流）。

**禁止**绕过自建 HTTP 请求。
