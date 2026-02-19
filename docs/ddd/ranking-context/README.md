# Ranking Context（排名上下文）

## 概述

排名上下文负责热度计算和排行榜功能，包括文章热度、创作者热度、话题热度等。

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| PostHotnessService | IPostHotnessService | 6 | 文章热度计算 |
| CreatorHotnessService | ICreatorHotnessService | 5 | 创作者热度计算 |
| RankingService | IRankingService | 5 | 排行榜管理 |
| TieredHotnessService | ITieredHotnessService | 6 | 分层热度计算 |
| HotTierManager | IHotTierManager | 3 | 热度层级管理 |
| LazyHotnessCalculator | ILazyHotnessCalculator | 3 | 懒加载热度计算 |
| HotnessChangeFilter | IHotnessChangeFilter | 2 | 热度变化过滤 |
| HotnessResultCache | IHotnessResultCache | 2 | 热度结果缓存 |
| CachedPostHotnessService | IPostHotnessService | 4 | 热度缓存装饰器 |

## 热度算法

### 文章热度公式

```
热度 = (点赞数 × 3 + 评论数 × 5 + 收藏数 × 4 + 阅读数 × 0.1) × 时间衰减因子
```

### 时间衰减因子

```csharp
// 使用指数衰减，半衰期为 7 天
double GetTimeDecayFactor(DateTimeOffset publishedAt)
{
    var ageInDays = (DateTimeOffset.UtcNow - publishedAt).TotalDays;
    var halfLife = 7.0;
    return Math.Pow(0.5, ageInDays / halfLife);
}
```

## 领域事件订阅

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| PostPublishedEvent | Post Context | 初始化文章热度 |
| PostLikedEvent | Post Context | 更新热度分数 |
| PostFavoritedEvent | Post Context | 更新热度分数 |
| PostViewedEvent | Post Context | 更新热度分数 |
| CommentCreatedEvent | Comment Context | 更新热度分数 |
| UserFollowedEvent | User Context | 更新创作者热度 |

## 分层热度架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Hot Tier (热门层)                           │
│                    Top 1000 文章                                 │
│                    实时计算，高精度                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Warm Tier (温热层)                           │
│                    Top 10000 文章                                │
│                    定期计算，中等精度                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Cold Tier (冷门层)                           │
│                    其他文章                                      │
│                    懒加载计算，低精度                             │
└─────────────────────────────────────────────────────────────────┘
```

## Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `ranking:post:hot` | ZSet | 文章热度排行榜 | 永久 |
| `ranking:creator:hot` | ZSet | 创作者热度排行榜 | 永久 |
| `ranking:topic:hot` | ZSet | 话题热度排行榜 | 永久 |
| `hotness:post:{postId}` | String | 文章热度缓存 | 5 分钟 |
| `hotness:tier:{tier}` | Set | 各层级文章ID | 永久 |

## 事件处理器

```csharp
public class PostLikedEventHandler : IDomainEventHandler<PostLikedEvent>
{
    private readonly IPostHotnessService _hotnessService;
    
    public async Task HandleAsync(PostLikedEvent @event, CancellationToken ct)
    {
        var delta = @event.IsLike ? 3 : -3; // 点赞权重为 3
        await _hotnessService.UpdatePostHotnessAsync(@event.PostId, delta);
    }
}
```


## 详细文档

- [PostHotnessService 详细设计](./post-hotness-service.md) ✅
- [CreatorHotnessService 详细设计](./creator-hotness-service.md) ✅
- [RankingService 详细设计](./ranking-service.md) ✅
