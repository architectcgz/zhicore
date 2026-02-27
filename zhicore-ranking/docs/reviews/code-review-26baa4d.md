# 代码 Review：26baa4d fix(ranking): 修复第二轮 code review 的 6 项问题

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit 26baa4d 涉及的 5 个文件（84+/22-） |
| 审查基准 | 上次 review（code-review-fb6e79d.md）的 6 项新问题 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 上次问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 说明 |
|------|------|---------|------|------|
| 新-1 | 高 | addScore() 热路径每次 Counter.builder().register() | ✅ 已修复 | 改用 ConcurrentHashMap 缓存 Counter 实例 |
| 新-2 | 高 | 阈值触发 flush() 同步阻塞消费线程 | ✅ 已修复 | 改用 tryLock() 非阻塞尝试 |
| 新-3 | 中 | 缓存 miss 回源后未回填 Redis | ✅ 已修复 | 新增 backfillRedis() 方法 |
| 新-4 | 中 | snapshotTimer() 每次调用都 register | ✅ 已修复 | 构造函数中预注册三个 Timer 字段 |
| 新-5 | 低 | @ConfigurationProperties 动态刷新需确认版本 | ✅ 已标注 | 注释明确 Spring Cloud Alibaba 2023.0.1.0 |
| 新-6 | 低 | batchSize=MAX_VALUE 导致阈值触发未被测试 | ✅ 已修复 | 新增 testThresholdTrigger_ShouldAutoFlush |

6 项全部修复，修复质量良好。

---

## 本次修复引入的新问题

### 新问题 1：backfillRedis() 并发回填缺少防击穿互斥

- 级别：**中**
- 涉及文件：`RankingQueryService.java:101-114`
- 问题描述：当 Redis 中某月份数据为空时，多个并发请求同时 miss，都会回源 MongoDB 并各自执行 `backfillRedis()`。架构文档（02-核心功能设计 5 节）要求"miss 时使用 Redisson 分布式锁，同一排行维度只允许一个请求回源 MongoDB，其余请求等待锁释放后读取已回填的缓存"
- 影响：瞬时高并发下多个请求同时穿透到 MongoDB，回填操作也重复执行
- 修正建议：在回源前加分布式锁（如 `ranking:lock:load:monthly:{year}-{month}`），获取锁的请求回源+回填，其余等待后读 Redis

### 新问题 2：backfillRedis() 回填的是 limit 截断后的部分数据，非完整月榜

- 级别：**中**
- 涉及文件：`RankingQueryService.java:76-78`
- 问题描述：`archiveService.getMonthlyArchive("post", year, month, limit)` 返回的是按 limit 截断的结果（比如 Top 20）。回填到 Redis 后，Sorted Set 中只有这 20 条数据。后续如果有请求查询 limit=50，Redis 中只有 20 条，返回结果不完整但不为空，会被判定为 cache hit
- 影响：不同 limit 的查询可能拿到不完整的缓存数据
- 修正建议：
  1. 回填时不受 limit 限制，从 MongoDB 拉取完整月榜数据（或 Top N 上限，如 1000）
  2. 或者在 Redis 中记录回填时的数据量，查询时判断 limit > 已缓存数量则回源

### 新问题 3：backfillRedis() 逐条 setScore 非原子，存在短暂数据不完整窗口

- 级别：**低**
- 涉及文件：`RankingQueryService.java:107-109`
- 问题描述：回填使用 for 循环逐条 `setScore()`，写入过程中如果有查询请求命中该 key，会读到部分数据（比如 20 条中只写了 5 条）。架构文档提到总榜全量刷新应使用 Pipeline 或 Lua 保证原子性，回填场景同理
- 影响：回填窗口期内查询结果不完整，概率较低但存在
- 修正建议：使用 Redis Pipeline 批量写入，或先写到临时 key 再 RENAME

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 新-1 | 中 | backfillRedis() 并发回填缺少防击穿互斥 | RankingQueryService |
| 新-2 | 中 | 回填的是 limit 截断后的部分数据 | RankingQueryService |
| 新-3 | 低 | 逐条 setScore 非原子，存在短暂数据不完整窗口 | RankingQueryService |

| 级别 | 数量 |
|------|------|
| 高 | 0 |
| 中 | 2 |
| 低 | 1 |

---

## 总体评价

经过三轮 review，代码质量有明显提升。本轮 6 项问题全部修复到位，没有引入高级问题。剩余 3 个新问题集中在 `backfillRedis()` 的边界场景，严重性较低，可在后续迭代中处理。

三轮 review 问题趋势：11 → 6 → 3，高级问题：3 → 2 → 0，收敛良好。

---

*审查完成于 2026-02-27*
