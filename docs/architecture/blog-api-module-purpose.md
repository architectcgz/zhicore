# ZhiCore-api 模块的作用

## 概述

`ZhiCore-api` 是一个**共享 API 模块**，用于在微服务之间共享接口定义、DTO 和事件。它不是一个独立运行的服务，而是一个被其他服务依赖的 Maven 模块。

## 为什么需要 ZhiCore-api？

在微服务架构中，服务之间需要相互调用。`ZhiCore-api` 模块解决了以下问题：

### 1. 服务间调用（Feign Client）

当一个服务需要调用另一个服务时，需要定义 Feign 客户端接口。这些接口定义在 `ZhiCore-api` 中，供所有服务共享。

**示例**：`ZhiCore-user` 需要调用 `ZhiCore-leaf` 服务生成分布式 ID

```java
// ZhiCore-api/client/LeafServiceClient.java
@FeignClient(name = "ZhiCore-leaf", fallbackFactory = LeafServiceFallbackFactory.class)
public interface LeafServiceClient {
    @GetMapping("/api/v1/leaf/segment/{bizTag}")
    ApiResponse<Long> getSegmentId(@PathVariable("bizTag") String bizTag);
}

// ZhiCore-user 服务中使用
@Service
public class CheckInApplicationService {
    @Autowired
    private LeafServiceClient leafServiceClient;  // 来自 ZhiCore-api
    
    public void checkIn(String userId) {
        // 调用 Leaf 服务生成签到记录 ID
        Long checkInId = leafServiceClient.getSegmentId("check_in").getData();
        // ...
    }
}
```

### 2. 数据传输对象（DTO）

服务之间传递数据时需要统一的数据结构。`ZhiCore-api` 定义了跨服务共享的 DTO。

**示例**：`ZhiCore-comment` 需要获取用户信息，`ZhiCore-notification` 也需要获取用户信息

```java
// ZhiCore-api/dto/user/UserSimpleDTO.java
public class UserSimpleDTO {
    private String userId;
    private String userName;
    private String avatar;
}

// ZhiCore-comment 服务使用
@Service
public class CommentApplicationService {
    @Autowired
    private UserServiceClient userServiceClient;  // 来自 ZhiCore-api
    
    public CommentVO getComment(String commentId) {
        Comment comment = commentRepository.findById(commentId);
        // 获取评论作者信息
        UserSimpleDTO author = userServiceClient.getUserSimple(comment.getUserId()).getData();
        return CommentVO.builder()
            .commentId(comment.getId())
            .content(comment.getContent())
            .authorName(author.getUserName())  // 使用共享的 DTO
            .build();
    }
}
```

### 3. 领域事件（Domain Events）

微服务之间通过消息队列（RocketMQ）进行异步通信。`ZhiCore-api` 定义了统一的事件格式。

**示例**：用户关注事件

```java
// ZhiCore-api/event/user/UserFollowedEvent.java
public class UserFollowedEvent extends DomainEvent {
    private String followerId;    // 关注者 ID
    private String followedUserId; // 被关注者 ID
}

// ZhiCore-user 服务发布事件
@Service
public class FollowApplicationService {
    @Autowired
    private UserEventPublisher eventPublisher;
    
    public void follow(String followerId, String followedUserId) {
        // 执行关注逻辑
        userFollowRepository.save(new UserFollow(followerId, followedUserId));
        
        // 发布事件
        eventPublisher.publish(new UserFollowedEvent(followerId, followedUserId));
    }
}

// ZhiCore-notification 服务消费事件
@Component
public class UserFollowedNotificationConsumer {
    @RocketMQMessageListener(topic = "user-events", consumerGroup = "notification-consumer")
    public void onMessage(UserFollowedEvent event) {  // 使用共享的事件类
        // 创建通知：XXX 关注了你
        notificationService.createFollowNotification(event.getFollowedUserId(), event.getFollowerId());
    }
}
```

## ZhiCore-api 模块结构

```
ZhiCore-api/
├── client/                          # Feign 客户端接口
│   ├── LeafServiceClient.java      # Leaf ID 生成服务客户端
│   ├── UserServiceClient.java      # 用户服务客户端
│   ├── PostServiceClient.java      # 文章服务客户端
│   └── fallback/                    # 降级处理
│       └── LeafServiceFallbackFactory.java  # 基础设施服务的降级
├── dto/                             # 数据传输对象
│   ├── user/
│   │   ├── UserDTO.java            # 完整用户信息
│   │   └── UserSimpleDTO.java      # 简化用户信息
│   └── post/
│       ├── PostDTO.java            # 完整文章信息
│       └── PostDetailDTO.java      # 文章详情
└── event/                           # 领域事件
    ├── DomainEvent.java            # 事件基类
    ├── user/
    │   ├── UserRegisteredEvent.java
    │   └── UserFollowedEvent.java
    ├── post/
    │   ├── PostPublishedEvent.java
    │   ├── PostLikedEvent.java
    │   └── PostViewedEvent.java
    └── comment/
        └── CommentCreatedEvent.java
```

## ZhiCore-user 为什么依赖 ZhiCore-api？

`ZhiCore-user` 服务依赖 `ZhiCore-api` 的原因：

### 1. 调用 Leaf 服务生成 ID

```java
// CheckInApplicationService.java
@Autowired
private LeafServiceClient leafServiceClient;  // 需要 ZhiCore-api

public void checkIn(String userId) {
    Long checkInId = leafServiceClient.getSegmentId("check_in").getData();
    // ...
}
```

### 2. 提供用户信息给其他服务

```java
// UserController.java
@GetMapping("/{userId}/simple")
public ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable String userId) {
    // 返回 UserSimpleDTO，供其他服务调用
    return ApiResponse.success(userAssembler.toSimpleDTO(user));
}
```

### 3. 发布领域事件

```java
// UserEventPublisher.java
public void publishUserRegistered(User user) {
    UserRegisteredEvent event = new UserRegisteredEvent(user.getId(), user.getUserName());
    domainEventPublisher.publish(TopicConstants.USER_TOPIC, event);
}

public void publishUserFollowed(String followerId, String followedUserId) {
    UserFollowedEvent event = new UserFollowedEvent(followerId, followedUserId);
    domainEventPublisher.publish(TopicConstants.USER_TOPIC, event);
}
```

## 为什么需要扫描 com.zhicore.api 包？

这就是之前遇到的问题！

```java
@SpringBootApplication(scanBasePackages = {
    "com.ZhiCore.user",      // 扫描用户服务自己的代码
    "com.zhicore.common",    // 扫描公共模块
    "com.zhicore.api"        // 扫描 API 模块 ← 必须添加！
})
```

**原因**：`ZhiCore-api` 中的 `LeafServiceFallbackFactory` 是一个 `@Component`，需要被 Spring 扫描并创建 Bean。

```java
// ZhiCore-api/client/fallback/LeafServiceFallbackFactory.java
@Component  // ← 这个注解需要被 Spring 扫描
public class LeafServiceFallbackFactory implements FallbackFactory<LeafServiceClient> {
    @Override
    public LeafServiceClient create(Throwable cause) {
        return new LeafServiceClient() {
            @Override
            public ApiResponse<Long> getSegmentId(String bizTag) {
                log.error("Leaf service call failed: {}", cause.getMessage());
                return ApiResponse.error(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
```

如果不扫描 `com.zhicore.api` 包：
1. Spring 找不到 `LeafServiceFallbackFactory` 这个 Bean
2. Feign 创建 `LeafServiceClient` 时失败
3. 依赖 `LeafServiceClient` 的服务（如 `CheckInApplicationService`）无法启动

## 依赖关系图

```
┌─────────────────────────────────────────────────────────────┐
│                      ZhiCore-api (共享模块)                      │
│  - Feign Client 接口                                         │
│  - DTO 定义                                                  │
│  - 领域事件                                                  │
│  - Fallback Factory (基础设施服务)                           │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ 依赖
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼────────┐  ┌──────▼───────┐  ┌───────▼────────┐
│  ZhiCore-user     │  │ ZhiCore-comment │  │  ZhiCore-post     │
│  - 调用 Leaf   │  │ - 调用 User  │  │  - 调用 Leaf   │
│  - 发布事件    │  │ - 调用 Post  │  │  - 发布事件    │
│  - 提供 DTO    │  │ - 发布事件   │  │  - 提供 DTO    │
└────────────────┘  └──────────────┘  └────────────────┘
```

## 架构优势

### 1. 接口统一
- 所有服务使用相同的 Feign 客户端接口
- 避免重复定义

### 2. 类型安全
- DTO 在编译期检查
- 避免运行时类型错误

### 3. 版本管理
- 接口变更在 `ZhiCore-api` 中统一管理
- 所有依赖服务同步更新

### 4. 解耦
- 服务只依赖接口，不依赖实现
- 符合依赖倒置原则

## 注意事项

### 1. ZhiCore-api 不应包含业务逻辑
- 只包含接口定义、DTO、事件
- 不包含具体实现

### 2. 业务服务的 Fallback Factory 应在各自服务中
- `LeafServiceFallbackFactory` 在 `ZhiCore-api` 中（基础设施服务，所有服务共用）
- `UserServiceFallbackFactory` 在 `ZhiCore-comment` 中（业务服务，降级策略可能不同）
- `PostServiceFallbackFactory` 在 `ZhiCore-comment` 中（业务服务，降级策略可能不同）

### 3. 必须扫描 com.zhicore.api 包
- 所有使用 `ZhiCore-api` 的服务都必须在 `@SpringBootApplication` 中添加 `"com.zhicore.api"` 到 `scanBasePackages`
- 否则 `@Component` 注解的类（如 `LeafServiceFallbackFactory`）无法被 Spring 发现

## 总结

`ZhiCore-api` 是微服务架构中的**契约层**：
- 定义服务间的调用接口（Feign Client）
- 定义数据传输格式（DTO）
- 定义事件通信协议（Domain Events）

所有业务服务都依赖它来实现服务间通信，这是微服务架构的标准实践。
