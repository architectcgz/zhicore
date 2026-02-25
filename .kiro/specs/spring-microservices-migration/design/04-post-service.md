# Post Service 设计

## DDD 分层结构

```
post-service/
├── src/main/java/com/ZhiCore/post/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   ├── PostController.java
│   │   │   ├── PostLikeController.java
│   │   │   ├── PostFavoriteController.java
│   │   │   └── CategoryController.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   ├── PostApplicationService.java
│   │   │   ├── PostLikeApplicationService.java
│   │   │   └── PostFavoriteApplicationService.java
│   │   └── event/
│   │       └── PostEventPublisher.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Post.java              # 聚合根
│   │   │   ├── PostStats.java         # 值对象
│   │   │   ├── PostLike.java
│   │   │   ├── PostFavorite.java
│   │   │   └── Category.java
│   │   ├── repository/
│   │   │   ├── PostRepository.java
│   │   │   ├── PostLikeRepository.java
│   │   │   └── CategoryRepository.java
│   │   ├── service/
│   │   │   └── PostDomainService.java
│   │   └── event/
│   │       ├── PostPublishedEvent.java
│   │       ├── PostUpdatedEvent.java
│   │       └── PostLikedEvent.java
│   └── infrastructure/
│       ├── repository/
│       │   ├── PostRepositoryImpl.java
│       │   ├── CachedPostRepository.java
│       │   └── mapper/
│       └── feign/
│           └── UserServiceClient.java
```

## Post 聚合根（充血模型）

```java
public class Post {
    private final Long id;
    private final String ownerId;
    private final LocalDateTime createdAt;
    
    private String title;
    private String raw;
    private String html;
    private String excerpt;
    private PostStatus status;
    private Long topicId;
    private LocalDateTime publishedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime updatedAt;
    private PostStats stats;
    
    // 私有构造函数
    private Post(Long id, String ownerId, String title, String raw) {
        Assert.notNull(id, "文章ID不能为空");
        Assert.hasText(ownerId, "作者ID不能为空");
        Assert.hasText(title, "标题不能为空");
        
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.raw = raw;
        this.status = PostStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.stats = PostStats.empty();
    }
    
    // 工厂方法 - 创建草稿
    public static Post createDraft(Long id, String ownerId, String title, String content) {
        Post post = new Post(id, ownerId, title, content);
        post.html = ContentRenderer.render(content);
        post.excerpt = ContentRenderer.generateExcerpt(content, 200);
        return post;
    }
    
    // 领域行为 - 发布文章
    public void publish() {
        if (this.status == PostStatus.PUBLISHED) {
            throw new DomainException("文章已经发布，不能重复发布");
        }
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("已删除的文章不能发布");
        }
        if (!StringUtils.hasText(this.title)) {
            throw new DomainException("文章标题不能为空");
        }
        if (this.title.length() > 200) {
            throw new DomainException("文章标题不能超过200字");
        }
        
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.scheduledAt = null;
    }
    
    // 领域行为 - 定时发布
    public void schedulePublish(LocalDateTime scheduledAt) {
        if (this.status != PostStatus.DRAFT) {
            throw new DomainException("只有草稿状态的文章可以设置定时发布");
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new DomainException("定时发布时间不能早于当前时间");
        }
        
        this.scheduledAt = scheduledAt;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 领域行为 - 撤回
    public void unpublish() {
        if (this.status != PostStatus.PUBLISHED) {
            throw new DomainException("只有已发布的文章可以撤回");
        }
        this.status = PostStatus.DRAFT;
        this.publishedAt = null;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 领域行为 - 软删除
    public void delete() {
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("文章已经删除");
        }
        this.status = PostStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 查询方法
    public boolean canBePublished() {
        return this.status == PostStatus.DRAFT && 
               StringUtils.hasText(this.title) && 
               StringUtils.hasText(this.raw);
    }
    
    public boolean isOwnedBy(String userId) {
        return this.ownerId.equals(userId);
    }
}

// 值对象 - 文章统计（不可变）
public final class PostStats {
    private final int likeCount;
    private final int commentCount;
    private final int favoriteCount;
    private final long viewCount;
    
    public PostStats(int likeCount, int commentCount, int favoriteCount, long viewCount) {
        this.likeCount = Math.max(0, likeCount);
        this.commentCount = Math.max(0, commentCount);
        this.favoriteCount = Math.max(0, favoriteCount);
        this.viewCount = Math.max(0, viewCount);
    }
    
    public static PostStats empty() {
        return new PostStats(0, 0, 0, 0);
    }
    
    public PostStats incrementViews() {
        return new PostStats(likeCount, commentCount, favoriteCount, viewCount + 1);
    }
    
    public PostStats incrementLikes() {
        return new PostStats(likeCount + 1, commentCount, favoriteCount, viewCount);
    }
}
```


## 文章点赞服务

```java
@Service
public class PostLikeApplicationService {
    
    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LeafIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    
    /**
     * 点赞文章
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void likePost(String userId, Long postId) {
        // 检查是否已点赞（先查 Redis）
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        
        if (Boolean.TRUE.equals(alreadyLiked)) {
            throw new BusinessException("已经点赞过了");
        }
        
        // 检查文章是否存在
        Post post = postRepository.findById(postId);
        if (post == null || post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章不存在或未发布");
        }
        
        String authorId = post.getOwnerId();
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            PostLike like = new PostLike(idGenerator.nextId(), postId, userId);
            likeRepository.save(like);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
            redisTemplate.opsForValue().set(likeKey, "1");
        } catch (Exception e) {
            // Redis 更新失败不影响主流程，记录日志后续通过定时任务修复
            log.warn("Redis 更新失败，postId={}, userId={}", postId, userId, e);
        }
        
        // 发布事件（用于通知、排行榜更新）
        eventPublisher.publish(new PostLikedEvent(postId, userId, authorId));
    }
    
    /**
     * 取消点赞
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void unlikePost(String userId, Long postId) {
        // 检查是否已点赞
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        Boolean liked = redisTemplate.hasKey(likeKey);
        
        if (!Boolean.TRUE.equals(liked)) {
            throw new BusinessException("尚未点赞");
        }
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            likeRepository.delete(postId, userId);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(PostRedisKeys.likeCount(postId));
            redisTemplate.delete(likeKey);
        } catch (Exception e) {
            // Redis 更新失败不影响主流程，记录日志后续通过定时任务修复
            log.warn("Redis 更新失败，postId={}, userId={}", postId, userId, e);
        }
        
        // 发布事件
        eventPublisher.publish(new PostUnlikedEvent(postId, userId));
    }
    
    /**
     * 检查是否已点赞
     */
    public boolean isLiked(String userId, Long postId) {
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(likeKey));
    }
    
    /**
     * 批量检查点赞状态（使用 Pipeline 优化）
     */
    public Map<Long, Boolean> batchCheckLiked(String userId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<String> keys = postIds.stream()
            .map(id -> PostRedisKeys.userLiked(userId, id))
            .collect(Collectors.toList());
        
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.exists(key.getBytes());
            }
            return null;
        });
        
        Map<Long, Boolean> likedMap = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            likedMap.put(postIds.get(i), Boolean.TRUE.equals(results.get(i)));
        }
        return likedMap;
    }
    
    /**
     * 获取用户点赞的文章列表（游标分页）
     */
    @ReadOnly
    public CursorPage<PostBriefVO> getUserLikedPosts(String userId, String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);
        
        List<PostLike> likes = likeRepository.findByUserIdCursor(userId, timeCursor, size + 1);
        
        boolean hasMore = likes.size() > size;
        String nextCursor = null;
        if (hasMore) {
            likes = likes.subList(0, size);
            PostLike lastLike = likes.get(likes.size() - 1);
            nextCursor = timeCursorCodec.encode(lastLike.getCreatedAt(), lastLike.getPostId());
        }
        
        // 批量获取文章信息
        List<Long> postIds = likes.stream().map(PostLike::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = postRepository.findByIds(postIds);
        
        List<PostBriefVO> voList = likes.stream()
            .map(like -> {
                Post post = postMap.get(like.getPostId());
                return post != null ? PostBriefVO.from(post) : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return new CursorPage<>(voList, nextCursor, hasMore);
    }
}
```

## 文章收藏服务

```java
@Service
public class PostFavoriteApplicationService {
    
    private final PostFavoriteRepository favoriteRepository;
    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LeafIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    
    /**
     * 收藏文章
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void favoritePost(String userId, Long postId) {
        // 检查是否已收藏
        String favoriteKey = PostRedisKeys.userFavorited(userId, postId);
        Boolean alreadyFavorited = redisTemplate.hasKey(favoriteKey);
        
        if (Boolean.TRUE.equals(alreadyFavorited)) {
            throw new BusinessException("已经收藏过了");
        }
        
        // 检查文章是否存在
        Post post = postRepository.findById(postId);
        if (post == null || post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章不存在或未发布");
        }
        
        String authorId = post.getOwnerId();
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            PostFavorite favorite = new PostFavorite(idGenerator.nextId(), postId, userId);
            favoriteRepository.save(favorite);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(PostRedisKeys.favoriteCount(postId));
            redisTemplate.opsForValue().set(favoriteKey, "1");
        } catch (Exception e) {
            log.warn("Redis 更新失败，postId={}, userId={}", postId, userId, e);
        }
        
        // 发布事件
        eventPublisher.publish(new PostFavoritedEvent(postId, userId, authorId));
    }
    
    /**
     * 取消收藏
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void unfavoritePost(String userId, Long postId) {
        String favoriteKey = PostRedisKeys.userFavorited(userId, postId);
        Boolean favorited = redisTemplate.hasKey(favoriteKey);
        
        if (!Boolean.TRUE.equals(favorited)) {
            throw new BusinessException("尚未收藏");
        }
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            favoriteRepository.delete(postId, userId);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(PostRedisKeys.favoriteCount(postId));
            redisTemplate.delete(favoriteKey);
        } catch (Exception e) {
            log.warn("Redis 更新失败，postId={}, userId={}", postId, userId, e);
        }
        
        eventPublisher.publish(new PostUnfavoritedEvent(postId, userId));
    }
    
    /**
     * 批量检查收藏状态
     */
    public Map<Long, Boolean> batchCheckFavorited(String userId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<String> keys = postIds.stream()
            .map(id -> PostRedisKeys.userFavorited(userId, id))
            .collect(Collectors.toList());
        
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.exists(key.getBytes());
            }
            return null;
        });
        
        Map<Long, Boolean> favoritedMap = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            favoritedMap.put(postIds.get(i), Boolean.TRUE.equals(results.get(i)));
        }
        return favoritedMap;
    }
    
    /**
     * 获取用户收藏的文章列表
     */
    @ReadOnly
    public CursorPage<PostBriefVO> getUserFavoritePosts(String userId, String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);
        
        List<PostFavorite> favorites = favoriteRepository.findByUserIdCursor(userId, timeCursor, size + 1);
        
        boolean hasMore = favorites.size() > size;
        String nextCursor = null;
        if (hasMore) {
            favorites = favorites.subList(0, size);
            PostFavorite lastFavorite = favorites.get(favorites.size() - 1);
            nextCursor = timeCursorCodec.encode(lastFavorite.getCreatedAt(), lastFavorite.getPostId());
        }
        
        List<Long> postIds = favorites.stream().map(PostFavorite::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = postRepository.findByIds(postIds);
        
        List<PostBriefVO> voList = favorites.stream()
            .map(fav -> {
                Post post = postMap.get(fav.getPostId());
                return post != null ? PostBriefVO.from(post) : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return new CursorPage<>(voList, nextCursor, hasMore);
    }
}
```

## 点赞/收藏 Controller

```java
@RestController
@RequestMapping("/api/v1/posts")
public class PostLikeController {
    
    private final PostLikeApplicationService likeService;
    
    @PostMapping("/{postId}/like")
    @RequirePermission("post:like")
    public Result<Void> likePost(@PathVariable Long postId, @CurrentUser String userId) {
        likeService.likePost(userId, postId);
        return Result.success();
    }
    
    @DeleteMapping("/{postId}/like")
    public Result<Void> unlikePost(@PathVariable Long postId, @CurrentUser String userId) {
        likeService.unlikePost(userId, postId);
        return Result.success();
    }
    
    @GetMapping("/{postId}/like/status")
    public Result<Boolean> checkLikeStatus(@PathVariable Long postId, @CurrentUser String userId) {
        boolean liked = likeService.isLiked(userId, postId);
        return Result.success(liked);
    }
    
    @GetMapping("/liked")
    public Result<CursorPage<PostBriefVO>> getUserLikedPosts(
            @CurrentUser String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        CursorPage<PostBriefVO> result = likeService.getUserLikedPosts(userId, cursor, size);
        return Result.success(result);
    }
}

@RestController
@RequestMapping("/api/v1/posts")
public class PostFavoriteController {
    
    private final PostFavoriteApplicationService favoriteService;
    
    @PostMapping("/{postId}/favorite")
    @RequirePermission("post:favorite")
    public Result<Void> favoritePost(@PathVariable Long postId, @CurrentUser String userId) {
        favoriteService.favoritePost(userId, postId);
        return Result.success();
    }
    
    @DeleteMapping("/{postId}/favorite")
    public Result<Void> unfavoritePost(@PathVariable Long postId, @CurrentUser String userId) {
        favoriteService.unfavoritePost(userId, postId);
        return Result.success();
    }
    
    @GetMapping("/favorited")
    public Result<CursorPage<PostBriefVO>> getUserFavoritePosts(
            @CurrentUser String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        CursorPage<PostBriefVO> result = favoriteService.getUserFavoritePosts(userId, cursor, size);
        return Result.success(result);
    }
}
```

## 数据库表设计 (Post_DB)

```sql
-- 文章表
CREATE TABLE posts (
    id BIGINT PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    raw TEXT,
    html TEXT,
    excerpt VARCHAR(500),
    status SMALLINT DEFAULT 0,       -- 0:草稿 1:已发布 2:已删除
    topic_id BIGINT,
    published_at TIMESTAMPTZ,
    scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_posts_owner ON posts(owner_id, status, created_at DESC);
CREATE INDEX idx_posts_topic ON posts(topic_id, status, published_at DESC);
CREATE INDEX idx_posts_published ON posts(status, published_at DESC) WHERE status = 1;

-- 文章统计表
CREATE TABLE post_stats (
    post_id BIGINT PRIMARY KEY,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    view_count BIGINT DEFAULT 0
);

-- 文章点赞表
CREATE TABLE post_likes (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_likes_user ON post_likes(user_id, created_at DESC);
CREATE INDEX idx_post_likes_post ON post_likes(post_id, created_at DESC);

-- 文章收藏表
CREATE TABLE post_favorites (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_favorites_user ON post_favorites(user_id, created_at DESC);
CREATE INDEX idx_post_favorites_post ON post_favorites(post_id, created_at DESC);

-- 分类表
CREATE TABLE categories (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    slug VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    parent_id BIGINT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

## Redis Key 设计

> **注意：Key 格式与 `11-data-models.md` 保持一致**
> 
> 统一使用 `post:{postId}:{field}:{subId}` 格式

```java
public class PostRedisKeys {
    
    private static final String PREFIX = "post:";
    
    // 文章详情缓存
    public static String detail(Long postId) {
        return PREFIX + postId;
    }
    
    // 点赞数
    public static String likeCount(Long postId) {
        return PREFIX + postId + ":like_count";
    }
    
    // 用户是否点赞（格式：post:{postId}:liked:{userId}）
    public static String userLiked(String userId, Long postId) {
        return PREFIX + postId + ":liked:" + userId;
    }
    
    // 收藏数
    public static String favoriteCount(Long postId) {
        return PREFIX + postId + ":favorite_count";
    }
    
    // 用户是否收藏（格式：post:{postId}:favorited:{userId}）
    public static String userFavorited(String userId, Long postId) {
        return PREFIX + postId + ":favorited:" + userId;
    }
    
    // 评论数
    public static String commentCount(Long postId) {
        return PREFIX + postId + ":comment_count";
    }
    
    // 浏览数
    public static String viewCount(Long postId) {
        return PREFIX + postId + ":view_count";
    }
}
```

## 领域事件定义

```java
// 文章点赞事件
@Data
@AllArgsConstructor
public class PostLikedEvent implements DomainEvent {
    private Long postId;
    private String userId;      // 点赞用户
    private String authorId;    // 文章作者（用于发送通知）
    
    @Override
    public String getRoutingKey() {
        return "post.liked";
    }
}

// 文章取消点赞事件
@Data
@AllArgsConstructor
public class PostUnlikedEvent implements DomainEvent {
    private Long postId;
    private String userId;
    
    @Override
    public String getRoutingKey() {
        return "post.unliked";
    }
}

// 文章收藏事件
@Data
@AllArgsConstructor
public class PostFavoritedEvent implements DomainEvent {
    private Long postId;
    private String userId;
    private String authorId;
    
    @Override
    public String getRoutingKey() {
        return "post.favorited";
    }
}

// 文章取消收藏事件
@Data
@AllArgsConstructor
public class PostUnfavoritedEvent implements DomainEvent {
    private Long postId;
    private String userId;
    
    @Override
    public String getRoutingKey() {
        return "post.unfavorited";
    }
}
```
