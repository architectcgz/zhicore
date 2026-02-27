# zhicore-user 代码 Review（第 3 轮）：删除 CachedUserRepository，缓存逻辑上移 application 层

## Review 信息

| 字段 | 内容 |
|------|------|
| 轮次 | 第 3 轮（统一缓存架构重构） |
| 审查范围 | commit c16f5f3，12 个文件（zhicore-user 部分），215+/3593- 行变更 |
| 变更概述 | 删除 infrastructure 层 CachedUserRepository 及 8 个测试文件，新建 application 层 CacheAsideUserQuery 装饰器，UserApplicationService 写操作后失效缓存 |
| 审查基准 | docs/architecture/03-数据架构.md、docs/architecture/06-可靠性与一致性设计.md |
| 审查日期 | 2026-02-27 |
| 上轮问题数 | 第 2 轮 4 项（4 高 / 0 中 / 0 低）→ 全部修复 |

## 涉及提交

| Commit | 说明 |
|--------|------|
| c16f5f3 | refactor(user): 删除 CachedUserRepository，缓存逻辑上移 application 层 |

## 审查标准

- 高：与架构文档的偏离/违规、并发安全缺陷、数据丢失风险、安全漏洞
- 中：设计不合理、可维护性问题、遗漏关键场景
- 低：代码风格、命名、冗余

---

## 架构变更评价

本次重构方向正确，将缓存逻辑从 infrastructure 层上移到 application 层，与 zhicore-content 模块的 CacheAsidePostQuery/CacheAsideTagQuery 保持一致。代码量从 540 行（CachedUserRepository）精简到 171 行（CacheAsideUserQuery），移除了热点数据识别、公平锁等过度设计。

---

## 问题清单

### 问题 1（高）：CacheAsideUserQuery 未被 Controller 调用，缓存装饰器形同虚设

**文件**：`UserController.java:60,89`、`CacheAsideUserQuery.java`

**现象**：
UserController 的 `getUser()` 和 `getUserSimple()` 直接调用 `UserApplicationService.getUserById()` / `getUserSimpleById()`，完全绕过了 CacheAsideUserQuery 装饰器。

```java
// UserController.java:60 — 直接调用 service，未走缓存
UserVO user = userApplicationService.getUserById(userId);

// UserController.java:89
UserSimpleDTO user = userApplicationService.getUserSimpleById(userId);
```

**影响**：
- CacheAsideUserQuery 虽然已实现，但没有任何调用方，缓存层完全不生效
- 所有用户查询请求直接穿透到数据库，等同于没有缓存
- 这是本次重构最关键的遗漏——删除了旧的 CachedUserRepository（通过 `@Primary` 自动拦截），但新的装饰器需要显式调用，而调用方未切换

**修复建议**：
Controller 层注入 `CacheAsideUserQuery`，查询方法改为调用装饰器：

```java
private final CacheAsideUserQuery cacheAsideUserQuery;

@GetMapping("/{userId}")
public ApiResponse<UserVO> getUser(@PathVariable Long userId) {
    UserVO user = cacheAsideUserQuery.getUserById(userId);
    return ApiResponse.success(user);
}
```

同时检查 Feign 内部接口（如有）是否也需要切换。

---

### 问题 2（高）：缓存失效逻辑重复——UserApplicationService 和 CacheAsideUserQuery 各有一份 evictUserCache

**文件**：`UserApplicationService.java:374-384`、`CacheAsideUserQuery.java:135-145`

**现象**：
两个类各自实现了一份几乎完全相同的 `evictUserCache()` 方法，且 UserApplicationService 直接注入了 `CacheRepository` 来做缓存失效，绕过了装饰器。

```java
// UserApplicationService.java:374 — 私有方法，直接操作 CacheRepository
private void evictUserCache(Long userId) {
    cacheRepository.delete(
        UserRedisKeys.userDetail(userId),
        UserRedisKeys.userDetail(userId) + ":simple"
    );
}

// CacheAsideUserQuery.java:135 — 公开方法，逻辑完全相同
public void evictUserCache(Long userId) {
    cacheRepository.delete(
        UserRedisKeys.userDetail(userId),
        UserRedisKeys.userDetail(userId) + ":simple"
    );
}
```

**影响**：
- 违反 DRY 原则，缓存键拼接逻辑散落两处，后续修改容易遗漏
- UserApplicationService 直接依赖 `CacheRepository`，破坏了"缓存逻辑集中在装饰器"的架构意图
- 如果未来新增缓存键（如批量查询缓存），需要同步修改两处

**修复建议**：
UserApplicationService 删除私有 `evictUserCache()` 和 `CacheRepository` 依赖，改为注入 `CacheAsideUserQuery` 并调用其公开的 `evictUserCache()`：

```java
// UserApplicationService 中
private final CacheAsideUserQuery cacheAsideUserQuery;

// 写操作后
cacheAsideUserQuery.evictUserCache(userId);
```

注意：这会引入 `UserApplicationService ↔ CacheAsideUserQuery` 的循环依赖（CacheAsideUserQuery 已依赖 UserApplicationService）。解决方案见问题 3。

---

### 问题 3（高）：CacheAsideUserQuery 直接依赖 UserApplicationService，存在循环依赖风险

**文件**：`CacheAsideUserQuery.java:34,44-45`

**现象**：
CacheAsideUserQuery 构造器注入了 UserApplicationService 作为数据源：

```java
// CacheAsideUserQuery.java:34
private final UserApplicationService userApplicationService;

// CacheAsideUserQuery.java:94 — 缓存未命中时回源
UserVO userVO = userApplicationService.getUserById(userId);
```

**影响**：
1. 如果 UserApplicationService 需要调用 CacheAsideUserQuery（如问题 2 的修复方案），会形成 Spring 循环依赖
2. 装饰器直接依赖 application service 而非 domain repository，层级关系不清晰——content 模块的 CacheAsidePostQuery 应该也是类似模式，需要确认是否一致
3. UserApplicationService.getUserById() 内部有 `@Transactional(readOnly = true)` 和关注统计查询，装饰器每次回源都会开启只读事务

**修复建议**：
方案 A（推荐）：提取查询接口，打破循环依赖

```java
// 定义查询端口
public interface UserQueryPort {
    UserVO getUserById(Long userId);
    UserSimpleDTO getUserSimpleById(Long userId);
}

// UserApplicationService 实现该接口
@Service
public class UserApplicationService implements UserQueryPort { ... }

// CacheAsideUserQuery 依赖接口
public class CacheAsideUserQuery {
    private final UserQueryPort userQueryPort;
    ...
}
```

方案 B：CacheAsideUserQuery 直接依赖 UserRepository（domain 层），自行组装 VO。这样装饰器不依赖 application service，但需要重复组装逻辑。

---

### 问题 4（中）：缓存失效在事务提交前执行，存在不一致窗口

**文件**：`UserApplicationService.java:210,239,276,320,354`

**现象**：
所有写操作中，`evictUserCache()` 在 `@Transactional` 方法体内、事务提交前调用：

```java
// UserApplicationService.java:207-210
outboxEventRepository.save(outboxEvent);
evictUserCache(userId);  // ← 事务尚未提交
log.info("User profile updated...");
```

**影响**：
- 缓存已删除，但事务尚未提交。此时其他线程查询会触发缓存回填，读到旧数据并缓存
- 如果事务随后回滚，缓存中已经是旧数据（或空），但数据库未变更，造成短暂不一致
- 架构文档 06-可靠性与一致性设计.md 提到了"延迟双删"策略，但当前实现未遵循

**修复建议**：
使用 `TransactionSynchronizationManager.registerSynchronization()` 将缓存失效延迟到事务提交后（与 `disableUser()` 中吊销 Token 的做法一致）：

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            evictUserCache(userId);
        }
    }
);
```

---

### 问题 5（中）：getUserSimpleById 日志格式错误，userId 不会被输出

**文件**：`CacheAsideUserQuery.java:125`

**现象**：

```java
log.debug("Cache miss for user simple, fetching from source: userId=", userId);
```

使用了逗号拼接而非 SLF4J 占位符 `{}`。SLF4J 会把 `userId` 当作异常参数，实际日志输出为 `userId=`，丢失关键 ID 信息。

**修复建议**：

```java
log.debug("Cache miss for user simple, fetching from source: userId={}", userId);
```

---

### 问题 6（中）：getUserSimpleById 缺少分布式锁防击穿，与 getUserById 策略不一致

**文件**：`CacheAsideUserQuery.java:111-130`

**现象**：
`getUserById()` 使用了分布式锁 + DCL 防击穿，但 `getUserSimpleById()` 注释写着"简要信息不需要锁保护"，缓存未命中时直接查数据源。

**影响**：
- 如果某个热门用户的 simple 缓存过期，瞬间大量并发请求会同时穿透到数据库
- simple 接口通常被列表页、评论区等高频场景调用，QPS 可能比 detail 更高
- 虽然 simple 查询较轻量，但缺少任何并发保护仍有击穿风险

**修复建议**：
这是一个设计取舍问题，不一定需要和 detail 完全一致。两种方案：
1. 加锁保护（与 detail 一致）：适用于用户量大、QPS 高的场景
2. 保持现状但加 `setIfAbsent` 防止并发回填覆盖：多个线程同时回填时，只有第一个写入成功

```java
// 方案 2：用 setIfAbsent 替代 set，避免并发回填
if (dto != null) {
    cacheRepository.setIfAbsent(cacheKey, dto, getDetailTtl());
}
```

---

### 问题 7（中）：batchGetUsersSimple 未接入缓存，批量查询全部穿透数据库

**文件**：`UserApplicationService.java:149-160`、`CacheAsideUserQuery.java`

**现象**：
`batchGetUsersSimple()` 仍然直接查数据库，CacheAsideUserQuery 未提供批量查询的缓存方法。旧的 CachedUserRepository 有 `findByIdsWithCache()` 批量缓存优化，重构后该能力丢失。

**影响**：
- 批量接口（`POST /batch/simple`）被其他服务 Feign 调用时，每次都全量查库
- 随着调用量增长，数据库压力会显著上升

**修复建议**：
在 CacheAsideUserQuery 中新增 `batchGetUsersSimple(Set<Long> userIds)` 方法，逐个查缓存，仅对 miss 的 ID 批量查库回填。这是常见的 multi-get 缓存模式。

---

### 问题 8（中）：simple 缓存键使用字符串拼接而非 UserRedisKeys 统一管理

**文件**：`CacheAsideUserQuery.java:112`、`UserApplicationService.java:378`

**现象**：

```java
// CacheAsideUserQuery.java:112
String cacheKey = UserRedisKeys.userDetail(userId) + ":simple";

// UserApplicationService.java:378
UserRedisKeys.userDetail(userId) + ":simple"
```

simple 缓存键通过字符串拼接 `+ ":simple"` 生成，而非在 `UserRedisKeys` 中定义专用方法。

**影响**：
- 缓存键格式散落在业务代码中，不符合 UserRedisKeys 集中管理的设计意图
- 如果键格式变更，需要全局搜索替换

**修复建议**：
在 UserRedisKeys 中新增：

```java
public static String userSimple(Long userId) {
    return PREFIX + ":" + userId + ":simple";
}
```

---

### 问题 9（低）：删除 8 个测试文件后，CacheAsideUserQuery 无任何测试覆盖

**文件**：`src/test/java/.../repository/CachedUserRepository*Test.java`（已删除）

**现象**：
旧的 CachedUserRepository 有 8 个测试文件（批量、一致性、DCL、异常、锁公平性、TTL jitter 等），全部随之删除。新的 CacheAsideUserQuery 没有对应的测试。

**影响**：
- 缓存三态（HIT/NULL/MISS）、DCL、锁降级、TTL jitter 等关键路径无测试保障
- 后续修改缓存逻辑时缺少回归保护

**修复建议**：
至少补充以下测试：
1. 缓存命中直接返回（不查库）
2. 缓存 MISS → 获取锁 → 查库 → 回填缓存
3. 缓存 NULL → 直接返回 null（防穿透）
4. 获取锁失败 → 降级直接查库
5. DCL：获取锁后缓存已被其他线程回填
6. evictUserCache 删除正确的 key

---

## 问题汇总

| 编号 | 级别 | 问题标题 |
|------|------|---------|
| 1 | 高 | CacheAsideUserQuery 未被 Controller 调用，缓存装饰器形同虚设 |
| 2 | 高 | 缓存失效逻辑重复，UserApplicationService 和 CacheAsideUserQuery 各有一份 |
| 3 | 高 | CacheAsideUserQuery 直接依赖 UserApplicationService，存在循环依赖风险 |
| 4 | 中 | 缓存失效在事务提交前执行，存在不一致窗口 |
| 5 | 中 | getUserSimpleById 日志格式错误，userId 不会被输出 |
| 6 | 中 | getUserSimpleById 缺少分布式锁防击穿，与 getUserById 策略不一致 |
| 7 | 中 | batchGetUsersSimple 未接入缓存，批量查询全部穿透数据库 |
| 8 | 中 | simple 缓存键使用字符串拼接而非 UserRedisKeys 统一管理 |
| 9 | 低 | 删除 8 个测试文件后，CacheAsideUserQuery 无任何测试覆盖 |

**统计：3 高 / 5 中 / 1 低，共 9 项**