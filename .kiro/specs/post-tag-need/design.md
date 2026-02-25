# 文章标签（Tag）功能设计文档

## 概述

本设计文档基于需求文档，为博客系统引入独立的 Tag 实体模型，建立 Post 与 Tag 的多对多关系。设计遵循 DDD（领域驱动设计）原则，确保 Tag 作为独立聚合根存在，支持高效查询和未来扩展。

### 设计目标

1. **Tag 作为独立实体**：Tag 不是简单的字符串，而是具有唯一标识（slug）的领域对象
2. **多对多关系管理**：通过关联表实现 Post 与 Tag 的灵活关联
3. **查询性能优化**：支持按 Tag 查询 Post，按 Post 查询 Tag，性能可预测
4. **数据一致性保证**：Tag 的唯一性、关联的完整性通过数据库约束保证
5. **可扩展性**：为未来的 Tag 统计、推荐、管理功能预留空间

### 技术栈

- **持久化层**：PostgreSQL（权威数据源）
- **缓存层**：Redis（Tag 热点数据、Post-Tag 关系缓存）
- **搜索层**：Elasticsearch（Tag 作为搜索维度）
- **消息队列**：RocketMQ（Tag 变更事件）

---

## 架构设计

### 领域模型

#### 1. Tag 聚合根

Tag 是独立的聚合根，包含以下核心属性：

```java
public class Tag {
    private final Long id;              // 雪花ID
    private String name;                // 展示名称（如 "PostgreSQL"）
    private final String slug;          // URL友好标识（如 "postgresql"）
    private String description;         // 标签描述（可选）
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
}
```


**设计原则**：
- Tag 的 slug 是全局唯一的，作为自然键使用
- Tag 的 name 可以修改，但 slug 一旦创建不可变
- Tag 创建时自动生成 slug（小写、去空格、URL编码）
- Tag 不包含业务逻辑，主要用于分类和检索

**工厂方法**：
```java
// 创建新标签
public static Tag create(Long id, String name);

// 从持久化恢复
public static Tag reconstitute(Long id, String name, String slug, 
                                String description, LocalDateTime createdAt, 
                                LocalDateTime updatedAt);
```

#### 2. PostTag 关联实体

PostTag 是值对象，表示 Post 与 Tag 的关联关系：

```java
public class PostTag {
    private final Long postId;          // 文章ID
    private final Long tagId;           // 标签ID
    private final LocalDateTime createdAt;  // 关联创建时间
}
```

**设计原则**：
- PostTag 不是聚合根，而是关联关系的表示
- 通过复合主键 (postId, tagId) 保证唯一性
- 创建时间用于追踪关联历史



#### 3. Post 聚合根扩展

Post 聚合根在应用层支持 Tag 关联相关行为：

```java
public class Post {
    // ... 现有字段
    
    // 注意：Post 与 Tag 的多对多关系由 PostTagRepository 统一管理
    // Post 聚合本身不直接持有 Tag 实体，仅在业务操作中使用 Tag ID 作为关联引用
    
    // 领域行为（应用层使用）
    public void attachTags(List<Long> tagIds);
    public void detachTag(Long tagId);
    public void replaceAllTags(List<Long> tagIds);
}
```

**设计说明**：
- Post 聚合在应用层支持 Tag 关联相关行为
- Post 与 Tag 的多对多关系由 PostTagRepository 统一管理
- Post 聚合本身不直接持有 Tag 实体，仅在业务操作中使用 Tag ID 作为关联引用
- Tag 的关联通过 PostTagRepository 管理，不持久化到 posts 表

---

## 组件和接口

### 1. 领域层

#### TagRepository 接口

```java
public interface TagRepository {
    // 保存标签
    Tag save(Tag tag);
    
    // 根据 ID 查询
    Optional<Tag> findById(Long id);
    
    // 根据 slug 查询（唯一）
    Optional<Tag> findBySlug(String slug);
    
    // 批量根据 slug 查询
    List<Tag> findBySlugIn(List<String> slugs);
    
    // 批量根据 ID 查询
    List<Tag> findByIdIn(List<Long> ids);
    
    // 检查 slug 是否存在
    boolean existsBySlug(String slug);
    
    // 分页查询所有标签
    Page<Tag> findAll(Pageable pageable);
    
    // 根据名称模糊搜索
    List<Tag> searchByName(String keyword, int limit);
}
```



#### PostTagRepository 接口

```java
public interface PostTagRepository {
    // 创建关联
    void attach(Long postId, Long tagId);
    
    // 批量创建关联
    void attachBatch(Long postId, List<Long> tagIds);
    
    // 删除关联
    void detach(Long postId, Long tagId);
    
    // 删除文章的所有关联
    void detachAllByPostId(Long postId);
    
    // 查询文章的所有标签ID
    List<Long> findTagIdsByPostId(Long postId);
    
    // 查询标签下的所有文章ID
    List<Long> findPostIdsByTagId(Long tagId);
    
    // 分页查询标签下的文章ID
    Page<Long> findPostIdsByTagId(Long tagId, Pageable pageable);
    
    // 检查关联是否存在
    boolean exists(Long postId, Long tagId);
    
    // 统计标签下的文章数量
    int countPostsByTagId(Long tagId);
    
    // 统计文章的标签数量
    int countTagsByPostId(Long postId);
}
```

#### TagDomainService 领域服务

```java
public interface TagDomainService {
    // 规范化标签名称为 slug
    String normalizeToSlug(String name);
    
    // 查找或创建标签（幂等操作）
    Tag findOrCreate(String name);
    
    // 批量查找或创建标签
    List<Tag> findOrCreateBatch(List<String> names);
    
    // 验证标签名称合法性
    void validateTagName(String name);
}
```

**Slug 规范化规则**：

1. 对原始名称进行 trim
2. 中文转拼音（使用 pinyin4j 库）
3. 转换为小写
4. 将所有空白字符统一替换为 `-`
5. 合并连续的 `-` 为单个 `-`
6. 移除首尾的 `-`
7. 过滤非法字符，仅保留 `[a-z0-9-]`
8. 结果为空则拒绝创建

**中文与非 ASCII 字符策略**：
- 方案：中文转拼音（使用 pinyin4j 库）
- 示例：`数据库` → `shu-ju-ku`（slug）
- 理由：确保 slug 符合 URL 规范，避免编码问题

**示例**：
```java
normalizeToSlug("Spring Boot")  → "spring-boot"
normalizeToSlug("  Java  ")     → "java"
normalizeToSlug("C++")          → "c"
normalizeToSlug("数据库")        → "shu-ju-ku"
normalizeToSlug("PostgreSQL")   → "postgresql"
normalizeToSlug("   ")          → throw ValidationException
```

**依赖库**：
```xml
<dependency>
    <groupId>com.belerweb</groupId>
    <artifactId>pinyin4j</artifactId>
    <version>2.5.1</version>
</dependency>
```



### 2. 应用层

#### PostApplicationService 扩展

```java
public class PostApplicationService {
    // 创建文章时附加标签
    public PostDTO createPost(CreatePostCommand command);
    
    // 更新文章时更新标签
    public PostDTO updatePost(UpdatePostCommand command);
    
    // 为文章添加标签
    public void attachTagsToPost(Long postId, List<String> tagNames);
    
    // 移除文章的标签
    public void detachTagFromPost(Long postId, String tagSlug);
    
    // 替换文章的所有标签
    public void replacePostTags(Long postId, List<String> tagNames);
}
```

**批量查询策略说明**：
- Post 列表查询应批量获取关联 Tag 信息
- 通过 `post_tags` + `tags` 的 IN 查询一次性加载
- 禁止在列表场景中对每个 Post 单独查询 Tag（避免 N+1 问题）

**示例**：
```java
// 错误：N+1 查询
for (Post post : posts) {
    List<Tag> tags = tagRepository.findByPostId(post.getId()); // N 次查询
}

// 正确：批量查询
List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());
Map<Long, List<Tag>> postTagsMap = postTagRepository.findTagsByPostIds(postIds); // 1 次查询
```

#### TagApplicationService 新增

```java
public class TagApplicationService {
    // 获取标签详情
    public TagDTO getTag(String slug);
    
    // 获取标签列表
    public PageResult<TagDTO> listTags(int page, int size);
    
    // 搜索标签
    public List<TagDTO> searchTags(String keyword, int limit);
    
    // 获取标签下的文章列表
    public PageResult<PostDTO> getPostsByTag(String slug, int page, int size);
    
    // 获取热门标签（按文章数量排序）
    public List<TagStatsDTO> getHotTags(int limit);
}
```



### 3. 接口层

#### TagController API 设计

```java
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    
    // 获取标签详情
    @GetMapping("/{slug}")
    public Result<TagVO> getTag(@PathVariable String slug);
    
    // 获取标签列表
    @GetMapping
    public Result<PageResult<TagVO>> listTags(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );
    
    // 搜索标签
    @GetMapping("/search")
    public Result<List<TagVO>> searchTags(
        @RequestParam String keyword,
        @RequestParam(defaultValue = "10") int limit
    );
    
    // 获取标签下的文章列表
    @GetMapping("/{slug}/posts")
    public Result<PageResult<PostVO>> getPostsByTag(
        @PathVariable String slug,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );
    
    // 获取热门标签
    @GetMapping("/hot")
    public Result<List<TagStatsVO>> getHotTags(
        @RequestParam(defaultValue = "10") int limit
    );
}
```

#### PostController API 扩展

```java
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {
    
    // 创建文章（支持标签）
    @PostMapping
    public Result<PostVO> createPost(@RequestBody CreatePostRequest request);
    
    // 更新文章（支持标签）
    @PutMapping("/{id}")
    public Result<PostVO> updatePost(
        @PathVariable Long id, 
        @RequestBody UpdatePostRequest request
    );
    
    // 为文章添加标签
    @PostMapping("/{id}/tags")
    public Result<Void> attachTags(
        @PathVariable Long id,
        @RequestBody AttachTagsRequest request
    );
    
    // 移除文章的标签
    @DeleteMapping("/{id}/tags/{slug}")
    public Result<Void> detachTag(
        @PathVariable Long id,
        @PathVariable String slug
    );
    
    // 获取文章的标签列表
    @GetMapping("/{id}/tags")
    public Result<List<TagVO>> getPostTags(@PathVariable Long id);
}
```



---

## 数据模型

### 1. PostgreSQL 表结构

#### tags 表

```sql
CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY,                      -- 雪花ID
    name VARCHAR(50) NOT NULL,                  -- 展示名称
    slug VARCHAR(50) NOT NULL UNIQUE,           -- URL友好标识（唯一）
    description VARCHAR(200),                   -- 标签描述
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 数据质量约束
    CONSTRAINT ck_tags_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_tags_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

-- 索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_slug ON tags(slug);
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
CREATE INDEX IF NOT EXISTS idx_tags_created_at ON tags(created_at DESC);

-- 注释
COMMENT ON TABLE tags IS '标签表';
COMMENT ON COLUMN tags.id IS '标签ID（雪花算法）';
COMMENT ON COLUMN tags.name IS '标签展示名称';
COMMENT ON COLUMN tags.slug IS 'URL友好标识（唯一）';
COMMENT ON COLUMN tags.description IS '标签描述';
COMMENT ON CONSTRAINT ck_tags_name_not_blank ON tags IS 'name 不允许为空或仅包含空白';
COMMENT ON CONSTRAINT ck_tags_slug_format ON tags IS 'slug 仅允许小写字母、数字和单个连字符';
```

**数据质量约束说明**：
- `ck_tags_name_not_blank`：确保 name 不为空或仅包含空白字符
- `ck_tags_slug_format`：确保 slug 格式符合规范（小写字母、数字、连字符）
- `updated_at` 字段由应用层显式维护（在 Tag 更新时设置）

#### post_tags 关联表

```sql
CREATE TABLE IF NOT EXISTS post_tags (
    post_id BIGINT NOT NULL,                    -- 文章ID
    tag_id BIGINT NOT NULL,                     -- 标签ID
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

-- 核心索引（覆盖按 tag 查 post 的场景）
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_post ON post_tags(tag_id, post_id);

-- 覆盖按 post 查 tag 的场景
CREATE INDEX IF NOT EXISTS idx_post_tags_post_tag ON post_tags(post_id, tag_id);

-- 时间索引（用于统计和分析）
CREATE INDEX IF NOT EXISTS idx_post_tags_created_at ON post_tags(created_at DESC);

-- 外键约束
ALTER TABLE post_tags 
    ADD CONSTRAINT fk_post_tags_post 
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

ALTER TABLE post_tags 
    ADD CONSTRAINT fk_post_tags_tag 
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE;

-- 注释
COMMENT ON TABLE post_tags IS '文章标签关联表';
COMMENT ON COLUMN post_tags.post_id IS '文章ID';
COMMENT ON COLUMN post_tags.tag_id IS '标签ID';
```

**索引设计说明**：
- `idx_post_tags_tag_post`：核心索引，覆盖"按 Tag 查询 Post 列表"场景
- `idx_post_tags_post_tag`：覆盖"按 Post 查询 Tag 列表"场景
- 复合索引与实际 JOIN 路径对齐，降低回表成本



#### tag_stats 统计表（可选，用于性能优化）

```sql
CREATE TABLE IF NOT EXISTS tag_stats (
    tag_id BIGINT PRIMARY KEY,                  -- 标签ID
    post_count INT NOT NULL DEFAULT 0,          -- 文章数量
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_tag_stats_post_count ON tag_stats(post_count DESC);

-- 外键约束
ALTER TABLE tag_stats 
    ADD CONSTRAINT fk_tag_stats_tag 
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE;

-- 注释
COMMENT ON TABLE tag_stats IS '标签统计表（派生数据）';
COMMENT ON COLUMN tag_stats.tag_id IS '标签ID';
COMMENT ON COLUMN tag_stats.post_count IS '文章数量';
```

**设计说明**：
- tag_stats 表为**派生数据（Derived Data）**，不作为业务权威来源
- 所有统计结果理论上均可通过 post_tags 表实时计算得到
- tag_stats 允许短暂不一致，用于性能优化
- 当数据异常或需要重建时，可通过全量扫描 post_tags 表重新计算
- 更新策略：异步更新，通过事件驱动或定时任务维护

### 2. MongoDB 文档结构（读模型）

#### PostDocument 扩展

```json
{
  "_id": "1234567890",
  "title": "文章标题",
  "content": "文章内容",
  "excerpt": "文章摘要",
  "authorId": "123",
  "authorName": "作者名",
  "tags": [
    {
      "id": "1001",
      "name": "PostgreSQL",
      "slug": "postgresql"
    },
    {
      "id": "1002",
      "name": "Spring Boot",
      "slug": "spring-boot"
    }
  ],
  "categoryId": "10",
  "categoryName": "技术",
  "status": "PUBLISHED",
  "viewCount": 100,
  "likeCount": 50,
  "commentCount": 20,
  "publishedAt": "2026-01-29T10:00:00Z",
  "createdAt": "2026-01-28T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```

**设计说明**：
- MongoDB 中冗余 Tag 的完整信息（id, name, slug）
- 便于搜索和展示，无需额外查询
- Tag 变更时通过事件同步更新

**MongoDB Tag 冗余策略说明**：
- `tags.slug` 作为主要过滤与查询字段
- `tags.name` / `id` 用于减少前端展示时的二次查询
- Tag 名称变更策略：
  - 不回刷 MongoDB 文档，读模型允许展示旧 name
  - 理由：Tag 名称变更频率极低，回刷成本高
  - 如需强一致，可通过异步任务批量更新（可选）



### 3. Elasticsearch 索引结构

#### post 索引扩展

```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "title": { 
        "type": "text",
        "analyzer": "ik_max_word",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "content": { 
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "tags": {
        "type": "nested",
        "properties": {
          "id": { "type": "keyword" },
          "name": { 
            "type": "text",
            "analyzer": "ik_max_word",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "slug": { "type": "keyword" }
        }
      },
      "authorId": { "type": "keyword" },
      "status": { "type": "keyword" },
      "publishedAt": { "type": "date" }
    }
  }
}
```

**设计说明**：
- tags 使用 nested 类型，支持精确的标签过滤
- tag.name 支持中文分词搜索
- tag.slug 作为 keyword 类型，支持精确匹配



### 4. Redis 缓存结构

#### Tag 缓存

```
Key: tag:slug:{slug}
Type: String (JSON)
TTL: 1 hour
Value: {
  "id": 1001,
  "name": "PostgreSQL",
  "slug": "postgresql",
  "description": "关系型数据库",
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### Post-Tag 关联缓存

```
Key: post:tags:{postId}
Type: List
TTL: 30 minutes
Value: [1001, 1002, 1003]  // Tag ID 列表
```

#### Tag-Post 关联缓存

```
Key: tag:posts:{tagId}
Type: Sorted Set
Score: publishedAt timestamp
TTL: 30 minutes
Value: postId
```

#### Tag 统计缓存

```
Key: tag:stats:{tagId}
Type: Hash
TTL: 1 hour
Fields:
  - postCount: 100
  - updatedAt: "2026-01-29T10:00:00Z"
```

#### 热门标签缓存

```
Key: tags:hot
Type: Sorted Set
Score: postCount
TTL: 1 hour
Value: tagId
```



---

## 核心流程设计

### 1. 创建文章并附加标签

```
用户请求 -> PostController.createPost()
  |
  v
PostApplicationService.createPost(command)
  |
  +-- 1. 验证用户权限
  |
  +-- 2. 生成文章ID（Leaf）
  |
  +-- 3. 创建 Post 聚合根
  |
  +-- 4. 处理标签
  |     |
  |     +-- TagDomainService.findOrCreateBatch(tagNames)
  |     |     |
  |     |     +-- 规范化标签名称为 slug
  |     |     +-- 批量查询已存在的标签
  |     |     +-- 创建不存在的标签
  |     |     +-- 返回 Tag 列表
  |     |
  |     +-- PostTagRepository.attachBatch(postId, tagIds)
  |
  +-- 5. 开启数据库事务
  |     |
  |     +-- 保存 Post 到 PostgreSQL
  |     +-- 保存 Post-Tag 关联到 post_tags
  |     +-- 提交事务
  |
  +-- 6. 事务成功后发布 PostCreated 事件
  |
  +-- 7. 异步处理（事件驱动）
  |     |
  |     +-- 保存 Post 内容到 MongoDB
  |     +-- 更新 Elasticsearch 索引
  |     +-- 更新 Redis 缓存
  |
  +-- 8. 返回 PostDTO
```

**事务边界说明**：
- PostgreSQL 写模型（posts、tags、post_tags）在同一数据库事务中提交
- 事务成功后再发布领域事件
- MongoDB、Elasticsearch、Redis 的更新通过异步事件驱动完成
- 系统允许短暂的最终一致性

### 2. 按标签查询文章列表

```
用户请求 -> TagController.getPostsByTag(slug, page, size)
  |
  v
TagApplicationService.getPostsByTag(slug, page, size)
  |
  +-- 1. 根据 slug 查询 Tag
  |     |
  |     +-- 先查 Redis: tag:slug:{slug}
  |     +-- 未命中则查 PostgreSQL
  |     +-- 写入 Redis 缓存
  |
  +-- 2. 查询 Tag 下的 Post ID 列表（分页）
  |     |
  |     +-- 先查 Redis: tag:posts:{tagId}
  |     +-- 未命中则查 PostgreSQL: post_tags
  |     +-- 写入 Redis 缓存
  |
  +-- 3. 批量查询 Post 详情
  |     |
  |     +-- 先查 Redis: post:{id}
  |     +-- 未命中则查 PostgreSQL + MongoDB
  |
  +-- 4. 组装 PostDTO 列表
  |
  +-- 5. 返回分页结果
```



### 3. 更新文章标签

```
用户请求 -> PostController.replacePostTags(postId, tagNames)
  |
  v
PostApplicationService.replacePostTags(postId, tagNames)
  |
  +-- 1. 验证用户权限（是否为文章作者）
  |
  +-- 2. 查询文章
  |
  +-- 3. 处理新标签
  |     |
  |     +-- TagDomainService.findOrCreateBatch(tagNames)
  |
  +-- 4. 删除旧关联
  |     |
  |     +-- PostTagRepository.detachAllByPostId(postId)
  |
  +-- 5. 创建新关联
  |     |
  |     +-- PostTagRepository.attachBatch(postId, newTagIds)
  |
  +-- 6. 更新缓存
  |     |
  |     +-- 删除 Redis: post:tags:{postId}
  |     +-- 删除 Redis: tag:posts:{oldTagId}
  |     +-- 删除 Redis: tag:stats:{oldTagId}
  |
  +-- 7. 发布 PostTagsUpdated 事件
  |
  +-- 8. 返回成功
```

### 4. 标签统计更新（异步）

```
PostCreated / PostDeleted / PostTagsUpdated 事件
  |
  v
TagStatsEventHandler
  |
  +-- 1. 提取受影响的 Tag ID 列表
  |
  +-- 2. 更新 tag_stats 表
  |     |
  |     +-- UPDATE tag_stats SET post_count = (
  |           SELECT COUNT(*) FROM post_tags WHERE tag_id = ?
  |         ) WHERE tag_id = ?
  |
  +-- 3. 更新 Redis 缓存
  |     |
  |     +-- 删除 Redis: tag:stats:{tagId}
  |     +-- 删除 Redis: tags:hot
  |
  +-- 4. 更新 Elasticsearch 索引
        |
        +-- 更新相关 Post 文档的 tags 字段
```



---

## 错误处理

### 1. 标签名称验证

**错误场景**：
- 标签名称为空
- 标签名称过长（>50字符）
- 标签名称包含非法字符

**处理策略**：
```java
public void validateTagName(String name) {
    if (!StringUtils.hasText(name)) {
        throw new DomainException("标签名称不能为空");
    }
    if (name.length() > 50) {
        throw new DomainException("标签名称不能超过50字符");
    }
    if (!name.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_]+$")) {
        throw new DomainException("标签名称只能包含中文、英文、数字、空格、连字符和下划线");
    }
}
```

### 2. 标签数量限制

**错误场景**：
- 单篇文章的标签数量超过限制（建议 ≤ 10）

**处理策略**：
```java
public void attachTagsToPost(Long postId, List<String> tagNames) {
    if (tagNames.size() > 10) {
        throw new DomainException("单篇文章最多只能添加10个标签");
    }
    // ...
}
```

### 3. 标签不存在

**错误场景**：
- 查询不存在的标签
- 为文章移除不存在的标签

**处理策略**：
```java
public TagDTO getTag(String slug) {
    Tag tag = tagRepository.findBySlug(slug)
        .orElseThrow(() -> new NotFoundException("标签不存在: " + slug));
    return tagAssembler.toDTO(tag);
}
```

### 4. 重复关联

**错误场景**：
- 为文章重复添加相同的标签

**处理策略**：
- 数据库层面：通过复合主键 (post_id, tag_id) 保证唯一性
- 应用层面：先检查是否已存在，避免重复插入

```java
public void attach(Long postId, Long tagId) {
    if (exists(postId, tagId)) {
        log.warn("Post-Tag relation already exists: postId={}, tagId={}", postId, tagId);
        return; // 幂等操作，直接返回
    }
    // 插入关联
}
```



### 5. 并发冲突

**错误场景**：
- 多个请求同时创建相同 slug 的标签

**处理策略**：
```java
public Tag findOrCreate(String name) {
    String slug = normalizeToSlug(name);
    
    // 先查询
    Optional<Tag> existing = tagRepository.findBySlug(slug);
    if (existing.isPresent()) {
        return existing.get();
    }
    
    // 不存在则创建
    try {
        Long id = idGenerator.nextId();
        Tag tag = Tag.create(id, name);
        return tagRepository.save(tag);
    } catch (DataIntegrityViolationException e) {
        // 唯一索引冲突，重新查询
        return tagRepository.findBySlug(slug)
            .orElseThrow(() -> new DomainException("创建标签失败"));
    }
}
```

---

## 测试策略

### 1. 单元测试

#### Tag 领域模型测试

```java
@Test
void testCreateTag() {
    // Given
    Long id = 1001L;
    String name = "PostgreSQL";
    
    // When
    Tag tag = Tag.create(id, name);
    
    // Then
    assertEquals(id, tag.getId());
    assertEquals(name, tag.getName());
    assertEquals("postgresql", tag.getSlug());
    assertNotNull(tag.getCreatedAt());
}

@Test
void testSlugNormalization() {
    // Given
    String name = "Spring Boot";
    
    // When
    String slug = TagDomainService.normalizeToSlug(name);
    
    // Then
    assertEquals("spring-boot", slug);
}
```

#### PostTag 关联测试

```java
@Test
void testAttachTag() {
    // Given
    Long postId = 1L;
    Long tagId = 1001L;
    
    // When
    postTagRepository.attach(postId, tagId);
    
    // Then
    assertTrue(postTagRepository.exists(postId, tagId));
}

@Test
void testDetachTag() {
    // Given
    Long postId = 1L;
    Long tagId = 1001L;
    postTagRepository.attach(postId, tagId);
    
    // When
    postTagRepository.detach(postId, tagId);
    
    // Then
    assertFalse(postTagRepository.exists(postId, tagId));
}
```



### 2. 集成测试

#### 创建文章并附加标签

```java
@Test
void testCreatePostWithTags() {
    // Given
    CreatePostCommand command = CreatePostCommand.builder()
        .title("测试文章")
        .content("测试内容")
        .tags(Arrays.asList("Java", "Spring Boot", "PostgreSQL"))
        .build();
    
    // When
    PostDTO post = postApplicationService.createPost(command);
    
    // Then
    assertNotNull(post.getId());
    assertEquals(3, post.getTags().size());
    assertTrue(post.getTags().stream()
        .anyMatch(tag -> tag.getSlug().equals("java")));
}
```

#### 按标签查询文章

```java
@Test
void testGetPostsByTag() {
    // Given
    String slug = "java";
    int page = 0;
    int size = 20;
    
    // When
    PageResult<PostDTO> result = tagApplicationService.getPostsByTag(slug, page, size);
    
    // Then
    assertNotNull(result);
    assertTrue(result.getTotal() > 0);
    result.getItems().forEach(post -> {
        assertTrue(post.getTags().stream()
            .anyMatch(tag -> tag.getSlug().equals(slug)));
    });
}
```

### 3. 性能测试

#### 标签查询性能

```java
@Test
void testTagQueryPerformance() {
    // Given: 创建 1000 个标签
    for (int i = 0; i < 1000; i++) {
        Tag tag = Tag.create(idGenerator.nextId(), "Tag" + i);
        tagRepository.save(tag);
    }
    
    // When: 查询标签
    long startTime = System.currentTimeMillis();
    Optional<Tag> tag = tagRepository.findBySlug("tag500");
    long endTime = System.currentTimeMillis();
    
    // Then: 查询时间应小于 10ms
    assertTrue(tag.isPresent());
    assertTrue(endTime - startTime < 10);
}
```

#### Post-Tag 关联查询性能

```java
@Test
void testPostTagQueryPerformance() {
    // Given: 创建 1 个标签，关联 10000 篇文章
    Long tagId = 1001L;
    for (int i = 0; i < 10000; i++) {
        postTagRepository.attach((long) i, tagId);
    }
    
    // When: 分页查询标签下的文章
    long startTime = System.currentTimeMillis();
    Page<Long> postIds = postTagRepository.findPostIdsByTagId(
        tagId, PageRequest.of(0, 20)
    );
    long endTime = System.currentTimeMillis();
    
    // Then: 查询时间应小于 50ms
    assertEquals(20, postIds.getContent().size());
    assertTrue(endTime - startTime < 50);
}
```



---

## 正确性属性（Correctness Properties）

*属性（Property）是系统应该在所有有效执行中保持为真的特征或行为——本质上是关于系统应该做什么的形式化陈述。属性是人类可读规范和机器可验证正确性保证之间的桥梁。*

**说明**：
- Correctness Properties 用于描述系统在理想状态下应满足的行为约束
- 并非所有属性都要求在初期实现中完全自动化验证
- 部分属性作为长期回归测试或重构安全网存在
- 属性测试使用 jqwik 框架（Java 的 Property-Based Testing 库）

### Property 1: Tag Slug 全局唯一性

*对于任意* 两个 Tag，如果它们的 slug 相同，则它们必须是同一个 Tag（即 id 相同）

**验证方式**: Requirements 4.1.1

**测试策略**: 
- 生成随机的 Tag 名称
- 尝试创建具有相同 slug 的 Tag
- 验证系统是否拒绝或返回已存在的 Tag

### Property 2: Slug 规范化一致性

*对于任意* Tag 名称，经过规范化处理后生成的 slug 应该是确定的、可重复的

**验证方式**: Requirements 4.1.2, 4.2.2

**测试策略**:
- 对同一个 Tag 名称多次调用 normalizeToSlug()
- 验证每次返回的 slug 都相同
- 测试大小写、空格、特殊字符的处理

### Property 3: 大小写和空格不敏感

*对于任意* Tag 名称的不同大小写和空格组合（如 "Java", "java", " Java "），它们应该生成相同的 slug

**验证方式**: Requirements 4.1.3

**测试策略**:
- 生成随机 Tag 名称的大小写和空格变体
- 验证所有变体生成相同的 slug
- 验证 findOrCreate 返回同一个 Tag

### Property 4: Slug 查询精确性

*对于任意* 已存在的 Tag，通过其 slug 查询应该返回该 Tag，且只返回该 Tag

**验证方式**: Requirements 4.1.4

**测试策略**:
- 创建随机 Tag
- 通过 slug 查询
- 验证返回的 Tag id 与创建的 Tag id 相同



### Property 5: Post-Tag 关联创建

*对于任意* Post 和 Tag 列表，创建关联后，查询 Post 的 Tag 列表应该包含所有指定的 Tag

**验证方式**: Requirements 4.2.1

**测试策略**:
- 创建随机 Post 和 Tag 列表
- 建立关联
- 查询 Post 的 Tag 列表
- 验证所有 Tag 都存在

### Property 6: Tag 自动创建幂等性

*对于任意* Tag 名称，多次调用 findOrCreate 应该返回同一个 Tag（id 相同）

**验证方式**: Requirements 4.2.3

**测试策略**:
- 对同一个 Tag 名称多次调用 findOrCreate
- 验证返回的 Tag id 都相同
- 验证数据库中只有一条记录

### Property 7: Post-Tag 关联唯一性

*对于任意* Post 和 Tag，多次建立关联应该是幂等的，不会创建重复记录

**验证方式**: Requirements 4.2.4, 5.2.2

**测试策略**:
- 创建随机 Post 和 Tag
- 多次调用 attach(postId, tagId)
- 验证关联表中只有一条记录
- 验证查询结果不重复

### Property 8: Tag 数量上限

*对于任意* Post，尝试添加超过 10 个 Tag 应该被拒绝

**验证方式**: Requirements 4.2.5

**测试策略**:
- 创建随机 Post
- 尝试添加 11 个或更多 Tag
- 验证系统抛出异常或拒绝操作

### Property 9: 按 Tag 查询 Post 的正确性

*对于任意* Tag，查询该 Tag 下的所有 Post，返回的每个 Post 都应该关联了该 Tag

**验证方式**: Requirements 4.3.1

**测试策略**:
- 创建随机 Tag 和多个 Post
- 为部分 Post 关联该 Tag
- 查询 Tag 下的 Post 列表
- 验证返回的所有 Post 都关联了该 Tag
- 验证未关联的 Post 不在结果中



### Property 10: 分页查询一致性

*对于任意* Tag 和分页参数，在数据未发生变更，且分页查询使用稳定排序条件（如 published_at + id 作为联合排序键）的前提下，多次查询同一页应该返回相同的结果

**验证方式**: Requirements 4.3.2

**测试策略**:
- 创建随机 Tag 和多个 Post
- 建立关联
- 使用稳定排序（published_at DESC, id DESC）
- 多次查询同一页
- 验证结果一致

**工程说明**：
- offset 分页在高并发场景下可能存在弱一致性
- 如需强一致分页，可在后期引入 keyset pagination（游标分页）
- 当前设计使用 offset 分页，适合大多数场景

### Property 11: 分页查询完整性

*对于任意* Tag，遍历所有分页结果，应该包含该 Tag 下的所有 Post，且不重复

**验证方式**: Requirements 4.3.2

**测试策略**:
- 创建随机 Tag 和已知数量的 Post
- 建立关联
- 遍历所有分页
- 验证总数正确，无重复，无遗漏

### Property 12: Tag 删除级联

*对于任意* Tag，删除 Tag 后，所有关联的 Post-Tag 关系也应该被删除

**验证方式**: 数据完整性约束（ON DELETE CASCADE）

**测试策略**:
- 创建随机 Tag 和 Post
- 建立关联
- 删除 Tag
- 验证 post_tags 表中不再有该 Tag 的记录

### Property 13: Post 删除级联

*对于任意* Post，删除 Post 后，所有关联的 Post-Tag 关系也应该被删除

**验证方式**: 数据完整性约束（ON DELETE CASCADE）

**测试策略**:
- 创建随机 Post 和 Tag
- 建立关联
- 删除 Post
- 验证 post_tags 表中不再有该 Post 的记录

### Property 14: Slug 规范化防止语义重复

*对于任意* 常见的语义重复 Tag 名称（如 "js", "JS", "JavaScript"），规范化后应该生成不同的 slug（除非它们确实相同）

**验证方式**: Requirements 5.2.3

**测试策略**:
- 测试常见的缩写和全称（如 "js" vs "javascript"）
- 验证它们生成不同的 slug
- 测试大小写变体（如 "JS" vs "js"）
- 验证它们生成相同的 slug



---

## 部署和运维

### 1. 数据库初始化

#### 初始化脚本顺序

```sql
-- V1: 创建 tags 表
-- V2: 创建 post_tags 关联表
-- V3: 创建 tag_stats 统计表（可选）
-- V4: 添加外键约束
-- V5: 创建索引
```

**说明**：这是新功能开发，不涉及现有数据迁移。所有表结构将在初始化时创建。



### 2. 监控指标

#### 业务指标

- **Tag 总数**：系统中的标签总数
- **热门 Tag Top 10**：按文章数量排序的前 10 个标签
- **孤儿 Tag 数量**：没有关联任何文章的标签数量
- **平均每篇文章的 Tag 数量**：post_tags 总数 / posts 总数

#### 性能指标

- **Tag 查询响应时间**：P50, P95, P99
- **Post-Tag 关联查询响应时间**：P50, P95, P99
- **按 Tag 查询 Post 响应时间**：P50, P95, P99
- **Tag 创建 QPS**：每秒创建的标签数量
- **Post-Tag 关联 QPS**：每秒创建的关联数量

#### 缓存指标

- **Tag 缓存命中率**：tag:slug:{slug} 的命中率
- **Post-Tag 关联缓存命中率**：post:tags:{postId} 的命中率
- **热门 Tag 缓存命中率**：tags:hot 的命中率

### 3. 告警规则

```yaml
alerts:
  - name: TagQuerySlowAlert
    condition: tag_query_p95 > 100ms
    severity: warning
    message: "Tag 查询响应时间过慢"
  
  - name: TagCacheMissRateHighAlert
    condition: tag_cache_miss_rate > 0.3
    severity: warning
    message: "Tag 缓存未命中率过高"
  
  - name: OrphanTagsHighAlert
    condition: orphan_tags_count > 1000
    severity: info
    message: "孤儿标签数量过多，建议清理"
  
  - name: PostTagsExceedLimitAlert
    condition: post_tags_exceed_limit_count > 10
    severity: warning
    message: "有文章的标签数量超过限制"
```



### 4. 性能优化建议

#### 数据库层面

1. **索引优化**：
   - tags.slug 唯一索引（已有）
   - post_tags (post_id, tag_id) 复合主键（已有）
   - post_tags.tag_id 索引（已有）
   - 定期 ANALYZE 表以更新统计信息

2. **查询优化**：
   - 使用 JOIN 而非 N+1 查询
   - 批量查询 Tag 信息
   - 使用 EXPLAIN ANALYZE 分析慢查询

3. **分区策略**（可选）：
   - 如果 post_tags 表过大，可按 created_at 分区
   - 按月或按年分区，便于归档历史数据

#### 缓存层面

1. **多级缓存**：
   - L1: 本地缓存（Caffeine）- 热点 Tag
   - L2: Redis 缓存 - 所有 Tag 和关联关系

2. **缓存预热**：
   - 应用启动时加载热门 Tag
   - 定时任务刷新热门 Tag 缓存

3. **缓存更新策略**：
   - Tag 创建/更新：立即更新缓存
   - Post-Tag 关联变更：删除相关缓存，懒加载
   - 统计数据：异步更新，允许短暂不一致

#### 应用层面

1. **批量操作**：
   - 批量创建 Tag
   - 批量建立 Post-Tag 关联
   - 批量查询 Tag 信息

2. **异步处理**：
   - Tag 统计更新异步化
   - Elasticsearch 索引更新异步化
   - MongoDB 数据同步异步化

3. **限流保护**：
   - Tag 创建限流（防止恶意创建大量标签）
   - Post-Tag 关联限流（防止批量操作）



---

## 扩展性设计

### 1. 未来功能扩展

#### Tag 元数据扩展

```java
public class Tag {
    // 现有字段
    private final Long id;
    private String name;
    private final String slug;
    private String description;
    
    // 未来可扩展字段
    private String color;           // 标签颜色（用于 UI 展示）
    private String icon;            // 标签图标
    private String coverImage;      // 标签封面图
    private Integer sortOrder;      // 排序权重
    private Boolean isOfficial;     // 是否为官方标签
    private Boolean isHidden;       // 是否隐藏
}
```

#### Tag 层级关系

```java
public class Tag {
    // 父标签ID（支持层级分类）
    private Long parentId;
    
    // 查询子标签
    public List<Tag> getChildren();
    
    // 查询父标签
    public Optional<Tag> getParent();
}
```

#### Tag 别名和合并

```java
public class TagAlias {
    private final Long id;
    private final Long tagId;       // 主标签ID
    private final String alias;     // 别名
    
    // 查询别名对应的主标签
    public Tag resolveToMainTag();
}

// 标签合并
public interface TagMergeService {
    // 将 sourceTag 合并到 targetTag
    void mergeTags(Long sourceTagId, Long targetTagId);
}
```



#### Tag 订阅功能

```java
public class TagSubscription {
    private final Long userId;
    private final Long tagId;
    private LocalDateTime subscribedAt;
    
    // 用户订阅标签
    public static TagSubscription subscribe(Long userId, Long tagId);
    
    // 取消订阅
    public void unsubscribe();
}

// 查询用户订阅的标签
public interface TagSubscriptionRepository {
    List<Tag> findSubscribedTagsByUserId(Long userId);
    List<Long> findSubscriberIdsByTagId(Long tagId);
}
```

#### Tag 推荐算法

```java
public interface TagRecommendationService {
    // 基于用户历史推荐标签
    List<Tag> recommendTagsForUser(Long userId, int limit);
    
    // 基于文章内容推荐标签
    List<Tag> recommendTagsForPost(String content, int limit);
    
    // 相关标签推荐
    List<Tag> findRelatedTags(Long tagId, int limit);
}
```

### 2. 国际化支持

```java
public class TagTranslation {
    private final Long tagId;
    private final String locale;    // zh-CN, en-US, ja-JP
    private String name;
    private String description;
}

// 多语言查询
public interface TagRepository {
    Optional<Tag> findBySlugWithTranslation(String slug, String locale);
}
```

### 3. SEO 优化

```java
public class Tag {
    // SEO 相关字段
    private String metaTitle;       // SEO 标题
    private String metaDescription; // SEO 描述
    private String metaKeywords;    // SEO 关键词
    
    // 生成 SEO 友好的 URL
    public String getSeoUrl() {
        return "/tags/" + slug;
    }
}
```



---

## 安全性考虑

### 1. 输入验证

```java
public class TagValidator {
    // 标签名称验证
    public void validateTagName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ValidationException("标签名称不能为空");
        }
        if (name.length() > 50) {
            throw new ValidationException("标签名称不能超过50字符");
        }
        if (!name.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_]+$")) {
            throw new ValidationException("标签名称只能包含中文、英文、数字、空格、连字符和下划线");
        }
    }
    
    // 防止 XSS 攻击
    public String sanitizeTagName(String name) {
        return HtmlUtils.htmlEscape(name.trim());
    }
    
    // 防止 SQL 注入（使用参数化查询）
    // 所有数据库操作都使用 JPA/MyBatis 的参数绑定
}
```

### 2. 权限控制

```java
public interface TagPermissionService {
    // 检查用户是否可以创建标签
    boolean canCreateTag(Long userId);
    
    // 检查用户是否可以编辑标签
    boolean canEditTag(Long userId, Long tagId);
    
    // 检查用户是否可以删除标签
    boolean canDeleteTag(Long userId, Long tagId);
}

// 权限规则
// - 普通用户：可以为自己的文章添加标签（自动创建）
// - 管理员：可以创建、编辑、删除任何标签
// - 系统：可以合并、隐藏标签
```

### 3. 防滥用机制

```java
public class TagAbusePreventionService {
    // 限制用户创建标签的频率
    @RateLimiter(key = "tag:create:#{userId}", rate = 10, period = "1m")
    public Tag createTag(Long userId, String name);
    
    // 限制单篇文章的标签数量
    public void validatePostTagCount(Long postId, int newTagCount) {
        if (newTagCount > 10) {
            throw new BusinessException("单篇文章最多只能添加10个标签");
        }
    }
    
    // 检测恶意标签（敏感词、垃圾信息）
    public boolean isMaliciousTag(String name) {
        return sensitiveWordFilter.contains(name);
    }
}
```



---

## 总结

### 设计亮点

1. **Tag 作为独立聚合根**：
   - Tag 不是简单的字符串，而是具有唯一标识的领域对象
   - 支持未来的元数据扩展（颜色、图标、描述等）

2. **多对多关系清晰建模**：
   - 通过 post_tags 关联表实现灵活的多对多关系
   - 支持高效的双向查询（Post -> Tags, Tag -> Posts）

3. **Slug 规范化保证唯一性**：
   - 自动将 Tag 名称规范化为 URL 友好的 slug
   - 防止因大小写、空格差异导致的重复标签

4. **多层缓存优化性能**：
   - 本地缓存 + Redis 缓存
   - 热点数据预加载
   - 缓存失效策略清晰

5. **读写分离架构**：
   - PostgreSQL 作为写模型和权威数据源
   - MongoDB 作为读模型，冗余 Tag 信息
   - Elasticsearch 支持全文搜索

6. **事件驱动的数据同步**：
   - 通过领域事件同步 MongoDB 和 Elasticsearch
   - 异步更新统计数据
   - 解耦各个存储层

7. **可扩展性设计**：
   - 预留了 Tag 层级、别名、订阅等扩展空间
   - 数据模型设计支持未来演进
   - 不破坏现有功能

### 技术债务

1. **缓存一致性**：
   - 多层缓存可能导致短暂的数据不一致
   - 需要合理的缓存失效策略

2. **性能优化空间**：
   - 大规模数据下的查询性能需要持续优化
   - 可能需要引入分区、分表等技术

### 下一步行动

1. **实现核心功能**：
   - Tag 领域模型
   - TagRepository 和 PostTagRepository
   - TagDomainService

2. **实现应用服务**：
   - PostApplicationService 扩展
   - TagApplicationService

3. **实现 API 接口**：
   - TagController
   - PostController 扩展

4. **数据库迁移**：
   - 创建 tags 表
   - 创建 post_tags 关联表
   - 创建索引

5. **测试**：
   - 单元测试
   - 集成测试
   - 性能测试

6. **监控和告警**：
   - 添加业务指标
   - 配置告警规则

