# Comment 实时回复上插设计（A 正在看评论时，B 新回复自动出现在上方）

## 文档状态

1. 初版设计日期：2026-03-26
2. 本次同步日期：2026-03-27
3. 状态：已按当前实现更新
4. 适用范围：
   - `zhicore-comment`
   - `zhicore-notification`
   - `zhicore-gateway`
   - `zhicore-frontend-vue`

## 目标

在用户 A 正在浏览某篇文章评论区时，如果有用户 B 新增回复：

1. A 在无需手动刷新页面的情况下感知到“有新回复”。
2. 新回复可实时插入到列表上方。
3. 不破坏当前阅读体验，避免列表跳动、重复、乱序。

补充目标：

1. 顶级新评论也纳入实时感知与上插范围。
2. 用户不在顶部时，不直接插入打断阅读，只显示计数提示。
3. 实时能力仅对登录用户开启。

## 已确认决策

1. 顶级新评论和回复都走同一条 comment-stream hint 链路。
2. 顶部且按“最新”排序时，顶级新评论直接上插。
3. 不在顶部时，顶级新评论只累加为页面级提示条：`有 X 条新评论`。
4. 已展开回复区内有新回复时，优先使用局部提示条：`有 X 条新回复`。
5. 未展开的回复区不自动展开，只在“查看更多回复”区域用 `+X` 表达。
6. “最热”排序不直接把最新评论插进当前列表，保留为 pending，用户切回“最新”或主动刷新后再合并。
7. 登录态消失时立即释放订阅并清空实时缓存。

## 当前落地方案

采用“评论流实时 hint + 增量拉取”方案。

### 总体链路

1. `zhicore-comment` 创建评论后，同事务写入 outbox。
2. outbox 投递 `CommentCreatedIntegrationEvent`。
3. `zhicore-notification` 消费事件：
   - 创建站内通知；
   - 广播评论流 hint 到文章维度 topic。
4. 前端评论页在登录态下订阅文章 topic。
5. 收到 hint 后，前端不直接使用 hint 渲染正文，而是调用增量接口拉取完整 `CommentVO`。
6. 前端根据当前阅读位置决定：
   - 立即上插；
   - 进入 pending，展示计数条；
   - 进入某个 root 评论的局部 pending。

### STOMP / SockJS 通道

1. 订阅目的地：`/topic/posts/{postId}/comment-stream`
2. 服务端 WebSocket 入口：`/ws/notification`
3. 网关需要同时支持两类路由：
   - WebSocket Upgrade：`lb:ws://zhicore-notification`
   - SockJS `info/xhr` HTTP 回退：`lb://zhicore-notification`
4. 本地 Vite 代理的 `/ws` target 必须是 `http://localhost:8000`，同时保留 `ws: true`，否则 SockJS `info` 探测会失败。

### Hint 载荷

当前实现使用 `parentId`，没有额外增加 `rootId` 字段：

```json
{
  "eventId": "evt-1",
  "eventType": "COMMENT_CREATED",
  "postId": 2002,
  "commentId": 3033,
  "parentId": 4044,
  "occurredAt": "2026-03-27T10:00:00Z"
}
```

字段语义：

1. `eventId`：全局唯一，用于前端去重。
2. `postId`：文章维度广播 key。
3. `commentId`：用于增量补偿失败时按单条评论兜底拉取。
4. `parentId`：
   - `null` 表示顶级评论；
   - 非 `null` 表示回复，且当前实现里它指向 root 评论 ID。
5. `occurredAt`：事件发生时间，仅用于观测与排查，不作为最终排序依据。

## 查询接口设计

### 顶级评论增量接口

`GET /api/v1/comments/post/{postId}/incremental?afterCreatedAt=...&afterId=...&size=20`

语义：

1. 返回“比游标更新”的顶级评论。
2. 返回顺序：`created_at DESC, id DESC`。
3. 用于主列表顶部插入。

实现注意：

1. 接口游标时间字符串按 UTC 语义解析。
2. 后端在进入 repository 查询前，显式转换到业务时区再构造 `TimeCursor`。
3. 这样可以避免把锚点评论重复拉回。

### 回复增量接口

`GET /api/v1/comments/{commentId}/replies/incremental?afterCreatedAt=...&afterId=...&size=20`

语义：

1. 返回某个 root 评论下“比游标更新”的回复。
2. 返回顺序：`created_at DESC, id DESC`。
3. 用于回复区顶部插入。

### 兜底拉取

如果当前列表还没有游标，或者增量接口没有返回目标评论，前端会退化为按 `commentId` 拉单条详情，再判断是否应并入当前列表。

## 前端插入策略

### 顶级评论

1. 当前为登录态，且订阅已建立。
2. 收到顶级评论 hint 后：
   - 若当前排序是 `latest`，且评论区顶部锚点仍接近视口顶部，直接上插并高亮约 1.8 秒；
   - 否则先放入 pending，页面显示 `有 X 条新评论`。
3. 用户点击提示条后：
   - 将 pending 顶级评论合并进顶部；
   - 平滑滚动到评论区顶部锚点。
4. 当前排序是 `hot` 时，不强插新评论，保留为 pending。

### 回复

1. 已展开且当前 root 下已有可见回复：
   - 新回复先进入该 root 的 pending；
   - 组件顶部显示 `有 X 条新回复`；
   - 点击后合并并高亮。
2. 未展开回复区：
   - 不自动展开；
   - “查看更多回复”文案携带 `+X`。
3. 其他 root 的新回复不会升级成全局提示条。

### 重连与页面可见性

1. STOMP 客户端断线后自动重连。
2. 页面从 hidden 回到 visible 时，前端会重新同步订阅。
3. 重建订阅后执行一次增量补偿拉取：
   - 顶级评论按当前游标补拉；
   - 已有可见回复的 root 按各自游标补拉。

## 去重与排序

1. 前端按 `eventId` 去重，避免至少一次投递带来的重复处理。
2. 评论合并按 `createdAt DESC, id DESC` 稳定排序。
3. 渲染正文永远以评论查询接口返回结果为准，不直接相信 hint 载荷。

## 后端实现对齐

### `zhicore-comment`

1. 已提供两个增量接口：
   - `/api/v1/comments/post/{postId}/incremental`
   - `/api/v1/comments/{commentId}/replies/incremental`
2. 已修复顶级评论增量游标的 UTC -> 业务时区转换问题。
3. outbox 更新 SQL 不再依赖不存在的 `updated_at` 列。
4. outbox 最近失败/死信统计改为基于 `COALESCE(next_attempt_at, created_at)` 统计。

### `zhicore-notification`

1. `CommentCreatedNotificationConsumer` 会同时：
   - 广播 comment-stream hint；
   - 继续处理文章作者/被回复用户通知。
2. 聚合通知组查询已统一使用数值型 `targetId`，避免 PostgreSQL 触发 `bigint = character varying`。

### `zhicore-gateway`

1. `/ws/message/**` 和 `/ws/notification/**` 已拆分为：
   - WebSocket Upgrade 路由；
   - SockJS HTTP 回退路由。
2. 这样 `.../info`、`.../xhr` 等 SockJS 请求不再 404。

## 能力边界

### In Scope

1. 评论页实时感知新顶级评论与新回复。
2. 基于 hint 的增量补拉与上插。
3. 登录态专属实时能力。
4. 页面可见性恢复和重连后的补偿。

### Out of Scope

1. 评论正文通过 WebSocket 直推。
2. 未登录用户实时能力。
3. 通知中心 UI 改版。
4. 多实例 WebSocket 广播一致性彻底治理。

## 风险与限制

1. 当前 notification 使用 simple broker，多实例部署下跨实例 topic 广播仍有一致性风险。
2. hint 到达不保证严格有序，客户端必须继续依赖增量接口兜底。
3. 页面正常登录初始化目前仍受 `/api/v1/auth/me` 异常影响，联调阶段通过已有 token 绕过，不属于本设计范围内修复项。
4. 回复区的“是否直接上插”当前主要依据是否已展开和是否已有可见回复，不做更细粒度的阅读锚点补偿。

## 联调结论（2026-03-27）

已验证通过：

1. 顶部场景下，新顶级评论可直接上插。
2. 非顶部场景下，页面出现 `有 1 条新评论`，点击后合并进入列表。
3. comment outbox 新纪录状态可落为 `SUCCEEDED`。
4. `notificationRealtimeClient.isConnected()` 为 `true`。
5. `zhicore-notification` 聚合查询不再出现 `operator does not exist: bigint = character varying`。
6. gateway 与前端本地代理的 SockJS `info` 探测已打通。

## 验收标准

1. A 打开文章评论页，B 新增顶级评论后，A 端 1 至 2 秒内可感知并在合适场景上插。
2. A 展开某条 root 评论回复后，B 新增回复时，A 端可看到局部新回复提示或直接插入结果。
3. 用户不在顶部时，不直接打断当前阅读位置。
4. 发生断线或页面恢复可见后，可以通过增量接口补齐缺失评论。
5. 不出现重复、明显乱序、列表大幅跳动。

## 后续建议

1. 为 comment-stream 增加端到端时延和丢弃率指标。
2. 评估 simple broker 替换为可横向扩展的 broker relay。
3. 如果后续需要更强的回复区体验，再补“局部阅读锚点保持”。
