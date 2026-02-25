# TASK-01 ~ TASK-05 架构一致性审查

> 审查范围：TASK-01 ~ TASK-05 的代码实现
> 审查日期：2026-02-25
> 审查视角：架构一致性、安全性、可观测性、并发/幂等风险

---

## TASK-01: 定时发布失败 DLQ 与告警

### 问题 1 — 已修复｜`Instant.now()` 重复调用

**位置**：`PostApplicationService.java:813-816`

```java
Instant now = Instant.now();
entity.setOccurredAt(now);
entity.setCreatedAt(now);
```

`occurredAt` 和 `createdAt` 需要语义一致，已统一使用同一个 `now` 变量。

**结论**：无需修改。

### 问题 2 — 低｜DLQ topic 命名使用 `%DLQ%` 前缀

**位置**：`OutboxEventDispatcher.java:188`

```java
private static final String ROCKETMQ_DLQ_PREFIX = "%DLQ%";
```

RocketMQ 的 `%DLQ%` 前缀是 Broker 自动生成死信队列的保留命名。业务侧主动投递到 `%DLQ%` 前缀的 topic 虽然技术上可行，但可能与 Broker 自动生成的死信队列混淆。

**建议**：考虑使用自定义前缀如 `BIZ_DLQ_` 或 `content-dlq-`，与 Broker 原生 DLQ 区分。如果团队已有约定则忽略。

### 问题 3 — 低｜告警与 DLQ 写入的事务边界

**位置**：`PostApplicationService.java:765-780`

告警和 DLQ 事件写入分别用独立 try-catch 包裹，这是正确的。但 `emitScheduledPublishDlqEvent` 内部调用了 `outboxEventMapper.insert(entity)`，此时外层方法的 `@Transactional` 已经在 catch 块中（异常已被捕获），需确认事务传播行为是否符合预期——DLQ 写入应该在独立事务中，避免与主事务回滚绑定。

**建议**：确认 `emitScheduledPublishDlqEvent` 是否需要 `@Transactional(propagation = REQUIRES_NEW)`。

---

## TASK-02: 删除文章清理正文图片

### 问题 4 — 中｜异步清理在 `@Transactional` 提交前触发

**位置**：`PostApplicationService.java:858`

`deletePost` 方法标注了 `@Transactional`，在事务提交前就调用了 `cleanupContentImagesAsync(postId)`。如果异步线程执行很快，可能在事务提交前就去 MongoDB 查内容，此时如果软删除逻辑同时清理了 MongoDB 内容，可能查不到。

**影响**：当前实现中软删除不清理 MongoDB 内容，所以实际不会触发问题。但语义上异步任务应在事务提交后触发更安全。

**建议**：考虑使用 `TransactionSynchronizationManager.registerSynchronization(afterCommit)` 或 `@TransactionalEventListener` 确保事务提交后再触发异步清理。当前可作为后续优化项。

### 问题 5 — 已修复｜`isSelfStorageImageUrl` 误判外链风险

**位置**：`PostContentImageCleanupService.java:152`

```java
// 绝对 URL 必须命中域名白名单，避免仅凭路径误判外链为“自有存储”
return allowedHosts != null && allowedHosts.contains(lowerHost);
```

已改为“域名白名单 +（相对路径使用明确前缀）”策略：绝对 URL 需要命中 `file-service` 域名白名单，相对路径仅允许 `/api/v1/upload/` 或 `/api/v1/files/` 前缀，避免误删外链或业务自定义路径。

**结论**：无需修改。

### 问题 6 — 低｜`contentType` 参数未实际使用

**位置**：`ContentImageExtractor.java:41`

```java
String ignored = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
```

变量名 `ignored` 已经说明了意图，但保留一个未使用的参数会让调用方产生误解。

**建议**：注释中已说明"保留以便未来做更精细的分支处理"，可以接受。但建议在方法 Javadoc 中明确标注 `contentType` 当前未使用。

---

## TASK-03: 物理删除管理员权限检查

### 问题 7 — 中｜审计日志仅用 `log.warn`，缺少结构化审计记录

**位置**：`PurgePostHandler.java:57-62`

```java
log.warn("AUDIT PurgePost: operatorId={}, postId={}, postDeleted={}, isAdmin={}",
        command.getUserId(), command.getPostId(), post.isDeleted(), UserContext.isAdmin());
```

物理删除是高危操作，仅靠日志审计存在以下风险：
- 日志可能被轮转清理，审计记录丢失
- 日志格式不结构化，难以做合规查询

**建议**：参考项目中已有的 `OutboxRetryAuditEntity`，为物理删除新增独立审计表或复用统一审计机制。当前阶段日志审计可接受，但建议作为后续改进项跟踪。

### 问题 8 — 低｜`UserContext.isAdmin()` 依赖 ThreadLocal，异步场景需注意

**位置**：`UserContext.java:59-65`

`isAdmin()` 从 `ThreadLocal` 读取角色信息。当前 `PurgePostHandler` 在同步请求链路中调用，没有问题。但如果未来有异步调用路径（如消息消费、定时任务触发物理清理），`ThreadLocal` 中不会有用户上下文，`isAdmin()` 会返回 `false`。

**建议**：当前实现正确。如果后续有异步清理场景，需通过 Command 对象显式传递角色信息，而非依赖 `UserContext`。

---

## TASK-04: StorageMonitor 存储空间检查

### 问题 9 — 中｜`alertStorageSizeExceeded` 缺少去重/限流

**位置**：`StorageMonitor.java:78-79`、`StorageMonitor.java:99-105`

每小时执行一次检查，如果存储持续超阈值，每小时都会触发告警。在存储空间不可能短时间内大幅变化的场景下，这会产生告警疲劳。

**建议**：确认 `AlertService.alertStorageSizeExceeded` 内部是否已有去重窗口（类似 Outbox 告警的 `shouldSendAlert` 机制）。如果没有，建议增加告警去重，例如同一数据源 24 小时内只告警一次。

### 问题 10 — 低｜MongoDB `dbStats` 在分片集群下语义不同

**位置**：`StorageMonitor.java:87`

```java
Document stats = mongoTemplate.getDb().runCommand(new Document("dbStats", 1));
```

在单机/副本集模式下 `dbStats` 返回的 `dataSize`/`storageSize` 是准确的。但在分片集群下，`dbStats` 返回的是所有分片的聚合值，且 `storageSize` 可能包含预分配空间，与实际磁盘占用有偏差。

**建议**：当前阶段可接受。如果后续迁移到分片集群，需要改用 `collStats` 按集合粒度监控，或通过运维侧 Prometheus exporter 采集更准确的磁盘指标。

---

## TASK-05: 分布式锁释放失败监控指标

### 无架构偏离

实现简洁正确：
- 通过构造函数注入 `MeterRegistry`，符合项目依赖注入风格
- Counter 命名 `content.lock.release.failure` 带 `component` tag，与项目其他指标风格一致
- 异常处理中递增计数器，不影响原有日志逻辑
- 测试使用 `SimpleMeterRegistry` mock，验证充分

无需修改。

---

## 总结

| 问题 | 级别 | 任务 | 需立即修复 |
|------|------|------|-----------|
| 1. `Instant.now()` 重复调用 | 已修复 | TASK-01 | 否 |
| 2. DLQ topic `%DLQ%` 前缀混淆 | 低 | TASK-01 | 否 |
| 3. DLQ 写入事务边界 | 低 | TASK-01 | 确认即可 |
| 4. 异步清理在事务提交前触发 | 中 | TASK-02 | 后续优化 |
| 5. `isSelfStorageImageUrl` 误判外链风险 | 已修复 | TASK-02 | 否 |
| 6. `contentType` 参数未使用 | 低 | TASK-02 | 否 |
| 7. 审计日志缺少结构化记录 | 中 | TASK-03 | 后续优化 |
| 8. `UserContext` ThreadLocal 异步风险 | 低 | TASK-03 | 否 |
| 9. 存储告警缺少去重/限流 | 中 | TASK-04 | 确认即可 |
| 10. MongoDB `dbStats` 分片语义 | 低 | TASK-04 | 否 |

建议优先确认：问题 3（事务边界）、问题 9（告警去重窗口是否满足预期）。
其余为低风险或后续优化项，不阻塞上线。
