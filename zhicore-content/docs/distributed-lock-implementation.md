# 分布式锁实现方案

## 概述

本文档详细说明了 zhicore-content 服务中分布式锁的实现方案，包括设计原理、安全性保证、使用场景和最佳实践。

**最后更新**：2026-02-23

---

## 目录

- [背景与问题](#背景与问题)
- [设计目标](#设计目标)
- [架构设计](#架构设计)
- [核心概念](#核心概念)
- [实现细节](#实现细节)
- [安全性保证](#安全性保证)
- [锁键管理](#锁键管理)
- [使用场景](#使用场景)
- [最佳实践](#最佳实践)
- [监控与告警](#监控与告警)
- [常见问题](#常见问题)

---

## 背景与问题

### 多实例部署的挑战

在微服务架构中，为了保证高可用性和负载均衡，通常会部署多个服务实例。但这会带来以下问题：

1. **定时任务重复执行**
   - 每个实例都会执行定时任务
   - 导致数据重复处理、资源浪费
   - 可能引发数据不一致

2. **并发操作冲突**
   - 多个实例同时操作同一资源
   - 可能导致数据覆盖或丢失

### 需要分布式锁的场景

在 zhicore-content 服务中，以下场景需要分布式锁保护：

| 场景 | 执行频率 | 风险 | 锁策略 |
|------|---------|------|--------|
| Outbox 事件投递 | 每 5 秒 | 重复投递消息到 MQ | 看门狗（批量大小不确定） |
| 消费事件清理 | 每天凌晨 2 点 | 重复删除数据库记录 | 固定 TTL 5分钟 |
| 不完整文章清理 | 每 10 分钟 | 重复清理 PG 和 MongoDB | 固定 TTL 5分钟 |
| 作者信息回填 | 每天凌晨 2 点 | 重复调用外部服务 | 看门狗（外部调用不确定） |

---

## 设计目标

### 功能性目标

1. **互斥性**：同一时刻只有一个实例能获取锁
2. **安全性**：绝不错误释放其他线程/实例的锁
3. **自动释放**：锁持有者崩溃时，锁能自动释放
4. **高可用性**：Redis 故障时不影响服务可用性（任务暂停，服务继续运行）

### 非功能性目标

1. **性能**：获取/释放锁的延迟 < 10ms
2. **可靠性**：使用 Redisson 的所有权检查机制
3. **可观测性**：完整的日志记录和监控指标
4. **易用性**：简单的 API，易于集成

---

## 架构设计

### 分层架构

```
┌─────────────────────────────────────────┐
│         Application Layer               │
│  (Schedulers, Services)                 │
└─────────────────┬───────────────────────┘
                  │ uses
┌─────────────────▼───────────────────────┐
│      Infrastructure Layer               │
│  - LockKeys (锁键管理)                  │
│  - LockManager (端口接口)               │
│  - RedissonLockManagerImpl (实现)       │
└─────────────────┬───────────────────────┘
                  │ uses
┌─────────────────▼───────────────────────┐
│         Redisson Client                 │
│  (Redis distributed lock)               │
└─────────────────────────────────────────┘
```

### 端口-适配器模式

采用六边形架构（Hexagonal Architecture）的端口-适配器模式：

- **端口（Port）**：`LockManager` 接口，定义锁操作契约
- **适配器（Adapter）**：`RedissonLockManagerImpl`，基于 Redisson 的实现
- **锁键管理**：`LockKeys` 组件，统一管理锁键命名
- **优势**：
  - 业务层不依赖具体实现
  - 易于测试（可以 Mock）
  - 可以切换不同的锁实现（Redisson、Zookeeper 等）
  - 锁键命名规范化，防止跨服务冲突

---

## 核心概念

### 看门狗（Watchdog）机制

**什么是看门狗？**

Redisson 的看门狗是一个后台线程，会定期（默认每 10 秒）自动延长锁的过期时间，直到锁被显式释放。

**如何启用看门狗？**

```java
// ❌ 错误 - 指定 leaseTime 会禁用看门狗
lock.tryLock(waitTime, leaseTime, unit);  // 看门狗不工作

// ✅ 正确 - 不指定 leaseTime 启用看门狗
lock.tryLock(waitTime, unit);  // 看门狗自动续期
```

**看门狗的工作原理：**

```
T0:  获取锁，默认过期时间 30 秒
T10: 看门狗检查锁仍被持有，延长到 T40
T20: 看门狗再次延长到 T50
T25: 业务完成，显式释放锁
     看门狗停止续期
```

**何时使用看门狗？**

| 场景 | 是否使用看门狗 | 原因 |
|------|--------------|------|
| 批量处理（大小不确定） | ✅ 使用 | 执行时间不可预测 |
| 外部服务调用 | ✅ 使用 | 网络延迟不确定 |
| 简单数据库操作 | ❌ 不使用 | 执行时间可预测，用固定 TTL |
| 定时清理任务 | ❌ 不使用 | 执行时间可预测，用固定 TTL |

### 固定 TTL vs 看门狗

| 特性 | 固定 TTL | 看门狗 |
|------|---------|--------|
| 适用场景 | 执行时间可预测 | 执行时间不确定 |
| 锁过期风险 | 需要设置足够长的时间 | 自动续期，无风险 |
| 故障恢复 | 锁到期自动释放 | 进程崩溃后锁到期释放 |
| 性能开销 | 无额外开销 | 后台线程定期续期 |
| 推荐用法 | 清理任务、简单操作 | 批量处理、外部调用 |

---

## 实现细节

### LockManager 接口

```java
public interface LockManager {
    
    /**
     * 尝试获取锁（固定过期时间）
     * 
     * 注意：指定 leaseTime 后，Redisson 看门狗不会自动续期
     * 
     * @param key 锁键（建议格式：{env}:{service}:{category}:{resource}:{action}:lock）
     * @param waitTime 等待时间（null 或 Duration.ZERO 表示不等待）
     * @param leaseTime 锁持有时间（自动释放时间，不能为 null）
     * @return 是否获取成功
     * @throws IllegalArgumentException 如果参数无效
     */
    boolean tryLock(String key, Duration waitTime, Duration leaseTime);
    
    /**
     * 尝试获取锁（启用看门狗自动续期）
     * 
     * 适用于执行时间不确定的任务，Redisson 会自动续期直到显式释放
     * 
     * @param key 锁键
     * @param waitTime 等待时间（null 或 Duration.ZERO 表示不等待）
     * @return 是否获取成功
     * @throws IllegalArgumentException 如果参数无效
     */
    boolean tryLockWithWatchdog(String key, Duration waitTime);
    
    /**
     * 释放锁
     * 
     * 注意：
     * - 只能释放当前线程持有的锁
     * - 如果锁已过期或被其他线程持有，释放操作会被忽略
     * - 建议在 finally 块中调用，确保锁一定会被尝试释放
     * 
     * @param key 锁键
     */
    void unlock(String key);
    
    /**
     * 检查锁是否由当前线程持有
     * 
     * 用于在长时间运行的任务中检查锁是否仍然有效
     * 
     * @param key 锁键
     * @return 是否由当前线程持有
     */
    boolean isHeldByCurrentThread(String key);
}
```

### RedissonLockManagerImpl 实现

#### 核心特性

1. **参数校验**
   ```java
   // 校验锁键
   if (key == null || key.trim().isEmpty()) {
       throw new IllegalArgumentException("Lock key cannot be null or empty");
   }
   
   // 校验时间参数
   if (waitTime != null && waitTime.isNegative()) {
       throw new IllegalArgumentException("waitTime cannot be negative");
   }
   
   // 警告极端值
   if (waitTime != null && waitTime.toMinutes() > 60) {
       log.warn("Unusually large waitTime: {}", waitTime);
   }
   ```

2. **所有权检查**
   ```java
   // Redisson 内置所有权检查（UUID + ThreadId）
   if (lock.isHeldByCurrentThread()) {
       lock.unlock();
   } else {
       log.warn("Attempting to unlock a lock not held by current thread: key={}", key);
   }
   ```

3. **异常处理**
   ```java
   try {
       // 锁操作
   } catch (InterruptedException e) {
       Thread.currentThread().interrupt();
       return false;
   } catch (Exception e) {
       log.error("Failed to acquire lock: key={}", key, e);
       return false;
   }
   ```

#### 完整实现

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockManagerImpl implements LockManager {
    
    private final RedissonClient redissonClient;
    
    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        // 参数校验
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        Duration validatedLeaseTime = validateLeaseTime(leaseTime);
        
        try {
            RLock lock = redissonClient.getLock(key);
            
            boolean acquired = lock.tryLock(
                validatedWaitTime.toMillis(),
                validatedLeaseTime.toMillis(),
                TimeUnit.MILLISECONDS
            );
            
            if (acquired) {
                log.debug("Lock acquired (fixed TTL): key={}, waitTime={}, leaseTime={}", 
                    key, validatedWaitTime, validatedLeaseTime);
            }
            
            return acquired;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to acquire lock: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime) {
        // 参数校验
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        
        try {
            RLock lock = redissonClient.getLock(key);
            
            // 不指定 leaseTime，启用看门狗自动续期
            boolean acquired = lock.tryLock(
                validatedWaitTime.toMillis(),
                TimeUnit.MILLISECONDS
            );
            
            if (acquired) {
                log.debug("Lock acquired (with watchdog): key={}, waitTime={}", 
                    key, validatedWaitTime);
            }
            
            return acquired;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to acquire lock: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public void unlock(String key) {
        validateKey(key);
        
        try {
            RLock lock = redissonClient.getLock(key);
            
            // 检查锁是否由当前线程持有
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", key);
            } else {
                // 锁已过期或被其他线程持有
                log.warn("Attempting to unlock a lock not held by current thread: key={}", key);
            }
            
        } catch (IllegalMonitorStateException e) {
            // Redisson 抛出此异常表示锁不属于当前线程
            log.warn("Attempting to unlock a lock not owned by current thread: key={}", key);
        } catch (Exception e) {
            // 记录错误并增加监控指标
            log.error("Failed to release lock: key={}, error={}", key, e.getMessage(), e);
            // TODO: 增加监控指标 lockReleaseFailureCounter.increment()
        }
    }
    
    @Override
    public boolean isHeldByCurrentThread(String key) {
        validateKey(key);
        
        try {
            RLock lock = redissonClient.getLock(key);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            log.error("Failed to check lock ownership: key={}", key, e);
            return false;
        }
    }
}
```

---

## 安全性保证

### Redisson 所有权检查机制

Redisson 使用 **UUID + ThreadId** 标识锁持有者，确保只有持有者才能释放锁。

#### 锁的 Redis 存储结构

```
Key: dev:zhicore-content:outbox:dispatcher:dispatch:lock
Value: {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",  // Redisson 客户端 UUID
    "threadId": 123                                   // 线程 ID
}
```

#### 所有权验证流程

```java
// 获取锁时
RLock lock = redissonClient.getLock(key);
lock.tryLock(waitTime, unit);
// Redis 存储: key -> {uuid: clientUUID, threadId: currentThreadId}

// 释放锁时
if (lock.isHeldByCurrentThread()) {  // 检查 UUID 和 ThreadId
    lock.unlock();  // 使用 Lua 脚本原子性删除
}
```

#### Lua 脚本保证原子性

Redisson 使用 Lua 脚本确保检查和删除操作的原子性：

```lua
-- 检查锁是否由当前客户端持有
if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
    -- 持有者匹配，删除锁
    return redis.call('del', KEYS[1])
else
    -- 持有者不匹配，返回 0
    return 0
end
```

### 为什么不需要 ThreadLocal？

**之前的设计（过度工程化）：**
```java
// ❌ 不必要的复杂性
private final ThreadLocal<Map<String, RLock>> heldLocks = 
    ThreadLocal.withInitial(ConcurrentHashMap::new);
```

**现在的设计（简洁有效）：**
```java
// ✅ 直接使用 Redisson 的所有权检查
RLock lock = redissonClient.getLock(key);
if (lock.isHeldByCurrentThread()) {
    lock.unlock();
}
```

**原因：**
1. Redisson 的 `isHeldByCurrentThread()` 已经检查了 UUID + ThreadId
2. ThreadLocal 增加了内存开销和复杂度
3. ThreadLocal 需要手动清理，容易内存泄漏
4. Redisson 的实现已经足够安全

### 锁过期场景分析

#### 场景：锁在业务执行过程中过期

```
时间线：
T0:  实例 A 获取锁（固定 TTL 30 秒）
T30: 锁自动过期（实例 A 业务还在执行）
T31: 实例 B 获取同一个锁
T35: 实例 A 业务完成，尝试释放锁
     -> lock.isHeldByCurrentThread() 返回 false
     -> 不会释放实例 B 的锁 ✅
     -> 记录警告日志 ⚠️
```

**结果：**
- ✅ 不会错误释放其他实例的锁（Redisson UUID 检查）
- ⚠️ 会记录警告日志（需要监控）
- ⚠️ 说明锁持有时间设置不合理或业务逻辑需要优化

**解决方案：**
1. 增加锁持有时间（固定 TTL 模式）
2. 使用看门狗自动续期（不确定执行时间的任务）
3. 优化业务逻辑，减少执行时间

---

## 锁键管理

### LockKeys 组件

为了防止跨服务、跨环境的锁冲突，我们使用 `LockKeys` 组件统一管理所有锁键。

#### 锁键命名格式

```
{env}:{service}:{category}:{resource}:{action}:lock
```

**示例：**
```
dev:zhicore-content:outbox:dispatcher:dispatch:lock
prod:zhicore-content:scheduler:consumed-event-cleanup:cleanup:lock
test:zhicore-content:scheduler:incomplete-post-cleanup:cleanup:lock
```

#### LockKeys 实现

```java
@Component
public class LockKeys {
    
    @Value("${spring.profiles.active:dev}")
    private String env;
    
    @Value("${spring.application.name:zhicore-content}")
    private String serviceName;
    
    /**
     * Outbox 事件投递锁
     */
    public String outboxDispatcher() {
        return buildKey("outbox", "dispatcher", "dispatch");
    }
    
    /**
     * 消费事件清理锁
     */
    public String consumedEventCleanup() {
        return buildKey("scheduler", "consumed-event-cleanup", "cleanup");
    }
    
    /**
     * 不完整文章清理锁
     */
    public String incompletePostCleanup() {
        return buildKey("scheduler", "incomplete-post-cleanup", "cleanup");
    }
    
    /**
     * 作者信息回填锁
     */
    public String authorInfoBackfill() {
        return buildKey("scheduler", "author-info-backfill", "backfill");
    }
    
    /**
     * 构建锁键
     */
    private String buildKey(String category, String resource, String action) {
        return String.format("%s:%s:%s:%s:%s:lock", 
            env, serviceName, category, resource, action);
    }
}
```

#### 为什么需要环境和服务前缀？

**问题场景：**
```
# 没有前缀的锁键
scheduler:outbox:dispatcher:lock

# 多环境共享 Redis 时的冲突
dev 环境实例 A 获取锁
prod 环境实例 B 无法获取锁（被 dev 阻塞）❌
```

**解决方案：**
```
# 带环境和服务前缀的锁键
dev:zhicore-content:outbox:dispatcher:dispatch:lock
prod:zhicore-content:outbox:dispatcher:dispatch:lock

# 不同环境使用不同的锁键，互不干扰 ✅
```

#### 使用方式

```java
@Component
public class OutboxEventDispatcher {
    
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    public void dispatch() {
        String lockKey = lockKeys.outboxDispatcher();
        
        boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, Duration.ZERO);
        
        if (!lockAcquired) {
            return;
        }
        
        try {
            // 业务逻辑
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

---

## 使用场景

### 1. Outbox 事件投递器

**场景**：定期扫描 Outbox 表，投递事件到 RocketMQ

**锁策略**：看门狗（批量大小不确定，执行时间不可预测）

```java
@Component
public class OutboxEventDispatcher implements SchedulingConfigurer {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;
    
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    public void dispatch() {
        String lockKey = lockKeys.outboxDispatcher();
        
        // 使用看门狗自动续期
        boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, LOCK_WAIT_TIME);
        
        if (!lockAcquired) {
            log.debug("无法获取 Outbox 投递锁，跳过本次执行");
            return;
        }
        
        try {
            // 批量读取待投递事件
            List<OutboxEventEntity> events = outboxEventMapper
                .findByStatusOrderByCreatedAtAsc("PENDING", batchSize);
            
            // 逐个投递
            for (OutboxEventEntity event : events) {
                dispatchEvent(event);
            }
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

**配置说明**：
- **执行频率**：每 5 秒（动态可配置）
- **等待时间**：0（不等待，获取不到直接返回）
- **锁策略**：看门狗自动续期
- **原因**：批量大小动态配置，执行时间不确定

### 2. 消费事件清理调度器

**场景**：每天凌晨清理 30 天前的消费记录

**锁策略**：固定 TTL 5 分钟（删除操作时间可预测）

```java
@Component
public class ConsumedEventCleanupScheduler {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(5);
    
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldEvents() {
        String lockKey = lockKeys.consumedEventCleanup();
        
        // 使用固定 TTL
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
        
        if (!lockAcquired) {
            return;
        }
        
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deletedCount = consumedEventRepository.cleanupBefore(cutoff);
            log.info("清理过期消费记录完成，删除记录数: {}", deletedCount);
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

**配置说明**：
- **执行频率**：每天凌晨 2 点
- **等待时间**：0（不等待）
- **锁持有时间**：5 分钟
- **原因**：删除操作时间可预测，通常在 1-2 分钟内完成

### 3. 不完整文章清理调度器

**场景**：每 10 分钟清理不完整文章和处理补偿任务

**锁策略**：固定 TTL 5 分钟（清理操作时间可预测）

```java
@Component
public class IncompletePostCleanupScheduler {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(5);
    
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    @Scheduled(cron = "0 */10 * * * *")
    public void cleanupIncompletePosts() {
        String lockKey = lockKeys.incompletePostCleanup();
        
        // 使用固定 TTL
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
        
        if (!lockAcquired) {
            return;
        }
        
        try {
            // 清理不完整文章
            int cleanedPosts = cleanupIncompletePostsInternal();
            
            // 处理补偿任务
            int processedTasks = processCompensationTasks();
            
            log.info("清理任务完成，清理文章数: {}, 处理补偿任务数: {}", 
                cleanedPosts, processedTasks);
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

**配置说明**：
- **执行频率**：每 10 分钟
- **等待时间**：0（不等待）
- **锁持有时间**：5 分钟
- **原因**：清理操作是幂等的，时间可预测

### 4. 作者信息回填调度器

**场景**：每天凌晨回填未知作者信息

**锁策略**：看门狗（外部服务调用时间不确定）

```java
@Component
public class AuthorInfoBackfillScheduler {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;
    
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void backfillUnknownAuthors() {
        String lockKey = lockKeys.authorInfoBackfill();
        
        // 使用看门狗自动续期
        boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, LOCK_WAIT_TIME);
        
        if (!lockAcquired) {
            return;
        }
        
        try {
            // 批量处理
            while (true) {
                List<Post> posts = postRepository
                    .findByOwnerNameAndVersion(UNKNOWN_AUTHOR_NAME, 0, 100);
                
                if (posts.isEmpty()) {
                    break;
                }
                
                // 批量获取用户信息并更新（调用外部服务）
                processPostsBatch(posts);
            }
        } finally {
            lockManager.unlock(lockKey);
        }
    }
}
```

**配置说明**：
- **执行频率**：每天凌晨 2 点
- **等待时间**：0（不等待）
- **锁策略**：看门狗自动续期
- **原因**：需要调用外部 user-service，网络延迟不确定

---

## 最佳实践

### 1. 选择合适的锁策略

| 任务特征 | 推荐策略 | 示例 |
|---------|---------|------|
| 执行时间可预测 | 固定 TTL | 数据库删除、简单查询 |
| 执行时间不确定 | 看门狗 | 批量处理、外部服务调用 |
| 批量大小动态 | 看门狗 | Outbox 投递（批量大小可配置） |
| 网络调用 | 看门狗 | RPC 调用、HTTP 请求 |

### 2. 锁持有时间设置（固定 TTL 模式）

| 任务类型 | 推荐持有时间 | 理由 |
|---------|-------------|------|
| 快速批量操作 | 30 秒 - 1 分钟 | 批量投递、快速查询 |
| 数据库删除 | 3 - 5 分钟 | 大量数据删除 |
| 外部服务调用 | 使用看门狗 | 网络延迟不确定 |
| 复杂业务逻辑 | 5 - 10 分钟 | 多步骤处理 |

**原则**：
- 持有时间 > 预期执行时间的 2 倍
- 不要设置过长（避免故障时长时间阻塞）
- 监控实际执行时间，动态调整
- 不确定时优先使用看门狗

### 3. 等待时间策略

```java
// 策略 1：不等待（推荐用于定时任务）
Duration.ZERO

// 策略 2：短时间等待（用于高频操作）
Duration.ofMillis(100)

// 策略 3：长时间等待（用于关键操作）
Duration.ofSeconds(5)
```

**选择依据**：
- **定时任务**：不等待（错过一次无所谓）
- **用户请求**：短时间等待（提升成功率）
- **关键操作**：长时间等待（必须成功）

### 4. 异常处理模式

```java
public void scheduledTask() {
    String lockKey = lockKeys.someTask();
    boolean lockAcquired = false;
    
    try {
        lockAcquired = lockManager.tryLock(lockKey, WAIT_TIME, LEASE_TIME);
        
        if (!lockAcquired) {
            log.debug("无法获取锁，跳过本次执行");
            return;
        }
        
        // 业务逻辑
        doBusinessLogic();
        
    } catch (Exception e) {
        log.error("任务执行失败", e);
        // 不要抛出异常，避免影响定时任务调度
    } finally {
        if (lockAcquired) {
            lockManager.unlock(lockKey);
        }
    }
}
```

**要点**：
- 使用 `finally` 确保锁一定会被尝试释放
- 捕获所有异常，避免影响定时任务
- 不需要 try-catch unlock（LockManager 内部已处理）

### 5. 参数校验

LockManager 实现了完整的参数校验：

```java
// ✅ 正确 - 参数合法
lockManager.tryLock(key, Duration.ZERO, Duration.ofMinutes(5));

// ❌ 错误 - 会抛出 IllegalArgumentException
lockManager.tryLock(null, Duration.ZERO, Duration.ofMinutes(5));  // key 为 null
lockManager.tryLock(key, Duration.ofMinutes(-1), Duration.ofMinutes(5));  // waitTime 为负
lockManager.tryLock(key, Duration.ZERO, null);  // leaseTime 为 null（应使用 tryLockWithWatchdog）
lockManager.tryLock(key, Duration.ZERO, Duration.ofSeconds(5));  // leaseTime 过短（警告）
```

**警告阈值**：
- waitTime > 60 分钟：不合理的等待时间
- leaseTime < 10 秒：可能导致锁过早过期
- leaseTime > 30 分钟：可能导致故障时长时间阻塞

### 6. 长时间任务的锁检查

对于执行时间可能超过锁持有时间的任务，建议使用看门狗或添加中间检查：

```java
public void longRunningTask() {
    String lockKey = lockKeys.someTask();
    
    // 方案 1：使用看门狗（推荐）
    boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, Duration.ZERO);
    
    if (!lockAcquired) {
        return;
    }
    
    try {
        // 第一阶段
        doPhase1();
        
        // 第二阶段
        doPhase2();
        
        // 第三阶段
        doPhase3();
        
    } finally {
        lockManager.unlock(lockKey);
    }
}

// 方案 2：固定 TTL + 中间检查（不推荐，复杂且容易出错）
public void longRunningTaskWithCheck() {
    String lockKey = lockKeys.someTask();
    boolean lockAcquired = lockManager.tryLock(lockKey, Duration.ZERO, Duration.ofMinutes(10));
    
    if (!lockAcquired) {
        return;
    }
    
    try {
        // 第一阶段
        doPhase1();
        
        // 检查锁是否仍然有效
        if (!lockManager.isHeldByCurrentThread(lockKey)) {
            log.warn("锁已过期，中止任务执行");
            return;
        }
        
        // 第二阶段
        doPhase2();
        
    } finally {
        lockManager.unlock(lockKey);
    }
}
```

---

## 监控与告警

### 关键指标

| 指标 | 说明 | 告警阈值 | 监控标签 |
|------|------|---------|---------|
| 锁获取成功率 | 成功获取锁的比例 | < 95% | task_name（不使用完整 key） |
| 锁持有时间 | 实际持有锁的时间 | > 配置时间的 80% | task_name |
| 锁释放失败次数 | 释放锁时出现异常 | > 10 次/小时 | task_name |
| 锁过期次数 | 业务未完成锁就过期 | > 5 次/小时 | task_name |

**注意：监控标签基数问题**

```java
// ❌ 错误 - 使用完整锁键作为标签（高基数风险）
meterRegistry.counter("lock.acquire",
    "key", "dev:zhicore-content:outbox:dispatcher:dispatch:lock"  // 每个环境一个标签
).increment();

// ✅ 正确 - 使用任务类别作为标签
meterRegistry.counter("lock.acquire",
    "task", "outbox-dispatcher"  // 固定的任务名称
).increment();

// ✅ 正确 - 或者规范化锁键前缀
meterRegistry.counter("lock.acquire",
    "category", "outbox",
    "resource", "dispatcher"
).increment();
```

### 日志监控

#### 正常日志

```
DEBUG - Lock acquired (with watchdog): key=dev:zhicore-content:outbox:dispatcher:dispatch:lock, waitTime=PT0S
DEBUG - Lock acquired (fixed TTL): key=dev:zhicore-content:scheduler:consumed-event-cleanup:cleanup:lock, waitTime=PT0S, leaseTime=PT5M
DEBUG - Lock released: key=dev:zhicore-content:outbox:dispatcher:dispatch:lock
```

#### 警告日志

```
WARN - Attempting to unlock a lock not held by current thread: key=dev:zhicore-content:outbox:dispatcher:dispatch:lock
```

**含义**：锁在业务执行过程中过期了

**处理**：
1. 检查业务执行时间是否过长
2. 如果使用固定 TTL，增加锁持有时间或改用看门狗
3. 优化业务逻辑，减少执行时间

#### 错误日志

```
ERROR - Failed to acquire lock: key=dev:zhicore-content:outbox:dispatcher:dispatch:lock, error=...
ERROR - Failed to release lock: key=dev:zhicore-content:outbox:dispatcher:dispatch:lock, error=...
```

**含义**：Redis 连接异常或其他系统错误

**处理**：
1. 检查 Redis 连接状态
2. 检查网络连接
3. 查看 Redisson 客户端日志

### Prometheus 指标示例

```java
@Component
public class LockMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordLockAcquired(String taskName, boolean success) {
        meterRegistry.counter("lock.acquire",
            "task", taskName,  // 使用任务名称，不是完整 key
            "success", String.valueOf(success)
        ).increment();
    }
    
    public void recordLockHoldTime(String taskName, Duration duration) {
        meterRegistry.timer("lock.hold.time",
            "task", taskName
        ).record(duration);
    }
    
    public void recordLockExpired(String taskName) {
        meterRegistry.counter("lock.expired",
            "task", taskName
        ).increment();
    }
}
```

---

## 常见问题

### Q1: 为什么使用 Redisson 而不是自己实现？

**A**: Redisson 提供了成熟的分布式锁实现，包括：
- 自动续期（看门狗机制）
- 可重入锁
- 公平锁
- 读写锁
- 信号量
- 完善的异常处理
- UUID + ThreadId 所有权检查

自己实现容易出现边界情况的 bug，且需要大量测试。

### Q2: 看门狗是如何工作的？

**A**: 
1. 获取锁时不指定 leaseTime
2. Redisson 启动后台线程（看门狗）
3. 每 10 秒检查一次锁是否仍被持有
4. 如果持有，自动延长过期时间到 30 秒
5. 显式释放锁后，看门狗停止续期

**注意**：指定 leaseTime 会禁用看门狗！

```java
// ❌ 看门狗不工作
lock.tryLock(waitTime, leaseTime, unit);

// ✅ 看门狗工作
lock.tryLock(waitTime, unit);
```

### Q3: 锁过期了怎么办？

**A**: 三种处理方式：

1. **使用看门狗**（推荐）
   ```java
   lockManager.tryLockWithWatchdog(key, Duration.ZERO);
   ```

2. **增加锁持有时间**
   ```java
   Duration.ofMinutes(10)  // 从 5 分钟增加到 10 分钟
   ```

3. **优化业务逻辑**
   - 减少数据库查询
   - 使用批量操作
   - 异步处理非关键步骤

### Q4: Redis 故障时会怎样？

**A**: 
- 获取锁失败，返回 `false`
- 业务逻辑不执行（定时任务跳过本次执行）
- 记录错误日志
- **服务继续运行，不影响服务可用性**

**重要说明**：
- "Redis 故障时不影响业务" 指的是**不影响服务可用性**
- 定时任务会暂停执行（无法获取锁）
- Outbox 投递暂停 = 消息堆积风险，需要监控

**建议**：
- 监控 Redis 可用性
- 配置 Redis 主从/集群
- 监控 Outbox 表堆积情况
- 设置告警阈值

### Q5: 会不会死锁？

**A**: 不会，因为：
- 锁有自动过期时间（lease time 或看门狗默认 30 秒）
- 即使持有者崩溃，锁也会自动释放
- Redisson 有看门狗机制，自动续期
- 所有锁操作都有超时时间

### Q6: 性能如何？

**A**: 
- 获取锁：< 5ms（本地 Redis）
- 释放锁：< 3ms
- 对业务影响：可忽略不计
- 看门狗续期：后台线程，不阻塞业务

**优化建议**：
- 使用 Redis 集群提高可用性
- 合理设置锁粒度（不要太粗）
- 避免在锁内执行耗时操作

### Q7: 为什么不需要 ThreadLocal？

**A**: 
- Redisson 的 `isHeldByCurrentThread()` 已经检查了 UUID + ThreadId
- ThreadLocal 增加了内存开销和复杂度
- ThreadLocal 需要手动清理，容易内存泄漏
- Redisson 的实现已经足够安全

**之前的设计（过度工程化）：**
```java
private final ThreadLocal<Map<String, RLock>> heldLocks = ...
```

**现在的设计（简洁有效）：**
```java
RLock lock = redissonClient.getLock(key);
if (lock.isHeldByCurrentThread()) {
    lock.unlock();
}
```

### Q8: 如何测试分布式锁？

**A**: 

1. **单元测试**：Mock LockManager
   ```java
   @Test
   public void testSchedulerWithLock() {
       when(lockManager.tryLock(any(), any(), any())).thenReturn(true);
       
       scheduler.execute();
       
       verify(lockManager).tryLock(any(), any(), any());
       verify(lockManager).unlock(any());
   }
   ```

2. **集成测试**：使用真实 Redis
   ```java
   @SpringBootTest
   @Testcontainers
   public class LockIntegrationTest {
       
       @Container
       static GenericContainer redis = new GenericContainer("redis:7")
           .withExposedPorts(6379);
       
       @Test
       public void testConcurrentLockAcquisition() {
           // 测试多线程并发获取锁
       }
   }
   ```

3. **压力测试**：模拟多实例
   ```bash
   # 启动 3 个实例
   java -jar app.jar --server.port=8081 &
   java -jar app.jar --server.port=8082 &
   java -jar app.jar --server.port=8083 &
   
   # 观察日志，确保只有一个实例执行任务
   ```

### Q9: 锁键命名为什么需要环境和服务前缀？

**A**: 防止跨服务、跨环境的锁冲突

**问题场景**：
```
# 没有前缀
scheduler:outbox:dispatcher:lock

# 多环境共享 Redis 时
dev 环境实例 A 获取锁
prod 环境实例 B 无法获取锁（被 dev 阻塞）❌
```

**解决方案**：
```
# 带前缀
dev:zhicore-content:outbox:dispatcher:dispatch:lock
prod:zhicore-content:outbox:dispatcher:dispatch:lock

# 不同环境使用不同的锁键，互不干扰 ✅
```

### Q10: 什么时候使用固定 TTL，什么时候使用看门狗？

**A**: 

| 场景 | 推荐策略 | 原因 |
|------|---------|------|
| 执行时间可预测 | 固定 TTL | 简单、无额外开销 |
| 执行时间不确定 | 看门狗 | 自动续期，防止过期 |
| 批量大小动态 | 看门狗 | 无法预测执行时间 |
| 外部服务调用 | 看门狗 | 网络延迟不确定 |
| 简单数据库操作 | 固定 TTL | 时间可预测 |

**经验法则**：
- 不确定时优先使用看门狗
- 简单操作使用固定 TTL
- 监控实际执行时间，动态调整策略

---

## 生产就绪收口点

本节列出将分布式锁方案从"能上线跑"提升到"生产就绪"的 3 个关键收口点。

### 收口点 1：固定 TTL 配置化与数据驱动

**问题**：
当前固定 TTL 使用硬编码值（如 5 分钟），无法根据实际执行时间动态调整。

**解决方案**：

#### 1.1 配置化 TTL

```yaml
# application.yml
zhicore:
  content:
    lock:
      consumed-event-cleanup:
        lease-time: 300000  # 5分钟（毫秒）
      incomplete-post-cleanup:
        lease-time: 300000  # 5分钟（毫秒）
```

```java
@Component
@ConfigurationProperties(prefix = "zhicore.content.lock")
@Data
public class LockProperties {
    
    private TaskLockConfig consumedEventCleanup = new TaskLockConfig(Duration.ofMinutes(5));
    private TaskLockConfig incompletePostCleanup = new TaskLockConfig(Duration.ofMinutes(5));
    
    @Data
    @AllArgsConstructor
    public static class TaskLockConfig {
        private Duration leaseTime;
    }
}
```

#### 1.2 数据驱动的 TTL 调整

**推荐公式**：`leaseTime = P95执行时间 × 2`

**实现步骤**：

1. **收集执行时间指标**
   ```java
   @Component
   public class ConsumedEventCleanupScheduler {
       
       private final MeterRegistry meterRegistry;
       
       @Scheduled(cron = "0 0 2 * * *")
       public void cleanupOldEvents() {
           String lockKey = lockKeys.consumedEventCleanup();
           
           long startTime = System.currentTimeMillis();
           boolean lockAcquired = lockManager.tryLock(
               lockKey, 
               Duration.ZERO, 
               lockProperties.getConsumedEventCleanup().getLeaseTime()
           );
           
           if (!lockAcquired) {
               return;
           }
           
           try {
               // 业务逻辑
               LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
               int deletedCount = consumedEventRepository.cleanupBefore(cutoff);
               
               // 记录执行时间
               long duration = System.currentTimeMillis() - startTime;
               meterRegistry.timer("lock.hold.time", 
                   "task", "consumed-event-cleanup"
               ).record(duration, TimeUnit.MILLISECONDS);
               
               log.info("清理过期消费记录完成，删除记录数: {}, 耗时: {}ms", 
                   deletedCount, duration);
           } finally {
               lockManager.unlock(lockKey);
           }
       }
   }
   ```

2. **监控锁过期情况**
   ```java
   // 在 unlock 时检测锁是否已过期
   @Override
   public void unlock(String key) {
       validateKey(key);
       
       try {
           RLock lock = redissonClient.getLock(key);
           
           if (lock.isHeldByCurrentThread()) {
               lock.unlock();
               log.debug("Lock released: key={}", key);
           } else {
               // 锁已过期，记录指标
               meterRegistry.counter("lock.expired",
                   "task", extractTaskName(key)  // 从 key 中提取任务名称
               ).increment();
               
               log.warn("Attempting to unlock a lock not held by current thread: key={}", key);
           }
       } catch (Exception e) {
           log.error("Failed to release lock: key={}, error={}", key, e.getMessage(), e);
       }
   }
   ```

3. **定期审查和调整**
   - 每月查看 Grafana 中的 `lock.hold.time` P95 值
   - 如果 P95 接近 leaseTime 的 50%，说明配置合理
   - 如果 P95 超过 leaseTime 的 80%，需要增加 leaseTime
   - 如果 `lock.expired` 计数器持续增长，说明 leaseTime 过短

**告警规则**：
```yaml
# Prometheus 告警规则
groups:
  - name: distributed_lock
    rules:
      - alert: LockExpiredTooOften
        expr: rate(lock_expired_total[5m]) > 0.1
        for: 5m
        annotations:
          summary: "锁过期频率过高"
          description: "任务 {{ $labels.task }} 的锁在业务未完成时过期，过去5分钟平均每分钟 {{ $value }} 次"
```

---

### 收口点 2：Outbox 堆积保护

**问题**：
Redis 故障时 Outbox 投递暂停，消息堆积可能导致：
- 数据库表膨胀
- 投递延迟增加
- 恢复时间过长

**解决方案**：

#### 2.1 Outbox 堆积监控指标

```java
@Component
public class OutboxMetrics {
    
    private final OutboxEventMapper outboxEventMapper;
    private final MeterRegistry meterRegistry;
    
    /**
     * 定期采集 Outbox 指标（每分钟）
     */
    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        // 1. PENDING 消息数量
        long pendingCount = outboxEventMapper.countByStatus(
            OutboxEventEntity.OutboxStatus.PENDING.name()
        );
        meterRegistry.gauge("outbox.pending.count", pendingCount);
        
        // 2. 最老的 PENDING 消息年龄（秒）
        Instant oldestCreatedAt = outboxEventMapper.findOldestPendingCreatedAt();
        if (oldestCreatedAt != null) {
            long ageSeconds = Duration.between(oldestCreatedAt, Instant.now()).getSeconds();
            meterRegistry.gauge("outbox.pending.oldest.age.seconds", ageSeconds);
        }
        
        // 3. 投递速率（过去1分钟）
        long dispatchedLastMinute = outboxEventMapper.countDispatchedSince(
            Instant.now().minus(Duration.ofMinutes(1))
        );
        meterRegistry.gauge("outbox.dispatch.rate.per.minute", dispatchedLastMinute);
        
        // 4. 失败率（过去1分钟）
        long failedLastMinute = outboxEventMapper.countFailedSince(
            Instant.now().minus(Duration.ofMinutes(1))
        );
        meterRegistry.gauge("outbox.failure.rate.per.minute", failedLastMinute);
    }
}
```

#### 2.2 数据库查询支持

```java
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
    
    /**
     * 统计指定状态的事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
    
    /**
     * 查找最老的 PENDING 消息创建时间
     */
    @Select("SELECT MIN(created_at) FROM outbox_event WHERE status = 'PENDING'")
    Instant findOldestPendingCreatedAt();
    
    /**
     * 统计指定时间后投递成功的消息数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'DISPATCHED' AND dispatched_at >= #{since}")
    long countDispatchedSince(@Param("since") Instant since);
    
    /**
     * 统计指定时间后失败的消息数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'FAILED' AND updated_at >= #{since}")
    long countFailedSince(@Param("since") Instant since);
}
```

#### 2.3 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: outbox_backlog
    rules:
      # 告警 1：PENDING 消息堆积
      - alert: OutboxBacklogHigh
        expr: outbox_pending_count > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Outbox 消息堆积"
          description: "PENDING 消息数量: {{ $value }}，超过阈值 1000"
      
      # 告警 2：最老消息年龄过大
      - alert: OutboxOldestMessageTooOld
        expr: outbox_pending_oldest_age_seconds > 600
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Outbox 最老消息年龄过大"
          description: "最老 PENDING 消息已存在 {{ $value }} 秒（超过 10 分钟）"
      
      # 告警 3：投递速率下降
      - alert: OutboxDispatchRateLow
        expr: rate(outbox_dispatch_rate_per_minute[5m]) < 10
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Outbox 投递速率过低"
          description: "过去5分钟平均投递速率: {{ $value }} 条/分钟（低于 10 条/分钟）"
      
      # 告警 4：失败率过高
      - alert: OutboxFailureRateHigh
        expr: rate(outbox_failure_rate_per_minute[5m]) > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Outbox 投递失败率过高"
          description: "过去5分钟平均失败速率: {{ $value }} 条/分钟（超过 5 条/分钟）"
```

#### 2.4 Grafana 仪表板

```json
{
  "dashboard": {
    "title": "Outbox 监控",
    "panels": [
      {
        "title": "PENDING 消息数量",
        "targets": [
          {
            "expr": "outbox_pending_count"
          }
        ]
      },
      {
        "title": "最老消息年龄（秒）",
        "targets": [
          {
            "expr": "outbox_pending_oldest_age_seconds"
          }
        ]
      },
      {
        "title": "投递速率（条/分钟）",
        "targets": [
          {
            "expr": "rate(outbox_dispatch_rate_per_minute[5m])"
          }
        ]
      },
      {
        "title": "失败率（条/分钟）",
        "targets": [
          {
            "expr": "rate(outbox_failure_rate_per_minute[5m])"
          }
        ]
      }
    ]
  }
}
```

---

### 收口点 3：环境标识配置规范

**问题**：
当前 `LockKeys` 直接使用 `spring.profiles.active`，但该配置可能包含多个 profile（如 `prod,k8s`），导致锁键异常。

**解决方案**：

#### 3.1 推荐配置方式

**方式 1：使用专用 `app.env` 配置（推荐）**

```yaml
# application.yml
app:
  env: dev  # 明确的环境标识：dev | test | prod

spring:
  profiles:
    active: dev,common  # 可以包含多个 profile
```

**方式 2：从 `spring.profiles.active` 提取第一个 profile**

```java
@Component
public class LockKeys {
    
    /**
     * 环境标识
     * 
     * 注意：不直接使用 spring.profiles.active，因为它可能包含多个 profile（如 "prod,k8s"）
     * 推荐配置：在 application.yml 中添加 app.env=dev|test|prod
     * 
     * 如果未配置 app.env，则从 spring.profiles.active 中提取第一个 profile
     */
    @Value("${app.env:#{null}}")
    private String appEnv;
    
    @Value("${spring.profiles.active:dev}")
    private String profilesActive;
    
    @Value("${spring.application.name:zhicore-content}")
    private String serviceName;
    
    /**
     * 获取环境标识
     * 
     * 优先使用 app.env，如果未配置则从 spring.profiles.active 中提取第一个 profile
     */
    private String getEnv() {
        if (appEnv != null && !appEnv.isEmpty()) {
            return appEnv;
        }
        
        // 从 spring.profiles.active 中提取第一个 profile
        if (profilesActive != null && !profilesActive.isEmpty()) {
            String[] profiles = profilesActive.split(",");
            return profiles[0].trim();
        }
        
        return "dev";
    }
    
    /**
     * 构建锁键
     */
    private String buildKey(String category, String resource, String action) {
        return String.format("%s:%s:%s:%s:%s:lock", 
            getEnv(), serviceName, category, resource, action);
    }
}
```

#### 3.2 配置示例

**开发环境**：
```yaml
# application-dev.yml
app:
  env: dev

spring:
  profiles:
    active: dev,common,local
```

**测试环境**：
```yaml
# application-test.yml
app:
  env: test

spring:
  profiles:
    active: test,common
```

**生产环境**：
```yaml
# application-prod.yml
app:
  env: prod

spring:
  profiles:
    active: prod,k8s,common
```

#### 3.3 锁键示例

使用 `app.env` 后的锁键格式：

```
# 开发环境
dev:zhicore-content:outbox:dispatcher:dispatch:lock

# 测试环境
test:zhicore-content:outbox:dispatcher:dispatch:lock

# 生产环境
prod:zhicore-content:outbox:dispatcher:dispatch:lock
```

**优势**：
- 环境标识明确，不受 `spring.profiles.active` 多值影响
- 不同环境的锁键完全隔离
- 易于理解和维护

#### 3.4 迁移步骤

1. **添加 `app.env` 配置**
   - 在所有环境的配置文件中添加 `app.env`
   - 确保值为 `dev`、`test` 或 `prod`

2. **更新 `LockKeys` 实现**
   - 使用上述代码替换当前实现
   - 优先读取 `app.env`，回退到 `spring.profiles.active` 第一个值

3. **验证锁键格式**
   - 启动应用，检查日志中的锁键格式
   - 确认环境标识正确

4. **清理旧锁键（可选）**
   - 如果之前使用了错误的锁键格式，可以手动清理 Redis
   - 或等待锁自动过期

---

## 收口点实施优先级

| 收口点 | 优先级 | 实施难度 | 预计工作量 | 实施状态 | 影响范围 |
|-------|-------|---------|-----------|---------|---------|
| **收口点 3：环境标识配置** | P0（立即） | 低 | 1 小时 | ✅ 已完成 | 所有使用分布式锁的服务 |
| **收口点 2：Outbox 堆积保护** | P1（本周） | 中 | 4 小时 | ✅ 已完成 | Outbox 投递器 |
| **收口点 1：固定 TTL 配置化** | P2（本月） | 中 | 6 小时 | 📝 已文档化 | 所有使用固定 TTL 的调度器 |

**实施状态说明**：
- ✅ **收口点 3 已完成**：
  - `LockKeys.java` 已支持 `app.env` 优先，回退到 `spring.profiles.active` 第一个 profile
  - `application.yml` 已添加 `app.env: ${APP_ENV:dev}` 配置
  - 环境变量 `APP_ENV` 可在部署时设置为 dev/test/prod

- ✅ **收口点 2 已完成**：
  - `OutboxEventMapper` 已添加 4 个统计查询方法
  - `OutboxMetrics` 组件已创建，每分钟采集指标
  - Prometheus 告警规则已提供（`docs/prometheus-alerts-outbox.yml`）
  - Grafana 仪表板已提供（`docs/grafana-dashboard-outbox.json`）
  - 监控指标：
    - `outbox.pending.count` - PENDING 消息数量
    - `outbox.pending.oldest.age.seconds` - 最老消息年龄
    - `outbox.dispatch.rate.per.minute` - 投递速率
    - `outbox.failure.rate.per.minute` - 失败率

- 📝 **收口点 1 待实施**：
  - 文档已提供完整实现方案
  - 需要创建 `LockProperties` 配置类
  - 需要在调度器中添加执行时间指标采集
  - 建议在本月内完成

**部署检查清单**：
- [ ] 在生产环境配置文件中设置 `app.env: prod`
- [ ] 将 `prometheus-alerts-outbox.yml` 部署到 Prometheus
- [ ] 导入 `grafana-dashboard-outbox.json` 到 Grafana
- [ ] 配置 Alertmanager 接收器（钉钉/企业微信/邮件）
- [ ] 验证 Outbox 指标正常上报到 Prometheus

---

## 总结

### 核心要点

1. **安全性**：Redisson UUID + ThreadId 检查，绝不错误释放其他实例的锁
2. **看门狗机制**：不指定 leaseTime 启用自动续期
3. **锁键管理**：使用 LockKeys 组件，格式 `{env}:{service}:{category}:{resource}:{action}:lock`
4. **参数校验**：完整的参数验证，防止极端值
5. **监控标签**：使用任务名称而非完整锁键，避免高基数

### 设计原则

1. **安全第一**：绝不错误释放其他实例的锁
2. **简单易用**：统一的 API，易于集成
3. **可观测性**：完整的日志和监控
4. **优雅降级**：Redis 故障时不影响服务可用性（任务暂停）

### 锁策略选择

| 任务 | 策略 | 原因 |
|------|------|------|
| Outbox 投递 | 看门狗 | 批量大小不确定 |
| 消费事件清理 | 固定 TTL 5分钟 | 删除操作可预测 |
| 不完整文章清理 | 固定 TTL 5分钟 | 清理操作可预测 |
| 作者信息回填 | 看门狗 | 外部服务调用不确定 |

### 参考资料

- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [分布式锁的实现与优化](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
- [并发控制规范](../.kiro/steering/17-concurrency.md)
- [缓存规范](../.kiro/steering/16-cache.md)

---

**维护者**：ZhiCore Team  
**最后更新**：2026-02-23
