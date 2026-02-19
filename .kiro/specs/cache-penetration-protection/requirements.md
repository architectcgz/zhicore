# Requirements Document

## Introduction

本文档定义了博客微服务系统中缓存击穿防护功能的需求。缓存击穿（Cache Breakdown）是指热点数据在缓存过期的瞬间，大量并发请求同时访问数据库，导致数据库压力激增的问题。本需求旨在通过 Redisson 分布式锁机制，在关键的缓存查询场景中防止缓存击穿，提升系统稳定性和性能。

## Glossary

- **System**: 博客微服务系统
- **Cache_Manager**: 缓存管理器，负责缓存的读写和失效
- **Distributed_Lock**: 分布式锁，基于 Redisson 实现的跨实例锁机制
- **Hot_Data**: 热点数据，访问频率高的数据（如热门文章、热门评论）
- **Cache_Breakdown**: 缓存击穿，热点数据缓存过期时大量请求同时访问数据库
- **Lock_Key**: 锁键，用于标识分布式锁的唯一键
- **TTL**: Time To Live，缓存过期时间
- **Redisson**: 基于 Redis 的 Java 分布式框架
- **Repository**: 仓储层，负责数据持久化和查询

## Requirements

### Requirement 1: 文章内容缓存击穿防护（热点数据）

**User Story:** 作为系统管理员，我希望在热门文章缓存过期时防止大量请求同时访问数据库，以保证系统稳定性。

#### Acceptance Criteria

1. WHEN 多个请求同时查询已过期的热点文章内容 THEN THE System SHALL 只允许一个请求获取 Redisson 锁并查询数据库
2. WHEN 获取锁成功后 THEN THE System SHALL 执行双重检查（DCL）确认缓存是否已被其他线程填充
3. WHEN 第一个请求成功加载数据并写入缓存后 THEN THE System SHALL 让其他等待的请求从缓存中读取数据
4. WHEN 获取分布式锁超时（5秒） THEN THE System SHALL 降级直接查询数据库而不阻塞请求
5. WHEN 分布式锁持有时间超过阈值（10秒） THEN THE Redisson SHALL 自动释放锁防止死锁
6. WHEN 查询数据库失败 THEN THE System SHALL 释放锁并返回错误，不缓存错误结果
7. WHEN 查询到空值 THEN THE System SHALL 缓存空值（60秒 TTL）防止缓存穿透
8. WHEN 锁释放时 THEN THE System SHALL 使用 try-finally 确保锁一定被释放

**技术说明:**
- 适用于 `CachedDualStorageManager.getPostContent()` 方法
- 适用于 `CachedDualStorageManager.getPostFullDetail()` 方法
- 锁键格式：`post:lock:content:{postId}` 和 `post:lock:full:{postId}`
- 使用 `RedissonClient.getLock()` 获取可重入锁
- 使用 `lock.tryLock(waitTime, leaseTime, TimeUnit)` 尝试获取锁

### Requirement 2: 用户信息缓存击穿防护（热点数据）

**User Story:** 作为系统管理员，我希望在热门用户信息缓存过期时防止数据库压力激增，以提升用户体验。

#### Acceptance Criteria

1. WHEN 多个请求同时查询已过期的热点用户信息 THEN THE System SHALL 只允许一个请求获取 Redisson 锁并查询数据库
2. WHEN 获取锁成功后 THEN THE System SHALL 执行双重检查（DCL）确认缓存是否已被其他线程填充
3. WHEN 用户信息不存在 THEN THE System SHALL 缓存空值（60秒 TTL）并设置较短的 TTL
4. WHEN 获取锁失败（超时 5秒） THEN THE System SHALL 降级直接查询数据库
5. WHEN 锁持有时间超过阈值（10秒） THEN THE Redisson SHALL 自动释放锁
6. WHEN 锁释放失败 THEN THE System SHALL 记录日志并依赖锁的自动过期机制
7. WHEN 查询数据库成功 THEN THE System SHALL 写入缓存（TTL + 随机抖动）后释放锁
8. WHEN 锁释放时 THEN THE System SHALL 使用 try-finally 确保锁一定被释放

**技术说明:**
- 适用于 `CachedUserRepository.findById()` 方法
- 锁键格式：`user:lock:detail:{userId}`
- 使用 `RedissonClient.getLock()` 获取可重入锁
- 使用 `lock.tryLock(waitTime, leaseTime, TimeUnit)` 尝试获取锁

### Requirement 3: 评论详情缓存击穿防护（热点数据）

**User Story:** 作为系统管理员，我希望在热门评论缓存过期时防止缓存击穿，以保证评论系统的稳定性。

#### Acceptance Criteria

1. WHEN 多个请求同时查询已过期的热点评论 THEN THE System SHALL 使用 Redisson 分布式锁防止并发查询数据库
2. WHEN 获取锁成功后 THEN THE System SHALL 执行双重检查（DCL）确认缓存是否已被其他线程填充
3. WHEN 评论已被删除 THEN THE System SHALL 缓存空值（60秒 TTL）并设置较短的 TTL
4. WHEN 分布式锁获取成功 THEN THE System SHALL 在查询数据库前进行双重检查缓存
5. WHEN 数据加载完成 THEN THE System SHALL 先写入缓存再释放锁
6. WHEN 缓存写入失败 THEN THE System SHALL 释放锁并记录错误日志
7. WHEN 获取锁超时（5秒） THEN THE System SHALL 降级直接查询数据库
8. WHEN 锁释放时 THEN THE System SHALL 使用 try-finally 确保锁一定被释放

**技术说明:**
- 适用于 `CachedCommentRepository.findById()` 方法
- 锁键格式：`comment:lock:detail:{commentId}`
- 使用 `RedissonClient.getLock()` 获取可重入锁
- 使用 `lock.tryLock(waitTime, leaseTime, TimeUnit)` 尝试获取锁

### Requirement 4: 分布式锁配置管理

**User Story:** 作为开发人员，我希望能够灵活配置分布式锁的参数，以适应不同的业务场景。

#### Acceptance Criteria

1. THE System SHALL 支持配置锁的等待时间（wait time）
2. THE System SHALL 支持配置锁的持有时间（lease time）
3. THE System SHALL 支持配置锁的重试次数
4. THE System SHALL 支持配置锁的重试间隔时间
5. THE System SHALL 支持为不同的业务场景配置不同的锁参数

### Requirement 5: 锁键命名规范

**User Story:** 作为开发人员，我希望有统一的锁键命名规范，以便于管理和监控。

#### Acceptance Criteria

1. THE System SHALL 使用格式 `{service}:lock:{entity}:{id}` 作为锁键命名规范
2. WHEN 创建文章内容锁键 THEN THE System SHALL 使用格式 `post:lock:content:{postId}`
3. WHEN 创建文章完整详情锁键 THEN THE System SHALL 使用格式 `post:lock:full:{postId}`
4. WHEN 创建用户信息锁键 THEN THE System SHALL 使用格式 `user:lock:detail:{userId}`
5. WHEN 创建评论详情锁键 THEN THE System SHALL 使用格式 `comment:lock:detail:{commentId}`
6. THE System SHALL 在各服务的 RedisKeys 工具类中集中管理所有锁键定义

**技术说明:**
- 在 `PostRedisKeys` 中添加 `lockContent(Long postId)` 和 `lockFullDetail(Long postId)` 方法
- 在 `UserRedisKeys` 中添加 `lockDetail(Long userId)` 方法
- 在 `CommentRedisKeys` 中添加 `lockDetail(Long commentId)` 方法

### Requirement 6: 锁的自动续期

**User Story:** 作为系统管理员，我希望长时间运行的查询不会因为锁过期而导致问题，以保证数据一致性。

#### Acceptance Criteria

1. WHEN 使用 Redisson 锁 THEN THE System SHALL 自动启用看门狗（Watchdog）机制
2. WHEN 锁持有时间接近过期 THEN THE System SHALL 自动延长锁的过期时间
3. WHEN 业务逻辑执行完成 THEN THE System SHALL 立即释放锁停止续期
4. WHEN 应用实例崩溃 THEN THE System SHALL 依赖锁的自动过期机制释放锁
5. THE System SHALL 记录锁的续期次数用于监控

### Requirement 7: 锁的可重入性

**User Story:** 作为开发人员，我希望同一线程可以多次获取同一把锁，以支持嵌套调用场景。

#### Acceptance Criteria

1. WHEN 同一线程多次获取同一把锁 THEN THE System SHALL 允许重入并增加持有计数
2. WHEN 释放可重入锁 THEN THE System SHALL 减少持有计数直到计数为零才真正释放
3. WHEN 不同线程尝试获取已被持有的锁 THEN THE System SHALL 阻塞或返回失败
4. THE System SHALL 记录锁的重入次数用于调试
5. WHEN 锁的持有计数不匹配 THEN THE System SHALL 记录警告日志

### Requirement 8: 异常处理和降级

**User Story:** 作为系统管理员，我希望在分布式锁不可用时系统能够优雅降级，以保证服务可用性。

#### Acceptance Criteria

1. WHEN Redis 连接失败 THEN THE System SHALL 降级直接查询数据库并记录错误
2. WHEN 获取锁超时 THEN THE System SHALL 降级直接查询数据库而不阻塞请求
3. WHEN 释放锁失败 THEN THE System SHALL 记录错误日志并依赖锁的自动过期
4. WHEN 分布式锁服务不可用 THEN THE System SHALL 不影响核心业务流程
5. THE System SHALL 监控锁相关异常并触发告警

### Requirement 9: 性能监控和指标

**User Story:** 作为运维人员，我希望能够监控分布式锁的使用情况，以便及时发现和解决问题。

#### Acceptance Criteria

1. THE System SHALL 记录锁获取成功次数
2. THE System SHALL 记录锁获取失败次数
3. THE System SHALL 记录锁等待时间分布
4. THE System SHALL 记录锁持有时间分布
5. THE System SHALL 记录因锁超时而降级的次数

### Requirement 10: 热点数据识别

**User Story:** 作为系统管理员，我希望系统能够识别热点数据，以便只对热点数据使用分布式锁。

#### Acceptance Criteria

1. THE System SHALL 支持基于访问频率识别热点数据
2. THE System SHALL 支持手动标记热点数据
3. WHEN 数据被识别为热点 THEN THE System SHALL 在缓存查询时使用分布式锁
4. WHEN 数据不是热点 THEN THE System SHALL 使用普通缓存查询以提升性能
5. THE System SHALL 定期更新热点数据列表

### Requirement 11: 锁的公平性

**User Story:** 作为开发人员，我希望在高并发场景下锁的获取是公平的，以避免请求饥饿。

#### Acceptance Criteria

1. THE System SHALL 支持配置公平锁模式
2. WHEN 启用公平锁 THEN THE System SHALL 按请求顺序分配锁
3. WHEN 禁用公平锁 THEN THE System SHALL 使用非公平锁以提升性能
4. THE System SHALL 为不同场景提供公平锁和非公平锁选项
5. THE System SHALL 记录锁的等待队列长度

### Requirement 12: 批量查询优化

**User Story:** 作为开发人员，我希望批量查询场景能够优化锁的使用，以提升性能。

#### Acceptance Criteria

1. WHEN 批量查询多个实体 THEN THE System SHALL 只对缓存未命中的实体使用锁
2. WHEN 批量查询中部分实体是热点数据 THEN THE System SHALL 只对热点数据使用分布式锁
3. THE System SHALL 避免在批量查询中持有多个锁导致死锁
4. THE System SHALL 支持批量查询的并行加载
5. WHEN 批量查询超时 THEN THE System SHALL 返回已加载的数据并记录未完成的查询

### Requirement 13: 测试和验证

**User Story:** 作为测试人员，我希望能够验证分布式锁的正确性，以保证功能质量。

#### Acceptance Criteria

1. THE System SHALL 提供单元测试验证锁的基本功能
2. THE System SHALL 提供集成测试验证多实例场景
3. THE System SHALL 提供压力测试验证高并发场景
4. THE System SHALL 提供混沌测试验证异常场景
5. THE System SHALL 提供性能测试对比加锁前后的性能差异

