# 代码 Review：fix-ranking-audit 分支（7 个提交，修复架构审计问题）

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | fix-ranking-audit 分支 7 个提交（526+/122-，15 文件） |
| 审查基准 | `architecture-compliance-audit.md` 中 14 项问题 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 审计问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 说明 |
|------|------|---------|------|------|
| 高-1 | 高 | 缓冲区刷写目标偏离 | ⏳ 未修复 | 已知 TODO，不在本次范围 |
| 高-2 | 高 | WAL 兜底机制缺失 | ⏳ 未修复 | 已知 TODO，不在本次范围 |
| 高-3 | 高 | 浏览事件防刷策略 | ✅ 已修复 | 用户级去重 + 分数上限 |
| 中-1 | 中 | 缺少取消收藏/删除评论 | ✅ 已修复 | 新增两个消费者 |
| 中-2 | 中 | 负向事件时间衰减不对称 | ✅ 已修复 | 统一移除增量衰减 |
| 中-3 | 中 | 快照任务仅清理无生成 | ⏳ 未修复 | 不在本次范围 |
| 中-4 | 中 | 创作者/话题聚合逻辑缺失 | ⏳ 未修复 | 不在本次范围 |
| 中-5 | 中 | 缓存穿透防护缺失 | ✅ 已修复 | 空结果缓存 30s |
| 中-6 | 中 | 快照/归档缺少分布式锁 | ✅ 已修复 | Redisson tryLock |
| 中-7 | 中 | 文章状态过滤未实现 | ⏳ 未修复 | 不在本次范围 |
| 低-1 | 低 | Sentinel 限流未实现 | ⏳ 未修复 | 不在本次范围 |
| 低-2 | 低 | 收藏/评论未应用时间衰减 | ✅ 已修复 | 统一移除增量衰减 |
| 低-3 | 低 | Redis 总榜无淘汰机制 | ✅ 已修复 | trimSortedSet Top 10000 |
| 低-4 | 低 | 可观测性指标不完整 | 🔶 部分修复 | 新增 viewDedupCounter |

本次修复 7/14 项，剩余 7 项为已知 TODO 或不在本次范围。

---

## 本次修复引入的新问题

### 新-1：incrementViewScoreWithCap() 存在竞态条件（check-then-act 非原子）

- 级别：**高**
- 涉及文件：`RankingRedisRepository.java:458-470`
- 问题描述：`get` 当前分数 → 判断是否超限 → `set` 新分数，三步操作非原子。并发场景下两个线程同时读到 `currentScore=4999`，都判断未超限，各加 1 分，最终分数变为 5001，突破上限。
- 影响：高并发浏览场景下分数上限失效
- 修正建议：改用 Lua 脚本实现原子 check-and-increment：

```lua
local key = KEYS[1]
local delta = tonumber(ARGV[1])
local cap = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', key) or '0')
if current >= cap then return 0 end
local allowed = math.min(delta, cap - current)
redis.call('SET', key, current + allowed)
return allowed
```

### 新-2：viewScoreCap key 无 TTL，永不过期

- 级别：**中**
- 涉及文件：`RankingRedisRepository.java:468`
- 问题描述：`redisTemplate.opsForValue().set(key, currentScore + allowedDelta)` 未设置过期时间。每篇文章都会产生一个 `ranking:view:cap:{postId}` key，永不过期，随文章数量增长 Redis 内存持续膨胀
- 影响：Redis 内存泄漏
- 修正建议：设置合理 TTL（如 30 天或与文章生命周期对齐），或在 Lua 脚本中一并设置

### 新-3：PostUnfavoritedRankingConsumer 未同步减少创作者热度

- 级别：**中**
- 涉及文件：`PostUnfavoritedRankingConsumer.java:42-48`
- 问题描述：取消收藏时仅减少文章热度，未减少作者的创作者热度。对比 `PostFavoritedRankingConsumer` 收藏时同时更新了创作者热度（`incrementCreatorScore`），取消收藏应对称扣减
- 影响：创作者热度只增不减（收藏维度），排行失真
- 修正建议：补充 `incrementCreatorScore(authorId, -scoreCalculator.getFavoriteDelta())`，与收藏事件对称

### 新-4：trimSortedSet() 的 zCard + removeRange 非原子

- 级别：**中**
- 涉及文件：`RankingRedisRepository.java:481-489`
- 问题描述：`zCard` 获取大小后、`removeRange` 执行前，如果有新成员写入，实际移除的范围可能偏移。极端情况下可能误删刚写入的高分成员
- 影响：低概率误删，因为淘汰任务每小时执行一次且总榜写入频率相对低，实际风险可控
- 修正建议：可选——改用 Lua 脚本合并为原子操作。当前实现在实际场景下风险可忽略

### 新-5：匿名用户浏览去重未实现

- 级别：**低**
- 涉及文件：`PostViewedRankingConsumer.java:68-75`
- 问题描述：`if (event.getUserId() != null)` 仅对登录用户做去重，匿名用户（userId 为 null）直接跳过去重检查。架构文档要求匿名用户按 IP+UserAgent 指纹去重
- 影响：匿名用户可无限刷浏览量（受分数上限 5000 约束，但去重失效）
- 修正建议：从事件中提取 IP+UA 生成指纹 hash，作为匿名用户的去重 key

### 新-6：RankingRefreshScheduler 与 RankingArchiveService 的 executeWithLock 重复

- 级别：**低**
- 涉及文件：`RankingRefreshScheduler.java:108-123`、`RankingArchiveService.java:397-415`
- 问题描述：两个类各自实现了完全相同的 `executeWithLock()` 方法，逻辑一致（tryLock(0, 30min) + InterruptedException 处理），违反 DRY 原则
- 修正建议：抽取为公共工具方法或基类

---

## 做得好的地方

1. **时间衰减对称性重构思路正确**：统一移除增量事件中的时间衰减，让正负事件完全对称（加多少减多少），衰减留给快照重建时统一处理。这个设计决策比逐事件衰减更合理
2. **浏览去重用 SETNX + TTL**：`tryAcquireViewDedup()` 用 `setIfAbsent` + 30 分钟 TTL，简洁高效
3. **分布式锁 tryLock(0, ...) 非阻塞**：归档和刷新任务都用 `waitTime=0` 立即返回，避免多实例排队等待
4. **新消费者结构统一**：`PostUnfavoritedRankingConsumer` 和 `CommentDeletedRankingConsumer` 完全遵循现有消费者模式
5. **空结果缓存与回填逻辑集成自然**：在 `loadAndBackfill()` 的 MongoDB 返回空时设置标记，查询入口处先检查标记

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 新-1 | 高 | viewScoreCap check-then-act 竞态 | RankingRedisRepository |
| 新-2 | 中 | viewScoreCap key 无 TTL | RankingRedisRepository |
| 新-3 | 中 | 取消收藏未减创作者热度 | PostUnfavoritedRankingConsumer |
| 新-4 | 中 | trimSortedSet 非原子（可忽略） | RankingRedisRepository |
| 新-5 | 低 | 匿名用户浏览去重未实现 | PostViewedRankingConsumer |
| 新-6 | 低 | executeWithLock 重复代码 | Scheduler / ArchiveService |

| 级别 | 数量 |
|------|------|
| 高 | 1 |
| 中 | 3 |
| 低 | 2 |

---

## 总体评价

本次 7 个提交修复了架构审计报告中 7/14 项问题，修复质量整体良好。时间衰减对称性重构的设计决策尤其合理。

新引入 6 个问题（1 高、3 中、2 低）。其中新-1（viewScoreCap 竞态）是必须修复的——高并发浏览场景下 check-then-act 非原子会导致分数上限失效，建议改用 Lua 脚本。新-3（取消收藏未减创作者热度）是逻辑遗漏，修复成本低。

---

*审查完成于 2026-02-27*
