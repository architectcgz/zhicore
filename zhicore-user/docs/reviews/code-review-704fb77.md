# 代码 Review：ff82802..704fb77 修复第一轮 code review 的 13 项问题

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit ff82802..704fb77 涉及的 9 个文件（169+/114-） |
| 审查基准 | docs/reviews/code-review-ff82802.md（上一轮 13 项问题）、docs/architecture/06-可靠性与一致性设计.md |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

## 涉及提交

| Commit | 说明 |
|--------|------|
| 49cc4db | fix(user): 修复 code review 高级问题（问题 1-5） |
| 4b6b3c2 | fix(user): 修复 code review 中级问题（问题 6-10） |
| 704fb77 | test(user): 适配 code review 修复后的测试变更 |

## 审查标准

- 高：与架构文档的偏离/违规、并发安全缺陷、数据丢失风险、安全漏洞
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：代码风格、命名、冗余

---

## 上一轮问题修复状态

| 编号 | 级别 | 问题标题 | 状态 |
|------|------|---------|------|
| 1 | 高 | 拉黑操作未获取关注锁 | ✅ 已修复 |
| 2 | 高 | storeRefreshToken 失败被静默吞掉 | ✅ 已修复 |
| 3 | 高 | disableUser() 事务内调用 Redis，回滚后 Token 已被清除 | ✅ 已修复 |
| 4 | 高 | findRetryableEvents() FOR UPDATE SKIP LOCKED 无事务上下文 | ✅ 已修复 |
| 5 | 中 | revokeAllRefreshTokens() 使用 KEYS 命令 | ✅ 已修复 |
| 6 | 中 | Outbox 事件构造散落多处 | ✅ 已修复（工厂方法） |
| 7 | 中 | 领域事件类已定义但未被使用 | ✅ 已修复 |
| 8 | 中 | storeRefreshToken() 日志格式错误 | ✅ 已修复（移除 try-catch 后不再需要） |
| 9 | 中 | Refresh Token 白名单 Key 未使用统一前缀 | ✅ 已修复 |
| 10 | 中 | OutboxEvent.maxRetries 注释仍写"3 次" | ✅ 已修复 |
| 11 | 低 | ZhiCoreUploadClient 字段名大写开头 | ✅ 已修复 |
| 12 | 低 | BlockApplicationService 导入了 ArrayList 但未使用 | ✅ 已修复 |
| 13 | 低 | updateProfile 的 Outbox topic 与架构文档不一致 | ✅ 已修复 |

上一轮 13 项问题全部修复。

---

## 新发现的问题

### 问题 1：拉黑操作的关注锁键与正常关注操作的锁键格式不一致，无法互斥

- 级别：**高**
- 涉及文件：`UserRedisKeys.java:135-144`、`BlockApplicationService.java:62`
- 问题描述：`blockFollowLock()` 生成的锁键为 `lock:follow:{min(A,B)}:{max(A,B)}`，按 userId 大小排序。但架构文档 06-可靠性与一致性设计 2.2 节中，正常关注操作的锁键为 `lock:follow:{userId}:{targetId}`（不排序）。两者格式不同，无法互斥
- 场景：A(id=100) 关注 B(id=200) 获取锁 `lock:follow:100:200`；同时 B 拉黑 A 获取锁 `lock:follow:100:200`——这种情况恰好一致。但 B(id=200) 关注 A(id=100) 获取锁 `lock:follow:200:100`，而 A 拉黑 B 获取锁 `lock:follow:100:200`——两把不同的锁，无法互斥
- 影响：拉黑操作删除关注关系时，反向关注操作可能并发执行，导致计数漂移
- 修正建议：统一关注锁的键格式。两种方案：
  1. 方案 A：关注操作也改为按 userId 排序（`lock:follow:{min}:{max}`），所有涉及同一对用户的操作共享同一把锁
  2. 方案 B：拉黑操作获取两把关注锁（`lock:follow:A:B` 和 `lock:follow:B:A`），覆盖双向关注场景

### 问题 2：publishPendingEvents() 加 @Transactional 后，REQUIRES_NEW 子事务会挂起外层事务，行锁持有时间过长

- 级别：**中**
- 涉及文件：`OutboxEventPublisher.java:68-83`
- 问题描述：`publishPendingEvents()` 加了 `@Transactional` 后，`findRetryableEvents()` 的 `FOR UPDATE SKIP LOCKED` 行锁在外层事务提交前一直持有。`publishSingleEvent()` 的 `REQUIRES_NEW` 会挂起外层事务，每条事件的 RocketMQ 发送（网络 IO）都在行锁持有期间执行。100 条事件 × 每条发送耗时 50ms = 5s 锁持有时间
- 影响：其他实例的 `findRetryableEvents()` 在这 5s 内会 SKIP 这些行，不会重复投递（这是正确的）。但如果 RocketMQ 响应慢或网络抖动，锁持有时间可能远超预期，导致 PG 连接长时间被占用
- 修正建议：当前实现功能上是正确的，FOR UPDATE SKIP LOCKED 已生效。但建议关注以下优化点：
  1. 将批量大小从 100 降低到 20-50，减少单次锁持有时间
  2. 添加 `@Transactional(timeout = 30)` 设置事务超时，防止极端情况下连接泄漏

### 问题 3：storeRefreshToken() 移除 try-catch 后，Redis 不可用时登录直接报 500

- 级别：**中**
- 涉及文件：`AuthApplicationService.java:175-181`
- 问题描述：上一轮问题 2 的修复方案是移除 `storeRefreshToken()` 的 try-catch，让异常向上抛出。这解决了"Token 不可用但登录成功"的问题，但引入了新的降级问题：Redis 不可用时，用户完全无法登录（500 错误），而非优雅降级
- 架构文档 06-可靠性与一致性设计 3.1 节并未明确定义 Redis 不可用时登录的降级策略
- 影响：Redis 短暂故障期间所有用户无法登录
- 修正建议：这是一个权衡决策，当前"宁可登录失败也不发不可用 Token"的策略是合理的安全选择。建议：
  1. 在 `login()` 方法中 catch Redis 异常，抛出明确的业务异常（如"系统繁忙，请稍后重试"），而非暴露 500
  2. 在架构文档中补充此降级策略的说明

### 问题 4：AuthApplicationService 中 storeRefreshToken() 存在重复的 Javadoc 注释块

- 级别：**低**
- 涉及文件：`AuthApplicationService.java:175-182`
- 问题描述：`storeRefreshToken()` 方法上方有两个连续的 `/** ... */` Javadoc 注释块，第一个是旧的（只有方法名），第二个是新增的（包含详细说明）。编译器只会取最后一个，但代码不整洁
- 修正建议：删除第一个重复的 Javadoc 块，只保留带详细说明的那个

---

## 做得好的地方

1. **拉黑双锁嵌套结构清晰**：`block()` 方法将双锁获取/释放用嵌套 try-finally 实现，释放顺序与获取顺序相反（先释放 followLock 再释放 blockLock），符合锁的 LIFO 释放原则。核心逻辑提取到 `doBlock()` 私有方法，可读性好
2. **TransactionSynchronization.afterCommit() 使用正确**：`disableUser()` 中将 `revokeAllRefreshTokens()` 放到 `afterCommit()` 回调中，确保只有事务成功提交后才清除 Token，彻底解决了上一轮的事务回滚不一致问题
3. **SCAN 替代 KEYS 实现规范**：`revokeAllRefreshTokens()` 使用 `redisTemplate.scan()` + try-with-resources 自动关闭 Cursor，避免了 KEYS 的全库扫描阻塞风险
4. **OutboxEvent.of() 工厂方法**：封装了 UUID、PENDING 状态、当前时间等默认值，调用方代码从 7 行缩减到 4 行，减少了出错概率
5. **测试适配到位**：`StatusManagement` 测试类中正确初始化/清理了 `TransactionSynchronizationManager`，确保 `afterCommit()` 回调在单元测试中不会报错

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 1 | 高 | 拉黑关注锁键与正常关注锁键格式不一致，无法互斥 | UserRedisKeys, BlockApplicationService |
| 2 | 中 | publishPendingEvents() 行锁持有时间可能过长 | OutboxEventPublisher |
| 3 | 中 | Redis 不可用时登录直接 500，缺少优雅降级 | AuthApplicationService |
| 4 | 低 | storeRefreshToken() 存在重复 Javadoc 注释块 | AuthApplicationService |

| 级别 | 数量 |
|------|------|
| 高 | 1 |
| 中 | 2 |
| 低 | 1 |

---

*审查完成于 2026-02-27*
