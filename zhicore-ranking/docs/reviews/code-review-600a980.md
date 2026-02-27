# 代码 Review：600a980 fix(ranking): 修复第三轮 code review 的 3 项 backfillRedis 问题

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit 600a980 涉及的 2 个文件（93+/10-） |
| 审查基准 | 上次 review（code-review-26baa4d.md）的 3 项新问题 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 上次问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 说明 |
|------|------|---------|------|------|
| 新-1 | 中 | backfillRedis() 并发回填缺少防击穿互斥 | ✅ 已修复 | Redisson 分布式锁 + double-check |
| 新-2 | 中 | 回填的是 limit 截断后的部分数据 | ✅ 已修复 | 回填拉取 BACKFILL_MAX_SIZE=1000，返回按 limit 截断 |
| 新-3 | 低 | 逐条 setScore 非原子 | ✅ 已修复 | 新增 batchSetScoreAtomic()，Pipeline + RENAME |

3 项全部修复，修复质量良好。

---

## 本次修复引入的新问题

### 新问题 1：batchSetScoreAtomic() 的 RENAME 会覆盖目标 key 已有的 TTL

- 级别：**中**
- 涉及文件：`RankingRedisRepository.java:105`
- 问题描述：`redisTemplate.rename(tmpKey, key)` 执行后，目标 key 的 TTL 会被临时 key 的 TTL 覆盖。临时 key 没有设置 TTL（默认永不过期），所以 RENAME 后目标 key 的原有 TTL 被清除，变为永不过期。月榜 key 原本有 365 天 TTL，回填后 TTL 丢失
- 影响：回填过的月榜 key 永不过期，Redis 内存无法自动回收历史月份数据
- 修正建议：RENAME 后重新设置 TTL，或在 Pipeline 中对临时 key 预设与目标 key 相同的 TTL

### 新问题 2：tmpKey 使用 System.currentTimeMillis() 存在极低概率碰撞

- 级别：**低**
- 涉及文件：`RankingRedisRepository.java:95`
- 问题描述：`key + ":tmp:" + System.currentTimeMillis()` 在同一毫秒内如果有两个线程同时调用（虽然分布式锁已大幅降低概率），会生成相同的临时 key，导致数据互相覆盖。实际场景下因为有 Redisson 锁保护，碰撞概率极低
- 修正建议：可选——改用 `UUID.randomUUID()` 或追加线程 ID，彻底消除碰撞可能

---

## 做得好的地方

1. **分布式锁 + double-check**：`loadAndBackfill()` 获取锁后再查一次 Redis，经典的 DCL 模式，避免重复回源
2. **降级兜底完整**：锁等待超时 → 读 Redis → 兜底查 MongoDB；InterruptedException → 恢复中断标志 + 兜底查 MongoDB。三条路径都有合理的降级策略
3. **Pipeline + RENAME 原子性**：`batchSetScoreAtomic()` 先写临时 key 再 RENAME，消除了回填窗口期读到部分数据的问题
4. **异常处理层次清晰**：`backfillRedis()` catch 后只 warn 不影响主流程返回；`batchSetScoreAtomic()` catch 后清理临时 key 再 throw，职责分明

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 新-1 | 中 | RENAME 覆盖目标 key 已有 TTL | RankingRedisRepository |
| 新-2 | 低 | tmpKey 用 currentTimeMillis 极低概率碰撞 | RankingRedisRepository |

| 级别 | 数量 |
|------|------|
| 高 | 0 |
| 中 | 1 |
| 低 | 1 |

---

## 总体评价

四轮 review 问题趋势：11 → 6 → 3 → 2，高级问题：3 → 2 → 0 → 0，收敛良好。

本轮 3 项问题全部修复到位，`loadAndBackfill()` 的分布式锁 + double-check + 降级兜底实现规范。剩余 2 个新问题（TTL 丢失、tmpKey 碰撞）严重性较低，其中 TTL 丢失建议在下次迭代中顺手修复。

---

*审查完成于 2026-02-27*
