# zhicore-content 测试代码补充清单

## 文档说明

本文档基于架构审阅报告（2026-02-24）和集成测试场景文档，列出需要补充的测试代码清单。
所有测试应遵循"正向 1 条 + 异常 1 条 + 可观测断言"原则。

**最后更新**: 2026-02-25

---

## 优先级说明

- **P0**: 高优先级，影响核心功能和数据一致性，必须优先完成
- **P1**: 中优先级，影响用户体验和系统可靠性，建议近期完成
- **P2**: 低优先级，技术债和优化项，可持续改进

---

## P0 - 高优先级测试（Sprint 1）

### 1. 定时发布相关测试（R1）

**测试类**: `ScheduledPublishIntegrationTest`（扩展现有测试）

#### 1.1 未到发布时间的处理
- **场景**: 消费定时发布事件时，DB 时间 < scheduledAt
- **验证点**:
  - 文章状态保持 SCHEDULED，未变更为 PUBLISHED
  - 事件状态根据重试次数转换（PENDING → SCHEDULED_PENDING）
  - 日志包含 dbNow、appNow、scheduledAt、remainingTime
  - 计算延迟时间正确：min(remaining_time, 2^retry 分钟, max_delay_minutes)

#### 1.2 到点幂等发布
- **场景**: DB 时间 >= scheduledAt，执行发布操作
- **验证点**:
  - 使用单 SQL 条件更新：`UPDATE posts SET status=PUBLISHED WHERE id=? AND status=SCHEDULED`
  - affected_rows == 0 时视为幂等 no-op
  - 查询文章状态区分"已发布"和"非法状态"
  - 已发布时更新事件状态为 PUBLISHED（幂等收敛）
  - 非法状态时标记事件为 FAILED 并记录错误
  - 发布成功后发出 PostPublishedDomainEvent

#### 1.3 重投机制测试
- **场景**: 未到发布时间，需要重新投递消息
- **验证点**:
  - reschedule_retry_count 正确递增
  - 延迟时间计算正确（指数退避）
  - 达到 max_reschedule_retries 后转为 SCHEDULED_PENDING
  - 日志包含 reschedule_retry_count 和延迟时间

#### 1.4 扫描入队闸门测试（CAS）
- **场景**: 定时扫描任务（每 1 分钟）扫描 SCHEDULED_PENDING 事件
- **验证点**:
  - 使用完整 WHERE 条件更新 last_enqueue_at（status + scheduledAt + last_enqueue_at）
  - 仅 affected_rows == 1 时允许入队
  - 并发扫描时只有一个实例成功入队（多线程测试）
  - 冷却期内（2 分钟）不重复入队

#### 1.5 补扫重置测试
- **场景**: 补扫任务（每 5 分钟）重置超时闸门
- **验证点**:
  - 重置 last_enqueue_at 超过 10 分钟的事件
  - 限定 scheduledAt <= db_now，避免未到点事件被误处理
  - 重置后事件可被下一轮扫描重新入队

#### 1.6 发布失败重试与 DLQ
- **场景**: 到达发布时间后发布操作失败
- **验证点**:
  - publish_retry_count 正确递增
  - 达到 max_publish_retries（默认 3）后转为 FAILED
  - 投递到死信队列 scheduled_publish_dlq
  - 触发告警（可 Mock 告警通道）
  - 日志包含 publish_retry_count 和错误信息

#### 1.7 时间基准测试
- **场景**: 验证使用数据库时间而非应用时间
- **验证点**:
  - 所有时间比较使用 `SELECT CURRENT_TIMESTAMP`
  - 日志同时记录 dbNow 和 appNow
  - 时钟漂移场景下行为正确

---

### 2. 点赞仓储测试（R2）

**测试类**: `PostLikeRepositoryIntegrationTest`（新建）

#### 2.1 基本 CRUD 操作
- **场景**: 保存、查询、删除点赞记录
- **验证点**:
  - save 成功写入数据库
  - findByPostId 返回所有点赞记录
  - findByUserId 返回用户所有点赞
  - delete 成功删除记录

#### 2.2 幂等性测试
- **场景**: 重复点赞同一文章
- **验证点**:
  - 唯一约束 (post_id, user_id) 生效
  - 捕获唯一约束冲突异常
  - 返回成功（幂等处理）
  - 数据库中只有一条记录

#### 2.3 删除幂等测试
- **场景**: 删除不存在的点赞记录
- **验证点**:
  - affected_rows == 0
  - 返回成功（幂等处理）
  - 不抛出异常

#### 2.4 索引命中测试
- **场景**: 验证索引有效性
- **验证点**:
  - 通过 EXPLAIN 验证 idx_post_likes_post_id 被使用
  - 通过 EXPLAIN 验证 idx_post_likes_user_id 被使用
  - 查询性能符合预期

#### 2.5 批量状态查询测试
- **场景**: 批量查询多篇文章的点赞状态
- **验证点**:
  - Redis 命中路径正确返回
  - Redis 未命中时查询 DB 并回填
  - Pipeline 批量操作正确执行

---

### 3. 收藏仓储测试（R3）

**测试类**: `PostFavoriteRepositoryIntegrationTest`（新建）

测试场景与点赞仓储测试（R2）完全一致，替换为收藏相关的表和字段：

#### 3.1 基本 CRUD 操作
#### 3.2 幂等性测试
#### 3.3 删除幂等测试
#### 3.4 索引命中测试
#### 3.5 批量状态查询测试

---

### 4. 乐观锁测试（R6）

**测试类**: `OptimisticLockIntegrationTest`（扩展现有测试）

#### 4.1 并发更新冲突测试
- **场景**: 多线程并发更新同一文章
- **验证点**:
  - 启用 OptimisticLockerInnerInterceptor
  - 版本冲突时抛出 OptimisticLockException
  - GlobalExceptionHandler 捕获并返回 HTTP 409
  - 响应包含 CONCURRENT_UPDATE_CONFLICT 错误码
  - 响应包含 retry_suggested: true
  - 日志记录冲突详情（entity type, ID, expected version）

#### 4.2 实体不存在测试
- **场景**: 更新不存在的文章
- **验证点**:
  - affected_rows == 0
  - 查询实体确认不存在
  - 返回 HTTP 404 NOT_FOUND
  - 不返回 409（区分不存在和版本冲突）

#### 4.3 版本冲突与实体不存在区分
- **场景**: 验证异常处理逻辑正确性
- **验证点**:
  - 实体不存在 → 404
  - 版本冲突 → 409 + CONCURRENT_UPDATE_CONFLICT
  - 不会混淆两种情况

---

## P1 - 中优先级测试（Sprint 2）

### 5. 列表查询参数测试（R7）

**测试类**: `PostListQueryIntegrationTest`（新建）

#### 5.1 参数互斥测试
- **场景**: 同时提供 cursor 和 page 参数
- **验证点**:
  - 返回 HTTP 400 Bad Request
  - 错误消息明确说明参数互斥

#### 5.2 排序模式限制测试
- **场景**: 验证排序模式与分页方式的限制
- **验证点**:
  - LATEST + page → 400 + "LATEST sort requires cursor pagination"
  - POPULAR + cursor → 400 + "POPULAR sort requires offset pagination"
  - LATEST + cursor → 200（正常）
  - POPULAR + page → 200（正常）

#### 5.3 size 边界测试
- **场景**: 验证 size 参数边界
- **验证点**:
  - size 未提供 → 默认 20
  - size < 1 → 400
  - size > 100 → 400
  - size = 1 → 200
  - size = 100 → 200

#### 5.4 sort/status 参数生效测试
- **场景**: 验证参数正确传递到 Repository 层
- **验证点**:
  - 不同 sort 参数返回不同排序结果
  - 不同 status 参数返回不同过滤结果
  - 参数正确传递到 PostRepositoryImpl

---

### 6. 状态参数安全解析测试（R8）

**测试类**: `PostStatusParsingTest`（单元测试）

#### 6.1 非法状态解析测试
- **场景**: 传入非法 status 参数
- **验证点**:
  - 返回 HTTP 400 Bad Request（不是 500）
  - 错误消息包含合法状态值列表
  - 不抛出 IllegalArgumentException
  - 日志记录 warning

#### 6.2 合法状态解析测试
- **场景**: 传入合法 status 参数
- **验证点**:
  - 正确解析为对应枚举值
  - 查询结果正确过滤

---

### 7. 光标分页测试（R10）

**测试类**: `CursorPaginationIntegrationTest`（新建）

#### 7.1 复合游标无重复遗漏测试
- **场景**: 创建多篇相同 publishedAt 的文章，分页浏览
- **验证点**:
  - 使用 (publishedAt, postId) 复合游标
  - 排序为 `ORDER BY published_at DESC, id DESC`
  - 翻页无重复文章
  - 翻页无遗漏文章
  - 所有文章都能被访问到

#### 7.2 游标编码解码测试
- **场景**: 验证游标编码和解码正确性
- **验证点**:
  - 编码格式：Base64("publishedAt|postId")
  - 解码返回 CursorToken{publishedAt, postId}
  - 编码后解码能还原原始值

#### 7.3 非法游标测试
- **场景**: 传入非法游标格式
- **验证点**:
  - 返回 HTTP 400 Bad Request
  - 错误消息说明游标格式错误
  - 不抛出未捕获异常

#### 7.4 首页请求测试
- **场景**: cursor 为空或 null
- **验证点**:
  - 视为首页请求
  - 返回最新的 size 条记录
  - 返回 nextCursor 用于下一页

---

### 8. 缓存三态测试（R11）

**测试类**: `CacheThreeStateIntegrationTest`（新建）

#### 8.1 MISS 状态测试
- **场景**: 首次查询缓存不存在
- **验证点**:
  - CacheRepository.getWithState 返回 CacheResult.MISS
  - 查询数据库
  - 回填缓存（TTL 30 分钟）
  - 返回查询结果

#### 8.2 NULL 状态测试
- **场景**: 资源不存在，缓存了 NULL 标记
- **验证点**:
  - CacheRepository.getWithState 返回 CacheResult.NULL
  - 不查询数据库
  - 返回 null
  - Redis 中存储 `{"type":"NULL"}`（无 value 字段）
  - TTL 为 5 分钟

#### 8.3 HIT 状态测试
- **场景**: 缓存命中
- **验证点**:
  - CacheRepository.getWithState 返回 CacheResult.HIT
  - 不查询数据库
  - 返回缓存值
  - Redis 中存储 `{"type":"HIT","value":<data>}`

#### 8.4 NULL TTL 测试
- **场景**: 验证 NULL 标记过期时间
- **验证点**:
  - NULL 标记 TTL 为 5 分钟
  - 正常缓存 TTL 为 30 分钟
  - 过期后重新查询数据库

#### 8.5 缓存失效后重建测试
- **场景**: 数据创建后失效 NULL 标记
- **验证点**:
  - 创建数据前缓存返回 NULL
  - 创建数据后失效缓存
  - 再次查询返回新数据（HIT）

---

### 9. 缓存键测试（R12）

**测试类**: `CacheKeyStrategyTest`（单元测试 + 集成测试）

#### 9.1 缓存键包含 size 测试
- **场景**: 不同 size 参数使用不同缓存键
- **验证点**:
  - size=10 和 size=20 使用不同 key
  - 不会互相污染缓存
  - key 格式：`post:list:v1:{status}:{sort}:{size}:{cursor|page}`

#### 9.2 status 占位符测试
- **场景**: status 参数为空时使用占位符
- **验证点**:
  - status 未提供 → key 中使用 "ALL"
  - status 提供 → key 中使用实际值
  - 不同 status 使用不同 key

#### 9.3 缓存键版本化测试
- **场景**: 缓存结构升级
- **验证点**:
  - key 包含版本前缀（v1）
  - 版本升级时 bump 版本号（v1 → v2）
  - 旧版本 key 自然过期

---

### 10. 缓存失效测试（R13）

**测试类**: `CacheInvalidationIntegrationTest`（新建）

#### 10.1 SCAN 失效测试
- **场景**: 使用 SCAN 批量删除缓存
- **验证点**:
  - 不使用 KEYS 命令
  - 使用 SCAN 游标遍历
  - 分批删除（默认批次 100）
  - 不阻塞 Redis

#### 10.2 批量删除性能测试
- **场景**: 验证 SCAN 批次大小配置
- **验证点**:
  - scanBatchSize 可配置
  - 批量删除效率符合预期
  - 日志记录删除的 key 数量

#### 10.3 精确删除优先测试
- **场景**: 优先使用精确 key 删除
- **验证点**:
  - 能精确定位 key 时不使用 pattern
  - pattern 删除时记录 warning 日志
  - 文档说明优先使用精确删除

---

### 11. Outbox 管理端测试（R14）

**测试类**: `OutboxAdminControllerIntegrationTest`（扩展现有测试）

#### 11.1 失败列表查询测试
- **场景**: 查询失败的 Outbox 事件
- **验证点**:
  - 只返回 status=FAILED 的事件
  - 支持按 eventType 过滤
  - 分页正确（page, size）
  - 边界处理正确（空列表、最后一页）

#### 11.2 手动重试测试
- **场景**: 运维人员手动重试失败事件
- **验证点**:
  - 重试成功后状态变为 PENDING
  - retry_count 重置为 0
  - 记录 operator_id 和 reason
  - 审计日志完整

#### 11.3 429 限频测试
- **场景**: 10 分钟内重复重试同一事件
- **验证点**:
  - 第一次重试成功
  - 10 分钟内第二次重试返回 HTTP 429
  - 错误消息说明限频原因
  - 日志记录 operator_id

#### 11.4 告警触发测试（Mock）
- **场景**: 事件失败达到阈值
- **验证点**:
  - 达到 max_retries 后触发告警
  - 告警内容包含 event_id、event_type、aggregate_id、retry_count、error_message
  - 限流生效（每分钟每 event_type 最多 10 条）
  - 可 Mock 告警通道验证调用

---

### 12. 标签并发测试（R18）

**测试类**: `TagConcurrencyIntegrationTest`（新建）

#### 12.1 标签差量更新测试
- **场景**: 验证使用差量更新而非全量替换
- **验证点**:
  - attach 只添加新标签
  - detach 只删除指定标签
  - 不影响其他标签
  - 使用版本控制

#### 12.2 标签并发冲突测试
- **场景**: 多线程并发更新同一文章的标签
- **验证点**:
  - 版本冲突时返回 HTTP 409
  - 错误码为 CONCURRENT_TAG_UPDATE
  - 响应包含 retry_suggested: true
  - 至少一个线程成功，其他线程收到 409

---

## P2 - 低优先级测试（Sprint 3）

### 13. 其他缺失测试

#### 13.1 作者信息更新测试（R5）
**测试类**: `PostAuthorInfoUpdateIntegrationTest`（新建）

- **场景**: 验证 @Update 注解修复后的正确性
- **验证点**:
  - updateAuthorInfo 使用 @Update 注解
  - 执行 UPDATE SQL 并返回 affected_rows
  - owner_name、owner_avatar_id、owner_profile_version 正确更新
  - 集成测试验证端到端流程

#### 13.2 发布时间映射测试（R9）
**测试类**: `PostDetailViewMappingTest`（单元测试 + 集成测试）

- **场景**: 验证 publishedAt 使用真实发布时间
- **验证点**:
  - PostDetailView 包含 publishedAt 字段
  - 映射使用 entity.getPublishedAt()
  - 不使用 createdAt 作为 publishedAt
  - 未发布文章 publishedAt 为 null
  - 已发布文章 publishedAt 为实际发布时间

#### 13.3 异步事件处理测试（R15）
**测试类**: `AsyncEventHandlerTest`（集成测试）

- **场景**: 验证 @EnableAsync 配置后事件异步执行
- **验证点**:
  - 事件处理器在独立线程执行
  - 线程名包含 "async-event-" 前缀
  - 不阻塞主请求链路
  - 线程池配置生效（core=8, max=16, queue=1000）

#### 13.4 标签 ID 生成测试（R16）
**测试类**: `TagIdGeneratorIntegrationTest`（新建）

- **场景**: 验证调用统一 ID 服务
- **验证点**:
  - 不使用本地 UUID 生成
  - 调用 IdGeneratorFeignClient
  - ID 服务不可用时返回 HTTP 503
  - 不提供本地降级
  - 熔断配置生效（超时、失败率阈值）

#### 13.5 标签批量创建失败测试（R17）
**测试类**: `TagBatchCreateTest`（集成测试）

- **场景**: 验证全成全败事务语义
- **验证点**:
  - 任意标签创建失败时回滚整个批次
  - 返回失败标签名和错误原因
  - 完整记录异常日志
  - 数据库中无部分成功的标签

---

### 14. 恢复已跳过的测试

**优先级**: 按核心链路优先恢复

#### 14.1 创建文章核心链路
- 恢复 `.skip` 标记的创建文章测试
- 验证 aggregateVersion 非空
- 验证 Outbox 事件正确写入

#### 14.2 发布/撤销发布
- 恢复发布相关测试
- 验证状态转换正确
- 验证 publishedAt 时间正确

#### 14.3 Outbox 投递
- 恢复 Outbox 投递测试
- 验证重试机制
- 验证失败处理

#### 14.4 点赞/收藏计数
- 恢复互动计数测试
- 验证计数不会为负数
- 验证 Redis 和 DB 一致性

---

## 测试执行计划

### Sprint 1（P0 - 2 周）
- Week 1: R1（定时发布）、R2/R3（点赞收藏仓储）
- Week 2: R6（乐观锁）、回归测试

### Sprint 2（P1 - 2 周）
- Week 3: R7/R8（列表参数）、R10（光标分页）
- Week 4: R11/R12/R13（缓存）、R14（Outbox 管理）

### Sprint 3（P2 - 2 周）
- Week 5: R15-R18（异步、标签）
- Week 6: 恢复 skip 测试、技术债清理

---

## 测试覆盖率目标

- **单元测试覆盖率**: ≥ 80%
- **集成测试覆盖率**: ≥ 70%
- **核心链路覆盖率**: 100%（创建、发布、点赞、收藏、定时发布、Outbox）

---

## 测试环境要求

- **基础设施**: Testcontainers（PostgreSQL、MongoDB、Redis）
- **并发测试**: 多线程测试框架（JUnit 5 + CountDownLatch）
- **Mock 框架**: Mockito（用于外部服务和告警通道）
- **断言库**: AssertJ（流式断言）

---

## 验收标准

每个测试场景必须满足：

1. **正向用例**: 验证正常流程正确执行
2. **异常用例**: 验证错误处理和边界条件
3. **可观测性**: 验证日志、指标、告警正确输出
4. **幂等性**: 验证重复操作不产生副作用
5. **并发安全**: 验证并发场景下数据一致性

---

## 相关文档

- [架构审阅与优化建议](../docs/02-架构审阅与优化建议.md)
- [集成测试场景清单](../docs/测试-集成测试场景.md)
- [集成测试结果记录](../docs/测试-集成测试结果.md)
- [需求文档](.kiro/specs/zhicore-content-architecture-fixes/requirements.md)
- [设计文档](.kiro/specs/zhicore-content-architecture-fixes/design.md)

---

**维护者**: 开发团队  
**审查频率**: 每个 Sprint 结束时更新进度
