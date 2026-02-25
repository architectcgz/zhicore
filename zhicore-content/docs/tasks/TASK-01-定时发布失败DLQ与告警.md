# TASK-01: 定时发布失败后投递 DLQ 与告警

## 背景

`PostApplicationService.java:752` 中，定时发布重试耗尽后仅记录日志，未投递 DLQ 也未触发告警。
失败事件不可观测，运维无法及时介入。

## 涉及文件

- `com.zhicore.content.application.service.PostApplicationService` — 第 752 行 TODO
- `com.zhicore.content.infrastructure.alert.AlertService` — 已有告警基础设施
- `com.zhicore.content.infrastructure.alert.AlertType` — 已有 `OUTBOX_DISPATCH_FAILED` 类型

## 实现要求

### 1. 触发告警

在 `PostApplicationService` 第 752 行处，调用 `AlertService` 发送告警：

```java
// 替换 TODO 注释
alertService.alert(Alert.builder()
    .type(AlertType.OUTBOX_DISPATCH_FAILED)
    .level(AlertLevel.HIGH)
    .message("定时发布失败且达到重试上限: postId=" + record.getPostId())
    .detail("lastError", record.getLastError())
    .detail("retryCount", String.valueOf(record.getRetryCount()))
    .build());
```

### 2. 投递 DLQ（死信队列）

方案选择：
- **推荐方案**：复用 Outbox 表，写入一条 `eventType=SCHEDULED_PUBLISH_DLQ` 的事件，由 `OutboxEventDispatcher` 统一投递到 RocketMQ 的 DLQ topic。
- 备选方案：直接发送 RocketMQ 消息到 `%DLQ%` topic（耦合度高，不推荐）。

具体步骤：
1. 在 `OutboxEventEntity` 或事件类型枚举中新增 `SCHEDULED_PUBLISH_DLQ` 类型
2. 在失败处理逻辑中，构造 DLQ 事件并写入 Outbox 表
3. DLQ 事件 payload 包含：postId、scheduledTime、failReason、retryCount

## 验收标准

- [ ] 定时发布重试耗尽后，`AlertService` 收到 HIGH 级别告警
- [ ] DLQ 事件写入 Outbox 表，eventType 为 `SCHEDULED_PUBLISH_DLQ`
- [ ] 日志中保留原有 error 日志（不删除）
- [ ] 单元测试覆盖：模拟重试耗尽场景，验证告警和 DLQ 事件均被触发

## 回滚策略

- 告警和 DLQ 均为新增逻辑，回滚只需 revert commit
- 不涉及表结构变更
