# Content Context（内容上下文）

## 概述

内容上下文负责内容管理相关功能，包括话题管理、举报处理等。

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| TopicService | ITopicService | 4 | 话题管理 |
| ReportService | IReportService | 5 | 举报管理 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| TopicCreatedEvent | 话题创建 | SearchIndexHandler |
| TopicStatsUpdatedEvent | 话题统计更新 | RankingHandler |
| ReportCreatedEvent | 举报创建 | AdminNotificationHandler |

### 订阅的事件

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| PostPublishedEvent | Post Context | 更新话题文章数 |
| PostDeletedEvent | Post Context | 更新话题文章数 |

## 目录结构

```
ZhiCoreCore/
├── Application/Content/
│   ├── ITopicApplicationService.cs
│   ├── TopicApplicationService.cs
│   ├── IReportApplicationService.cs
│   └── ReportApplicationService.cs
├── Domain/
│   ├── Repositories/
│   │   ├── ITopicRepository.cs
│   │   └── IReportRepository.cs
│   └── EventHandlers/Content/
│       ├── TopicCreatedEventHandler.cs
│       └── ReportCreatedEventHandler.cs
└── Infrastructure/Repositories/
    ├── TopicRepository.cs
    └── ReportRepository.cs
```

## 话题功能

### 话题类型

| 类型 | 说明 |
|------|------|
| Official | 官方话题，由管理员创建 |
| User | 用户话题，由用户创建 |
| Hot | 热门话题，系统自动标记 |

### 话题统计

```csharp
public class TopicStats
{
    public long TopicId { get; set; }
    public int PostCount { get; set; }      // 文章数
    public int FollowerCount { get; set; }  // 关注数
    public int ViewCount { get; set; }      // 浏览数
    public double HotnessScore { get; set; } // 热度分数
}
```

## 举报功能

### 举报类型

| 类型 | 说明 |
|------|------|
| Post | 举报文章 |
| Comment | 举报评论 |
| User | 举报用户 |
| Message | 举报私信 |

### 举报状态

| 状态 | 说明 |
|------|------|
| Pending | 待处理 |
| Processing | 处理中 |
| Resolved | 已处理 |
| Rejected | 已驳回 |

### 举报流程

```
用户举报 → 创建举报记录 → 发布 ReportCreatedEvent
                              │
                              ▼
                    AdminNotificationHandler
                    通知管理员处理
                              │
                              ▼
                    管理员审核 → 处理/驳回
                              │
                              ▼
                    发布 ReportProcessedEvent
```

## 详细文档

- [TopicService 详细设计](./topic-service.md) ✅
- [ReportService 详细设计](./report-service.md) ✅
