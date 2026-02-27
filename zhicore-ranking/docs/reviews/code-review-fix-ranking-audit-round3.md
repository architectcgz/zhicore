# 代码 Review（第三轮）：fix-ranking-audit 分支（2 个新增提交，修复 Round 2 建议）

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | fix-ranking-audit 分支第 12-13 个提交（77+/56-，5 文件） |
| 审查基准 | `code-review-fix-ranking-audit-round2.md` 中 1 项建议 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 提交列表

| 序号 | Hash | 说明 |
|------|------|------|
| 12 | `d860c6b` | style: Lua 脚本 RedisScript 实例提升为成员变量缓存，统一风格 |
| 13 | `4e3fdbb` | refactor: 将 DistributedLockExecutor 从 ranking 移到 zhicore-common |

---

## Round 2 建议修复状态

| 编号 | 级别 | 问题标题 | 状态 | 修复提交 | 说明 |
|------|------|---------|------|---------|------|
| 建议-1 | 建议 | Lua 脚本 RedisScript 实例可缓存 | ✅ 已修复 | `d860c6b` | 提升为成员变量，与 incrementScript 风格统一 |

1/1 项修复。

---

## 各提交分析

### 提交 12：`d860c6b` — RedisScript 实例缓存

**RankingRedisRepository.java:67-70**：
- 新增两个成员变量 `viewScoreCapScript` 和 `trimSortedSetScript`
- 与已有的 `incrementScript`（第 64 行）风格完全一致
- 方法内 `new DefaultRedisScript<>()` 替换为成员变量引用，消除重复构造

### 提交 13：`4e3fdbb` — DistributedLockExecutor 迁移到 zhicore-common

**新文件 `zhicore-common/.../cache/DistributedLockExecutor.java`**（68 行）：

相比原 ranking 模块版本（50 行），有两处增强：

1. **新增重载方法**：`executeWithLock(lockKey, waitTime, leaseTime, task)`，支持自定义等待时间和持有时间。原方法委托给新方法，`waitTime=ZERO, leaseTime=30min`
2. **unlock 前增加 `isHeldByCurrentThread()` 检查**（第 59 行）：防止锁超时自动释放后当前线程误 unlock 其他线程持有的锁。这是 Redisson 最佳实践

**包路径选择**：`com.zhicore.common.cache` — 与缓存/Redis 相关的通用工具放在 cache 包下，合理

**调用方变更**：
- `RankingArchiveService`：import 从 `ranking.infrastructure.scheduler` 改为 `common.cache`
- `RankingRefreshScheduler`：同上
- 原 `ranking/.../scheduler/DistributedLockExecutor.java` 已删除

---

## 本轮修复引入的新问题

无。

---

## 问题汇总

| 级别 | 数量 |
|------|------|
| 高 | 0 |
| 中 | 0 |
| 低 | 0 |
| 建议 | 0 |

---

## 总体评价

本轮 2 个提交质量很高：

1. `d860c6b` 纯风格统一，改动精准，三个 Lua 脚本的 `RedisScript` 实例现在风格一致
2. `4e3fdbb` 不仅完成了模块迁移，还顺手增强了两处：重载方法支持自定义超时、`isHeldByCurrentThread()` 防误释放。这两个改进都是实际生产中的最佳实践

本分支 13 个提交零遗留问题，可合并。

---

## 分支整体回顾（13 个提交）

| 轮次 | 提交范围 | 修复审计问题 | 引入新问题 | 最终状态 |
|------|---------|------------|-----------|---------|
| 初始实现 | #1-7 | 7/14 项审计问题 | 6 项（1高3中2低） | Round 1 Review |
| Round 1 修复 | #8-11 | 6/6 项 Round 1 问题 | 1 项建议 | Round 2 Review |
| Round 2 修复 | #12-13 | 1/1 项建议 + 额外增强 | 0 | ✅ 清零 |

---

*审查完成于 2026-02-27*
