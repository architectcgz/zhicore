# 代码 Review：92d9e9d feat(ranking): 按架构文档重构缓冲区、计算器、调度器，补齐 Micrometer 指标

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit 92d9e9d 涉及的 9 个文件（661+/538-） |
| 审查基准 | docs/architecture/01-架构总览与设计决策.md、02-核心功能设计.md |
| 审查日期 | 2026-02-26 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

## 审查标准

- 高：与架构文档的偏离/违规、并发安全缺陷、数据丢失风险
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：代码风格、命名、冗余

---

## 高级问题

### 问题 1：ScoreBufferService 刷写目标与架构文档不一致——实现写 Redis，文档要求写 MongoDB

- 级别：**高**
- 涉及文件：`ScoreBufferService.java:doFlushSnapshot()`
- 架构文档要求（02-核心功能设计 2.3 节）：
  > "刷写操作：取出并清空 Map，批量写入 MongoDB（权威数据源），upsert 各文章的 raw_score 增量。Redis 排行数据不在此阶段更新，统一由快照生成任务全量刷新"
- 实际实现：`doFlushSnapshot()` 调用的是 `rankingRedisRepository.incrementPostScore()` / `incrementCreatorScore()` / `incrementTopicScore()`，直接 ZINCRBY 写 Redis Sorted Set，完全没有写 MongoDB
- 影响：
  1. 违反"Redis 中始终存储含衰减的 final_score"的设计——缓冲区刷写的是未衰减的 raw_score 增量，直接 ZINCRBY 到 Redis 会导致未衰减分数与已衰减分数混合累加，排名失真
  2. MongoDB 作为权威数据源没有收到任何增量数据，快照生成时无法从 MongoDB 读取最新 raw_score
  3. 服务重启后 Redis 数据丢失，MongoDB 中无增量记录，数据永久丢失
- 修正建议：
  1. 刷写目标改为 MongoDB（upsert raw_score 增量），移除对 Redis 的直接写入
  2. Redis 排行数据由快照生成任务统一全量刷新（读 MongoDB → 计算 final_score → ZADD）
  3. 如果当前阶段 MongoDB 持久化层尚未就绪，应在代码中标注 TODO 并在任务清单中跟踪

### 问题 2：ReentrantLock + Condition 声明了但未实际用于刷写线程协调

- 级别：**高**
- 涉及文件：`ScoreBufferService.java`、`BufferFlushScheduler.java`
- 架构文档要求（02-核心功能设计 2.3 节）：
  > "只有一个专用刷写线程负责执行 swap-and-flush。定时器到期和阈值触发均通过 signal() 唤醒该线程"
- 实际实现：
  - `flushLock` 和 `flushCondition` 在 `ScoreBufferService` 中声明了，但 `signalFlush()` 方法中的 `flushCondition.signal()` 没有任何线程在 `await()` 等待
  - `BufferFlushScheduler` 通过 `@Scheduled` 定时调用 `scoreBufferService.flush()`，是 Spring 调度线程直接执行刷写，不是"专用刷写线程被唤醒"
  - `addScore()` 中阈值触发调用 `signalFlush()`，但 signal 发出后无人接收，阈值触发实际上是空操作
- 影响：阈值触发刷写（1000 条）完全失效，只有定时刷写（5s）在工作。高峰期 5s 内可能积累远超 1000 条事件，刷写延迟增大
- 修正建议：
  1. 方案 A（推荐，简单）：移除 Condition 机制，阈值触发时直接调用 `flush()`（flushLock 已保证互斥）
  2. 方案 B（符合文档）：启动一个专用刷写线程在 `flushCondition.await()` 上阻塞，定时器和阈值触发均通过 `signal()` 唤醒

### 问题 3：WAL 兜底机制完全缺失

- 级别：**高**
- 涉及文件：`ScoreBufferService.java`、`ScoreBufferLifecycle.java`
- 架构文档要求（02-核心功能设计 2.3 节）：
  > "连续失败 3 次后写入本地磁盘 WAL 文件（data/ranking-wal/），服务启动时自动检查并恢复未完成的刷写"
  > "swap 后的待刷写数据先写入本地磁盘 WAL 文件，刷写成功后删除 WAL"
- 实际实现：
  - 刷写失败只做了 merge 回缓冲区，没有连续失败计数
  - 没有 WAL 文件写入逻辑
  - 没有启动时 WAL 恢复逻辑
  - `ScoreBufferLifecycle` 优雅停机时也没有 WAL 写入
- 影响：连续刷写失败时数据在内存中反复 merge，如果此时服务被终止，数据永久丢失。SIGKILL 场景下无任何持久化兜底
- 修正建议：
  1. 当前阶段如果不实现 WAL，至少在代码中标注 `// TODO: WAL 兜底（见架构文档 2.3 节）`
  2. 增加连续失败计数器，连续失败 N 次后记录 ERROR 日志并触发告警
  3. 在任务清单中单独跟踪 WAL 实现

---

## 中级问题

### 问题 4：RankingQueryService 缓存命中/未命中指标语义错误

- 级别：**中**
- 涉及文件：`RankingQueryService.java`
- 问题描述：代码将"查询日期在 Redis TTL 范围内 → 走 Redis"记为 `cacheHitCounter`，"超出范围 → 走 MongoDB"记为 `cacheMissCounter`。但这不是缓存命中/未命中——这是基于日期的路由策略。真正的 cache hit/miss 应该是：查询 Redis 有数据 = hit，Redis 无数据回源 MongoDB = miss。当前实现下，即使 Redis 中该月份的数据已过期或不存在，只要日期在 365 天内就会被计为 hit
- 影响：监控指标失真，无法反映真实的缓存命中率，告警阈值（命中率 < 90%）形同虚设
- 修正建议：在实际查询 Redis 后根据返回结果是否为空来判断 hit/miss

### 问题 5：HotScoreCalculator 使用 @Value 注入无法实现 Nacos 动态刷新

- 级别：**中**
- 涉及文件：`HotScoreCalculator.java`
- 问题描述：commit message 声称"权重改为 @Value 构造器注入，支持 Nacos 动态刷新"，但 `@Value` 注入的值在 Bean 构造时就已固定为 final 字段。Nacos 配置变更后，Spring 不会重新创建这个 Bean，权重值不会更新。要实现动态刷新需要 `@RefreshScope` + `@Value`（字段注入），或使用 `@ConfigurationProperties` Bean
- 影响：运维通过 Nacos 调整权重后不生效，必须重启服务
- 修正建议：
  1. 方案 A：给 `HotScoreCalculator` 加 `@RefreshScope`，改为字段注入（注意 `@RefreshScope` 会导致 Bean 被代理，有性能开销）
  2. 方案 B（推荐）：将权重配置收入 `RankingWeightProperties`（`@ConfigurationProperties`），计算时从 properties Bean 实时读取

### 问题 6：RankingRefreshScheduler 只对 refreshHotPosts 接入了 Timer，其余两个定时任务未接入

- 级别：**中**
- 涉及文件：`RankingRefreshScheduler.java`
- 问题描述：`refreshHotPosts()` 使用 `snapshotTimer.record()` 包裹，但 `refreshCreatorRanking()` 和 `refreshTopicRanking()` 仍然手动计算 `System.currentTimeMillis()` 差值。三个定时任务的监控方式不一致
- 架构文档要求（02-核心功能设计 5 节）：`ranking_snapshot_duration_seconds`（Timer）按 `type` 标签区分
- 影响：创作者和话题排行的快照耗时没有进入 Micrometer，Grafana 看不到这两个维度的 P99
- 修正建议：三个定时任务统一使用 Timer，通过 `tag("type", "post"/"creator"/"topic")` 区分

### 问题 7：ScoreBufferService 的 eventConsumeCounter 缺少 eventType 标签

- 级别：**中**
- 涉及文件：`ScoreBufferService.java`
- 架构文档要求（02-核心功能设计 5 节）：`ranking_event_consume_total`（Counter）按 `eventType` 标签区分
- 实际实现：`eventConsumeCounter` 只有一个无标签的 Counter，无法区分浏览/点赞/评论/收藏等事件类型
- 影响：无法按事件类型分析消费量分布，排查问题时缺少维度
- 修正建议：`addScore()` 方法需要接收 eventType 参数，或根据 entityType 打标签

### 问题 8：HotPostDetailService 将 getAvatarUrl 改为 getAvatarId，语义变更需确认

- 级别：**中**
- 涉及文件：`HotPostDetailService.java:93`
- 问题描述：原代码 `.ownerAvatar(post.getAuthor().getAvatarUrl())` 改为 `.ownerAvatar(post.getAuthor().getAvatarId())`。`ownerAvatar` 字段名暗示存储的是头像 URL（前端可直接渲染），但改为 avatarId 后前端需要额外拼接 CDN 地址才能展示。如果下游 API 消费者期望的是完整 URL，这是一个破坏性变更
- 影响：前端排行榜页面头像可能无法正常显示
- 修正建议：确认 `ownerAvatar` 字段的契约——如果 API 约定返回 URL，应在此处拼接完整 CDN 地址；如果约定返回 ID，需同步更新 API 文档和前端

---

## 低级问题

### 问题 9：Micrometer 指标命名不符合 Prometheus 惯例

- 级别：**低**
- 涉及文件：`ScoreBufferService.java`、`RankingQueryService.java`、`RankingRefreshScheduler.java`
- 问题描述：当前指标名使用点号分隔（如 `ranking.buffer.flush.total`、`ranking.cache.hit.total`），Micrometer 会自动转换为下划线，但架构文档定义的名称是 `ranking_buffer_flush_total`、`ranking_cache_hit_total`。虽然 Micrometer 会自动做转换，但 `.total` 后缀在 Prometheus 中会被自动追加 `_total`，导致最终指标名变成 `ranking_buffer_flush_total_total`
- 修正建议：Counter 类型的指标名去掉 `.total` 后缀，Prometheus 会自动追加

### 问题 10：RankingRefreshScheduler 文件末尾缺少换行符

- 级别：**低**
- 涉及文件：`RankingRefreshScheduler.java`
- 问题描述：diff 显示文件末尾 `}` 后没有换行（`\ No newline at end of file`），不符合 POSIX 文本文件规范，部分工具（如 `git diff`）会持续提示
- 修正建议：文件末尾补一个换行

### 问题 11：ScoreBufferServiceTest 缺少对 flushTimer.record() 返回值的验证

- 级别：**低**
- 涉及文件：`ScoreBufferServiceTest.java`
- 问题描述：`doFlush()` 中 `flushTimer.record(() -> doFlushSnapshot(snapshot))` 的返回值依赖 `Supplier` 版本的 `record()`，但实际传入的是 `Supplier<Integer>`。`SimpleMeterRegistry` 下能正常工作，但测试没有验证 Timer 指标是否被正确记录（如 `timer.count()` 是否递增）
- 修正建议：可选——在关键测试中断言 `meterRegistry.get("ranking.buffer.flush.duration").timer().count()` 递增

---

## 做得好的地方

1. **AtomicReference swap-and-flush**：`bufferRef.getAndSet(new ConcurrentHashMap<>())` 原子替换实现正确，消除了旧版 `sumThenReset()` 遍历期间的竞态窗口
2. **刷写失败补偿**：`doFlushSnapshot()` 中逐条 try-catch + 失败条目 merge 回缓冲区的设计合理，比旧版的全量 catch 粒度更细
3. **SmartLifecycle 优雅停机**：phase = MAX_VALUE - 1 确保在其他 Bean 销毁前执行，`stopAccepting()` → `flush()` → 检查残留的三步流程清晰
4. **测试覆盖**：新增了 swap 原子性、部分失败补偿、stopAccepting 拒绝新事件、事件计数器等针对性测试，覆盖了核心并发场景
5. **Micrometer 指标体系**：buffer flush/success/failure Counter、flush Timer、buffer size Gauge、event consume Counter、cache hit/miss Counter 基本覆盖了架构文档要求的可观测性维度

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 1 | 高 | 刷写目标写 Redis 而非 MongoDB，与架构文档不一致 | ScoreBufferService |
| 2 | 高 | ReentrantLock + Condition 未实际用于刷写线程协调，阈值触发失效 | ScoreBufferService, BufferFlushScheduler |
| 3 | 高 | WAL 兜底机制完全缺失 | ScoreBufferService, ScoreBufferLifecycle |
| 4 | 中 | 缓存命中/未命中指标语义错误（路由 ≠ 命中） | RankingQueryService |
| 5 | 中 | @Value 构造器注入无法实现 Nacos 动态刷新 | HotScoreCalculator |
| 6 | 中 | 只有 refreshHotPosts 接入 Timer，其余两个定时任务未接入 | RankingRefreshScheduler |
| 7 | 中 | eventConsumeCounter 缺少 eventType 标签 | ScoreBufferService |
| 8 | 中 | getAvatarUrl → getAvatarId 语义变更需确认 | HotPostDetailService |
| 9 | 低 | Counter 指标名带 .total 后缀，Prometheus 会重复追加 _total | 多个文件 |
| 10 | 低 | RankingRefreshScheduler 文件末尾缺少换行符 | RankingRefreshScheduler |
| 11 | 低 | 测试未验证 Timer 指标是否被正确记录 | ScoreBufferServiceTest |

| 级别 | 数量 |
|------|------|
| 高 | 3 |
| 中 | 5 |
| 低 | 3 |

---

*审查完成于 2026-02-27*

