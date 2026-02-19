# Search Context（搜索上下文）

## 概述

搜索上下文负责全文搜索功能，基于 OpenSearch/Elasticsearch 实现，包括文章搜索、用户搜索、话题搜索等。

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| ElasticsearchSearchService | ISearchService | 6 | 全文搜索 |
| SearchIndexService | ISearchIndexService | 4 | 索引管理 |

## 搜索架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      SearchController                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SearchApplicationService                        │
│                    (Application Layer)                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ElasticsearchClient                            │
│                    (Infrastructure)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   OpenSearch / Elasticsearch                     │
└─────────────────────────────────────────────────────────────────┘
```

## 领域事件订阅

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| PostPublishedEvent | Post Context | 索引新文章 |
| PostUpdatedEvent | Post Context | 更新文章索引 |
| PostDeletedEvent | Post Context | 删除文章索引 |
| UserProfileUpdatedEvent | User Context | 更新用户索引 |

## 索引设计

### 文章索引 (posts)

```json
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "title": { 
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "content": { 
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "excerpt": { "type": "text" },
      "authorId": { "type": "keyword" },
      "authorName": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "topicId": { "type": "long" },
      "status": { "type": "keyword" },
      "publishedAt": { "type": "date" },
      "viewCount": { "type": "long" },
      "likeCount": { "type": "long" },
      "commentCount": { "type": "long" }
    }
  }
}
```

### 用户索引 (users)

```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "userName": { "type": "keyword" },
      "nickName": { 
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "bio": { "type": "text" },
      "followerCount": { "type": "long" },
      "postCount": { "type": "long" }
    }
  }
}
```

## 目录结构

```
BlogCore/
├── Application/Search/
│   ├── ISearchApplicationService.cs
│   └── SearchApplicationService.cs
├── Domain/
│   └── EventHandlers/Search/
│       ├── PostPublishedSearchHandler.cs
│       ├── PostUpdatedSearchHandler.cs
│       └── PostDeletedSearchHandler.cs
└── Infrastructure/Search/
    ├── ElasticsearchSearchService.cs
    ├── SearchIndexService.cs
    └── SearchConfig.cs
```

## 详细文档

- [SearchService 详细设计](./search-service.md)

## 降级策略

当 Elasticsearch 不可用时：

1. **搜索降级**：使用数据库 LIKE 查询（性能较差但可用）
2. **索引降级**：将索引任务放入队列，稍后重试

```csharp
public class ResilientSearchService : ISearchService
{
    private readonly ISearchService _primaryService;  // Elasticsearch
    private readonly ISearchService _fallbackService; // Database
    private readonly IResiliencePolicy _policy;
    
    public async Task<SearchResult> SearchPostsAsync(SearchQuery query)
    {
        return await _policy.ExecuteWithFallbackAsync(
            async () => await _primaryService.SearchPostsAsync(query),
            async () => await _fallbackService.SearchPostsAsync(query));
    }
}
```

## 配置

```json
{
  "Elasticsearch": {
    "Url": "http://localhost:9200",
    "DefaultIndex": "blog",
    "Username": "",
    "Password": "",
    "EnableDebugMode": false
  }
}
```
