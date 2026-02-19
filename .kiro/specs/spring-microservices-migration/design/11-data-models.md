# 数据模型设计

## 领域事件定义

### 基础事件类

```java
public abstract class DomainEvent {
    private final String eventId;
    private final LocalDateTime occurredAt;
    
    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }
    
    public abstract String getRoutingKey();
    
    // getters
}
```

### Post 领域事件

```java
public class PostPublishedEvent extends DomainEvent {
    private final Long postId;
    private final String authorId;
    private final String title;
    private final List<String> tags;
    
    @Override
    public String getRoutingKey() {
        return "post.published";
    }
}

public class PostUpdatedEvent extends DomainEvent {
    private final Long postId;
    private final String title;
    private final String content;
    private final String excerpt;
    private final List<String> tags;
    
    @Override
    public String getRoutingKey() {
        return "post.updated";
    }
}

public class PostDeletedEvent extends DomainEvent {
    private final Long postId;
    private final String authorId;
    
    @Override
    public String getRoutingKey() {
        return "post.deleted";
    }
}

public class PostLikedEvent extends DomainEvent {
    private final Long postId;
    private final String postOwnerId;
    private final String likerId;
    
    @Override
    public String getRoutingKey() {
        return "post.liked";
    }
}
```


### Comment 领域事件

```java
public class CommentCreatedEvent extends DomainEvent {
    private final Long commentId;
    private final Long postId;
    private final String postOwnerId;
    private final String commentAuthorId;
    private final Long parentId;
    private final String replyToUserId;
    private final String commentContent;
    
    @Override
    public String getRoutingKey() {
        return "comment.created";
    }
}

public class CommentLikedEvent extends DomainEvent {
    private final Long commentId;
    private final String commentOwnerId;
    private final String likerId;
    
    @Override
    public String getRoutingKey() {
        return "comment.liked";
    }
}
```

### User 领域事件

```java
public class UserFollowedEvent extends DomainEvent {
    private final String followerId;
    private final String followingId;
    
    @Override
    public String getRoutingKey() {
        return "user.followed";
    }
}

public class UserProfileUpdatedEvent extends DomainEvent {
    private final String userId;
    private final String nickName;
    private final String avatarUrl;
    
    @Override
    public String getRoutingKey() {
        return "user.profile.updated";
    }
}
```

---

## Redis Key 规范

### Key 命名规则

> **设计说明：统一 Redis Key 命名规范**
> 
> 所有服务统一使用 `{service}:{entity}:{id}:{field}:{subId}` 格式，确保：
> 1. 命名一致性，便于维护和排查问题
> 2. 避免不同服务间的 Key 冲突
> 3. 便于按前缀批量操作（如清理某个服务的所有缓存）

```
{service}:{entity}:{id}:{field}:{subId}

示例：
- post:123                      # 文章详情
- post:123:like_count           # 文章点赞数
- post:123:liked:user-abc       # 用户是否点赞文章
- user:abc:following_count      # 用户关注数
- comment:456:like_count        # 评论点赞数
```

### User Service Keys

```java
public class UserRedisKeys {
    private static final String PREFIX = "user:";
    
    // 用户详情缓存
    public static String detail(String userId) {
        return PREFIX + userId;
    }
    
    // 用户关注数
    public static String followingCount(String userId) {
        return PREFIX + userId + ":following_count";
    }
    
    // 用户粉丝数
    public static String followersCount(String userId) {
        return PREFIX + userId + ":followers_count";
    }
    
    // 用户是否关注某人
    public static String isFollowing(String followerId, String followingId) {
        return PREFIX + followerId + ":following:" + followingId;
    }
    
    // 用户签到位图
    public static String checkInBitmap(String userId, String yearMonth) {
        return PREFIX + userId + ":checkin:" + yearMonth;
    }
    
    // 用户 Session
    public static String session(String userId, String deviceId) {
        return PREFIX + userId + ":session:" + deviceId;
    }
}
```

### Post Service Keys

> **注意：Key 格式已统一为 `post:{postId}:{field}:{subId}`**

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
    
    // 评论数
    public static String commentCount(Long postId) {
        return PREFIX + postId + ":comment_count";
    }
    
    // 收藏数
    public static String favoriteCount(Long postId) {
        return PREFIX + postId + ":favorite_count";
    }
    
    // 浏览数
    public static String viewCount(Long postId) {
        return PREFIX + postId + ":view_count";
    }
    
    // 用户是否点赞文章（格式：post:{postId}:liked:{userId}）
    public static String userLiked(String userId, Long postId) {
        return PREFIX + postId + ":liked:" + userId;
    }
    
    // 用户是否收藏文章（格式：post:{postId}:favorited:{userId}）
    public static String userFavorited(String userId, Long postId) {
        return PREFIX + postId + ":favorited:" + userId;
    }
}
```


### Comment Service Keys

```java
public class CommentRedisKeys {
    private static final String PREFIX = "comment:";
    
    // 评论详情缓存
    public static String detail(Long commentId) {
        return PREFIX + commentId;
    }
    
    // 评论点赞数
    public static String likeCount(Long commentId) {
        return PREFIX + commentId + ":like_count";
    }
    
    // 评论回复数
    public static String replyCount(Long commentId) {
        return PREFIX + commentId + ":reply_count";
    }
    
    // 用户是否点赞评论
    public static String userLiked(String userId, Long commentId) {
        return PREFIX + commentId + ":liked:" + userId;
    }
    
    // 文章评论列表缓存
    public static String postComments(Long postId, String sortType) {
        return PREFIX + "list:" + postId + ":" + sortType;
    }
}
```

### Notification Service Keys

```java
public class NotificationRedisKeys {
    private static final String PREFIX = "notification:";
    
    // 用户未读通知数
    public static String unreadCount(String userId) {
        return PREFIX + userId + ":unread_count";
    }
    
    // 聚合通知列表缓存
    public static String aggregatedList(String userId, int page, int size) {
        return PREFIX + userId + ":aggregated:" + page + ":" + size;
    }
    
    // 聚合通知列表缓存模式（用于批量删除）
    public static String aggregatedListPattern(String userId) {
        return PREFIX + userId + ":aggregated:*";
    }
}
```

### Ranking Service Keys

```java
public class RankingRedisKeys {
    private static final String PREFIX = "ranking:";
    
    // 文章热度排行榜
    public static String hotPosts() {
        return PREFIX + "posts:hot";
    }
    
    // 日榜
    public static String dailyHotPosts(LocalDate date) {
        return PREFIX + "posts:daily:" + date;
    }
    
    // 周榜
    public static String weeklyHotPosts(int weekNumber) {
        return PREFIX + "posts:weekly:" + weekNumber;
    }
    
    // 创作者排行榜
    public static String hotCreators() {
        return PREFIX + "creators:hot";
    }
    
    // 话题排行榜
    public static String hotTopics() {
        return PREFIX + "topics:hot";
    }
}
```

---

## 通用枚举定义

```java
// 文章状态
public enum PostStatus {
    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    SCHEDULED(2, "定时发布"),
    DELETED(3, "已删除");
    
    private final int code;
    private final String description;
}

// 评论状态
public enum CommentStatus {
    NORMAL(0, "正常"),
    DELETED(1, "已删除");
}

// 消息类型
public enum MessageType {
    TEXT(0, "文本"),
    IMAGE(1, "图片"),
    FILE(2, "文件");
}

// 消息状态
public enum MessageStatus {
    SENT(0, "已发送"),
    RECALLED(1, "已撤回");
}

// 通知类型
public enum NotificationType {
    LIKE(0, "点赞"),
    COMMENT(1, "评论"),
    FOLLOW(2, "关注"),
    REPLY(3, "回复"),
    SYSTEM(4, "系统");
}

// 用户状态
public enum UserStatus {
    ACTIVE(0, "正常"),
    DISABLED(1, "禁用");
}
```
