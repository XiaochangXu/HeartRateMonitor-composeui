# 契约 12：i18n 数字渲染规范（禁止违反）

## 问题：小语种下数字输出本地数字系统，导致 URL 失效与数字异常显示

### 症状

应用语言切到尼泊尔语（ne）/孟加拉语（bn）/阿拉伯语（ar）等使用本地数字系统的语言时：
- OBS URL / WebSocket 端口（`http://x:8000/`）被渲染为 `http://x:८०००/`（天城文），外部工具无法解析
- 通知文案、统计卡 BPM、时间（`14:30`→`१४:३०`）、图表刻度（`80`→`८०`）、小数（`12.5`→`१२.५`，阿拉伯语下小数点变 `٫`）全部显示本地数字

### 根因

Android 的 locale 敏感格式化在这些语言下使用本地数字系统（天城文 ०-९ 等）渲染数字，共四个层面（2026-08 三轮审查才找全，务必逐一对照）：

1. **资源层**：`getString(R.string.xxx, intValue)` + `%N$d` 占位符（`String.format` 语义，按当前 Locale 格式化整数）
2. **Kotlin 层**：未传 Locale 的 `String.format("%.1f", v)`、`"%.1f".format(v)`、`%02d`
3. **日期层**：`SimpleDateFormat(pattern, Locale.getDefault())`——pattern 中的数字部分同样走本地数字系统
4. **三方库层**：Vico 图表轴未显式传 `valueFormatter` 时，默认 `DecimalValueFormatter` 内部走默认 locale 渲染刻度

## 正确做法（本项目强制，2026-08 已全量整改 159 个文件）

1. **字符串资源**：数字占位符一律 `%N$s`（禁止 `%N$d`/`%N$f`），代码侧传 `int.toString()`——Kotlin `Int.toString()` 恒输出 ASCII 十进制，不受 Locale 影响
2. **代码内直接格式化**：`String.format(Locale.US, "%.1f", v)`，禁止省略 Locale 参数
3. **时间格式化**：`SimpleDateFormat(pattern, Locale.US)`（纯数字 pattern 在任何 locale 下格式相同，唯一差异就是数字系统，改后仅数字恒 ASCII）
4. **图表轴标签**：必须显式传 `CartesianValueFormatter { _, value, _ -> value.toInt().toString() }`（参考 `RealtimeChart.kt` 与 `ChartComponents.kt` 既有写法）
5. **对外协议数据**（webhook JSON、上报载荷）：`String.format(Locale.US, ...)`（`WebhookRepository` 既有做法，2026-08 之前即正确）

## 新增代码检查清单

- 新增含数字的 `strings.xml` 条目 → 占位符用 `%N$s`，禁止 `%N$d`
- 新增 `stringResource` / `getString` 带数字参数 → 传 `.toString()`
- 新增 `String.format(` / `.format(` / `SimpleDateFormat` → 显式 `Locale.US`
- 新增图表轴 → 显式 valueFormatter，禁止依赖库默认值
- 修改翻译文件占位符后 → 校验各语言占位符编号/数量与默认 `values` 一致（不一致会在对应语言下运行时抛 `MissingFormatArgumentException`）

## 补充：RTL 语言下纯 URL 文本 bidi 重排问题

阿拉伯语（ar）等 RTL 语言下，纯 URL 文本（如 `http://192.168.1.1:8000/`）末尾的 `/` 会被 bidi 算法移到开头，显示为 `/http://192.168.1.1:8000`。

**修复方式**：对纯 URL 的 `Text` 组件添加 `textDirection = TextDirection.Ltr`。

注意：仅对纯 URL 文本适用，含本地化前缀的混合文本（如 `عنوان الوصول: http://...`）不需要，bidi 算法能正确处理混合文本中的 LTR 段。
