# Redis 缓存策略实现指南

## 概述

本文档详细说明了博客文章服务（ZhiCore-post）的 Redis 缓存策略实现，包括架构设计、实现细节、优化特性和最佳实践。

---

## 架构设计

### 设计模式

使用**装饰器模式（Decorator Pattern）**实现缓存层：
- `CachedDualStorageManager` 装饰 `DualStorageManagerImpl`
- `CachedDraftManager` 装饰 `DraftManagerImpl`
- 通过 `@Primary` 注解确保缓存版本优先注入

### 缓存策略：Cache-Aside 模式

采用 **Cache-Aside（旁路缓存）模式**：

#### 读操作流程
```
1. 应用查询缓存
2. 缓存命中 → 直接返回
3. 缓存未命中 → 查询数据库
4. 将数据库结果写入缓存
5. 返回数据
```

#### 写操作流程
```
1. 应用更新数据库
2. 删除相关缓存
3. 下次读取时重新加载最新数据
```

### 为什么先删除缓存再更新数据库？

**实际上我们采用的是：先更新数据库，再删除缓存**

这是 Cache-Aside 模式的标准做法，原因如下：

#### 1. 数据一致性保证

**正确做法（先更新数据库，再删除缓存）**：
```
时间线：
T1: 更新数据库成功
T2: 删除缓存成功
T3: 下次读取时从数据库加载最新数据

最坏情况：删除缓存失败
结果：缓存中是旧数据，但会在 TTL 过期后自动更新
影响：短期内可能读到旧数据（可接受）
```

**错误做法（先删除缓存，再更新数据库）**：
```
时间线：
T1: 删除缓存成功
T2: 另一个请求读取，缓存未命中
T3: 从数据库读取旧数据并写入缓存
T4: 更新数据库成功

结果：缓存中是旧数据，数据库是新数据
影响：长期数据不一致（直到 TTL 过期）
```

#### 2. 并发场景分析

**场景：两个并发请求（一个写，一个读）**

**先更新数据库，再删除缓存**：
```
线程A（写）          线程B（读）
更新数据库
                    查询缓存（命中旧数据）
                    返回旧数据
删除缓存

结果：线程B可能读到旧数据，但下次读取会是新数据
影响：短暂不一致，可接受
```

**先删除缓存，再更新数据库**：
```
线程A（写）          线程B（读）
删除缓存
                    查询缓存（未命中）
                    查询数据库（旧数据）
                    写入缓存（旧数据）
更新数据库

结果：缓存中是旧数据，数据库是新数据
影响：长期不一致，不可接受
```

#### 3. 为什么不更新缓存而是删除缓存？

**删除缓存的优势**：
1. **简单可靠**：只需要删除操作，不需要构造复杂的缓存对象
2. **避免浪费**：如果数据不再被访问，就不需要重新缓存
3. **减少竞态**：避免多个更新操作导致缓存数据错乱
4. **延迟加载**：只在需要时才加载最新数据

**更新缓存的问题**：
1. **复杂性高**：需要构造完整的缓存对象（可能涉及多表查询）
2. **浪费资源**：更新后的数据可能不再被访问
3. **竞态条件**：多个并发更新可能导致缓存数据错乱
4. **一致性难**：部分更新可能导致缓存对象不完整

#### 4. 实际代码示例

```java
@Override
public void updatePost(Post post, PostContent content) {
    // 1. 先更新数据库（保证数据持久化）
    delegate.updatePost(post, content);
    
    // 2. 再删除缓存（让下次读取时重新加载）
    evictCache(post.getId());
    
    // 如果删除缓存失败，最坏情况是短期内读到旧数据
    // 但 TTL 过期后会自动更新，不会造成长期不一致
}

private void evictCache(String postId) {
    try {
        // 删除所有相关缓存
        redisTemplate.delete(PostRedisKeys.content(postId));
        redisTemplate.delete(PostRedisKeys.fullDetail(postId));
        redisTemplate.delete(PostRedisKeys.detail(postId));
        log.debug("Evicted cache for post: {}", postId);
    } catch (Exception e) {
        // 删除失败不影响业务，只记录日志
        log.warn("Failed to evict cache for post: {}, error: {}", 
                 postId, e.getMessage());
    }
}
```

#### 5. 极端情况处理

**问题：如果删除缓存失败怎么办？**

**解决方案**：
1. **TTL 兜底**：所有缓存都有过期时间，最坏情况下会自动过期
2. **异步重试**：可以将删除操作放入消息队列异步重试
3. **监控告警**：监控缓存删除失败率，及时发现问题
4. **手动清理**：提供管理接口手动清理缓存

**代码示例**：
```java
private void evictCache(String postId) {
    try {
        redisTemplate.delete(PostRedisKeys.content(postId));
        redisTemplate.delete(PostRedisKeys.fullDetail(postId));
        redisTemplate.delete(PostRedisKeys.detail(postId));
    } catch (Exception e) {
        log.warn("Failed to evict cache, will retry asynchronously", e);
        // 发送到消息队列异步重试
        messageQueue.send(new CacheEvictMessage(postId));
    }
}
```

---

## 实现细节

### 1. 文章内容缓存（热点数据）

**实现类**: `CachedDualStorageManager`

**缓存方法**: `getPostContent(String postId)`

**缓存配置**:
- **Key**: `post:{postId}:content`
- **TTL**: 600秒（10分钟）+ 随机抖动（0-60秒）
- **序列化**: Jackson JSON

**特性**:
- 空值缓存防止缓存穿透（TTL: 60秒）
- 随机抖动防止缓存雪崩
- 缓存失败时优雅降级到数据库查询

**代码示例**:
```java
@Override
public PostContent getPostContent(String postId) {
    String key = PostRedisKeys.content(postId);
    
    try {
        // 1. 查询缓存
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                return null; // 空值缓存
            }
            log.debug("Cache hit for content: key={}", key);
            return (PostContent) cached;
        }
        
        // 2. 缓存未命中，查询数据库
        log.debug("Cache miss for content: key={}", key);
        PostContent content = delegate.getPostContent(postId);
        
        // 3. 写入缓存
        if (content != null) {
            long ttlWithJitter = cacheProperties.getEntityDetail() + randomJitter();
            redisTemplate.opsForValue().set(key, content, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached content: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            // 空值缓存
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        
        return content;
    } catch (Exception e) {
        // 4. 异常降级
        log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
        return delegate.getPostContent(postId);
    }
}
```

### 2. 文章详情缓存

**实现类**: `CachedDualStorageManager`

**缓存方法**: `getPostFullDetail(String postId)`

**缓存配置**:
- **Key**: `post:{postId}:full`
- **TTL**: 600秒（10分钟）+ 随机抖动（0-60秒）
- **内容**: PostgreSQL 元数据 + MongoDB 内容的聚合数据

**特性**:
- 缓存完整的文章详情（元数据 + 内容）
- 减少跨数据库查询次数
- 空值缓存防止缓存穿透

### 3. 草稿缓存

**实现类**: `CachedDraftManager`

**缓存方法**: 
- `getLatestDraft(String postId, String userId)` - 单个草稿
- `getUserDrafts(String userId)` - 用户草稿列表

**缓存配置**:
- **单个草稿 Key**: `post:draft:{postId}:{userId}`
- **单个草稿 TTL**: 300秒（5分钟）+ 随机抖动
- **草稿列表 Key**: `post:drafts:{userId}`
- **草稿列表 TTL**: 180秒（3分钟）+ 随机抖动

**特性**:
- 较短的 TTL（草稿频繁更新）
- 保存草稿时删除缓存（而非更新）避免不一致
- 空值缓存防止缓存穿透

### 4. 缓存失效策略

#### 文章更新时
```java
@Override
public void updatePost(Post post, PostContent content) {
    // 1. 更新数据库
    delegate.updatePost(post, content);
    
    // 2. 删除缓存
    evictCache(post.getId());
}
```

**删除的缓存**:
- `post:{postId}:content` - 文章内容
- `post:{postId}:full` - 文章完整详情
- `post:{postId}:detail` - 文章元数据

#### 文章删除时
```java
@Override
public void deletePost(String postId) {
    // 1. 删除数据库
    delegate.deletePost(postId);
    
    // 2. 删除缓存
    evictCache(postId);
}
```

#### 草稿保存时
```java
@Override
public void saveDraft(String postId, String userId, String content, boolean isAutoSave) {
    // 1. 保存到数据库
    delegate.saveDraft(postId, userId, content, isAutoSave);
    
    // 2. 删除缓存
    evictDraftCache(postId, userId);
}
```

**删除的缓存**:
- `post:draft:{postId}:{userId}` - 单个草稿
- `post:drafts:{userId}` - 用户草稿列表

---

## 缓存优化特性

### 1. 防止缓存穿透

**问题**: 查询不存在的数据导致每次都访问数据库

**解决方案**: 缓存空值
```java
if (content == null) {
    // 缓存空值，TTL: 60秒
    redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
            CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
}
```

### 2. 防止缓存雪崩

**问题**: 大量缓存同时过期导致数据库压力激增

**解决方案**: 随机抖动
```java
private int randomJitter() {
    return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
}

long ttlWithJitter = cacheProperties.getEntityDetail() + randomJitter();
```

### 3. 优雅降级

**问题**: Redis 不可用时服务中断

**解决方案**: 捕获异常并降级到数据库查询
```java
try {
    // 查询缓存
    Object cached = redisTemplate.opsForValue().get(key);
    // ...
} catch (Exception e) {
    log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
    return delegate.getPostContent(postId);
}
```

### 4. 缓存预热

提供缓存预热方法，用于系统启动或热点数据识别后主动加载：

```java
// 预热文章内容缓存
public void warmUpContentCache(String postId);

// 预热文章完整详情缓存
public void warmUpFullDetailCache(String postId);

// 预热草稿缓存
public void warmUpDraftCache(String postId, String userId);

// 预热用户草稿列表缓存
public void warmUpUserDraftsCache(String userId);
```

---

## Redis Key 设计

### Key 命名规范

格式: `{service}:{entity}:{id}:{field}`

### Key 定义

所有 Key 定义在 `PostRedisKeys` 类中：

```java
// 文章内容
post:{postId}:content

// 文章完整详情
post:{postId}:full

// 文章元数据
post:{postId}:detail

// 单个草稿
post:draft:{postId}:{userId}

// 用户草稿列表
post:drafts:{userId}
```

---

## 配置管理

### 缓存 TTL 配置

**配置文件**: `application.yml`

```yaml
cache:
  ttl:
    entity-detail: 600      # 实体详情缓存 TTL（秒）
    list: 300               # 列表缓存 TTL（秒）
    stats: -1               # 统计数据缓存 TTL（秒），-1 表示永久
    session: 604800         # 会话缓存 TTL（秒）
    hot-data: 3600          # 热点数据缓存 TTL（秒）
```

### Redis 连接配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis123456}
      database: 0
```

---

## 性能优化效果

### 预期性能提升

1. **文章详情查询**:
   - 缓存命中: < 10ms（Redis 查询）
   - 缓存未命中: < 200ms（数据库查询 + 缓存写入）
   - 缓存命中率预期: > 80%

2. **文章内容查询**:
   - 缓存命中: < 5ms（Redis 查询）
   - 缓存未命中: < 150ms（MongoDB 查询 + 缓存写入）
   - 缓存命中率预期: > 70%

3. **草稿查询**:
   - 缓存命中: < 5ms（Redis 查询）
   - 缓存未命中: < 100ms（MongoDB 查询 + 缓存写入）
   - 缓存命中率预期: > 60%（草稿更新频繁）

### 数据库压力降低

- PostgreSQL 查询减少: 70-80%
- MongoDB 查询减少: 60-70%
- 整体响应时间降低: 50-70%

---

## 监控和日志

### 日志记录

所有缓存操作都有详细的日志记录：

```java
log.debug("Cache hit for content: key={}", key);
log.debug("Cache miss for content: key={}", key);
log.debug("Cached content: key={}, ttl={}s", key, ttlWithJitter);
log.debug("Evicted cache for post: {}", postId);
log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
```

### 监控指标

建议监控以下指标：
- 缓存命中率
- 缓存未命中率
- 缓存查询响应时间
- 缓存失效次数
- Redis 连接失败次数

---

## 最佳实践

### 1. 缓存 Key 设计

- ✅ 使用统一的命名规范
- ✅ 包含服务名、实体类型、ID
- ✅ 使用分隔符便于识别
- ✅ 集中管理在 `PostRedisKeys` 类中

### 2. TTL 设置

- ✅ 根据数据更新频率设置不同的 TTL
- ✅ 添加随机抖动防止缓存雪崩
- ✅ 空值使用较短的 TTL

### 3. 缓存更新策略

- ✅ 使用 Cache-Aside 模式
- ✅ 写操作先更新数据库再删除缓存
- ✅ 删除缓存而非更新缓存
- ✅ 避免缓存和数据库不一致

### 4. 异常处理

- ✅ 捕获 Redis 异常并降级
- ✅ 记录详细的日志
- ✅ 不影响核心业务流程

### 5. 监控和告警

- ✅ 监控缓存命中率
- ✅ 监控 Redis 连接状态
- ✅ 设置告警阈值

---

## 并发问题分析与解决方案

### 当前实现的并发问题

**目前的实现没有加锁**，在多实例部署时可能出现以下并发问题：

#### 问题 1: 缓存击穿（Cache Breakdown）

**场景**：热点数据过期时，多个请求同时查询

```
时间线（3个实例同时查询同一个过期的热点文章）：

实例A                实例B                实例C
查询缓存（未命中）
                    查询缓存（未命中）
                                        查询缓存（未命中）
查询数据库
                    查询数据库
                                        查询数据库
写入缓存
                    写入缓存
                                        写入缓存

结果：3次数据库查询，3次缓存写入（浪费资源）
```

**影响**：
- 数据库瞬时压力增大
- 资源浪费（重复查询和写入）
- 响应时间变长

**解决方案**：使用 Redisson 分布式锁（详见下文"推荐方案：使用 Redisson 分布式锁"）

#### 问题 2: 缓存不一致（Cache Inconsistency）

**场景**：并发读写导致缓存数据错乱

```
时间线（实例A更新，实例B读取）：

实例A（写）          实例B（读）
更新数据库（新数据）
                    查询缓存（未命中）
                    查询数据库（新数据）
删除缓存
                    写入缓存（新数据）✅

结果：正常情况，缓存是新数据
```

**但如果网络延迟或执行顺序不同**：

```
时间线（极端情况）：

实例A（写）          实例B（读）
更新数据库（新数据）
删除缓存
                    查询缓存（未命中）
                    查询数据库（新数据）
                    写入缓存（新数据）✅

结果：正常情况，缓存是新数据
```

**真正的问题场景（双写不一致）**：

```
时间线（两个实例同时更新同一篇文章）：

实例A（写）          实例B（写）
更新数据库（版本1）
                    更新数据库（版本2，覆盖版本1）
删除缓存
                    删除缓存
                    
下次读取：
实例C（读）
查询缓存（未命中）
查询数据库（版本2）
写入缓存（版本2）✅

结果：正常情况，缓存是最新版本
```

### 你的项目会出现这些问题吗？

**会的！** 你的项目是微服务架构，很可能会多实例部署：

1. **ZhiCore-post 服务多实例部署**：
   - 负载均衡下有多个实例
   - 同一篇热点文章可能被多个实例同时查询

2. **高并发场景**：
   - 热点文章发布后大量用户访问
   - 缓存过期时多个请求同时到达

3. **并发更新场景**：
   - 虽然同一用户不太可能同时更新同一篇文章
   - 但管理员操作、自动化任务可能导致并发更新

### 解决方案

#### 推荐方案：使用 Redisson 分布式锁（用于缓存击穿）

**为什么选择 Redisson？**

项目已经集成了 Redisson，它提供了以下优势：
- ✅ **自动续期**：看门狗（Watchdog）机制自动延长锁的过期时间
- ✅ **可重入**：同一线程可以多次获取同一把锁
- ✅ **公平锁**：支持公平锁，按请求顺序获取锁
- ✅ **红锁（RedLock）**：支持多 Redis 实例的分布式锁
- ✅ **简单易用**：API 简洁，无需手动管理锁的续期和释放

**实现示例**：

```java
package com.zhicore.post.infrastructure.service;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.post.domain.service.DualStorageManager;
import com.zhicore.post.infrastructure.cache.PostRedisKeys;
import com.zhicore.post.infrastructure.mongodb.document.PostContent;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Redisson 的缓存管理器（防止缓存击穿）
 */
@Slf4j
@Primary
@Service
public class CachedDualStorageManagerWithRedisson implements DualStorageManager {

    private final DualStorageManager delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;

    // 锁的等待时间（秒）
    private static final int LOCK_WAIT_TIME = 3;
    // 锁的持有时间（秒）
    private static final int LOCK_LEASE_TIME = 10;

    public CachedDualStorageManagerWithRedisson(
            RedisTemplate<String, Object> redisTemplate,
            RedissonClient redissonClient,
            CacheProperties cacheProperties,
            @Qualifier("dualStorageManagerImpl") DualStorageManager delegate) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PostContent getPostContent(Long postId) {
        String key = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.contentLock(postId);

        try {
            // 1. 第一次查缓存
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return handleCachedValue(cached, "content", key);
            }

            // 2. 获取 Redisson 锁
            RLock lock = redissonClient.getLock(lockKey);

            try {
                // 3. 尝试获取锁（等待3秒，持有10秒）
                boolean lockAcquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

                if (lockAcquired) {
                    try {
                        // 4. 双重检查缓存（DCL）
                        cached = redisTemplate.opsForValue().get(key);
                        if (cached != null) {
                            return handleCachedValue(cached, "content", key);
                        }

                        // 5. 查询数据库
                        log.debug("Cache miss for content, loading from database: key={}", key);
                        PostContent content = delegate.getPostContent(postId);

                        // 6. 写缓存
                        cacheContent(key, content);

                        return content;
                    } finally {
                        // 7. 释放锁
                        lock.unlock();
                        log.debug("Released lock: key={}", lockKey);
                    }
                } else {
                    // 8. 获取锁超时，降级查询数据库
                    log.warn("Failed to acquire lock within {}s, falling back to database: postId={}", 
                             LOCK_WAIT_TIME, postId);
                    return delegate.getPostContent(postId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while acquiring lock: {}", e.getMessage());
                return delegate.getPostContent(postId);
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
            return delegate.getPostContent(postId);
        }
    }

    /**
     * 处理缓存值
     */
    @SuppressWarnings("unchecked")
    private PostContent handleCachedValue(Object cached, String type, String key) {
        if (CacheConstants.NULL_VALUE.equals(cached)) {
            log.debug("Cache hit (null value) for {}: key={}", type, key);
            return null;
        }
        log.debug("Cache hit for {}: key={}", type, key);
        return (PostContent) cached;
    }

    /**
     * 缓存文章内容
     */
    private void cacheContent(String key, PostContent content) {
        if (content != null) {
            long ttlWithJitter = cacheProperties.getEntityDetail() + randomJitter();
            redisTemplate.opsForValue().set(key, content, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached content: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached null value for content: key={}", key);
        }
    }

    /**
     * 生成随机抖动值
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    // ... 其他方法实现
}
```

**Redis Key 定义**：

在 `PostRedisKeys` 类中添加锁 Key：

```java
/**
 * 文章内容锁 Key
 * Key: post:lock:content:{postId}
 */
public static String contentLock(Long postId) {
    return String.format("%s:lock:content:%s", PREFIX, postId);
}

/**
 * 文章详情锁 Key
 * Key: post:lock:full:{postId}
 */
public static String fullDetailLock(Long postId) {
    return String.format("%s:lock:full:%s", PREFIX, postId);
}
```

**关键点说明**：

1. **双重检查锁（DCL）**：获取锁后再次检查缓存，避免重复查询
2. **超时降级**：获取锁超时后直接查询数据库，不阻塞请求
3. **自动续期**：Redisson 的看门狗机制自动延长锁的过期时间
4. **异常处理**：捕获所有异常并降级到数据库查询

**优点**：
- 防止缓存击穿
- 减少数据库压力
- 自动续期和看门狗机制
- 可重入锁支持

**缺点**：
- 增加复杂度
- 需要处理锁超时
- 可能影响响应时间（等待锁）

#### 备选方案 1: 简单的 Redis 分布式锁（不推荐）

如果不想使用 Redisson，可以使用简单的 Redis 分布式锁：

```java
/**
 * 使用分布式锁的缓存查询（简单实现，不推荐）
 */
@SuppressWarnings("unchecked")
public PostContent getPostContent(Long postId) {
    String key = PostRedisKeys.content(postId);
    String lockKey = PostRedisKeys.contentLock(postId);

    try {
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                return null;
            }
            log.debug("Cache hit for content: key={}", key);
            return (PostContent) cached;
        }

        // 2. 缓存未命中，尝试获取锁
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // 3. 获取锁成功，双重检查缓存（可能其他线程已经加载）
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        return null;
                    }
                    return (PostContent) cached;
                }

                // 4. 查询数据库
                log.debug("Cache miss for content, loading from database: key={}", key);
                PostContent content = delegate.getPostContent(postId);

                // 5. 写缓存
                cacheContent(key, content);

                return content;
            } finally {
                // 6. 释放锁
                redisTemplate.delete(lockKey);
            }
        } else {
            // 7. 获取锁失败，等待后重试
            log.debug("Failed to acquire lock, waiting for cache to be populated: key={}", key);
            Thread.sleep(50); // 等待 50ms
            
            // 8. 重试查询缓存
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !CacheConstants.NULL_VALUE.equals(cached)) {
                return (PostContent) cached;
            }
            
            // 9. 仍未命中，降级查询数据库（不写缓存，避免竞争）
            return delegate.getPostContent(postId);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Thread interrupted while waiting for lock: {}", e.getMessage());
        return delegate.getPostContent(postId);
    } catch (Exception e) {
        log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
        return delegate.getPostContent(postId);
    }
}
```

**缺点**：
- 无自动续期，长时间查询可能导致锁过期
- 无可重入性，嵌套调用会死锁
- 需要手动管理锁的释放
- 异常处理复杂

**不推荐原因**：项目已有 Redisson，应该使用 Redisson 的分布式锁。

#### 备选方案 2: 延迟双删（推荐用于缓存一致性）

在更新操作中使用延迟双删策略：

```java
@Override
public void updatePost(Post post, PostContent content) {
    Long postId = post.getId();
    
    try {
        // 1. 第一次删除缓存
        evictCache(postId);
        
        // 2. 更新数据库
        delegate.updatePost(post, content);
        
        // 3. 延迟后再次删除缓存（异步）
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500); // 延迟 500ms
                evictCache(postId);
                log.debug("Delayed cache eviction completed for post: {}", postId);
            } catch (Exception e) {
                log.warn("Delayed cache eviction failed for post {}: {}", postId, e.getMessage());
            }
        });
        
    } catch (Exception e) {
        log.error("Failed to update post: {}", e.getMessage(), e);
        throw e;
    }
}
```

**为什么需要延迟双删？**

```
时间线（解决并发读写问题）：

实例A（写）          实例B（读）
第一次删除缓存
                    查询缓存（未命中）
更新数据库
                    查询数据库（可能是旧数据）
                    写入缓存（旧数据）❌
延迟 500ms
第二次删除缓存      ← 删除实例B写入的旧数据

下次读取：
实例C（读）
查询缓存（未命中）
查询数据库（新数据）
写入缓存（新数据）✅
```

**优点**：
- 解决并发读写不一致
- 实现简单
- 不影响主流程性能

**缺点**：
- 延迟时间难以确定
- 仍有小概率不一致
- 增加系统复杂度

#### 备选方案 4: 设置合理的 TTL（最简单）

**当前方案已经采用**，通过较短的 TTL 降低不一致影响：

```java
// 文章内容缓存：10分钟 + 随机抖动
long ttlWithJitter = cacheProperties.getEntityDetail() + randomJitter();

// 草稿缓存：5分钟（更新频繁）
long draftTtl = 300 + randomJitter();
```

**优点**：
- 实现简单
- 不一致影响时间有限
- 适合大多数场景

**缺点**：
- 无法完全避免不一致
- 热点数据频繁过期影响性能

#### 备选方案 3: 使用消息队列（强烈推荐用于缓存一致性）

**这是最优方案，解决了延迟双删的 CPU 空转问题！**

通过消息队列通知所有实例删除缓存：

```java
@Override
public void updatePost(Post post, PostContent content) {
    Long postId = post.getId();
    
    // 1. 更新数据库
    delegate.updatePost(post, content);
    
    // 2. 发送 MQ 消息（异步，不阻塞）
    PostUpdatedEvent event = new PostUpdatedEvent(postId, title, content);
    rocketMQTemplate.asyncSend(
        TopicConstants.TOPIC_POST_EVENTS + ":" + TopicConstants.TAG_POST_UPDATED,
        event,
        callback
    );
    
    // 3. 立即返回，不等待
}

// 消息消费者（所有实例都会收到）
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_UPDATED,
    consumerGroup = "post-cache-evict-consumer"
)
public class PostUpdatedCacheEvictConsumer extends AbstractEventConsumer<PostUpdatedEvent> {
    
    @Override
    protected void doHandle(PostUpdatedEvent event) {
        Long postId = event.getPostId();
        
        // 删除本地实例的缓存
        redisTemplate.delete(PostRedisKeys.content(postId));
        redisTemplate.delete(PostRedisKeys.fullDetail(postId));
        redisTemplate.delete(PostRedisKeys.detail(postId));
        
        log.info("Evicted cache for post: {}", postId);
    }
}
```

**优点**：
- ✅ **无 CPU 空转**：消息队列异步处理，不占用业务线程
- ✅ **分布式支持**：所有实例同步删除缓存
- ✅ **高可靠性**：消息持久化，支持重试
- ✅ **解耦**：缓存逻辑与业务逻辑分离
- ✅ **可扩展**：可以添加更多消费者处理其他逻辑
- ✅ **复用现有事件**：项目已有完善的事件体系

**缺点**：
- 需要依赖 RocketMQ（项目已有）
- 可能有消息延迟（通常 < 100ms）

**详细实现**：参见 [基于消息队列的缓存更新方案](./cache-update-with-mq.md)

### 推荐方案组合

根据你的项目特点，推荐以下组合：

#### 1. 对于热点数据查询（防止缓存击穿）

**使用分布式锁 + 合理 TTL**：
- 热点文章使用分布式锁
- 普通文章直接查询（性能优先）
- 通过访问统计识别热点数据

```java
public PostContent getPostContent(Long postId) {
    // 判断是否为热点数据
    if (isHotPost(postId)) {
        return getPostContentWithLock(postId); // 使用分布式锁
    } else {
        return getPostContentNormal(postId);   // 普通查询
    }
}
```

#### 2. 对于更新操作（防止缓存不一致）

**使用 MQ 消息队列（强烈推荐）**：
- 主流程：更新数据库后发送 MQ 消息
- 异步：所有实例消费消息并删除缓存
- 优势：无 CPU 空转，支持分布式，可靠性高

```java
@Override
public void updatePost(Post post, PostContent content) {
    // 1. 更新数据库
    delegate.updatePost(post, content);
    
    // 2. 发送 MQ 消息（异步）
    sendCacheEvictMessage(postId, post, content);
    
    // 3. 立即返回，不阻塞
}
```

**备选方案：延迟双删**（如果不想引入 MQ）：
- 主流程：先更新数据库，再删除缓存
- 异步：延迟双删确保一致性
- 缺点：CPU 空转，不支持分布式

#### 3. 对于草稿等频繁更新的数据

**使用较短 TTL + MQ 消息**：
- TTL: 3-5分钟
- 更新时发送 MQ 消息删除缓存
- 接受短暂不一致

### 性能影响对比

| 方案 | 查询性能 | 更新性能 | 一致性 | 复杂度 | CPU 使用 |
|------|---------|---------|--------|--------|---------|
| 无锁（当前） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐⭐ |
| 分布式锁 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| 延迟双删 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ (空转) |
| 消息队列 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

**推荐**: 消息队列方案（无 CPU 空转，性能最优）

### 何时需要加锁？

**需要加锁的场景**：
1. ✅ 热点数据（访问量大）
2. ✅ 缓存构建成本高（复杂查询、多表关联）
3. ✅ 数据库压力大
4. ✅ 对一致性要求高

**不需要加锁的场景**：
1. ✅ 普通数据（访问量小）
2. ✅ 缓存构建成本低（单表查询）
3. ✅ 数据库压力小
4. ✅ 可接受短暂不一致

### 建议

对于你的项目，建议：

1. **短期方案**（立即实施）：
   - **使用 MQ 消息队列方案**（强烈推荐）
   - 复用现有的 PostUpdatedEvent 事件
   - 创建 PostUpdatedCacheEvictConsumer 消费者
   - 无 CPU 空转，支持分布式，可靠性高
   - 详见：[基于消息队列的缓存更新方案](./cache-update-with-mq.md)

2. **中期优化**（如果出现性能问题）：
   - 为热点文章添加分布式锁（使用 Redisson）
   - 优化消息发送和消费逻辑
   - 增加缓存监控和告警

3. **长期优化**（高并发场景）：
   - 考虑使用 CDC（Change Data Capture）
   - 实现缓存预热机制
   - 使用 Redisson 等成熟框架

---

## 故障排查

### 常见问题

#### 1. Redis 连接失败

**症状**: 应用启动失败或缓存不工作

**排查步骤**:
```bash
# 检查 Redis 是否运行
docker ps | grep redis

# 检查 Redis 连接
redis-cli -h localhost -p 6379 -a redis123456 ping

# 检查应用配置
cat application.yml | grep redis
```

#### 2. 缓存未生效

**症状**: 每次查询都访问数据库

**排查步骤**:
```bash
# 查看日志
tail -f logs/ZhiCore-post.log | grep -i "cache miss"

# 检查 Redis 中是否有数据
redis-cli -h localhost -p 6379 -a redis123456
> KEYS post:*
```

#### 3. 缓存不一致

**症状**: 更新后仍然返回旧数据

**排查步骤**:
```bash
# 手动删除缓存
redis-cli -h localhost -p 6379 -a redis123456
> DEL post:123:content
> DEL post:123:full

# 检查缓存失效逻辑
# 确保 updatePost() 调用了 evictCache()
```

---

## 相关文件

### 实现文件
- `ZhiCore-post/src/main/java/com/ZhiCore/post/infrastructure/service/CachedDualStorageManager.java`
- `ZhiCore-post/src/main/java/com/ZhiCore/post/infrastructure/service/CachedDraftManager.java`
- `ZhiCore-post/src/main/java/com/ZhiCore/post/infrastructure/cache/PostRedisKeys.java`

### 配置文件
- `ZhiCore-common/src/main/java/com/ZhiCore/common/config/RedisConfig.java`
- `ZhiCore-common/src/main/java/com/ZhiCore/common/config/CacheProperties.java`
- `ZhiCore-common/src/main/java/com/ZhiCore/common/cache/CacheConstants.java`
- `ZhiCore-post/src/main/resources/application.yml`

---

**文档版本**: 1.0  
**最后更新**: 2025-01-26  
**作者**: ZhiCore Team
