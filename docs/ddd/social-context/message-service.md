# MessageService 详细设计

## 服务概述

MessageService 负责私信功能的管理，包括：
- 消息发送（含防刷检测、拉黑检查）
- 消息列表查询
- 会话列表管理
- 消息已读状态管理（检查点机制）
- 离线消息获取
- 陌生人消息限制

## 当前实现分析

### 依赖清单（9 个依赖）

```csharp
public class MessageService(
    AppDbContext context, 
    IUnreadCountService unifiedUnreadCountService,
    ILogger<MessageService> logger,
    IAntiSpamService antiSpamService,
    IUserBlockService userBlockService,
    ISnowflakeIdService snowflakeIdService,
    IOptions<AntiSpamConfig> antiSpamConfig,
    IConnectionMultiplexer redis,
    IRedisPolicyProvider redisPolicyProvider) : IMessageService
```

### 当前架构特点

1. **检查点机制**：使用 CheckpointMessageId 标记已读位置，而非逐条标记
2. **双向会话记录**：发送方和接收方各维护一条会话记录
3. **陌生人限制**：未互相关注且未回复时，限制消息数量
4. **Polly 保护**：Redis 操作有熔断保护

### 数据模型

```csharp
// 消息表
public class Message
{
    public long Id { get; set; }           // 雪花 ID
    public string SenderId { get; set; }   // 发送者 ID
    public string ReceiverId { get; set; } // 接收者 ID
    public string Content { get; set; }    // 消息内容
    public MessageType MessageType { get; set; } // 消息类型
    public bool Deleted { get; set; }      // 是否删除
    public DateTimeOffset CreatedAt { get; set; }
}

// 会话表
public class Conversation
{
    public string From { get; set; }       // 会话所有者
    public string To { get; set; }         // 对方用户
    public long CheckpointMessageId { get; set; } // 已读检查点
    public long LastMessageId { get; set; } // 最后一条消息 ID
    public DateTimeOffset LastReadTime { get; set; }
    public DateTimeOffset UpdateTime { get; set; }
}
```

### 检查点机制说明

```
消息时间线：
M1 ─► M2 ─► M3 ─► M4 ─► M5 ─► M6
              ▲
              │
        CheckpointMessageId = M3
        
未读消息 = M4, M5, M6（ID > CheckpointMessageId）
```

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IMessageRepository.cs
public interface IMessageRepository
{
    /// <summary>
    /// 添加消息
    /// </summary>
    Task<Message> AddAsync(Message message);
    
    /// <summary>
    /// 获取消息
    /// </summary>
    Task<Message?> GetByIdAsync(long messageId);
    
    /// <summary>
    /// 获取会话消息列表
    /// </summary>
    Task<(IReadOnlyList<MessageDto> Items, int Total)> GetConversationMessagesAsync(
        string userId1, string userId2, int page, int pageSize);
    
    /// <summary>
    /// 获取离线消息
    /// </summary>
    Task<IReadOnlyList<MessageDto>> GetOfflineMessagesAsync(string userId, int limit);
    
    /// <summary>
    /// 软删除消息
    /// </summary>
    Task DeleteAsync(long messageId);
}

// BlogCore/Domain/Repositories/IConversationRepository.cs
public interface IConversationRepository
{
    /// <summary>
    /// 获取会话
    /// </summary>
    Task<Conversation?> GetAsync(string from, string to);
    
    /// <summary>
    /// 获取用户的会话列表
    /// </summary>
    Task<IReadOnlyList<ConversationRespDto>> GetUserConversationsAsync(
        string userId, int page, int pageSize);
    
    /// <summary>
    /// 创建或更新会话
    /// </summary>
    Task CreateOrUpdateAsync(string from, string to, long lastMessageId);
    
    /// <summary>
    /// 更新检查点
    /// </summary>
    Task UpdateCheckpointAsync(string from, string to, long checkpointMessageId);
    
    /// <summary>
    /// 获取总未读数
    /// </summary>
    Task<int> GetTotalUnreadCountAsync(string userId);
}
```

### Domain Service

```csharp
// BlogCore/Domain/Services/IMessageDomainService.cs
public interface IMessageDomainService
{
    /// <summary>
    /// 验证消息发送
    /// </summary>
    Task ValidateSendMessageAsync(string senderId, string receiverId);
    
    /// <summary>
    /// 检查陌生人消息限制
    /// </summary>
    Task<StrangerMessageStatus> CheckStrangerLimitAsync(string senderId, string receiverId);
    
    /// <summary>
    /// 创建消息实体
    /// </summary>
    Message CreateMessage(string senderId, string receiverId, string content, MessageType type);
}

// BlogCore/Domain/Services/MessageDomainService.cs
public class MessageDomainService : IMessageDomainService
{
    private readonly IUserBlockService _userBlockService;
    private readonly IConversationRepository _conversationRepository;
    private readonly IMessageRepository _messageRepository;
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly MessageAntiSpamConfig _config;
    
    public async Task ValidateSendMessageAsync(string senderId, string receiverId)
    {
        // 1. 不能给自己发消息
        if (senderId == receiverId)
            throw new BusinessException(BusinessError.CannotSendToSelf);
        
        // 2. 检查是否被对方拉黑
        var isBlockedByReceiver = await _userBlockService.IsBlockedByAsync(senderId, receiverId);
        if (isBlockedByReceiver)
            throw new BusinessException(BusinessError.BlockedByReceiver);
        
        // 3. 检查是否拉黑了对方
        var hasBlockedReceiver = await _userBlockService.IsBlockedAsync(senderId, receiverId);
        if (hasBlockedReceiver)
            throw new BusinessException(BusinessError.HasBlockedReceiver);
    }
    
    public async Task<StrangerMessageStatus> CheckStrangerLimitAsync(string senderId, string receiverId)
    {
        if (!_config.EnableStrangerMessageLimit)
        {
            return new StrangerMessageStatus
            {
                IsStranger = false,
                CanSend = true
            };
        }
        
        // 检查是否被对方关注
        var isFollowedByTarget = await _userFollowRepository.ExistsAsync(receiverId, senderId);
        if (isFollowedByTarget)
        {
            return new StrangerMessageStatus { IsStranger = false, CanSend = true };
        }
        
        // 检查是否收到过对方的回复
        var hasReceivedReply = await _messageRepository.HasMessageFromAsync(receiverId, senderId);
        if (hasReceivedReply)
        {
            return new StrangerMessageStatus { IsStranger = false, CanSend = true };
        }
        
        // 是陌生人，检查已发送消息数
        var sentCount = await _messageRepository.GetSentCountAsync(senderId, receiverId);
        
        return new StrangerMessageStatus
        {
            IsStranger = true,
            SentCount = sentCount,
            MaxCount = _config.MaxMessagesToStrangerBeforeReply,
            CanSend = sentCount < _config.MaxMessagesToStrangerBeforeReply
        };
    }
    
    public Message CreateMessage(string senderId, string receiverId, string content, MessageType type)
    {
        return new Message
        {
            Id = _snowflakeIdService.NextId(),
            SenderId = senderId,
            ReceiverId = receiverId,
            Content = content,
            MessageType = type,
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow
        };
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Social/IMessageApplicationService.cs
public interface IMessageApplicationService
{
    Task<MessageDto> SendMessageAsync(string senderId, SendMessageReq req);
    Task<MessageListResp> GetMessagesAsync(string currentUserId, GetMessagesReq req);
    Task<ConversationListResp> GetConversationsAsync(string userId, int page, int pageSize);
    Task MarkAsReadAsync(string userId, long messageId);
    Task<long> MarkConversationAsReadAsync(string currentUserId, string otherUserId);
    Task DeleteMessageAsync(string userId, long messageId);
    Task<List<MessageDto>> GetOfflineMessagesAsync(string userId, int limit);
}

// BlogCore/Application/Social/MessageApplicationService.cs
public class MessageApplicationService : IMessageApplicationService
{
    private readonly IMessageDomainService _domainService;
    private readonly IMessageRepository _messageRepository;
    private readonly IConversationRepository _conversationRepository;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IUnreadCountService _unreadCountService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<MessageApplicationService> _logger;
    
    public async Task<MessageDto> SendMessageAsync(string senderId, SendMessageReq req)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Message, senderId, req.ReceiverId);
        
        if (antiSpamResult.IsBlocked)
        {
            if (antiSpamResult.LimitType == AntiSpamLimitType.StrangerLimit)
                throw BusinessException.CreateAntiSpamException(BusinessError.StrangerMessageLimit, antiSpamResult);
            else
                throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        }
        
        // 2. 验证消息发送
        await _domainService.ValidateSendMessageAsync(senderId, req.ReceiverId);
        
        // 3. 创建消息
        var message = _domainService.CreateMessage(
            senderId, req.ReceiverId, req.Content, req.GetValidatedMessageType());
        
        // 4. 保存消息
        await _messageRepository.AddAsync(message);
        
        // 5. 更新会话记录（双向）
        await _conversationRepository.CreateOrUpdateAsync(senderId, req.ReceiverId, message.Id);
        await _conversationRepository.CreateOrUpdateAsync(req.ReceiverId, senderId, message.Id);
        
        // 6. 记录操作（用于防刷统计）
        await _antiSpamService.RecordActionAsync(AntiSpamActionType.Message, senderId, req.ReceiverId);
        
        // 7. 清除未读数缓存
        await _unreadCountService.InvalidateUnreadCountCacheAsync(req.ReceiverId, NotificationType.Chat);
        
        // 8. 发布领域事件
        await _eventDispatcher.DispatchAsync(new MessageSentEvent
        {
            MessageId = message.Id,
            SenderId = senderId,
            ReceiverId = req.ReceiverId,
            Content = req.Content
        });
        
        _logger.LogInformation("消息发送成功: {SenderId} -> {ReceiverId}, MessageId={MessageId}", 
            senderId, req.ReceiverId, message.Id);
        
        return MapToDto(message);
    }
    
    public async Task<long> MarkConversationAsReadAsync(string currentUserId, string otherUserId)
    {
        // 1. 获取最新消息 ID
        var latestMessageId = await _messageRepository.GetLatestMessageIdAsync(otherUserId, currentUserId);
        
        if (latestMessageId <= 0)
            return 0;
        
        // 2. 更新检查点
        await _conversationRepository.UpdateCheckpointAsync(currentUserId, otherUserId, latestMessageId);
        
        // 3. 清除未读数缓存
        await _unreadCountService.ResetUnreadCountByTypeAsync(currentUserId, (int)MessageCheckpointType.ChatMessage);
        
        _logger.LogInformation("会话已读更新: {UserId} <- {OtherUserId}, CheckpointMessageId={CheckpointMessageId}", 
            currentUserId, otherUserId, latestMessageId);
        
        return latestMessageId;
    }
}
```

## 领域事件

### MessageSentEvent

```csharp
public record MessageSentEvent : DomainEventBase
{
    public override string EventType => nameof(MessageSentEvent);
    
    public long MessageId { get; init; }
    public string SenderId { get; init; } = string.Empty;
    public string ReceiverId { get; init; } = string.Empty;
    public string Content { get; init; } = string.Empty;
}
```

### 事件处理器

```csharp
// 通过 SignalR 实时推送消息
public class MessageSentEventHandler : IDomainEventHandler<MessageSentEvent>
{
    private readonly IHubContext<ChatHub> _hubContext;
    
    public async Task HandleAsync(MessageSentEvent @event, CancellationToken ct = default)
    {
        await _hubContext.Clients
            .User(@event.ReceiverId)
            .SendAsync("ReceiveMessage", new
            {
                @event.MessageId,
                @event.SenderId,
                @event.Content
            }, ct);
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `antispam:stranger:{userId}:{targetId}` | String | 陌生人关系缓存 | 1 小时 |
| `unread:chat:{userId}` | String | 聊天未读数缓存 | 5 分钟 |

### 陌生人关系缓存

```csharp
// 缓存陌生人关系判断结果
await _redisPolicyProvider.ExecuteSilentAsync(async _ =>
{
    var strangerKey = RedisKeys.AntiSpam.GetStrangerRelationKey(userId, targetId);
    await _redis.StringSetAsync(strangerKey, isStranger ? "1" : "0", TimeSpan.FromHours(1));
});
```

## 降级策略

### Redis 不可用时

```csharp
// 陌生人关系检查降级到数据库
bool? isStrangerFromCache = await _redisPolicyProvider.ExecuteSilentAsync<bool?>(
    async _ =>
    {
        var cachedValue = await _redis.StringGetAsync(strangerKey);
        return cachedValue.HasValue ? cachedValue == "1" : null;
    },
    defaultValue: null,
    operationKey: $"GetStrangerRelation:{userId}:{targetId}");

if (!isStrangerFromCache.HasValue)
{
    // Redis 不可用，从数据库查询
    var isFollowed = await _dbContext.UserFollows.AnyAsync(...);
    var hasReply = await _dbContext.Messages.AnyAsync(...);
    isStranger = !isFollowed && !hasReply;
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 9 | 7 |
| 方法行数 | SendMessageAsync 100+ 行 | SendMessageAsync 50 行 |
| 职责分离 | 混杂 | 清晰分层 |
| 实时推送 | 无 | 通过事件处理器 |
