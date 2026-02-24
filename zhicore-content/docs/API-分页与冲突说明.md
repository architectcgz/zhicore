# API：分页规则与并发冲突说明（T23）

## 1. 文章列表：`GET /api/v1/posts`

### 参数

- `sort`：`LATEST`（默认）或 `POPULAR`
- `status`：`PUBLISHED`（默认语义）或 `ALL`（占位，用于 key 维度与兼容扩展）
- `size`：1~100（默认 20）
- `cursor`：仅 `sort=LATEST` 支持
- `page`：仅 `sort=POPULAR` 支持（1-based）

### 组合约束（非法返回 400）

- `sort=LATEST`：禁止传 `page`，必须使用 `cursor`（可为空，表示从第一页开始）
- `sort=POPULAR`：禁止传 `cursor`，必须使用 `page`

### Cursor 编码

- 复合游标：`Base64Url(publishedAt|postId)`
- `publishedAt` 使用 `ISO_LOCAL_DATE_TIME`
- `postId` 为 Long

示例（LATEST）：
- 首次：`GET /api/v1/posts?sort=LATEST&size=20`
- 下一页：`GET /api/v1/posts?sort=LATEST&size=20&cursor={nextCursor}`

示例（POPULAR）：
- `GET /api/v1/posts?sort=POPULAR&page=1&size=20`

## 2. 并发冲突（HTTP 409）

当出现乐观锁冲突/并发写冲突时，接口返回 HTTP 409，并在响应体 `data` 中补充：

- `error_code`：场景化错误码
- `retry_suggested`：是否建议客户端重试

常见错误码：
- `CONCURRENT_UPDATE_CONFLICT`：通用并发更新冲突
- `CONCURRENT_TAG_UPDATE`：标签更新并发冲突（建议重试）

## 3. Outbox 管理端接口

### 3.1 查询失败事件

- `GET /api/v1/admin/outbox/failed?page=1&size=20&eventType={可选}`

返回：失败事件分页列表（包含 `eventId/eventType/aggregateId/retryCount/lastError/...`）。

### 3.2 手动重试

- `POST /api/v1/admin/outbox/{eventId}/retry`
- Body：`{ "reason": "..." }`

约束：
- 仅允许对 `FAILED` 状态事件进行重试
- 同一事件 10 分钟内最多重试一次（超过返回 429）
- 重试会记录审计：`operator_id/reason/retried_at/result`

