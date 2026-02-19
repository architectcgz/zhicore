# Notification Service 设计

## DDD 分层结构

```
notification-service/
├── src/main/java/com/blog/notification/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   └── NotificationController.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   ├── NotificationApplicationService.java
│   │   │   └── NotificationAggregationService.java
│   │   └── event/
│   │       └── NotificationEventConsumer.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Notification.java      # 聚合根
│   │   │   ├── NotificationType.java
│   │   │   └── GlobalAnnouncement.java
│   │   ├── repository/
│   │   │   └── NotificationRepository.java
│   │   └── service/
│   │       └── NotificationDomainService.java
│   └── infrastructure/
│       ├── repository/
│       │   └── mapper/
│       ├── push/
│       │   └── NotificationPushService.java
│       └── mq/
│           └── NotificationEventConsumerImpl.java
```


## Notification 聚合根（充血模型）

```java
public class Notification {
    private final Long id;
    private final String recipientId;
    private final NotificationType type;
    private final LocalDateTime createdAt;
    
    private String actorId;           // 触发者ID
    private String targetType;        // 目标类型（post/comment）
    private Long targetId;            // 目标ID
    private String content;           // 通知内容
    private boolean isRead;
    private LocalDateTime readAt;
    
    // 私有构造函数
    private Notification(Long id, String recipientId, NotificationType type) {
        Assert.notNull(id, "通知ID不能为空");
        Assert.hasText(recipientId, "接收者ID不能为空");
        Assert.notNull(type, "通知类型不能为空");
        
        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }
    
    // 工厂方法 - 创建点赞通知
    public static Notification createLikeNotification(Long id, String recipientId,
                                                       String actorId, String targetType, 
                                                       Long targetId) {
        Notification notification = new Notification(id, recipientId, NotificationType.LIKE);
        notification.actorId = actorId;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.content = "赞了你的" + (targetType.equals("post") ? "文章" : "评论");
        return notification;
    }
    
    // 工厂方法 - 创建评论通知
    public static Notification createCommentNotification(Long id, String recipientId,
                                                          String actorId, Long postId,
                                                          Long commentId, String commentContent) {
        Notification notification = new Notification(id, recipientId, NotificationType.COMMENT);
        notification.actorId = actorId;
        notification.targetType = "post";
        notification.targetId = postId;
        notification.content = truncate(commentContent, 100);
        return notification;
    }
    
    // 工厂方法 - 创建关注通知
    public static Notification createFollowNotification(Long id, String recipientId,
                                                         String actorId) {
        Notification notification = new Notification(id, recipientId, NotificationType.FOLLOW);
        notification.actorId = actorId;
        notification.content = "关注了你";
        return notification;
    }
    
    // 领域行为 - 标记已读
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
    
    private static String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
```


## 通知聚合服务

### 设计思路

通知聚合的核心问题是：同类通知（如"多人点赞同一篇文章"）可能跨页分布，简单的分页后聚合会导致聚合不完整。

**解决方案：先聚合再分页**

1. 在数据库层面先按 `(type, target_type, target_id)` 分组聚合
2. 对聚合后的结果进行分页
3. 每个聚合组只返回最新的代表通知 + 聚合统计信息

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           通知聚合查询流程                                        │
│                                                                                  │
│   原始通知数据:                                                                   │
│   ┌────────────────────────────────────────────────────────────────────────┐    │
│   │ ID │ Type │ TargetId │ ActorId │ CreatedAt                             │    │
│   │ 1  │ LIKE │ post:100 │ userA   │ 10:00                                 │    │
│   │ 2  │ LIKE │ post:100 │ userB   │ 10:05                                 │    │
│   │ 3  │ LIKE │ post:100 │ userC   │ 10:10                                 │    │
│   │ 4  │ LIKE │ post:200 │ userD   │ 10:15                                 │    │
│   │ 5  │ FOLLOW│ null    │ userE   │ 10:20                                 │    │
│   └────────────────────────────────────────────────────────────────────────┘    │
│                                        │                                         │
│                                        ▼                                         │
│   聚合后结果 (按 type+target 分组):                                               │
│   ┌────────────────────────────────────────────────────────────────────────┐    │
│   │ GroupKey      │ Count │ LatestId │ LatestTime │ Actors                 │    │
│   │ LIKE:post:100 │   3   │    3     │   10:10    │ [userC, userB, userA]  │    │
│   │ LIKE:post:200 │   1   │    4     │   10:15    │ [userD]                │    │
│   │ FOLLOW:null   │   1   │    5     │   10:20    │ [userE]                │    │
│   └────────────────────────────────────────────────────────────────────────┘    │
│                                        │                                         │
│                                        ▼                                         │
│   分页返回 (按 LatestTime 排序):                                                  │
│   Page 1: [FOLLOW:null, LIKE:post:200, LIKE:post:100]                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 数据库聚合查询

> **性能优化说明**
> 
> 聚合查询在大数据量下可能存在性能问题，采用以下优化策略：
> 1. **Redis 缓存聚合结果**：缓存用户的聚合通知列表，TTL 5分钟
> 2. **增量更新缓存**：新通知到达时，更新缓存而非重新查询
> 3. **物化视图（可选）**：对于超大数据量场景，可使用 PostgreSQL 物化视图预计算
> 4. **分区表**：按时间分区，只查询最近 N 天的通知

```java
// Repository 接口
public interface NotificationRepository {
    
    /**
     * 聚合查询通知
     * 先按 (type, target_type, target_id) 分组，再分页
     */
    List<AggregatedNotificationDTO> findAggregatedNotifications(
        String recipientId, int page, int size);
    
    /**
     * 查询某个聚合组的详细通知列表（用于展开查看）
     */
    List<Notification> findByGroup(String recipientId, NotificationType type,
                                   String targetType, Long targetId, int limit);
}

// MyBatis Mapper
@Mapper
public interface NotificationMapper {
    
    /**
     * 聚合查询 SQL
     * 使用窗口函数获取每组的统计信息和最新通知
     */
    @Select("""
        WITH grouped AS (
            SELECT 
                type,
                target_type,
                target_id,
                COUNT(*) as total_count,
                COUNT(*) FILTER (WHERE is_read = false) as unread_count,
                MAX(created_at) as latest_time,
                ARRAY_AGG(DISTINCT actor_id ORDER BY actor_id) 
                    FILTER (WHERE actor_id IS NOT NULL) as actor_ids
            FROM notifications
            WHERE recipient_id = #{recipientId}
            GROUP BY type, target_type, target_id
        ),
        ranked AS (
            SELECT 
                g.*,
                n.id as latest_notification_id,
                n.content as latest_content,
                ROW_NUMBER() OVER (ORDER BY g.latest_time DESC) as rn
            FROM grouped g
            JOIN notifications n ON (
                n.recipient_id = #{recipientId}
                AND n.type = g.type
                AND (n.target_type = g.target_type OR (n.target_type IS NULL AND g.target_type IS NULL))
                AND (n.target_id = g.target_id OR (n.target_id IS NULL AND g.target_id IS NULL))
                AND n.created_at = g.latest_time
            )
        )
        SELECT * FROM ranked
        WHERE rn > #{offset} AND rn <= #{offset} + #{size}
        ORDER BY latest_time DESC
        """)
    List<AggregatedNotificationDTO> findAggregatedNotifications(
        @Param("recipientId") String recipientId,
        @Param("offset") int offset,
        @Param("size") int size
    );
    
    /**
     * 获取聚合组总数（用于分页）
     */
    @Select("""
        SELECT COUNT(DISTINCT (type, target_type, target_id))
        FROM notifications
        WHERE recipient_id = #{recipientId}
        """)
    int countAggregatedGroups(@Param("recipientId") String recipientId);
}
```

### 聚合服务实现

```java
@Service
public class NotificationAggregationService {
    
    private final NotificationRepository notificationRepository;
    private final UserServiceClient userServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    
    /**
     * 获取聚合通知列表（带缓存）
     * 
     * @param userId 用户ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 聚合后的通知列表
     */
    public PageResult<AggregatedNotificationVO> getAggregatedNotifications(
            String userId, int page, int size) {
        
        // 1. 尝试从缓存获取
        String cacheKey = NotificationRedisKeys.aggregatedList(userId, page, size);
        PageResult<AggregatedNotificationVO> cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 2. 查询聚合后的通知（数据库层面已完成聚合）
        List<AggregatedNotificationDTO> aggregatedList = notificationRepository
            .findAggregatedNotifications(userId, page, size);
        
        // 3. 批量获取用户信息（避免 N+1）
        Set<String> allActorIds = aggregatedList.stream()
            .flatMap(dto -> dto.getActorIds().stream())
            .collect(Collectors.toSet());
        Map<String, UserDTO> userMap = userServiceClient.batchGetUsers(
            new ArrayList<>(allActorIds));
        
        // 4. 组装 VO
        List<AggregatedNotificationVO> voList = aggregatedList.stream()
            .map(dto -> buildAggregatedVO(dto, userMap))
            .collect(Collectors.toList());
        
        // 5. 获取总数
        int totalGroups = notificationRepository.countAggregatedGroups(userId);
        
        PageResult<AggregatedNotificationVO> result = PageResult.of(voList, page, size, totalGroups);
        
        // 6. 缓存结果
        cacheResult(cacheKey, result);
        
        return result;
    }
    
    /**
     * 从缓存获取聚合结果
     */
    @SuppressWarnings("unchecked")
    private PageResult<AggregatedNotificationVO> getCachedResult(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return (PageResult<AggregatedNotificationVO>) cached;
            }
        } catch (Exception e) {
            log.warn("获取通知缓存失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 缓存聚合结果
     */
    private void cacheResult(String cacheKey, PageResult<AggregatedNotificationVO> result) {
        try {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
        } catch (Exception e) {
            log.warn("缓存通知结果失败: {}", e.getMessage());
        }
    }
    
    /**
     * 清除用户的通知缓存（新通知到达时调用）
     */
    public void invalidateCache(String userId) {
        try {
            Set<String> keys = redisTemplate.keys(NotificationRedisKeys.aggregatedListPattern(userId));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清除通知缓存失败: {}", e.getMessage());
        }
    }
    
    private AggregatedNotificationVO buildAggregatedVO(
            AggregatedNotificationDTO dto, Map<String, UserDTO> userMap) {
        
        // 取最近的几个用户（最多3个）
        List<String> recentActorIds = dto.getActorIds().stream()
            .limit(3)
            .collect(Collectors.toList());
        
        List<UserDTO> recentActors = recentActorIds.stream()
            .map(userMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // 构建聚合文案
        String aggregatedContent = buildAggregatedContent(
            dto.getType(), 
            recentActors, 
            dto.getTotalCount()
        );
        
        return AggregatedNotificationVO.builder()
            .type(dto.getType())
            .targetType(dto.getTargetType())
            .targetId(dto.getTargetId())
            .totalCount(dto.getTotalCount())
            .unreadCount(dto.getUnreadCount())
            .latestTime(dto.getLatestTime())
            .latestContent(dto.getLatestContent())
            .recentActors(recentActors)
            .aggregatedContent(aggregatedContent)
            .build();
    }
    
    private String buildAggregatedContent(NotificationType type, 
                                          List<UserDTO> actors, int totalCount) {
        if (actors.isEmpty()) {
            return getActionText(type);
        }
        
        String firstActorName = actors.get(0).getNickName();
        
        if (totalCount == 1) {
            return firstActorName + getActionText(type);
        }
        
        return String.format("%s等%d人%s", firstActorName, totalCount, getActionText(type));
    }
    
    private String getActionText(NotificationType type) {
        return switch (type) {
            case LIKE -> "赞了你的内容";
            case COMMENT -> "评论了你的文章";
            case FOLLOW -> "关注了你";
            case REPLY -> "回复了你的评论";
            default -> "与你互动";
        };
    }
}
```

### 聚合 DTO 定义

```java
@Data
@Builder
public class AggregatedNotificationDTO {
    private NotificationType type;
    private String targetType;
    private Long targetId;
    private int totalCount;           // 该组通知总数
    private int unreadCount;          // 该组未读数
    private LocalDateTime latestTime; // 最新通知时间
    private Long latestNotificationId;
    private String latestContent;
    private List<String> actorIds;    // 所有触发者ID
}

@Data
@Builder
public class AggregatedNotificationVO {
    private NotificationType type;
    private String targetType;
    private Long targetId;
    private int totalCount;
    private int unreadCount;
    private LocalDateTime latestTime;
    private String latestContent;
    private List<UserDTO> recentActors;  // 最近的几个触发者
    private String aggregatedContent;     // 聚合后的文案
}
```
```

## 事件消费者

```java
/**
 * 点赞通知消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "liked",
    consumerGroup = "notification-post-liked-consumer"
)
public class PostLikedNotificationConsumer implements RocketMQListener<PostLikedEvent> {
    
    private final NotificationApplicationService notificationService;
    private final NotificationPushService pushService;
    
    @Override
    public void onMessage(PostLikedEvent event) {
        // 不给自己发通知
        if (event.getPostOwnerId().equals(event.getLikerId())) {
            return;
        }
        
        Notification notification = notificationService.createLikeNotification(
            event.getPostOwnerId(),
            event.getLikerId(),
            "post",
            event.getPostId()
        );
        
        // 实时推送
        pushService.push(event.getPostOwnerId(), notification);
    }
}

/**
 * 评论通知消费者
 */
@Component
@RocketMQMessageListener(
    topic = "comment-topic",
    selectorExpression = "created",
    consumerGroup = "notification-comment-created-consumer"
)
public class CommentCreatedNotificationConsumer implements RocketMQListener<CommentCreatedEvent> {
    
    private final NotificationApplicationService notificationService;
    private final NotificationPushService pushService;
    
    @Override
    public void onMessage(CommentCreatedEvent event) {
        // 通知文章作者
        if (!event.getPostOwnerId().equals(event.getCommentAuthorId())) {
            Notification notification = notificationService.createCommentNotification(
                event.getPostOwnerId(),
                event.getCommentAuthorId(),
                event.getPostId(),
                event.getCommentId(),
                event.getCommentContent()
            );
            pushService.push(event.getPostOwnerId(), notification);
        }
        
        // 如果是回复，通知被回复者
        if (event.getReplyToUserId() != null && 
            !event.getReplyToUserId().equals(event.getCommentAuthorId())) {
            Notification replyNotification = notificationService.createReplyNotification(
                event.getReplyToUserId(),
                event.getCommentAuthorId(),
                event.getCommentId(),
                event.getCommentContent()
            );
            pushService.push(event.getReplyToUserId(), replyNotification);
        }
    }
}

/**
 * 关注通知消费者
 */
@Component
@RocketMQMessageListener(
    topic = "user-topic",
    selectorExpression = "followed",
    consumerGroup = "notification-user-followed-consumer"
)
public class UserFollowedNotificationConsumer implements RocketMQListener<UserFollowedEvent> {
    
    private final NotificationApplicationService notificationService;
    private final NotificationPushService pushService;
    
    @Override
    public void onMessage(UserFollowedEvent event) {
        Notification notification = notificationService.createFollowNotification(
            event.getFollowingId(),
            event.getFollowerId()
        );
        pushService.push(event.getFollowingId(), notification);
    }
}
```


## 数据库表设计 (Notification_DB)

```sql
-- 通知表
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    recipient_id VARCHAR(36) NOT NULL,
    type SMALLINT NOT NULL,           -- 0:点赞 1:评论 2:关注 3:回复 4:系统
    actor_id VARCHAR(36),
    target_type VARCHAR(20),
    target_id BIGINT,
    content VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_aggregate ON notifications(recipient_id, type, target_type, target_id);

-- 全局公告表
CREATE TABLE global_announcements (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    type SMALLINT DEFAULT 0,          -- 0:普通 1:重要 2:紧急
    is_active BOOLEAN DEFAULT TRUE,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 小助手消息表
CREATE TABLE assistant_messages (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(200),
    content TEXT NOT NULL,
    type SMALLINT DEFAULT 0,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_assistant_messages_user ON assistant_messages(user_id, is_read, created_at DESC);
```

## 批量已读优化

```java
@Service
public class NotificationApplicationService {
    
    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 批量标记已读
     * 使用数据库批量更新，避免逐条更新
     */
    @Transactional
    public void markAllAsRead(String userId) {
        // 批量更新数据库
        notificationRepository.markAllAsRead(userId);
        
        // 清除未读计数缓存
        redisTemplate.delete(NotificationRedisKeys.unreadCount(userId));
    }
    
    /**
     * 获取未读数（优先从缓存获取）
     */
    public int getUnreadCount(String userId) {
        String key = NotificationRedisKeys.unreadCount(userId);
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            return (Integer) cached;
        }
        
        int count = notificationRepository.countUnread(userId);
        redisTemplate.opsForValue().set(key, count, Duration.ofMinutes(5));
        return count;
    }
}
```
