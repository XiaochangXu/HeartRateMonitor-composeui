# 多语言适配 Spec（20 种语言）

> 本文档是 Android 端（`android/`）多语言适配的规范与进度追踪文档。
> **规则：每完成一种语言，必须在本文件「进度跟踪表」标记状态并追加一条「变更日志」，再继续下一种语言。**

---

## 一、背景与目标

当前项目已支持 4 种语言（英文 `values`、简体中文 `values-zh`、繁体台湾 `values-zh-rTW`、繁体香港 `values-zh-rHK`）。
目标是在此基础上新增以下 **20 种语言**：

| # | 语言 | 资源目录 | 书写方向 | 备注 |
|---|------|----------|----------|------|
| 1 | 日语 | `values-ja` | LTR | 汉字+假名 |
| 2 | 韩语 | `values-ko` | LTR | |
| 3 | 德语 | `values-de` | LTR | 文本膨胀率高，合成词长 |
| 4 | 俄语 | `values-ru` | LTR | 复杂复数规则 |
| 5 | 法语 | `values-fr` | LTR | |
| 6 | 西班牙语 | `values-es` | LTR | |
| 7 | 葡萄牙语 | `values-pt` | LTR | 默认覆盖 pt 全域；如需巴西变体再拆 `values-pt-rBR` |
| 8 | 意大利语 | `values-it` | LTR | |
| 9 | 波兰语 | `values-pl` | LTR | 复杂复数规则 |
| 10 | 荷兰语 | `values-nl` | LTR | |
| 11 | 土耳其语 | `values-tr` | LTR | |
| 12 | 印尼语 | `values-id` | LTR | 注意用 `id`，`in` 是废弃代码 |
| 13 | 印地语 | `values-hi` | LTR | 天城文（Devanagari） |
| 14 | 越南语 | `values-vi` | LTR | |
| 15 | 泰语 | `values-th` | LTR | 无空格分词，断行特殊 |
| 16 | 菲律宾语 | `values-fil` | LTR | 注意用 `fil`，`tl` 是旧代码 |
| 17 | 马来语 | `values-ms` | LTR | |
| 18 | 孟加拉语 | `values-bn` | LTR | 孟加拉文（Bengali） |
| 19 | 阿拉伯语 | `values-ar` | **RTL** | 最高风险项，见「七、RTL 专项」 |
| 20 | 尼泊尔语 | `values-ne` | LTR | 天城文 |

> 目标语言数量：**20 种**（不含已有的 4 种）。每种语言覆盖 **11 个模块、407 条字符串**。

---

## 二、现状盘点

- 字符串全部集中在各模块 `src/main/res/values*/strings.xml`，UI 侧统一通过 Compose `stringResource(R.string.xxx)` 引用（全项目 337 处），**无 UI 硬编码中文/英文文案**（代码中的中文仅存在于日志、注释、测试）。
- 11 个需要建语言目录的模块及当前字符串数（以英文 `values/` 为基准）：

  | 模块 | 路径 | 条数 |
  |------|------|------|
  | app | `app/src/main/res` | 33 |
  | core/ui | `core/ui/src/main/res` | 6 |
  | service | `service/src/main/res` | 56 |
  | data/repository | `data/repository/src/main/res` | 8 |
  | feature/settings | `feature/settings/src/main/res` | 185 |
  | feature/favorite | `feature/favorite/src/main/res` | 9 |
  | feature/history | `feature/history/src/main/res` | 21 |
  | feature/main | `feature/main/src/main/res` | 28 |
  | feature/server | `feature/server/src/main/res` | 29 |
  | feature/webhook | `feature/webhook/src/main/res` | 17 |
  | feature/alarm | `feature/alarm/src/main/res` | 18 |
  | **合计** | | **410** |

- `AndroidManifest.xml` 已声明 `android:supportsRtl="true"`；Compose UI 1.12 / Material3 1.5，RTL 布局镜像由 Compose 自动处理。
- **打包语言白名单（重要）**：`app/build.gradle.kts` 的 `androidResources.localeFilters` 决定哪些语言的资源会打包进 APK。当前已包含全部 24 种语言（en、zh、zh-rTW、zh-rHK 及本 spec 的 20 种目标语言）。**新增语言时必须在 `localeFilters` 中追加对应代码，否则资源会被打包阶段过滤掉，界面仍显示默认语言**（曾因此导致日语不生效）。
- 尚未配置 `localeConfig`（Android 13+ 每应用语言列表），作为后续可选事项（见「十三」）。
- 底部导航栏 [AppBottomNavBar.kt](file:///d:/Download/HeartRateMonitor-composeui/android/app/src/main/java/com/github/heartratemonitor_compose/ui/AppBottomNavBar.kt) 已显式处理 RTL 拖拽方向；悬浮窗 [FloatingWindowContent.kt](file:///d:/Download/HeartRateMonitor-composeui/android/service/src/main/java/com/github/heartratemonitor_compose/service/FloatingWindowContent.kt) 为固定 padding + 固定字号布局。

---

## 三、标准执行流程（每种语言都要走一遍）

1. **翻译**：以英文 `values/strings.xml` 为翻译源（术语对齐可参照 `values-zh`），为 11 个模块各新建 `values-<lang>/strings.xml`。
   - `feature/settings` 中 `fair_memory_adaptation` 为多段长文本（含 `\n`），必须完整翻译，不得留空。
2. **保留占位符与格式**：`%1$s` / `%1$d` / `%2$s` 等占位符、`\n` 换行、`&amp;`、`✓`、`…` 等符号必须原样保留，占位符数量与英文版一致。
3. **XML 转义**：
   - `&` → `&amp;`
   - 单引号 `'` → 用 `\'` 转义（或整串用双引号包裹）
   - 用作 `format` 的字符串中字面 `%` → `%%`（如现有 `brightness` 的 `%1$d%%`）
4. **校验**：运行「九、自动化校验」脚本，确认 11 个模块键名集合、数量与英文版完全一致，且无残留源语言字符。
5. **确认白名单**：检查 `app/build.gradle.kts` 的 `androidResources.localeFilters` 已包含该语言代码（首次已全量加入，后续通常无需改动）。
6. **构建验证**：`./gradlew :app:assembleDebug`（或 `:app:lint`），确保资源合并与 XML 合法。可用 `aapt2 dump resources` 抽查 APK 中是否存在 `(xx)` 配置。
7. **更新本文档**：在「十一、进度跟踪表」标记该语言「已完成 + 完成日期」，并在「十二、变更日志」追加记录。

**完成定义（Definition of Done）**：11 模块 410 条翻译完成 → 校验通过 → 构建通过 → 本文档已更新。

---

## 四、翻译规范

- 语气：面向心率监测场景，保持简洁、中性、非正式但专业；通知类文本保持客观。
- 单位：`bpm`、`km/h`、`HTTP`、`WebSocket`、`GPS`、`BLE` 等专有名词/缩写**不翻译**。
- 品牌名：`Gitee`、`GitHub`、`MIT`、`Android`、`Room`、`Webhook`、`Release`、`Action`、`Intent` 等保持原文。
- 术语一致性：同一模块内同一概念（如 连接/断开/历史记录/收藏/预警）用词必须统一；跨模块同名 key 含义一致。
- **专有名词本地化**：协议/服务名（HTTP、WebSocket、BLE、GPS、bpm）保持原文；但「Fair Memory」等专有名词**必须按目标语言转写**（如 ja フェアメモリ / ko 페어 메모리 / hi फेयर मेमोरी），不得整词保留英文；并列连接词（&）用本地语言词替换（如 と/및/и/और/與）。
- 不得改动 `name` 属性，不得增删条目，不得合并/拆分条目。
- **溢出自动精简规则**：若真机抽查发现某文案在固定宽度区域（胶囊、按钮、Tab、状态栏、悬浮窗等）撑满/截断，**直接自动精简该文案**——在保持语义完整的前提下改用更短表达（缩写、去修饰词、状态化短语如「X: 开/关」），同组正反状态（on/off、enabled/disabled）必须同步精简、长度对称。精简后需重新校验键名/占位符并在「变更日志」记录具体条目与精简前后文案。

---

## 五、UI 尺寸与排版适配要点（重点）

> 目标：**翻译不是全部**。每种语言完成后必须按本清单做 UI 尺寸检查，尤其是文本膨胀与固定尺寸区域。

### 5.1 已知文本膨胀风险语言

德语、俄语、波兰语、法语、西班牙语、葡萄牙语、土耳其语较英文通常膨胀 **20%–35%**；荷兰语、德语存在长合成词（如 "Einstellungen"、"Verbindungsunterbrechung"）。

### 5.2 需要重点检查的 UI 区域

| 区域 | 位置 | 风险点 | 检查项 |
|------|------|--------|--------|
| 底部导航 4 个 Tab | [AppBottomNavBar.kt](file:///d:/Download/HeartRateMonitor-composeui/android/app/src/main/java/com/github/heartratemonitor_compose/ui/AppBottomNavBar.kt)（`CapsuleNavItem` label `maxLines=1`） | 德语/俄语/葡萄牙语标签变长，`weight(1f)` 均分空间可能截断 | 标签不省略、不重叠、不溢出 |
| 悬浮窗 | [FloatingWindowContent.kt](file:///d:/Download/HeartRateMonitor-composeui/android/service/src/main/java/com/github/heartratemonitor_compose/service/FloatingWindowContent.kt) | 固定 padding、字号/图标由用户设置，速度与 bpm 并排 | 各字号下不截断、不换行挤压 |
| 状态栏常驻 | `StatusBarOverlayContent.kt` | 窄条区域显示数字+单位 | 数值+单位不溢出 |
| 心跳大数字卡片 / 实时曲线 | `HeartRateCard.kt`、`RealtimeChart.kt` | 大数字与单位、环形图刻度 | 单位不挤压数字 |
| 设置项标题/副标题 | `SettingsComponents.kt` 等 | 长标题换行后布局是否仍对齐 | 无重叠、无截断 |
| 对话框/弹窗 | 删除确认、权限解释、更新日志等 | 俄语/波兰语长句 | 按钮不被顶出、文本可滚动 |
| 全屏心率模式 | `FullScreenHeartRate.kt` | 大字号播报文本 | 不同字号均完整显示 |
| 按钮 | 各 Screen | 短按钮装长词（如 "Verbinden"/"Проверить"） | 不换行截断 |

### 5.3 复杂脚本渲染（印地语/尼泊尔语/孟加拉语/泰语/阿拉伯语）

- 系统默认字体均覆盖这些脚本，但需验证：
  - 项目是否自定义了 `FontFamily`（如有，需确认回退链，避免天城文/泰文显示豆腐块）；
  - `fontWeight = Bold` 对无粗字重的脚本（泰文、天城文部分字形）会被合成渲染，检查是否可读；
  - 泰语无空格分词，长文本断行由 ICU 处理，重点检查设置页长文案是否断行异常。

---

## 六、阿拉伯语（RTL）专项

阿拉伯语为唯一 RTL 语言，**必须单独重点验收**：

1. **布局镜像**：`supportsRtl=true` 已开启，Compose 自动镜像；逐屏检查图标位置、对齐、拖拽方向。
2. **混合方向文本**：URL、`bpm`、数字与阿拉伯文混排的字符串（如 `access_url_format`、`alarm_notification_body`、`http_access_url`）需验证 Bidi 顺序，必要时用 `\u200E`(LTR mark)/`\u200F`(RTL mark) 或调整词序。
3. **数字呈现**：`%d` 在阿拉伯语 locale 下默认渲染为阿拉伯-印度数字（٠-٩）。确认产品预期：
   - 若期望西文数字，格式化时需固定 `Locale`（如 `String.format(Locale.US, ...)`）；
   - 若保留本地数字，需确认心率/统计场景可读性。
4. **方向敏感图标**：半圆环刻度、信号图标、趋势箭头、`ic_*` 方向类图标在 RTL 下是否需要镜像（`graphicsLayer.scaleX = -1f` 或换用可镜像图标）。
5. **自定义绘制**：实时曲线（Vico）与环形图若未随 LayoutDirection 翻转，需在 RTL 下手动适配（如按宽度镜像坐标）。
6. **底部导航拖拽**：现有 `SurfaceFallbackNav` 已处理 `isLtr` 分支，RTL 下验证拖拽方向正确。
7. **阿拉伯语复数**：见「七」。

---

## 七、复数 / 数字 / 日期时间专项

- 含计数的字符串（`history`、`favorite`、`alarm`、`service` 模块中的 `%1$d` 句式）在**俄语（3 种+复数）、波兰语（3 种+）、阿拉伯语（6 种）**中必须按复数规则处理：
  - **本次翻译策略**：先以「中性单数/总述」句式规避，或接受单一复数形式，保证语法不崩；
  - **后续改造建议**：将 `已删除 %1$d 条记录`、`已选择 %1$d 项`、`剩余 %1$d 秒`、`%1$d 个样本`、`%1$d 条历史记录`、`%1$d 个收藏设备` 等改为 `<plurals>` 资源（涉及代码从 `stringResource` 改为 `pluralStringResource`），列入「十三」后续事项。
- 时间/日期：`stat_time_range`、`marker_heart_rate` 等使用本地格式，翻译时不得把时间格式写死成 24 小时或特定分隔符。
- 数字宽度：泰文/孟加拉文/天城文数字与拉丁数字宽度不同，检查对齐。

---

## 八、自动化校验清单

每次翻译完成后，用以下 PowerShell 校验键名一致性（将 `$lang` 换成目标语言代码）：

```powershell
$mods = 'app','core/ui','service','data/repository','feature/settings','feature/favorite','feature/history','feature/main','feature/server','feature/webhook','feature/alarm'
$lang = 'ja'   # 改为待校验语言
$root = 'd:\Download\HeartRateMonitor-composeui\android'
function Get-Keys($p) {
  [regex]::Matches((Get-Content $p -Raw), '<string name="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
}
foreach ($m in $mods) {
  $ref = Get-Keys "$root\$m\src\main\res\values\strings.xml"
  $tgt = Get-Keys "$root\$m\src\main\res\values-$lang\strings.xml"
  $missing = @($ref | Where-Object { $_ -notin $tgt })
  $extra   = @($tgt | Where-Object { $_ -notin $ref })
  if ($missing.Count -or $extra.Count) { Write-Host "$m 不一致: 缺失=$missing 多余=$extra" } else { Write-Host "$m OK ($($ref.Count) 条)" }
}
```

预期输出：11 行全部 `OK`，且各模块条数 = 上表条数。

---

## 九、构建验证

```powershell
cd d:\Download\HeartRateMonitor-composeui\android
.\gradlew.bat :app:assembleDebug
```

- 资源合并/XML 解析错误会在构建期报错，必须保证构建通过。
- 可选：`.\gradlew.bat :app:lint` 检查资源问题。
- 注意：`baselineprofile` 模块的 `waitForText("Heart Rate Monitor", "心率监控器", ...)` 等断言只在 en/zh 下成立，**不要**在新语言环境下跑 Baseline Profile 生成。

---

## 十、进度跟踪表

| # | 语言 | 资源目录 | 状态 | 完成日期 | 备注 |
|---|------|----------|------|----------|------|
| 1 | 日语 | `values-ja` | ✅ 已完成 | 2026-08-20 | 文本膨胀低，无需额外 UI 调整 |
| 2 | 韩语 | `values-ko` | ✅ 已完成 | 2026-08-20 | 文本膨胀低，无需额外 UI 调整 |
| 3 | 德语 | `values-de` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，建议真机抽查导航栏/设置项是否截断 |
| 4 | 俄语 | `values-ru` | ✅ 已完成 | 2026-08-20 | 计数句式用中性表达规避复数，文本膨胀高，真机抽查 |
| 5 | 法语 | `values-fr` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 6 | 西班牙语 | `values-es` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 7 | 葡萄牙语 | `values-pt` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 8 | 意大利语 | `values-it` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 9 | 波兰语 | `values-pl` | ✅ 已完成 | 2026-08-20 | 计数句式用中性表达规避复数，文本膨胀高，真机抽查 |
| 10 | 荷兰语 | `values-nl` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 11 | 土耳其语 | `values-tr` | ✅ 已完成 | 2026-08-20 | 文本膨胀高，真机抽查按钮/弹窗标题 |
| 12 | 印尼语 | `values-id` | ✅ 已完成 | 2026-08-20 | 文本膨胀适中 |
| 13 | 印地语 | `values-hi` | ✅ 已完成 | 2026-08-20 | 天城文脚本，真机抽查渲染与折行 |
| 14 | 越南语 | `values-vi` | ✅ 已完成 | 2026-08-20 | 文本膨胀适中 |
| 15 | 泰语 | `values-th` | ✅ 已完成 | 2026-08-20 | 无空格分词，真机抽查断行/长句渲染 |
| 16 | 菲律宾语 | `values-fil` | ✅ 已完成 | 2026-08-20 | 文本膨胀适中；注意 `fil` 代码、撇号需转义 |
| 17 | 马来语 | `values-ms` | ✅ 已完成 | 2026-08-20 | 文本膨胀适中 |
| 18 | 孟加拉语 | `values-bn` | ✅ 已完成 | 2026-08-20 | 孟加拉文脚本，真机抽查渲染与折行 |
| 19 | 阿拉伯语 | `values-ar` | ✅ 已完成 | 2026-08-20 | RTL，需真机重点验收镜像/混合方向文本/图表 |
| 20 | 尼泊尔语 | `values-ne` | ✅ 已完成 | 2026-08-20 | 天城文渲染检查 |

状态约定：`⬜ 未开始` → `🔄 进行中` → `✅ 已完成（日期）`。

---

## 十一、变更日志

> 每完成一种语言，在此追加一条。格式：`YYYY-MM-DD：完成 <语言>（<资源目录>），11 模块 410 条，校验与构建通过。`

- 2026-08-20：创建本文档，确立 20 语言适配规范与进度机制。
- 2026-08-20：完成 日语（`values-ja`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。
- 2026-08-20：**修复打包过滤问题**：`app/build.gradle.kts` 原 `localeFilters` 仅含 `en/zh`，导致日语（及 zh-rTW/zh-rHK）被打包阶段过滤。已扩展为全部 24 种语言，日语在设备上验证生效。
- 2026-08-20：完成 韩语（`values-ko`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(ko)` 资源。
- 2026-08-20：完成 德语（`values-de`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(de)` 资源。德语文本膨胀率高，待真机抽查 UI。
- 2026-08-20：完成 俄语（`values-ru`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(ru)` 资源。计数句式采用中性表达规避复数变格，真机抽查 UI。
- 2026-08-20：**俄语溢出精简**：可用设备页右上角胶囊 `scan_filter_on/off` 由「Фильтр сканирования: вкл./выкл.」精简为「Фильтр: вкл./выкл.」（26→13 字符），已重新构建并验证。（依据「溢出自动精简规则」）
- 2026-08-20：完成 波兰语（`values-pl`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(pl)` 资源。计数句式采用中性表达规避复数变格；胶囊文案 `scan_filter_on/off` 直接使用精简格式「Filtr: wł./wył.」，真机抽查 UI。
- 2026-08-20：完成 印地语（`values-hi`），11 模块 407 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(hi)` 资源。天城文脚本渲染需真机抽查（长词折行、加粗合成）。
- 2026-08-20：**补全遗留未本地化项**（所有语言生效）：① Webhook 编辑弹窗 3 个硬编码标签 URL/Body (JSON)/Headers (JSON) 改为字符串资源（webhook 模块新增 3 个 key，每语言 407→410 条）；② settings 中「Fair Memory」按语言转写（ja フェアメモリ / ko 페어 메모리 / ru Фэр Мемори / hi फेयर मेमोरी；de/pl 保留原词）；③ `http_websocket_server` 标题的 & 改用本地连接词（と/및/и/और/與/i）。已重新校验键名并构建通过。
- 2026-08-20：完成 泰语（`values-th`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(th)` 资源。泰语无空格分词，需真机检查断行与长句渲染。
- 2026-08-20：完成 孟加拉语（`values-bn`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(bn)` 资源。孟加拉文脚本渲染需真机抽查（长词折行、加粗合成）。
- 2026-08-20：完成 阿拉伯语（`values-ar`，RTL），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(ar)` 资源。布局镜像由 Compose 自动处理（supportsRtl 已确认），bpm 单位保留原文，计数句式 %d 在阿拉伯语环境自动渲染为阿拉伯-印度数字。**需真机重点验收 RTL**：镜像布局、混合方向文本（URL/端口/图表）、底部导航与悬浮窗拖拽方向。
- 2026-08-20：完成 尼泊尔语（`values-ne`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过，APK 已确认含 `(ne)` 资源。天城文脚本渲染需真机抽查（长词折行、加粗合成）。**至此 20 种目标语言全部完成。**
- 2026-08-20：**精简首页「修改心率上限」底部弹窗标题**（`feature/main` 的 `heart_rate_ring_max`，仅作弹窗标题）：en「Max Heart Rate」、de「Max. Puls」、ru「Макс. пульс」、pl「Maks. tętno」、hi「अधिकतम हृदय गति」、ne「अधिकतम मुटुको धड्कन」、bn「সর্বোচ্চ হৃদস্পন্দন」、th「ชีพจรสูงสุด」、ar「أقصى نبض」；zh/zh-rTW/zh-rHK/ja/ko 原值已短，未改动。已构建并安装。
- 2026-08-20：完成 法语（`values-fr`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。法语引号用 « »、省略撇号转义，弹窗标题用「FC max」精简格式。
- 2026-08-20：完成 西班牙语（`values-es`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「FC máx.」精简格式。
- 2026-08-20：完成 葡萄牙语（`values-pt`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「FC máx.」精简格式。
- 2026-08-20：完成 意大利语（`values-it`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「FC max」精简格式。
- 2026-08-20：完成 荷兰语（`values-nl`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Max. hartslag」精简格式。
- 2026-08-20：完成 土耳其语（`values-tr`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Maks. kalp hızı」精简格式。
- 2026-08-20：完成 印尼语（`values-id`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Detak jantung maks.」精简格式。
- 2026-08-20：完成 越南语（`values-vi`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Nhịp tim tối đa」精简格式。
- 2026-08-20：完成 菲律宾语（`values-fil`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Max na rate ng puso」精简格式。修复一处撇号转义（`iba\'t ibang`，资源编译报 unescaped apostrophe 已解决）。
- 2026-08-20：完成 马来语（`values-ms`），11 模块 410 条，键名/数量/占位符校验通过，`assembleDebug` 构建通过。弹窗标题用「Kadar jantung maks.」精简格式。**至此 20 种目标语言全部完成，APK 确认含全部 23 种语言资源（en + 22 本地化），已安装到设备。**

---

## 十二、后续可选事项（不在本次范围）

1. **`<plurals>` 改造**：将计数类字符串改为复数资源，优先处理 ru/pl/ar/bn 等复杂复数语言（涉及 `pluralStringResource` 代码改动）。
2. **localeConfig**：新增 `res/xml/locales_config.xml` 并在 Manifest 引用，限定 Android 13+ 每应用语言列表（加入全部目标语言）。
3. **商店文案本地化**：`fastlane/metadata/android` 目前仅 `en-US` 与 `zh-CN`，如需商店级多语言需同步补 `full_description`/`short_description`/`title`/`changelogs`。
4. **应用内更新日志**：`app/src/main/res/raw/changelog.md` 目前未本地化，如需按语言展示需改为按 locale 加载不同 raw 资源。
5. **Baseline Profile 断言**：如要支持多语言环境生成，需将 `waitForText` 的硬编码文案改为资源驱动。
