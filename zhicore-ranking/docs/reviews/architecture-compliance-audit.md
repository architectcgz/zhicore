# 架构完整性审计：代码 vs 架构文档

## 审计信息

| 项目 | 内容 |
|------|------|
| 审计范围 | zhicore-ranking 全部 Java 源文件（master 分支） |
| 审计基准 | `01-架构总览与设计决策.md`、`02-核心功能设计.md`、`architecture-review-2026-02-26.md` |
| 审计日期 | 2026-02-27 |
| 审计人 | Architecture Review (Claude Opus 4.6) |

---

## 一、已实现功能清单

| 架构文档要求 | 状态 | 说明 |
|-------------|------|------|
| 热度权重公式 | ✅ | `HotScoreCalculator` + `RankingWeightProperties` |
| 时间衰减半衰期 7 天 | ✅ | `calculateTimeDecay()` |
| 权重可配置 + Nacos 动态刷新 | ✅ | `@ConfigurationProperties` |
| 本地聚合缓冲区 swap-and-flush | ✅ | `AtomicReference<ConcurrentHashMap>` |
| 缓冲区双触发（5s + 1000 条） | ✅ | `BufferFlushScheduler` + `tryLock` |
| ReentrantLock 单线程刷写 | ✅ | `flushLock` |
| 刷写失败 merge 回缓冲区 | ✅ | `doFlushSnapshot()` 失败分支 |
| 连续失败计数告警 | ✅ | `consecutiveFailureCount` |
| RocketMQ 事件消费 | ✅ | 5 个 Consumer |
| 负向事件（取消点赞） | ✅ | `PostUnlikedRankingConsumer` |
| 消费幂等性 | ✅ | `StatefulIdempotentHandler` |
| Redis Sorted Set 缓存 | ✅ | `RankingRedisRepository` |
| Lua 脚本原子更新多榜 | ✅ | `INCREMENT_SCRIPT` |
| Redis Key 规范命名 | ✅ | `RankingRedisKeys` |
| 日/周/月榜 TTL 过期 | ✅ | Lua EXPIRE |
| API 6 端点 | ✅ | `RankingController` |
| 月榜智能路由 | ✅ | `RankingQueryService` |
| 分布式锁防击穿 + DCL | ✅ | Redisson `tryLock` |
| Pipeline+RENAME 原子回填 | ✅ | `batchSetScoreAtomic()` |
| MongoDB 归档 | ✅ | `RankingArchiveService` |
| MongoDB 复合索引 | ✅ | `@CompoundIndexes` |
| 创作者热度计算 | ✅ | `calculateCreatorHotScore()` |
| Micrometer 指标 | ✅ | Counter/Timer/Gauge |
| 优雅停机 | ✅ | `ScoreBufferLifecycle` |
| 过期数据清理 | ✅ | `RankingRefreshScheduler` |

---

## 二、未实现 / 偏离架构文档的功能（高级）

### 高-1：缓冲区刷写目标偏离——写 Redis 而非 MongoDB

- 架构要求：缓冲区刷写到 MongoDB（权威数据源），Redis 由快照任务全量刷新（02 文档 2.3 节）
- 代码现状：`ScoreBufferService.doFlushSnapshot()` 直接写 Redis（ZINCRBY）
- 代码标记：`ScoreBufferService.java:33-34` 有 TODO
- 影响：Redis 故障时缓冲数据永久丢失；MongoDB 无实时热度增量
- 状态：已知偏离，待 MongoDB 持久化层就绪后迁移

### 高-2：WAL 兜底机制完全缺失

- 架构要求：连续失败 3 次后写入本地磁盘 WAL，启动时自动恢复（02 文档 2.3 节）
- 代码现状：`ScoreBufferService.java:214-217` 仅记录 ERROR 日志
- 代码标记：`ScoreBufferService.java:36-37` 有 TODO
- 影响：宕机时缓冲区数据完全丢失，无恢复机制
- 状态：已知缺失，待实现

### 高-3：浏览事件防刷策略未实现

- 架构要求：同一用户同一文章 30 分钟内只计一次；单篇浏览上限 5000 分；匿名用户 IP+UA 指纹去重
- 代码现状：`PostViewedRankingConsumer` 仅做消息级幂等（`tryProcess`），无用户级去重、无分数上限
- 涉及文件：`PostViewedRankingConsumer.java:41-69`
- 影响：可通过重复浏览刷高文章热度

---

## 三、未实现 / 偏离架构文档的功能（中级）

### 中-1：负向事件覆盖不完整——缺少取消收藏和删除评论

- 架构要求：取消收藏（-8）、删除评论（-10）、1 小时时间窗口防刷去重
- 代码现状：仅实现 `PostUnlikedRankingConsumer`（-5），无取消收藏、无删除评论消费者
- 影响：用户取消收藏或删除评论后热度不递减

### 中-2：负向事件时间衰减不对称

- 架构要求：取消互动应与原始互动使用相同的衰减计算
- 代码现状：`PostUnlikedRankingConsumer.java:57` 传入 `null` 不应用时间衰减，但点赞时应用了衰减
- 影响：取消点赞扣减的分数（原始权重）大于点赞增加的分数（衰减后权重），导致热度计算不对称

### 中-3：快照任务职责偏离——仅清理过期数据，无全量快照生成

- 架构要求：总榜 5 分钟全量刷新，日/周/月榜 1 小时快照生成（02 文档 3.1 节）
- 代码现状：`RankingRefreshScheduler` 三个定时任务仅执行 `cleanupExpired*` 清理过期 key，无快照生成逻辑
- 涉及文件：`RankingRefreshScheduler.java:58-96`
- 影响：架构文档要求的"从 MongoDB 全量重建 Redis 缓存"机制不存在

### 中-4：创作者/话题排行聚合逻辑缺失

- 架构要求：创作者排行 = 该创作者 Top 50 篇文章 final_score 之和；话题排行 = 该话题 Top 100 篇文章之和
- 代码现状：创作者热度通过 MQ 消费者逐条增量累加（每次互动事件 +delta），无"聚合该创作者所有文章分数"的逻辑
- 涉及文件：`PostLikedRankingConsumer.java:62`、`CreatorRankingService.java`
- 影响：创作者排行仅反映增量互动，不反映文章综合热度

### 中-5：缓存穿透防护缺失——无空结果缓存

- 架构要求：空结果缓存 30s 防穿透（02 文档 5.1 节）
- 代码现状：`RankingQueryService.loadAndBackfill()` MongoDB 返回空时不缓存，下次请求仍回源
- 涉及文件：`RankingQueryService.java:109-151`
- 影响：不存在的月份被反复查询时，每次都穿透到 MongoDB

### 中-6：快照生成缺少分布式锁

- 架构要求：快照生成使用分布式锁确保单实例执行（02 文档 5.2 节）
- 代码现状：`RankingRefreshScheduler` 和 `RankingArchiveService` 的 `@Scheduled` 无分布式锁
- 影响：多实例部署时同一快照/归档任务被重复执行

### 中-7：文章状态过滤未实现

- 架构要求：监听文章状态变更事件，从 Redis ZREM 移除；快照生成时过滤非正常状态文章（02 文档 5.4 节）
- 代码现状：无文章状态变更消费者，`PostRankingService.removePost()` 存在但无调用方
- 影响：已删除/下架文章仍出现在排行榜中

---

## 四、未实现 / 偏离架构文档的功能（低级）

### 低-1：Sentinel 限流未实现

- 架构要求：单接口 500 QPS 限流（02 文档 5.3 节）
- 代码现状：`RankingController` 无 `@SentinelResource` 注解，无限流配置
- 影响：高并发下无保护

### 低-2：收藏/评论事件未应用时间衰减

- 架构要求：所有互动事件基于文章发布时间衰减
- 代码现状：`PostFavoritedRankingConsumer.java:57` 和 `CommentCreatedRankingConsumer.java:58` 传入 `null`，不衰减
- 影响：老文章的收藏/评论权重与新文章相同，轻微失真

### 低-3：Redis 总榜无淘汰机制

- 架构要求：总榜只存 Top 10000，定期 ZREMRANGEBYRANK 清理（02 文档 4.2 节）
- 代码现状：`ranking:posts:hot` 无成员上限，无定期清理
- 影响：随文章增长 Sorted Set 无限膨胀

### 低-4：可观测性指标不完整

- 架构要求：去重拦截数、链路追踪（02 文档 5.5 节）
- 代码现状：无去重拦截计数指标，无 Sleuth/Micrometer Tracing 集成
- 影响：排查问题时缺少关键指标

---

## 五、问题汇总

| 编号 | 级别 | 问题标题 | 状态 |
|------|------|---------|------|
| 高-1 | 高 | 缓冲区刷写目标偏离（Redis→MongoDB） | TODO 已标记 |
| 高-2 | 高 | WAL 兜底机制缺失 | TODO 已标记 |
| 高-3 | 高 | 浏览事件防刷策略未实现 | 未实现 |
| 中-1 | 中 | 缺少取消收藏/删除评论消费者 | 未实现 |
| 中-2 | 中 | 负向事件时间衰减不对称 | 实现偏差 |
| 中-3 | 中 | 快照任务仅清理，无全量生成 | 实现偏差 |
| 中-4 | 中 | 创作者/话题聚合逻辑缺失 | 未实现 |
| 中-5 | 中 | 缓存穿透防护缺失 | 未实现 |
| 中-6 | 中 | 快照/归档缺少分布式锁 | 未实现 |
| 中-7 | 中 | 文章状态过滤未实现 | 未实现 |
| 低-1 | 低 | Sentinel 限流未实现 | 未实现 |
| 低-2 | 低 | 收藏/评论未应用时间衰减 | 实现偏差 |
| 低-3 | 低 | Redis 总榜无淘汰机制 | 未实现 |
| 低-4 | 低 | 可观测性指标不完整 | 部分实现 |

| 级别 | 数量 |
|------|------|
| 高 | 3 |
| 中 | 7 |
| 低 | 4 |

---

## 六、总体评价

架构文档定义的 25+ 项核心功能中，约 70% 已实现且质量良好。

主要差距集中在三个方面：

1. **数据持久化层未就绪**（高-1、高-2）：缓冲区直接写 Redis 而非 MongoDB，WAL 缺失。这两项已有 TODO 标记，是已知的技术债务，需要 MongoDB 持久化层（`ranking_scores` 集合）就绪后才能迁移。

2. **防刷/安全机制缺失**（高-3、中-1、中-7）：浏览去重、负向事件完整覆盖、文章状态过滤均未实现。这些直接影响排行榜数据的准确性和安全性。

3. **快照/聚合逻辑不完整**（中-3、中-4、中-6）：快照任务仅做清理不做生成，创作者/话题聚合逻辑缺失，定时任务无分布式锁。

建议优先级：
- **P0**：高-1（刷写目标）、高-2（WAL）、高-3（浏览防刷）
- **P1**：中-1 ~ 中-7
- **P2**：低-1 ~ 低-4

---

*审计完成于 2026-02-27*
