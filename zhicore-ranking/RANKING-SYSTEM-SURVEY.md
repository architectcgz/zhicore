# ZhiCore 排行榜系统调研问卷

> **目的**: 全面了解当前排行榜系统的实现细节,为优化方案提供依据
> 
> **填写说明**: 
> - ✅ 表示已确认的信息(从代码中提取)
> - ❓ 表示需要你确认或补充的信息
> - 📝 表示需要你详细说明的部分

---

## A. Redis 与排行榜数据结构

### A.1 Redis 客户端与封装

**✅ 已确认**:
- Redis 客户端: `RedisTemplate<String, Object>` (Spring Data Redis + Lettuce)
- 序列化方式: 
  - Key: `StringRedisSerializer`
  - Value: `GenericJackson2JsonRedisSerializer` (Jackson)
- Lua 脚本执行: `DefaultRedisScript<Long>` (每次发送脚本正文)

**❓ 需要确认**:
- [ ] 是否考虑使用 `SCRIPT LOAD` + `EVALSHA` 来缓存脚本 SHA?
- [ ] 是否需要引入 Redisson 来支持更高级的分布式特性?
A：可以引入
### A.2 Key 命名与周期生成

**✅ 已确认**:

| 实体类型 | 总榜 Key | 日榜 Key | 周榜 Key | 月榜 Key |
|---------|---------|---------|---------|---------|
| Post | `ranking:posts:hot` | `ranking:posts:daily:2024-01-28` | `ranking:posts:weekly:4` | `ranking:posts:monthly:2024:01` |
| Creator | `ranking:creators:hot` | `ranking:creators:daily:2024-01-28` | ❌ 无 | ❌ 无 |
| Topic | `ranking:topics:hot` | `ranking:topics:daily:2024-01-28` | ❌ 无 | ❌ 无 |

**✅ 周榜计算逻辑**:
```java
// 使用 Java 默认 Locale 的 WeekFields
WeekFields weekFields = WeekFields.of(Locale.getDefault());
int weekNumber = LocalDate.now().get(weekFields.weekOfWeekBasedYear());
```

**❓ 需要确认**:
- [ ] 当前 `Locale.getDefault()` 在生产环境是什么? (中国: 周一开始, 美国: 周日开始)
A：目前的盛生产环境是UTC+8
- [ ] 是否需要固定为 `Locale.CHINA` 或 `Locale.US`?
- [ ] 时区是否固定? 当前使用系统默认时区还是 `Asia/Shanghai`?
- [ ] 跨年周(如 2024-W01 可能包含 2023-12-31)如何处理?

### A.3 TTL 策略

**✅ 已确认** (从 Lua 脚本和 README):

| 榜单类型 | TTL | 说明 |
|---------|-----|------|
| 总榜 (hot) | 永久 | 不设置过期时间 |
| 日榜 (daily) | 2 天 | `Duration.ofDays(2).getSeconds()` = 172800 秒 |
| 周榜 (weekly) | 14 天 | `Duration.ofDays(14).getSeconds()` = 1209600 秒 |
| 月榜 (monthly) | 365 天 | `Duration.ofDays(365).getSeconds()` = 31536000 秒 |

**❓ 需要确认**:
- [ ] 是否需要年榜 (yearly)?
需要
  - [ ] 如果需要, TTL 设置为多少? (建议: 永久或 3 年)
年榜不是每年都要更新吗？为什么不设置成一年
- [ ] Creator 和 Topic 的日榜 TTL 是否也是 2 天? (代码中未使用 Lua 脚本)

### A.4 TopN 目标与裁剪策略

**✅ 已确认**:
- 查询默认数量: 20
- 查询最大数量: 100
- 归档数量: 100 (配置项 `ranking.archive.limit`)

**❓ 需要确认**:
- [ ] ZSET 是否需要裁剪? 当前是否允许无限增长?
- [ ] 如果需要裁剪, TopK 保留多少? (建议: 300-1000)
- [ ] 裁剪策略:
  - [ ] 定时任务裁剪 (如每小时执行 `ZREMRANGEBYRANK`)
  - [ ] 写入时裁剪 (Lua 脚本中添加裁剪逻辑)
  - [ ] 不裁剪 (依赖 TTL 自动过期)

### A.5 查询接口

**✅ 已确认**:
- 查询方式: `ZREVRANGE` (降序) + `WITHSCORES`
- 返回类型: `List<HotScore>` (包含 entityId, score, rank)

**❓ 需要确认**:
- [ ] 是否需要同时返回 post 详情? (需要再查 DB/缓存)
可以返回post的基本信息
- [ ] 是否有"分数并列时的稳定排序"要求?
  - [ ] 如果需要, 排序依据: 更新时间? 文章 ID? 其他?
按更新时间排序
---

## B. 事件模型与分数计算

### B.1 事件来源与类型

**✅ 已确认** (从消费者代码和 `HotScoreCalculator`):

| 事件类型 | Topic | Tag | 消费者 | 权重 (delta) |
|---------|-------|-----|--------|-------------|
| 浏览 | `post-events` | `post-viewed` | `PostViewedRankingConsumer` | **1.0** |
| 点赞 | `post-events` | `post-liked` | `PostLikedRankingConsumer` | **5.0** |
| 取消点赞 | `post-events` | `post-unliked` | `PostUnlikedRankingConsumer` | **-5.0** (负数) |
| 收藏 | `post-events` | `post-favorited` | `PostFavoritedRankingConsumer` | **8.0** |
| 评论 | `comment-events` | `comment-created` | `CommentCreatedRankingConsumer` | **10.0** |

**权重配置** (来自 `HotScoreCalculator`):
```java
private static final double VIEW_WEIGHT = 1.0;
private static final double LIKE_WEIGHT = 5.0;
private static final double COMMENT_WEIGHT = 10.0;
private static final double FAVORITE_WEIGHT = 8.0;
```

**❓ 需要确认**:
- [ ] 是否有其他事件类型? (分享、转发、举报等)
- [ ] 是否有"取消收藏"事件? 如果有, 是否产生负增量?

### B.2 权重与计算方式

**✅ 已确认**:
- 使用 `HotScoreCalculator` 计算分数
- 应用时间衰减因子: `scoreDelta = baseDelta * timeDecay`
- 使用 `DoubleAdder` 存储分数 (支持浮点数)

**时间衰减算法** (指数衰减):
```java
// 半衰期模型：每过7天，热度衰减为原来的一半
private static final double HALF_LIFE_DAYS = 7.0;

public double calculateTimeDecay(LocalDateTime publishedAt) {
    long daysSincePublish = ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now());
    return Math.pow(0.5, daysSincePublish / HALF_LIFE_DAYS);
}
```

**文章热度公式**:
```
score = (views * 1 + likes * 5 + comments * 10 + favorites * 8) * timeDecay
```

**创作者热度公式** (无时间衰减):
```
score = followers * 2 + totalLikes * 1 + totalComments * 1.5 + postCount * 3
```

**❓ 需要确认**:
- [ ] 时间衰减参数是否需要可配置? (当前硬编码为 7 天半衰期)
需要可配置，后面会编辑成配置文件
- [ ] 是否需要支持"负分数"? (如举报、违规内容)
需要
- [ ] 创作者热度是否需要时间衰减?
这里需要你帮我考虑一下，我的经验不多


### B.3 幂等与重复

**✅ 已确认** (从 `PostLikeApplicationService` 和 `PostFavoriteApplicationService`):

**业务层幂等保证**:
1. **数据库层**: 使用复合主键 `PRIMARY KEY (post_id, user_id)` 保证唯一性
   ```sql
   CREATE TABLE post_likes (
       post_id BIGINT NOT NULL,
       user_id BIGINT NOT NULL,
       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (post_id, user_id)  -- 天然防重
   );
   ```

2. **应用层**: 双重检查机制
   ```java
   // 1. 先查 Redis (快速失败)
   if (redisTemplate.hasKey(likeKey)) {
       throw new BusinessException("已经点赞过了");
   }
   
   // 2. 事务中再查数据库 (防止并发)
   transactionTemplate.executeWithoutResult(status -> {
       if (likeRepository.exists(postId, userId)) {
           throw new BusinessException("已经点赞过了");
       }
       // 保存点赞记录
   });
   ```

3. **消息层**: 使用 `StatefulIdempotentHandler` 基于 `event.getEventId()` 去重

**结论**:
- ✅ like/favorite **已在业务层保证"一个用户一次"**
- ✅ unlike/unfavorite 也有相应的检查机制
- ✅ 即使消息重试, 也不会产生重复点赞/收藏

**❓ 需要确认**:
- [ ] view (浏览) 是否允许重复? 
  - [ ] 如果允许, 是否需要去重? (如同用户同文章 5min 只算一次)
  - [ ] 当前实现: view 事件没有业务层去重, 每次浏览都会产生事件
- [ ] 幂等 key (`StatefulIdempotentHandler`) 的 TTL 是多少? (防止 Redis 内存无限增长)
- [ ] 如果 MQ 消息重试, 排行榜服务的幂等性如何保证?
  - [ ] 当前: 基于 `eventId` 去重
  - [ ] 问题: 如果 `eventId` 相同但事件内容不同 (如先 like 后 unlike), 如何处理?

---

## C. ScoreBufferService 聚合实现细节

### C.1 scoreBuffer 的结构

**✅ 已确认**:
```java
private final ConcurrentHashMap<String, DoubleAdder> scoreBuffer = new ConcurrentHashMap<>();
```
- Key 格式: `"{entityType}:{entityId}"` (如 `"post:123456"`)
- Value: `DoubleAdder` (线程安全的累加器)

**❓ 需要确认**:
- [ ] scoreBuffer 是否包含时间桶信息? (当前没有)
- [ ] 是否需要记录事件发生时间? (用于跨天/跨周/跨月场景)
- [ ] 是否需要分片/分段锁来提高并发性能?

### C.2 flush 行为

**✅ 已确认**:
```java
@Scheduled(fixedDelayString = "${ranking.buffer.flush-interval:5000}")
public void flushToRedis() {
    // 1. 读取并清空缓冲区
    Map<String, Double> snapshot = new HashMap<>();
    scoreBuffer.forEach((key, adder) -> {
        double value = adder.sumThenReset();
        if (value != 0) {
            snapshot.put(key, value);
        }
    });
    
    // 2. 批量刷写到 Redis
    for (Map.Entry<String, Double> entry : snapshot.entrySet()) {
        // 解析 key, 调用对应的 increment 方法
    }
}
```

**❓ 需要确认**:
- [ ] flush 是否会并发执行? (Spring @Scheduled 默认单线程)
- [ ] `sumThenReset()` 与新写入的并发安全性:
  - [ ] 是否可能丢失数据? (在 reset 后, flush 前有新写入)
  - [ ] 当前实现是否正确? (DoubleAdder 的 sumThenReset 是原子的)
- [ ] flush 失败如何处理?
  - [ ] 重试? (可能导致重复)
  - [ ] 丢弃? (可能丢失数据)
  - [ ] 回滚? (把 delta 加回 buffer)
  - [ ] 记录日志? (人工介入)
- [ ] 是否需要监控 flush 失败次数?

### C.3 batch 大小控制

**✅ 已确认**:
- 配置项: `ranking.buffer.batch-size` (默认 1000)
- 当前实现: 达到 batch-size 后停止本次 flush

**❓ 需要确认**:
- [ ] 如果 buffer 中有 2000 条, 本次只 flush 1000 条, 剩余 1000 条何时处理?
- [ ] 是否需要"分批 flush"逻辑? (循环处理直到 buffer 为空)
- [ ] flushInterval 线上打算配置多少? (当前默认 5000ms)

---

## D. RankingRedisRepository 现状

### D.1 incrementPostScore 实现

**✅ 已确认**:
```java
public void incrementPostScore(String postId, double delta) {
    List<String> keys = List.of(
        RankingRedisKeys.hotPosts(),           // ranking:posts:hot
        RankingRedisKeys.todayPosts(),         // ranking:posts:daily:2024-01-28
        RankingRedisKeys.currentWeekPosts(),   // ranking:posts:weekly:4
        RankingRedisKeys.currentMonthPosts()   // ranking:posts:monthly:2024:01
    );
    
    redisTemplate.execute(
        incrementScript,
        keys,
        postId,
        String.valueOf(delta),
        String.valueOf(Duration.ofDays(2).getSeconds()),    // daily TTL
        String.valueOf(Duration.ofDays(14).getSeconds()),   // weekly TTL
        String.valueOf(Duration.ofDays(365).getSeconds())   // monthly TTL
    );
}
```

**❓ 需要确认**:
- [ ] **核心问题**: Key 是基于**当前时间**生成的, 如果在跨天/跨周/跨月时 flush, 分数会被写入到**新的时间周期**, 这是否符合预期?
  - [ ] 示例: 23:59:50 的事件在 00:00:05 flush, 会被计入新的一天
- [ ] 是否需要在 buffer 中记录事件时间, flush 时使用事件时间生成 key?
- [ ] Creator 和 Topic 为什么不使用 Lua 脚本? 是否需要统一?

### D.2 Lua 脚本与 Pipeline

**✅ 已确认**:
- 使用 `DefaultRedisScript` 执行 Lua 脚本
- 每次调用都发送脚本正文 (未使用 EVALSHA)
- 未使用 Pipeline (每个 post 一次 Redis 调用)

**❓ 需要确认**:
- [ ] 是否需要使用 `SCRIPT LOAD` + `EVALSHA` 缓存脚本?
  - [ ] 优势: 减少网络传输, 提高性能
  - [ ] 劣势: 需要处理脚本不存在的情况 (NOSCRIPT 错误)
- [ ] 是否需要使用 Pipeline 批量执行?
  - [ ] 优势: 减少网络往返次数
  - [ ] 劣势: 增加实现复杂度

---

## E. RocketMQ 消费配置与语义

### E.1 消费模式

**✅ 已确认**:
- 使用 `RocketMQListener<String>` (Spring Boot Starter)
- 消费模式: **并发消费 (CONCURRENTLY)** - 无序消费
  ```java
  @RocketMQMessageListener(
      topic = TopicConstants.TOPIC_POST_EVENTS,
      selectorExpression = TopicConstants.TAG_POST_VIEWED,
      consumerGroup = RankingConsumerGroups.POST_VIEWED_CONSUMER
      // 未配置 consumeMode, 默认 CONCURRENTLY
  )
  ```
- 消费组: 每个事件类型独立消费组 (如 `POST_VIEWED_CONSUMER`)

**消息有序性**:
- ❌ **消息无序**: 同一文章的多个事件可能乱序消费
  - 示例: like → view → comment 可能变成 view → comment → like
- ✅ **适合排行榜**: 排行榜是累加操作,顺序不影响最终结果
  - `score += delta` 满足交换律: `(a + b) + c = (a + c) + b`

**❓ 需要确认**:
- [ ] 并发消费的线程数配置:
  - [ ] `consumeThreadMin`: ___ (RocketMQ 默认 20)
  - [ ] `consumeThreadMax`: ___ (RocketMQ 默认 20)
- [ ] `consumeMessageBatchMaxSize`: ___ (默认 1, 单条消费)
- [ ] 是否需要改为顺序消费? 
  - [ ] 如果需要, 需要按 `postId` 分区 (MessageSelector)
  - [ ] 代价: 降低吞吐量, 增加延迟

### E.2 ack / offset 提交时机

**✅ 已确认**:
```java
public void onMessage(String message) {
    try {
        // 1. 幂等性检查
        if (!tryProcess(messageId)) {
            return;  // 直接返回, 消息被 ack
        }
        
        // 2. 放入 buffer
        scoreBufferService.addScore("post", postId, scoreDelta);
        
        // 3. 标记完成
        markCompleted(messageId);
        
        // 4. 方法正常返回 → 自动 ack
    } catch (Exception e) {
        // 5. 抛出异常 → 消息重试
        throw new RuntimeException(...);
    }
}
```

**❓ 需要确认**:
- [ ] 当前实现: 收到消息 → 放入 buffer → 立刻 ack (不等 flush)
- [ ] 这是否符合预期? 如果进程在 flush 前宕机, buffer 中的数据会丢失
- [ ] 是否可以接受这种"窗口期丢失"?
- [ ] 如果不可接受, 是否需要:
  - [ ] 等 flush 成功后再 ack? (会降低吞吐量)
  - [ ] 使用 WAL (Write-Ahead Log) 持久化 buffer?
  - [ ] 使用事务消息?

### E.3 DLQ / 重试策略

**❓ 需要确认**:
- [ ] 最大重试次数: ___ (默认 16 次)
- [ ] 延迟级别: ___ (默认 "1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h")
- [ ] 死信队列 (DLQ) 如何处理?
  - [ ] 人工介入?
  - [ ] 告警?
  - [ ] 自动重放?

### E.4 消息内容

**✅ 已确认** (从 `PostViewedEvent`):
```java
{
  "eventId": "uuid",        // 消息 ID (用于幂等)
  "postId": 123456,         // 文章 ID (Long)
  "userId": "user123",      // 用户 ID
  "publishedAt": "2024-01-28T10:00:00",  // 发布时间
  "timestamp": "2024-01-28T10:30:00"     // 事件时间
}
```

**消息顺序性**:
- ✅ **当前**: 并发消费, 消息无序
- ✅ **影响**: 排行榜是累加操作, 顺序不影响结果
- ❌ **不需要**: "同一文章事件顺序必须保持"的需求

**❓ 需要确认**:
- [ ] 是否所有事件都包含 `timestamp` 字段?
- [ ] 是否需要使用 `timestamp` 而不是 `LocalDate.now()` 来生成 key?
  - [ ] 当前问题: flush 时使用当前时间, 跨天时分数会被写入新周期
  - [ ] 解决方案: 在 buffer 中记录事件时间, flush 时使用事件时间

---

## F. 归档与持久化 (MongoDB/PG)

### F.1 归档任务

**✅ 已确认**:
- 归档方式: Spring `@Scheduled` 定时任务
- 归档时间:
  - 日榜: 每天凌晨 2:00 (`0 0 2 * * ?`)
  - 周榜: 每周一凌晨 3:00 (`0 0 3 ? * MON`)
  - 月榜: 每月1号凌晨 4:00 (`0 0 4 1 * ?`)

**❓ 需要确认**:
- [ ] 归档任务是否需要分布式锁? (防止多实例重复归档)需要
- [ ] 归档失败如何处理? (重试? 告警?)重试，多次失败告警

### F.2 归档内容

**✅ 已确认**:
- MongoDB 集合: `ranking_archive`
- 归档数量: Top 100 (配置项 `ranking.archive.limit`)
- 文档结构:
```javascript
{
  entityId: "1234567890",
  entityType: "post",
  score: 1250.5,
  rank: 1,
  rankingType: "monthly",
  period: {
    year: 2024,
    month: 1,
    week: 4,
    date: ISODate("2024-01-28")
  },
  archivedAt: ISODate("2024-02-01T04:00:00"),
  version: 1
}
```

**❓ 需要确认**:
- [ ] 归档数量是否需要可配置? (不同榜单不同数量)需要可配置
- [ ] 是否需要归档全量数据? (如 Top 1000)不需要归档全量数据
- [ ] 是否需要归档分数为 0 的数据? 不需要

### F.3 Redis 与 Mongo 的一致性

**✅ 已确认**:
- 归档后 Redis 数据**不清理**, 依赖 TTL 自动过期
- 查询时自动路由: Redis (热数据) → MongoDB (冷数据)

**❓ 需要确认**:
- [ ] 是否需要"归档后清理 Redis"? (节省内存) 需要
- [ ] 如果清理, 如何保证一致性? (归档成功后再清理)归档成功后清理
- [ ] 历史榜单查询频率如何? (决定是否需要缓存)需要缓存

---

## G. 性能与容量目标

### G.1 大概量级

**❓ 需要确认**:
- [ ] 峰值消息吞吐: ___ 条/秒
- [ ] 活跃 post 数量级: ___ (一天内会被打分的文章数)
- [ ] Redis 实例规格:
  - [ ] 单机 / 集群?
  - [ ] 内存: ___ GB
  - [ ] 预估 QPS: ___

### G.2 延迟目标

**❓ 需要确认**:
- [ ] 排行榜更新允许延迟: 
  - [ ] 200ms / 1s / 5s / 1min / 其他: ___ 允许1min
- [ ] 允许"进程宕机丢分窗口"多大?
  - [ ] 5s (一个 flush 周期)
  - [ ] 1min
  - [ ] 不允许丢失 (需要 WAL)
    允许丢失1min，但是需要保证正确性
---

## H. 正确性标准 (最重要)

**请为不同事件类型选择正确性标准**:

### 标准定义

- **A: 允许轻微误差** (可能丢/重复少量增量, 换极高吞吐)
  - 适用场景: view (浏览) 事件
  - 实现: 当前方案 (buffer + 定时 flush)
  
- **B: 不丢但可重复** (至少一次, 靠可接受误差或业务幂等)
  - 适用场景: like, favorite, comment 事件
  - 实现: 当前方案 + 业务层幂等
  
- **C: 严格不丢且不重复** (需要 WAL/去重/更复杂链路, 成本最高)
  - 适用场景: 金额相关, 严格计数
  - 实现: WAL + 事务消息 + 分布式事务

### 事件正确性要求

**❓ 请为每种事件选择标准 (A/B/C)**:

| 事件类型 | 正确性标准 | 说明 |
|---------|-----------|------|
| view (浏览) | ___ | |
| like (点赞) | ___ | |
| unlike (取消点赞) | ___ | |
| favorite (收藏) | ___ | |
| comment (评论) | ___ | |

**❓ 其他确认**:
- [ ] 是否可以接受"进程宕机时丢失 buffer 中的数据"?

- [ ] 是否可以接受"消息重试导致的重复计分"?
- [ ] 是否需要"精确到个位数"的分数, 还是"大致排名正确"即可?

---

## I. 其他问题与建议

### I.1 已知问题

**✅ 已识别的问题**:

1. **时间维度不一致**
   - 问题: flush 时使用当前时间生成 key, 跨天/跨周/跨月时分数会被写入新周期
   - 影响: 23:59 的事件可能被计入第二天
   
2. **Creator 和 Topic 没有周榜/月榜**
   - 问题: 只有总榜和日榜, 归档时取总榜快照
   - 影响: 无法查询历史周榜/月榜
   
3. **没有 ZSET 裁剪**
   - 问题: ZSET 可能无限增长
   - 影响: 内存占用, 查询性能

### I.2 优化建议

**📝 请补充你的想法**:
- [ ] 是否有其他已知问题?
- [ ] 是否有特定的优化目标?
- [ ] 是否有特定的技术约束?

---

## 填写完成后

请将填写好的问卷发送给我, 我会基于你的回答设计优化方案。

**重点关注**:
- H 部分 (正确性标准) - 决定整体架构
- D.1 部分 (时间维度问题) - 核心设计缺陷
- E.2 部分 (ack 时机) - 数据可靠性
- G 部分 (性能目标) - 优化方向

---

**文档版本**: v1.0  
**创建时间**: 2024-01-28  
**最后更新**: 2024-01-28
