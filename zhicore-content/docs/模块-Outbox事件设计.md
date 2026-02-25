# 模块设计：Outbox 事件投递（接手版）

## 1. 模块目的
Outbox 模块解决的问题是：
- 业务事务已提交，但 MQ 发送失败导致下游感知不到变更。
- 多实例部署下，避免重复扫描与重复投递。

它提供“本地事务写事件 + 后台可靠投递”的标准路径。

## 2. 关键类与职责
### 2.1 写入侧
- `OutboxEventPublisher`
  - 实现 `IntegrationEventPublisher`
  - 在业务事务中把事件序列化并写入 `outbox_event`
  - 初始状态为 `PENDING`

### 2.2 投递侧
- `OutboxEventDispatcher`
  - 定时扫描 `PENDING` 事件
  - 通过分布式锁保证同一时刻仅一个实例执行扫描投递
  - 投递成功后标记 `DISPATCHED`
  - 失败重试，超过阈值标记 `FAILED`

### 2.3 数据访问
- `OutboxEventMapper`
  - 查询、更新 Outbox 记录

## 3. 表结构与字段语义（`outbox_event`）
- `event_id`：业务事件唯一 ID（幂等关键字段）
- `event_type`：事件 Java 类型全名（当前反序列化依赖）
- `aggregate_id` / `aggregate_version`：聚合标识与版本
- `payload`：JSON 事件体
- `status`：`PENDING` / `DISPATCHED` / `FAILED`
- `retry_count`：重试次数
- `last_error`：最近错误信息
- `occurred_at` / `created_at` / `dispatched_at`：时间线字段

## 4. 完整时序
### 4.1 业务线程
1. 应用服务完成业务逻辑（例如创建/发布文章）。
2. 调用 `integrationEventPublisher.publish(event)`。
3. `OutboxEventPublisher` 序列化事件并 `insert outbox_event(PENDING)`。
4. 若序列化或插入失败，抛异常回滚业务事务。

### 4.2 调度线程
1. 按 `scanInterval` 触发 `dispatch()`。
2. 尝试 `tryLockWithWatchdog(lockKey)`。
3. 拉取 `PENDING`（按创建时间升序，受 `batchSize` 限制）。
4. 对每条记录：
   - 反序列化 payload
   - 组装 RocketMQ 消息头
   - `syncSend`（支持延迟级别）
   - 更新为 `DISPATCHED`
5. 若失败：`retry_count+1`，超限则 `FAILED`。
6. 释放分布式锁。

## 5. 配置项与运行参数
- `OutboxProperties.scanInterval`：扫描间隔
- `OutboxProperties.batchSize`：每批处理条数
- `OutboxProperties.maxRetry`：最大重试次数
- `RocketMqProperties.postEvents`：Topic 前缀

建议基线（可按容量调优）：
- 扫描间隔：2s ~ 5s
- 批大小：100 ~ 500
- 最大重试：3 ~ 8

## 6. 一致性、幂等与顺序性
### 6.1 一致性
- 业务数据和 outbox 写入同事务提交，保障“要么都成功，要么都失败”。

### 6.2 幂等
- 上游：`event_id` 唯一。
- 下游：结合 `consumed_events` 防重复消费。

### 6.3 顺序性
- 当前保证“按创建时间扫描”；严格全局顺序不可保证。
- 同一聚合的顺序依赖 `aggregate_version` 与消费端幂等/去重设计。

## 7. 异常分支与恢复策略
### 7.1 序列化失败
- 表现：业务事务失败回滚。
- 检查：是否事件字段不可序列化、ObjectMapper 配置变更。

### 7.2 MQ 暂时故障
- 表现：`retry_count` 上升，仍为 `PENDING`。
- 恢复：MQ 恢复后自动继续投递。

### 7.3 超过最大重试
- 表现：状态 `FAILED`。
- 当前缺口：告警发送逻辑仍是 TODO。
- 建议：上线前必须接通告警渠道（Webhook/短信/企业IM）。

### 7.4 分布式锁获取失败
- 表现：当前实例跳过本轮调度。
- 说明：正常现象，代表其他实例在执行。

## 8. 观测与排障 Runbook
### 8.1 SQL 快速排查
```sql
-- 按状态看积压
select status, count(*) from outbox_event group by status;

-- 查看最近失败
select event_id, event_type, retry_count, last_error, updated_at
from outbox_event
where status = 'FAILED'
order by updated_at desc
limit 50;

-- 查看长时间未投递的 pending
select event_id, event_type, created_at, retry_count
from outbox_event
where status = 'PENDING'
  and created_at < now() - interval '10 minutes'
order by created_at asc;
```

### 8.2 日志关键字
- `Integration event saved to outbox`
- `Outbox event dispatched`
- `Outbox event dispatch failed`

### 8.3 排障步骤
1. 先看 `PENDING/FAILED` 数量趋势。
2. 若 `FAILED` 增长，核对 `last_error` 分类（反序列化/MQ/网络）。
3. 检查锁日志，确认是否有实例在执行调度。
4. 验证下游消费是否幂等，避免重复副作用。

## 9. 扩展与改造建议
- 建议新增“事件类型注册表”，替代类名反射，降低重构风险。
- `FAILED` 增加人工重放接口（按 event_id / 时间窗）。
- 增加 dead-letter topic 方案，隔离长期失败事件。
- 补全告警（当前代码存在 TODO）。

## 10. 相关代码锚点
- `src/main/java/com/zhicore/content/infrastructure/messaging/OutboxEventPublisher.java`
- `src/main/java/com/zhicore/content/infrastructure/messaging/OutboxEventDispatcher.java`
- `src/main/resources/db/schema.sql`（`outbox_event`）
