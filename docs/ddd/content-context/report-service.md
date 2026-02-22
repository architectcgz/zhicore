# ReportService 详细设计

## 服务概述

ReportService 负责举报功能的管理，包括：
- 创建举报（文章、评论、话题）
- 举报查询（单个/列表）
- 重复举报检测
- 被举报内容验证

## 当前实现分析

### 依赖清单（4 个依赖）

```csharp
public class ReportService(
    AppDbContext dbContext,
    ILogger<ReportService> logger,
    IAntiSpamService antiSpamService,
    ISnowflakeIdService snowflakeIdService) : IReportService
```

### 支持的举报类型

| 类型 | 说明 |
|------|------|
| Post | 文章举报 |
| Comment | 评论举报 |
| Topic | 话题举报 |

### 举报状态流转

```
Pending（待处理）
    │
    ▼
Processing（处理中）
    │
    ├─► Resolved（已处理）
    │
    ├─► Rejected（已拒绝）
    │
    └─► Closed（已关闭）
```

### 当前架构特点

1. **防刷保护**：通过 AntiSpamService 限制举报频率
2. **重复检测**：同一用户对同一内容只能有一个待处理举报
3. **内容验证**：举报前验证被举报内容是否存在


## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IReportRepository.cs
public interface IReportRepository
{
    /// <summary>
    /// 添加举报
    /// </summary>
    Task<Report> AddAsync(Report report);
    
    /// <summary>
    /// 获取举报详情
    /// </summary>
    Task<ReportDto?> GetByIdAsync(long reportId);
    
    /// <summary>
    /// 检查是否已举报
    /// </summary>
    Task<bool> HasPendingReportAsync(ReportType type, long targetId, string reporterId);
    
    /// <summary>
    /// 获取用户的举报列表
    /// </summary>
    Task<(IReadOnlyList<ReportDto> Items, int Total)> GetUserReportsAsync(
        string reporterId, ReportSearchReq request);
    
    /// <summary>
    /// 更新举报状态
    /// </summary>
    Task UpdateStatusAsync(long reportId, ReportStatus status, string? processorId, string? processNote);
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IReportDomainService.cs
public interface IReportDomainService
{
    /// <summary>
    /// 验证举报操作
    /// </summary>
    Task ValidateReportAsync(ReportType type, long targetId, string reporterId);
    
    /// <summary>
    /// 创建举报实体
    /// </summary>
    Report CreateReport(CreateReportReq request, string reporterId, string? ipAddress, string? userAgent);
}

// ZhiCoreCore/Domain/Services/ReportDomainService.cs
public class ReportDomainService : IReportDomainService
{
    private readonly IReportRepository _reportRepository;
    private readonly AppDbContext _dbContext;
    private readonly ISnowflakeIdService _snowflakeIdService;
    
    public async Task ValidateReportAsync(ReportType type, long targetId, string reporterId)
    {
        // 1. 检查是否已有待处理的举报
        var hasPending = await _reportRepository.HasPendingReportAsync(type, targetId, reporterId);
        if (hasPending)
            throw new InvalidOperationException("您已经举报过该内容");
        
        // 2. 验证被举报内容是否存在
        var exists = type switch
        {
            ReportType.Post => await _dbContext.Posts.AnyAsync(p => p.Id == targetId && !p.Deleted),
            ReportType.Topic => await _dbContext.Topics.AnyAsync(t => t.Id == targetId && !t.Deleted),
            ReportType.Comment => await _dbContext.Comments.AnyAsync(c => c.Id == targetId && !c.Deleted),
            _ => false
        };
        
        if (!exists)
            throw new InvalidOperationException($"被举报的内容不存在");
    }
    
    public Report CreateReport(CreateReportReq request, string reporterId, string? ipAddress, string? userAgent)
    {
        return new Report
        {
            Id = _snowflakeIdService.NextId(),
            Type = request.Type,
            TargetId = request.TargetId,
            Reason = request.Reason,
            Description = request.Description,
            ReporterId = reporterId,
            Status = ReportStatus.Pending,
            CreateTime = DateTimeOffset.UtcNow,
            IpAddress = ipAddress,
            UserAgent = userAgent
        };
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Content/IReportApplicationService.cs
public interface IReportApplicationService
{
    Task<long> CreateReportAsync(CreateReportReq request, string reporterId, string? ipAddress = null, string? userAgent = null);
    Task<ReportDto?> GetReportByIdAsync(long reportId);
    Task<bool> HasReportedAsync(ReportType type, long targetId, string reporterId);
    Task<ReportListResp> GetUserReportsAsync(string reporterId, ReportSearchReq request);
}

// ZhiCoreCore/Application/Content/ReportApplicationService.cs
public class ReportApplicationService : IReportApplicationService
{
    private readonly IReportDomainService _domainService;
    private readonly IReportRepository _reportRepository;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<ReportApplicationService> _logger;
    
    public async Task<long> CreateReportAsync(CreateReportReq request, string reporterId, string? ipAddress = null, string? userAgent = null)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Report, reporterId, request.TargetId.ToString(), ipAddress);
        
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 验证举报操作
        await _domainService.ValidateReportAsync(request.Type, request.TargetId, reporterId);
        
        // 3. 创建举报
        var report = _domainService.CreateReport(request, reporterId, ipAddress, userAgent);
        
        // 4. 保存举报
        await _reportRepository.AddAsync(report);
        
        // 5. 记录操作（用于防刷统计）
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Report, reporterId, request.TargetId.ToString(), ipAddress);
        
        // 6. 发布领域事件
        await _eventDispatcher.DispatchAsync(new ReportCreatedEvent
        {
            ReportId = report.Id,
            ReporterId = reporterId,
            Type = request.Type,
            TargetId = request.TargetId,
            Reason = request.Reason
        });
        
        _logger.LogInformation("用户 {ReporterId} 举报了 {Type} {TargetId}，举报ID: {ReportId}", 
            reporterId, request.Type, request.TargetId, report.Id);
        
        return report.Id;
    }
}
```

## 领域事件

### ReportCreatedEvent

```csharp
public record ReportCreatedEvent : DomainEventBase
{
    public override string EventType => nameof(ReportCreatedEvent);
    
    public long ReportId { get; init; }
    public string ReporterId { get; init; } = string.Empty;
    public ReportType Type { get; init; }
    public long TargetId { get; init; }
    public ReportReason Reason { get; init; }
}
```

### 事件处理器

```csharp
// 通知管理员有新举报
public class ReportCreatedEventHandler : IDomainEventHandler<ReportCreatedEvent>
{
    private readonly INotificationService _notificationService;
    
    public async Task HandleAsync(ReportCreatedEvent @event, CancellationToken ct = default)
    {
        // 通知管理员
        await _notificationService.NotifyAdminsAsync(
            "新举报待处理",
            $"收到新的{@event.Type}举报，举报ID: {@event.ReportId}");
    }
}
```

## 数据模型

```csharp
public class Report
{
    public long Id { get; set; }              // 雪花 ID
    public ReportType Type { get; set; }      // 举报类型
    public long TargetId { get; set; }        // 被举报内容 ID
    public ReportReason Reason { get; set; }  // 举报原因
    public string? Description { get; set; }  // 详细描述
    public string ReporterId { get; set; }    // 举报人 ID
    public ReportStatus Status { get; set; }  // 举报状态
    public string? ProcessorId { get; set; }  // 处理人 ID
    public string? ProcessNote { get; set; }  // 处理备注
    public DateTimeOffset CreateTime { get; set; }
    public DateTimeOffset? ProcessTime { get; set; }
    public string? IpAddress { get; set; }
    public string? UserAgent { get; set; }
    public bool Deleted { get; set; }
}
```

## 举报原因枚举

```csharp
public enum ReportReason
{
    [Description("垃圾广告")]
    Spam = 1,
    
    [Description("色情低俗")]
    Pornography = 2,
    
    [Description("违法违规")]
    Illegal = 3,
    
    [Description("侵权内容")]
    Infringement = 4,
    
    [Description("人身攻击")]
    PersonalAttack = 5,
    
    [Description("虚假信息")]
    FalseInfo = 6,
    
    [Description("其他")]
    Other = 99
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 4 | 4 |
| 职责分离 | 一般 | 清晰分层 |
| 领域事件 | 无（注释掉） | 有 |
| 可测试性 | 中 | 高 |
