# zhicore-integration 模块

## 概述

zhicore-integration 模块包含所有用于跨服务通信的集成事件（Integration Events）。

集成事件用于通过 RocketMQ 在不同微服务之间传递消息，实现最终一致性和事件驱动架构。

## 与领域事件的区别

| 特性 | 领域事件 (Domain Event) | 集成事件 (Integration Event) |
|------|------------------------|----------------------------|
| **位置** | 各服务的 `domain.event` 包 | `zhicore-integration.messaging` 包 |
| **作用域** | 限界上下文内部 | 跨服务通信 |
| **传输方式** | 内存中传递 | RocketMQ 消息队列 |
| **字段** | 包含完整的领域信息 | 只包含跨服务必需的最小信息 |
| **序列化** | 不需要 | 必须支持 JSON 序列化 |

## 模块结构

```
zhicore-integration/
└── src/main/java/com/zhicore/integration/messaging/
    ├── IntegrationEvent.java              # 集成事件基类
    ├── user/                               # 用户相关事件
    │   ├── UserProfileUpdatedIntegrationEvent.java
    │   └── UserFollowedIntegrationEvent.java
    ├── comment/                            # 评论相关事件
    │   └── CommentCreatedIntegrationEvent.java
    └── post/                               # 文章相关事件
        ├── PostCreatedIntegrationEvent.java
        ├── PostPublishedIntegrationEvent.java
        ├── PostTagsUpdatedIntegrationEvent.java
        └── PostStatsUpdatedIntegrationEvent.java
```

## 集成事件列表

### 用户相关事件

#### UserProfileUpdatedIntegrationEvent
用户资料更新事件，当用户修改昵称、头像或个人简介时发布。

**字段**:
- `userId`: 用户ID
- `username`: 用户名
- `nickname`: 昵称
- `avatar`: 头像文件ID
- `bio`: 个人简介

**消费者**:
- zhicore-content: 更新文章作者信息

#### UserFollowedIntegrationEvent
用户关注事件，当用户关注另一个用户时发布。

**字段**:
- `followerId`: 关注者ID
- `followedId`: 被关注者ID

**消费者**:
- zhicore-notification: 创建关注通知

### 评论相关事件

#### CommentCreatedIntegrationEvent
评论创建事件，当用户创建评论时发布。

**字段**:
- `commentId`: 评论ID
- `postId`: 文章ID
- `userId`: 评论者ID
- `content`: 评论内容
- `parentId`: 父评论ID

**消费者**:
- zhicore-ranking: 更新文章热度排名
- zhicore-notification: 创建评论通知

### 文章相关事件

#### PostCreatedIntegrationEvent
文章创建事件，当用户创建文章时发布。

**字段**:
- `postId`: 文章ID
- `title`: 标题
- `excerpt`: 摘要
- `authorId`: 作者ID
- `authorName`: 作者名称
- `tagIds`: 标签ID列表
- `topicId`: 话题ID
- `topicName`: 话题名称
- `status`: 状态
- `publishedAt`: 发布时间
- `createdAt`: 创建时间

**消费者**:
- zhicore-search: 索引文章
- zhicore-ranking: 更新排行榜

#### PostPublishedIntegrationEvent
文章发布事件，当文章从草稿状态变为发布状态时发布。

**消费者**:
- zhicore-search: 更新索引
- zhicore-notification: 通知关注者

#### PostTagsUpdatedIntegrationEvent
文章标签更新事件，当文章的标签发生变化时发布。

**消费者**:
- zhicore-search: 更新索引

#### PostStatsUpdatedIntegrationEvent
文章统计更新事件，当文章的统计信息（浏览量、点赞数等）发生变化时发布。

**字段**:
- `postId`: 文章ID
- `viewCount`: 浏览量
- `likeCount`: 点赞数
- `favoriteCount`: 收藏数
- `commentCount`: 评论数

**消费者**:
- zhicore-content: 更新文章统计信息

## 使用指南

### 1. 添加依赖

在需要使用集成事件的服务中添加依赖：

```xml
<dependency>
    <groupId>com.zhicore</groupId>
    <artifactId>zhicore-integration</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. 发布集成事件

```java
import com.zhicore.integration.messaging.user.UserProfileUpdatedIntegrationEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Service
@RequiredArgsConstructor
public class UserEventPublisher {
    
    private final RocketMQTemplate rocketMQTemplate;
    
    public void publishUserProfileUpdated(User user) {
        UserProfileUpdatedIntegrationEvent event = new UserProfileUpdatedIntegrationEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getAvatar(),
            user.getBio(),
            user.getVersion()
        );
        
        rocketMQTemplate.syncSend(
            TopicConstants.USER_PROFILE_UPDATED,
            event
        );
    }
}
```

### 3. 消费集成事件

```java
import com.zhicore.integration.messaging.user.UserProfileUpdatedIntegrationEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

@Component
@RocketMQMessageListener(
    topic = TopicConstants.USER_PROFILE_UPDATED,
    consumerGroup = "content-user-profile-consumer"
)
public class UserProfileUpdatedConsumer implements RocketMQListener<UserProfileUpdatedIntegrationEvent> {
    
    @Override
    public void onMessage(UserProfileUpdatedIntegrationEvent event) {
        // 处理事件
        log.info("收到用户资料更新事件: userId={}, nickname={}", 
            event.getUserId(), event.getNickname());
        
        // 更新文章作者信息
        postService.updateAuthorInfo(
            event.getUserId(),
            event.getNickname(),
            event.getAvatar()
        );
    }
}
```

## 设计原则

### 1. 最小化载荷
集成事件只包含跨服务必需的最小信息，避免传输大量数据。

```java
// ✅ 正确 - 只包含必需字段
private final String excerpt;  // 摘要，而不是完整内容

// ❌ 错误 - 包含不必要的大字段
private final String content;  // 完整内容，可能很大
```

### 2. 版本控制
所有集成事件都包含 `schemaVersion` 字段，支持消息演进和向后兼容。

```java
super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
```

### 3. 幂等性支持
所有集成事件都包含 `eventId` 和 `aggregateVersion` 字段，支持幂等性处理。

```java
// 消费者可以使用 eventId 去重
if (consumedEventRepository.exists(event.getEventId())) {
    return;  // 已处理过，跳过
}
```

### 4. 不可变性
所有集成事件都是不可变的（final 字段），确保线程安全。

```java
private final Long userId;  // 不可变
```

## 迁移指南

从旧的事件类（`com.zhicore.api.event.*`）迁移到新的集成事件，请参考：

- [迁移指南](../zhicore-client/MIGRATION.md)
- [迁移状态](../zhicore-client/MIGRATION-STATUS.md)

## 相关文档

- [领域事件和集成事件设计文档](../.kiro/specs/zhicore-content-domain-event-unification/design.md)
- [需求文档](../.kiro/specs/zhicore-content-domain-event-unification/requirements.md)
- [RocketMQ 配置指南](../config/nacos/zhicore/OUTBOX-CONFIG-GUIDE.md)

---

**最后更新**: 2026-02-23  
**维护者**: ZhiCore 架构团队
