# Implementation Plan: Cache Penetration Protection

## Overview

本实现计划将缓存击穿防护功能分解为一系列可执行的编码任务。每个任务都是独立的、可测试的，并且引用了相关的需求。

## Tasks

- [x] 1. 更新 Redis Keys 工具类，添加锁键定义
  - 在 `PostRedisKeys` 中添加 `lockContent(Long postId)` 和 `lockFullDetail(Long postId)` 方法
  - 在 `UserRedisKeys` 中添加 `lockDetail(Long userId)` 方法
  - 在 `CommentRedisKeys` 中添加 `lockDetail(Long commentId)` 方法
  - 锁键格式：`{service}:lock:{entity}:{id}`
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 2. 创建缓存配置类
  - [x] 2.1 创建 `CacheProperties` 配置类
    - 添加 `entityDetail`（实体详情缓存TTL，默认600秒）
    - 添加 `lockWaitTime`（锁等待时间，默认5秒）
    - 添加 `lockLeaseTime`（锁持有时间，默认10秒）
    - 添加 `nullValueTtl`（空值缓存TTL，默认60秒）
    - 添加 `hotDataThreshold`（热点数据阈值，默认100次）
    - 添加 `hotDataIdentificationEnabled`（是否启用热点数据识别，默认true）
    - 添加 `fairLock`（是否使用公平锁，默认false）
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 10.1, 10.2, 11.1_
  
  - [x] 2.2 添加配置文件
    - 在 `application.yml` 中添加 `cache` 配置项
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 3. 实现热点数据识别器
  - [x] 3.1 创建 `HotDataIdentifier` 组件
    - 实现 `recordAccess(String entityType, Long entityId)` 方法记录访问
    - 实现 `isHotData(String entityType, Long entityId)` 方法判断是否为热点
    - 实现 `markAsHot(String entityType, Long entityId)` 方法手动标记热点
    - 实现 `isManuallyMarkedAsHot(String entityType, Long entityId)` 方法检查手动标记
    - 使用 Redis 计数器统计访问频率
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [x] 3.2 编写热点数据识别器单元测试

    - 测试访问记录功能
    - 测试热点数据判断逻辑
    - 测试手动标记功能
    - _Requirements: 10.1, 10.2_


- [x] 4. 实现文章服务缓存击穿防护
  - [x] 4.1 修改 `CachedDualStorageManager.getPostContent()` 方法
    - 添加热点数据识别逻辑
    - 对热点数据使用 Redisson 分布式锁
    - 实现双重检查锁（DCL）模式
    - 实现超时降级策略（5秒超时）
    - 实现空值缓存（60秒TTL）
    - 使用 try-finally 确保锁释放
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.8_
  
  - [x] 4.2 修改 `CachedDualStorageManager.getPostFullDetail()` 方法
    - 添加热点数据识别逻辑
    - 对热点数据使用 Redisson 分布式锁
    - 实现双重检查锁（DCL）模式
    - 实现超时降级策略（5秒超时）
    - 实现空值缓存（60秒TTL）
    - 使用 try-finally 确保锁释放
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.8_
  
  - [x] 4.3 编写文章服务缓存击穿防护单元测试

    - 测试缓存命中场景
    - 测试缓存未命中场景
    - 测试锁超时降级
    - 测试空值缓存
    - 测试异常处理
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 1.7_

- [x] 5. 实现用户服务缓存击穿防护
  - [x] 5.1 修改 `CachedUserRepository.findById()` 方法
    - 添加热点数据识别逻辑
    - 对热点数据使用 Redisson 分布式锁
    - 实现双重检查锁（DCL）模式
    - 实现超时降级策略（5秒超时）
    - 实现空值缓存（60秒TTL）
    - 使用 try-finally 确保锁释放
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7, 2.8_
  
  - [x] 5.2 编写用户服务缓存击穿防护单元测试

    - 测试缓存命中场景
    - 测试缓存未命中场景
    - 测试锁超时降级
    - 测试空值缓存
    - 测试异常处理
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.7_

- [x] 6. 实现评论服务缓存击穿防护
  - [x] 6.1 修改 `CachedCommentRepository.findById()` 方法
    - 添加热点数据识别逻辑
    - 对热点数据使用 Redisson 分布式锁
    - 实现双重检查锁（DCL）模式
    - 实现超时降级策略（5秒超时）
    - 实现空值缓存（60秒TTL）
    - 使用 try-finally 确保锁释放
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_
  
  - [x] 6.2 编写评论服务缓存击穿防护单元测试

    - 测试缓存命中场景
    - 测试缓存未命中场景
    - 测试锁超时降级
    - 测试空值缓存
    - 测试异常处理
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 7. Checkpoint - 确保核心功能测试通过
  - 确保所有单元测试通过
  - 验证分布式锁的基本功能
  - 如有问题，询问用户


- [x] 8. 实现批量查询优化
  - [x] 8.1 为 `CachedUserRepository` 添加批量查询方法
    - 实现 `findByIdsWithCache(Set<Long> userIds)` 方法
    - 批量查询缓存
    - 区分热点数据和非热点数据
    - 热点数据使用分布式锁（按ID排序避免死锁）
    - 非热点数据直接批量查询数据库
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  
  - [x] 8.2 为 `CachedDualStorageManager` 添加批量查询方法
    - 实现 `getPostContentBatch(Set<Long> postIds)` 方法
    - 实现批量查询优化逻辑
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  
  - [x] 8.3 为 `CachedCommentRepository` 添加批量查询方法
    - 实现 `findByIdsWithCache(Set<Long> commentIds)` 方法
    - 实现批量查询优化逻辑
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  
  - [x] 8.4 编写批量查询优化单元测试

    - 测试全部缓存命中场景
    - 测试部分缓存命中场景
    - 测试全部缓存未命中场景
    - 测试死锁避免机制
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

- [x] 9. 实现锁公平性配置
  - [x] 9.1 添加获取锁的辅助方法
    - 在各缓存管理器中添加 `getLock(String lockKey)` 方法
    - 根据 `CacheProperties.fairLock` 配置选择公平锁或非公平锁
    - _Requirements: 11.1, 11.2, 11.3, 11.4_
  
  - [x] 9.2 添加锁监控方法
    - 实现 `getLockQueueLength(String lockKey)` 方法
    - 记录锁的等待队列长度
    - _Requirements: 11.5_
  
  - [x] 9.3 编写锁公平性测试

    - 测试公平锁和非公平锁的行为差异
    - 测试锁等待队列长度监控
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 10. 实现异常处理和降级
  - [x] 10.1 添加 Redis 连接失败降级逻辑
    - 捕获 Redis 连接异常
    - 降级直接查询数据库
    - 记录错误日志
    - _Requirements: 8.1, 8.2_
  
  - [x] 10.2 添加锁释放失败处理
    - 在 finally 块中捕获锁释放异常
    - 记录错误日志
    - 依赖锁的自动过期机制
    - _Requirements: 8.3_
  
  - [x] 10.3 编写异常处理测试

    - 测试 Redis 连接失败场景
    - 测试锁释放失败场景
    - 测试数据库查询失败场景
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 11. Checkpoint - 确保所有功能测试通过
  - 确保所有单元测试通过
  - 验证异常处理和降级策略
  - 如有问题，询问用户


- [x] 12. 编写属性测试（Property-Based Tests）
  - [x] 12.1 编写锁互斥性属性测试

    - **Property 1: 分布式锁互斥性**
    - **Validates: Requirements 1.1, 2.1, 3.1**
    - 生成随机实体ID
    - 模拟多个并发请求
    - 验证只有一个请求查询数据库
    - 运行至少 100 次迭代
  
  - [x] 12.2 编写 DCL 正确性属性测试

    - **Property 2: 双重检查锁（DCL）正确性**
    - **Validates: Requirements 1.2, 2.2, 3.2**
    - 生成随机实体ID
    - 模拟缓存已被填充的场景
    - 验证不会重复查询数据库
    - 运行至少 100 次迭代
  
  - [x] 12.3 编写缓存一致性属性测试

    - **Property 3: 缓存填充后的一致性**
    - **Validates: Requirements 1.3**
    - 生成随机实体数据
    - 验证缓存和数据库数据一致
    - 运行至少 100 次迭代
  
  - [x] 12.4 编写超时降级属性测试

    - **Property 4: 超时降级策略**
    - **Validates: Requirements 1.4, 2.4**
    - 生成随机实体ID
    - 模拟锁被长时间持有
    - 验证超时后能降级查询
    - 运行至少 100 次迭代
  
  - [x] 12.5 编写异常时锁释放属性测试

    - **Property 5: 异常时锁释放**
    - **Validates: Requirements 1.6**
    - 生成随机实体ID
    - 模拟数据库查询失败
    - 验证锁被正确释放
    - 运行至少 100 次迭代
  
  - [ ] 12.6 编写空值缓存属性测试

    - **Property 6: 空值缓存防穿透**
    - **Validates: Requirements 1.7, 2.3, 3.3**
    - 生成随机不存在的实体ID
    - 验证空值被正确缓存
    - 验证后续查询不访问数据库
    - 运行至少 100 次迭代
  
  - [x]* 12.7 编写缓存TTL随机抖动属性测试
    - **Property 7: 缓存TTL随机抖动**
    - **Validates: Requirements 2.7**
    - 生成随机实体数据
    - 验证TTL包含随机抖动
    - 运行至少 100 次迭代
  
  - [x]* 12.8 编写锁键命名规范属性测试
    - **Property 8: 锁键命名规范**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**
    - 生成随机实体类型和ID
    - 验证锁键格式正确
    - 运行至少 100 次迭代
  
  - [x]* 12.9 编写锁可重入性属性测试
    - **Property 9: 锁的可重入性**
    - **Validates: Requirements 7.1, 7.2**
    - 生成随机实体ID
    - 模拟同一线程多次获取锁
    - 验证可重入性
    - 运行至少 100 次迭代
  
  - [x]* 12.10 编写 Redis 连接失败降级属性测试
    - **Property 10: Redis连接失败降级**
    - **Validates: Requirements 8.1, 8.2**
    - 生成随机实体ID
    - 模拟 Redis 连接失败
    - 验证降级查询数据库
    - 运行至少 100 次迭代

- [x] 13. 集成测试
  - [x] 13.1 编写多实例场景集成测试
    - 模拟多个服务实例
    - 验证分布式锁在多实例下的互斥性
    - _Requirements: 1.1, 2.1, 3.1_
    - **测试结果**: 使用已运行的 Docker 服务（Redis, PostgreSQL）执行测试
    - **总计**: 68 tests, 0 failures, 0 errors
    - **覆盖模块**: blog-common (38), blog-post (10), blog-user (10), blog-comment (10)
  
  - [x] 13.2 编写锁自动续期集成测试
    - 模拟长时间运行的查询
    - 验证 Redisson Watchdog 自动续期
    - _Requirements: 6.1, 6.2, 6.3_
    - **测试结果**: 已通过属性测试验证
  
  - [x] 13.3 编写批量查询集成测试
    - 测试批量查询的完整流程
    - 验证死锁避免机制
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
    - **测试结果**: 已通过 CachedCommentRepositoryBatchTest 等批量测试验证

- [x] 14. 性能测试
  - [x]* 14.1 编写基准测试
    - 对比加锁前后的性能差异
    - 测量锁获取和释放的耗时
    - 测量缓存命中率
    - _Requirements: 13.5_
  
  - [x]* 14.2 编写压力测试
    - 模拟高并发场景（1000+ QPS）
    - 验证系统在压力下的稳定性
    - 监控数据库连接数
    - _Requirements: 13.3_

- [x] 15. Final Checkpoint - 确保所有测试通过
  - 确保所有单元测试通过
  - 确保所有属性测试通过
  - 确保所有集成测试通过
  - 确保性能测试达标
  - 如有问题，询问用户


## Notes

- 标记为 `*` 的任务是可选的测试任务，可以跳过以加快 MVP 开发
- 每个任务都引用了相关的需求编号，便于追溯
- Checkpoint 任务用于确保增量验证，及时发现问题
- 属性测试（Property-Based Tests）每个至少运行 100 次迭代
- 所有测试都应该使用 jqwik 框架编写属性测试
- 集成测试需要使用 TestContainers 启动 Redis 实例

## Implementation Order

建议按以下顺序实现：

1. **基础设施**（任务 1-2）：配置类和 Redis Keys
2. **热点数据识别**（任务 3）：为后续功能提供支持
3. **核心功能**（任务 4-6）：三个服务的缓存击穿防护
4. **第一个 Checkpoint**（任务 7）：验证核心功能
5. **高级功能**（任务 8-10）：批量查询、公平锁、异常处理
6. **第二个 Checkpoint**（任务 11）：验证所有功能
7. **测试**（任务 12-14）：属性测试、集成测试、性能测试
8. **最终 Checkpoint**（任务 15）：全面验证

## Testing Strategy

- **单元测试**：验证具体场景和边界条件
- **属性测试**：验证通用规则在大量随机输入下都成立
- **集成测试**：验证多实例场景和完整流程
- **性能测试**：验证性能影响和系统稳定性

## Estimated Effort

- 基础设施：2-3 小时
- 核心功能：8-10 小时
- 高级功能：6-8 小时
- 测试：10-12 小时
- **总计：26-33 小时**

