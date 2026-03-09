# ZhiCore-message 模块与 im-system 集成架构设计

## 文档版本

| 版本 | 日期 | 作者 | 说明 |
|------|------|------|------|
| 1.0 | 2025-01-23 | System | 初始版本 - 定义 ZhiCore-message 在引入 im-system 后的新职责 |

---

## 背景

随着博客系统引入 **im-system**（通用即时通讯系统）作为底层聊天基础设施，**ZhiCore-message** 模块的职责需要重新定义。

### 问题

- **im-system** 提供完整的 IM 能力（单聊、群聊、消息持久化、在线状态等）
- **ZhiCore-message** 原本承担私信功能，与 im-system 存在功能重叠
- 需要明确两者的边界和职责分工

### 解决方案

**保留 ZhiCore-message 模块，但重新定位为"博客消息业务编排层"**，而不是完整的 IM 系统。

---

## 架构定位

### 系统分层

```
┌─────────────────────────────────────────────────────────────┐
│                      ZhiCore 微服务系统                          │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  ZhiCore-user   │  │  ZhiCore-post   │  │ ZhiCore-comment │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              ZhiCore-message                            │   │
│  │  (博客消息业务编排层 / IM 适配层)                     │   │
│  │                                                      │   │
│  │  ✓ 私信业务编排 (调用 im-system)                    │   │
│  │  ✓ 会话聚合与展示                                    │   │
│  │  ✓ 通知摘要聚合（读取 notification 服务）            │   │
│  │  ✓ 博客特定的消息规则                                │   │
│  │  ✓ 消息权限控制                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
└──────────────────────────┼───────────────────────────────────┘
                           │ Feign Client 调用
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    im-system                                 │
│              (通用 IM 基础设施服务)                           │
│                                                              │
│  ✓ 单聊/群聊消息传输                                         │
│  ✓ 消息持久化存储                                            │
│  ✓ 在线状态管理                                              │
│  ✓ 消息推送 (WebSocket/TCP)                                 │
│  ✓ 消息同步与离线消息                                        │
│  ✓ 会话管理                                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 职责划分

### ZhiCore-message 的新职责

#### 1. 消息中心聚合（读取 notification 服务）

`ZhiCore-message` 不再负责系统通知的生成和持久化，这部分由 `ZhiCore-notification` 独立承担。`ZhiCore-message` 只负责聚合展示：

| 能力 | 归属服务 | 说明 |
|------|---------|------|
| 系统通知生成/存储 | ZhiCore-notification | 消费点赞、评论、关注等事件，生成通知读模型 |
| 私信发送/会话管理 | ZhiCore-message + im-system | message 编排业务规则，im-system 负责即时通讯基础设施 |
| 消息中心聚合 | ZhiCore-message | 聚合 notification 摘要与私信会话列表，对外提供统一入口 |

这意味着 `message` 与 `notification` 的边界是：
- `notification` 负责通知领域状态
- `message` 负责消息中心体验与私信业务编排

#### 2. 私信业务编排（调用 im-system）

作为博客业务与 im-system 之间的适配层：

```java
@Service
public class PrivateMessageService {
    @Autowired
    private ImSystemClient imSystemClient;  // im-system 的 Feign 客户端
    
    @Autowired
    private UserBlockService userBlockService;
    
    @Autowired
    private ContentFilterService contentFilterService;
    
    /**
     * 发送私信（博客业务层）
     */
    public void sendPrivateMessage(SendMessageRequest request) {
        String fromUserId = request.getFromUserId();
        String toUserId = request.getToUserId();
        String content = request.getContent();
        String requestId = request.getRequestId();
        
        // 1. 博客业务校验
        validateMessagePermission(fromUserId, toUserId);
        
        // 2. 内容过滤（敏感词检测）
        String filteredContent = contentFilterService.filter(content);
        
        // 3. 调用 im-system 发送消息
        ImMessageRequest imRequest = ImMessageRequest.builder()
            .requestId(requestId)
            .fromUserId(fromUserId)
            .toUserId(toUserId)
            .content(filteredContent)
            .messageType(MessageType.TEXT)
            .build();
            
        ApiResponse<Long> response = imSystemClient.sendMessage(imRequest);
        
        if (!response.isSuccess()) {
            throw new BusinessException("消息发送失败");
        }
        
        // 4. 落库消息发送结果，统计和审计走异步补偿
        messageRepository.save(MessageRecord.sent(
            requestId, fromUserId, toUserId, response.getData()
        ));
        messageOutboxPublisher.publishStatisticsSync(requestId, fromUserId, toUserId);
    }
    
    /**
     * 博客业务权限校验
     */
    private void validateMessagePermission(String fromUserId, String toUserId) {
        // 检查是否被拉黑
        if (userBlockService.isBlocked(fromUserId, toUserId)) {
            throw new BusinessException("无法给该用户发送消息");
        }
        
        // 检查是否需要互相关注才能私信（可配置）
        if (requireMutualFollow && !friendService.areMutualFollowers(fromUserId, toUserId)) {
            throw new BusinessException("只能给互相关注的用户发私信");
        }
        
        // 检查发送频率限制（防刷）
        if (isRateLimited(fromUserId)) {
            throw new BusinessException("发送消息过于频繁，请稍后再试");
        }
    }
}
```

#### 3. 消息聚合与展示

统一的消息中心，聚合多种消息类型：

```java
@Service
public class MessageCenterService {
    @Autowired
    private SystemNotificationRepository notificationRepository;
    
    @Autowired
    private ImSystemClient imSystemClient;
    
    /**
     * 获取消息中心数据
     */
    public MessageCenterVO getMessageCenter(String userId) {
        // 1. 获取系统通知（本地）
        List<SystemNotificationVO> systemNotifications = 
            notificationRepository.findByUserId(userId, PageRequest.of(0, 20));
        
        // 2. 获取私信会话列表（im-system）
        ApiResponse<List<ConversationVO>> conversationsResponse = 
            imSystemClient.getConversations(userId);
        List<ConversationVO> conversations = conversationsResponse.getData();
        
        // 3. 计算未读数
        int systemUnreadCount = notificationRepository.countUnread(userId);
        int chatUnreadCount = conversations.stream()
            .mapToInt(ConversationVO::getUnreadCount)
            .sum();
        
        // 4. 聚合返回
        return MessageCenterVO.builder()
            .systemNotifications(systemNotifications)
            .systemUnreadCount(systemUnreadCount)
            .conversations(conversations)
            .chatUnreadCount(chatUnreadCount)
            .totalUnreadCount(systemUnreadCount + chatUnreadCount)
            .build();
    }
}
```

#### 4. 博客特定的消息规则

实现博客业务特有的消息功能：

```java
@Service
public class BulkMessageService {
    /**
     * VIP 用户群发消息
     */
    public void sendBulkMessage(String fromUserId, List<String> toUserIds, String content) {
        // 1. 检查权限（只有 VIP 用户可以群发）
        if (!userService.isVip(fromUserId)) {
            throw new BusinessException("只有VIP用户可以群发消息");
        }
        
        // 2. 检查群发数量限制
        if (toUserIds.size() > MAX_BULK_SIZE) {
            throw new BusinessException("单次群发不能超过" + MAX_BULK_SIZE + "人");
        }
        
        // 3. 创建批量发送任务，异步并发投递
        BulkMessageTask task = bulkMessageTaskRepository.create(fromUserId, toUserIds, content);
        bulkMessageDispatcher.dispatch(task.getTaskId());
    }
}
```

批量发送的推荐策略：
- 主流程只负责校验和创建任务，不在 HTTP 请求里串行调用 IM
- Worker 池按批次并发发送，并对失败项进入重试队列
- 对外返回任务 ID、成功数、失败数和最终完成状态

### im-system 的职责

im-system 作为通用 IM 基础设施，提供：

| 功能 | 说明 |
|------|------|
| 消息传输 | WebSocket/TCP 长连接，实时消息推送 |
| 消息持久化 | 消息历史存储、分库分表 |
| 会话管理 | 会话列表、未读数统计 |
| 在线状态 | 用户在线/离线状态管理 |
| 消息同步 | 多端消息同步、离线消息 |
| 消息撤回 | 消息撤回功能 |
| 群聊功能 | 群组管理、群消息分发 |

**im-system 不关心博客业务逻辑**，只提供纯粹的 IM 能力。

---

## 技术实现

### 依赖关系

```xml
<!-- ZhiCore-message/pom.xml -->
<dependencies>
    <!-- 博客公共模块 -->
    <dependency>
        <groupId>com.ZhiCore</groupId>
        <artifactId>ZhiCore-common</artifactId>
    </dependency>
    
    <dependency>
        <groupId>com.ZhiCore</groupId>
        <artifactId>zhicore-client</artifactId>
    </dependency>
    
    <!-- im-system 客户端（新增） -->
    <dependency>
        <groupId>org.test</groupId>
        <artifactId>im-system-client</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- OpenFeign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>
```

### Feign Client 定义

在**共享契约模块**中定义 im-system 的 Feign 客户端：

```java
// shared-client/client/ImSystemClient.java
@FeignClient(
    name = "im-system", 
    url = "${im-system.url}",
    primary = false
)
public interface ImSystemClient {
    
    /**
     * 发送消息
     */
    @PostMapping("/api/v1/messages")
    ApiResponse<Long> sendMessage(@RequestBody ImMessageRequest request);
    
    /**
     * 获取会话列表
     */
    @GetMapping("/api/v1/conversations")
    ApiResponse<List<ConversationVO>> getConversations(@RequestParam("userId") String userId);
    
    /**
     * 获取会话消息历史
     */
    @GetMapping("/api/v1/conversations/{conversationId}/messages")
    ApiResponse<List<MessageVO>> getMessages(
        @PathVariable("conversationId") String conversationId,
        @RequestParam("page") int page,
        @RequestParam("size") int size
    );
    
    /**
     * 标记消息已读
     */
    @PutMapping("/api/v1/messages/{messageId}/read")
    ApiResponse<Void> markAsRead(@PathVariable("messageId") Long messageId);
}
```

### 降级策略

```java
// ZhiCore-message/infrastructure/feign/ImSystemFallbackFactory.java
@Component
public class ImSystemFallbackFactory implements FallbackFactory<ImSystemClient> {
    
    @Override
    public ImSystemClient create(Throwable cause) {
        return new ImSystemClient() {
            @Override
            public ApiResponse<Long> sendMessage(ImMessageRequest request) {
                log.error("IM系统调用失败，消息发送失败", cause);
                return ApiResponse.error(ResultCode.SERVICE_UNAVAILABLE, "消息服务暂时不可用");
            }
            
            @Override
            public ApiResponse<List<ConversationVO>> getConversations(String userId) {
                log.error("IM系统调用失败，获取会话列表失败", cause);
                return ApiResponse.success(Collections.emptyList());
            }
            
            // ... 其他方法的降级实现
        };
    }
}
```

### 配置

```yaml
# ZhiCore-message/application.yml
im-system:
  url: http://im-system:8080  # im-system 服务地址
  
feign:
  client:
    config:
      im-system:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: basic
```

---

## API 设计

### ZhiCore-message 对外提供的 API

#### 1. 系统通知 API

```java
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    
    /**
     * 获取系统通知列表
     */
    @GetMapping
    public ApiResponse<PageResult<SystemNotificationVO>> getNotifications(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // 返回系统通知（点赞、评论、关注等）
    }
    
    /**
     * 标记通知已读
     */
    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long notificationId) {
        // 标记系统通知已读
    }
    
    /**
     * 获取未读通知数
     */
    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount() {
        // 返回未读系统通知数
    }
}
```

#### 2. 私信 API（适配层）

```java
@RestController
@RequestMapping("/api/v1/messages")
public class PrivateMessageController {
    
    /**
     * 发送私信
     */
    @PostMapping
    public ApiResponse<Long> sendMessage(@RequestBody SendMessageRequest request) {
        // 博客业务校验 + 调用 im-system
    }
    
    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> getConversations() {
        // 直接转发到 im-system
    }
    
    /**
     * 获取会话消息历史
     */
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<List<MessageVO>> getConversationMessages(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // 直接转发到 im-system
    }
}
```

#### 3. 消息中心 API

```java
@RestController
@RequestMapping("/api/v1/message-center")
public class MessageCenterController {
    
    /**
     * 获取消息中心（聚合视图）
     */
    @GetMapping
    public ApiResponse<MessageCenterVO> getMessageCenter() {
        // 聚合系统通知 + 私信会话
    }
    
    /**
     * 获取总未读数
     */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> getTotalUnreadCount() {
        // 系统通知未读数 + 私信未读数
    }
}
```

---

## 数据流转

### 发送私信流程

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ 前端    │────►│ ZhiCore-message │────►│  im-system   │────►│  PostgreSQL  │
│         │     │              │     │              │     │              │
│         │     │ 1. 业务校验  │     │ 3. 消息持久化│     │ 4. 存储消息  │
│         │     │ 2. 内容过滤  │     │ 4. 推送消息  │     │              │
│         │◄────│              │◄────│              │     │              │
│         │     │ 6. 返回结果  │     │ 5. 返回ID    │     │              │
└─────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                       │
                       │ 7. 记录统计
                       ▼
                ┌──────────────┐
                │  Redis       │
                │  (统计数据)  │
                └──────────────┘
```

### 系统通知流程

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ ZhiCore-post    │────►│  RocketMQ    │────►│ ZhiCore-message │
│              │     │              │     │              │
│ 发布事件     │     │ PostLikedEvent│    │ 1. 消费事件  │
│              │     │              │     │ 2. 创建通知  │
└──────────────┘     └──────────────┘     │ 3. 存储DB    │
                                           │ 4. 推送前端  │
                                           └──────────────┘
```

---

## 迁移策略

### 阶段 1：准备阶段

1. 部署 im-system 服务
2. 在共享契约模块中添加 ImSystemClient
3. 在 ZhiCore-message 中添加 im-system-client 依赖

### 阶段 2：双写阶段

1. 新私信同时写入 ZhiCore-message 和 im-system
2. 读取优先从 im-system 读取，失败则降级到 ZhiCore-message
3. 验证数据一致性

### 阶段 3：迁移阶段

1. 历史私信数据迁移到 im-system
2. 验证数据完整性
3. 切换读取路径到 im-system

### 阶段 4：清理阶段

1. 移除 ZhiCore-message 中的私信存储表
2. 移除旧的私信相关代码
3. 保留系统通知功能

---

## 优势总结

### 1. 职责清晰

- **ZhiCore-message**：博客业务消息编排
- **im-system**：通用 IM 基础设施

### 2. 解耦

- im-system 不耦合博客业务
- 可以被其他系统复用

### 3. 灵活性

- 可以独立升级 im-system
- 可以替换 IM 实现而不影响博客业务

### 4. 可维护性

- 系统通知和私信分离存储
- 各自独立演进

---

## 注意事项

### 1. 用户ID映射

- ZhiCore 和 im-system 可能使用不同的用户ID体系
- 需要在 ZhiCore-message 中做映射转换

### 2. 消息格式

- ZhiCore 的消息格式可能与 im-system 不完全一致
- 需要在适配层做格式转换

### 3. 权限控制

- im-system 提供基础的消息传输
- 博客特定的权限控制（如拉黑、关注限制）在 ZhiCore-message 中实现

### 4. 降级策略

- im-system 不可用时，私信功能降级
- 系统通知不受影响

---

## 相关文档

- [im-system 架构文档](../../../im-system/docs/architecture-overview.md)
- [共享契约模块说明](./blog-api-module-purpose.md)
- [DDD 架构总览](../ddd/overview.md)
