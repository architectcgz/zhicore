# MQ技术选型：统一使用RabbitMQ

## 文档概述

**决策时间**：2024-12-24  
**决策内容**：评论点赞系统从Redis Stream迁移至RabbitMQ，统一全系统的消息队列技术栈  
**影响范围**：评论点赞、评论回复消息处理  

---

## 背景

### 当前架构现状

**混合MQ架构**：
```
文章点赞系统
├─ 客户端：SADD检查去重 (Redis)
├─ 发布端：发送MQ消息 (RabbitMQ)
└─ 消费端：批量写入数据库 + 更新Redis缓存 (PostLikeBatchWriteConsumer)

评论点赞系统（旧方案）
├─ 客户端：SADD检查去重 (Redis)
├─ 发布端：XADD发送消息 (Redis Stream)
└─ 消费端：批量写入数据库 + 更新Redis缓存 + 触发热度更新 (CommentLikeStreamConsumer)

通知系统
├─ 发布端：发送MQ消息 (RabbitMQ)
└─ 消费端：SignalR推送 (RabbitMQ消费者)
```

**问题**：
- 维护两套消息队列系统（RabbitMQ + Redis Stream）
- 技术栈不统一，增加开发和运维成本
- Redis承担过多职责（缓存 + 索引 + 消息队列）

---

## 技术对比分析

### 1. 架构统一性

| 维度 | Redis Stream | RabbitMQ |
|------|-------------|----------|
| **技术栈统一** | ❌ 需同时维护两套MQ | ✅ 全系统统一MQ |
| **开发规范** | ❌ 不同的消息格式和消费模式 | ✅ 统一的消息格式和批量处理逻辑 |
| **监控告警** | ❌ 需要分别配置监控 | ✅ 统一的监控面板 |
| **运维成本** | ⚠️ 需要学习两套工具 | ✅ 只需掌握RabbitMQ |

### 2. 职责分离与故障隔离

**Redis Stream方案的问题**：
```
Redis承担多重角色：
├─ 缓存层：评论内容、统计数据、点赞关系
├─ 索引层：热度ZSet、时间ZSet、点赞Set
└─ 消息队列：点赞消息Stream ← 职责过重
```

**风险**：
- Redis故障 → 缓存失效 + 消息队列中断（影响面过大）
- Redis内存压力 → 可能需要淘汰Stream数据（消息丢失风险）
- Redis重启 → 消息可能丢失（取决于AOF配置）

**RabbitMQ方案的优势**：
```
职责清晰分离：
Redis：专注缓存和索引
├─ 评论内容缓存
├─ 统计数据缓存
└─ 热度ZSet索引

RabbitMQ：专注消息队列
└─ 点赞/回复/通知等所有异步消息
```

**优势**：
- ✅ **单一职责**：每个组件专注自己的核心功能
- ✅ **故障隔离**：Redis故障不影响消息队列，RabbitMQ故障不影响缓存
- ✅ **独立扩展**：可根据缓存和MQ各自的负载独立调优

### 3. 可靠性与持久化

| 特性 | Redis Stream | RabbitMQ |
|------|-------------|----------|
| **持久化机制** | AOF（异步写入） | 磁盘持久化（同步写入） |
| **消息丢失风险** | ⚠️ Redis重启可能丢失部分消息 | ✅ 持久化队列几乎不丢消息 |
| **死信队列** | ❌ 需手动实现 | ✅ 原生DLX支持 |
| **消息重试** | ❌ 需手动ACK管理 | ✅ 自动重试 + 退避策略 |
| **集群方案** | Redis Cluster（复杂） | RabbitMQ Cluster（成熟） |
| **生产实践** | 相对较少 | 行业标准，经验丰富 |

**Redis Stream的持久化问题**：
```bash
# AOF配置
appendfsync everysec  # 每秒同步一次

# 风险：
# 1. Redis崩溃 → 最多丢失1秒的点赞消息
# 2. AOF重写期间 → 可能丢失更多消息
# 3. 内存不足时 → Stream可能被淘汰
```

**RabbitMQ的可靠性保证**：
```csharp
// 持久化队列
channel.QueueDeclare(
    queue: "comment_like_batch_queue",
    durable: true,        // 队列持久化
    exclusive: false,
    autoDelete: false
);

// 持久化消息
var properties = channel.CreateBasicProperties();
properties.Persistent = true;  // 消息持久化

// 发布者确认
channel.ConfirmSelect();
channel.BasicPublish(...);
channel.WaitForConfirmsOrDie();  // 确保消息已写入磁盘
```

### 4. 运维与监控

| 维度 | Redis Stream | RabbitMQ |
|------|-------------|----------|
| **监控工具** | redis-cli / RedisInsight | RabbitMQ Management UI（功能强大） |
| **消息查询** | XRANGE（需知道Stream Key） | Web UI可视化查询 |
| **队列状态** | XINFO（命令行） | 实时图表、告警 |
| **消息追踪** | ❌ 需自己实现 | ✅ Tracing插件 |
| **死信管理** | ❌ 手动处理 | ✅ DLX自动路由 |
| **告警集成** | ⚠️ 需自定义脚本 | ✅ Prometheus集成 |

**RabbitMQ Management UI优势**：
- 实时查看队列长度、消费速率、消息积压
- 一键查看死信队列、重发消息
- 可视化消费者连接状态
- 内置性能监控图表

### 5. 性能对比

**数据局部性分析**：

```
Redis Stream方案：
1. XREADGROUP 读取消息 (Redis)              ← 1次Redis调用
2. 更新数据库 (PostgreSQL)                  ← 1次DB调用
3. HINCRBY 更新统计 (Redis)                 ← 1次Redis调用
4. ZADD 加入热度队列 (Redis)                ← 1次Redis调用
5. XACK 确认消息 (Redis)                    ← 1次Redis调用
总计：4次Redis调用 + 1次DB调用

RabbitMQ方案：
1. 接收MQ消息 (RabbitMQ)                   ← 1次MQ调用
2. 更新数据库 (PostgreSQL)                  ← 1次DB调用
3. HINCRBY 更新统计 (Redis)                 ← 1次Redis调用
4. ZADD 加入热度队列 (Redis)                ← 1次Redis调用
5. ACK确认消息 (RabbitMQ)                  ← 1次MQ调用
总计：2次Redis调用 + 2次MQ调用 + 1次DB调用
```

**性能差异**：
- Redis Stream方案：4次本地Redis调用
- RabbitMQ方案：2次Redis调用 + 2次MQ调用
- **网络延迟差异**：< 1ms（内网环境）
- **数据库操作才是瓶颈**：批量写入耗时远大于MQ调用

**实际QPS分析**：
```
评论系统QPS：< 1万/秒
RabbitMQ处理能力：10万+ QPS
性能富余：10倍以上

结论：性能差异可忽略，可靠性收益远大于微小的性能损耗
```

### 6. 开发与维护成本

**代码复用性**：

当前已有的RabbitMQ消费者：
```
ZhiCore-consumer-worker/Consumers/
├─ PostLikeBatchWriteConsumer.cs        ✅ 文章点赞（已实现）
├─ CommentBatchWriteConsumer.cs         ✅ 评论批量写入（已实现）
├─ AntiSpamActionConsumer.cs            ✅ 反垃圾处理（已实现）
└─ ... (10+ RabbitMQ消费者)
```

**迁移评估**：
```csharp
// 评论点赞可以复用文章点赞的批量处理架构
// 只需增加热度队列更新逻辑

public class CommentLikeBatchWriteConsumer : BackgroundService
{
    // 复用 PostLikeBatchWriteConsumer 的批量处理逻辑
    // 批量大小、超时时间、重试策略完全一致
    
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // 1. 批量接收消息（与PostLikeBatchWriteConsumer相同）
        // 2. 批量去重、过滤（与PostLikeBatchWriteConsumer相同）
        // 3. 事务写入数据库（与PostLikeBatchWriteConsumer相同）
        // 4. 异步更新Redis缓存（与PostLikeBatchWriteConsumer相同）
        // 5. 额外：ZADD加入热度更新队列 ← 唯一差异
    }
}
```

**开发成本**：
- ✅ 80%代码可复用
- ✅ 统一的配置管理（RabbitMqConfig）
- ✅ 统一的监控和日志格式
- ✅ 团队已熟悉RabbitMQ开发模式

---

## 决策依据

### 核心理由

#### 1. 架构统一性 > 微小的性能差异

**统一后的架构**：
```
全系统异步消息 → RabbitMQ
├─ 文章点赞批量写入
├─ 评论点赞批量写入 ← 迁移
├─ 评论回复批量写入
├─ 通知推送
├─ 反垃圾处理
└─ ... (所有异步事件)

Redis → 专注缓存和索引
├─ 文章/评论内容缓存
├─ 统计数据缓存
├─ 点赞关系Set
├─ 热度排序ZSet
└─ 时间排序ZSet
```

**收益**：
- 开发效率提升（统一的消息格式和处理模式）
- 运维成本降低（单一监控面板、告警系统）
- 故障排查简化（统一的日志格式、追踪系统）

#### 2. 可靠性 > 性能

**评论点赞场景分析**：
- 用户点赞后期望状态立即生效（Redis Set已实现）
- 点赞数更新可容忍1-2秒延迟（用户体验可接受）
- **消息绝对不能丢失**（否则点赞数永久不一致）

**可靠性对比**：
```
Redis Stream丢失风险：
- Redis崩溃：丢失最近1秒的消息（appendfsync everysec）
- 内存不足：Stream可能被LRU淘汰
- AOF重写：极端情况下丢失更多消息

RabbitMQ丢失风险：
- 持久化队列 + 持久化消息 + 发布者确认
- 几乎不丢消息（除非磁盘完全损坏）
```

#### 3. 职责分离 > 数据局部性

**职责分离的价值**：
- Redis故障不影响消息队列（业务隔离）
- 可独立调优Redis和RabbitMQ（性能优化）
- 未来可独立替换组件（架构灵活性）

**数据局部性的实际价值**：
- 减少跨系统调用：< 1ms网络延迟
- 相比数据库批量写入（50-100ms），影响可忽略

#### 4. 行业最佳实践

**RabbitMQ的行业地位**：
- 消息队列事实标准（与Kafka并列）
- 丰富的生产实践经验
- 成熟的运维工具链
- 大量的故障排查文档

**Redis Stream的定位**：
- 适合简单的消息传递场景
- 适合与Redis强耦合的轻量级MQ需求
- **不是专业的消息队列系统**

---

## 迁移方案

### 1. 消息格式设计

**定义评论点赞消息**：
```csharp
// ZhiCore-shared/Messages/CommentLikeBatchMessage.cs
public class CommentLikeBatchMessage
{
    public long CommentId { get; set; }
    public string UserId { get; set; }
    public DateTimeOffset LikedAt { get; set; }
    public bool IsUnlike { get; set; }  // true=取消点赞, false=点赞
    public long PostId { get; set; }     // 用于热度更新时定位ZSet
}
```

### 2. 发布端改造

**旧代码（Redis Stream）**：
```csharp
// ZhiCore-core/Services/Impl/CommentLikeService.cs
await _redisStreamService.PublishCommentLikeAsync(new
{
    cid = commentId,
    uid = userId,
    delta = 1,
    timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
});
```

**新代码（RabbitMQ）**：
```csharp
// ZhiCore-core/Services/Impl/CommentLikeService.cs
await _eventPublisher.PublishAsync(
    RabbitMqConstants.Exchanges.CommentLike,
    RabbitMqConstants.RoutingKeys.CommentLikeBatch,
    new CommentLikeBatchMessage
    {
        CommentId = commentId,
        UserId = userId,
        PostId = postId,  // 从评论信息中获取
        LikedAt = DateTimeOffset.UtcNow,
        IsUnlike = false
    }
);
```

### 3. 消费端改造

**新建消费者（复用文章点赞架构）**：
```csharp
// ZhiCore-consumer-worker/Consumers/CommentLikeBatchWriteConsumer.cs
public class CommentLikeBatchWriteConsumer : BackgroundService
{
    private readonly ConcurrentBag<CommentLikeRecord> _likeBatch = new();
    private readonly ConcurrentBag<CommentUnlikeRecord> _unlikeBatch = new();
    
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // 1. 批量消费消息（BatchSize=100, Timeout=2秒）
        // 2. 去重、过滤已存在记录
        // 3. 事务批量写入数据库
        // 4. 异步更新Redis缓存
        // 5. 异步添加到热度更新队列（额外逻辑）
        // 6. 批量ACK消息
    }
    
    private async Task FlushBatchAsync(CancellationToken stoppingToken)
    {
        // ... 批量写入数据库 ...
        
        // 额外：触发热度更新
        foreach (var like in processedLikes)
        {
            await _database.SortedSetAddAsync(
                RedisKeys.CommentCache.HotUpdateUrgentQueue,
                like.CommentId,
                DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            );
        }
    }
}
```

### 4. 配置管理

**RabbitMQ队列配置**：
```json
// appsettings.json
{
  "RabbitMq": {
    "Queues": {
      "CommentLikeBatchQueue": {
        "QueueName": "comment_like_batch_queue",
        "Exchange": "comment_like_exchange",
        "RoutingKey": "comment.like.batch",
        "Durable": true,
        "PrefetchCount": 100,
        "AutoAck": false
      }
    }
  }
}
```

**消费者配置**：
```csharp
// ZhiCore-shared/Config/CommentLikeBatchConfig.cs
public class CommentLikeBatchConfig
{
    public int BatchSize { get; set; } = 100;
    public int BatchTimeoutSeconds { get; set; } = 2;
    public int MaxRetryCount { get; set; } = 3;
    public int RetryDelaySeconds { get; set; } = 5;
}
```

### 5. 迁移步骤

**阶段1：双写验证（1周）**
```
1. 部署新的RabbitMQ消费者（不启动）
2. 修改发布端，同时发送到Redis Stream + RabbitMQ
3. 启动RabbitMQ消费者，观察日志
4. 对比两个消费者的处理结果（数据一致性验证）
```

**阶段2：灰度切换（1周）**
```
1. 配置开关，部分流量走RabbitMQ
2. 监控RabbitMQ消费者性能指标
3. 逐步提升RabbitMQ流量比例（10% → 50% → 100%）
4. 确认无异常后，停止Redis Stream消费者
```

**阶段3：清理下线（1天）**
```
1. 移除Redis Stream相关代码
2. 删除CommentLikeStreamConsumer
3. 删除CommentLikeStreamConfig
4. 更新文档
```

---

## 监控指标

### RabbitMQ监控

| 指标 | 目标值 | 告警阈值 | 说明 |
|------|--------|----------|------|
| 队列消息积压 | < 1000 | > 5000 | 消费速度跟不上生产速度 |
| 消费延迟 | < 2秒 | > 10秒 | 消息处理延迟 |
| 消费者连接数 | >= 1 | = 0 | 消费者异常断线 |
| 死信队列消息数 | 0 | > 10 | 消息处理失败 |
| 消息确认率 | > 99.9% | < 99% | 消息丢失风险 |

### 业务监控

| 指标 | 目标值 | 告警阈值 | 说明 |
|------|--------|----------|------|
| 点赞成功率 | > 99.9% | < 99% | 客户端点赞失败 |
| 数据库写入成功率 | > 99.99% | < 99.9% | 数据库异常 |
| Redis缓存命中率 | > 95% | < 90% | 缓存失效 |
| 热度队列大小 | < 1000 | > 5000 | 热度更新积压 |

---

## 风险评估

### 迁移风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 双写数据不一致 | 中 | 低 | 双写验证阶段对比结果，发现问题立即修复 |
| RabbitMQ性能不足 | 高 | 极低 | 压测验证，RabbitMQ处理能力远超需求 |
| 消息格式兼容性 | 低 | 中 | 定义明确的Message Schema，版本控制 |
| 消费者Bug导致数据丢失 | 高 | 低 | 灰度切换，完善的死信队列处理 |

### 回滚方案

**如果迁移出现问题**：
```
1. 立即切换流量回Redis Stream（配置开关）
2. 停止RabbitMQ消费者
3. 检查RabbitMQ死信队列，手动重放消息
4. 分析问题，修复后重新灰度
```

**回滚时间**：< 5分钟（配置热更新）

---

## 结论

### 最终决策

**✅ 统一使用RabbitMQ作为全系统消息队列**

### 核心收益

1. **架构统一**：降低开发和运维成本，提升团队效率
2. **职责分离**：Redis专注缓存，RabbitMQ专注消息队列，故障隔离
3. **可靠性提升**：RabbitMQ的持久化和重试机制更成熟
4. **运维简化**：统一的监控面板、告警系统、故障排查
5. **代码复用**：80%代码可复用文章点赞的批量处理逻辑

### 权衡取舍

**牺牲**：
- 微小的性能差异（< 1ms，可忽略）
- 需要维护RabbitMQ基础设施（已有）

**获得**：
- 架构一致性（长期收益）
- 更高的可靠性（核心价值）
- 更简单的运维（降本增效）

### 适用场景总结

**何时使用RabbitMQ**：
- ✅ 需要高可靠性的消息传递
- ✅ 跨系统/跨服务的消息路由
- ✅ 复杂的消息重试和死信处理
- ✅ 团队已有RabbitMQ运维经验
- ✅ 需要统一的消息队列管理

**何时使用Redis Stream**：
- ✅ 简单的日志收集（允许丢失）
- ✅ 实时数据流处理（时效性 > 可靠性）
- ✅ 与Redis强耦合的临时消息
- ✅ 无额外MQ基础设施的小型项目
- ⚠️ **不适合核心业务消息**

---

## 参考资料

### 相关文档
- [评论系统设计文档](./comment/comment_system.md)
- [点赞系统设计文档](./like/like_sys.md)
- [RabbitMQ官方文档](https://www.rabbitmq.com/documentation.html)
- [Redis Stream官方文档](https://redis.io/docs/data-types/streams/)

### 变更记录
| 日期 | 变更内容 | 责任人 |
|------|---------|--------|
| 2024-12-24 | 初始版本，决策统一使用RabbitMQ | - |
