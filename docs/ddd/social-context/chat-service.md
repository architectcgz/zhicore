# ChatService 详细设计

## 服务概述

ChatService 负责私信/聊天功能，包括：
- 消息发送
- 消息接收者验证
- 会话列表管理
- 消息历史查询
- 用户存在性检查

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| POST | `/api/chat/messages` | SendMessageAsync | 发送私信 |
| GET | `/api/chat/conversations` | GetConversationsAsync | 获取会话列表 |
| GET | `/api/chat/conversations/{userId}` | GetConversationAsync | 获取与某用户的聊天记录 |
| GET | `/api/chat/unread` | GetUnreadCountAsync | 获取未读消息数 |
| POST | `/api/chat/conversations/{userId}/read` | MarkAsReadAsync | 标记消息已读 |
| DELETE | `/api/chat/messages/{id}` | DeleteMessageAsync | 删除消息 |

## 当前实现分析

### 依赖清单（7 依赖）

```csharp
public class ChatService(
    AppDbContext dbContext,
    IMapper mapper,
    ILogger<ChatService> logger,
    IUserBlockService userBlockService,
    IAntiSpamService antiSpamService,
    ISnowflakeIdService snowflakeIdService,
    IEventPublisher eventPublisher
) : IChatService
```

### 当前架构特点

1. **拉黑检查**：发送前检查双向拉黑状态
2. **防刷保护**：限制消息发送频率
3. **MQ 异步**：通过 RabbitMQ 异步处理消息

### 问题分析

1. **缓存逻辑分散**：部分在 ChatService，部分在 CachedChatService
2. **验证逻辑重复**：每次发送都要验证接收者

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IChatRepository.cs
public interface IChatRepository
{
    /// <summary>
    /// 根据ID获取消息
    /// </summary>
    Task<ChatMessage?> GetByIdAsync(long messageId);
    
    /// <summary>
    /// 获取会话消息列表
    /// </summary>
    Task<(IReadOnlyList<ChatMessageVo> Items, int Total)> GetConversationMessagesAsync(
        string userId1, string userId2, int page, int pageSize);
    
    /// <summary>
    /// 获取用户会话列表
    /// </summary>
    Task<IReadOnlyList<ConversationVo>> GetConversationsAsync(string userId, int page, int pageSize);
    
    /// <summary>
    /// 添加消息
    /// </summary>
    Task<ChatMessage> AddAsync(ChatMessage message);
    
    /// <summary>
    /// 标记消息为已读
    /// </summary>
    Task MarkAsReadAsync(string senderId, string receiverId);
    
    /// <summary>
    /// 获取未读消息数
    /// </summary>
    Task<int> GetUnreadCountAsync(string userId);
    
    /// <summary>
    /// 获取与特定用户的未读消息数
    /// </summary>
    Task<int> GetUnreadCountWithUserAsync(string userId, string otherUserId);
    
    /// <summary>
    /// 删除消息
    /// </summary>
    Task DeleteAsync(long messageId, string userId);
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/ChatRepository.cs
public class ChatRepository : IChatRepository
{
    private readonly AppDbContext _dbContext;
    private readonly IMapper _mapper;
    private readonly ILogger<ChatRepository> _logger;
    
    public ChatRepository(
        AppDbContext dbContext, 
        IMapper mapper,
        ILogger<ChatRepository> logger)
    {
        _dbContext = dbContext;
        _mapper = mapper;
        _logger = logger;
    }
    
    public async Task<ChatMessage?> GetByIdAsync(long messageId)
    {
        return await _dbContext.ChatMessages
            .AsNoTracking()
            .FirstOrDefaultAsync(m => m.Id == messageId && !m.Deleted);
    }
    
    public async Task<(IReadOnlyList<ChatMessageVo> Items, int Total)> GetConversationMessagesAsync(
        string userId1, string userId2, int page, int pageSize)
    {
        var query = _dbContext.ChatMessages
            .Where(m => !m.Deleted &&
                ((m.SenderId == userId1 && m.ReceiverId == userId2) ||
                 (m.SenderId == userId2 && m.ReceiverId == userId1)))
            .OrderByDescending(m => m.CreateTime);
        
        var total = await query.CountAsync();
        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(m => new ChatMessageVo
            {
                Id = m.Id,
                SenderId = m.SenderId,
                ReceiverId = m.ReceiverId,
                Content = m.Content,
                IsRead = m.IsRead,
                CreateTime = m.CreateTime
            })
            .ToListAsync();
        
        return (items, total);
    }
    
    public async Task<IReadOnlyList<ConversationVo>> GetConversationsAsync(string userId, int page, int pageSize)
    {
        // 获取用户参与的所有会话（按最新消息时间排序）
        var conversations = await (
            from m in _dbContext.ChatMessages
            where !m.Deleted && (m.SenderId == userId || m.ReceiverId == userId)
            group m by m.SenderId == userId ? m.ReceiverId : m.SenderId into g
            select new
            {
                OtherUserId = g.Key,
                LastMessage = g.OrderByDescending(x => x.CreateTime).First(),
                UnreadCount = g.Count(x => x.ReceiverId == userId && !x.IsRead)
            }
        )
        .OrderByDescending(x => x.LastMessage.CreateTime)
        .Skip((page - 1) * pageSize)
        .Take(pageSize)
        .ToListAsync();
        
        // 获取对方用户信息
        var otherUserIds = conversations.Select(c => c.OtherUserId).ToList();
        var users = await _dbContext.Users
            .Where(u => otherUserIds.Contains(u.Id))
            .ToDictionaryAsync(u => u.Id);
        
        return conversations.Select(c => new ConversationVo
        {
            OtherUserId = c.OtherUserId,
            OtherUserNickName = users.TryGetValue(c.OtherUserId, out var u) ? u.NickName : "未知用户",
            OtherUserAvatarUrl = u?.AvatarUrl,
            LastMessage = c.LastMessage.Content,
            LastMessageTime = c.LastMessage.CreateTime,
            UnreadCount = c.UnreadCount
        }).ToList();
    }
    
    public async Task<ChatMessage> AddAsync(ChatMessage message)
    {
        await _dbContext.ChatMessages.AddAsync(message);
        await _dbContext.SaveChangesAsync();
        return message;
    }
    
    public async Task MarkAsReadAsync(string senderId, string receiverId)
    {
        await _dbContext.ChatMessages
            .Where(m => m.SenderId == senderId && m.ReceiverId == receiverId && !m.IsRead)
            .ExecuteUpdateAsync(s => s.SetProperty(m => m.IsRead, true));
    }
    
    public async Task<int> GetUnreadCountAsync(string userId)
    {
        return await _dbContext.ChatMessages
            .CountAsync(m => m.ReceiverId == userId && !m.IsRead && !m.Deleted);
    }
    
    public async Task<int> GetUnreadCountWithUserAsync(string userId, string otherUserId)
    {
        return await _dbContext.ChatMessages
            .CountAsync(m => m.SenderId == otherUserId && m.ReceiverId == userId && !m.IsRead && !m.Deleted);
    }
    
    public async Task DeleteAsync(long messageId, string userId)
    {
        await _dbContext.ChatMessages
            .Where(m => m.Id == messageId && (m.SenderId == userId || m.ReceiverId == userId))
            .ExecuteUpdateAsync(s => s.SetProperty(m => m.Deleted, true));
    }
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IChatDomainService.cs
public interface IChatDomainService
{
    /// <summary>
    /// 验证消息接收者
    /// </summary>
    Task<MessageReceiverValidationResult> ValidateReceiverAsync(string senderId, string receiverId);
    
    /// <summary>
    /// 创建消息实体
    /// </summary>
    Task<ChatMessage> CreateMessageAsync(string senderId, string receiverId, string content);
}

// ZhiCoreCore/Domain/Services/ChatDomainService.cs
public class ChatDomainService : IChatDomainService
{
    private readonly IUserRepository _userRepository;
    private readonly IUserBlockService _userBlockService;
    private readonly ISnowflakeIdService _snowflakeIdService;
    
    public async Task<MessageReceiverValidationResult> ValidateReceiverAsync(string senderId, string receiverId)
    {
        // 1. 不能给自己发消息
        if (senderId == receiverId)
        {
            return new MessageReceiverValidationResult
            {
                IsValid = false,
                ErrorCode = "CANNOT_MESSAGE_SELF",
                ErrorMessage = "不能给自己发送消息"
            };
        }
        
        // 2. 检查接收者是否存在
        var receiverExists = await _userRepository.ExistsAsync(receiverId);
        if (!receiverExists)
        {
            return new MessageReceiverValidationResult
            {
                IsValid = false,
                ErrorCode = "USER_NOT_FOUND",
                ErrorMessage = "用户不存在"
            };
        }
        
        // 3. 检查是否被对方拉黑
        var isBlockedBy = await _userBlockService.IsBlockedByAsync(senderId, receiverId);
        if (isBlockedBy)
        {
            return new MessageReceiverValidationResult
            {
                IsValid = false,
                ErrorCode = "BLOCKED_BY_RECEIVER",
                ErrorMessage = "对方已将你拉黑，无法发送消息"
            };
        }
        
        // 4. 检查是否拉黑了对方
        var hasBlocked = await _userBlockService.IsBlockedAsync(senderId, receiverId);
        if (hasBlocked)
        {
            return new MessageReceiverValidationResult
            {
                IsValid = false,
                ErrorCode = "HAS_BLOCKED_RECEIVER",
                ErrorMessage = "你已拉黑对方，无法发送消息"
            };
        }
        
        return new MessageReceiverValidationResult { IsValid = true };
    }
    
    public async Task<ChatMessage> CreateMessageAsync(string senderId, string receiverId, string content)
    {
        // 验证接收者
        var validation = await ValidateReceiverAsync(senderId, receiverId);
        if (!validation.IsValid)
        {
            throw new BusinessException(validation.ErrorCode, validation.ErrorMessage);
        }
        
        return new ChatMessage
        {
            Id = _snowflakeIdService.NextId(),
            SenderId = senderId,
            ReceiverId = receiverId,
            Content = content,
            IsRead = false,
            CreateTime = DateTimeOffset.UtcNow
        };
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Social/IChatApplicationService.cs
public interface IChatApplicationService
{
    Task<ChatMessageVo> SendMessageAsync(string senderId, SendMessageReq req);
    Task<PaginatedResult<ChatMessageVo>> GetConversationAsync(string userId, string otherUserId, int page, int pageSize);
    Task<IReadOnlyList<ConversationVo>> GetConversationsAsync(string userId, int page, int pageSize);
    Task<int> GetUnreadCountAsync(string userId);
    Task MarkAsReadAsync(string userId, string otherUserId);
}

// ZhiCoreCore/Application/Social/ChatApplicationService.cs
public class ChatApplicationService : IChatApplicationService
{
    private readonly IChatRepository _chatRepository;
    private readonly IChatDomainService _chatDomainService;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly IMapper _mapper;
    private readonly ILogger<ChatApplicationService> _logger;
    
    public async Task<ChatMessageVo> SendMessageAsync(string senderId, SendMessageReq req)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Message, senderId, req.ReceiverId);
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 通过 Domain Service 创建消息
        var message = await _chatDomainService.CreateMessageAsync(senderId, req.ReceiverId, req.Content);
        
        // 3. 持久化
        await _chatRepository.AddAsync(message);
        
        // 4. 记录操作
        await _antiSpamService.RecordActionAsync(AntiSpamActionType.Message, senderId, req.ReceiverId);
        
        // 5. 发布领域事件
        await _eventDispatcher.DispatchAsync(new MessageSentEvent
        {
            MessageId = message.Id,
            SenderId = senderId,
            ReceiverId = req.ReceiverId,
            Content = message.Content
        });
        
        _logger.LogInformation("消息发送成功: MessageId={MessageId}, SenderId={SenderId}, ReceiverId={ReceiverId}",
            message.Id, senderId, req.ReceiverId);
        
        return _mapper.Map<ChatMessageVo>(message);
    }
    
    public async Task MarkAsReadAsync(string userId, string otherUserId)
    {
        await _chatRepository.MarkAsReadAsync(otherUserId, userId);
        
        // 发布事件更新未读数缓存
        await _eventDispatcher.DispatchAsync(new MessagesReadEvent
        {
            UserId = userId,
            OtherUserId = otherUserId
        });
    }
}
```

## DTO 定义

### ChatMessageVo

```csharp
/// <summary>
/// 聊天消息视图对象
/// </summary>
public class ChatMessageVo
{
    /// <summary>
    /// 消息ID
    /// </summary>
    public long Id { get; set; }
    
    /// <summary>
    /// 发送者ID
    /// </summary>
    public string SenderId { get; set; } = string.Empty;
    
    /// <summary>
    /// 发送者昵称
    /// </summary>
    public string? SenderNickName { get; set; }
    
    /// <summary>
    /// 发送者头像
    /// </summary>
    public string? SenderAvatarUrl { get; set; }
    
    /// <summary>
    /// 接收者ID
    /// </summary>
    public string ReceiverId { get; set; } = string.Empty;
    
    /// <summary>
    /// 消息内容
    /// </summary>
    public string Content { get; set; } = string.Empty;
    
    /// <summary>
    /// 是否已读
    /// </summary>
    public bool IsRead { get; set; }
    
    /// <summary>
    /// 创建时间
    /// </summary>
    public DateTimeOffset CreateTime { get; set; }
}
```

### ConversationVo

```csharp
/// <summary>
/// 会话视图对象
/// </summary>
public class ConversationVo
{
    /// <summary>
    /// 对方用户ID
    /// </summary>
    public string OtherUserId { get; set; } = string.Empty;
    
    /// <summary>
    /// 对方昵称
    /// </summary>
    public string OtherUserNickName { get; set; } = string.Empty;
    
    /// <summary>
    /// 对方头像
    /// </summary>
    public string? OtherUserAvatarUrl { get; set; }
    
    /// <summary>
    /// 最后一条消息内容
    /// </summary>
    public string LastMessage { get; set; } = string.Empty;
    
    /// <summary>
    /// 最后消息时间
    /// </summary>
    public DateTimeOffset LastMessageTime { get; set; }
    
    /// <summary>
    /// 未读消息数
    /// </summary>
    public int UnreadCount { get; set; }
}
```

### SendMessageReq

```csharp
/// <summary>
/// 发送消息请求
/// </summary>
public class SendMessageReq
{
    /// <summary>
    /// 接收者ID
    /// </summary>
    [Required]
    public string ReceiverId { get; set; } = string.Empty;
    
    /// <summary>
    /// 消息内容
    /// </summary>
    [Required]
    [MaxLength(2000)]
    public string Content { get; set; } = string.Empty;
}
```

### MessageReceiverValidationResult

```csharp
/// <summary>
/// 消息接收者验证结果
/// </summary>
public class MessageReceiverValidationResult
{
    /// <summary>
    /// 是否有效
    /// </summary>
    public bool IsValid { get; set; }
    
    /// <summary>
    /// 错误码
    /// </summary>
    public string? ErrorCode { get; set; }
    
    /// <summary>
    /// 错误消息
    /// </summary>
    public string? ErrorMessage { get; set; }
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
// ZhiCoreCore/Domain/EventHandlers/Social/MessageSentEventHandler.cs
public class MessageSentEventHandler : IDomainEventHandler<MessageSentEvent>
{
    private readonly IHubContext<ChatHub> _hubContext;
    private readonly INotificationApplicationService _notificationService;
    private readonly IDatabase _redis;
    private readonly ILogger<MessageSentEventHandler> _logger;
    
    public async Task HandleAsync(MessageSentEvent @event, CancellationToken ct = default)
    {
        // 1. 通过 SignalR 实时推送消息
        try
        {
            await _hubContext.Clients
                .User(@event.ReceiverId)
                .SendAsync("ReceiveMessage", new
                {
                    @event.MessageId,
                    @event.SenderId,
                    @event.Content,
                    SentAt = DateTimeOffset.UtcNow
                }, ct);
            
            _logger.LogDebug("消息已推送: ReceiverId={ReceiverId}", @event.ReceiverId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "推送消息失败: ReceiverId={ReceiverId}", @event.ReceiverId);
        }
        
        // 2. 更新未读数缓存
        try
        {
            var key = $"chat:unread:{@event.ReceiverId}";
            await _redis.StringIncrementAsync(key);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新未读数缓存失败");
        }
        
        // 3. 创建私信通知（可选，根据用户设置）
        try
        {
            await _notificationService.CreateNotificationAsync(new CreateNotificationReq
            {
                UserId = @event.ReceiverId,
                Type = NotificationType.MessageReceived,
                Title = "你收到一条新私信",
                Content = @event.Content.Length > 50 
                    ? @event.Content.Substring(0, 50) + "..." 
                    : @event.Content,
                SourceUserId = @event.SenderId,
                TargetId = @event.MessageId.ToString(),
                TargetType = "message"
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "创建私信通知失败");
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `chat:unread:{userId}` | String | 总未读消息数 | 永久 |
| `chat:unread:{userId}:{otherUserId}` | String | 与特定用户的未读数 | 永久 |
| `chat:validation:{senderId}:{receiverId}` | String | 消息验证结果缓存 | 2 分钟 |
| `user:exists:{userId}` | String | 用户存在性缓存 | 10 分钟 |

### Cached Decorator

```csharp
// 已在 CachedChatService 中实现
public class CachedChatService : CacheDecoratorBase, IChatService
{
    public async Task<MessageReceiverValidationResult> ValidateMessageReceiverAsync(
        string senderId, string receiverId)
    {
        var cacheKey = $"chat:validation:{senderId}:{receiverId}";
        
        // 检查缓存
        var cached = await GetFromCacheAsync<MessageReceiverValidationResult>(
            cacheKey, $"GetMessageValidation:{senderId}:{receiverId}");
        
        if (cached != null) return cached;
        
        // 调用内部服务
        var result = await _inner.ValidateMessageReceiverAsync(senderId, receiverId);
        
        // 缓存结果（短TTL：2分钟）
        await SetToCacheAsync(cacheKey, result, TimeSpan.FromMinutes(2),
            $"SetMessageValidation:{senderId}:{receiverId}");
        
        return result;
    }
}
```

## 实时通信

### SignalR Hub

```csharp
public class ChatHub : Hub
{
    public async Task SendMessage(string receiverId, string content)
    {
        var senderId = Context.UserIdentifier;
        
        // 通过 Application Service 发送消息
        // Hub 只负责实时通信，不处理业务逻辑
    }
    
    public async Task MarkAsRead(string otherUserId)
    {
        var userId = Context.UserIdentifier;
        
        // 通知对方消息已读
        await Clients.User(otherUserId).SendAsync("MessagesRead", userId);
    }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 7 | 6 |
| 验证逻辑 | 散落在服务中 | 集中在 Domain Service |
| 数据访问 | 直接操作 DbContext | 通过 Repository 抽象 |
| 推送逻辑 | 混在服务中 | 独立事件处理器 |
| 缓存策略 | 部分实现 | 完整实现 |

## DI 注册

```csharp
// ZhiCoreCore/Extensions/SocialServiceExtensions.cs
public static IServiceCollection AddChatServices(this IServiceCollection services)
{
    // Repository
    services.AddScoped<IChatRepository, ChatRepository>();
    
    // Domain Service
    services.AddScoped<IChatDomainService, ChatDomainService>();
    
    // Application Service
    services.AddScoped<IChatApplicationService, ChatApplicationService>();
    
    // 事件处理器
    services.AddScoped<IDomainEventHandler<MessageSentEvent>, MessageSentEventHandler>();
    services.AddScoped<IDomainEventHandler<MessagesReadEvent>, MessagesReadEventHandler>();
    
    return services;
}
```
