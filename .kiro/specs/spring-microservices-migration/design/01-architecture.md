# 整体架构设计

## 架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   Client Layer                                   │
│                        (Web Browser / Mobile App / API Client)                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway (Spring Cloud Gateway)                  │
│                    - 路由转发 - JWT认证 - 限流熔断 - 日志记录                      │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Business Services Layer                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │User Service │ │Post Service │ │Comment Svc  │ │Message Svc  │               │
│  │  (用户服务)  │ │  (文章服务)  │ │  (评论服务)  │ │  (消息服务)  │               │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │Notification │ │Search Svc   │ │Ranking Svc  │ │Upload Svc   │               │
│  │  (通知服务)  │ │  (搜索服务)  │ │ (排行榜服务) │ │  (上传服务)  │               │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘               │
│  ┌─────────────┐                                                                │
│  │Admin Svc    │                                                                │
│  │  (管理服务)  │                                                                │
│  └─────────────┘                                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            Infrastructure Layer                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │   Nacos     │ │  Sentinel   │ │  RocketMQ   │ │   Redis     │               │
│  │(注册+配置)   │ │ (熔断限流)   │ │  (消息队列)  │ │   (缓存)    │               │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                               │
│  │Elasticsearch│ │  SkyWalking │ │  Leaf       │                               │
│  │  (搜索引擎)  │ │ (链路追踪)   │ │ (分布式ID)  │                               │
│  └─────────────┘ └─────────────┘ └─────────────┘                               │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Data Storage Layer                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │  User_DB    │ │  Post_DB    │ │ Comment_DB  │ │ Message_DB  │               │
│  │(PostgreSQL) │ │(PostgreSQL) │ │(PostgreSQL) │ │(PostgreSQL) │               │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘               │
│  ┌─────────────┐ ┌─────────────┐                                               │
│  │Notification │ │ Content_DB  │                                               │
│  │    _DB      │ │(PostgreSQL) │                                               │
│  │(PostgreSQL) │ └─────────────┘                                               │
│  └─────────────┘                                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 服务间通信模式

> **设计说明：同步 vs 异步通信选择原则**
> 
> | 场景 | 通信方式 | 原因 |
> |------|---------|------|
> | 需要立即返回结果的查询 | 同步 (OpenFeign) | 用户等待响应 |
> | 获取用户信息、文章详情 | 同步 (OpenFeign) | 数据聚合需要 |
> | 事件通知（点赞、评论、关注） | 异步 (RocketMQ) | 不影响主流程 |
> | 搜索索引更新 | 异步 (RocketMQ) | 最终一致性即可 |
> | 排行榜更新 | 异步 (RocketMQ) | 允许短暂延迟 |
> | 统计数据同步 | 异步 (RocketMQ/CDC) | 最终一致性 |

### 同步调用 SLA 与容错配置

> **设计说明：同步调用默认配置**
> 
> 所有同步调用必须配置超时、重试和熔断策略，避免级联故障。

| 调用方 | 被调用方 | 依赖等级 | 超时(ms) | 重试次数 | 熔断阈值 | 降级策略 |
|--------|---------|---------|---------|---------|---------|---------|
| Post Service | User Service | **强依赖** | 3000 | 2 | 50% | 返回默认用户信息 |
| Comment Service | User Service | **强依赖** | 3000 | 2 | 50% | 返回默认用户信息 |
| Comment Service | Post Service | **强依赖** | 3000 | 2 | 50% | 抛出异常 |
| Notification Service | User Service | 弱依赖 | 2000 | 1 | 30% | 跳过用户信息填充 |
| Search Service | User Service | 弱依赖 | 2000 | 1 | 30% | 返回空用户信息 |
| Gateway | All Services | **强依赖** | 10000 | 0 | 50% | 返回 503 |

#### Sentinel 熔断配置示例

```java
@Configuration
public class SentinelConfig {
    
    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        
        // User Service 熔断规则
        DegradeRule userServiceRule = new DegradeRule("user-service")
            .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
            .setCount(0.5)                    // 错误率 50% 触发熔断
            .setTimeWindow(30)                // 熔断持续 30 秒
            .setMinRequestAmount(10)          // 最小请求数
            .setStatIntervalMs(10000);        // 统计窗口 10 秒
        rules.add(userServiceRule);
        
        DegradeRuleManager.loadRules(rules);
    }
}
```

#### OpenFeign 超时与重试配置

```yaml
# application.yml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 3000
            logger-level: BASIC
          user-service:
            connect-timeout: 2000
            read-timeout: 3000
          post-service:
            connect-timeout: 2000
            read-timeout: 3000

# Sentinel 配置
feign:
  sentinel:
    enabled: true
```

#### 降级处理示例

```java
@FeignClient(name = "user-service", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {
    
    @GetMapping("/api/v1/users/{userId}")
    UserDTO getUser(@PathVariable String userId);
    
    @PostMapping("/api/v1/users/batch")
    Map<String, UserBriefDTO> batchGetUsers(@RequestBody Set<String> userIds);
}

@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {
    
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public UserDTO getUser(String userId) {
                log.warn("User service fallback for userId={}, cause={}", userId, cause.getMessage());
                // 返回默认用户信息
                return UserDTO.defaultUser(userId);
            }
            
            @Override
            public Map<String, UserBriefDTO> batchGetUsers(Set<String> userIds) {
                log.warn("User service batch fallback, cause={}", cause.getMessage());
                // 返回空 Map，调用方需要处理
                return Collections.emptyMap();
            }
        };
    }
}
```

### 异步事件投递与补偿策略

> **设计说明：异步事件可靠性保证**
> 
> 所有异步事件必须配置重试、死信和补偿策略。

| Topic | 事件类型 | 是否顺序 | 幂等 Key | 最大重试 | 死信策略 | 补偿机制 |
|-------|---------|---------|---------|---------|---------|---------|
| ZhiCore-post-events | PostPublished | 否 | eventId | 3 | 转入死信队列 | 定时任务重试 |
| ZhiCore-post-events | PostLiked | 否 | postId:userId | 3 | 丢弃（幂等） | CDC 对账 |
| ZhiCore-user-events | UserFollowed | 否 | followerId:followingId | 3 | 丢弃（幂等） | CDC 对账 |
| ZhiCore-comment-events | CommentCreated | 否 | eventId | 3 | 转入死信队列 | 定时任务重试 |
| ZhiCore-message-events | MessageSent | **是** | messageId | 5 | 人工处理 | 消息补发 |

#### 死信队列配置

```java
@Configuration
public class RocketMQDeadLetterConfig {
    
    public static final String DLQ_TOPIC = "ZhiCore-events-dlq";
    
    /**
     * 死信消息处理
     */
    @RocketMQMessageListener(
        topic = DLQ_TOPIC,
        consumerGroup = "dlq-consumer-group"
    )
    @Component
    public class DeadLetterConsumer implements RocketMQListener<String> {
        
        @Autowired
        private DeadLetterRepository deadLetterRepository;
        
        @Override
        public void onMessage(String message) {
            // 记录死信消息，等待人工处理或定时任务重试
            DeadLetter deadLetter = new DeadLetter(message, LocalDateTime.now());
            deadLetterRepository.save(deadLetter);
            
            // 发送告警
            alertService.sendAlert("死信消息", message);
        }
    }
}
```

#### 定时补偿任务

```java
@Component
@Scheduled(cron = "0 */5 * * * ?")  // 每 5 分钟执行
public class EventCompensationTask {
    
    @Autowired
    private DeadLetterRepository deadLetterRepository;
    
    @Autowired
    private DomainEventPublisher eventPublisher;
    
    /**
     * 重试死信消息（最多重试 3 次）
     */
    public void retryDeadLetters() {
        List<DeadLetter> deadLetters = deadLetterRepository
            .findByRetryCountLessThanAndStatus(3, DeadLetterStatus.PENDING);
        
        for (DeadLetter dl : deadLetters) {
            try {
                DomainEvent event = parseEvent(dl.getMessage());
                eventPublisher.publish(event);
                dl.markAsProcessed();
            } catch (Exception e) {
                dl.incrementRetryCount();
                if (dl.getRetryCount() >= 3) {
                    dl.markAsFailed();
                    alertService.sendAlert("死信消息重试失败", dl.getMessage());
                }
            }
            deadLetterRepository.save(dl);
        }
    }
}
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              同步调用 (OpenFeign + Sentinel)                     │
│                                                                                  │
│   适用场景：                                                                      │
│   - 需要立即返回结果的查询操作                                                    │
│   - 数据聚合（如获取文章列表时需要用户信息）                                       │
│   - 权限验证（如检查用户是否有权限操作）                                          │
│                                                                                  │
│   User Service ◄──────────► Post Service ◄──────────► Comment Service           │
│        │                         │                          │                    │
│        │                         │                          │                    │
│        ▼                         ▼                          ▼                    │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         Sentinel (熔断降级)                              │   │
│   │              - 熔断器状态: CLOSED → OPEN → HALF_OPEN                     │   │
│   │              - 降级策略: 返回默认值 / 抛出异常                            │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              异步通信 (RocketMQ)                                 │
│                                                                                  │
│   适用场景：                                                                      │
│   - 事件通知（不影响主流程的操作）                                                │
│   - 最终一致性场景（搜索索引、排行榜、统计）                                       │
│   - 跨服务的数据同步                                                             │
│                                                                                  │
│   Post Service ──► PostPublishedEvent ──► Topic ──┬──► Search Service           │
│                                                   ├──► Ranking Service          │
│                                                   └──► Notification Service     │
│                                                                                  │
│   User Service ──► UserFollowedEvent ──► Topic ──┬──► Notification Service      │
│                                                  └──► Ranking Service           │
│                                                                                  │
│   Comment Service ──► CommentCreatedEvent ──► Topic ──┬──► Post Service         │
│                                                       └──► Notification Svc     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 服务依赖关系

> **设计说明：依赖等级定义**
> 
> - **强依赖（关键路径）**：被调用服务不可用时，调用方核心功能受损
> - **弱依赖（非关键路径）**：被调用服务不可用时，调用方可降级处理

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              服务依赖关系图                                       │
│                                                                                  │
│   ┌─────────────┐                                                               │
│   │User Service │◄═══════════════════════════════════════════════════════┐      │
│   │  (用户服务)  │ [强依赖]                                                │      │
│   └──────┬──────┘                                                        │      │
│          │ 同步调用（获取用户信息）[强依赖]                                 │      │
│          ▼                                                               │      │
│   ┌─────────────┐     同步调用      ┌─────────────┐                      │      │
│   │Post Service │◄═══════════════►│Comment Svc  │                      │      │
│   │  (文章服务)  │ [强依赖]          │  (评论服务)  │                      │      │
│   └──────┬──────┘                   └──────┬──────┘                      │      │
│          │                                  │                             │      │
│          │ 异步事件 [弱依赖]                  │ 异步事件 [弱依赖]            │      │
│          ▼                                  ▼                             │      │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                │      │
│   │Search Svc   │     │Ranking Svc  │     │Notification │════════════════┘      │
│   │  (搜索服务)  │     │ (排行榜服务) │     │  (通知服务)  │ [弱依赖]              │
│   │ [弱依赖]    │     │ [弱依赖]    │     │             │                       │
│   └─────────────┘     └─────────────┘     └─────────────┘                       │
│                                                                                  │
│   图例：                                                                         │
│   ═══════► 强依赖 (关键路径，需要熔断降级)                                        │
│   ───────► 弱依赖 (非关键路径，可降级跳过)                                        │
│   - - - -► 异步事件 (最终一致性)                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```


## 充血模型设计原则

本系统采用 DDD 充血模型（Rich Domain Model）设计，领域对象不仅包含数据，还包含业务行为和规则验证。

### 充血模型 vs 贫血模型

| 特性 | 贫血模型 | 充血模型 |
|------|---------|---------|
| 数据 | 只有 getter/setter | 有 getter，无 public setter |
| 行为 | 业务逻辑在 Service 层 | 业务逻辑在领域对象内 |
| 验证 | Service 层验证 | 领域对象自我验证 |
| 状态转换 | 外部直接修改 | 通过领域方法修改 |
| 不变量 | 难以保证 | 构造时和方法内保证 |

### 设计规范

**1. 构造函数私有化 + 工厂方法**
```java
// 私有构造函数
private Post(Long id, String ownerId, String title, String raw) {
    Assert.notNull(id, "文章ID不能为空");
    Assert.hasText(ownerId, "作者ID不能为空");
}

// 工厂方法 - 创建草稿
public static Post createDraft(Long id, String ownerId, String title, String content) {
    Post post = new Post(id, ownerId, title, content);
    post.html = ContentRenderer.render(content);
    return post;
}

// 工厂方法 - 从持久化恢复
public static Post reconstitute(...) { ... }
```

**2. 领域行为封装业务规则**
```java
public void publish() {
    if (this.status == PostStatus.PUBLISHED) {
        throw new DomainException("文章已经发布，不能重复发布");
    }
    this.status = PostStatus.PUBLISHED;
    this.publishedAt = LocalDateTime.now();
}
```

**3. 值对象不可变**
```java
public final class PostStats {
    private final int likeCount;
    private final int commentCount;
    
    public PostStats incrementLikes() {
        return new PostStats(likeCount + 1, commentCount, ...);
    }
}
```

**4. 应用层只做协调**
```java
@Service
public class PostApplicationService {
    public void publishPost(String userId, Long postId) {
        Post post = postRepository.findById(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException("无权操作");
        }
        post.publish();
        postRepository.update(post);
        eventPublisher.publish(new PostPublishedEvent(postId, userId));
    }
}
```

### 领域对象职责划分

| 层次 | 职责 | 示例 |
|------|------|------|
| 聚合根 | 业务规则、状态转换、不变量保护 | Post.publish(), Comment.delete() |
| 值对象 | 不可变数据、相等性比较 | PostStats, Address |
| 领域服务 | 跨聚合的业务逻辑 | TransferService |
| 应用服务 | 用例协调、事务管理、事件发布 | PostApplicationService |
| 领域事件 | 聚合间解耦通信 | PostPublishedEvent |
