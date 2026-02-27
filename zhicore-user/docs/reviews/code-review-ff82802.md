# 代码 Review：3d19f23..ff82802 zhicore-user 业务流程集成 Outbox/分布式锁/Refresh Token/重试增强

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit 3d19f23..ff82802 涉及的 24 个文件（497+/114-） |
| 审查基准 | docs/architecture/04-事件驱动架构.md、06-可靠性与一致性设计.md |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

## 涉及提交

| Commit | 说明 |
|--------|------|
| 3d19f23 | feat(user): 补充缺失的领域事件类 |
| 1c0165f | feat(user): 实现 Refresh Token 白名单与 Token Rotation |
| f451d41 | feat(user): 业务流程集成 Outbox 事件与分布式锁优化 |
| 8a97a3a | feat(user): Outbox 重试增强——指数退避、DEAD 状态、FOR UPDATE SKIP LOCKED |
| 872d79c | fix(user): 修复 BlogUploadClient 文件名与 UserAssembler 字段名不匹配 |
| ff82802 | test(user): 适配 User.reconstitute() 新签名及新增依赖 mock |

## 审查标准

- 高：与架构文档的偏离/违规、并发安全缺陷、数据丢失风险、安全漏洞
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：代码风格、命名、冗余

---

## 高级问题

### 问题 1：拉黑操作未获取关注锁，与架构文档要求的多锁排序规则不一致

- 级别：**高**
- 涉及文件：`BlockApplicationService.java:60-113`
- 架构文档要求（06-可靠性与一致性设计 2.2 节、5 节）：
  > "拉黑操作需同时获取拉黑锁和关注锁"
  > "所有锁按 userId 数值从小到大排序后依次获取：先获取 lock:block:{A}:{B}，再获取 lock:follow:{A}:{B}"
- 实际实现：`block()` 方法只获取了 `blockLock`（`lock:block:{min}:{max}`），没有获取 `followLock`（`lock:follow:{min}:{max}`）
- 影响：
  1. 拉黑操作中的"取消双向关注"与正常的关注/取消关注操作可能并发执行，操作相同的 `user_follows` 和 `user_follow_stats` 行
  2. 竞态场景：A 拉黑 B（删除关注关系、计数 -1）与 C 关注 A（计数 +1）并发时，统计计数可能漂移
  3. 极端情况下，拉黑事务删除了 A→B 的关注关系并减了计数，但关注操作的幂等检查在拉黑事务提交前已通过，导致关注关系被重新插入但计数已被减掉
- 修正建议：按架构文档要求，拉黑操作依次获取 `blockLock` 和 `followLock`，任一获取失败则释放已持有的锁

### 问题 2：storeRefreshToken 失败被静默吞掉，用户登录成功但 Token 实际不可用

- 级别：**高**
- 涉及文件：`AuthApplicationService.java:169-178`
- 问题描述：`storeRefreshToken()` 方法中 Redis 写入失败时仅记录 ERROR 日志，不抛异常。但 `login()` 方法已经向客户端返回了包含该 Refresh Token 的 `TokenVO`。客户端拿到 Token 后尝试刷新时，白名单中不存在该 Token，会被判定为"重放攻击"并清除该用户所有 Refresh Token
- 影响：
  1. 用户登录"成功"但 Refresh Token 实际无法使用，Access Token 过期后被迫重新登录
  2. 更严重的是，`refreshToken()` 中白名单校验失败会调用 `revokeAllRefreshTokens(userId)`，导致该用户在其他设备上的合法 Token 也被清除
- 修正建议：
  1. `storeRefreshToken()` 失败时应抛出异常，让 `login()` 返回登录失败（Redis 不可用时登录降级）
  2. 或者：`refreshToken()` 中白名单校验失败时，不要无条件清除所有 Token，而是先检查 Redis 连接状态，区分"Redis 不可用"和"真正的重放攻击"

### 问题 3：disableUser() 在事务内调用 Redis 操作，事务回滚后 Token 已被清除

- 级别：**高**
- 涉及文件：`UserApplicationService.java:219-243`
- 问题描述：`disableUser()` 标注了 `@Transactional`，方法内先写 DB（`user.disable()` + `outboxEventRepository.save()`），然后调用 `authApplicationService.revokeAllRefreshTokens(userId)` 清除 Redis 中的 Refresh Token。如果 `outboxEventRepository.save()` 之后、事务提交之前发生异常（如 DB 连接断开），事务回滚，但 Redis 中的 Token 已经被删除
- 影响：用户状态未变更（仍为 ACTIVE），但所有 Refresh Token 已被清除，用户被迫重新登录
- 注释中写了"事务提交后生效"，但实际代码并未实现这一点
- 修正建议：
  1. 将 `revokeAllRefreshTokens()` 移到事务提交后执行，使用 `TransactionSynchronizationManager.registerSynchronization()` 的 `afterCommit()` 回调
  2. 或者使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 模式

### 问题 4：findRetryableEvents() 使用 FOR UPDATE SKIP LOCKED 但调用方 publishPendingEvents() 无事务上下文

- 级别：**高**
- 涉及文件：`OutboxEventRepositoryImpl.java:222-243`、`OutboxEventPublisher.java:69-83`
- 问题描述：`findRetryableEvents()` 的 SQL 使用了 `FOR UPDATE SKIP LOCKED`，这要求在事务内执行才能持有行锁。但调用方 `publishPendingEvents()` 没有 `@Transactional` 注解，默认 auto-commit 模式下 `SELECT ... FOR UPDATE` 在查询返回后立即释放锁，`SKIP LOCKED` 完全失效
- 进一步问题：`publishSingleEvent()` 标注了 `@Transactional(propagation = REQUIRES_NEW)`，每条事件在独立事务中处理。但行锁在 `findRetryableEvents()` 返回时已释放，`publishSingleEvent()` 的新事务无法继承这些锁
- 影响：多实例部署时，两个实例可能同时查到相同的 PENDING 事件并重复投递到 RocketMQ（虽然下游幂等消费可兜底，但浪费资源且增加下游压力）
- 修正建议：
  1. 方案 A（推荐）：给 `publishPendingEvents()` 加 `@Transactional`，在同一事务内完成查询和遍历。`publishSingleEvent()` 的 `REQUIRES_NEW` 会挂起外层事务，行锁在外层事务提交前一直持有
  2. 方案 B：移除 `FOR UPDATE SKIP LOCKED`，改用乐观方式——`publishSingleEvent()` 中先 `UPDATE status = 'PROCESSING' WHERE id = ? AND status IN ('PENDING', 'FAILED')` 抢占，影响行数 = 0 则跳过

---

## 中级问题

### 问题 5：revokeAllRefreshTokens() 使用 KEYS 命令，生产环境有阻塞风险

- 级别：**中**
- 涉及文件：`AuthApplicationService.java:154-164`
- 问题描述：`redisTemplate.keys(UserRedisKeys.refreshTokenPattern(userId))` 底层执行 Redis `KEYS refresh_token:{userId}:*` 命令。`KEYS` 是 O(N) 全库扫描，在 Redis 数据量大时会阻塞主线程，导致其他请求超时
- 影响：高并发场景下，如果有大量用户同时被禁用或触发重放攻击检测，`KEYS` 命令可能导致 Redis 短暂不可用
- 修正建议：使用 `SCAN` 命令替代 `KEYS`，或者改用 Redis Set 存储用户的所有 tokenId（`SMEMBERS` + 批量 `DEL`），避免全库扫描

### 问题 6：Outbox 事件构造散落在多个 Application Service 中，存在大量重复代码

- 级别：**中**
- 涉及文件：`UserApplicationService.java:88-98, 228-237, 259-268`、`BlockApplicationService.java:92-98`
- 问题描述：注册、禁用、启用、拉黑四处业务代码中，OutboxEvent 的构造逻辑几乎相同（`UUID.randomUUID()` + `JSON.toJSONString()` + `OutboxEventStatus.PENDING` + `LocalDateTime.now()`），但分散在各个方法中手动拼装
- 影响：
  1. 新增事件类型时容易遗漏必要字段或写错 topic/tag
  2. 架构文档 3.2 节要求事件载荷包含 `eventId`、`occurredAt`、`aggregateVersion`、`schemaVersion`，但当前实现的 payload 中均未包含这些元数据字段
- 修正建议：
  1. 在 `OutboxEvent` 上提供工厂方法 `OutboxEvent.of(topic, tag, shardingKey, payload)`，封装默认值
  2. 事件载荷应包含架构文档要求的元数据（eventId、occurredAt、schemaVersion），建议在序列化时统一包装

### 问题 7：领域事件类已定义但未被使用，Outbox payload 直接用 Map/DTO 序列化

- 级别：**中**
- 涉及文件：`UserActivatedEvent.java`、`UserDeactivatedEvent.java`、`UserBlockedEvent.java`、`UserPasswordChangedEvent.java`
- 问题描述：commit 3d19f23 新增了 4 个领域事件类（`UserActivatedEvent`、`UserDeactivatedEvent`、`UserBlockedEvent`、`UserPasswordChangedEvent`），但后续 commit f451d41 中写入 Outbox 时，payload 使用的是 `Map.of(...)` 或直接构造 DTO，完全没有使用这些领域事件类
- 影响：
  1. 领域事件类成为死代码，增加维护负担
  2. `Map.of("userId", userId)` 缺乏类型约束，字段名拼写错误无法在编译期发现
  3. 不同事件的 payload 结构不统一，下游消费方难以建立稳定的反序列化契约
- 修正建议：Outbox payload 统一使用对应的领域事件类序列化，如 `JSON.toJSONString(new UserDeactivatedEvent(userId))`

### 问题 8：storeRefreshToken() 日志格式错误，userId 不会被正确输出

- 级别：**中**
- 涉及文件：`AuthApplicationService.java:176`
- 问题描述：`log.error("Failed to store refresh token in whitelist: userId=", userId, e)` 使用的是字符串拼接而非 SLF4J 占位符。SLF4J 的 `log.error(String, Object...)` 要求使用 `{}` 占位符，当前写法 `userId=` 后面的逗号会导致 `userId` 被当作异常参数的一部分，日志中不会输出 userId 的值
- 影响：排查 Token 存储失败问题时，日志中缺少关键的 userId 信息
- 修正建议：改为 `log.error("Failed to store refresh token in whitelist: userId={}", userId, e)`

### 问题 9：Refresh Token 白名单 Key 未使用统一前缀，与 UserRedisKeys 命名规范不一致

- 级别：**中**
- 涉及文件：`UserRedisKeys.java:108-109`
- 问题描述：`refreshTokenWhitelist()` 返回 `refresh_token:{userId}:{tokenId}`，使用下划线分隔且无 `user:` 前缀。而 `UserRedisKeys` 中其他所有 Key 均以 `user:` 为前缀、冒号分隔（如 `user:{userId}:detail`、`user:{userId}:token:access`）
- 影响：
  1. 运维通过 `user:*` 模式无法扫描到 Refresh Token 白名单 Key，增加排查难度
  2. 旧格式 `refreshToken()` 返回 `user:{userId}:token:refresh` 仍保留，两套 Key 共存容易混淆
- 修正建议：统一为 `user:{userId}:token:refresh:{tokenId}`，同时清理旧的 `refreshToken()` 方法（如果已无调用方）

### 问题 10：OutboxEvent.maxRetries 注释仍写"默认为 3 次"，实际已改为 10 次

- 级别：**中**
- 涉及文件：`OutboxEvent.java:88-92`
- 问题描述：字段注释写 `默认为 3 次，超过后标记为 FAILED`，但构造函数中 `this.maxRetries = 10`，且超过后标记为 `DEAD` 而非 `FAILED`。注释与实现完全不一致
- 影响：后续维护者阅读注释会产生误解，可能在其他地方硬编码 3 次重试
- 修正建议：更新注释为"默认为 10 次，超过后标记为 DEAD"

---

## 低级问题

### 问题 11：ZhiCoreUploadClient 字段名大写开头，违反 Java 命名规范

- 级别：**低**
- 涉及文件：`UserApplicationService.java:53`
- 问题描述：`private final ZhiCoreUploadClient ZhiCoreUploadClient` 字段名以大写字母开头，与 Java 字段命名规范（camelCase）不一致。Lombok `@RequiredArgsConstructor` 会基于字段名生成构造参数，Spring 按类型注入不受影响，但代码可读性差
- 修正建议：改为 `zhiCoreUploadClient`（小写开头）

### 问题 12：BlockApplicationService 导入了 ArrayList 但未使用

- 级别：**低**
- 涉及文件：`BlockApplicationService.java:29`
- 问题描述：`import java.util.ArrayList` 未被任何代码引用，属于冗余导入
- 修正建议：删除未使用的 import

### 问题 13：Outbox 事件 topic 命名与架构文档不一致

- 级别：**低**
- 涉及文件：`UserApplicationService.java:91, 230, 261`、`BlockApplicationService.java:94`
- 问题描述：架构文档 04-事件驱动架构 3.1 节定义的 Topic 为 `user-profile-updated`，但 `updateProfile()` 中使用的是 `user-profile-changed`。`disableUser()` 使用 `user-deactivated`、`enableUser()` 使用 `user-activated`、`block()` 使用 `user-blocked`，这些与文档一致。但 `register()` 使用 `user-registered` 而文档也是 `user-registered`，唯独 profile 更新的 topic 名不匹配
- 修正建议：统一为架构文档定义的 `user-profile-updated`，或更新架构文档

---

## 做得好的地方

1. **Token Rotation 设计合理**：每次刷新签发新 Refresh Token 并废弃旧 Token，有效防止 Token 被窃取后长期滥用。重放攻击检测（白名单不存在时清除所有 Token）的思路正确，只是实现细节需要打磨
2. **拉黑锁键排序防死锁**：`UserRedisKeys.blockLock()` 按 `Math.min/max` 排序生成锁键，确保任意两个用户之间的拉黑操作使用相同的锁键，从根本上避免了 A↔B 互相拉黑的死锁
3. **Outbox 指数退避 + DEAD 状态**：从固定 3 次重试升级为 10 次指数退避（1s→5min），DEAD 状态与 FAILED 状态分离，语义更清晰。`scheduleNextRetry()` 和 `isExhausted()` 封装在领域模型中，职责内聚
4. **FOR UPDATE SKIP LOCKED 方向正确**：虽然当前事务上下文有问题（见问题 4），但选择 `SKIP LOCKED` 而非 `NOWAIT` 是正确的——多实例场景下不会因锁冲突抛异常，而是跳过已被其他实例锁定的行

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 1 | 高 | 拉黑操作未获取关注锁，与架构文档多锁排序规则不一致 | BlockApplicationService |
| 2 | 高 | storeRefreshToken 失败被静默吞掉，登录成功但 Token 不可用 | AuthApplicationService |
| 3 | 高 | disableUser() 事务内调用 Redis，回滚后 Token 已被清除 | UserApplicationService |
| 4 | 高 | findRetryableEvents() FOR UPDATE SKIP LOCKED 无事务上下文，行锁失效 | OutboxEventRepositoryImpl, OutboxEventPublisher |
| 5 | 中 | revokeAllRefreshTokens() 使用 KEYS 命令，生产有阻塞风险 | AuthApplicationService |
| 6 | 中 | Outbox 事件构造散落多处，缺少架构文档要求的元数据字段 | UserApplicationService, BlockApplicationService |
| 7 | 中 | 领域事件类已定义但未被使用，payload 用 Map 序列化 | 4 个 Event 类, UserApplicationService |
| 8 | 中 | storeRefreshToken() 日志格式错误，userId 不会被输出 | AuthApplicationService |
| 9 | 中 | Refresh Token 白名单 Key 未使用统一前缀 | UserRedisKeys |
| 10 | 中 | OutboxEvent.maxRetries 注释仍写"3 次"，实际已改为 10 次 | OutboxEvent |
| 11 | 低 | ZhiCoreUploadClient 字段名大写开头 | UserApplicationService |
| 12 | 低 | BlockApplicationService 导入了 ArrayList 但未使用 | BlockApplicationService |
| 13 | 低 | updateProfile 的 Outbox topic 与架构文档不一致 | UserApplicationService |

| 级别 | 数量 |
|------|------|
| 高 | 4 |
| 中 | 6 |
| 低 | 3 |

---

*审查完成于 2026-02-27*

