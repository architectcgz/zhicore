# 实现计划：文章标签（Tag）功能

## 概述

本实现计划基于设计文档，将文章标签功能分解为可执行的编码任务。实现遵循 DDD 原则，从领域层开始，逐步向外扩展到应用层和接口层。

## 任务列表

- [x] 1. 数据库初始化
  - 创建 tags 表、post_tags 关联表、tag_stats 统计表
  - 添加索引和约束
  - _Requirements: 4.1, 5.2_

- [x] 2. 领域层 - Tag 聚合根
  - [x] 2.1 创建 Tag 领域模型
    - 实现 Tag 实体类（id, name, slug, description, createdAt, updatedAt）
    - 实现工厂方法 create() 和 reconstitute()
    - 添加 @JsonCreator 支持 Jackson 序列化
    - _Requirements: 4.1_

  - [x] 2.2 编写 Tag 领域模型单元测试

    - 测试 Tag 创建
    - 测试字段验证
    - _Requirements: 4.1_

- [x] 3. 领域层 - PostTag 关联实体
  - [x] 3.1 创建 PostTag 值对象
    - 实现 PostTag 类（postId, tagId, createdAt）
    - _Requirements: 4.2_

  - [x] 3.2 编写 PostTag 单元测试

    - 测试 PostTag 创建
    - _Requirements: 4.2_


- [x] 4. 领域层 - TagDomainService
  - [x] 4.1 实现 Slug 规范化逻辑
    - 实现 normalizeToSlug() 方法
    - 集成 pinyin4j 库处理中文转拼音
    - 实现 7 步规范化流程
    - _Requirements: 4.1.3, 4.2.2_

  - [x] 4.2 编写 Slug 规范化属性测试

    - **Property 2: Slug 规范化一致性**
    - **Property 3: 大小写和空格不敏感**
    - **Validates: Requirements 4.1.2, 4.1.3, 4.2.2**

  - [x] 4.3 实现 Tag 验证逻辑
    - 实现 validateTagName() 方法
    - 验证名称长度、格式、非法字符
    - _Requirements: 4.1_

  - [x] 4.4 编写 Tag 验证单元测试

    - 测试空名称、超长名称、非法字符
    - _Requirements: 4.1_

  - [x] 4.5 实现 findOrCreate 逻辑
    - 实现 findOrCreate() 方法
    - 实现 findOrCreateBatch() 方法
    - 处理并发冲突（唯一索引冲突）
    - _Requirements: 4.2.3_

  - [x] 4.6 编写 findOrCreate 属性测试

    - **Property 6: Tag 自动创建幂等性**
    - **Validates: Requirements 4.2.3**

- [x] 5. 基础设施层 - TagRepository
  - [x] 5.1 创建 TagRepository 接口
    - 定义 save, findById, findBySlug, findBySlugIn 等方法
    - _Requirements: 4.1_

  - [x] 5.2 实现 TagRepositoryImpl（JPA）
    - 创建 TagEntity JPA 实体
    - 实现 TagRepository 接口
    - 实现领域模型与实体的转换
    - _Requirements: 4.1_

  - [x] 5.3 编写 TagRepository 集成测试

    - 测试保存、查询、批量查询
    - **Property 1: Tag Slug 全局唯一性**
    - **Property 4: Slug 查询精确性**
    - **Validates: Requirements 4.1.1, 4.1.4**


- [x] 6. 基础设施层 - PostTagRepository
  - [x] 6.1 创建 PostTagRepository 接口
    - 定义 attach, attachBatch, detach, detachAllByPostId 等方法
    - 定义查询方法 findTagIdsByPostId, findPostIdsByTagId
    - _Requirements: 4.2_

  - [x] 6.2 实现 PostTagRepositoryImpl（JPA）
    - 创建 PostTagEntity JPA 实体（复合主键）
    - 实现 PostTagRepository 接口
    - 实现批量操作优化
    - _Requirements: 4.2_

  - [x] 6.3 编写 PostTagRepository 集成测试

    - 测试关联创建、删除、查询
    - **Property 7: Post-Tag 关联唯一性**
    - **Validates: Requirements 4.2.4, 5.2.2**

- [x] 7. 应用层 - PostApplicationService 扩展
  - [x] 7.1 扩展 CreatePostCommand 支持标签
    - 添加 tags 字段（List<String>）
    - 添加标签数量验证（≤ 10）
    - _Requirements: 4.2.1, 4.2.5_

  - [x] 7.2 实现创建文章时附加标签
    - 修改 createPost() 方法
    - 调用 TagDomainService.findOrCreateBatch()
    - 调用 PostTagRepository.attachBatch()
    - 确保事务边界正确
    - _Requirements: 4.2.1, 4.2.3_

  - [x] 7.3 编写创建文章附加标签集成测试

    - **Property 5: Post-Tag 关联创建**
    - **Property 8: Tag 数量上限**
    - **Validates: Requirements 4.2.1, 4.2.5**

  - [x] 7.4 实现更新文章时更新标签
    - 修改 updatePost() 方法
    - 支持标签的增删改
    - _Requirements: 4.2.1_

  - [x] 7.5 编写更新文章标签集成测试

    - 测试标签的增删改
    - _Requirements: 4.2.1_

  - [x] 7.6 实现 replacePostTags() 方法
    - 删除旧关联
    - 创建新关联
    - 更新缓存
    - 发布事件
    - _Requirements: 4.2.1_

  - [x] 7.7 编写 replacePostTags 集成测试

    - 测试标签替换
    - _Requirements: 4.2.1_


- [x] 8. 应用层 - TagApplicationService
  - [x] 8.1 创建 TagApplicationService
    - 实现 getTag() 方法
    - 实现 listTags() 方法
    - 实现 searchTags() 方法
    - _Requirements: 4.1.4_

  - [x] 8.2 编写 TagApplicationService 单元测试

    - 测试 getTag, listTags, searchTags
    - _Requirements: 4.1.4_

  - [x] 8.3 实现 getPostsByTag() 方法
    - 查询 Tag
    - 分页查询 Post ID 列表
    - 批量查询 Post 详情（避免 N+1）
    - _Requirements: 4.3.1, 4.3.2_

  - [x] 8.4 编写 getPostsByTag 集成测试

    - **Property 9: 按 Tag 查询 Post 的正确性**
    - **Property 10: 分页查询一致性**
    - **Property 11: 分页查询完整性**
    - **Validates: Requirements 4.3.1, 4.3.2**

  - [x] 8.5 实现 getHotTags() 方法
    - 查询 tag_stats 表
    - 按 post_count 排序
    - 使用 Redis 缓存
    - _Requirements: 4.4_

  - [x] 8.6 编写 getHotTags 单元测试

    - 测试热门标签查询
    - _Requirements: 4.4_

- [x] 9. 接口层 - TagController
  - [x] 9.1 创建 TagController
    - 实现 GET /api/v1/tags/{slug}
    - 实现 GET /api/v1/tags
    - 实现 GET /api/v1/tags/search
    - 实现 GET /api/v1/tags/{slug}/posts
    - 实现 GET /api/v1/tags/hot
    - 添加 Swagger 文档注解
    - _Requirements: 4.1.4, 4.3.1, 4.4_

  - [x] 9.2 编写 TagController API 测试

    - 测试所有 API 端点
    - 测试参数验证
    - 测试错误处理
    - _Requirements: 4.1.4, 4.3.1, 4.4_


- [x] 10. 接口层 - PostController 扩展
  - [x] 10.1 扩展 CreatePostRequest 支持标签
    - 添加 tags 字段
    - 添加验证注解
    - _Requirements: 4.2.1_

  - [x] 10.2 扩展 UpdatePostRequest 支持标签
    - 添加 tags 字段
    - _Requirements: 4.2.1_

  - [x] 10.3 实现 POST /api/v1/posts/{id}/tags
    - 为文章添加标签
    - _Requirements: 4.2.1_

  - [x] 10.4 实现 DELETE /api/v1/posts/{id}/tags/{slug}
    - 移除文章的标签
    - _Requirements: 4.2.1_

  - [x] 10.5 实现 GET /api/v1/posts/{id}/tags
    - 获取文章的标签列表
    - _Requirements: 4.2.1_

  - [x] 10.6 编写 PostController 标签相关 API 测试

    - 测试标签的增删查
    - _Requirements: 4.2.1_

- [x] 11. 缓存层 - Tag 缓存
  - [x] 11.1 实现 Tag 缓存
    - 实现 tag:slug:{slug} 缓存
    - 实现缓存失效策略
    - TTL: 1 hour (CacheConstants.TAG_CACHE_TTL_SECONDS)
    - 实现文件: CachedTagRepository.java, TagRedisKeys.java
    - 包含缓存穿透保护、缓存雪崩防护、优雅降级
    - _Requirements: 5.1_

  - [x] 11.2 实现 Post-Tag 关联缓存
    - 实现 post:tags:{postId} 缓存
    - 实现 tag:posts:{tagId} 缓存
    - TTL: 30 minutes (CacheConstants.POST_TAG_CACHE_TTL_SECONDS)
    - 实现文件: CachedPostTagRepository.java
    - 包含自动缓存失效、优雅降级
    - _Requirements: 5.1_

  - [x] 11.3 实现热门标签缓存
    - 实现 tags:hot 缓存
    - TTL: 1 hour (CacheConstants.HOT_TAGS_CACHE_TTL_SECONDS)
    - 实现文件: TagApplicationService.java (getHotTags方法)
    - 缓存键包含limit参数以支持不同结果集
    - _Requirements: 5.1_

  - [x]* 11.4 编写缓存层单元测试
    - 测试缓存读写
    - 测试缓存失效
    - 测试优雅降级 (Redis不可用时回退到数据库)
    - 测试结果: TagApplicationServiceTest 26个测试全部通过
    - _Requirements: 5.1_


- [x] 12. 事件驱动 - MongoDB 同步
  - [x] 12.1 扩展 PostDocument 支持标签
    - 添加 tags 字段（List<TagInfo>）
    - TagInfo 包含 id, name, slug
    - _Requirements: 5.3_

  - [x] 12.2 实现 PostCreated 事件处理
    - 监听 PostCreated 事件
    - 同步 Tag 信息到 MongoDB
    - _Requirements: 5.3_

  - [x] 12.3 实现 PostTagsUpdated 事件处理
    - 监听 PostTagsUpdated 事件
    - 更新 MongoDB 中的 Tag 信息
    - _Requirements: 5.3_

  - [x]* 12.4 编写 MongoDB 同步集成测试
    - 测试事件驱动同步
    - 所有 7 个测试通过
    - _Requirements: 5.3_

- [x] 13. 事件驱动 - Elasticsearch 同步
  - [x] 13.1 扩展 Elasticsearch 索引支持标签
    - 添加 tags 字段（nested 类型）
    - 更新索引映射
    - _Requirements: 5.1_

  - [x] 13.2 实现 PostCreated 事件处理
    - 监听 PostCreated 事件
    - 同步 Tag 信息到 Elasticsearch
    - _Requirements: 5.1_

  - [x] 13.3 实现 PostTagsUpdated 事件处理
    - 监听 PostTagsUpdated 事件
    - 更新 Elasticsearch 中的 Tag 信息
    - _Requirements: 5.1_

  - [x] 13.4 编写 Elasticsearch 同步集成测试

    - 测试事件驱动同步
    - 所有 8 个测试通过
    - _Requirements: 5.1_

- [x] 14. 统计功能 - Tag 统计
  - [x] 14.1 实现 TagStatsEventHandler
    - 监听 PostCreated, PostDeleted, PostTagsUpdated 事件
    - 更新 tag_stats 表
    - 更新 Redis 缓存
    - _Requirements: 4.4_

  - [x] 14.2 编写 TagStatsEventHandler 单元测试

    - 测试统计更新逻辑
    - _Requirements: 4.4_


- [x] 15. 数据完整性测试
  - [x]* 15.1 编写级联删除属性测试
    - **Property 12: Tag 删除级联**
    - **Property 13: Post 删除级联**
    - **Validates: 数据完整性约束**

  - [x]* 15.2 编写 Slug 规范化防止语义重复测试
    - **Property 14: Slug 规范化防止语义重复**
    - **Validates: Requirements 5.2.3**

- [ ] 16. 检查点 - 核心功能验证
  - 确保所有核心功能测试通过
  - 验证 Tag 创建、查询、关联功能
  - 验证 Post 创建时附加标签功能
  - 验证按 Tag 查询 Post 功能
  - 询问用户是否有问题

- [x] 17. 性能优化
  - [x] 17.1 优化批量查询
    - 实现 PostTagRepository.findTagsByPostIds()
    - 实现 TagRepository.findTagsByPostIds()
    - 避免 N+1 查询
    - 测试文件: PostTagBatchQueryTest.java (6个测试全部通过)
    - _Requirements: 5.1_

  - [ ]* 17.2 编写性能测试
    - 测试批量查询性能
    - 测试分页查询性能
    - _Requirements: 5.1_

  - [x] 17.3 添加数据库查询日志
    - 记录慢查询
    - 分析查询性能
    - 实现文件: SlowQueryInterceptor.java, MyBatisConfig.java
    - 配置: application.yml (performance.slow-query-threshold-ms, performance.slow-query-log-enabled)
    - _Requirements: 5.1_

- [ ] 18. 监控和告警
  - [ ] 18.1 添加业务指标
    - Tag 总数
    - 热门 Tag Top 10
    - 孤儿 Tag 数量
    - 平均每篇文章的 Tag 数量
    - _Requirements: 运维需求_

  - [ ] 18.2 添加性能指标
    - Tag 查询响应时间
    - Post-Tag 关联查询响应时间
    - 按 Tag 查询 Post 响应时间
    - _Requirements: 运维需求_

  - [ ] 18.3 配置告警规则
    - Tag 查询响应时间过慢
    - Tag 缓存未命中率过高
    - 孤儿标签数量过多
    - _Requirements: 运维需求_


- [x] 19. 文档和示例
  - [x] 19.1 更新 API 文档

    - 更新 Swagger/OpenAPI 文档
    - 添加 Tag 相关 API 示例
    - _Requirements: 文档需求_

  - [ ]* 19.2 编写使用指南
    - 如何为文章添加标签
    - 如何按标签查询文章
    - 如何管理标签
    - _Requirements: 文档需求_

- [ ] 20. 最终检查点
  - 确保所有测试通过
  - 验证所有 API 端点正常工作
  - 验证缓存和事件驱动同步正常
  - 验证监控指标正常采集
  - 询问用户是否满意

## 注意事项

1. **测试优先**：每个功能实现后立即编写测试
2. **事务边界**：确保 PostgreSQL 写操作在同一事务中
3. **批量操作**：避免 N+1 查询，使用批量查询
4. **缓存策略**：合理设置 TTL，及时失效缓存
5. **事件驱动**：确保事件在事务成功后发布
6. **性能监控**：关注查询性能，及时优化

## 依赖库

```xml
<!-- 中文转拼音 -->
<dependency>
    <groupId>com.belerweb</groupId>
    <artifactId>pinyin4j</artifactId>
    <version>2.5.1</version>
</dependency>

<!-- 属性测试 -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.7.4</version>
    <scope>test</scope>
</dependency>
```

## 总结

本实现计划共 20 个主要任务，涵盖：
- 数据库初始化
- 领域层实现（Tag、PostTag、TagDomainService）
- 基础设施层实现（Repository）
- 应用层实现（ApplicationService）
- 接口层实现（Controller）
- 缓存层实现
- 事件驱动同步（MongoDB、Elasticsearch）
- 统计功能
- 性能优化
- 监控告警

标记为 `*` 的任务为可选任务（主要是测试和文档），可根据项目进度决定是否实现。

