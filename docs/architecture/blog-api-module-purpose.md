# 共享契约模块的作用

## 概述

共享契约模块是一个**共享 API/Client 模块**，用于在微服务之间共享接口定义、DTO 和事件。它不是一个独立运行的服务，而是一个被其他服务依赖的 Maven 模块。

## 为什么需要共享契约模块？

在微服务架构中，服务之间需要相互调用。共享契约模块解决了以下问题：

### 1. 服务间调用（Feign Client）

当一个服务需要调用另一个服务时，需要定义 Feign 客户端接口。这些接口定义在共享契约模块中，供所有服务共享。

**示例**：`ZhiCore-user` 需要调用 ID 生成服务生成分布式 ID

```java
// 共享契约模块/client/LeafServiceClient.java
@FeignClient(name = "zhicore-id-generator", path = "/api/v1/id", primary = false)
public interface LeafServiceClient {
    @GetMapping("/segment/{bizTag}")
    ApiResponse<Long> getSegmentId(@PathVariable("bizTag") String bizTag);
}

// ZhiCore-user 服务中使用
@Service
public class CheckInApplicationService {
    @Autowired
    private LeafServiceClient leafServiceClient;  // 来自共享契约模块
    
    public void checkIn(String userId) {
        // 调用 Leaf 服务生成签到记录 ID
        Long checkInId = leafServiceClient.getSegmentId("check_in").getData();
        // ...
    }
}
```

### 2. 数据传输对象（DTO）

服务之间传递数据时需要统一的数据结构。共享契约模块定义了跨服务共享的 DTO。

**示例**：`ZhiCore-comment` 需要获取用户信息，`ZhiCore-notification` 也需要获取用户信息

```java
// shared-client/dto/user/UserSimpleDTO.java
public class UserSimpleDTO {
    private String userId;
    private String userName;
    private String avatar;
}

// ZhiCore-comment 服务使用
@Service
public class CommentApplicationService {
    @Autowired
    private UserServiceClient userServiceClient;  // 来自共享契约模块
    
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

微服务之间通过消息队列（RocketMQ）进行异步通信。共享契约模块定义了统一的事件格式。

**示例**：用户关注事件

```java
// shared-client/event/user/UserFollowedEvent.java
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

## 共享契约模块结构

```
shared-client/
├── client/                          # Feign 客户端接口
│   ├── LeafServiceClient.java      # Leaf ID 生成服务客户端
│   ├── UserServiceClient.java      # 用户服务客户端
│   ├── PostServiceClient.java      # 文章服务客户端
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

## ZhiCore-user 为什么依赖共享契约模块？

`ZhiCore-user` 服务依赖共享契约模块的原因：

### 1. 调用 Leaf 服务生成 ID

```java
// CheckInApplicationService.java
@Autowired
private LeafServiceClient leafServiceClient;  // 需要共享契约模块

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

## 为什么需要启用 Feign 客户端扫描？

共享契约模块中的 Feign 接口不会自动被启用，调用方需要通过 `@EnableFeignClients` 显式注册客户端包。

```java
@EnableFeignClients(basePackageClasses = {
    LeafServiceClient.class,
    UserServiceClient.class
})
@SpringBootApplication(scanBasePackages = {
    "com.zhicore.user",
    "com.zhicore.common"
})
```

**原因**：共享契约模块只提供接口契约，不应该依赖共享模块内的 `@Component` Bean。业务专属 `FallbackFactory` 应放在调用方服务本地。

如果不启用客户端扫描：
1. Spring 不会创建共享 Feign Client 代理
2. 依赖 `LeafServiceClient` 的服务（如 `CheckInApplicationService`）无法注入客户端
3. 服务启动时会出现 `NoSuchBeanDefinitionException`

## 依赖关系图

```
┌─────────────────────────────────────────────────────────────┐
│                      共享契约模块 (共享模块)                      │
│  - Feign Client 接口                                         │
│  - DTO 定义                                                  │
│  - 领域事件                                                  │
│  - 共享契约（不放业务 fallback 实现）                        │
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
- 接口变更在共享契约模块中统一管理
- 所有依赖服务同步更新

### 4. 解耦
- 服务只依赖接口，不依赖实现
- 符合依赖倒置原则

## 注意事项

### 1. 共享契约模块不应包含业务逻辑
- 只包含接口定义、DTO、事件
- 不包含具体实现

### 2. 业务服务的 Fallback Factory 应在各自服务中
- 共享契约模块只定义接口、DTO、事件，不放 `@Component` 形式的业务 fallback
- `UserServiceFallbackFactory` 放在 `ZhiCore-comment` 等调用方服务中
- `PostServiceFallbackFactory` 放在具体调用方服务中，避免共享模块强绑定 Spring Bean

### 3. 需要启用 Feign 客户端扫描
- 所有使用共享契约模块的服务都应通过 `@EnableFeignClients` 显式扫描客户端接口
- 不建议依赖 `scanBasePackages` 去兜底发现共享模块中的 Spring Bean

## 总结

共享契约模块是微服务架构中的**契约层**：
- 定义服务间的调用接口（Feign Client）
- 定义数据传输格式（DTO）
- 定义事件通信协议（Domain Events）

所有业务服务都依赖它来实现服务间通信，这是微服务架构的标准实践。
