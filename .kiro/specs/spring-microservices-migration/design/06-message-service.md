# Message Service 设计

## DDD 分层结构

```
message-service/
├── src/main/java/com/ZhiCore/message/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   ├── MessageController.java
│   │   │   └── ConversationController.java
│   │   ├── websocket/
│   │   │   └── MessageWebSocketHandler.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   ├── MessageApplicationService.java
│   │   │   └── ConversationApplicationService.java
│   │   └── event/
│   │       └── MessageEventPublisher.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Message.java           # 聚合根
│   │   │   ├── Conversation.java      # 聚合根
│   │   │   └── MessageType.java
│   │   ├── repository/
│   │   │   ├── MessageRepository.java
│   │   │   └── ConversationRepository.java
│   │   ├── service/
│   │   │   └── MessageDomainService.java
│   │   └── event/
│   │       └── MessageSentEvent.java
│   └── infrastructure/
│       ├── repository/
│       │   └── mapper/
│       ├── push/
│       │   ├── PushService.java
│       │   ├── WebSocketPushService.java
│       │   └── TcpPushService.java
│       └── feign/
│           └── UserServiceClient.java
```


## Message 聚合根（充血模型）

> **设计说明：消息顺序性保证 (CP-MSG-01)**
> 
> 消息服务需要保证同一会话内消息的顺序性，实现方式：
> 1. 数据库层面：使用 `conversation_id + created_at` 索引保证查询顺序
> 2. 消息队列层面：使用 RocketMQ 顺序消息，以 `conversationId` 作为 shardingKey
> 3. 推送层面：WebSocket 推送按消息创建时间排序

```java
public class Message {
    private final Long id;
    private final Long conversationId;
    private final String senderId;
    private final String receiverId;
    private final LocalDateTime createdAt;
    
    private MessageType type;
    private String content;
    private String mediaUrl;
    private boolean isRead;
    private LocalDateTime readAt;
    private MessageStatus status;
    
    // 私有构造函数
    private Message(Long id, Long conversationId, String senderId, 
                    String receiverId, MessageType type, String content) {
        Assert.notNull(id, "消息ID不能为空");
        Assert.notNull(conversationId, "会话ID不能为空");
        Assert.hasText(senderId, "发送者ID不能为空");
        Assert.hasText(receiverId, "接收者ID不能为空");
        
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.type = type;
        this.content = content;
        this.isRead = false;
        this.status = MessageStatus.SENT;
        this.createdAt = LocalDateTime.now();
    }
    
    // 工厂方法 - 创建文本消息
    public static Message createText(Long id, Long conversationId, 
                                     String senderId, String receiverId, String content) {
        validateTextContent(content);
        return new Message(id, conversationId, senderId, receiverId, 
                          MessageType.TEXT, content);
    }
    
    // 工厂方法 - 创建图片消息
    public static Message createImage(Long id, Long conversationId,
                                      String senderId, String receiverId, String imageUrl) {
        Assert.hasText(imageUrl, "图片URL不能为空");
        Message message = new Message(id, conversationId, senderId, receiverId,
                                     MessageType.IMAGE, null);
        message.mediaUrl = imageUrl;
        return message;
    }
    
    // 工厂方法 - 创建文件消息
    public static Message createFile(Long id, Long conversationId,
                                     String senderId, String receiverId, 
                                     String fileName, String fileUrl) {
        Assert.hasText(fileUrl, "文件URL不能为空");
        Message message = new Message(id, conversationId, senderId, receiverId,
                                     MessageType.FILE, fileName);
        message.mediaUrl = fileUrl;
        return message;
    }
    
    // 领域行为 - 标记已读
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
    
    // 领域行为 - 撤回消息
    public void recall(String operatorId) {
        if (!this.senderId.equals(operatorId)) {
            throw new DomainException("只能撤回自己发送的消息");
        }
        if (this.status == MessageStatus.RECALLED) {
            throw new DomainException("消息已经撤回");
        }
        // 检查是否在可撤回时间内（2分钟）
        if (this.createdAt.plusMinutes(2).isBefore(LocalDateTime.now())) {
            throw new DomainException("消息发送超过2分钟，无法撤回");
        }
        
        this.status = MessageStatus.RECALLED;
        this.content = "[消息已撤回]";
    }
    
    private static void validateTextContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new DomainException("消息内容不能为空");
        }
        if (content.length() > 5000) {
            throw new DomainException("消息内容不能超过5000字");
        }
    }
}
```


## Conversation 聚合根

```java
public class Conversation {
    private final Long id;
    private final String participant1Id;
    private final String participant2Id;
    private final LocalDateTime createdAt;
    
    private Long lastMessageId;
    private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    private int unreadCount1;  // participant1 的未读数
    private int unreadCount2;  // participant2 的未读数
    
    // 工厂方法 - 创建或获取会话
    public static Conversation createOrGet(Long id, String userId1, String userId2) {
        // 确保 participant1Id < participant2Id，保证唯一性
        String p1 = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
        String p2 = userId1.compareTo(userId2) < 0 ? userId2 : userId1;
        
        Conversation conv = new Conversation();
        conv.id = id;
        conv.participant1Id = p1;
        conv.participant2Id = p2;
        conv.createdAt = LocalDateTime.now();
        return conv;
    }
    
    // 领域行为 - 更新最后消息
    public void updateLastMessage(Message message) {
        this.lastMessageId = message.getId();
        this.lastMessageContent = truncateContent(message.getContent(), 100);
        this.lastMessageAt = message.getCreatedAt();
        
        // 增加对方的未读数
        if (message.getSenderId().equals(participant1Id)) {
            this.unreadCount2++;
        } else {
            this.unreadCount1++;
        }
    }
    
    // 领域行为 - 清除未读数
    public void clearUnreadCount(String userId) {
        if (userId.equals(participant1Id)) {
            this.unreadCount1 = 0;
        } else if (userId.equals(participant2Id)) {
            this.unreadCount2 = 0;
        }
    }
    
    // 查询方法
    public String getOtherParticipant(String userId) {
        return userId.equals(participant1Id) ? participant2Id : participant1Id;
    }
    
    public int getUnreadCount(String userId) {
        return userId.equals(participant1Id) ? unreadCount1 : unreadCount2;
    }
}
```

## 多端推送架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Message Service                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         PushService (策略模式)                           │   │
│   │                                                                          │   │
│   │   push(userId, message) {                                                │   │
│   │       // 1. 查询用户在线设备                                              │   │
│   │       List<Device> devices = deviceRegistry.getOnlineDevices(userId);    │   │
│   │                                                                          │   │
│   │       // 2. 按设备类型分发                                                │   │
│   │       for (Device device : devices) {                                    │   │
│   │           switch (device.type) {                                         │   │
│   │               case WEB -> webSocketPush.push(device, message);           │   │
│   │               case MOBILE -> tcpPush.push(device, message);              │   │
│   │           }                                                              │   │
│   │       }                                                                  │   │
│   │                                                                          │   │
│   │       // 3. 离线消息存储                                                  │   │
│   │       if (devices.isEmpty()) {                                           │   │
│   │           offlineMessageStore.save(userId, message);                     │   │
│   │       }                                                                  │   │
│   │   }                                                                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐             │
│   │ WebSocketPush    │  │   TcpPush        │  │ OfflineStore     │             │
│   │ (Web端推送)       │  │ (移动端推送)      │  │ (离线消息存储)    │             │
│   └──────────────────┘  └──────────────────┘  └──────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 分布式 WebSocket 消息路由

### 问题背景

在多节点部署场景下，用户 A 连接到节点 1，用户 B 连接到节点 2。当用户 A 发送消息给用户 B 时，消息请求到达节点 1，但节点 1 无法直接推送到连接在节点 2 的用户 B。

### 解决方案：Redis Pub/Sub 广播

使用 Redis Pub/Sub 实现跨节点消息广播，每个节点维护本地连接注册表，收到广播后检查目标用户是否在本地。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        分布式 WebSocket 消息路由架构                              │
│                                                                                  │
│   用户A(节点1) 发送消息给 用户B(节点2)                                            │
│                                                                                  │
│   ┌─────────────┐                                        ┌─────────────┐        │
│   │   节点 1     │                                        │   节点 2     │        │
│   │             │                                        │             │        │
│   │  用户A ●────┼──► 发送消息                              │      ● 用户B │        │
│   │             │      │                                 │      ▲      │        │
│   │  Local      │      │                                 │  Local      │        │
│   │  Registry   │      │                                 │  Registry   │        │
│   │  [A: ✓]     │      │                                 │  [B: ✓]     │        │
│   └─────────────┘      │                                 └──────┼──────┘        │
│                        ▼                                        │               │
│   ┌─────────────────────────────────────────────────────────────┼───────────┐   │
│   │                     Redis Pub/Sub                           │           │   │
│   │                                                             │           │   │
│   │   Topic: "message:push:broadcast"                           │           │   │
│   │                                                             │           │   │
│   │   ┌─────────────────────────────────────────────────────────┼─────┐     │   │
│   │   │  BroadcastMessage {                                     │     │     │   │
│   │   │    targetUserId: "B",                                   │     │     │   │
│   │   │    message: {...},                                      │     │     │   │
│   │   │    sourceInstanceId: "node-1",                          │     │     │   │
│   │   │    timestamp: 1705123456789                             │     │     │   │
│   │   │  }                                                      │     │     │   │
│   │   └─────────────────────────────────────────────────────────┼─────┘     │   │
│   │                        │                                    │           │   │
│   └────────────────────────┼────────────────────────────────────┼───────────┘   │
│                            │                                    │               │
│                            ▼                                    ▼               │
│   ┌─────────────┐    ┌─────────────┐                     ┌─────────────┐        │
│   │   节点 1     │    │   节点 2     │                     │   节点 3     │        │
│   │             │    │             │                     │             │        │
│   │  收到广播    │    │  收到广播    │                     │  收到广播    │        │
│   │  检查: B在?  │    │  检查: B在?  │                     │  检查: B在?  │        │
│   │  → 否，忽略  │    │  → 是，推送! │                     │  → 否，忽略  │        │
│   │             │    │      │      │                     │             │        │
│   └─────────────┘    └──────┼──────┘                     └─────────────┘        │
│                             │                                                   │
│                             ▼                                                   │
│                      WebSocket 推送到用户B                                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. LocalConnectionRegistry（本地连接注册表）

每个节点维护一个内存中的本地连接注册表，记录连接到当前节点的用户。

```java
@Component
public class LocalConnectionRegistry {
    
    /**
     * 本地连接的用户ID集合
     * Key: userId
     * Value: sessionId 集合（一个用户可能有多个连接）
     */
    private final ConcurrentHashMap<String, Set<String>> localConnections = new ConcurrentHashMap<>();
    
    /**
     * 注册本地连接
     */
    public void register(String userId, String sessionId) {
        localConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }
    
    /**
     * 注销本地连接
     */
    public void unregister(String userId, String sessionId) {
        Set<String> sessions = localConnections.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                localConnections.remove(userId);
            }
        }
    }
    
    /**
     * 检查用户是否连接到本节点
     */
    public boolean isUserConnectedLocally(String userId) {
        Set<String> sessions = localConnections.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
```

#### 2. DeviceRegistry（全局设备注册表）

使用 Redis 存储所有节点的设备信息，用于判断用户是否在线。

```java
@Component
public class DeviceRegistry {
    
    private final RedissonClient redissonClient;
    
    private static final String DEVICE_KEY_PREFIX = "message:device:";
    private static final String USER_DEVICES_KEY_PREFIX = "message:user:devices:";
    
    /**
     * 注册设备（全局）
     */
    public void register(Device device) {
        // 存储设备信息到 Redis
        String deviceKey = DEVICE_KEY_PREFIX + device.getDeviceId();
        RMap<String, Object> deviceMap = redissonClient.getMap(deviceKey);
        deviceMap.put("deviceId", device.getDeviceId());
        deviceMap.put("userId", device.getUserId());
        deviceMap.put("type", device.getType().name());
        deviceMap.put("connectionId", device.getConnectionId());
        
        // 添加到用户设备列表
        String userDevicesKey = USER_DEVICES_KEY_PREFIX + device.getUserId();
        RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
        userDevices.put(device.getDeviceId(), device.getConnectionId());
    }
    
    /**
     * 获取用户的在线设备列表
     */
    public List<Device> getOnlineDevices(String userId) {
        String userDevicesKey = USER_DEVICES_KEY_PREFIX + userId;
        RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
        
        return userDevices.keySet().stream()
                .map(this::getDevice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
```

#### 3. DistributedWebSocketPushService（分布式推送服务）

通过 Redis Pub/Sub 广播消息到所有节点。

```java
@Service
public class DistributedWebSocketPushService {
    
    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final LocalConnectionRegistry localConnectionRegistry;
    
    private static final String PUSH_TOPIC = "message:push:broadcast";
    
    @Value("${spring.application.instance-id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String instanceId;
    
    /**
     * 初始化：订阅 Redis Topic
     */
    @PostConstruct
    public void init() {
        RTopic topic = redissonClient.getTopic(PUSH_TOPIC);
        
        topic.addListener(String.class, (channel, messageJson) -> {
            BroadcastMessage broadcastMessage = JsonUtils.fromJson(messageJson, BroadcastMessage.class);
            if (broadcastMessage != null) {
                handleBroadcastMessage(broadcastMessage);
            }
        });
    }
    
    /**
     * 推送消息（分布式）
     * 通过 Redis Pub/Sub 广播到所有节点
     */
    public void push(String userId, PushMessage message) {
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setTargetUserId(userId);
        broadcastMessage.setMessage(message);
        broadcastMessage.setSourceInstanceId(instanceId);
        broadcastMessage.setTimestamp(System.currentTimeMillis());
        
        RTopic topic = redissonClient.getTopic(PUSH_TOPIC);
        topic.publish(JsonUtils.toJson(broadcastMessage));
    }
    
    /**
     * 处理广播消息
     * 检查目标用户是否连接到本节点，如果是则推送
     */
    private void handleBroadcastMessage(BroadcastMessage broadcastMessage) {
        String targetUserId = broadcastMessage.getTargetUserId();
        PushMessage message = broadcastMessage.getMessage();
        
        // 检查目标用户是否连接到本节点
        if (localConnectionRegistry.isUserConnectedLocally(targetUserId)) {
            // 用户连接在本节点，通过本地 WebSocket 推送
            messagingTemplate.convertAndSendToUser(
                    targetUserId,
                    "/queue/messages",
                    message
            );
        }
        // 用户不在本节点，忽略
    }
}
```

#### 4. MessageWebSocketHandler（连接管理）

在 WebSocket 连接建立/断开时，同步更新本地和全局注册表。

```java
@Controller
public class MessageWebSocketHandler {
    
    private final DeviceRegistry deviceRegistry;
    private final LocalConnectionRegistry localConnectionRegistry;
    private final MultiChannelPushService pushService;
    
    /**
     * 处理 WebSocket 连接建立事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        String userId = extractUserId(event);
        String sessionId = extractSessionId(event);
        
        // 1. 注册到全局设备注册表（Redis）
        Device device = Device.builder()
                .deviceId("web-" + sessionId)
                .userId(userId)
                .type(DeviceType.WEB)
                .connectionId(sessionId)
                .build();
        deviceRegistry.register(device);
        
        // 2. 注册到本地连接注册表（内存）
        localConnectionRegistry.register(userId, sessionId);
        
        // 3. 推送离线消息
        pushService.pushOfflineMessages(userId, device);
    }
    
    /**
     * 处理 WebSocket 断开连接事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String userId = extractUserId(event);
        String sessionId = extractSessionId(event);
        
        // 1. 从全局注册表注销
        deviceRegistry.unregister("web-" + sessionId);
        
        // 2. 从本地注册表注销
        localConnectionRegistry.unregister(userId, sessionId);
    }
}
```

### 消息流程时序图

```
┌─────┐     ┌─────────┐     ┌─────────┐     ┌─────────────┐     ┌─────────┐     ┌─────┐
│用户A │     │ 节点1    │     │  Redis  │     │ Pub/Sub     │     │ 节点2    │     │用户B │
└──┬──┘     └────┬────┘     └────┬────┘     └──────┬──────┘     └────┬────┘     └──┬──┘
   │             │               │                 │                 │             │
   │ 发送消息     │               │                 │                 │             │
   │────────────>│               │                 │                 │             │
   │             │               │                 │                 │             │
   │             │ 查询用户B设备  │                 │                 │             │
   │             │──────────────>│                 │                 │             │
   │             │               │                 │                 │             │
   │             │ 返回设备列表   │                 │                 │             │
   │             │<──────────────│                 │                 │             │
   │             │               │                 │                 │             │
   │             │ 发布广播消息   │                 │                 │             │
   │             │───────────────┼────────────────>│                 │             │
   │             │               │                 │                 │             │
   │             │               │                 │ 广播到所有订阅者  │             │
   │             │               │                 │────────────────>│             │
   │             │               │                 │                 │             │
   │             │               │                 │                 │ 检查本地注册表│
   │             │               │                 │                 │ 用户B在本地? │
   │             │               │                 │                 │──────┐      │
   │             │               │                 │                 │      │ 是   │
   │             │               │                 │                 │<─────┘      │
   │             │               │                 │                 │             │
   │             │               │                 │                 │ WebSocket推送│
   │             │               │                 │                 │────────────>│
   │             │               │                 │                 │             │
   │             │               │                 │                 │             │ 收到消息
   │             │               │                 │                 │             │<────────
```

### 设计优势

1. **水平扩展**：新增节点只需订阅同一 Redis Topic，无需修改配置
2. **低延迟**：Redis Pub/Sub 是内存操作，延迟极低（通常 < 1ms）
3. **高可用**：Redis 支持主从复制和哨兵模式，保证高可用
4. **简单可靠**：每个节点独立判断，逻辑简单，不依赖复杂的路由表
5. **离线消息**：用户离线时消息存储到 Redis，上线后自动推送


## 推送服务实现

```java
// 推送服务接口
public interface PushService {
    void push(String userId, PushMessage message);
    void pushToDevice(String deviceId, PushMessage message);
}

// 推送服务实现（策略模式）
@Service
public class MultiChannelPushService implements PushService {
    
    private final DeviceRegistry deviceRegistry;
    private final WebSocketPushService webSocketPush;
    private final TcpPushService tcpPush;
    private final OfflineMessageStore offlineStore;
    private final RetryTemplate retryTemplate;
    
    @Override
    public void push(String userId, PushMessage message) {
        List<Device> devices = deviceRegistry.getOnlineDevices(userId);
        
        if (devices.isEmpty()) {
            // 用户离线，存储离线消息
            offlineStore.save(userId, message);
            return;
        }
        
        for (Device device : devices) {
            try {
                pushToDevice(device, message);
            } catch (PushException e) {
                // 推送失败，加入重试队列
                retryTemplate.execute(ctx -> {
                    pushToDevice(device, message);
                    return null;
                });
            }
        }
    }
    
    private void pushToDevice(Device device, PushMessage message) {
        switch (device.getType()) {
            case WEB -> webSocketPush.push(device.getConnectionId(), message);
            case IOS, ANDROID -> tcpPush.push(device.getConnectionId(), message);
        }
    }
}

// WebSocket 推送服务
@Service
public class WebSocketPushService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public void push(String connectionId, PushMessage message) {
        messagingTemplate.convertAndSendToUser(
            connectionId, 
            "/queue/messages", 
            message
        );
    }
}

// 离线消息存储
@Service
public class OfflineMessageStore {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public void save(String userId, PushMessage message) {
        String key = "offline:messages:" + userId;
        redisTemplate.opsForList().rightPush(key, message);
        // 设置过期时间（7天）
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    
    public List<PushMessage> getAndClear(String userId) {
        String key = "offline:messages:" + userId;
        List<Object> messages = redisTemplate.opsForList().range(key, 0, -1);
        redisTemplate.delete(key);
        return messages.stream()
            .map(m -> (PushMessage) m)
            .collect(Collectors.toList());
    }
}

// 用户上线事件处理器 - 负责推送离线消息
@Component
public class UserOnlineEventHandler {
    
    private final OfflineMessageStore offlineStore;
    private final MultiChannelPushService pushService;
    private final DeviceRegistry deviceRegistry;
    
    /**
     * 处理用户上线事件
     * 当用户建立 WebSocket/TCP 连接时触发
     */
    @EventListener
    public void handleUserOnline(UserOnlineEvent event) {
        String userId = event.getUserId();
        String deviceId = event.getDeviceId();
        
        // 获取并清除离线消息
        List<PushMessage> offlineMessages = offlineStore.getAndClear(userId);
        
        if (offlineMessages.isEmpty()) {
            return;
        }
        
        // 按时间顺序推送离线消息到当前设备
        Device device = deviceRegistry.getDevice(deviceId);
        if (device != null) {
            for (PushMessage message : offlineMessages) {
                try {
                    pushService.pushToDevice(device, message);
                } catch (PushException e) {
                    // 推送失败，重新存入离线队列
                    offlineStore.save(userId, message);
                    log.warn("Failed to deliver offline message to user {}: {}", userId, e.getMessage());
                }
            }
        }
    }
}

// WebSocket 连接建立时发布上线事件
@Component
public class WebSocketConnectionListener {
    
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceRegistry deviceRegistry;
    
    public void onConnectionEstablished(String userId, String deviceId, WebSocketSession session) {
        // 注册设备
        Device device = Device.builder()
            .deviceId(deviceId)
            .userId(userId)
            .type(DeviceType.WEB)
            .connectionId(session.getId())
            .connectedAt(LocalDateTime.now())
            .build();
        deviceRegistry.register(device);
        
        // 发布上线事件，触发离线消息推送
        eventPublisher.publishEvent(new UserOnlineEvent(userId, deviceId));
    }
}
```

## 数据库表设计 (Message_DB)

```sql
-- 会话表
CREATE TABLE conversations (
    id BIGINT PRIMARY KEY,
    participant1_id VARCHAR(36) NOT NULL,
    participant2_id VARCHAR(36) NOT NULL,
    last_message_id BIGINT,
    last_message_content VARCHAR(200),
    last_message_at TIMESTAMPTZ,
    unread_count1 INT DEFAULT 0,
    unread_count2 INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (participant1_id, participant2_id)
);

CREATE INDEX idx_conversations_p1 ON conversations(participant1_id, last_message_at DESC);
CREATE INDEX idx_conversations_p2 ON conversations(participant2_id, last_message_at DESC);

-- 消息表
CREATE TABLE messages (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id VARCHAR(36) NOT NULL,
    receiver_id VARCHAR(36) NOT NULL,
    type SMALLINT NOT NULL,          -- 0:文本 1:图片 2:文件
    content TEXT,
    media_url VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    status SMALLINT DEFAULT 0,       -- 0:已发送 1:已撤回
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_receiver ON messages(receiver_id, is_read, created_at DESC);
```
