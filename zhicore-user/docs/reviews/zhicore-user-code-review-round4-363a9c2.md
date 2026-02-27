# zhicore-user 代码 Review（第 4 轮）：Review Round3 全部 9 项问题修复

## Review 信息

| 字段 | 内容 |
|------|------|
| 轮次 | 第 4 轮（Round3 修复复审） |
| 审查范围 | commit 363a9c2，7 个文件，859+/429- 行变更 |
| 变更概述 | 提取 UserQueryPort 接口 + @Primary 装饰器模式，缓存失效改为 afterCommit，补充批量缓存查询和 15 个单元测试 |
| 审查基准 | docs/reviews/zhicore-user-code-review-round3-c16f5f3.md（9 项问题） |
| 审查日期 | 2026-02-27 |
| 上轮问题数 | 9 项（3 高 / 5 中 / 1 低） |

## 涉及提交

| Commit | 说明 |
|--------|------|
| 363a9c2 | fix(user): 修复 Review Round3 全部 9 项问题 |

## 审查标准

- 高：与架构文档的偏离/违规、并发安全缺陷、数据丢失风险、安全漏洞
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：代码风格、命名、冗余

---

## 上轮问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 说明 |
|------|------|---------|------|------|
| 1 | 高 | CacheAsideUserQuery 未被 Controller 调用 | ✅ 已修复 | Controller 注入 `UserQueryPort`，查询走 `@Primary` 装饰器，写操作走 `UserApplicationService` |
| 2 | 高 | 缓存失效逻辑重复 | ✅ 已修复 | CacheAsideUserQuery 删除了 `evictUserCache()`，缓存失效统一由 UserApplicationService 负责 |
| 3 | 高 | 循环依赖风险 | ✅ 已修复 | 提取 `UserQueryPort` 接口，`@Qualifier("userApplicationService")` 注入具体实现，无循环依赖 |
| 4 | 中 | 缓存失效在事务提交前执行 | ✅ 已修复 | 所有写操作改为 `registerCacheEviction()` → `afterCommit()` 回调 |
| 5 | 中 | getUserSimpleById 日志格式错误 | ✅ 已修复 | 改为 `"userId={}"` 占位符格式 |
| 6 | 中 | getUserSimpleById 缺少并发回填保护 | ✅ 已修复 | 改用 `setIfAbsent` 防止并发回填覆盖 |
| 7 | 中 | batchGetUsersSimple 未接入缓存 | ✅ 已修复 | 新增多级缓存查询：逐个查缓存 → 批量查库回填 miss 的 ID |
| 8 | 中 | simple 缓存键未统一管理 | ✅ 已修复 | UserRedisKeys 新增 `userSimple()` 方法，所有引用统一使用 |
| 9 | 低 | CacheAsideUserQuery 无测试覆盖 | ✅ 已修复 | 新增 `CacheAsideUserQueryTest`，15 个测试覆盖 HIT/NULL/MISS、DCL、锁降级、批量查询等 |

---

## 新发现问题

### 问题 1（中）：batchGetUsersSimple 所有 miss ID 共享同一个 jitter TTL

**文件**：`CacheAsideUserQuery.java:159-160`

**现象**：

```java
// 在循环外计算一次 TTL，所有 miss ID 共享
Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
        ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));

for (Long missedId : missedIds) {
    cacheRepository.setIfAbsent(cacheKey, dto, ttl); // 同一个 ttl
}
```

批量回填时，TTL jitter 只计算了一次，所有 miss 的 ID 使用相同的过期时间。

**影响**：
- 批量回填的缓存会在同一时刻集中过期，触发缓存雪崩
- jitter 的设计初衷就是让不同 key 的过期时间分散，这里失去了意义

**修复建议**：
将 TTL 计算移到循环内部，每个 key 独立计算 jitter：

```java
for (Long missedId : missedIds) {
    Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
            ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
    cacheRepository.setIfAbsent(cacheKey, dto, ttl);
}
```

---

### 问题 2（中）：UserApplicationService 仍直接依赖 CacheRepository，缓存失效逻辑未完全收敛到装饰器

**文件**：`UserApplicationService.java:8,60,384-394`

**现象**：
Round3 问题 2 的修复方案是删除 CacheAsideUserQuery 的 `evictUserCache()`，改由 UserApplicationService 自行负责缓存失效。这避免了循环依赖，但 UserApplicationService 仍然直接注入 `CacheRepository` 并手动拼接缓存键：

```java
private final CacheRepository cacheRepository;

private void evictUserCache(Long userId) {
    cacheRepository.delete(
        UserRedisKeys.userDetail(userId),
        UserRedisKeys.userSimple(userId)
    );
}
```

**影响**：
- 缓存键的"写入"在 CacheAsideUserQuery，"失效"在 UserApplicationService，两处需要保持同步
- 如果装饰器未来新增缓存维度（如 `user:{id}:roles`），UserApplicationService 的 evict 方法需要同步修改，容易遗漏
- 不过考虑到循环依赖的约束，这是一个合理的折中方案

**建议**（非必须修复）：
可以将缓存键清单提取为 `UserRedisKeys.allCacheKeys(userId)` 静态方法，让写入和失效都引用同一个键清单，减少遗漏风险：

```java
// UserRedisKeys
public static String[] allCacheKeys(Long userId) {
    return new String[] { userDetail(userId), userSimple(userId) };
}
```

---

### 问题 3（低）：getUserById 用 `set()` 回填，getUserSimpleById 用 `setIfAbsent()` 回填，策略不一致

**文件**：`CacheAsideUserQuery.java:98,130`

**现象**：

```java
// getUserById — 获取锁后用 set() 回填
cacheValue(cacheKey, userVO);  // 内部调用 cacheRepository.set()

// getUserSimpleById — 无锁，用 setIfAbsent() 回填
cacheRepository.setIfAbsent(cacheKey, dto, ttl);
```

**影响**：
- `getUserById` 持有锁，用 `set()` 覆盖写是安全的，逻辑正确
- `getUserSimpleById` 无锁，用 `setIfAbsent()` 防并发覆盖，逻辑也正确
- 但 `cacheValue()` 私有方法内部对 null 值用了 `setIfAbsent`，对非 null 值用了 `set`，而 `getUserSimpleById` 没有复用 `cacheValue()`，而是自己写了一套回填逻辑

**建议**（非必须修复）：
两种策略各有道理，当前实现功能上没有 bug。但建议在 `cacheValue()` 方法注释中说明"此方法仅供持锁场景使用"，避免后续开发者误用。

---

## 问题汇总

| 编号 | 级别 | 问题标题 |
|------|------|---------|
| 1 | 中 | batchGetUsersSimple 所有 miss ID 共享同一个 jitter TTL |
| 2 | 中 | UserApplicationService 仍直接依赖 CacheRepository，缓存失效逻辑未完全收敛 |
| 3 | 低 | getUserById 与 getUserSimpleById 回填策略不一致，缺少注释说明 |

**统计：0 高 / 2 中 / 1 低，共 3 项**

上轮 9 项问题全部修复，本轮无高级问题。

