# 契约 1：Room Entity 不得泄漏到 UI/ViewModel 层

违反即视为破坏架构。

## 约束

- `data/db/` 下的 Entity（HeartRateSession / HeartRateRecord / FavoriteDeviceEntity 等）只允许出现在 `data/db`、`data/repository`、`:service` 的 `FlushRecordsWorker`（Worker 排队落盘路径，2026-09 落盘缓冲迁入 `:data:repository` 后 `:service` 仅此一处）与 DAO 测试中。
- UI/ViewModel 一律使用 `data/model/` 的 Domain Model（HeartRateSessionInfo / HeartRateRecordInfo / FavoriteDeviceInfo / SessionStatsInfo），映射函数 `toInfo()` / `toEntity()` 在 Repository 层完成。

## 新增表/字段流程

先加 Entity，再在 `data/model/` 补对应 Info 类与映射，Repository 对外只返回 Info 类型。
