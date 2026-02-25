# Design Document

## Overview

本设计文档描述了博客文章服务（ZhiCore-post）混合存储架构的详细设计方案。通过引入 MongoDB 作为内容存储层，配合现有的 PostgreSQL 元数据存储，实现以下核心目标：

1. **性能提升**：将大文本内容从 PostgreSQL 迁移到 MongoDB，提升查询性能
2. **功能扩展**：支持版本历史、草稿自动保存、富文本内容等高级功能
3. **存储优化**：通过冷热分离降低 PostgreSQL 存储压力
4. **架构灵活性**：为未来的功能扩展提供更灵活的数据模型

## Architecture

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        ZhiCore Post Service                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐                                           │
│  │  API Layer       │                                           │
│  │  - REST APIs     │                                           │
│  │  - Validation    │                                           │
│  └────────┬─────────┘                                           │
│           │                                                      │
│  ┌────────▼─────────┐                                           │
│  │  Service Layer   │                                           │
│  │  - Business Logic│                                           │
│  │  - Dual Storage  │                                           │
│  └────────┬─────────┘                                           │
│           │                                                      │
│     ┌─────┴─────┐                                               │
│     │           │                                               │
│  ┌──▼───────┐ ┌▼──────────┐                                    │
│  │PostgreSQL│ │  MongoDB  │                                    │
│  │Repository│ │Repository │                                    │
│  └──┬───────┘ └┬──────────┘                                    │
│     │          │                                                │
└─────┼──────────┼────────────────────────────────────────────────┘
      │          │
┌─────▼──────┐ ┌▼────────────┐
│PostgreSQL  │ │  MongoDB    │
│            │ │             │
│- Metadata  │ │- Content    │
│- Relations │ │- Versions   │
│- Stats     │ │- Drafts     │
└────────────┘ └─────────────┘
```

### 数据分层策略

**PostgreSQL 层（元数据层）**：
- 文章元数据（ID、标题、作者、状态、时间戳）
- 关系数据（分类、标签、点赞、收藏）
- 统计数据（浏览量、点赞数、评论数）
- 索引和查询优化

**MongoDB 层（内容层）**：
- 文章内容（Markdown、HTML、纯文本）
- 版本历史（每次编辑的完整快照）
- 草稿数据（用户维度的单份草稿）
- 富文本内容（内容块、媒体资源）
- 归档数据（冷数据的完整快照）

**Redis 层（缓存层）**：
- 热点文章内容缓存
- 文章详情缓存
- 用户草稿缓存

## Components and Interfaces

### 1. Dual Storage Manager

负责协调 PostgreSQL 和 MongoDB 的数据操作，确保数据一致性。

```java
public interface DualStorageManager {
    /**
     * 创建文章（三阶段提交）
     * 1. PG Insert (Status=PUBLISHING)
     * 2. Mongo Insert
     * 3. PG Update (Status=PUBLISHED)
     */
    String createPost(PostMetadata metadata, PostContent content);
    
    /**
     * 获取文章完整内容（供搜索服务或编辑使用）
     */
    PostDetail getPostFullDetail(String postId);
    
    /**
     * 仅获取内容（供前端延迟加载）
     */
    PostContent getPostContent(String postId);
}
```

### 2. Post Metadata Repository (PostgreSQL)

管理文章元数据的 CRUD 操作。

```java
public interface PostMetadataRepository extends BaseMapper<PostMetadata> {
    /**
     * 根据ID查询元数据
     */
    PostMetadata selectById(String id);
    
    /**
     * 分页查询文章列表
     */
    Page<PostMetadata> selectPage(Page<PostMetadata> page, QueryWrapper<PostMetadata> wrapper);
    
    /**
     * 更新文章状态
     */
    int updateStatus(String id, PostStatus status);
    
    /**
     * 批量查询元数据
     */
    List<PostMetadata> selectBatchIds(Collection<String> ids);
}
```

### 3. Post Content Repository (MongoDB)

管理文章内容的存储和查询。

```java
public interface PostContentRepository extends MongoRepository<PostContent, String> {
    /**
     * 根据文章ID查询内容
     */
    Optional<PostContent> findByPostId(String postId);
    
    /**
     * 保存或更新内容
     */
    PostContent save(PostContent content);
    
    /**
     * 删除内容
     */
    void deleteByPostId(String postId);
    
    /**
     * 批量查询内容
     */
    List<PostContent> findByPostIdIn(List<String> postIds);
}
```

### 4. Version Manager

管理文章的版本历史。

```java
public interface VersionManager {
    /**
     * 创建新版本
     * @param postId 文章ID
     * @param content 内容快照
     * @param editedBy 编辑者
     * @param changeNote 变更说明
     * @return 版本号
     */
    Integer createVersion(String postId, String content, String editedBy, String changeNote);
    
    /**
     * 获取版本列表
     * @param postId 文章ID
     * @param page 页码
     * @param size 每页大小
     * @return 版本列表
     */
    Page<PostVersion> getVersions(String postId, int page, int size);
    
    /**
     * 获取特定版本
     * @param postId 文章ID
     * @param version 版本号
     * @return 版本详情
     */
    Optional<PostVersion> getVersion(String postId, Integer version);
    
    /**
     * 恢复到指定版本
     * @param postId 文章ID
     * @param version 版本号
     * @return 新的版本号
     */
    Integer restoreVersion(String postId, Integer version);
    
    /**
     * 清理旧版本
     * @param postId 文章ID
     * @param keepCount 保留的版本数
     */
    void cleanOldVersions(String postId, int keepCount);
}
```

### 5. Draft Manager

管理文章草稿的自动保存和恢复。

```java
public interface DraftManager {
    /**
     * 自动保存草稿
     * @param postId 文章ID
     * @param userId 用户ID
     * @param content 草稿内容
     * @param isAutoSave 是否自动保存
     */
    void saveDraft(String postId, String userId, String content, boolean isAutoSave);
    
    /**
     * 获取最新草稿
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿内容
     */
    Optional<PostDraft> getLatestDraft(String postId, String userId);
    
    /**
     * 获取用户所有草稿
     * @param userId 用户ID
     * @return 草稿列表
     */
    List<PostDraft> getUserDrafts(String userId);
    
    /**
     * 删除草稿
     * @param postId 文章ID
     * @param userId 用户ID
     */
    void deleteDraft(String postId, String userId);
    
    /**
     * 清理过期草稿
     * @param expireDays 过期天数
     */
    void cleanExpiredDrafts(int expireDays);
}
```

### 6. Archive Manager

管理内容归档和冷热分离。

```java
public interface ArchiveManager {
    /**
     * 归档文章内容
     * @param postId 文章ID
     * @param reason 归档原因
     */
    void archivePost(String postId, String reason);
    
    /**
     * 恢复归档内容
     * @param postId 文章ID
     */
    void restorePost(String postId);
    
    /**
     * 查询归档内容
     * @param postId 文章ID
     * @return 归档内容
     */
    Optional<PostArchive> getArchivedContent(String postId);
    
    /**
     * 批量归档
     * @param threshold 归档阈值（天数）
     * @return 归档数量
     */
    int batchArchive(int threshold);
    
    /**
     * 检查是否已归档
     * @param postId 文章ID
     * @return 是否已归档
     */
    boolean isArchived(String postId);
}
```

### 7. Consistency Checker

检查和修复数据一致性。

```java
public interface ConsistencyChecker {
    /**
     * 检查单篇文章一致性
     * @param postId 文章ID
     * @return 检查结果
     */
    ConsistencyCheckResult checkPost(String postId);
    
    /**
     * 批量检查一致性
     * @param postIds 文章ID列表
     * @return 检查报告
     */
    ConsistencyReport batchCheck(List<String> postIds);
    
    /**
     * 修复不一致数据
     * @param postId 文章ID
     * @param strategy 修复策略（以PG为准/以Mongo为准）
     */
    void repair(String postId, RepairStrategy strategy);
    
    /**
     * 定时一致性检查
     */
    void scheduledCheck();
}
```

## Data Models

### PostgreSQL 数据模型

```sql
-- 文章元数据表
CREATE TABLE posts (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    excerpt VARCHAR(500),
    status VARCHAR(20) NOT NULL,  -- DRAFT/PUBLISHED/ARCHIVED
    topic_id VARCHAR(64),
    published_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_archived BOOLEAN DEFAULT FALSE,  -- 是否已归档
    
    -- 统计字段
    view_count INTEGER DEFAULT 0,
    like_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    
    -- 移除 raw 和 html 字段，由 MongoDB 接管
    
    -- 索引
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status),
    INDEX idx_published_at (published_at),
    INDEX idx_is_archived (is_archived)
);

-- 文章分类关联表
CREATE TABLE post_categories (
    post_id VARCHAR(64) NOT NULL,
    category_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (post_id, category_id)
);

-- 文章标签关联表
CREATE TABLE post_tags (
    post_id VARCHAR(64) NOT NULL,
    tag_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (post_id, tag_id)
);
```

### MongoDB 数据模型

```javascript
// 文章内容集合
{
  _id: ObjectId,
  postId: String,           // 文章ID（与PostgreSQL关联）
  contentType: String,      // markdown/html/rich
  raw: String,              // Markdown原文
  html: String,             // HTML渲染结果
  text: String,             // 纯文本（用于搜索）
  wordCount: Number,        // 字数统计
  readingTime: Number,      // 预计阅读时间（分钟）
  
  // 富文本内容块（可选）
  blocks: [
    {
      type: String,         // text/image/video/code/chart
      content: String,
      props: Object,
      order: Number
    }
  ],
  
  // 媒体资源（可选）
  media: [
    {
      type: String,         // image/video/audio
      url: String,
      thumbnail: String,
      size: Number,
      metadata: Object
    }
  ],
  
  createdAt: Date,
  updatedAt: Date
}

// 版本历史集合
{
  _id: ObjectId,
  postId: String,
  version: Number,          // 版本号（递增）
  content: String,          // 内容快照
  contentType: String,
  editedBy: String,         // 编辑者ID
  editedAt: Date,
  changeNote: String,       // 变更说明
  metadata: Object          // 其他元数据
}

// 草稿集合 (Upsert Mode)
{
  _id: ObjectId,
  postId: String,
  userId: String,           // 联合唯一索引 (postId, userId)
  content: String,
  contentType: String,
  savedAt: Date,
  deviceId: String,
  isAutoSave: Boolean,
  wordCount: Number
}

// 归档集合
{
  _id: ObjectId,
  postId: String,
  content: String,
  contentType: String,
  archivedAt: Date,
  archiveReason: String,    // time/inactive/manual
  snapshot: Object          // 完整快照（包含元数据）
}
```

### MongoDB 索引设计

```javascript
// post_contents 集合索引
db.post_contents.createIndex({ postId: 1 }, { unique: true });
db.post_contents.createIndex({ updatedAt: -1 });

// post_versions 集合索引
db.post_versions.createIndex({ postId: 1, version: -1 });
db.post_versions.createIndex({ postId: 1, editedAt: -1 });

// post_drafts 集合索引
db.post_drafts.createIndex({ postId: 1, userId: 1 }, { unique: true }); // 确保每个用户每篇文章只有一个草稿
db.post_drafts.createIndex({ userId: 1, savedAt: -1 });
db.post_drafts.createIndex({ savedAt: 1 }, { expireAfterSeconds: 2592000 }); // 30天自动过期

// post_archives 集合索引
db.post_archives.createIndex({ postId: 1 }, { unique: true });
db.post_archives.createIndex({ archivedAt: -1 });
```

## Error Handling

### 错误分类

1. **数据一致性错误**
   - PostgreSQL 写入成功但 MongoDB 失败
   - MongoDB 写入成功但 PostgreSQL 失败
   - 数据不一致检测

2. **连接错误**
   - MongoDB 连接失败
   - PostgreSQL 连接失败
   - 网络超时

3. **业务逻辑错误**
   - 文章不存在
   - 版本不存在
   - 权限不足

4. **资源限制错误**
   - 存储空间不足
   - 版本数量超限
   - 并发冲突

### 错误处理策略

#### 三阶段提交回滚机制

三阶段提交通过 `@Transactional` 注解和补偿删除机制确保数据一致性。以下是详细的回滚场景分析：

**场景1: PostgreSQL 写入失败（阶段1失败）**

```
时间线：
T1: 尝试写入 PostgreSQL → 失败 ❌
T2: 不执行后续步骤
T3: 抛出异常

结果：
- PostgreSQL: 无数据 ✅
- MongoDB: 无数据 ✅
- 用户体验: 收到上传失败提示
- 数据一致性: 完全一致，无任何数据残留
```

**场景2: MongoDB 写入失败（阶段2失败）**

```
时间线：
T1: 写入 PostgreSQL (Status=PUBLISHING) → 成功 ✅
T2: 尝试写入 MongoDB → 失败 ❌
T3: 触发异常处理
T4: PostgreSQL 事务自动回滚
T5: 尝试补偿删除 MongoDB（实际无数据）

结果：
- PostgreSQL: 事务回滚，PUBLISHING 记录被删除 ✅
- MongoDB: 无数据 ✅
- 用户体验: 收到上传失败提示
- 数据一致性: 完全一致，无任何数据残留
```

**场景3: 状态更新失败（阶段3失败）**

```
时间线：
T1: 写入 PostgreSQL (Status=PUBLISHING) → 成功 ✅
T2: 写入 MongoDB → 成功 ✅
T3: 尝试更新 PostgreSQL 状态为 PUBLISHED → 失败 ❌
T4: 触发异常处理
T5: PostgreSQL 事务自动回滚
T6: 尝试补偿删除 MongoDB

结果（正常情况）：
- PostgreSQL: 事务回滚，PUBLISHING 记录被删除 ✅
- MongoDB: 补偿删除成功，数据被清理 ✅
- 用户体验: 收到上传失败提示
- 数据一致性: 完全一致，无任何数据残留

结果（异常情况 - 补偿删除失败）：
- PostgreSQL: 事务回滚，PUBLISHING 记录被删除 ✅
- MongoDB: 残留孤儿数据 ⚠️
- 用户体验: 收到上传失败提示
- 数据一致性: 存在孤儿数据，需要 ConsistencyChecker 清理
```

#### 实现代码

```java
public class DualStorageManagerImpl implements DualStorageManager {
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPost(PostMetadata metadata, PostContent content) {
        // 阶段1: 写入 PostgreSQL (Status = PUBLISHING)
        metadata.setStatus(PostStatus.PUBLISHING);
        String postId = postMetadataRepository.insert(metadata);
        
        try {
            // 阶段2: 写入 MongoDB
            content.setPostId(postId);
            postContentRepository.save(content);
            
            // 阶段3: 更新 PostgreSQL 状态 (Status = PUBLISHED)
            postMetadataRepository.updateStatus(postId, PostStatus.PUBLISHED);
            
            return postId;
            
        } catch (Exception e) {
            // 回滚处理：
            // 1. PostgreSQL 会自动回滚（@Transactional）
            // 2. 尝试补偿删除 MongoDB 数据（Best Effort）
            try {
                postContentRepository.deleteByPostId(postId);
                log.info("Successfully compensated MongoDB deletion for postId: {}", postId);
            } catch (Exception deleteException) {
                // 补偿删除失败，记录日志，依赖 ConsistencyChecker 清理
                log.error("Failed to compensate MongoDB deletion for postId: {}, will be cleaned by ConsistencyChecker", 
                    postId, deleteException);
            }
            
            throw new DualStorageException("Failed to create post", e);
        }
    }
}
```

#### 孤儿数据清理

当补偿删除失败时，MongoDB 中可能残留孤儿数据。系统通过 `ConsistencyChecker` 定期清理：

```java
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
public void cleanOrphanData() {
    // 1. 查询 MongoDB 中所有 postId
    List<String> mongoPostIds = postContentRepository.findAllPostIds();
    
    // 2. 批量查询 PostgreSQL 中是否存在对应记录
    List<String> pgPostIds = postMetadataRepository.selectBatchIds(mongoPostIds)
        .stream()
        .map(Post::getId)
        .collect(Collectors.toList());
    
    // 3. 找出孤儿数据（在 MongoDB 中存在但在 PostgreSQL 中不存在）
    Set<String> orphanIds = new HashSet<>(mongoPostIds);
    orphanIds.removeAll(pgPostIds);
    
    // 4. 删除孤儿数据
    if (!orphanIds.isEmpty()) {
        log.warn("Found {} orphan records in MongoDB, cleaning up: {}", 
            orphanIds.size(), orphanIds);
        orphanIds.forEach(postContentRepository::deleteByPostId);
    }
}
```

### 降级策略

```java
public class PostServiceImpl implements PostService {
    
    @Override
    public PostDetail getPostDetail(String postId) {
        try {
            // 正常流程：从双存储获取
            return dualStorageManager.getPostDetail(postId);
            
        } catch (MongoException e) {
            log.warn("MongoDB unavailable, fallback to PostgreSQL only", e);
            
            // 降级：仅从 PostgreSQL 获取元数据
            PostMetadata metadata = postMetadataRepository.selectById(postId);
            if (metadata == null) {
                throw new PostNotFoundException(postId);
            }
            
            // 返回不包含内容的详情
            return PostDetail.builder()
                .metadata(metadata)
                .content(null)
                .contentUnavailable(true)
                .build();
        }
    }
}
```

## Testing Strategy

### 测试层次

1. **单元测试**
   - Repository 层测试
   - Service 层业务逻辑测试
   - 工具类测试

2. **集成测试**
   - PostgreSQL 集成测试
   - MongoDB 集成测试
   - 双写一致性测试

3. **端到端测试**
   - API 接口测试
   - 完整业务流程测试

4. **性能测试**
   - 查询性能测试
   - 并发写入测试
   - 大数据量测试

### 测试工具和框架

- **单元测试**: JUnit 5 + Mockito
- **集成测试**: Spring Boot Test + Testcontainers
- **API 测试**: RestAssured
- **性能测试**: JMeter
- **数据库测试**: H2 (PostgreSQL mode) + Embedded MongoDB

### 测试数据准备

```java
@TestConfiguration
public class TestDataConfig {
    
    @Bean
    public TestDataInitializer testDataInitializer() {
        return new TestDataInitializer() {
            @Override
            public void initialize() {
                // 创建测试用户
                createTestUsers();
                
                // 创建测试文章
                createTestPosts();
                
                // 创建测试版本
                createTestVersions();
                
                // 创建测试草稿
                createTestDrafts();
            }
        };
    }
}
```

现在让我使用 prework 工具来分析验收标准的可测试性：



## Correctness Properties

*属性（Property）是关于系统行为的形式化陈述，应该在所有有效执行中保持为真。属性是人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: 双写原子性

*对于任意*文章创建或更新操作，PostgreSQL 和 MongoDB 的写入要么全部成功，要么全部失败，不存在部分成功的情况。

**Validates: Requirements 2.1, 2.3, 7.1**

### Property 2: 数据一致性

*对于任意*文章 ID，如果在 PostgreSQL 中存在元数据，则在 MongoDB 中必须存在对应的内容；反之亦然。

**Validates: Requirements 2.2, 7.5**

### Property 3: 删除同步性

*对于任意*被删除的文章，在 PostgreSQL 和 MongoDB 中都不应该存在该文章的任何数据。

**Validates: Requirements 2.4**

### Property 4: 回滚完整性

*对于任意*写入失败的操作，如果 PostgreSQL 写入成功但 MongoDB 失败，则 PostgreSQL 的数据应该被回滚；如果 MongoDB 写入成功但 PostgreSQL 失败，则 MongoDB 的数据应该被删除。

**Validates: Requirements 2.5, 7.2, 7.3**

### Property 5: 列表查询优化

*对于任意*文章列表查询，系统应该只访问 PostgreSQL 而不访问 MongoDB，且返回的数据不包含文章内容字段。

**Validates: Requirements 2.6**

### Property 6: 版本创建单调性

*对于任意*文章的版本历史，版本号应该是单调递增的，且每次内容更新都应该创建一个新版本。

**Validates: Requirements 3.1, 3.6**

### Property 7: 版本排序正确性

*对于任意*文章的版本列表查询，返回的版本应该按编辑时间倒序排列，即最新的版本在最前面。

**Validates: Requirements 3.2**

### Property 8: 版本内容完整性

*对于任意*有效的版本号，查询该版本应该返回完整的内容快照，包括内容本身和所有元数据（版本号、编辑者、编辑时间、变更说明）。

**Validates: Requirements 3.3, 3.6**

### Property 9: 版本恢复幂等性

*对于任意*文章和版本号，恢复到该版本后，当前内容应该与该版本的内容一致，且应该创建一个新的版本记录。

**Validates: Requirements 3.4**

### Property 10: 版本清理策略

*对于任意*文章，当版本数量超过配置的最大值时，系统应该自动删除最旧的版本，保留最新的 N 个版本。

**Validates: Requirements 3.5**

### Property 11: 草稿自动保存周期性

*对于任意*正在编辑的文章，系统应该每 30 秒（或配置的间隔）自动保存一次草稿到 MongoDB。

**Validates: Requirements 4.1**

### Property 12: 草稿恢复提示

*对于任意*文章，如果存在草稿且草稿的保存时间晚于文章的更新时间，打开编辑器时应该提示用户恢复草稿。

**Validates: Requirements 4.2, 4.3**

### Property 13: 草稿发布清理

*对于任意*文章，当用户发布文章后，对应的草稿记录应该被自动删除。

**Validates: Requirements 4.4**

### Property 14: 草稿保存容错性

*对于任意*草稿保存操作，即使保存失败，也不应该中断用户的编辑流程，只需要在前端显示警告。

**Validates: Requirements 4.5**

### Property 15: 内容格式支持

*对于任意*内容格式（Markdown、HTML、富文本），系统都应该能正确存储和检索，且检索后的内容与存储前的内容一致。

**Validates: Requirements 5.1, 5.3, 5.4**

### Property 16: 富文本块类型支持

*对于任意*支持的内容块类型（文本、图片、视频、代码、图表），系统都应该能正确存储其结构和属性。

**Validates: Requirements 5.2**

### Property 17: 媒体资源元数据完整性

*对于任意*包含媒体资源的文章，系统应该存储媒体的 URL、缩略图、大小等完整元数据。

**Validates: Requirements 5.5**

### Property 18: 冷数据识别准确性

*对于任意*文章，如果其更新时间距离当前时间超过配置的阈值（如 6 个月），则应该被标记为冷数据候选。

**Validates: Requirements 6.1**

### Property 19: 归档数据完整性

*对于任意*被归档的文章，MongoDB 中应该存在其完整快照，且 PostgreSQL 中应该只保留元数据而不包含内容字段。

**Validates: Requirements 6.2, 6.3**

### Property 20: 归档内容可访问性

*对于任意*已归档的文章，查询时应该能从 MongoDB 加载内容并返回完整数据，用户体验与查询未归档文章一致。

**Validates: Requirements 6.4**

### Property 21: 归档自动恢复

*对于任意*已归档的文章，当用户更新其内容时，系统应该自动将其恢复为热数据并更新内容。

**Validates: Requirements 6.5**

### Property 22: 归档错误隔离

*对于任意*归档操作，如果归档失败，原数据应该保持不变，不应该出现数据丢失或损坏。

**Validates: Requirements 6.6**

### Property 23: 一致性检查准确性

*对于任意*文章，一致性检查应该能准确识别 PostgreSQL 和 MongoDB 之间的数据差异，并生成详细的检查报告。

**Validates: Requirements 7.5**

### Property 24: 数据修复正确性

*对于任意*检测到不一致的文章，修复操作应该以 PostgreSQL 为准，将 MongoDB 的数据同步为与 PostgreSQL 一致。

**Validates: Requirements 7.6**

### Property 25: 缓存一致性

*对于任意*频繁访问的文章，如果内容在 Redis 缓存中存在，则缓存的内容应该与数据库中的内容一致。

**Validates: Requirements 8.3**

### Property 26: 索引使用验证

*对于任意*MongoDB 查询，如果存在适用的索引，查询执行计划应该显示使用了索引而不是全表扫描。

**Validates: Requirements 8.4**

### Property 27: 批量操作原子性

*对于任意*批量写入操作，要么所有记录都成功写入，要么全部失败，不存在部分成功的情况。

**Validates: Requirements 8.5**

### Property 28: 降级功能可用性

*对于任意*系统负载高或 MongoDB 不可用的情况，系统应该能降级为仅使用 PostgreSQL，并保持基本功能可用。

**Validates: Requirements 8.6, 10.4**

### Property 29: 迁移数据完整性（Round-trip Property）

*对于任意*文章，迁移到 MongoDB 后再读取，其内容应该与迁移前完全一致，包括所有字段和格式。

**Validates: Requirements 9.1, 9.2, 9.5**

### Property 30: 迁移错误隔离

*对于任意*迁移批次，单个文章的迁移失败不应该影响其他文章的迁移，失败的文章 ID 应该被记录。

**Validates: Requirements 9.3**

### Property 31: 迁移报告完整性

*对于任意*迁移任务，完成后应该生成包含成功数、失败数、耗时等信息的完整报告。

**Validates: Requirements 9.4**

### Property 32: 监控指标记录

*对于任意*数据库操作，系统应该记录其响应时间、成功率等监控指标。

**Validates: Requirements 10.1, 10.2**

### Property 33: 告警触发及时性

*对于任意*异常情况（数据不一致、连接失败、性能下降、存储不足），系统应该及时触发告警通知管理员。

**Validates: Requirements 10.3, 10.4, 10.5, 10.6**

### Property 34: API 响应结构兼容性

*对于任意*现有 API 端点，升级到混合架构后，其响应的数据结构应该与升级前保持一致。

**Validates: Requirements 11.1, 11.2**

### Property 35: API 请求参数兼容性

*对于任意*现有 API 端点，升级到混合架构后，其接受的请求参数格式应该与升级前保持一致。

**Validates: Requirements 11.3**

### Property 36: API 错误响应兼容性

*对于任意*错误场景，升级到混合架构后，API 返回的错误码和错误信息应该与升级前保持一致。

**Validates: Requirements 11.6**

### Property 37: 新旧功能隔离性

*对于任意*新增功能，其 API 端点和实现不应该影响现有功能的正常运行。

**Validates: Requirements 11.4**

## Testing Strategy

### 测试方法论

本项目采用**双重测试策略**：

1. **单元测试（Unit Tests）**：验证具体示例、边界条件和错误场景
2. **属性测试（Property-Based Tests）**：验证通用属性在所有输入下都成立

两种测试方法是互补的，共同确保系统的正确性和健壮性。

### 单元测试策略

**测试范围**：
- Repository 层的 CRUD 操作
- Service 层的业务逻辑
- 错误处理和边界条件
- 配置加载和验证

**测试工具**：
- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers（PostgreSQL + MongoDB）

**示例测试**：

```java
@SpringBootTest
@Testcontainers
class DualStorageManagerTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0");
    
    @Autowired
    private DualStorageManager dualStorageManager;
    
    @Test
    @DisplayName("创建文章应该同时写入 PostgreSQL 和 MongoDB")
    void testCreatePost() {
        // Given
        PostMetadata metadata = createTestMetadata();
        PostContent content = createTestContent();
        
        // When
        String postId = dualStorageManager.createPost(metadata, content);
        
        // Then
        assertNotNull(postId);
        assertTrue(postgresHasPost(postId));
        assertTrue(mongoHasContent(postId));
    }
    
    @Test
    @DisplayName("MongoDB 写入失败应该回滚 PostgreSQL")
    void testRollbackOnMongoFailure() {
        // Given
        PostMetadata metadata = createTestMetadata();
        PostContent content = createTestContent();
        simulateMongoFailure();
        
        // When & Then
        assertThrows(DualStorageException.class, () -> {
            dualStorageManager.createPost(metadata, content);
        });
        
        // Verify rollback
        assertFalse(postgresHasAnyNewPost());
    }
}
```

### 属性测试策略

**测试框架**：
- **jqwik**（Java 的属性测试库）
- 每个属性测试运行 **100 次迭代**（最小值）
- 使用随机生成器创建测试数据

**属性测试标签格式**：
```java
@Property
@Tag("Feature: post-mongodb-hybrid, Property 1: 双写原子性")
void dualWriteAtomicity(@ForAll("posts") Post post) {
    // 测试实现
}
```

**示例属性测试**：

```java
class DualStoragePropertiesTest {
    
    @Property(tries = 100)
    @Tag("Feature: post-mongodb-hybrid, Property 1: 双写原子性")
    void dualWriteAtomicity(@ForAll("validPosts") Post post) {
        // Given: 任意有效的文章数据
        
        // When: 创建文章
        String postId = dualStorageManager.createPost(
            post.getMetadata(), 
            post.getContent()
        );
        
        // Then: 两个数据库都应该有数据，或者都没有
        boolean inPostgres = postgresRepository.existsById(postId);
        boolean inMongo = mongoRepository.findByPostId(postId).isPresent();
        
        assertEquals(inPostgres, inMongo, 
            "PostgreSQL and MongoDB should be consistent");
    }
    
    @Property(tries = 100)
    @Tag("Feature: post-mongodb-hybrid, Property 29: 迁移数据完整性")
    void migrationRoundTrip(@ForAll("validPosts") Post originalPost) {
        // Given: 任意有效的文章
        String postId = saveToPostgres(originalPost);
        
        // When: 迁移到 MongoDB 并读取
        migrationTool.migrate(postId);
        Post migratedPost = loadFromMongo(postId);
        
        // Then: 内容应该完全一致
        assertEquals(originalPost.getContent(), migratedPost.getContent());
        assertEquals(originalPost.getMetadata(), migratedPost.getMetadata());
    }
    
    @Property(tries = 100)
    @Tag("Feature: post-mongodb-hybrid, Property 6: 版本创建单调性")
    void versionMonotonicity(@ForAll("validPosts") Post post) {
        // Given: 任意文章
        String postId = createPost(post);
        
        // When: 多次更新文章
        List<Integer> versions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            post.setContent("Updated content " + i);
            updatePost(postId, post);
            versions.add(getCurrentVersion(postId));
        }
        
        // Then: 版本号应该单调递增
        for (int i = 1; i < versions.size(); i++) {
            assertTrue(versions.get(i) > versions.get(i-1),
                "Version numbers should be monotonically increasing");
        }
    }
    
    // 数据生成器
    @Provide
    Arbitrary<Post> validPosts() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200),
            Arbitraries.strings().ofMinLength(10).ofMaxLength(10000),
            Arbitraries.of(PostStatus.values())
        ).as((title, content, status) -> 
            Post.builder()
                .title(title)
                .content(content)
                .status(status)
                .build()
        );
    }
}
```

### 集成测试策略

**测试场景**：
- 完整的文章创建、编辑、发布流程
- 版本历史管理流程
- 草稿自动保存和恢复流程
- 内容归档和恢复流程
- 数据迁移流程

**测试环境**：
- 使用 Testcontainers 启动真实的 PostgreSQL 和 MongoDB
- 使用 Embedded Redis 进行缓存测试
- 模拟网络故障和数据库故障

### 性能测试策略

**测试工具**：
- JMeter（API 性能测试）
- JMH（Java 微基准测试）

**测试指标**：
- 文章列表查询：< 100ms（P95）
- 文章详情查询：< 200ms（P95）
- 文章创建：< 300ms（P95）
- 并发写入：> 1000 TPS

**测试场景**：
- 单用户顺序操作
- 多用户并发操作
- 大数据量查询
- 缓存命中率测试

### 测试覆盖率目标

- **代码覆盖率**：> 80%
- **分支覆盖率**：> 75%
- **属性测试覆盖**：所有核心业务逻辑
- **集成测试覆盖**：所有关键业务流程

### 持续集成

**CI 流程**：
1. 代码提交触发 CI
2. 运行单元测试
3. 运行属性测试（100 次迭代）
4. 运行集成测试
5. 生成测试报告和覆盖率报告
6. 测试失败则阻止合并

**测试环境**：
- 使用 GitHub Actions 或 GitLab CI
- 每次 PR 都运行完整测试套件
- 主分支合并前必须通过所有测试
