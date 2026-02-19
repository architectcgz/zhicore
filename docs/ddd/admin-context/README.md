# Admin Context（管理上下文）

## 概述

管理上下文负责平台管理功能，包括内容审核、用户管理、举报处理等。

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| AdminPostService | IAdminPostService | 5 | 文章管理（下架、恢复） |
| AdminUserService | IAdminUserService | 4 | 用户管理（封禁、解封） |
| AdminReportService | IAdminReportService | 4 | 举报处理 |
| AdminStatsService | IAdminStatsService | 3 | 平台统计 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| PostTakenDownEvent | 文章下架 | SearchIndexHandler, NotificationHandler |
| PostRestoredEvent | 文章恢复 | SearchIndexHandler |
| UserLockedEvent | 用户封禁 | SessionHandler, NotificationHandler |
| UserUnlockedEvent | 用户解封 | NotificationHandler |
| ReportProcessedEvent | 举报处理完成 | NotificationHandler |

## 目录结构

```
BlogCore/
├── Application/Admin/
│   ├── IAdminPostApplicationService.cs
│   ├── AdminPostApplicationService.cs
│   ├── IAdminUserApplicationService.cs
│   ├── AdminUserApplicationService.cs
│   ├── IAdminReportApplicationService.cs
│   └── AdminReportApplicationService.cs
├── Domain/
│   └── EventHandlers/Admin/
│       ├── PostTakenDownEventHandler.cs
│       ├── UserLockedEventHandler.cs
│       └── ReportProcessedEventHandler.cs
└── Infrastructure/
    └── AdminAuditLog.cs
```

## 文章管理

### 下架流程

```
管理员操作 → 更新文章状态为 TakenDown
                │
                ▼
        发布 PostTakenDownEvent
                │
        ┌───────┴───────┐
        ▼               ▼
SearchIndexHandler  NotificationHandler
删除搜索索引        通知作者
```

### 下架原因

| 原因代码 | 说明 |
|---------|------|
| Spam | 垃圾内容 |
| Illegal | 违法内容 |
| Inappropriate | 不当内容 |
| Copyright | 版权问题 |
| Other | 其他原因 |

## 用户管理

### 封禁流程

```
管理员操作 → 更新用户状态为 Locked
                │
                ▼
        发布 UserLockedEvent
                │
        ┌───────┴───────┐
        ▼               ▼
SessionHandler      NotificationHandler
撤销所有会话        通知用户
```

### 封禁类型

| 类型 | 说明 |
|------|------|
| Temporary | 临时封禁（指定时长） |
| Permanent | 永久封禁 |

## 审计日志

所有管理操作都会记录审计日志：

```csharp
public class AdminAuditLog
{
    public long Id { get; set; }
    public string AdminId { get; set; }
    public string Action { get; set; }        // TakeDownPost, LockUser, etc.
    public string TargetType { get; set; }    // Post, User, Comment
    public string TargetId { get; set; }
    public string Reason { get; set; }
    public string Details { get; set; }       // JSON 格式的详细信息
    public DateTimeOffset CreatedAt { get; set; }
}
```

## 详细文档

- [AdminPostService 详细设计](./admin-post-service.md)
- [AdminUserService 详细设计](./admin-user-service.md)
