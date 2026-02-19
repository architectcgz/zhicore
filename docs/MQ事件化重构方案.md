# MQ 事件化重构方案

## 背景

原有实现为了避免接口阻塞，常通过 `Task.Run` 异步执行通知推送、邮件发送、统计回写等逻辑。该做法在低并发时可以工作，但扩展性差、失败难追踪，且后台任务与请求生命周期紧耦合，出现以下问题：

- **可靠性不足**：`Task.Run` 受限于进程生命周期，一旦实例重启或线程异常，任务直接丢失。
- **资源竞争**：大量并发请求会创建同等数量后台任务，造成线程调度抖动甚至压垮服务器。
- **难以观测**：缺少统一的事件入口，排查通知/邮件失败只能翻日志。
- **职责混杂**：业务服务既要处理核心逻辑，又要承担补偿、去重、失败重试等基础设施职责。

为解决上述问题，本次重构将关键异步流程全面事件化，统一通过 RabbitMQ 派发，由后台消费者串行处理。

## 调整前架构

```
HTTP 请求 → 业务服务
               ├─ 同步执行业务
               ├─ Task.Run 推送通知
               ├─ Task.Run 发送邮件
               └─ Task.Run 刷新统计 / 记录历史
```

- 缺少集中式队列。
- 异步任务散落在各服务中且无统一容错。
- 防刷操作历史依赖数据库即时写入，接口耗时受影响。

## 新架构概览

```
HTTP 请求 → 业务服务 ──┐
                       │ 发布消息 (RabbitMQ)
                       ▼
                 RabbitMQ 队列
                       ▼
            后台服务消费者（Scoped DbContext）
                       ├─ 通知派发
                       ├─ 邮件发送
                       ├─ 防刷历史入库
                       ├─ 关注统计 / 热度刷新
                       └─ 举报结果同步
```

核心要点：

- 业务服务聚焦事务逻辑与事件入队，失败会写日志但不会阻塞主流程。
- 消费者负责可靠执行：失败时 `BasicNack` 重新入队，支持按需要添加死信/报警。
- 引入统一 `IAppEventPublisher`，避免散落的 `RabbitMqPublisherUtil` 调用。
- 统计与通知能力集中在后台服务内，方便加指标、链路追踪。

## 新增队列清单

| 队列名称                    | 用途                                  |
|----------------------------|---------------------------------------|
| `notification.dispatch`    | 点赞 / 评论 / 关注 / 举报等通知派发   |
| `email.send`               | 所有业务邮件异步发送                  |
| `antispam.action`          | 防刷操作记录入库                      |
| `follow.events`            | 关注/取关增量更新统计 & 热度          |
| `report.processed`         | 举报处理结果及处罚通知                |
| `post.scheduled.publish`   | 定时发布文章                          |
| `post.like.persistence`    | （既有）文章点赞持久化                |
| `comment.like.persistence` | （既有）评论点赞持久化                |

> 配置可在 `appsettings*.json` 的 `RabbitMq` 部分调整。

## 功能改动明细

### 1. 通知派发
- 点赞、评论、回复、关注等入口服务不再直接调用 `INotificationService`，统一发布 `NotificationDispatchMessage`。
- 新增 `NotificationDispatchBackgroundService` 负责读取消息、补齐上下文（文章标题、用户昵称）、调用通知服务，并推送 SignalR。

### 2. 邮件发送
- 找回密码 / 注册验证码 / 管理员触发的密码重置邮件改为发布 `EmailSendMessage`。
- 新增 `EmailSendBackgroundService` 统一使用 `IEmailService` 发送，支持失败重试和日志追踪。

### 3. 防刷历史
- `AntiSpamService.RecordActionAsync` 改为向 `antispam.action` 入队，后台消费者批量写入 `UserActionHistories`。
- 降低接口写库压力，同时可扩展为下游实时风控分析。

### 4. 关注与创作者热度
- 关注/取关发布 `FollowChangedMessage`，在 `FollowEventsBackgroundService` 中串行刷新关注统计及创作者热度，避免多处 `Task.Run` + `CreateScope`。

### 5. 举报与处罚通知
- 管理端处理举报后发布 `ReportProcessedMessage`，`ReportProcessedBackgroundService` 自动通知举报人 & 被处理对象，实现流程解耦。

### 6. 定时发布文章
- `ScheduledPostPublisher` 不再轮询数据库，而是监听 `post.scheduled.publish` 队列，根据 `PublishAt` 延迟执行，彻底移除固定时间 `Task.Delay`。

## 部署与迁移建议

1. **队列创建**：在 RabbitMQ 管理台或基础设施脚本中创建上述新队列，保证持久化与自动恢复。
2. **配置同步**：更新所有环境的 `appsettings*.json` 或环境变量，确保队列名字一致。
3. **灰度上线**：建议先部署消费者，再逐步发布业务服务，避免消息无人消费。
4. **监控告警**：为各新队列配置队列深度监控，必要时增加死信交换机或重试策略。
5. **代码清理**：旧的 `Task.Run` 逻辑已移除，如需特殊处理请统一走事件总线。

## 后续规划

- 将文章统计落库、定时任务等更多基础设施逻辑迁移到事件驱动。
- 对消费者增加指标上报（处理耗时、失败次数），接入 Prometheus/Grafana。
- 在 CI 中添加示例集成测试，校验事件入队与消费链路。

通过本次重构，接口耗时更稳定、失败更可观测，同时为业务扩展（例如统一重试、并发控制、跨站服务扩展）奠定基础。欢迎在实际运行后反馈问题，共同迭代。***
