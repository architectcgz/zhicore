# ZhiCore-microservice 架构文档 Review

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | zhicore-microservice 顶层 + 各子服务 architecture 文档（排除 zhicore-content） |
| 审查日期 | 2026-02-26 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

## 审查标准

- 高：架构缺陷、一致性风险、数据安全隐患
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：文档质量、命名不一致、冗余内容

---

## 一、跨文档级问题（全局性）

### 问题 1：事件发布在事务内使用异步发送——消息丢失风险

- 级别：**高**
- 涉及文档：`04-service-communication.md`、`05-ddd-layered-architecture.md`、`06-data-architecture.md`
- 问题描述：多处示例中，领域事件在 `@Transactional` 方法内通过 `rocketMQTemplate.asyncSend()` 发送。事务提交前消息已发出，若事务回滚则消费者收到"幽灵事件"；事务提交后应用崩溃则消息丢失。
- 影响范围：所有事件驱动的跨服务同步（搜索索引、通知、排行榜）
- 修正建议：
  1. 使用 RocketMQ 事务性消息（Half Message）
  2. 或 Transactional Outbox 模式（事件先写本地 outbox 表，后台投递）
  3. 至少使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 在事务提交后发送

### 问题 2：ZhiCore-message 与 ZhiCore-notification 职责重叠

- 级别：**高**
- 涉及文档：`02-microservices-list.md`、`blog-message-im-integration.md`
- 问题描述：notification 服务订阅 PostLikedEvent/CommentCreatedEvent/UserFollowedEvent 创建通知；message 服务的新职责也包含"系统通知管理"，处理相同事件。两个服务都在做系统通知。
- 修正建议：明确二选一，或划分边界（notification 负责生成存储，message 负责聚合展示），消除矛盾描述。

### 问题 3：文档间数据不一致——服务数量自相矛盾

- 级别：**中**
- 涉及文档：`01-system-overview.md`、`02-microservices-list.md`、`README.md`
- 问题描述：标题说"14 个微服务"，概述说"13 个模块"，业务服务数量在 8 和 9 之间摇摆（gateway 归属不统一）。
- 修正建议：统一为"1 网关 + 8 业务服务 + 2 支持服务 + 2 共享模块 = 13 模块"。

---

## 二、04-service-communication.md

### 问题 4：Feign Client 服务名不一致

- 级别：**中**
- 问题描述：ZhiCore-api 中定义 `@FeignClient(name = "user-service")`，各服务继承后又定义 `@FeignClient(name = "ZhiCore-user")`，服务名冲突。
- 修正建议：ZhiCore-api 中只定义纯接口不加 `@FeignClient`，各服务自行添加注解；或统一服务名。

### 问题 5：降级策略不一致

- 级别：**中**
- 问题描述：`getUserSimple()` 降级返回假数据（"用户123"），`getUserById()` 返回错误。假数据比报错更具误导性。
- 修正建议：统一策略，默认返回错误；需要优雅降级时在 DTO 中增加 `degraded` 标记。

### 问题 6：消费失败缺少死信队列和补偿机制

- 级别：**高**
- 问题描述：文档只说"RocketMQ 会自动重试"，未说明重试耗尽后的 DLQ 处理、幂等消费的具体实现（`StatefulIdempotentHandler` 未展开）。
- 修正建议：补充 DLQ 处理策略、幂等键设计、告警机制。

---

## 三、05-ddd-layered-architecture.md

### 问题 7：类级别 @Transactional 粒度过粗

- 级别：**高**
- 问题描述：`PostApplicationService` 使用类级别 `@Transactional`，所有方法（含查询）都开启写事务。
- 修正建议：改为方法级别，查询用 `readOnly = true`，写用 `rollbackFor = Exception.class`。

### 问题 8：reconstitute 工厂方法参数过多

- 级别：**中**
- 问题描述：`Post.reconstitute()` 有 10 个参数，可读性差且易传错。
- 修正建议：使用 Builder 模式或 `PostSnapshot` 值对象封装恢复参数。

### 问题 9：领域事件发布位置建议过于绝对

- 级别：**中**
- 问题描述：文档完全禁止领域层与事件关联，但 DDD 主流实践允许领域对象注册事件、应用层发布。
- 修正建议：区分"注册事件"和"发布事件"，补充事件收集机制说明。

---

## 四、06-data-architecture.md

### 问题 10：延迟双删实现不可靠

- 级别：**高**
- 问题描述：使用 `CompletableFuture.runAsync() + Thread.sleep(500)` 实现延迟双删。应用重启时异步任务丢失；500ms 硬编码无依据；使用公共线程池可能被阻塞。
- 修正建议：使用 `ScheduledExecutorService` 或 MQ 延迟消息；延迟时间可配置并说明依据。

### 问题 11：缓存穿透防护使用魔法字符串 "NULL"

- 级别：**中**
- 问题描述：空值缓存使用字符串 `"NULL"` 作为标记，如果业务数据恰好包含该字符串会误判。
- 修正建议：使用专用的空值对象常量（如 `CacheConstants.NULL_MARKER`），或使用 Redis 的特殊数据结构标记。

---

## 五、03-file-upload-architecture.md / file-service-*.md

### 问题 12：文件删除在循环中逐个调用——性能和事务风险

- 级别：**中**
- 涉及文档：`file-service-data-flow.md`
- 问题描述：删除文章时，循环逐个调用 `FileClient.deleteFile()`。如果文章有 20 张图片，就是 20 次 HTTP 调用，任何一次失败都会导致部分删除。
- 修正建议：
  1. 提供批量删除接口 `deleteFiles(List<String> fileIds)`
  2. 文件删除改为异步（发事件，由 file-service 消费处理），主流程不阻塞

### 问题 13：秒传基于 SHA-256 哈希——哈希碰撞和安全风险

- 级别：**低**
- 问题描述：秒传仅依赖文件哈希判断是否为同一文件。虽然 SHA-256 碰撞概率极低，但文档未说明是否校验文件大小等辅助条件。另外，不同用户上传相同文件会共享存储，删除时需要引用计数，文档虽提到 `ref_count` 但未说明并发递减的原子性保证。
- 修正建议：秒传判断增加 `file_size` 辅助校验；`ref_count` 递减使用数据库原子操作 `UPDATE SET ref_count = ref_count - 1 WHERE ref_count > 0`。

---

## 六、blog-message-im-integration.md

### 问题 14：私信发送缺少事务保证

- 级别：**中**
- 问题描述：`PrivateMessageService.sendPrivateMessage()` 先做业务校验，再调用 im-system 发送，最后记录统计。如果 im-system 调用成功但统计记录失败，数据不一致；如果 im-system 超时但实际已发送，重试会导致重复消息。
- 修正建议：
  1. im-system 调用应支持幂等（基于 requestId 去重）
  2. 统计记录失败不应影响主流程，可异步补偿

### 问题 15：群发消息在循环中逐个发送——无并发控制和失败处理

- 级别：**中**
- 问题描述：`BulkMessageService.sendBulkMessage()` 在 for 循环中逐个调用 im-system，失败只记日志不重试。大量发送时串行执行效率低。
- 修正建议：使用线程池并发发送；失败的消息写入重试队列；增加整体成功率统计返回给调用方。

---

## 七、blog-api-module-purpose.md

### 问题 16：FallbackFactory 放在 ZhiCore-api 共享模块中——违反职责分离

- 级别：**中**
- 问题描述：`LeafServiceFallbackFactory` 作为 `@Component` 放在 ZhiCore-api 中，导致所有依赖 ZhiCore-api 的服务都必须扫描 `com.zhicore.api` 包。如果某个服务不需要 Leaf 服务，也会被迫加载这个 Bean。
- 修正建议：
  1. FallbackFactory 全部移到各自服务中（文档自己也承认"业务服务的 Fallback 应在各自服务中"）
  2. 或使用 `@ConditionalOnProperty` 条件加载

---

## 八、file-service-integration.md

### 问题 17：熔断框架不一致——Sentinel vs Resilience4j

- 级别：**中**
- 问题描述：`04-service-communication.md` 中 Feign 配置使用 Sentinel 熔断（`feign.sentinel.enabled: true`），而 `file-service-integration.md` 中使用 Resilience4j（`resilience4j.circuitbreaker`）。同一系统使用两套熔断框架增加维护成本。
- 修正建议：统一使用一套熔断框架。建议统一用 Sentinel（与 Spring Cloud Alibaba 生态一致）。

---

## 九、README.md

### 问题 18：文档状态标记与实际不符

- 级别：**低**
- 问题描述：README 中多个文档标记为"📝 待编写"（如 01-系统概述、02-微服务列表、03-文件上传架构），但实际这些文档已经完成且内容丰富。
- 修正建议：更新 README 中的文档状态标记为"✅ 已完成"。

---

## 问题汇总

| 级别 | 数量 | 关键问题 |
|------|------|---------|
| 高 | 5 | 事件发布与事务不一致、服务职责重叠、消费失败无 DLQ、类级别事务、延迟双删不可靠 |
| 中 | 11 | 服务名冲突、降级策略不一致、参数过多、文件删除性能、私信幂等性等 |
| 低 | 2 | 哈希碰撞辅助校验、文档状态标记 |

---

*以上为顶层 docs/architecture/ 的 12 个文档 review。各子服务（zhicore-user、zhicore-comment 等）的 architecture 文档 review 见后续追加。*
