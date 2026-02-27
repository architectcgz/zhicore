# 代码 Review（第二轮）：fix-ranking-audit 分支（4 个新增提交，修复 Round 1 Review 问题）

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | fix-ranking-audit 分支第 8-11 个提交（186+/102-，8 文件） |
| 审查基准 | `code-review-fix-ranking-audit.md` 中 6 项新问题 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 提交列表

| 序号 | Hash | 说明 |
|------|------|------|
| 8 | `7ab1537` | fix: viewScoreCap 和 trimSortedSet 改用 Lua 脚本保证原子性 |
| 9 | `fa6d8ee` | fix: 取消收藏时同步减少创作者热度 |
| 10 | `5f0cd82` | feat: 匿名用户浏览按 IP+UA 指纹去重 |
| 11 | `1f60619` | refactor: 抽取 DistributedLockExecutor 消除重复代码 |

---

## Round 1 问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 修复提交 | 说明 |
|------|------|---------|------|---------|------|
| 新-1 | 高 | viewScoreCap check-then-act 竞态 | ✅ 已修复 | `7ab1537` | 改用 Lua 脚本原子 check-and-increment |
| 新-2 | 中 | viewScoreCap key 无 TTL | ✅ 已修复 | `7ab1537` | Lua 脚本中一并设置 30 天 TTL |
| 新-3 | 中 | 取消收藏未减创作者热度 | ✅ 已修复 | `fa6d8ee` | 补充 `incrementCreatorScore` 对称扣减 |
| 新-4 | 中 | trimSortedSet 非原子 | ✅ 已修复 | `7ab1537` | 改用 Lua 脚本合并 ZCARD+ZREMRANGEBYRANK |
| 新-5 | 低 | 匿名用户浏览去重未实现 | ✅ 已修复 | `5f0cd82` | IP+UA SHA-256 指纹去重 |
| 新-6 | 低 | executeWithLock 重复代码 | ✅ 已修复 | `1f60619` | 抽取 DistributedLockExecutor |

6/6 项全部修复。

---

## 各提交修复质量分析

### 提交 8：`7ab1537` — Lua 脚本保证原子性

修复新-1（高）、新-2（中）、新-4（中）三项。

**viewScoreCap Lua 脚本**（`RankingRedisRepository.java:455-468`）：
- GET → 判断 cap → SET + EXPIRE 合并为单次 Lua 执行，竞态条件消除
- TTL 仅在 key 无过期时设置（`TTL == -1`），避免每次覆盖已有 TTL
- 返回值用 `tostring()` 序列化为 String，调用方 `Double.parseDouble()` 解析，类型处理正确

**trimSortedSet Lua 脚本**（`RankingRedisRepository.java:497-503`）：
- ZCARD + ZREMRANGEBYRANK 合并为原子操作
- 逻辑简洁，`size <= topN` 直接返回 0

两个 Lua 脚本均使用 `DefaultRedisScript` 内联定义，与项目中已有的 `INCREMENT_SCRIPT` 风格一致。

### 提交 9：`fa6d8ee` — 取消收藏同步减少创作者热度

修复新-3（中）。

**PostUnfavoritedRankingConsumer.java:58-64**：
- 新增 `if (event.getAuthorId() != null)` 判空后调用 `incrementCreatorScore(authorId, -favoriteDelta)`
- 与 `PostFavoritedRankingConsumer` 收藏时的 `incrementCreatorScore(authorId, +favoriteDelta)` 完全对称
- `PostUnfavoritedEvent` 新增 `authorId` 字段，构造函数同步更新为三参数

修复干净，无遗留问题。

### 提交 10：`5f0cd82` — 匿名用户浏览按 IP+UA 指纹去重

修复新-5（低）。

**PostViewedRankingConsumer.java:118-130** `resolveDedupId()`：
- 登录用户：直接用 `userId` 作为去重 ID
- 匿名用户：`IP + "|" + UA` 拼接后 SHA-256 取前 16 位 hex，加 `anon:` 前缀
- IP 为空时返回 null，跳过去重（容错处理合理）

**PostViewedRankingConsumer.java:135-144** `sha256Short()`：
- `HexFormat.of().formatHex(hash, 0, 8)` 取前 8 字节 = 16 位 hex，碰撞概率极低
- `NoSuchAlgorithmException` 直接 throw RuntimeException，SHA-256 为 JDK 保证算法，合理

**PostViewedEvent.java**：
- 新增 `clientIp` 和 `userAgent` 字段，构造函数同步更新

**调用方统一**（`PostViewedRankingConsumer.java:78-84`）：
- 原来的 `if (userId != null)` 分支逻辑被 `resolveDedupId()` 统一替代
- `dedupId != null` 时才做去重检查，null 时跳过（IP 也为空的极端情况）

### 提交 11：`1f60619` — 抽取 DistributedLockExecutor

修复新-6（低）。

**DistributedLockExecutor.java**（新文件，50 行）：
- `@Component` + `@RequiredArgsConstructor`，注入 `RedissonClient`
- `executeWithLock(String lockKey, Runnable task)`：tryLock(0, 30min) 非阻塞
- InterruptedException 正确恢复中断标志
- 日志级别合理：获取成功 info，跳过 debug，中断 warn

**调用方变更**：
- `RankingArchiveService`：移除 `RedissonClient` 依赖，改注入 `DistributedLockExecutor`，调用 `lockExecutor.executeWithLock(LOCK_PREFIX + taskName, ...)`
- `RankingRefreshScheduler`：同上，移除 `RedissonClient`，改注入 `DistributedLockExecutor`
- 两处调用方式一致，锁 key 拼接方式保持不变（各自 `LOCK_PREFIX` + 任务名）

接口参数从 `taskName` 改为 `lockKey`（完整 key），调用方自行拼接前缀，职责划分更清晰。

---

## 本轮修复引入的新问题

### 建议-1：Lua 脚本实例可缓存为静态字段（微优化）

- 级别：**建议**
- 涉及文件：`RankingRedisRepository.java:482`、`RankingRedisRepository.java:513`
- 问题描述：`incrementViewScoreWithCap()` 和 `trimSortedSet()` 每次调用都 `new DefaultRedisScript<>()`。虽然 `DefaultRedisScript` 构造成本极低，但项目中已有的 `INCREMENT_SCRIPT` 采用了静态字段 + 成员变量缓存的模式（第 67-68 行）。两个新脚本的 String 常量已是 `static final`，但 `RedisScript` 实例在方法内每次 new
- 影响：无功能影响，仅风格不一致
- 修正建议：可选——将 `RedisScript` 实例也提升为成员变量，与 `incrementScript` 风格统一

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 建议-1 | 建议 | Lua 脚本 RedisScript 实例可缓存 | RankingRedisRepository |

| 级别 | 数量 |
|------|------|
| 高 | 0 |
| 中 | 0 |
| 低 | 0 |
| 建议 | 1 |

---

## 总体评价

本轮 4 个提交干净利落地修复了 Round 1 Review 的全部 6 项问题，未引入新的高/中/低级别问题。

修复亮点：
1. Lua 脚本方案正确解决了竞态条件和 TTL 缺失，一个提交同时修复 3 项问题（新-1、新-2、新-4）
2. 匿名用户指纹去重的 `resolveDedupId()` 设计统一了登录/匿名两条路径，代码比原来更简洁
3. `DistributedLockExecutor` 抽取合理，接口设计以 `lockKey` 而非 `taskName` 为参数，职责边界清晰

仅有 1 项风格建议（RedisScript 实例缓存），不影响功能，可选修复。

本分支 11 个提交整体可合并。

---

*审查完成于 2026-02-27*
