---
name: Android心率监测项目开发规范
description: Android 心率监测项目开发规范。当新增或修改 Kotlin/Compose 代码、调整分层与模块边界、增删 DataStore 设置项、改动 BLE 心率数据流或落盘链路、处理 WindowInsets 避让与 i18n 数字渲染、或改完需执行 gradlew 验证基线时，必须先加载本 Skill。内含 13 条禁止破坏的架构契约（详情见 docs/contracts/）与验证基线。
---

# 使用流程

1. 确认改动涉及哪一层 → 查阅下方对应契约的详情文件
2. 改动完成 → 判断改动范围：
   - **较大改动或用户要求时**：执行「验证基线」中的全量命令
   - **小改动**（文案/样式/间距/简单逻辑/UI调整）：可只跑 `assembleDebug` 或跳过，由用户决定
3. 涉及 BLE 连接/断开/重连、设置热更新 → 提示用户真机回归

# 项目规则

- 禁止未经用户允许就将代码提交到远程仓库
- 禁止排除 android 目录下的 .key 文件夹（用户要求密钥入库）

# 通用原则

- 优先官方文档 > 官方最佳实践 > 稳定版本文档；与官方指导冲突时以官方为准
- 可维护性优先于开发速度，简洁性优先于不必要的复杂设计
- 禁止编造 API、文档内容、版本信息、性能数据、项目事实；不确定就查官方文档或明确说明
- 保持架构模块化、单一职责、低耦合高内聚、避免循环依赖、集中管理共享功能、不重复已有实现；除非明确要求，否则不改变项目架构
- 默认最小化修改：不改无关文件/代码，不做未经请求的重构
- 新增代码必须与现有项目风格一致，统一为新写法，禁止新旧写法混用
- 优先渐进式改进，而非大规模重写
- 优先使用项目已有技术，没有充分理由不要引入新框架

# 通用代码规范

- **代码风格**：清晰命名、小型函数/类、不可变数据、可读代码、单一职责；避免超长方法、巨大类、重复代码、过深嵌套、魔法数字、不必要全局状态
- **分层职责**：UI层负责渲染与交互，业务层负责逻辑/状态/工作流程协调，数据层负责 Repository/网络请求/数据库/缓存；禁止绕过分层，业务逻辑不得直接访问具体数据源
- **状态管理**：使用单向数据流，状态集中管理，UI由状态驱动，避免维护重复状态（feature 页面强制 MVI，见契约10）
- **注释**：只解释"为什么"（设计原因、业务规则、兼容性、性能/安全考虑），禁止描述代码表面行为；注释尽量精简
- **错误处理**：禁止静默忽略错误；使用统一错误处理方式，明确处理异常
- **并发**：遵循项目现有异步编程方式；永远不要阻塞主线程；避免共享可变状态，确保线程安全
- **配置**：禁止硬编码 URL、版本号、配置参数；版本一律走 `gradle/libs.versions.toml`；适用情况下支持多环境配置
- **技术决策**：多方案需说明优缺点及对项目影响，不将某方案描述为唯一正确方案
- **代码质量**：新增代码应易读、易维护、遵循现有架构、保持向后兼容、具备可测试性
- **重构触发**：仅在存在 Bug、可维护性较差、性能明显受影响或用户明确要求时重构
- **修改说明**：改动公共 API / 核心业务逻辑 / 项目架构时，必须说明原因、收益、可能缺点及是否需同步修改相关组件

# 13 条架构契约（禁止破坏）

改动涉及对应领域时，必须先 Read 详情文件并遵守其中约束。

| # | 契约 | 一句话要点 | 详情文件 |
|---|---|---|---|
| 1 | Room Entity 不得泄漏到 UI/ViewModel | Entity 只在 data 层，UI 用 Domain Model，映射在 Repository | `docs/contracts/01-room-entity.md` |
| 2 | 配置读写统一走 SettingsRepository | 禁止直接用 SharedPreferences/DataStore；键类型化，默认值唯一来源 | `docs/contracts/02-settings-repository.md` |
| 3 | Service 抽象边界 | 数据面走 HeartRateRepository，控制面走 BleConnectionManager/ServiceLauncher | `docs/contracts/03-service-boundary.md` |
| 4 | 组件职责与体量上限 | MainViewModel 只做编排；Composable 文件 ≤350 行，子组件 ≤150 行 | `docs/contracts/04-component-responsibility.md` |
| 5 | 公共工具复用 | Sheet/Flow/设置容器/Webhook 一律用已有工具，禁止重复造轮子 | `docs/contracts/05-common-utils.md` |
| 6 | 敏感机制 | BLE 纪元、心率新鲜度、写后立读、KillStateSaver 同步落盘等不得"顺手简化" | `docs/contracts/06-sensitive-mechanisms.md` |
| 7 | 验证基线 | 较大改动或用户要求时跑 assembleDebug + 串行全量单测；已知 flaky 见详情 | `docs/contracts/07-verification-baseline.md` |
| 8 | 全局单例容器化 | 进程级共享组件一律 Hilt @Singleton；禁止新增全局可变 object/手写单例 | `docs/contracts/08-singleton-container.md` |
| 9 | 模块边界契约 | 多模块依赖方向、namespace、Hilt 注入模式、新代码归属决策表 | `docs/contracts/09-module-boundary.md` |
| 10 | 纯 UDF / MVI 状态 | feature ViewModel 强制 MVI 三元组；UiState 集合用 immutable 类型 | `docs/contracts/10-udf-mvi.md` |
| 11 | WindowInsets 避坑 | Tab 页面 LazyColumn 必须用 contentPadding 避让，禁止 windowInsetsPadding | `docs/contracts/11-window-insets.md` |
| 12 | i18n 数字渲染 | 数字占位符用 %s、格式化显式 Locale.US、图表轴显式 valueFormatter | `docs/contracts/12-i18n-numbers.md` |
| 13 | 心率数据流 SSOT | 实时数据一律经 HeartRateRepository；服务重建必须对账 resetForNewServiceInstance | `docs/contracts/13-heart-rate-ssot.md` |

# 验证基线

**较大改动或用户要求时执行**（详情见契约 7）。小改动（文案/样式/间距/简单逻辑）可跳过，由用户决定。

**较大改动包括但不限于**：
- 改动公共 API / 核心业务逻辑 / 项目架构
- 改动 BLE 心率数据流或落盘链路
- 改动设置存储链路（DataStore / SettingsRepository）
- 新增模块或新增含单测的模块
- 重构（提取组件、移动文件、调整依赖方向）
- 改动契约 6 中的敏感机制

```bash
gradlew :app:assembleDebug
gradlew --max-workers=1 :app:testDebugUnitTest :service:testDebugUnitTest :data:database:testDebugUnitTest :data:settings:testDebugUnitTest :data:repository:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:ui:testDebugUnitTest :core:model:testDebugUnitTest :feature:alarm:testDebugUnitTest :feature:server:testDebugUnitTest :feature:settings:testDebugUnitTest
```

- 单测必须串行（`--max-workers=1`），并行会因多模块共用 DataStore 文件产生环境性假失败
- 当前基线状态与用例分布见 `docs/baseline/current.md`
- 已知 flaky 清单及处置方式见契约 7 详情
