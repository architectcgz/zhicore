# 博客系统点赞功能设计文档

## 一、系统架构

### 1.1 技术栈
- **缓存层**: Redis (Set + Hash)
- **消息队列**: RabbitMQ
- **数据库**: PostgreSQL
- **原子性保证**: Lua脚本

### 1.2 架构特点
- **读写分离**: Redis承载高频读写，数据库作为持久化存储
- **异步写入**: 使用RabbitMQ异步批量写入数据库，降低RT
- **原子性**: 通过Lua脚本保证Redis多键操作的原子性
- **最终一致性**: Redis和数据库通过消息队列保证最终一致

---

## 二、数据结构设计（已统一前缀 blog:，与 comment_system 保持一致，数据库为准）

### 2.1 Redis数据结构（评论点赞）

#### 2.1.1 点赞关系 (Set)
```
Key: blog:comments:{commentId}:likes        -- 使用 HashTag {commentId}，便于集群同槽
Type: Set
Value: Set<userId>
TTL: 永不过期（不设过期，避免重复计数风险）
用途: 防重复点赞、快速查询点赞状态
```

**示例**:
```bash
SADD blog:comments:{123}:likes "user001"
SISMEMBER blog:comments:{123}:likes "user001"  # 1=已点赞
```

#### 2.1.2 评论统计 (Hash，由消费者维护)
```
Key: blog:comments:{commentId}:stats
Type: Hash
Fields:
  - like_count: 点赞数
  - reply_count: 回复数（只维护根评论）
TTL: 永不过期（手动失效/重建）
用途: 存储评论统计，计数仅由消费者更新，入口不直接改计数
```

**示例**:
```bash
HGETALL blog:comments:{123}:stats
HINCRBY blog:comments:{123}:stats like_count 1  # 仅消费者执行
```

#### 2.1.3 点赞防刷/限流键（由 AntiSpamRateLimitMiddleware 使用）
- 计数：`blog:antispam:like:count:{actor}:{period}` （固定窗口，分钟/小时/天）
- 冷却：`blog:antispam:like:cooldown:{actor}:{targetId}`
- 同一目标频控：`blog:antispam:like:target:{actor}:{targetId}`

> actor 形式：`user:{userId}` 或 `ip:{ip}`；Redis 不可用时中间件放行但记录日志。

### 2.2 数据库表结构

#### 2.2.1 点赞记录表 (post_likes)
```sql
CREATE TABLE post_likes (
    post_id BIGINT NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    create_time TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_likes_user_id ON post_likes(user_id);
```

#### 2.2.2 文章统计表 (post_stats)
```sql
CREATE TABLE post_stats (
    post_id BIGINT PRIMARY KEY,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    update_time TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### 2.3 RabbitMQ消息结构

#### 2.3.1 点赞记录消息
```json
{
    "PostId": 123,
    "UserId": "user001",
    "LikedAt": "2025-12-24T10:30:00Z",
    "IsUnlike": false
}
```

**字段说明**:
- `PostId`: 文章ID
- `UserId`: 用户ID
- `LikedAt`: 操作时间
- `IsUnlike`: false=点赞, true=取消点赞

#### 2.3.2 队列配置
```
队列名称: post_like_batch_queue
持久化: true
独占: false
自动删除: false
预取数量: 200 (可配置)
批处理大小: 100条/批次
批处理超时: 5秒
```

---

## 三、核心流程设计

### 3.1 点赞流程（评论，数据库为准）

```
用户点赞请求
    ↓
1. 防刷/限流：AntiSpamRateLimitMiddleware (固定窗口+冷却+同目标频控)
    ↓
2. 校验评论存在、拉黑关系
    ↓
3. Redis SADD blog:comments:{cid}:likes userId   # 仅写关系，立即返回状态
    ↓
4. 发布 MQ (CommentLikeBatchMessage IsUnlike=false)
    ↓
5. 返回成功（点赞数延迟由消费者更新）
```

### 3.2 取消点赞流程（评论，数据库为准）

```
用户取消点赞请求
    ↓
1. 防刷/限流：AntiSpamRateLimitMiddleware（包含取消后的冷却写入）
    ↓
2. 校验评论存在
    ↓
3. Redis SREM blog:comments:{cid}:likes userId   # 仅写关系
    ↓
4. 发布 MQ (CommentLikeBatchMessage IsUnlike=true)
    ↓
5. 返回成功（点赞数延迟由消费者更新）
```

### 3.3 消费者批量写入流程（保持与 comment_system 一致的“数据库为准”）

```
RabbitMQ Consumer
    ↓
1. 收集批次 (默认50条，或超时3s)
    ↓
2. 过滤无效评论 & 去重 (commentId+userId)
    ↓
3. 事务：
    ├─ 批量插入 CommentLikes（忽略已存在）
    ├─ 批量删除取消点赞记录
    └─ 原子更新 comment_stats.like_count（防负数）
    ↓
4. 提交事务
    ↓
5. 异步更新 Redis Hash: HINCRBY blog:comments:{cid}:stats like_count ±count
    ↓
6. 热度更新入队（comments:hot:urgent 等）
    ↓
7. ACK 所有消息；失败 NACK 重入队
```

### 3.3 消费者批量写入流程

```
RabbitMQ Consumer
    ↓
1. 接收消息并缓存到批次
    ↓
2. 达到批次大小(100条)或超时(5秒)
    ↓
3. 开始批量处理
    ↓
4. 去重处理 (相同postId+userId只保留最后一条)
    ↓
5. 查询数据库已存在记录 (批量查询)
    ↓
6. 过滤已存在记录
    ↓
7. 开启数据库事务
    ├─ 7.1 批量插入点赞记录 (BulkInsert)
    ├─ 7.2 批量删除取消点赞记录 (SQL IN)
    ├─ 7.3 原子更新post_stats.like_count
    └─ 7.4 提交事务
    ↓
8. ACK所有消息
    ↓
9. 异步更新Redis缓存 (补偿机制)
```

**批量处理优势**:
- 减少数据库连接开销
- 提高吞吐量 (100条/批次)
- 事务保证原子性

---

## 四、一致性保证

### 4.1 Redis与数据库一致性

#### 4.1.1 写入流程（数据库为唯一真实源）
```
用户操作
    ↓
Redis SADD/SREM（仅关系，立即返回状态，不改计数）
    ↓
发布MQ消息
    ↓
消费者事务写DB + 更新comment_stats + HINCRBY Redis
```

**特点**:
- **数据库为准**：计数只由消费者修改，防止 Redis > DB。
- **最终一致**：入口快速返回，计数 1-2 秒内同步。
- **容错性**：MQ 持久化、失败 NACK 重试，可对账重建。

#### 4.1.2 异常场景处理

**场景1: MQ消息发布失败**
```
Redis已更新关系
    ↓
MQ发布失败 → 记录日志
    ↓
降级写DB（需同时修正 comment_stats.like_count，当前实现缺省，需补齐或定期对账）
```

**场景2: 消费者处理失败**
```
事务回滚（DB未改）
    ↓
消息NACK重入队
    ↓
自动重试
```

**场景3: Redis缓存失效/丢失**
```
查询点赞状态未命中
    ↓
回源DB查询并回填 SADD
    ↓
计数可由定时对账/消费者写入修复
```

### 4.2 幂等性设计

#### 4.2.1 Redis层幂等
- **Lua脚本**: SADD天然幂等，重复执行返回0
- **限流**: 每秒10次，防止重复提交

#### 4.2.2 数据库层幂等
- **主键约束**: (post_id, user_id) 联合主键
- **INSERT时自动去重**: 违反约束则忽略
- **批量处理去重**: 消费者先查询已存在记录

### 4.3 数据对账

#### 4.3.1 对账策略
```sql
-- 修复脚本: 从数据库重建Redis缓存
SELECT post_id, COUNT(*) as like_count
FROM post_likes
GROUP BY post_id;
```

**对账时机**:
- 每日凌晨定时对账
- 手动触发修复
- 监控告警后修复

---

## 五、性能优化

### 5.1 Redis优化

#### 5.1.1 批量操作
```csharp
// 批量获取点赞数 (Pipeline)
var batch = redis.CreateBatch();
var tasks = postIds.Select(id => 
    batch.HashGetAsync($"blog:posts:stats:{id}", "like_count")
).ToList();
batch.Execute();
```

#### 5.1.2 内存缓存 (MemoryCache)
```
文章基本信息 (PostBasicInfo)
    ↓
MemoryCache (滑动过期10分钟)
    ↓
减少Redis/DB查询
```

### 5.2 数据库优化

#### 5.2.1 批量写入
```csharp
// EF Core BulkInsert
await dbContext.PostLikes.AddRangeAsync(newLikes);
await dbContext.SaveChangesAsync();
```

#### 5.2.2 索引优化
```sql
-- 用户维度查询
CREATE INDEX idx_post_likes_user_id ON post_likes(user_id);

-- 文章维度查询 (主键已覆盖)
PRIMARY KEY (post_id, user_id)
```

### 5.3 RabbitMQ优化

#### 5.3.1 预取配置
```
prefetchCount: 200
批处理大小: 100
```
**说明**: 预取200条，批量处理100条，保证消费者始终有消息处理

#### 5.3.2 批量ACK
```csharp
// 处理成功后批量ACK
foreach (var tag in deliveryTags) {
    await channel.BasicAckAsync(tag, multiple: false);
}
```

---

## 六、防刷与限流

### 6.1 防刷策略 (AntiSpamService)

#### 6.1.1 时间窗口限流
```
规则: 用户每5分钟最多点赞50次
实现: Redis Sorted Set (ZADD + ZCOUNT)
```

#### 6.1.2 冷却时间
```
规则: 点赞/取消点赞需间隔1秒
实现: Redis String (GET + SET + EXPIRE)
```

### 6.2 限流策略 (RateLimitService)

#### 6.2.1 滑动窗口限流
```
规则: 每用户每秒最多10次请求
实现: Redis INCR + EXPIRE
```

```csharp
// 限流检查
var count = await redis.StringIncrementAsync($"blog:ratelimit:like:{userId}");
if (count == 1) {
    await redis.KeyExpireAsync(key, TimeSpan.FromSeconds(1));
}
if (count > 10) {
    throw new BusinessException("请求过于频繁");
}
```

---

## 七、监控与告警

### 7.1 关键指标

#### 7.1.1 性能指标
- **点赞RT**: p50 < 50ms, p99 < 200ms
- **MQ积压**: < 1000条
- **消费延迟**: < 10秒

#### 7.1.2 一致性指标
- **Redis-DB差异率**: < 0.1%
- **MQ消息失败率**: < 0.01%

### 7.2 日志记录

#### 7.2.1 关键日志
```csharp
// 点赞成功
logger.LogInformation("用户点赞成功 - postId: {PostId}, userId: {UserId}, newCount: {Count}");

// MQ发布失败
logger.LogError("发布点赞消息失败 - postId: {PostId}, userId: {UserId}");

// 批量写入
logger.LogInformation("批量写入点赞 - 点赞: {LikeCount}条, 取消: {UnlikeCount}条");
```

---

## 八、降级方案

### 8.1 Redis不可用
```
Redis异常
    ↓
降级到数据库模式
    ↓
直接操作数据库 (性能下降，但功能正常)
```

### 8.2 RabbitMQ不可用
```
MQ异常
    ↓
记录日志告警
    ↓
定时对账修复
```

---

## 九、设计优势

### 9.1 高性能
- ✅ Redis承载高频读写，RT < 100ms
- ✅ 异步批量写入，减少DB压力
- ✅ Lua脚本原子操作，避免多次网络IO

### 9.2 高可用
- ✅ Redis降级到数据库
- ✅ RabbitMQ消息持久化
- ✅ 消费者重试机制

### 9.3 数据一致性
- ✅ Lua脚本保证Redis原子性
- ✅ 数据库事务保证ACID
- ✅ 定时对账修复不一致

### 9.4 可扩展性
- ✅ 消费者可水平扩展
- ✅ Redis可集群部署
- ✅ RabbitMQ支持分片队列

---

## 十、未来优化方向

1. **Redis Cluster**: 点赞数据分片存储
2. **热点文章缓存**: 针对热点文章单独优化
3. **实时统计流**: 使用Flink实时聚合点赞数
4. **AI防刷**: 机器学习识别异常点赞行为
