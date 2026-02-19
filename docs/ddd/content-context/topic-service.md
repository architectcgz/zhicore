# TopicService 详细设计

## 服务概述

TopicService 负责话题管理，包括：
- 话题创建、更新、删除
- 话题查询（列表、详情）
- 话题关注/取消关注
- 话题统计

## 当前实现分析

### 依赖清单（4 依赖）

```csharp
public class TopicService(
    AppDbContext dbContext,
    IMapper mapper,
    ILogger<TopicService> logger,
    ISnowflakeIdService snowflakeIdService
) : ITopicService
```

### 当前架构特点

1. **简单直接**：直接通过 DbContext 操作
2. **统计更新**：文章发布时更新话题统计
3. **无缓存**：每次查询都访问数据库

### 问题分析

1. **统计更新耦合**：PostService 直接调用 TopicService 更新统计
2. **无热度计算**：缺少话题热度排行

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/ITopicRepository.cs
public interface ITopicRepository
{
    /// <summary>
    /// 根据ID获取话题
    /// </summary>
    Task<Topic?> GetByIdAsync(long topicId);
    
    /// <summary>
    /// 根据名称获取话题
    /// </summary>
    Task<Topic?> GetByNameAsync(string name);
    
    /// <summary>
    /// 检查话题是否存在
    /// </summary>
    Task<bool> ExistsAsync(long topicId);
    
    /// <summary>
    /// 获取话题列表
    /// </summary>
    Task<(IReadOnlyList<TopicVo> Items, int Total)> GetListAsync(
        int page, int pageSize, string? sortBy = null);
    
    /// <summary>
    /// 获取热门话题
    /// </summary>
    Task<IReadOnlyList<TopicVo>> GetHotTopicsAsync(int count);
    
    /// <summary>
    /// 添加话题
    /// </summary>
    Task<Topic> AddAsync(Topic topic);
    
    /// <summary>
    /// 更新话题
    /// </summary>
    Task UpdateAsync(Topic topic);
    
    /// <summary>
    /// 删除话题
    /// </summary>
    Task DeleteAsync(long topicId);
    
    /// <summary>
    /// 获取话题统计
    /// </summary>
    Task<TopicStats?> GetStatsAsync(long topicId);
    
    /// <summary>
    /// 更新话题统计
    /// </summary>
    Task UpdateStatsAsync(long topicId, Action<TopicStats> updateAction);
    
    /// <summary>
    /// 增加文章数
    /// </summary>
    Task IncrementPostCountAsync(long topicId, int delta = 1);
}
```

### Application Service

```csharp
// BlogCore/Application/Content/ITopicApplicationService.cs
public interface ITopicApplicationService
{
    Task<TopicVo> CreateTopicAsync(string userId, CreateTopicReq req);
    Task<TopicVo> UpdateTopicAsync(string userId, UpdateTopicReq req);
    Task DeleteTopicAsync(string userId, long topicId);
    Task<TopicVo?> GetTopicAsync(long topicId);
    Task<PaginatedResult<TopicVo>> GetTopicsAsync(TopicQueryReq query);
    Task<IReadOnlyList<TopicVo>> GetHotTopicsAsync(int count = 10);
    Task FollowTopicAsync(string userId, long topicId);
    Task UnfollowTopicAsync(string userId, long topicId);
}

// BlogCore/Application/Content/TopicApplicationService.cs
public class TopicApplicationService : ITopicApplicationService
{
    private readonly ITopicRepository _topicRepository;
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly IMapper _mapper;
    private readonly ILogger<TopicApplicationService> _logger;
    
    public async Task<TopicVo> CreateTopicAsync(string userId, CreateTopicReq req)
    {
        // 1. 检查话题名称是否已存在
        var existing = await _topicRepository.GetByNameAsync(req.Name);
        if (existing != null)
            throw new BusinessException(BusinessError.TopicNameExists);
        
        // 2. 创建话题
        var topic = new Topic
        {
            Id = _snowflakeIdService.NextId(),
            Name = req.Name,
            Description = req.Description,
            CoverUrl = req.CoverUrl,
            CreatorId = userId,
            Type = TopicType.User,
            Status = TopicStatus.Active,
            CreateTime = DateTimeOffset.UtcNow
        };
        
        await _topicRepository.AddAsync(topic);
        
        // 3. 发布领域事件
        await _eventDispatcher.DispatchAsync(new TopicCreatedEvent
        {
            TopicId = topic.Id,
            Name = topic.Name,
            CreatorId = userId
        });
        
        _logger.LogInformation("话题创建成功: TopicId={TopicId}, Name={Name}", topic.Id, topic.Name);
        
        return _mapper.Map<TopicVo>(topic);
    }
}
```

## 领域事件

### TopicCreatedEvent

```csharp
public record TopicCreatedEvent : DomainEventBase
{
    public override string EventType => nameof(TopicCreatedEvent);
    
    public long TopicId { get; init; }
    public string Name { get; init; } = string.Empty;
    public string CreatorId { get; init; } = string.Empty;
}
```

### TopicStatsUpdatedEvent

```csharp
public record TopicStatsUpdatedEvent : DomainEventBase
{
    public override string EventType => nameof(TopicStatsUpdatedEvent);
    
    public long TopicId { get; init; }
    public int PostCount { get; init; }
    public int FollowerCount { get; init; }
}
```

### 事件处理器

```csharp
// BlogCore/Domain/EventHandlers/Content/PostPublishedTopicHandler.cs
public class PostPublishedTopicHandler : IDomainEventHandler<PostPublishedEvent>
{
    private readonly ITopicRepository _topicRepository;
    private readonly ILogger<PostPublishedTopicHandler> _logger;
    
    public async Task HandleAsync(PostPublishedEvent @event, CancellationToken ct = default)
    {
        if (!@event.TopicId.HasValue) return;
        
        try
        {
            await _topicRepository.IncrementPostCountAsync(@event.TopicId.Value, 1);
            _logger.LogDebug("话题文章数已更新: TopicId={TopicId}", @event.TopicId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新话题文章数失败: TopicId={TopicId}", @event.TopicId);
        }
    }
}

// BlogCore/Domain/EventHandlers/Content/PostDeletedTopicHandler.cs
public class PostDeletedTopicHandler : IDomainEventHandler<PostDeletedEvent>
{
    private readonly ITopicRepository _topicRepository;
    private readonly ILogger<PostDeletedTopicHandler> _logger;
    
    public async Task HandleAsync(PostDeletedEvent @event, CancellationToken ct = default)
    {
        if (!@event.TopicId.HasValue) return;
        
        try
        {
            await _topicRepository.IncrementPostCountAsync(@event.TopicId.Value, -1);
            _logger.LogDebug("话题文章数已更新: TopicId={TopicId}", @event.TopicId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新话题文章数失败: TopicId={TopicId}", @event.TopicId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `topic:{topicId}` | String | 话题详情 | 10 分钟 |
| `topic:hot` | ZSet | 热门话题排行 | 5 分钟 |
| `topic:stats:{topicId}` | Hash | 话题统计 | 永久 |

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 4 | 5 |
| 统计更新 | 被动调用 | 事件驱动 |
| 热度排行 | 无 | 支持 |
| 缓存 | 无 | 支持 |
