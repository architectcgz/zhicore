# 表设计
## 1. 评论表 (comments)

| 字段           | 类型       | 描述                                 | 索引              |
| ------------ | -------- | ---------------------------------- | ----------------- |
| `cid`        | INT (PK) | 评论的唯一标识                            | 主键索引              |
| `root_id`    | INT      | 顶级评论的ID，顶级评论的 `root_id` 与 `cid` 相同 | 索引 (`root_id`)    |
| `parent_id`  | INT      | 父评论ID，如果是顶级评论则为 NULL               | 索引 (`parent_id`)  |
| `user_id`    | INT      | 评论作者的用户ID                          | 索引 (`user_id`)    |
| `content`    | TEXT     | 评论内容                               | （不需要索引）           |
| `created_at` | DATETIME | 评论创建时间                             | 索引 (`created_at`) |
| `updated_at` | DATETIME | 评论最后更新时间                           | （不需要索引）           |
| `deleted` | BOOLEAN  | 是否删除（软删除）                          | （不需要索引）           |

## 2. 评论统计表 (comment_stats)

| 字段            | 类型           | 描述            | 索引    |
| ------------- | ------------ | ------------- | ------- |
| `cid`         | INT (PK, FK) | 关联的评论ID（逻辑外键） | 主键索引    |
| `like_count`  | INT          | 点赞数           | （不需要索引） |
| `reply_count` | INT          | 回复数           | （不需要索引） |
|`hot_score`| INT| 评论热度| 不需要索引
| `created_at`  | DATETIME     | 统计信息创建时间      | （不需要索引） |
| `updated_at`  | DATETIME     | 统计信息最后更新时间    | （不需要索引） |

不使用物理外键，使用逻辑外键，在系统逻辑中手动处理一致性。
物理外键在插入/更新时，db会检查外键约束，造成数据库的负担，在并发高时尤为明显。
此外分布式系统中，数据通常分布在多个数据库中。物理外键只适用于单个数据库实例，而在跨数据库、跨服务的架构中，数据关系需要通过应用层来维护一致性。

<!-- 评论第一页查询时查询parent_id = NULL的评论，order by  -->

# 评论统计架构设计
为了保证评论数据的实时性以及在高并发情况下的稳定性，评论的统计部分都先保存到redis，使用后台同步/消息队列的方式将状态同步到db
使用Hash保存评论的统计
```bash
HSET comments:{cid}:stats like_count {count}
HSET comments:{cid}:stats reply_count {count}
```

**键名规范**：`comments:{cid}:stats`
- 使用复数 `comments`（统一风格）
- 使用 `{cid}` Hash Tag（Redis Cluster支持）
- 语义清晰

## 使用Hash的原因
1.Redis 的 Hash 结构适合存储与一个唯一标识（比如 cid）相关的多个字段
```bash
HINCRBY comments:{cid}:stats like_count 1
HINCRBY comments:{cid}:stats reply_count 1
```
更新字段都是O(1)时间复杂度的
2.Redis 的 Hash 在存储少量字段时相对于 String 类型的多个键值对更加节省内存。
3.查询方便
通过HGETALL可以方便的将所有字段以及对应的值取出
```bash
HGETALL comments:{cid}:stats
```

## 评论点赞流程
1.将评论点赞关系添加到 Redis Set 中，确保每个人只能点赞一次
```bash
SADD comments:{cid}:likes {uid}  # 将评论点赞关系添加到 Redis Set 中，确保唯一性
```
2.发送评论点赞信息到消息队列（如 Kafka 或 RabbitMQ或Redis Stream）。
```bash
{
  "type": "like",
  "cid": "12345",
  "uid": "user1",
  "delta": 1,
  "timestamp": 1734321600000
}
```
3.后台消费者处理点赞操作并更新数据库，确保评论点赞数/点赞关系一致。
```bash
后台消费者从消息队列中消费点赞消息。
更新数据库中的评论点赞数：消费者根据消息中的 cid 和 uid 更新数据库中的评论点赞数。
UPDATE comments SET like_count = like_count + 1 WHERE cid = {cid};
INSERT INTO comment_likes (cid, uid) VALUES ({cid}, {uid}) ON DUPLICATE KEY UPDATE uid = {uid};
更新 Redis 中的评论点赞数：消费者在 Redis 中使用 HINCRBY 增量更新评论的点赞数。
更新 Redis 中的评论点赞关系：消费者确保评论的点赞关系正确存储在 Redis Set 中，防止用户重复点赞。
HINCRBY comments:{cid}:stats like_count 1  # 增量更新 Redis 中评论点赞数
SADD comments:{cid}:likes {uid}  # 确保点赞关系的唯一性
```
4.更新评论的热度信息，并通过批量更新方式提升性能。

```bash
ZADD comments:hot_score {热度分数} {cid}  # 更新评论的热度分数
```

### 点赞流程架构选择

#### ❌ 方案A：客户端直接更新Redis（已废弃）
**流程**：
1. 客户端：SADD + HINCRBY（立即更新Redis计数）
2. 客户端：发送MQ消息
3. 消费者：更新数据库

**存在的严重问题**：

**问题1：数据库与Redis长期不一致**
- 消息队列处理失败时，Redis显示100次点赞，数据库只有99次
- Redis重启/过期后从数据库重建，用户看到点赞数突然减少（100→95）
- 违背了"数据库是唯一真实数据源"的原则

**问题2：Set与Hash不同步导致重复计数**
```bash
# 去重用的Set
comments:{cid}:likes → Set {uid1, uid2}  # TTL = 1小时

# 计数用的Hash  
comments:{cid}:stats → Hash {like_count: 2}  # TTL = 2小时

时间线：
T0: 用户A点赞 → Set添加A, Hash=1 ✅
T1(1小时后): Set过期删除 ⚠️
T2: 用户A再次点赞
    → SADD返回true（Set重建，A"首次"添加）
    → Hash +1 = 2  ⚠️ 重复计数！
    → MQ消息发送
T3: 消费者处理
    → INSERT违反唯一约束失败 ❌
    → 数据库仍是1次，Redis显示2次
```

**问题3：缓存初始化逻辑有缺陷**
- 只在newCount==1时检查数据库
- 无法处理"缓存过期后重新计数"的场景
- 例如：真实99次点赞，缓存过期后从1开始重新计数

---

#### ✅ 方案B：数据库为准（最终一致性）【当前采用】
**流程**：
1. 客户端：SADD检查去重（快速返回点赞状态）
2. 客户端：发送MQ消息
3. 客户端：返回成功（此时Redis计数不变）
4. 消费者：事务更新数据库 + HINCRBY更新Redis

**优点**：
- ✅ 数据库是唯一真实数据源（Single Source of Truth）
- ✅ Redis可以随时从数据库安全重建
- ✅ 不会出现Redis计数 > 数据库的情况
- ✅ 消费者批量处理，降低数据库压力

**权衡**：
- ⚠️ 点赞数延迟1-2秒更新（但点赞状态立即生效，用户体验可接受）

**实现细节**：
- 点赞关系Set仍然客户端立即更新（用于去重和状态显示）
- 点赞计数Hash由消费者更新（保证与数据库一致）
- 消费者处理失败时，Redis计数不会错误增加

---

### 回复数更新架构

**采用相同的"数据库为准"方案**：

说明：`reply_count` 只维护根评论（`root_id == cid`）的回复数；非根评论不维护回复数（只维护点赞数）。

**流程**：
```
1. 客户端：发表回复
   ↓
2. 插入数据库（comment表）
   ↓
3. 发送MQ消息（Redis Stream）
   ↓
4. 消费者：
   - 更新根评论的reply_count（数据库+Redis）
   - 更新文章的comment_count
```

**为什么不在客户端直接更新Redis？**
- 与点赞数相同的原因：保证数据库是唯一真实数据源
- 避免消费者失败导致的数据不一致
- 统一的架构更易维护

**权衡**：
- 回复数显示有1-2秒延迟（用户可接受）
- 回复内容立即可见（用户体验不受影响）

---

### 消息队列技术选型：RabbitMQ

**选择RabbitMQ的理由**：
1. ✅ **成熟可靠**：企业级消息队列，久经考验
2. ✅ **消息持久化**：支持消息和队列持久化，保证消息不丢失
3. ✅ **灵活路由**：支持多种Exchange类型（Direct、Topic、Fanout）
4. ✅ **消费者确认**：ACK机制保证消息至少被消费一次
5. ✅ **死信队列**：处理失败消息，支持重试和告警
6. ✅ **跨服务通信**：适合微服务架构，支持多语言客户端
7. ✅ **监控完善**：自带管理界面，方便监控和运维

**适用场景**：
- 需要可靠的消息传递保证
- 微服务架构下的异步通信
- 需要灵活的消息路由策略
- 需要完善的监控和管理工具

**当前场景**：评论系统需要可靠的消息传递和灵活的扩展性，RabbitMQ完全满足需求

---

### 点赞/回复消息结构

**点赞消息**：
```json
{
  "type": "like",
  "cid": 12345,
  "uid": "user123",
  "delta": 1,
  "timestamp": 1735023600000
}
```

**回复消息**：
```json
{
  "type": "reply",
  "cid": 12346,
  "parent_cid": 12345,
  "root_cid": 12300,
  "post_id": 100,
  "timestamp": 1735023600000
}
```

**字段说明**：
- `type`：操作类型（like/reply/unlike等）
- `cid`：评论ID
- `uid`：用户ID（点赞用）
- `parent_cid`：父评论ID（回复用）
- `root_cid`：根评论ID（回复用）
- `post_id`：文章ID（回复用）
- `delta`：增量值（+1点赞/-1取消）
- `timestamp`：消息时间戳

### RabbitMQ架构设计

#### Exchange和Queue配置

**Exchange类型**：Direct Exchange（直接路由）

**队列定义**：
```
Exchange: comment.events
  ├─ Queue: comment.like.queue (routing key: comment.like)
  ├─ Queue: comment.reply.queue (routing key: comment.reply)
  └─ Queue: comment.unlike.queue (routing key: comment.unlike)
```

**队列属性**：
- **持久化**：durable=true（队列重启后不丢失）
- **消息持久化**：deliveryMode=2（消息持久化到磁盘）
- **自动删除**：autoDelete=false（消费者断开后队列保留）
- **死信队列**：配置DLX处理失败消息

#### 死信队列配置

**死信Exchange**：`comment.events.dlx`

**死信队列**：
```
Exchange: comment.events.dlx
  ├─ Queue: comment.like.dlq
  ├─ Queue: comment.reply.dlq
  └─ Queue: comment.unlike.dlq
```

**触发条件**：
- 消息被拒绝（basic.reject/basic.nack）且requeue=false
- 消息TTL过期
- 队列达到最大长度

#### 发布消息

**点赞消息发布**：
```csharp
var message = new CommentLikeMessage
{
    Type = "like",
    Cid = 123125,
    Uid = "user1",
    Delta = 1,
    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
};

// 发布到Exchange，指定routing key
channel.BasicPublish(
    exchange: "comment.events",
    routingKey: "comment.like",
    basicProperties: properties, // deliveryMode=2持久化
    body: Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message))
);
```

#### 消费消息

**消费者配置**：
```csharp
// 设置预取数量（QoS）
channel.BasicQos(prefetchSize: 0, prefetchCount: 10, global: false);

// 创建消费者
var consumer = new EventingBasicConsumer(channel);
consumer.Received += async (model, ea) =>
{
    try
    {
        var body = ea.Body.ToArray();
        var message = JsonSerializer.Deserialize<CommentLikeMessage>(
            Encoding.UTF8.GetString(body)
        );
        
        // 处理消息
        await ProcessLikeMessageAsync(message);
        
        // 手动确认
        channel.BasicAck(deliveryTag: ea.DeliveryTag, multiple: false);
    }
    catch (Exception ex)
    {
        // 处理失败，拒绝消息并发送到死信队列
        channel.BasicNack(
            deliveryTag: ea.DeliveryTag, 
            multiple: false, 
            requeue: false // 不重新入队，进入死信队列
        );
        
        _logger.LogError(ex, "处理点赞消息失败: {Message}", message);
    }
};

// 开始消费
channel.BasicConsume(
    queue: "comment.like.queue",
    autoAck: false, // 手动确认
    consumer: consumer
);
```

**消费者特性**：
- ✅ **手动ACK**：确保消息处理成功后才确认
- ✅ **预取限制**：prefetchCount=10，控制并发数
- ✅ **失败重试**：失败消息进入死信队列，可配置延迟重试
- ✅ **幂等处理**：消费者需要处理重复消息（至少一次语义）



# 评论热度

## 热度计算公式

```bash
热度 = Math.min((like_count * like_weight + reply_count * reply_weight) / (1 + time_decay_factor * (current_time - created_at) / decay_scaling_factor), Integer.MAX_VALUE)

参数配置：
time_decay_factor = 0.0001  # 时间衰减因子
like_weight = 10            # 点赞权重
reply_weight = 5            # 回复权重
scale_factor = 1000         # 分数缩放因子
```

**公式说明**：
- **点赞和回复**：点赞权重更高（10 vs 5），鼓励高质量内容
- **时间衰减**：随时间推移，热度自然下降，保证新内容有机会
- **防溢出**：使用Math.min限制最大值

---

## 热度存储

**键名规范**：`posts:{postId}:comments:hot`

```bash
# ZSet存储，score = 热度分数
ZADD posts:{postId}:comments:hot 
  8532 123  # 评论123的热度8532
  7821 124
  6543 125
```

**用途**：
- 热评排序（ZREVRANGE获取TopN）
- 热度对比（ZSCORE查询单个评论热度）

---

## 热度更新策略：双队列方案

热度更新面临**两个核心需求**：
1. **实时性**：新点赞/回复的评论需要立即更新热度
2. **时间衰减**：所有评论都需要定期更新（即使没有新互动）

### ❌ 旧方案的问题

#### 方案A：用Set存储待更新评论
```bash
SADD comments:dirty_hot {cid}
SPOP comments:dirty_hot 100  # 定时随机取100个
```
**缺陷**：
- SPOP随机抽取，某些cid可能永远抽不到（无公平性保证）
- 无法区分热门评论和冷门评论的更新优先级
- 极端情况下可能导致部分评论热度永久过时

#### 方案B：用ZSet按添加次数排序
```bash
ZINCRBY comments:dirty_hot 1 {cid}  # 每次点赞score+1
ZREVRANGE comments:dirty_hot 0 99   # 取score最高的100个
```
**缺陷**：
- 热门评论score越来越高，频繁占据更新队列
- 冷门评论score低，可能永远排不到前列（饿死问题）
- 忽略了时间衰减需求（冷门评论也需要定期更新）

**根本问题**：旧方案只考虑了实时性，忽略了时间衰减和公平性。

---

### ✅ 新方案：双队列架构设计

#### 队列1：高优先级队列（实时更新）
**键名**：`comments:hot:urgent`

**数据结构**：ZSet
```bash
# score = 加入时间戳（保证FIFO）
# value = 评论ID
ZADD comments:hot:urgent {timestamp} {cid}
```

**触发时机**：点赞/回复消费者处理消息时
```bash
# 消费者更新数据库后
ZADD comments:hot:urgent {当前时间戳} {cid}
```

**特点**：
- ✅ 按时间戳排序，保证先进先出（FIFO）
- ✅ 同一cid多次点赞会覆盖（自动去重）
- ✅ 高频处理，保证实时性

---

#### 队列2：低优先级队列（定期全量）
**键名**：`comments:hot:scheduled`

**数据结构**：ZSet
```bash
# score = 上次更新时间戳
# value = 评论ID
ZADD comments:hot:scheduled {last_update_time} {cid}
```

**用途**：
- 记录每个评论的最后更新时间
- 定期扫描需要时间衰减的评论

**特点**：
- ✅ 保证所有评论定期更新（时间衰减）
- ✅ 即使冷门评论也不会被遗忘
- ✅ 按上次更新时间排序，公平处理

---

### 定时任务设计

#### 任务A：高优先级处理（高频）

**执行周期**：每10秒

**逻辑**：
```python
while True:
    # 1. 获取最早的100个待更新评论（按时间戳升序）
    cids = ZRANGE comments:hot:urgent 0 99
    
    if cids.empty():
        sleep(10)
        continue
    
    # 2. 批量获取统计数据
    stats = BatchGetCommentStats(cids)
    
    # 3. 批量计算新热度
    new_scores = {}
    for cid in cids:
        stat = stats[cid]
        new_score = CalculateHotScore(
            like_count=stat.like_count,
            reply_count=stat.reply_count,
            create_time=stat.create_time
        )
        new_scores[cid] = new_score
    
    # 4. Pipeline批量更新Redis和数据库
    Pipeline {
        for cid, score in new_scores:
            # 更新热度索引
            ZADD posts:{post_id}:comments:hot {score} {cid}
            
            # 移除高优先级队列
            ZREM comments:hot:urgent {cid}
            
            # 更新低优先级队列（记录更新时间）
            ZADD comments:hot:scheduled {当前时间戳} {cid}
    }
    
    # 5. 批量更新数据库
    UPDATE comment_stats 
    SET hot_score = CASE cid 
        WHEN 123 THEN 8532
        WHEN 124 THEN 7821
        ...
    END
    WHERE cid IN (123, 124, ...)
    
    sleep(10)
```

**性能指标**：
- 处理延迟：< 10秒（新点赞的评论10秒内更新热度）
- 批量大小：100条/次
- 吞吐量：600条/分钟

---

#### 任务B：低优先级处理（低频）

**执行周期**：每5分钟

**逻辑**：
```python
while True:
    当前时间 = now()
    过期时间 = 当前时间 - 5分钟
    
    # 1. 找出5分钟前更新的评论（需要时间衰减）
    cids = ZRANGEBYSCORE comments:hot:scheduled 0 {过期时间} LIMIT 0 500
    
    if cids.empty():
        sleep(300)
        continue
    
    # 2-5. 同任务A的处理逻辑
    # 计算新热度 → 更新Redis → 更新数据库
    
    # 6. 更新scheduled队列的时间戳
    for cid in cids:
        ZADD comments:hot:scheduled {当前时间} {cid}
    
    sleep(300)
```

**性能指标**：
- 更新周期：5分钟（保证时间衰减生效）
- 批量大小：500条/次
- 覆盖范围：全部评论（无遗漏）

---

### 完整流程示例

```
时间线：

T0(00:00): 评论123被点赞
    → 消费者：ZADD comments:hot:urgent {T0} 123

T1(00:10): 定时任务A执行（第1次）
    → ZRANGE comments:hot:urgent 0 99  # 取到123
    → 计算新热度：8532
    → ZADD posts:10:comments:hot 8532 123
    → ZREM comments:hot:urgent 123
    → ZADD comments:hot:scheduled {T1} 123
    → 更新数据库：UPDATE comment_stats SET hot_score = 8532

T2-T6(00:10-05:00): 评论123没有新点赞
    → 不在urgent队列
    → 任务A不处理

T7(05:10): 定时任务B执行（第1次）
    → ZRANGEBYSCORE comments:hot:scheduled 0 {00:10}
    → 取到123（上次更新是00:10，已超5分钟）
    → 重新计算热度：8450（时间衰减，分数下降）
    → ZADD posts:10:comments:hot 8450 123
    → ZADD comments:hot:scheduled {T7} 123
    → 更新数据库

结果：
- 有点赞：10秒内实时更新
- 无点赞：5分钟定期衰减
- 所有评论都不会被遗忘
```

---

## 去重和优化

### 1. 自动去重
```bash
# 同一评论短时间内多次点赞
ZADD comments:hot:urgent {T1} 123  # 第1次
ZADD comments:hot:urgent {T2} 123  # 第2次（1秒后）

# ZSet自动覆盖，score变为T2
# 结果：只更新1次（避免重复计算）
```

### 2. 批量优化
```bash
# Pipeline减少网络往返
Pipeline {
    HGETALL comments:1:stats
    HGETALL comments:2:stats
    ...
    HGETALL comments:100:stats
}
# 100条评论，1次网络往返
```

### 3. 动态调整批量大小
```python
# 根据队列积压情况动态调整
urgent_size = ZCARD(comments:hot:urgent)

if urgent_size > 10000:
    batch_size = 500  # 积压严重，加速消费
elif urgent_size > 5000:
    batch_size = 200
else:
    batch_size = 100  # 正常处理
```

---

## 故障降级

### 场景1：Redis故障
```
1. 热度更新失败
   ↓
2. 记录失败日志
   ↓
3. 仅更新数据库
   ↓
4. Redis恢复后从数据库重建
```

### 场景2：数据库压力过大
```
1. 批量大小降低（500 → 100）
   ↓
2. 执行周期延长（10秒 → 30秒）
   ↓
3. 只更新Redis，异步同步数据库
```

---

## 监控指标

| 指标 | 目标 | 告警阈值 |
|------|------|----------|
| urgent队列大小 | < 1000 | > 5000 |
| scheduled队列大小 | 全部评论数 | - |
| 任务A延迟 | < 10秒 | > 30秒 |
| 任务B延迟 | < 5分钟 | > 10分钟 |
| 热度计算失败率 | < 0.1% | > 1% |

**监控命令**：
```bash
# 查看队列大小
ZCARD comments:hot:urgent
ZCARD comments:hot:scheduled

# 查看最老的待处理评论
ZRANGE comments:hot:urgent 0 0 WITHSCORES
ZRANGE comments:hot:scheduled 0 0 WITHSCORES
```

---

## 热度冷启动优化方案

**问题**：系统重启时，`comments:hot:scheduled` 队列为空，所有评论的时间衰减无法生效。

**❌ 不合理方案**：启动时将所有评论ID加载到scheduled队列
- 百万级评论会导致Redis内存爆炸
- 启动时间过长（可能数分钟）
- 大部分冷门评论永远不会被查看，无需缓存热度

**✅ 优化方案：按需增量加载**

### 策略1：评论被查询时自动加入队列
```python
# 在GetComment/GetCommentList等查询方法中
def GetCommentWithHotScore(cid):
    # 1. 查询热度
    hot_score = GetHotScore(cid)
    
    # 2. 检查是否在scheduled队列
    exists = ZSCORE comments:hot:scheduled {cid}
    
    # 3. 如果不存在，加入队列
    if exists is None:
        ZADD comments:hot:scheduled {当前时间戳} {cid}
    
    return hot_score
```

**优点**：
- ✅ 只有被查询的评论才进入队列（按需加载）
- ✅ 热门评论自然会被频繁查询，自动进入队列
- ✅ 冷门评论不占用队列空间

### 策略2：定时任务B的兜底逻辑
```python
# 任务B：低优先级处理
while True:
    过期时间 = 当前时间 - 5分钟
    cids = ZRANGEBYSCORE comments:hot:scheduled 0 {过期时间} LIMIT 0 500
    
    # 兜底：如果队列为空，从数据库加载活跃评论
    if cids.empty():
        # 只加载最近7天有互动的评论（而非全部）
        cids = SELECT cid FROM comment_stats 
                WHERE updated_at > NOW() - INTERVAL 7 DAY 
                LIMIT 1000
        
        # 批量加入scheduled队列
        for cid in cids:
            ZADD comments:hot:scheduled {当前时间戳} {cid}
    
    # 正常更新逻辑...
```

**优点**：
- ✅ 启动时只加载活跃评论（7天内有互动的）
- ✅ 数量可控（例如1000条，而非全部百万条）
- ✅ 保证核心评论的热度及时更新

### 策略3：新评论自动加入
```python
# 消费者处理点赞/回复消息时
def ProcessLikeMessage(msg):
    cid = msg.cid
    
    # 1. 更新数据库
    # 2. 更新Redis统计
    
    # 3. 加入两个队列
    ZADD comments:hot:urgent {当前时间戳} {cid}      # 高优先级
    ZADD comments:hot:scheduled {当前时间戳} {cid}   # 低优先级（兜底）
```

**综合方案**：
- 策略1 + 策略2 + 策略3 组合使用
- 保证热度更新覆盖全面且资源可控

---

# 评论缓存架构设计

评论缓存设计内容较多，已单独拆分至：[comment_cache.md](./comment_cache.md)

**主要内容包括**：
- 缓存分层结构（String、Hash、Set、ZSet、List）
- 差异化TTL策略
- 批量操作优化（Pipeline、Hash Tag）
- 缓存一致性保证（Cache-Aside、失效策略）
- 缓存预热策略
- 缓存三大问题防护（穿透、击穿、雪崩）

---

# 评论查询设计
## 查询根评论列表

### 基础查询
```sql
-- 查询文章的根评论（第一页）
SELECT * FROM post_comments 
WHERE post_id = {postId} AND parent_id IS NULL AND deleted = false
ORDER BY create_time DESC
LIMIT {pageSize};
```

### 游标分页优化

**问题**：传统的 `OFFSET/LIMIT` 分页在大偏移量时性能差（需要跳过前N条记录）。

**解决方案**：使用游标分页（Cursor-based Pagination）

#### 按时间排序的游标
```sql
-- 首页（无游标）
SELECT * FROM post_comments 
WHERE post_id = {postId} AND parent_id IS NULL AND deleted = false
ORDER BY create_time DESC, id DESC
LIMIT {pageSize};

-- 下一页（带游标）
SELECT * FROM post_comments 
WHERE post_id = {postId} AND parent_id IS NULL AND deleted = false
  AND (create_time < {cursorCreateTime} 
       OR (create_time = {cursorCreateTime} AND id < {cursorCommentId}))
ORDER BY create_time DESC, id DESC
LIMIT {pageSize};
```

**游标格式**：`Base64(create_time:comment_id)`

#### 按热度排序的游标
```sql
-- 首页（无游标）
SELECT c.*, s.hot_score 
FROM post_comments c
LEFT JOIN comment_stats s ON c.id = s.comment_id
WHERE c.post_id = {postId} AND c.parent_id IS NULL AND c.deleted = false
ORDER BY COALESCE(s.hot_score, 0) DESC, c.create_time DESC, c.id DESC
LIMIT {pageSize};

-- 下一页（带游标）
SELECT c.*, s.hot_score 
FROM post_comments c
LEFT JOIN comment_stats s ON c.id = s.comment_id
WHERE c.post_id = {postId} AND c.parent_id IS NULL AND c.deleted = false
  AND (COALESCE(s.hot_score, 0) < {cursorHotScore}
       OR (COALESCE(s.hot_score, 0) = {cursorHotScore} AND c.id < {cursorCommentId}))
ORDER BY COALESCE(s.hot_score, 0) DESC, c.create_time DESC, c.id DESC
LIMIT {pageSize};
```

**游标格式**：`Base64(hot_score:create_time:comment_id)`

**优势**：
- ✅ 性能稳定，不受数据量影响（O(1) vs O(N)）
- ✅ 避免数据重复/遗漏（传统分页在新增/删除数据时可能出现问题）
- ✅ 索引友好（利用 `(create_time, id)` 复合索引）

### 结构化缓存优化

**首页缓存策略**（仅缓存第一页，无游标时）：

```
缓存层次：
1. 尝试从 Redis ZSet 获取评论ID列表
   - 时间模式：posts:{postId}:comments:time
   - 热度模式：posts:{postId}:comments:hot
   
2. 批量从 Redis Hash 获取评论内容
   - comments:{cid}:obj (静态内容)
   - comments:{cid}:stats (点赞/回复数)
   
3. 缓存未命中 → 查询数据库 → 异步回填缓存
```

**适用场景**：
- ✅ 首页访问频繁（80%流量集中在第一页）
- ✅ 降低数据库压力
- ⚠️ 后续页走游标分页，直接查询数据库（访问量低，缓存收益小）

**TTL策略**：
- ZSet索引：1小时（可快速重建）
- 评论内容：详见 [comment_cache.md](./comment_cache.md)

## 查询评论回复
展开评论这里需要分页查询/游标分页。
**核心原则**：查询的条件是 `root_id = cid`，无论回复的是谁，root_id都是根评论id。

### 基础查询
```sql
-- 查询某个根评论下的所有回复
SELECT * FROM post_comments 
WHERE root_id = {rootId} AND id != {rootId} AND deleted = false
ORDER BY create_time ASC;
```

### 批量查询优化

**性能问题**：逐条查询关联数据（点赞状态、父评论作者）会产生 N+1 查询问题。

**解决方案**：批量查询 + 字典映射

#### 1. 批量获取点赞状态
```sql
-- 传统方式（N+1问题）
FOR EACH comment IN replies:
    SELECT EXISTS(SELECT 1 FROM comment_likes 
                  WHERE comment_id = {comment.id} AND user_id = {userId})

-- 优化方式（1次查询）
SELECT comment_id FROM comment_likes 
WHERE comment_id IN ({cid1}, {cid2}, ..., {cidN}) 
  AND user_id = {userId}
```

**实现**：
```csharp
// 批量查询，构建字典
var commentIds = replies.Select(c => c.Id).ToList();
var userLikes = await commentLikeService.AreCommentsLikedByUserAsync(commentIds, userId);
// 结果：Dictionary<long, bool>

// O(1)时间复杂度获取点赞状态
foreach (var reply in replies) {
    reply.IsLiked = userLikes.GetValueOrDefault(reply.Id, false);
}
```

#### 2. 批量获取父评论作者信息
```sql
-- 传统方式（N+1问题）
FOR EACH reply IN replies:
    SELECT u.* FROM post_comments c
    JOIN users u ON c.author_id = u.id
    WHERE c.id = {reply.parent_id}

-- 优化方式（1次查询）
SELECT c.id, u.id, u.nick_name, u.avatar_url
FROM post_comments c
JOIN users u ON c.author_id = u.id
WHERE c.id IN ({parentId1}, {parentId2}, ..., {parentIdN})
  AND c.deleted = false
```

**实现**：
```csharp
// 批量查询，构建字典
var parentIds = replies.Select(r => r.ParentId).Distinct().ToList();
var parentCommentsDict = await GetParentCommentsBatchAsync(parentIds);
// 结果：Dictionary<long, CommentWithAuthor>

// O(1)时间复杂度获取父评论信息
foreach (var reply in replies) {
    var parentComment = parentCommentsDict.GetValueOrDefault(reply.ParentId);
    reply.ParentAuthor = parentComment?.Author;
}
```

#### 3. 批量获取统计数据（点赞数、回复数）
```sql
-- 优化方式（1次查询）
SELECT comment_id, like_count, reply_count
FROM comment_stats
WHERE comment_id IN ({cid1}, {cid2}, ..., {cidN})
```

**性能对比**：

| 查询方式 | 数据库往返次数 | 100条回复耗时 |
|---------|--------------|--------------|
| 逐条查询 (N+1) | 1 + 100*3 = 301次 | ~3000ms |
| 批量查询 | 1 + 3 = 4次 | ~40ms |
| **性能提升** | **75倍** | **75倍** |

**关键技巧**：
- ✅ 使用 `IN` 查询批量获取数据
- ✅ 使用 `Dictionary` 映射，O(1)查找
- ✅ 使用 `Distinct()` 去重，减少查询量
- ✅ 使用 EF Core 的 `ToDictionaryAsync()` 直接构建字典

## @功能实现
回复的@功能通过parent_id先拿到评论，然后得到评论的发送者即可。

**批量获取**（已在上述优化中实现）：
- 批量查询父评论及作者信息
- 避免逐条查询父评论的 N+1 问题

## 根评论删除的影响
如果根评论被删除，其下所有评论都不可见（查询时自动过滤）。

# 评论删除设计
## 软删除策略
评论采用软删除（标记deleted字段为true），而非物理删除，保留数据用于审计和恢复。

## 删除逻辑
### 删除普通回复
- 只软删除目标评论本身（设置deleted = true）
- 文章评论数减1
- 更新根评论的回复数（-1）

### 删除根评论
- 只软删除目标评论本身（设置deleted = true）
- **重要**：文章评论数减去 `根评论数(1) + 该根评论下所有子回复数量`
- 原因：虽然子评论在数据库中仍存在，但查询时会被自动过滤（因为root_id指向的根评论已被删除），因此这些评论实际上都变得不可见
- 实现：
```sql
-- 统计需要减少的评论数
int decrementCount = 1; -- 根评论本身

-- 统计该根评论下所有有效的子回复
SELECT COUNT(*) FROM post_comments 
WHERE root_id = {rootId} AND id != {rootId} AND deleted = false;

-- 文章评论数递减
UPDATE post_stats 
SET comment_count = MAX(0, comment_count - decrementCount)
WHERE post_id = {postId};
```

## 事务保护与原子操作

### 事务隔离

**问题**：删除操作涉及多表更新，需要保证数据一致性。

**解决方案**：使用显式事务包裹所有数据库操作

```csharp
// 开启事务
await using var transaction = await context.Database.BeginTransactionAsync();
try
{
    // 1. 查询评论（加锁）
    var comment = await context.Comments
        .Where(c => c.Id == commentId && !c.Deleted)
        .FirstOrDefaultAsync();
    
    // 2. 权限检查
    if (comment.AuthorId != userId && postOwnerId != userId)
        throw new BusinessException(BusinessError.InsufficientPermission);
    
    // 3. 计算需要减少的评论数
    int decrementCount = 1;
    if (comment.ParentId == null) {
        var replyCount = await context.Comments
            .Where(c => c.RootId == commentId && c.Id != commentId && !c.Deleted)
            .CountAsync();
        decrementCount += replyCount;
    }
    
    // 4. 软删除评论
    comment.Deleted = true;
    comment.UpdateTime = DateTimeOffset.UtcNow;
    
    // 5. 原子更新文章评论数（避免并发问题）
    await context.PostStats
        .Where(ps => ps.PostId == comment.PostId)
        .ExecuteUpdateAsync(s => s
            .SetProperty(ps => ps.CommentCount, 
                        ps => Math.Max(0, ps.CommentCount - decrementCount))
            .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
    
    // 6. 保存所有更改
    await context.SaveChangesAsync();
    
    // 7. 提交事务
    await transaction.CommitAsync();
}
catch (Exception)
{
    // 回滚事务
    await transaction.RollbackAsync();
    throw;
}
```

**事务保证**：
- ✅ 原子性：所有操作要么全部成功，要么全部回滚
- ✅ 一致性：评论数始终等于实际可见评论数
- ✅ 隔离性：避免并发删除导致的数据不一致

### 原子更新（ExecuteUpdateAsync）

**传统方式的问题**（并发不安全）：
```csharp
// ❌ 读-改-写模式，存在并发竞争
var postStats = await context.PostStats.FirstAsync(ps => ps.PostId == postId);
postStats.CommentCount = Math.Max(0, postStats.CommentCount - decrementCount);
await context.SaveChangesAsync();

// 并发场景：
// T1: Read (count=100)  → Write (count=99)
// T2: Read (count=100)  → Write (count=99)
// 结果：两次删除，评论数只减1 ❌
```

**原子更新方式**（并发安全）：
```csharp
// ✅ 单条SQL直接更新，数据库保证原子性
await context.PostStats
    .Where(ps => ps.PostId == postId)
    .ExecuteUpdateAsync(s => s
        .SetProperty(ps => ps.CommentCount, 
                    ps => Math.Max(0, ps.CommentCount - decrementCount)));

// 等价SQL：
// UPDATE post_stats 
// SET comment_count = GREATEST(0, comment_count - {decrementCount})
// WHERE post_id = {postId}

// 并发场景：
// T1: UPDATE (count=100 → 99) ✅
// T2: UPDATE (count=99 → 98)  ✅
// 结果：正确递减
```

**优势**：
- ✅ **并发安全**：数据库层面的原子操作
- ✅ **性能更高**：1条SQL vs 2条SQL（Read + Write）
- ✅ **无需乐观锁**：不需要版本号字段

### 异步更新缓存

**策略**：数据库事务提交后，异步更新Redis缓存（不阻塞响应）

```csharp
// 提交事务后，触发异步任务
await transaction.CommitAsync();

// 异步更新Redis（不阻塞主流程）
_ = Task.Run(async () =>
{
    using var scope = serviceScopeFactory.CreateScope();
    var serviceProvider = scope.ServiceProvider;
    try
    {
        await serviceProvider.GetRequiredService<ICommentReplyStatsService>()
            .DecrementAsync(rootId);
    }
    catch (Exception ex)
    {
        logger.LogWarning(ex, "更新评论统计Redis缓存失败，将由后台同步修复");
        // 缓存更新失败不影响主流程
    }
});
```

**设计原则**：
- ✅ **数据库为准**：缓存失败不影响业务
- ✅ **异步更新**：不阻塞用户响应
- ✅ **容错机制**：缓存失败有后台同步兜底

## 查询过滤
所有评论查询都需要过滤：
- `deleted = false`（过滤已删除的评论）
- 如果是回复查询，还需确保root_id对应的根评论存在且未删除

## 权限控制
- 评论作者可以删除自己的评论
- 文章作者可以删除文章下的任何评论
- 管理员可以删除任何评论


