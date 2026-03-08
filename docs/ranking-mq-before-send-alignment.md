# Ranking MQ 发送前是否已写入 Inbox 对齐记录

## 问题

当前需要确认两件事：

1. 在事件发送到 MQ 之前，系统是否已经写入了 `inbox`。
2. 对 `ranking` 方案 B 的实现，哪些链路已经具备“先持久化、再发 MQ”的基础，哪些没有。

## 结论

### 1. `content -> MQ -> ranking` 这条链路，发 MQ 之前没有写 `inbox`

这里写入的是 `outbox_event`，不是 `inbox`。

- `content` 服务通过 `OutboxEventPublisher` 在业务事务内写入 PostgreSQL 的 `outbox_event` 表。
- 后台 `OutboxEventDispatcher` 再异步扫描 `outbox_event`，调用 `RocketMQTemplate.syncSend(...)` 把消息投递到 MQ。

也就是说，这条链路当前是：

`业务事务 -> outbox_event -> RocketMQ -> ranking consumer`

不是：

`业务事务 -> inbox -> RocketMQ`

对应代码：

- `zhicore-content/src/main/java/com/zhicore/content/infrastructure/messaging/OutboxEventPublisher.java`
- `zhicore-content/src/main/java/com/zhicore/content/infrastructure/messaging/OutboxEventDispatcher.java`
- `zhicore-content/src/main/java/com/zhicore/content/infrastructure/persistence/pg/entity/OutboxEventEntity.java`

### 2. `comment -> MQ -> ranking` 这条链路更弱，发 MQ 前既没有写 `inbox`，也没有写 `outbox`

`comment` 服务当前是直接调用 `DomainEventPublisher.publish(...)` 发 RocketMQ。

- `CommentEventPublisher` 直接委托 `DomainEventPublisher`
- `DomainEventPublisher` 内部调用 `rocketMQTemplate.asyncSend(...)`

这意味着评论事件当前是“直接发 MQ”，没有经过 `outbox`，更没有 `inbox`。

对应代码：

- `zhicore-comment/src/main/java/com/zhicore/comment/infrastructure/mq/CommentEventPublisher.java`
- `zhicore-common/src/main/java/com/zhicore/common/mq/DomainEventPublisher.java`
- `zhicore-comment/src/main/java/com/zhicore/comment/application/service/CommentApplicationService.java`

### 3. `ranking` 侧的 `inbox` 在最初对齐时还只是模型草稿

最初对齐时，这个 worktree 里已经新增了：

- `ranking_event_inbox`
- `ranking_post_hot_state`
- `RankingInboxProperties`
- `RankingSnapshotProperties`

当时 `ranking` 的 MQ consumers 仍然是：

`消费 MQ -> 本地内存缓冲 -> 5 秒 flush -> Redis`

还没有改成：

`消费 MQ -> durable inbox -> 聚合 -> authority state -> snapshot -> Redis`

当时对应代码：

- `zhicore-ranking/src/main/java/com/zhicore/ranking/infrastructure/mq/BaseRankingConsumer.java`
- `zhicore-ranking/src/main/java/com/zhicore/ranking/application/service/ScoreBufferService.java`
- `zhicore-ranking/src/main/java/com/zhicore/ranking/infrastructure/scheduler/BufferFlushScheduler.java`
- `zhicore-ranking/src/main/java/com/zhicore/ranking/infrastructure/mongodb/RankingEventInbox.java`

### 4. 当前状态（本 worktree 最新实现）

当前 `ranking` 消费链路已经切换为：

`消费 MQ -> ranking_event_inbox -> 聚合 -> ranking_post_hot_state -> snapshot -> Redis`

也就是说：

- `ranking` 已经不再依赖 JVM 内存缓冲做热度持久化。
- `ScoreBufferService` / `BufferFlushScheduler` 已从当前实现中移除。
- 消费侧的业务可靠处理边界已经前移到 `ranking_event_inbox`。

## 当前对齐后的判断

### 已具备“先持久化再发 MQ”基础的链路

只有 `content` 的集成事件链路，而且持久化的是 `outbox`，不是 `inbox`。

这意味着：

- 对 `post like/favorite` 这类事件，生产侧可靠性基础已经不错。
- `ranking` 这边需要补的是消费者侧的 durable inbox，而不是再去改造 `content` 的发送前 `inbox`。

### 还不具备该基础的链路

`comment` 链路目前没有 `outbox`。

这意味着：

- 评论事件如果需要达到和 `content` 集成事件同等级别的可靠性，后续应该补 `comment` 自己的 `outbox`，或者统一迁到集成事件 + outbox 模式。
- 仅仅给 `ranking` 增加 consumer-side inbox，可以解决“consumer 宕机丢 5 秒内内存缓冲”的问题，但不能修复“producer 直接发 MQ”这一侧的可靠性短板。

## 对方案 B 实现的直接影响

1. `ranking` 的 durable inbox 是消费者侧持久化落点，不是生产侧发送前落点。
2. `content` 的 post 互动事件继续沿用 `outbox -> MQ -> ranking inbox` 的方向。
3. `comment` 事件如果继续直接发 MQ，那么即使 `ranking` 已做 inbox，也仍然只能保证“消息到达 ranking 后”的处理可靠，不能把 producer 侧提升到 outbox 级别。

## 后续实现约束

为了避免后续再次对齐这部分，实施时默认遵守以下约束：

1. `post` 互动链路按 `content outbox -> MQ -> ranking inbox` 实现。
2. `ranking` consumer 只有在 `ranking_event_inbox` 持久化成功后，才算消息进入业务侧可靠处理范围。
3. `comment` 链路暂时视为“producer 直发 MQ”，如果本轮要纳入同等级可靠性范围，需要单独补 producer outbox。

## 一句话结论

当前代码里，发 MQ 之前没有写 `inbox`。

- `content` 写的是 `outbox_event`
- `comment` 是直接发 MQ
- `ranking` 的 `inbox` 是消费侧落点，现已真正接到消费链路里
