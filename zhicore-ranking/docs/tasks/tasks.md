# zhicore-ranking 任务清单

## 概述

当前 zhicore-ranking 基于 Redis+MongoDB+本地缓冲区实现事件驱动热度计算，包含 ScoreBufferService、HotScoreCalculator、多个 MQ Consumer 和定时快照刷新。

架构文档已完成 review 修复（2026-02-26），共处理 19 项问题（6 高 / 9 中 / 4 低），主要修复包括：
- swap-and-flush 原子性保证（AtomicReference.getAndSet + 刷写失败补偿 + WAL 兜底）
- Redis 总榜改为快照全量刷新（非增量 ZINCRBY），Top 10000 淘汰机制，内存容量预估
- 负向事件处理（取消点赞 -5、取消收藏 -8、删除评论 -10）+ 防刷去重机制
- 缓冲区刷写只写 MongoDB，Redis 由快照任务统一全量刷新，消除双写不一致
- 刷写线程协调（ReentrantLock + Condition，单线程执行，阈值触发仅唤醒）
- 消费幂等性（业务唯一键去重 + Redis SET 15s TTL + 重试策略）
- 优雅停机完整设计（SmartLifecycle + 超时控制 + WAL + 最坏情况量化评估）
- Redis 存储 final_score（含衰减），消除快照刷新前后排名跳跃
- 创作者排行聚合逻辑（Top 50 文章 final_score 之和，快照后二次聚合）
- 话题排行聚合逻辑（Top 100 关联文章 final_score 之和，按 topicId 分组）
- 快照文档限制 Top 1000，远低于 16MB 上限
- 缓存防击穿（Redisson 分布式锁）、防穿透（空结果短 TTL）、防雪崩（TTL 随机偏移 + 逻辑过期）
- 快照生成分布式锁 + 失败重试机制
- 文章状态过滤（监听删除/下架/封禁事件，ZREM + status=INACTIVE）
- 浏览防刷（30min 用户级去重 + 单篇 5000 分上限）
- metadata 一致性策略（事件驱动异步更新 + 快照生成时批量拉取覆盖）
- rank 字段语义说明、错误码表、可观测性指标具体定义与告警阈值
- ADR-1 数据丢失量化评估（峰值 2000 QPS，5s 窗口对 Top 100 影响 < 2%）
- 技术选型版本号修正为实际 pom.xml 版本

主要代码差距在于优雅停机缺失、无可观测性指标、定时任务频率未验证、缺少限流保护。

## 任务列表

### TASK-RK-01: 优雅停机 Shutdown Hook 刷写缓冲区

- 优先级: P0
- 预估改动: `application/service/ScoreBufferService.java`、`RankingApplication.java`
- 依赖: 无

#### 描述

注册 Spring `@PreDestroy` 或 `SmartLifecycle` 钩子，在应用关闭时将 ScoreBufferService 内存缓冲区中的待刷写数据强制写入 Redis。需设置最大等待时间（10s），超时后记录 WARN 日志并放弃剩余数据。

#### 验收标准

- [ ] 应用正常关闭时，缓冲区数据全部刷写到 Redis
- [ ] 刷写超过 10s 超时后记录 WARN 日志并终止
- [ ] 刷写过程中新事件不再进入缓冲区
- [ ] 单元测试模拟关闭流程，验证缓冲区清空

#### 回滚策略

移除 Shutdown Hook 注册代码，恢复原有行为（关闭时缓冲区数据丢失）。

#### 建议 commit 拆分

- `feat(ranking): 添加优雅停机 Shutdown Hook 刷写缓冲区`

---

### TASK-RK-02: 接入 Micrometer 指标

- 优先级: P0
- 预估改动: `application/service/ScoreBufferService.java`、`application/service/RankingQueryService.java`、`infrastructure/scheduler/RankingRefreshScheduler.java`
- 依赖: 无

#### 描述

接入以下 Micrometer 指标：
- `ranking.buffer.size`（Gauge，当前缓冲区大小）
- `ranking.buffer.flush.duration`（Timer，刷写耗时）
- `ranking.buffer.flush.count`（Counter，刷写次数）
- `ranking.cache.hit` / `ranking.cache.miss`（排行榜缓存命中率）
- `ranking.snapshot.duration`（Timer，快照生成耗时）

#### 验收标准

- [ ] `/actuator/prometheus` 可查询上述全部指标
- [ ] 缓冲区大小实时反映当前待刷写条目数
- [ ] 刷写耗时包含 p50/p90/p99 百分位
- [ ] 缓存命中/未命中分别递增

#### 回滚策略

移除指标埋点代码，不影响业务逻辑。

#### 建议 commit 拆分

- `feat(ranking): 接入缓冲区大小/刷写耗时 Micrometer 指标`
- `feat(ranking): 接入缓存命中率与快照生成耗时指标`

---

### TASK-RK-03: 快照生成定时任务频率验证

- 优先级: P1
- 预估改动: `infrastructure/scheduler/RankingRefreshScheduler.java`、`infrastructure/config/RankingProperties.java`
- 依赖: 无

#### 描述

验证 RankingRefreshScheduler 的定时任务执行频率是否符合架构设计（热榜 5 分钟刷新、归档快照每日一次）。将频率配置外部化到 RankingProperties，支持 Nacos 动态调整。

#### 验收标准

- [ ] 热榜刷新频率默认 5 分钟，可通过配置调整
- [ ] 归档快照频率默认每日 02:00，可通过配置调整
- [ ] 定时任务执行时记录 INFO 日志（含耗时）
- [ ] 配置变更后无需重启即可生效

#### 回滚策略

恢复硬编码的定时频率。

#### 建议 commit 拆分

- `refactor(ranking): 快照定时任务频率外部化配置`

---

### TASK-RK-04: 接入 Sentinel 限流

- 优先级: P1
- 预估改动: `interfaces/controller/RankingController.java`
- 依赖: 无

#### 描述

对排行榜查询接口配置 Sentinel 限流，防止高并发场景下缓存穿透。热榜查询 1000 QPS，创作者/话题排行 500 QPS。

#### 验收标准

- [ ] 热榜查询超过 1000 QPS 时返回 429
- [ ] 创作者/话题排行超过 500 QPS 时返回 429
- [ ] 限流规则支持 Nacos 动态推送
- [ ] 限流触发时记录 WARN 日志

#### 回滚策略

移除 Sentinel 注解和配置，限流失效但业务正常。

#### 建议 commit 拆分

- `feat(ranking): 接入 Sentinel 排行榜查询限流`
