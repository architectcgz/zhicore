# 全量未读通知与消息推送架构实践

## 背景

通知系统承担了点赞、评论、关注、系统公告、私信、小助手等多源事件的聚合与实时推送。最初依赖“检查点”方案维护未读状态，但随着模型迁移到基于 `is_read` 字段的逐条更新，原有方案暴露出以下问题：

- **一致性差**：检查点表和通知表经常不一致，前端会出现“标记已读但数字不变”的体验。
- **缓存混乱**：Redis 缺少统一失效策略，多个模块自行处理，旧值经常残留。
- **扩展性弱**：新增通知类型必须同步扩展检查点逻辑和缓存代码，成本高且容易遗漏。

我们面向发布场景对统一未读数的架构进行了重构，核心目标是：Redis 只做查询加速，真实数据由数据库承担；所有写操作先精准失效缓存，再按需重建，确保推送与持久层一致。

---

## 架构概览

```
┌──────────────┐         ┌─────────────┐        ┌──────────────┐
│ 事件源服务群 │ ─ MQ ─▶ │ Notification │ ────▶ │ SignalR Hub  │
└──────────────┘         │ Dispatch    │        └──────────────┘
        │                 │ Background  │                 │
        │                 │ Service     │                 │推送 unread & event
        │                 └─────────────┘                 ▼
        ▼                                            前端应用
┌──────────────────────┐
│ 应用服务（HTTP/API） │
│  - NotificationSvc    │
│  - MessageSvc         │
│  - AssistantSvc       │
└─────────┬────────────┘
          │写操作
          ▼
┌──────────────────────┐
│   PostgreSQL / EF    │
│  - Notifications      │
│  - Messages           │
│  - AssistantMessages  │
└─────────┬────────────┘
          │
          │读取 + 缓存回填
          ▼
┌──────────────────────┐
│ Redis (Hash per user)│
│  ZhiCore:unread_count:{u}│
│   ├─ comment          │
│   ├─ mention          │
│   ├─ interaction      │
│   ├─ system           │
│   ├─ chat             │
│   └─ assistant        │
└──────────────────────┘
```

### 关键要素

1. **消息总线（RabbitMQ）**
   - 发布者（点赞、评论、关注等服务）把事件发往 `notification.dispatch` 队列。
   - `NotificationDispatchBackgroundService` 消费后补齐上下文，调用通知服务写库并推送 SignalR。
   - 异步化后，业务入口无需直接关心未读数刷新，统一走通知服务。

2. **统一未读数服务 `IUnreadCountService`**
   - Redis Hash 缓存用户的未读详情和聚合字段。
   - `GetUnifiedUnreadCountAsync(userId, forceRefresh)`：读取缓存，必要时从数据库统计并回填。
   - `InvalidateUnreadCountCacheAsync(userId, params NotificationType[])`：对指定类型或整键失效。
   - `ResetUnreadCountByTypeAsync` / `ResetAllUnreadCountsAsync`：处理标记已读或清零时的缓存同步。

3. **写路径策略**
   - 所有写操作（通知生成/删除、批量已读、私信发送、小助手消息等）统一遵循：
     1. 更新数据库。
     2. 调用 `InvalidateUnreadCountCacheAsync` 精准删除相关字段。
     3. 通过 Hub 推送最新未读数（`forceRefresh: true`）。

4. **读路径策略**
   - API 或 Hub 在查询未读数时优先命中 Redis。
   - 若不存在或设置 `forceRefresh`，则访问数据库统计后写回缓存。
   - 聚合类型（如 `interaction`、`system`）在 Redis 中单字段保存，避免重复统计。

---

## 详细流程

### 1. MQ 推送链路

1. 点赞服务调用 `MQPublisher.PublishAsync(NotificationDispatchMessage)`。
2. `NotificationDispatchBackgroundService` 消费消息：
   - 查询文章/用户信息补全标题、昵称等上下文。
   - 调用 `NotificationService.CreateNotificationAsync`。
3. `NotificationService` 落库后：
   - `InvalidateUnreadCountCacheAsync(userId, notificationType)`。
   - 获取最新未读数（`forceRefresh: true`）并通过 `NotificationHub` 推送。

这样 MQ 系统只负责把事件变成通知，未读数刷新逻辑完全由统一服务处理，避免多处重复代码。

### 2. 通知服务写操作

| 场景                     | 失效类型                                       |
|--------------------------|------------------------------------------------|
| 新增点赞通知             | `Invalidate(unread, NotificationType.Like)`    |
| 标记单条通知已读/未读    | 根据通知类型失效；若无法识别则整键删除        |
| 批量标记已读             | 查询涉及的通知类型集合，逐个失效               |
| 按类别标记已读           | 互动/系统等聚合类型直接失效对应字段            |
| 删除通知                 | 用通知类型失效（找不到类型则整键）             |
| 重置全部未读             | 直接 `KeyDelete`                               |

### 3. 小助手与私信服务

- `AssistantMessageService`：创建、标记已读、全部已读、删除等操作，均调用 `InvalidateUnreadCountCacheAsync(userId, NotificationType.Assistant)`。
- `MessageService`：发送或删除私信后，针对收发双方执行 `InvalidateUnreadCountCacheAsync(..., NotificationType.Chat)`。

### 4. Controller 层

- 删除旧的检查点接口，统一使用 `GetUnifiedUnreadCountAsync(userId, true)` 获取最新值。
- 所有需要返回未读数的 API（单条已读、批量已读、删除操作）在写数据库后立即查询缓存服务并推送 Hub。

---

## Redis 缓存结构示例

```
Key: ZhiCore:unread_count:12345
{
  "comment":         3,
  "mention":         1,
  "interaction":     5,  // like + follow + interaction
  "system":          2,  // system + post published/taken down + report
  "chat":            4,
  "assistant":       1,
  "global":          0
}
```

- 非聚合类型：直接存原始数量。
- 聚合类型：缓存总和，数据库查询时一次性统计，避免多表扫描。
- 如果需精细字段（例如点赞单独显示），可在失效时改为失效单个字段并重建。

---

## 经验与收益

1. **一致性提升**：Redis 只持有可丢弃的副本，不再担心写漏死角导致脏数据。
2. **逻辑集中**：未读数统计、缓存、失效全部聚合到 `UnreadCountServiceV2`，业务模块通过接口使用。
3. **扩展简单**：新增通知类型时，只需更新枚举与聚合配置，不再修改检查点表或多个缓存片段。
4. **MQ 解耦**：事件源只需投递消息，后台服务统一落地与推送，保证顺序与备份。
5. **调试友好**：日志集中，易于定位缓存失效和重建耗时；后续可加入 Prometheus 监控命中率和时延。

---

## 实践建议

1. **严格只在服务层使用 Redis**：防止 Controller/背景任务绕过统一接口直接操作缓存。
2. **写入后立即失效**：避免想着“延迟批量刷新”，容易引入竞态。
3. **推送流程使用 `forceRefresh`**：确保 SignalR 送到前端的是 DB 最新值。
4. **监控关键指标**：例如 `unread_cache_hit`、`unread_rebuild_duration`，便于发现重建热点。
5. **持续演进**：对于高频类型，可后续考虑增量更新，但要确保有幂等逻辑与原子性保障。

---

## 结语

通过明确“数据库是唯一真相、Redis 是缓存副本”的原则，并结合 MQ 消息流和 SignalR 推送，我们构建了一套稳定的未读数架构：既保证用户体验的实时性，又降低维护成本。未来如需扩展新的消息类型或引入多租户，也能在此架构基础上平滑演进。欢迎在实际使用中持续反馈，帮助我们进一步优化这一体系。 
