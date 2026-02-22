# 基础设施设计

## 分布式ID生成器

### 方案选型

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| **UUID** | 简单、无依赖 | 无序、存储空间大、索引性能差 | 分布式追踪ID |
| **数据库自增** | 简单、有序 | 单点瓶颈、扩展性差 | 单体应用 |
| **Snowflake** | 有序、高性能、趋势递增 | 依赖时钟、需要配置机器ID | 高并发场景 |
| **Leaf-Segment** | 高可用、无时钟依赖 | 依赖数据库、ID不连续 | 中等并发场景 |
| **Leaf-Snowflake** | 高性能、自动分配机器ID | 依赖ZK/Nacos | 大规模分布式系统 |

本项目选用 **美团 Leaf（Snowflake 模式）**，原因：
1. 高性能：单机 QPS 可达 10万+
2. 趋势递增：对数据库索引友好
3. 自动分配 WorkerId：通过 Nacos 自动分配，无需手动配置
4. 成熟稳定：美团开源，经过大规模生产验证

### Leaf-Snowflake ID 结构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              64-bit Snowflake ID                                 │
│                                                                                  │
│   ┌───────┬────────────────────────────┬────────────┬────────────┐              │
│   │ 1 bit │         41 bits            │  10 bits   │  12 bits   │              │
│   │ sign  │       timestamp            │  workerId  │  sequence  │              │
│   │  (0)  │   (毫秒级时间戳)            │  (机器ID)  │  (序列号)  │              │
│   └───────┴────────────────────────────┴────────────┴────────────┘              │
│                                                                                  │
│   - sign: 固定为 0，保证 ID 为正数                                               │
│   - timestamp: 41 位时间戳，可用约 69 年                                         │
│   - workerId: 10 位机器 ID，支持 1024 个节点                                     │
│   - sequence: 12 位序列号，每毫秒可生成 4096 个 ID                               │
│                                                                                  │
│   理论 QPS: 1000 * 4096 = 409.6 万/秒（单机）                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Leaf 服务配置

#### Maven 依赖

```xml
<!-- Leaf 依赖 -->
<dependency>
    <groupId>com.sankuai.inf.leaf</groupId>
    <artifactId>leaf-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

#### 配置文件

```yaml
# application.yml
leaf:
  name: ZhiCore-leaf
  snowflake:
    enable: true
    # 使用 Nacos 自动分配 WorkerId
    zk:
      address: ${NACOS_ADDR:localhost:8848}
    port: ${server.port:8080}
```

### ID 生成器实现

```java
/**
 * 分布式 ID 生成器接口
 */
public interface IdGenerator {
    
    /**
     * 生成下一个 ID
     */
    long nextId();
    
    /**
     * 批量生成 ID
     */
    List<Long> nextIds(int count);
    
    /**
     * 解析 ID 中的时间戳
     */
    long parseTimestamp(long id);
    
    /**
     * 解析 ID 中的 WorkerId
     */
    int parseWorkerId(long id);
}

/**
 * Leaf Snowflake ID 生成器实现
 */
@Component
@Primary
public class LeafIdGenerator implements IdGenerator {
    
    private final SnowflakeService snowflakeService;
    
    // Snowflake 各部分的位数
    private static final int TIMESTAMP_BITS = 41;
    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    
    // 起始时间戳（2024-01-01 00:00:00）
    private static final long EPOCH = 1704067200000L;
    
    public LeafIdGenerator(SnowflakeService snowflakeService) {
        this.snowflakeService = snowflakeService;
    }
    
    @Override
    public long nextId() {
        Result result = snowflakeService.getId("ZhiCore");
        if (result.getStatus() != Status.SUCCESS) {
            throw new IdGenerationException("ID 生成失败: " + result.getStatus());
        }
        return result.getId();
    }
    
    @Override
    public List<Long> nextIds(int count) {
        if (count <= 0 || count > 10000) {
            throw new IllegalArgumentException("批量生成数量必须在 1-10000 之间");
        }
        
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(nextId());
        }
        return ids;
    }
    
    @Override
    public long parseTimestamp(long id) {
        // 右移 22 位（workerId + sequence）得到时间戳
        long timestamp = (id >> (WORKER_ID_BITS + SEQUENCE_BITS)) + EPOCH;
        return timestamp;
    }
    
    @Override
    public int parseWorkerId(long id) {
        // 右移 12 位（sequence），然后取低 10 位
        return (int) ((id >> SEQUENCE_BITS) & ((1 << WORKER_ID_BITS) - 1));
    }
}

/**
 * ID 生成异常
 */
public class IdGenerationException extends RuntimeException {
    public IdGenerationException(String message) {
        super(message);
    }
}
```

### WorkerId 自动分配（基于 Nacos）

```java
/**
 * 基于 Nacos 的 WorkerId 分配器
 * 
 * 原理：
 * 1. 服务启动时向 Nacos 注册临时节点
 * 2. 根据节点序号分配 WorkerId（0-1023）
 * 3. 服务下线时自动释放 WorkerId
 */
@Component
public class NacosWorkerIdAllocator {
    
    private final NamingService namingService;
    private final String serviceName;
    private final String ip;
    private final int port;
    
    private volatile int workerId = -1;
    
    @PostConstruct
    public void init() throws NacosException {
        // 注册服务实例
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setHealthy(true);
        instance.setWeight(1.0);
        
        namingService.registerInstance(serviceName + "-leaf", instance);
        
        // 获取所有实例，计算 WorkerId
        List<Instance> instances = namingService.getAllInstances(serviceName + "-leaf");
        instances.sort(Comparator.comparing(i -> i.getIp() + ":" + i.getPort()));
        
        for (int i = 0; i < instances.size(); i++) {
            Instance inst = instances.get(i);
            if (inst.getIp().equals(ip) && inst.getPort() == port) {
                this.workerId = i % 1024;  // 取模保证在 0-1023 范围内
                break;
            }
        }
        
        if (workerId < 0) {
            throw new IllegalStateException("无法分配 WorkerId");
        }
        
        log.info("WorkerId 分配成功: {}", workerId);
    }
    
    public int getWorkerId() {
        return workerId;
    }
}
```

### 时钟回拨处理

```java
/**
 * 时钟回拨处理策略
 */
@Component
public class ClockBackwardHandler {
    
    private final MeterRegistry meterRegistry;
    
    // 允许的最大时钟回拨毫秒数
    private static final long MAX_BACKWARD_MS = 5;
    
    // 上次生成 ID 的时间戳
    private volatile long lastTimestamp = -1L;
    
    /**
     * 处理时钟回拨
     * 
     * @param currentTimestamp 当前时间戳
     * @return 调整后的时间戳
     */
    public long handleClockBackward(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            
            // 记录时钟回拨指标
            meterRegistry.counter("leaf.clock.backward", 
                "offset_ms", String.valueOf(offset)).increment();
            
            if (offset <= MAX_BACKWARD_MS) {
                // 小幅回拨：等待时钟追上
                try {
                    Thread.sleep(offset);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentTimestamp = System.currentTimeMillis();
                
                if (currentTimestamp < lastTimestamp) {
                    throw new ClockBackwardException(
                        "时钟回拨超过允许范围: " + (lastTimestamp - currentTimestamp) + "ms");
                }
            } else {
                // 大幅回拨：抛出异常，触发告警
                throw new ClockBackwardException(
                    "检测到严重时钟回拨: " + offset + "ms");
            }
        }
        
        lastTimestamp = currentTimestamp;
        return currentTimestamp;
    }
}

/**
 * 时钟回拨异常
 */
public class ClockBackwardException extends RuntimeException {
    public ClockBackwardException(String message) {
        super(message);
    }
}
```

### ID 生成监控

```yaml
# Prometheus 告警规则
groups:
  - name: leaf-id-alerts
    rules:
      # ID 生成失败告警
      - alert: LeafIdGenerationFailed
        expr: increase(leaf_id_generation_error_total[5m]) > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Leaf ID 生成失败"
          description: "过去 5 分钟内 ID 生成失败 {{ $value }} 次"
      
      # 时钟回拨告警
      - alert: LeafClockBackward
        expr: increase(leaf_clock_backward_total[5m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "检测到时钟回拨"
          description: "过去 5 分钟内检测到 {{ $value }} 次时钟回拨"
      
      # ID 生成 QPS 过高告警
      - alert: LeafIdGenerationHighQPS
        expr: rate(leaf_id_generation_total[1m]) > 100000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "ID 生成 QPS 过高"
          description: "当前 ID 生成 QPS: {{ $value }}"
```

### 各服务 ID 使用规范

| 实体 | ID 类型 | 生成方式 | 说明 |
|------|--------|---------|------|
| User | String (UUID) | `UUID.randomUUID()` | 用户ID使用UUID，便于分库分表 |
| Post | Long | `idGenerator.nextId()` | 文章ID使用Leaf |
| Comment | Long | `idGenerator.nextId()` | 评论ID使用Leaf |
| Message | Long | `idGenerator.nextId()` | 私信ID使用Leaf |
| Notification | Long | `idGenerator.nextId()` | 通知ID使用Leaf |
| PostLike | Long | `idGenerator.nextId()` | 点赞记录ID使用Leaf |
| Follow | Long | `idGenerator.nextId()` | 关注记录ID使用Leaf |

```java
// 使用示例
@Service
public class PostApplicationService {
    
    private final IdGenerator idGenerator;
    
    public Long createPost(String authorId, CreatePostRequest request) {
        Long postId = idGenerator.nextId();
        Post post = Post.create(postId, authorId, request.getTitle(), request.getContent());
        postRepository.save(post);
        return postId;
    }
}
```

---

## 缓存架构

### 缓存策略矩阵

> **设计说明：各实体缓存策略一览**
> 
> 明确每个数据实体的缓存策略、TTL、失效触发条件和重建来源。

| 服务 | 数据实体 | 缓存策略 | TTL | 失效触发 | 重建来源 |
|------|---------|---------|-----|---------|---------|
| **User Service** | 用户资料 | Cache-Aside | 10m | 资料更新 | DB |
| | 关注列表 | Cache-Aside | 5m | 关注/取关 | DB |
| | 关注统计 | Write-Through | 永久 | 关注/取关 | CDC/定时对账 |
| **Post Service** | 文章详情 | Cache-Aside | 10m | 文章更新 | DB |
| | 文章列表 | Cache-Aside | 5m | 文章发布/删除 | DB |
| | 点赞统计 | Write-Through | 永久 | 点赞/取消 | CDC/定时对账 |
| | 浏览量 | Write-Behind | 永久 | 每分钟同步 | Redis → DB |
| | 用户点赞状态 | Write-Through | 永久 | 点赞/取消 | CDC |
| **Comment Service** | 评论列表 | Cache-Aside | 5m | 评论创建/删除 | DB |
| | 评论统计 | Write-Through | 永久 | 评论/点赞 | CDC/定时对账 |
| **Notification Service** | 聚合通知 | Cache-Aside | 5m | 新通知到达 | DB |
| | 未读计数 | Write-Through | 永久 | 通知创建/已读 | CDC |
| **Ranking Service** | 热门文章 | Refresh-Ahead | 1h | 定时刷新 | 计算任务 |
| | 热门作者 | Refresh-Ahead | 1h | 定时刷新 | 计算任务 |

### 统计类缓存对账与重建机制

> **设计说明：stats 缓存永久存储的对账机制**
> 
> 统计类缓存（点赞数、关注数等）设置为永久（TTL=-1），需要配套对账和重建机制。

#### 对账任务配置

```java
@Component
public class StatsReconciliationTask {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final PostStatsRepository postStatsRepository;
    private final UserStatsRepository userStatsRepository;
    private final MeterRegistry meterRegistry;
    
    // 对账阈值：Redis 与 DB 差异超过此值触发告警
    private static final int ALERT_THRESHOLD = 10;
    
    /**
     * 每天凌晨 3 点执行全量对账
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcileAllStats() {
        reconcilePostLikeCounts();
        reconcileUserFollowCounts();
        reconcileCommentCounts();
    }
    
    /**
     * 每小时执行增量对账（最近 1 小时有变更的数据）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void reconcileRecentStats() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        reconcileRecentPostLikeCounts(since);
        reconcileRecentUserFollowCounts(since);
    }
    
    private void reconcilePostLikeCounts() {
        List<PostLikeCount> dbCounts = postStatsRepository.getAllLikeCounts();
        int fixedCount = 0;
        int alertCount = 0;
        
        for (PostLikeCount count : dbCounts) {
            String key = PostRedisKeys.likeCount(count.getPostId());
            Long redisCount = (Long) redisTemplate.opsForValue().get(key);
            
            if (redisCount == null || !redisCount.equals((long) count.getCount())) {
                int diff = Math.abs((redisCount == null ? 0 : redisCount.intValue()) - count.getCount());
                
                // 修复 Redis 数据
                redisTemplate.opsForValue().set(key, count.getCount());
                fixedCount++;
                
                // 差异过大触发告警
                if (diff > ALERT_THRESHOLD) {
                    alertCount++;
                    log.warn("点赞计数差异过大: postId={}, db={}, redis={}", 
                        count.getPostId(), count.getCount(), redisCount);
                }
            }
        }
        
        // 记录指标
        meterRegistry.counter("stats.reconciliation.fixed", "type", "post_like").increment(fixedCount);
        meterRegistry.counter("stats.reconciliation.alert", "type", "post_like").increment(alertCount);
        
        log.info("点赞计数对账完成: 修复={}, 告警={}", fixedCount, alertCount);
    }
    
    /**
     * 缓存重建（用于 Redis 故障恢复后）
     */
    public void rebuildAllStatsCache() {
        log.info("开始重建统计缓存...");
        
        // 1. 重建点赞计数
        List<PostLikeCount> likeCounts = postStatsRepository.getAllLikeCounts();
        for (PostLikeCount count : likeCounts) {
            redisTemplate.opsForValue().set(
                PostRedisKeys.likeCount(count.getPostId()), 
                count.getCount()
            );
        }
        
        // 2. 重建关注计数
        List<UserFollowCount> followCounts = userStatsRepository.getAllFollowCounts();
        for (UserFollowCount count : followCounts) {
            redisTemplate.opsForValue().set(
                UserRedisKeys.followersCount(count.getUserId()), 
                count.getFollowersCount()
            );
            redisTemplate.opsForValue().set(
                UserRedisKeys.followingCount(count.getUserId()), 
                count.getFollowingCount()
            );
        }
        
        log.info("统计缓存重建完成");
    }
}
```

#### 监控告警配置

```yaml
# Prometheus 告警规则
groups:
  - name: cache-stats-alerts
    rules:
      - alert: StatsReconciliationHighDiff
        expr: increase(stats_reconciliation_alert_total[1h]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "统计缓存对账差异过大"
          description: "过去 1 小时内有 {{ $value }} 条记录差异超过阈值"
      
      - alert: StatsReconciliationFailed
        expr: stats_reconciliation_last_success_timestamp < time() - 86400
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "统计缓存对账任务失败"
          description: "对账任务超过 24 小时未成功执行"
```

### 缓存更新策略规范

> **设计说明：统一缓存更新策略**
> 
> 不同场景使用不同的缓存策略，以下是各策略的适用场景和实现方式：

| 策略 | 适用场景 | 实现方式 | 示例 |
|------|---------|---------|------|
| **Cache-Aside** | 读多写少的实体数据 | 读时查缓存→miss→查DB→写缓存；写时先更新DB→删缓存 | 文章详情、用户信息 |
| **Write-Through** | 需要强一致性的计数器 | 写操作同时更新DB和缓存（事务提交后更新缓存） | 点赞数、关注数 |
| **Write-Behind** | 高频写入的统计数据 | 先写缓存，异步批量写入DB | 浏览量统计 |
| **Refresh-Ahead** | 热点数据预加载 | 缓存过期前主动刷新 | 热门文章列表 |

#### Cache-Aside 模式（默认策略）

```java
// 读操作
public Post findById(Long postId) {
    String key = PostRedisKeys.detail(postId);
    
    // 1. 查缓存
    Post cached = (Post) redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return cached;
    }
    
    // 2. 查数据库
    Post post = delegate.findById(postId);
    
    // 3. 写缓存（带随机抖动防止缓存雪崩）
    if (post != null) {
        Duration ttl = cacheConfig.getEntityDetailTtl()
            .plus(Duration.ofSeconds(randomJitter()));
        redisTemplate.opsForValue().set(key, post, ttl);
    }
    
    return post;
}

// 写操作
public void update(Post post) {
    // 先更新数据库
    delegate.update(post);
    // 再删除缓存（而非更新，避免并发问题）
    redisTemplate.delete(PostRedisKeys.detail(post.getId()));
}
```

#### Write-Through 模式（计数器场景）

> **设计说明：计数器写失败补偿机制**
> 
> Redis 更新失败时的处理流程：
> 1. 记录失败日志（包含 postId、userId、操作类型）
> 2. 发送告警（失败率超过阈值时）
> 3. CDC 实时补偿（秒级）
> 4. 定时任务兜底对账（小时级）

```java
// 点赞操作：事务提交后更新缓存
public void likePost(String userId, Long postId) {
    // 数据库操作在事务中执行
    transactionTemplate.executeWithoutResult(status -> {
        PostLike like = new PostLike(idGenerator.nextId(), postId, userId);
        likeRepository.save(like);
    });
    
    // 事务提交成功后，更新 Redis 缓存
    String likeKey = PostRedisKeys.userLiked(userId, postId);
    try {
        redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
        redisTemplate.opsForValue().set(likeKey, "1");
    } catch (Exception e) {
        // Redis 更新失败处理
        handleCacheUpdateFailure("like", postId, userId, e);
    }
}

/**
 * 缓存更新失败处理
 */
private void handleCacheUpdateFailure(String operation, Long postId, String userId, Exception e) {
    // 1. 记录失败日志
    log.warn("Redis 更新失败: operation={}, postId={}, userId={}, error={}", 
        operation, postId, userId, e.getMessage());
    
    // 2. 记录指标（用于告警）
    meterRegistry.counter("cache.update.failure", 
        "operation", operation, 
        "service", "post-service"
    ).increment();
    
    // 3. 记录到失败队列（可选，用于快速重试）
    cacheFailureQueue.add(new CacheFailure(operation, postId, userId, LocalDateTime.now()));
    
    // 注意：不抛出异常，主流程已成功
    // CDC 和定时任务会自动修复数据
}
```

#### Write-Behind 模式（高频写入场景）

```java
// 浏览量统计：先写 Redis，定时批量同步到数据库
@Service
public class ViewCountService {
    
    public void incrementViewCount(Long postId) {
        // 只更新 Redis，不直接写数据库
        redisTemplate.opsForValue().increment(PostRedisKeys.viewCount(postId));
    }
}

@Scheduled(fixedRate = 60000)  // 每分钟同步一次
public void syncViewCountsToDatabase() {
    Set<String> keys = redisTemplate.keys("post:*:view_count");
    for (String key : keys) {
        Long postId = extractPostId(key);
        Long count = (Long) redisTemplate.opsForValue().get(key);
        postStatsRepository.updateViewCount(postId, count);
    }
}
```

### 缓存分层原则

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Application Layer                                   │
│                                                                                  │
│   PostApplicationService                                                         │
│   ├── 不直接操作 Redis                                                           │
│   └── 通过 Repository 或 CachedService 访问数据                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            Infrastructure Layer                                  │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    CachedPostRepository (装饰器模式)                      │   │
│   │                                                                          │   │
│   │   findById(postId) {                                                     │   │
│   │       // 1. 查缓存                                                        │   │
│   │       Post cached = redisTemplate.get("post:" + postId);                 │   │
│   │       if (cached != null) return cached;                                 │   │
│   │                                                                          │   │
│   │       // 2. 查数据库                                                      │   │
│   │       Post post = delegate.findById(postId);                             │   │
│   │                                                                          │   │
│   │       // 3. 写缓存（带随机抖动）                                           │   │
│   │       redisTemplate.set("post:" + postId, post, ttl + randomJitter());   │   │
│   │       return post;                                                       │   │
│   │   }                                                                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                        │                                         │
│                                        ▼                                         │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         PostRepositoryImpl                               │   │
│   │                         (MyBatis-Plus 实现)                              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```


### 缓存装饰器实现

```java
@Repository
@Primary
public class CachedPostRepository implements PostRepository {
    
    private final PostRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheConfig cacheConfig;
    
    @Override
    public Post findById(Long postId) {
        String key = PostRedisKeys.detail(postId);
        
        // 1. 查缓存
        Post cached = (Post) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        
        // 2. 查数据库
        Post post = delegate.findById(postId);
        
        // 3. 写缓存（防止缓存穿透：空值也缓存）
        if (post != null) {
            Duration ttl = cacheConfig.getEntityDetailTtl()
                .plus(Duration.ofSeconds(randomJitter()));
            redisTemplate.opsForValue().set(key, post, ttl);
        } else {
            // 缓存空值，短过期时间
            redisTemplate.opsForValue().set(key, NullValue.INSTANCE, Duration.ofMinutes(1));
        }
        
        return post;
    }
    
    @Override
    public void update(Post post) {
        // Cache-Aside: 先更新数据库，再删除缓存
        delegate.update(post);
        redisTemplate.delete(PostRedisKeys.detail(post.getId()));
    }
    
    @Override
    public void delete(Long postId) {
        delegate.delete(postId);
        redisTemplate.delete(PostRedisKeys.detail(postId));
    }
    
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, 60);
    }
}
```

### 缓存配置

```yaml
# application.yml
cache:
  ttl:
    entity-detail: 10m    # 实体详情缓存 10 分钟
    list: 5m              # 列表缓存 5 分钟
    stats: -1             # 统计数据永久（通过事件更新）
    session: 7d           # 会话缓存 7 天
  jitter:
    max-seconds: 60       # 最大抖动秒数
```

```java
@Configuration
@ConfigurationProperties(prefix = "cache.ttl")
public class CacheConfig {
    private Duration entityDetail = Duration.ofMinutes(10);
    private Duration list = Duration.ofMinutes(5);
    private Duration stats = Duration.ofSeconds(-1);
    private Duration session = Duration.ofDays(7);
    
    // getters and setters
}
```

---

## 消息队列架构

### RocketMQ Topic 设计

> **设计说明：按业务域拆分 Topic**
> 
> 为避免单一 Topic 成为性能瓶颈，按业务域拆分为多个 Topic：
> - `ZhiCore-post-events`：文章相关事件（发布、更新、删除、点赞等）
> - `ZhiCore-user-events`：用户相关事件（关注、资料更新等）
> - `ZhiCore-comment-events`：评论相关事件（创建、点赞等）
> - `ZhiCore-message-events`：私信相关事件（需要顺序消息）
> 
> 拆分优势：
> 1. 避免单 Topic 成为瓶颈
> 2. 不同 Topic 可独立扩容
> 3. 消费者可按需订阅，减少无效消息处理
> 4. 故障隔离，单个 Topic 问题不影响其他业务

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              RocketMQ Topology                                   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    ZhiCore-post-events (Topic)                              │   │
│   │   Tags: published, updated, deleted, liked, unliked, favorited           │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    ZhiCore-user-events (Topic)                              │   │
│   │   Tags: followed, unfollowed, profile-updated, registered                │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    ZhiCore-comment-events (Topic)                           │   │
│   │   Tags: created, liked, unliked, deleted                                 │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    ZhiCore-message-events (Topic) - 顺序消息                 │   │
│   │   Tags: sent, read                                                       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│           ┌────────────────────────────┬────────────────────────────┐           │
│           │                            │                            │           │
│           ▼                            ▼                            ▼           │
│   ┌───────────────┐          ┌───────────────┐          ┌───────────────┐       │
│   │ Search        │          │ Notification  │          │ Ranking       │       │
│   │ Consumer      │          │ Consumer      │          │ Consumer      │       │
│   │ Group         │          │ Group         │          │ Group         │       │
│   │               │          │               │          │               │       │
│   │ Topics:       │          │ Topics:       │          │ Topics:       │       │
│   │ post-events   │          │ post-events   │          │ post-events   │       │
│   │               │          │ comment-events│          │ user-events   │       │
│   │               │          │ user-events   │          │               │       │
│   └───────────────┘          └───────────────┘          └───────────────┘       │
└─────────────────────────────────────────────────────────────────────────────────┘
```


### RocketMQ 配置

> **设计说明：消息顺序性保证**
> 
> 对于需要保证顺序的场景（如私信消息），使用 RocketMQ 顺序消息：
> - 使用 `syncSendOrderly` 方法发送顺序消息
> - 以 `conversationId` 作为 shardingKey，确保同一会话的消息发送到同一队列
> - 消费者使用 `ConsumeMode.ORDERLY` 模式保证顺序消费

### 消费者配置规范

> **设计说明：消费者配置矩阵**
> 
> 不同事件类型的消费者配置要求。

| Consumer Group | 订阅 Topic | ConsumeMode | 并发度 | 最大重试 | 说明 |
|----------------|-----------|-------------|--------|---------|------|
| search-consumer-group | ZhiCore-post-events | CONCURRENTLY | 20 | 3 | 搜索索引更新 |
| notification-consumer-group | ZhiCore-post-events, ZhiCore-comment-events, ZhiCore-user-events | CONCURRENTLY | 10 | 3 | 通知创建 |
| ranking-consumer-group | ZhiCore-post-events, ZhiCore-user-events | CONCURRENTLY | 5 | 3 | 排行榜更新 |
| message-consumer-group | ZhiCore-message-events | **ORDERLY** | 1 | 5 | 私信消息（顺序） |

#### 顺序消费者示例（私信消息）

```java
/**
 * 私信消息消费者 - 顺序消费
 * 
 * 注意：
 * 1. consumeMode = ORDERLY 保证同一队列的消息顺序消费
 * 2. consumeThreadNumber = 1 保证单线程处理（每个队列）
 * 3. 顺序消费失败会阻塞后续消息，需要谨慎处理异常
 */
@Service
@RocketMQMessageListener(
    topic = RocketMQConfig.TOPIC_MESSAGE,
    selectorExpression = "*",
    consumerGroup = RocketMQConfig.MESSAGE_CONSUMER_GROUP,
    consumeMode = ConsumeMode.ORDERLY,      // 顺序消费
    consumeThreadNumber = 1,                 // 单线程（每队列）
    maxReconsumeTimes = 5                    // 最大重试次数
)
public class MessageEventConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        MessageSentEvent event = objectMapper.readValue(message, MessageSentEvent.class);
        
        // 顺序消费：同一会话的消息按顺序处理
        messageService.processMessage(event);
    }
}
```

#### 并发消费者示例（通知）

```java
/**
 * 通知消费者 - 并发消费
 */
@Service
@RocketMQMessageListener(
    topic = RocketMQConfig.TOPIC_POST,
    selectorExpression = "liked || favorited",
    consumerGroup = RocketMQConfig.NOTIFICATION_CONSUMER_GROUP,
    consumeMode = ConsumeMode.CONCURRENTLY,  // 并发消费
    consumeThreadNumber = 10,                 // 10 个消费线程
    maxReconsumeTimes = 3                     // 最大重试次数
)
public class PostLikeNotificationConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        // 并发消费：使用幂等性处理器防止重复
        // ...
    }
}
```

```java
@Configuration
public class RocketMQConfig {
    
    // ========== Topics（按业务域拆分）==========
    public static final String TOPIC_POST = "ZhiCore-post-events";
    public static final String TOPIC_USER = "ZhiCore-user-events";
    public static final String TOPIC_COMMENT = "ZhiCore-comment-events";
    public static final String TOPIC_MESSAGE = "ZhiCore-message-events";  // 顺序消息
    
    // ========== Post Tags ==========
    public static final String TAG_POST_PUBLISHED = "published";
    public static final String TAG_POST_UPDATED = "updated";
    public static final String TAG_POST_DELETED = "deleted";
    public static final String TAG_POST_LIKED = "liked";
    public static final String TAG_POST_UNLIKED = "unliked";
    public static final String TAG_POST_FAVORITED = "favorited";
    
    // ========== User Tags ==========
    public static final String TAG_USER_FOLLOWED = "followed";
    public static final String TAG_USER_UNFOLLOWED = "unfollowed";
    public static final String TAG_USER_PROFILE_UPDATED = "profile-updated";
    public static final String TAG_USER_REGISTERED = "registered";
    
    // ========== Comment Tags ==========
    public static final String TAG_COMMENT_CREATED = "created";
    public static final String TAG_COMMENT_LIKED = "liked";
    public static final String TAG_COMMENT_UNLIKED = "unliked";
    public static final String TAG_COMMENT_DELETED = "deleted";
    
    // ========== Message Tags ==========
    public static final String TAG_MESSAGE_SENT = "sent";
    public static final String TAG_MESSAGE_READ = "read";
    
    // ========== Consumer Groups ==========
    public static final String SEARCH_CONSUMER_GROUP = "search-consumer-group";
    public static final String NOTIFICATION_CONSUMER_GROUP = "notification-consumer-group";
    public static final String RANKING_CONSUMER_GROUP = "ranking-consumer-group";
    public static final String MESSAGE_CONSUMER_GROUP = "message-consumer-group";
    
    /**
     * 根据事件类型获取对应的 Topic
     */
    public static String getTopicForEvent(Class<?> eventClass) {
        String className = eventClass.getSimpleName();
        if (className.startsWith("Post")) {
            return TOPIC_POST;
        } else if (className.startsWith("User") || className.startsWith("Follow")) {
            return TOPIC_USER;
        } else if (className.startsWith("Comment")) {
            return TOPIC_COMMENT;
        } else if (className.startsWith("Message")) {
            return TOPIC_MESSAGE;
        }
        throw new IllegalArgumentException("Unknown event type: " + className);
    }
}
```

```yaml
# application.yml
rocketmq:
  name-server: ${ROCKETMQ_NAMESRV:localhost:9876}
  producer:
    group: ZhiCore-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
    retry-times-when-send-async-failed: 2
  consumer:
    # 各服务配置自己的 consumer group
```

### 消息发布者

> **设计说明：事件发布 Topic 路由**
> 
> 事件发布时根据事件类型自动路由到对应 Topic，格式为 `{topic}:{tag}`。

```java
@Component
public class DomainEventPublisher {
    
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 发布普通消息
     * 自动根据事件类型路由到对应 Topic
     */
    public void publish(DomainEvent event) {
        // 根据事件类型获取 Topic
        String topic = RocketMQConfig.getTopicForEvent(event.getClass());
        String destination = topic + ":" + event.getTag();
        String payload = objectMapper.writeValueAsString(event);
        
        Message<String> message = MessageBuilder
            .withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, event.getEventId())
            .build();
        
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("消息发送成功: topic={}, msgId={}", topic, sendResult.getMsgId());
            }
            
            @Override
            public void onException(Throwable e) {
                log.error("消息发送失败: topic={}, eventId={}", topic, event.getEventId(), e);
            }
        });
    }
    
    /**
     * 发布顺序消息（用于需要保证顺序的场景，如私信消息）
     * 
     * @param event 领域事件
     * @param shardingKey 分片键（如 conversationId），相同 shardingKey 的消息会发送到同一队列
     */
    public void publishOrderly(DomainEvent event, String shardingKey) {
        String topic = RocketMQConfig.getTopicForEvent(event.getClass());
        String destination = topic + ":" + event.getTag();
        String payload = objectMapper.writeValueAsString(event);
        
        Message<String> message = MessageBuilder
            .withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, event.getEventId())
            .build();
        
        // 使用 syncSendOrderly 保证顺序
        rocketMQTemplate.syncSendOrderly(destination, message, shardingKey, 3000);
    }
    
    /**
     * 发布事务消息（用于需要本地事务+消息原子性的场景）
     */
    public void publishInTransaction(DomainEvent event, Object arg) {
        String topic = RocketMQConfig.getTopicForEvent(event.getClass());
        String destination = topic + ":" + event.getTag();
        String payload = objectMapper.writeValueAsString(event);
        
        Message<String> message = MessageBuilder
            .withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, event.getEventId())
            .build();
        
        rocketMQTemplate.sendMessageInTransaction(destination, message, arg);
    }
    
    /**
     * 发布延迟消息（用于定时发布文章等场景）
     */
    public void publishDelayed(DomainEvent event, int delayLevel) {
        String topic = RocketMQConfig.getTopicForEvent(event.getClass());
        String destination = topic + ":" + event.getTag();
        String payload = objectMapper.writeValueAsString(event);
        
        Message<String> message = MessageBuilder
            .withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, event.getEventId())
            .build();
        
        rocketMQTemplate.syncSend(destination, message, 3000, delayLevel);
    }
}
```

### 事务消息监听器

```java
/**
 * 事务消息监听器
 * 用于处理需要本地事务+消息原子性的场景
 */
@RocketMQTransactionListener
public class ZhiCoreTransactionListener implements RocketMQLocalTransactionListener {
    
    private final PostRepository postRepository;
    
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            // 执行本地事务
            if (arg instanceof PublishPostContext context) {
                Post post = postRepository.findById(context.getPostId());
                post.publish();
                postRepository.update(post);
            }
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("本地事务执行失败", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 事务回查：检查本地事务是否执行成功
        String postId = msg.getHeaders().get(RocketMQHeaders.KEYS, String.class);
        Post post = postRepository.findById(Long.parseLong(postId));
        
        if (post != null && post.getStatus() == PostStatus.PUBLISHED) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

### 消费者幂等性设计

```java
@Component
public class IdempotentMessageHandler {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String PROCESSED_KEY_PREFIX = "mq:processed:";
    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);
    
    /**
     * 幂等性检查
     * @return true 如果消息未处理过，false 如果已处理
     */
    public boolean tryProcess(String messageId) {
        String key = PROCESSED_KEY_PREFIX + messageId;
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", DEDUP_WINDOW);
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 标记消息处理完成
     */
    public void markProcessed(String messageId) {
        String key = PROCESSED_KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, "1", DEDUP_WINDOW);
    }
}

// 使用示例 - 改进版：使用状态机保证幂等性和可重试性
@Service
@RocketMQMessageListener(
    topic = RocketMQConfig.TOPIC_POST,
    selectorExpression = "liked",
    consumerGroup = RocketMQConfig.NOTIFICATION_CONSUMER_GROUP,
    consumeMode = ConsumeMode.CONCURRENTLY,  // 并发消费
    maxReconsumeTimes = 3                     // 最大重试次数
)
public class PostLikedNotificationConsumer implements RocketMQListener<String> {
    
    private final StatefulIdempotentHandler idempotentHandler;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(String message) {
        PostLikedEvent event = objectMapper.readValue(message, PostLikedEvent.class);
        String messageId = event.getEventId();
        
        // 幂等性检查：使用状态而非简单标记
        ProcessingStatus status = idempotentHandler.getStatus(messageId);
        
        if (status == ProcessingStatus.COMPLETED) {
            log.info("Message already processed: {}", messageId);
            return;
        }
        
        // 标记为处理中（防止并发重复处理）
        if (!idempotentHandler.tryMarkProcessing(messageId)) {
            log.info("Message is being processed by another consumer: {}", messageId);
            return;
        }
        
        try {
            // 处理业务逻辑
            notificationService.createLikeNotification(event);
            
            // 标记为完成
            idempotentHandler.markCompleted(messageId);
        } catch (Exception e) {
            // 处理失败，标记为失败状态（而非删除），允许重试
            idempotentHandler.markFailed(messageId);
            throw e;
        }
    }
}

/**
 * 改进版幂等性处理器 - 使用状态机
 * 状态流转：NONE -> PROCESSING -> COMPLETED/FAILED
 */
@Component
public class StatefulIdempotentHandler {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String STATUS_KEY_PREFIX = "mq:status:";
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);
    private static final Duration FAILED_TTL = Duration.ofMinutes(30);
    
    public enum ProcessingStatus {
        NONE,       // 未处理
        PROCESSING, // 处理中
        COMPLETED,  // 已完成
        FAILED      // 处理失败
    }
    
    /**
     * 获取消息处理状态
     */
    public ProcessingStatus getStatus(String messageId) {
        String key = STATUS_KEY_PREFIX + messageId;
        String status = (String) redisTemplate.opsForValue().get(key);
        if (status == null) {
            return ProcessingStatus.NONE;
        }
        return ProcessingStatus.valueOf(status);
    }
    
    /**
     * 尝试标记为处理中
     * 使用 SETNX 保证原子性，防止并发处理
     */
    public boolean tryMarkProcessing(String messageId) {
        String key = STATUS_KEY_PREFIX + messageId;
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, ProcessingStatus.PROCESSING.name(), PROCESSING_TIMEOUT);
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 标记为完成
     */
    public void markCompleted(String messageId) {
        String key = STATUS_KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, ProcessingStatus.COMPLETED.name(), COMPLETED_TTL);
    }
    
    /**
     * 标记为失败（允许重试）
     * 使用较短的 TTL，过期后可以重新处理
     */
    public void markFailed(String messageId) {
        String key = STATUS_KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, ProcessingStatus.FAILED.name(), FAILED_TTL);
    }
}
```

### RocketMQ 延迟消息级别

```java
/**
 * RocketMQ 延迟消息级别
 * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
 */
public class DelayLevel {
    public static final int SECONDS_1 = 1;
    public static final int SECONDS_5 = 2;
    public static final int SECONDS_10 = 3;
    public static final int SECONDS_30 = 4;
    public static final int MINUTES_1 = 5;
    public static final int MINUTES_2 = 6;
    public static final int MINUTES_3 = 7;
    public static final int MINUTES_4 = 8;
    public static final int MINUTES_5 = 9;
    public static final int MINUTES_6 = 10;
    public static final int MINUTES_7 = 11;
    public static final int MINUTES_8 = 12;
    public static final int MINUTES_9 = 13;
    public static final int MINUTES_10 = 14;
    public static final int MINUTES_20 = 15;
    public static final int MINUTES_30 = 16;
    public static final int HOURS_1 = 17;
    public static final int HOURS_2 = 18;
}

// 使用示例：定时发布文章
public void schedulePublish(Long postId, LocalDateTime scheduledAt) {
    long delayMillis = Duration.between(LocalDateTime.now(), scheduledAt).toMillis();
    int delayLevel = calculateDelayLevel(delayMillis);
    
    PostScheduledEvent event = new PostScheduledEvent(postId, scheduledAt);
    eventPublisher.publishDelayed(event, delayLevel);
}
```

---

## 事务与 Redis 一致性处理

### 问题场景

在 `@Transactional` 事务中同时操作数据库和 Redis 会导致数据不一致：

```java
// ❌ 错误示例：事务中操作 Redis
@Transactional
public void likePost(String userId, Long postId) {
    likeRepository.save(like);           // 1. 数据库操作
    redisTemplate.increment(likeCount);  // 2. Redis 操作
    eventPublisher.publish(event);       // 3. 如果这里抛异常
}
// 问题：步骤3抛异常导致事务回滚，但 Redis 已经被修改了
```

### 解决方案：事务提交后回调

```java
@Service
public class PostLikeApplicationService {
    
    private final TransactionTemplate transactionTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * ✅ 正确示例：Redis 操作放在事务提交后
     */
    public void likePost(String userId, Long postId) {
        // 1. 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            PostLike like = new PostLike(idGenerator.nextId(), postId, userId);
            likeRepository.save(like);
        });
        
        // 2. 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
            redisTemplate.opsForValue().set(likeKey, "1");
        } catch (Exception e) {
            // Redis 更新失败不影响主流程，记录日志后续通过定时任务修复
            log.warn("Redis 更新失败，postId={}, userId={}", postId, userId, e);
        }
        
        // 3. 发布事件
        eventPublisher.publish(new PostLikedEvent(postId, userId, authorId));
    }
}
```

### 替代方案：使用 TransactionSynchronization

```java
@Service
public class PostLikeApplicationService {
    
    @Transactional
    public void likePost(String userId, Long postId) {
        // 数据库操作
        PostLike like = new PostLike(idGenerator.nextId(), postId, userId);
        likeRepository.save(like);
        
        // 注册事务提交后回调
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功后执行 Redis 操作
                    try {
                        redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
                        redisTemplate.opsForValue().set(likeKey, "1");
                    } catch (Exception e) {
                        log.warn("Redis 更新失败", e);
                    }
                    // 发布事件
                    eventPublisher.publish(new PostLikedEvent(postId, userId, authorId));
                }
            }
        );
    }
}
```

### 数据修复机制

即使使用了事务提交后回调，Redis 操作仍可能失败。提供两种数据修复方案：

#### 方案一：定时任务修复（简单场景）

```java
@Component
@Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨3点执行
public class DataReconciliationTask {
    
    /**
     * 修复点赞计数不一致
     */
    public void reconcileLikeCounts() {
        // 从数据库查询实际点赞数
        List<PostLikeCount> dbCounts = likeRepository.countGroupByPostId();
        
        for (PostLikeCount count : dbCounts) {
            String key = PostRedisKeys.likeCount(count.getPostId());
            Long redisCounts = (Long) redisTemplate.opsForValue().get(key);
            
            if (redisCounts == null || !redisCounts.equals(count.getCount())) {
                // 修复 Redis 数据
                redisTemplate.opsForValue().set(key, count.getCount());
                log.info("修复点赞计数: postId={}, db={}, redis={}", 
                    count.getPostId(), count.getCount(), redisCounts);
            }
        }
    }
}
```

#### 方案二：CDC 实时同步（推荐）

基于 PostgreSQL WAL 日志的 Change Data Capture（CDC）方案，使用 Debezium + RocketMQ Connect 实时捕获数据库变更并同步到 Redis。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CDC 架构                                            │
│                                                                                  │
│   ┌──────────┐    WAL     ┌──────────┐  RocketMQ  ┌──────────┐                  │
│   │PostgreSQL│ ────────▶  │ Debezium │ ────────▶  │ RocketMQ │                  │
│   │          │            │ Connector│            │          │                  │
│   └──────────┘            └──────────┘            └──────────┘                  │
│                                                         │                        │
│                                                         ▼                        │
│                                                  ┌──────────┐                   │
│                                                  │   CDC    │                   │
│                                                  │ Consumer │                   │
│                                                  └──────────┘                   │
│                                                         │                        │
│                                                         ▼                        │
│                                                  ┌──────────┐                   │
│                                                  │  Redis   │                   │
│                                                  │          │                   │
│                                                  └──────────┘                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

##### PostgreSQL 配置

```sql
-- 启用逻辑复制
ALTER SYSTEM SET wal_level = 'logical';
ALTER SYSTEM SET max_replication_slots = 4;
ALTER SYSTEM SET max_wal_senders = 4;

-- 创建复制槽
SELECT pg_create_logical_replication_slot('debezium_slot', 'pgoutput');

-- 创建发布（指定需要监听的表）
CREATE PUBLICATION ZhiCore_cdc FOR TABLE 
    post_likes, 
    post_favorites, 
    post_stats,
    comment_likes,
    comment_stats,
    user_follows,
    user_follow_stats;
```

##### RocketMQ Connect + Debezium 配置

```json
{
  "name": "ZhiCore-postgres-source",
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "debezium",
  "database.password": "${DB_PASSWORD}",
  "database.dbname": "ZhiCore",
  "database.server.name": "ZhiCore",
  "plugin.name": "pgoutput",
  "publication.name": "ZhiCore_cdc",
  "slot.name": "debezium_slot",
  "table.include.list": "public.post_likes,public.post_stats,public.comment_likes,public.user_follow_stats",
  "transforms": "route",
  "transforms.route.type": "org.apache.rocketmq.connect.transforms.PatternRename",
  "transforms.route.pattern": "ZhiCore\\.public\\.(.*)",
  "transforms.route.replacement": "cdc-$1"
}
```

##### CDC Consumer 实现

```java
@Service
@RocketMQMessageListener(
    topic = "cdc-post_stats",
    consumerGroup = "cdc-redis-sync-group"
)
public class PostStatsCdcConsumer implements RocketMQListener<String> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(String message) {
        DebeziumEvent event = objectMapper.readValue(message, DebeziumEvent.class);
        
        if (event.getOp().equals("c") || event.getOp().equals("u")) {
            // INSERT 或 UPDATE
            PostStats after = event.getAfter(PostStats.class);
            syncPostStatsToRedis(after);
        } else if (event.getOp().equals("d")) {
            // DELETE
            PostStats before = event.getBefore(PostStats.class);
            deletePostStatsFromRedis(before.getPostId());
        }
    }
    
    private void syncPostStatsToRedis(PostStats stats) {
        Long postId = stats.getPostId();
        redisTemplate.opsForValue().set(
            PostRedisKeys.likeCount(postId), stats.getLikeCount());
        redisTemplate.opsForValue().set(
            PostRedisKeys.commentCount(postId), stats.getCommentCount());
        redisTemplate.opsForValue().set(
            PostRedisKeys.favoriteCount(postId), stats.getFavoriteCount());
        redisTemplate.opsForValue().set(
            PostRedisKeys.viewCount(postId), stats.getViewCount());
    }
    
    private void deletePostStatsFromRedis(Long postId) {
        redisTemplate.delete(PostRedisKeys.likeCount(postId));
        redisTemplate.delete(PostRedisKeys.commentCount(postId));
        redisTemplate.delete(PostRedisKeys.favoriteCount(postId));
        redisTemplate.delete(PostRedisKeys.viewCount(postId));
    }
}

/**
 * 监听 post_likes 表变更，同步用户点赞状态
 */
@Service
@RocketMQMessageListener(
    topic = "cdc-post_likes",
    consumerGroup = "cdc-redis-sync-group"
)
public class PostLikesCdcConsumer implements RocketMQListener<String> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(String message) {
        DebeziumEvent event = objectMapper.readValue(message, DebeziumEvent.class);
        
        if (event.getOp().equals("c")) {
            // INSERT - 用户点赞
            PostLike like = event.getAfter(PostLike.class);
            redisTemplate.opsForValue().set(
                PostRedisKeys.userLiked(like.getUserId(), like.getPostId()), "1");
        } else if (event.getOp().equals("d")) {
            // DELETE - 取消点赞
            PostLike like = event.getBefore(PostLike.class);
            redisTemplate.delete(
                PostRedisKeys.userLiked(like.getUserId(), like.getPostId()));
        }
    }
}

/**
 * 监听 user_follow_stats 表变更，同步关注统计
 */
@Service
@RocketMQMessageListener(
    topic = "cdc-user_follow_stats",
    consumerGroup = "cdc-redis-sync-group"
)
public class FollowStatsCdcConsumer implements RocketMQListener<String> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(String message) {
        DebeziumEvent event = objectMapper.readValue(message, DebeziumEvent.class);
        
        if (event.getOp().equals("c") || event.getOp().equals("u")) {
            UserFollowStats stats = event.getAfter(UserFollowStats.class);
            redisTemplate.opsForValue().set(
                UserRedisKeys.followersCount(stats.getUserId()), stats.getFollowersCount());
            redisTemplate.opsForValue().set(
                UserRedisKeys.followingCount(stats.getUserId()), stats.getFollowingCount());
        }
    }
}

/**
 * 监听 comment_stats 表变更
 */
@Service
@RocketMQMessageListener(
    topic = "cdc-comment_stats",
    consumerGroup = "cdc-redis-sync-group"
)
public class CommentStatsCdcConsumer implements RocketMQListener<String> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(String message) {
        DebeziumEvent event = objectMapper.readValue(message, DebeziumEvent.class);
        
        if (event.getOp().equals("c") || event.getOp().equals("u")) {
            CommentStats stats = event.getAfter(CommentStats.class);
            redisTemplate.opsForValue().set(
                CommentRedisKeys.likeCount(stats.getCommentId()), stats.getLikeCount());
            redisTemplate.opsForValue().set(
                CommentRedisKeys.replyCount(stats.getCommentId()), stats.getReplyCount());
        }
    }
}

/**
 * Debezium 事件解析
 */
@Data
public class DebeziumEvent {
    private String op;      // c=create, u=update, d=delete, r=read(snapshot)
    private JsonNode before;
    private JsonNode after;
    private JsonNode source;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public <T> T getBefore(Class<T> clazz) {
        return objectMapper.convertValue(before, clazz);
    }
    
    public <T> T getAfter(Class<T> clazz) {
        return objectMapper.convertValue(after, clazz);
    }
}
```

##### CDC 方案优势

| 特性 | 定时任务 | CDC |
|------|---------|-----|
| 实时性 | 低（分钟/小时级） | 高（秒级） |
| 数据库压力 | 高（全表扫描） | 低（只读 WAL） |
| 一致性 | 最终一致 | 准实时一致 |
| 复杂度 | 低 | 中 |
| 适用场景 | 小数据量、低频更新 | 大数据量、高频更新 |

##### 混合方案（推荐）

生产环境建议同时使用两种方案：
1. **CDC 实时同步**：处理正常情况下的数据变更
2. **定时任务兜底**：处理 CDC 故障期间的数据不一致
```

---

## 分布式锁

```java
@Component
public class DistributedLock {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String LOCK_PREFIX = "lock:";
    
    /**
     * 尝试获取锁
     */
    public boolean tryLock(String lockKey, String requestId, Duration timeout) {
        String key = LOCK_PREFIX + lockKey;
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, requestId, timeout);
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 释放锁（使用 Lua 脚本保证原子性）
     */
    public boolean unlock(String lockKey, String requestId) {
        String key = LOCK_PREFIX + lockKey;
        String script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(key),
            requestId
        );
        return Long.valueOf(1).equals(result);
    }
}
```

---

## 配置管理规范

### 配置分类原则

| 配置类型 | 存储位置 | 说明 | 示例 |
|---------|---------|------|------|
| **静态配置** | 本地 `application.yml` | 启动时加载，变更需重启 | 端口、日志级别、线程池大小 |
| **动态配置** | Nacos 配置中心 | 运行时可热更新 | 限流阈值、开关配置、业务参数 |
| **敏感配置** | 环境变量 / Vault | 密钥、密码等敏感信息 | 数据库密码、JWT 密钥 |
| **服务发现** | Nacos 注册中心 | 服务实例信息 | 服务地址、健康状态 |

### 配置命名规范

```yaml
# 命名格式：{service}.{module}.{property}
# 示例：

# 本地配置 (application.yml)
server:
  port: 8080
  
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/ZhiCore_user
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}  # 敏感配置从环境变量读取

# Nacos 动态配置 (user-service.yml)
ZhiCore:
  user:
    follow:
      max-following: 1000        # 最大关注数
      batch-size: 100            # 批量查询大小
    cache:
      profile-ttl: 10m           # 用户资料缓存时间
    rate-limit:
      follow-per-minute: 30      # 每分钟最大关注次数
```

### Nacos 配置结构

```
nacos-config/
├── common.yml                    # 公共配置（所有服务共享）
├── user-service.yml              # 用户服务配置
├── user-service-dev.yml          # 用户服务开发环境配置
├── user-service-prod.yml         # 用户服务生产环境配置
├── post-service.yml              # 文章服务配置
├── comment-service.yml           # 评论服务配置
├── message-service.yml           # 消息服务配置
├── notification-service.yml      # 通知服务配置
├── search-service.yml            # 搜索服务配置
└── gateway.yml                   # 网关配置
```

### 配置加载优先级

```
优先级从高到低：
1. 命令行参数 (--server.port=8081)
2. 环境变量 (SERVER_PORT=8081)
3. Nacos 配置中心 (user-service-{profile}.yml)
4. Nacos 配置中心 (user-service.yml)
5. Nacos 配置中心 (common.yml)
6. 本地 application-{profile}.yml
7. 本地 application.yml
```

### 动态配置刷新

```java
/**
 * 动态配置类 - 支持 Nacos 热更新
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ZhiCore.user.rate-limit")
@RefreshScope  // 支持动态刷新
public class RateLimitConfig {
    
    private int followPerMinute = 30;
    private int likePerMinute = 60;
    private int commentPerMinute = 20;
}

/**
 * 使用动态配置
 */
@Service
public class FollowService {
    
    private final RateLimitConfig rateLimitConfig;
    
    public void follow(String followerId, String followingId) {
        // 使用动态配置的限流阈值
        if (isRateLimited(followerId, rateLimitConfig.getFollowPerMinute())) {
            throw new RateLimitException("操作过于频繁，请稍后再试");
        }
        // ...
    }
}
```

### 配置变更监听

```java
/**
 * 监听 Nacos 配置变更
 */
@Component
public class ConfigChangeListener {
    
    @NacosConfigListener(dataId = "user-service.yml", groupId = "DEFAULT_GROUP")
    public void onConfigChange(String config) {
        log.info("配置变更: {}", config);
        // 可以在这里做一些配置变更后的处理
    }
}
```

### 敏感配置管理

```yaml
# bootstrap.yml - 敏感配置从环境变量读取
spring:
  datasource:
    password: ${DB_PASSWORD}
    
  redis:
    password: ${REDIS_PASSWORD}

jwt:
  secret: ${JWT_SECRET}

rocketmq:
  name-server: ${ROCKETMQ_NAMESRV}
```

```bash
# Docker Compose 环境变量配置
services:
  user-service:
    environment:
      - DB_PASSWORD=${DB_PASSWORD}
      - REDIS_PASSWORD=${REDIS_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - ROCKETMQ_NAMESRV=rocketmq:9876
```

---

## 数据迁移策略

### 迁移工具选型

| 工具 | 优势 | 劣势 | 适用场景 |
|------|------|------|---------|
| **Flyway** | 简单易用、Spring Boot 集成好 | 回滚能力弱 | 中小型项目 |
| **Liquibase** | 功能强大、支持多种格式 | 学习曲线陡 | 大型项目 |

本项目选用 **Flyway**，原因：
1. 与 Spring Boot 集成简单
2. SQL 脚本直观易懂
3. 团队熟悉度高

### Flyway 配置

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false  # 生产环境禁止乱序执行
```

### 迁移脚本命名规范

```
db/migration/
├── V1__create_users_table.sql
├── V2__create_posts_table.sql
├── V3__create_comments_table.sql
├── V4__add_user_avatar_column.sql
├── V5__create_post_stats_table.sql
├── R__refresh_materialized_views.sql  # 可重复执行的脚本
└── U4__rollback_user_avatar.sql       # 回滚脚本（Flyway Teams）
```

### 迁移脚本示例

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(500),
    bio TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户ID（UUID）';
COMMENT ON COLUMN users.status IS '用户状态：ACTIVE/INACTIVE/BANNED';
```

### 数据迁移流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              数据迁移流程                                        │
│                                                                                  │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐ │
│   │  开发    │ -> │  测试    │ -> │  预发布  │ -> │  生产    │ -> │  验证    │ │
│   │  环境    │    │  环境    │    │  环境    │    │  环境    │    │  数据    │ │
│   └──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘ │
│                                                                                  │
│   步骤：                                                                         │
│   1. 开发环境编写并测试迁移脚本                                                   │
│   2. 测试环境验证迁移脚本                                                        │
│   3. 预发布环境模拟生产数据迁移                                                   │
│   4. 生产环境执行迁移（低峰期）                                                   │
│   5. 验证数据完整性和一致性                                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 大表迁移策略

对于大表（>100万行）的结构变更，采用以下策略：

```sql
-- 1. 创建新表
CREATE TABLE users_new (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    -- 新增字段
    phone VARCHAR(20),
    -- ... 其他字段
);

-- 2. 分批迁移数据（避免长事务）
INSERT INTO users_new (id, username, ...)
SELECT id, username, ...
FROM users
WHERE id > :last_id
ORDER BY id
LIMIT 10000;

-- 3. 同步增量数据（使用触发器或 CDC）
CREATE TRIGGER sync_users_to_new
AFTER INSERT OR UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION sync_to_users_new();

-- 4. 切换表名（短暂锁表）
BEGIN;
ALTER TABLE users RENAME TO users_old;
ALTER TABLE users_new RENAME TO users;
COMMIT;

-- 5. 验证后删除旧表
DROP TABLE users_old;
```

### 回滚方案

```sql
-- 每个迁移脚本都应该有对应的回滚脚本
-- U4__rollback_user_avatar.sql

-- 回滚 V4__add_user_avatar_column.sql
ALTER TABLE users DROP COLUMN IF EXISTS avatar_url;
```

```java
/**
 * 回滚命令（仅限紧急情况）
 */
@Component
public class MigrationRollback {
    
    @Autowired
    private Flyway flyway;
    
    /**
     * 回滚到指定版本（需要 Flyway Teams）
     * 生产环境慎用，建议使用补偿脚本
     */
    public void rollbackTo(String targetVersion) {
        // Flyway Teams 功能
        // flyway.undo();
        
        // 开源版本：执行补偿脚本
        log.warn("执行回滚到版本: {}", targetVersion);
    }
}
```

### 数据校验

```java
/**
 * 迁移后数据校验
 */
@Component
public class MigrationValidator {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * 校验迁移后数据完整性
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        
        // 1. 检查记录数
        Long userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users", Long.class);
        Long expectedCount = getExpectedCount("users");
        if (!userCount.equals(expectedCount)) {
            errors.add("用户表记录数不匹配: expected=" + expectedCount + ", actual=" + userCount);
        }
        
        // 2. 检查外键完整性
        Long orphanPosts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.author_id)",
            Long.class);
        if (orphanPosts > 0) {
            errors.add("存在孤儿文章记录: " + orphanPosts);
        }
        
        // 3. 检查索引
        // ...
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

### 灰度发布流程

> **设计说明：数据迁移灰度发布**
> 
> 大规模数据迁移采用灰度发布策略，逐步切换流量，降低风险。

#### 灰度发布阶段

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              灰度发布流程                                        │
│                                                                                  │
│   阶段1: 准备期（1周）                                                           │
│   ├── 部署新服务（不接收流量）                                                    │
│   ├── 执行数据迁移脚本                                                           │
│   ├── 启动 CDC 双写同步                                                          │
│   └── 验证数据一致性                                                             │
│                                                                                  │
│   阶段2: 灰度期（2-3周）                                                         │
│   ├── 5% 流量 → 新服务（观察 1 周）                                              │
│   ├── 20% 流量 → 新服务（观察 3 天）                                             │
│   ├── 50% 流量 → 新服务（观察 3 天）                                             │
│   └── 100% 流量 → 新服务                                                         │
│                                                                                  │
│   阶段3: 收尾期（1周）                                                           │
│   ├── 停止 CDC 双写                                                              │
│   ├── 下线旧服务                                                                 │
│   └── 清理旧数据                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 灰度开关配置

```java
/**
 * 灰度发布配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ZhiCore.gray")
@RefreshScope
public class GrayReleaseConfig {
    
    /**
     * 灰度比例（0-100）
     */
    private int percentage = 0;
    
    /**
     * 灰度白名单用户（优先走新服务）
     */
    private Set<String> whitelistUsers = new HashSet<>();
    
    /**
     * 灰度黑名单用户（强制走旧服务）
     */
    private Set<String> blacklistUsers = new HashSet<>();
    
    /**
     * 是否启用灰度
     */
    private boolean enabled = false;
}

/**
 * 灰度路由决策器
 */
@Component
public class GrayRouter {
    
    private final GrayReleaseConfig config;
    
    /**
     * 判断请求是否走新服务
     */
    public boolean shouldUseNewService(String userId) {
        if (!config.isEnabled()) {
            return false;
        }
        
        // 白名单优先
        if (config.getWhitelistUsers().contains(userId)) {
            return true;
        }
        
        // 黑名单排除
        if (config.getBlacklistUsers().contains(userId)) {
            return false;
        }
        
        // 按比例灰度（基于用户ID哈希）
        int hash = Math.abs(userId.hashCode() % 100);
        return hash < config.getPercentage();
    }
}
```

#### Gateway 灰度路由

```java
/**
 * Gateway 灰度路由过滤器
 */
@Component
public class GrayRouteFilter implements GlobalFilter, Ordered {
    
    private final GrayRouter grayRouter;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = extractUserId(exchange);
        
        if (userId != null && grayRouter.shouldUseNewService(userId)) {
            // 修改路由目标为新服务
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (route != null) {
                Route newRoute = Route.async()
                    .id(route.getId() + "-gray")
                    .uri(getGrayServiceUri(route))
                    .predicate(route.getPredicate())
                    .filters(route.getFilters())
                    .build();
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, newRoute);
            }
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
```

### 流量切换与监控

#### 流量切换步骤

| 步骤 | 操作 | 验证指标 | 回滚条件 |
|------|------|---------|---------|
| 1 | 部署新服务，灰度 0% | 服务健康检查通过 | - |
| 2 | 灰度 5%（内部用户） | 错误率 < 0.1%，P99 < 500ms | 错误率 > 1% |
| 3 | 灰度 20% | 错误率 < 0.1%，P99 < 500ms | 错误率 > 0.5% |
| 4 | 灰度 50% | 错误率 < 0.1%，P99 < 500ms | 错误率 > 0.3% |
| 5 | 灰度 100% | 错误率 < 0.1%，P99 < 500ms | 错误率 > 0.1% |

#### 监控指标

```yaml
# Prometheus 灰度监控告警规则
groups:
  - name: gray-release-alerts
    rules:
      # 灰度服务错误率告警
      - alert: GrayServiceHighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{service=~".*-gray", status=~"5.."}[5m])) 
          / sum(rate(http_server_requests_seconds_count{service=~".*-gray"}[5m])) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "灰度服务错误率过高"
          description: "灰度服务 {{ $labels.service }} 错误率 {{ $value | humanizePercentage }}"
      
      # 灰度服务延迟告警
      - alert: GrayServiceHighLatency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(http_server_requests_seconds_bucket{service=~".*-gray"}[5m])) by (le, service)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "灰度服务延迟过高"
          description: "灰度服务 {{ $labels.service }} P99 延迟 {{ $value }}s"
      
      # 数据一致性告警
      - alert: DataInconsistency
        expr: data_reconciliation_diff_total > 100
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "数据一致性异常"
          description: "新旧服务数据差异数量 {{ $value }}"
```

#### 数据对账机制

```java
/**
 * 灰度期间数据对账任务
 * 对比新旧服务的数据一致性
 */
@Component
public class GrayDataReconciliationTask {
    
    private final JdbcTemplate oldServiceJdbc;
    private final JdbcTemplate newServiceJdbc;
    private final MeterRegistry meterRegistry;
    
    /**
     * 每 10 分钟执行一次对账
     */
    @Scheduled(fixedRate = 600000)
    public void reconcile() {
        // 1. 对账用户数据
        reconcileUsers();
        
        // 2. 对账文章数据
        reconcilePosts();
        
        // 3. 对账统计数据
        reconcileStats();
    }
    
    private void reconcileUsers() {
        // 抽样对账：随机抽取 1000 条记录
        List<String> sampleIds = oldServiceJdbc.queryForList(
            "SELECT id FROM users ORDER BY RANDOM() LIMIT 1000", String.class);
        
        int diffCount = 0;
        for (String userId : sampleIds) {
            Map<String, Object> oldData = oldServiceJdbc.queryForMap(
                "SELECT * FROM users WHERE id = ?", userId);
            Map<String, Object> newData = newServiceJdbc.queryForMap(
                "SELECT * FROM users WHERE id = ?", userId);
            
            if (!compareData(oldData, newData)) {
                diffCount++;
                log.warn("用户数据不一致: userId={}", userId);
            }
        }
        
        meterRegistry.gauge("data.reconciliation.diff", 
            Tags.of("entity", "user"), diffCount);
    }
    
    private boolean compareData(Map<String, Object> old, Map<String, Object> newData) {
        // 忽略时间戳字段的微小差异
        Set<String> ignoreFields = Set.of("updated_at", "created_at");
        
        for (String key : old.keySet()) {
            if (ignoreFields.contains(key)) continue;
            
            Object oldValue = old.get(key);
            Object newValue = newData.get(key);
            
            if (!Objects.equals(oldValue, newValue)) {
                return false;
            }
        }
        return true;
    }
}
```

#### 快速回滚方案

```java
/**
 * 灰度回滚服务
 */
@Service
public class GrayRollbackService {
    
    private final GrayReleaseConfig config;
    private final NacosConfigService nacosConfigService;
    
    /**
     * 紧急回滚：立即将灰度比例设为 0
     */
    public void emergencyRollback(String reason) {
        log.warn("执行紧急回滚: {}", reason);
        
        // 1. 更新 Nacos 配置，灰度比例设为 0
        config.setPercentage(0);
        config.setEnabled(false);
        
        // 2. 推送配置变更
        nacosConfigService.publishConfig(
            "gray-release.yml", 
            "DEFAULT_GROUP",
            "ZhiCore.gray.enabled: false\nZhiCore.gray.percentage: 0"
        );
        
        // 3. 发送告警通知
        alertService.sendAlert(AlertLevel.CRITICAL, 
            "灰度发布已回滚", reason);
    }
    
    /**
     * 渐进式回滚：逐步降低灰度比例
     */
    public void gradualRollback() {
        int currentPercentage = config.getPercentage();
        
        // 每次降低 10%
        int newPercentage = Math.max(0, currentPercentage - 10);
        config.setPercentage(newPercentage);
        
        log.info("灰度比例调整: {} -> {}", currentPercentage, newPercentage);
    }
}
```


---

## Docker Compose 开发环境

> **说明：复用现有基础设施**
> 
> 当前环境已有 ZhiCore-postgres（5432）、ZhiCore-redis（6500）运行中，新增服务需避免端口冲突。

### 基础设施服务清单

| 服务 | 镜像 | 端口 | 用途 | 备注 |
|------|------|------|------|------|
| PostgreSQL | - | 5432 | 主数据库 | 复用现有 ZhiCore-postgres |
| Redis | - | 6500 | 缓存、分布式锁、会话 | 复用现有 ZhiCore-redis |
| RocketMQ NameServer | apache/rocketmq:5.1.4 | 9876 | 消息队列名称服务 | 新增 |
| RocketMQ Broker | apache/rocketmq:5.1.4 | 10911 | 消息队列代理 | 新增 |
| RocketMQ Dashboard | apacherocketmq/rocketmq-dashboard:latest | 8180 | 消息队列管理界面 | 新增 |
| Nacos | nacos/nacos-server:v2.3.0 | 8848 | 服务注册与配置中心 | 新增 |
| Elasticsearch | elasticsearch:8.11.0 | 9200 | 全文搜索 | 新增 |
| Kibana | kibana:8.11.0 | 5601 | ES 可视化 | 新增 |
| RustFS | ghcr.io/rustfs/rustfs:latest | 9000/9001 | 对象存储（S3 兼容） | 新增，替代 MinIO |
| Prometheus | prom/prometheus:latest | 9090 | 监控指标收集 | 新增 |
| Grafana | grafana/grafana:latest | 3000 | 监控可视化 | 新增 |

### docker-compose.yml

```yaml
version: '3.8'

services:
  # ==================== 消息队列 ====================
  rocketmq-namesrv:
    image: apache/rocketmq:5.1.4
    container_name: ZhiCore-rocketmq-namesrv
    command: sh mqnamesrv
    ports:
      - "9876:9876"
    environment:
      JAVA_OPT_EXT: "-Xms256m -Xmx256m"
    volumes:
      - rocketmq_namesrv_logs:/home/rocketmq/logs
    networks:
      - ZhiCore-network

  rocketmq-broker:
    image: apache/rocketmq:5.1.4
    container_name: ZhiCore-rocketmq-broker
    command: sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/conf/broker.conf
    ports:
      - "10911:10911"
      - "10909:10909"
    environment:
      JAVA_OPT_EXT: "-Xms512m -Xmx512m"
    volumes:
      - rocketmq_broker_logs:/home/rocketmq/logs
      - rocketmq_broker_store:/home/rocketmq/store
      - ./rocketmq/broker.conf:/home/rocketmq/conf/broker.conf
    depends_on:
      - rocketmq-namesrv
    networks:
      - ZhiCore-network

  rocketmq-dashboard:
    image: apacherocketmq/rocketmq-dashboard:latest
    container_name: ZhiCore-rocketmq-dashboard
    ports:
      - "8180:8080"
    environment:
      JAVA_OPTS: "-Drocketmq.namesrv.addr=rocketmq-namesrv:9876"
    depends_on:
      - rocketmq-namesrv
      - rocketmq-broker
    networks:
      - ZhiCore-network

  # ==================== 服务注册与配置中心 ====================
  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: ZhiCore-nacos
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: ""
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_TOKEN: ${NACOS_TOKEN:-SecretKey012345678901234567890123456789012345678901234567890123456789}
      NACOS_AUTH_IDENTITY_KEY: serverIdentity
      NACOS_AUTH_IDENTITY_VALUE: security
    ports:
      - "8848:8848"
      - "9848:9848"
      - "9849:9849"
    volumes:
      - nacos_data:/home/nacos/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/readiness"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - ZhiCore-network

  # ==================== 搜索引擎 ====================
  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: ZhiCore-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - ZhiCore-network

  kibana:
    image: kibana:8.11.0
    container_name: ZhiCore-kibana
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
    networks:
      - ZhiCore-network

  # ==================== 对象存储（RustFS - S3 兼容）====================
  rustfs:
    image: ghcr.io/rustfs/rustfs:latest
    container_name: ZhiCore-rustfs
    command: server /data --console-address ":9001"
    environment:
      RUSTFS_ROOT_USER: ${RUSTFS_USER:-rustfsadmin}
      RUSTFS_ROOT_PASSWORD: ${RUSTFS_PASSWORD:-rustfsadmin123}
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # Console
    volumes:
      - rustfs_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
    networks:
      - ZhiCore-network

  # ==================== 监控 ====================
  prometheus:
    image: prom/prometheus:latest
    container_name: ZhiCore-prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus/rules:/etc/prometheus/rules
      - prometheus_data:/prometheus
    networks:
      - ZhiCore-network

  grafana:
    image: grafana/grafana:latest
    container_name: ZhiCore-grafana
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin123}
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
    networks:
      - ZhiCore-network

# ==================== 数据卷 ====================
volumes:
  rocketmq_namesrv_logs:
  rocketmq_broker_logs:
  rocketmq_broker_store:
  nacos_data:
  es_data:
  rustfs_data:
  prometheus_data:
  grafana_data:

# ==================== 网络 ====================
networks:
  ZhiCore-network:
    external: true  # 使用现有网络，与 ZhiCore-postgres、ZhiCore-redis 互通
```

### 网络配置说明

由于需要与现有的 `ZhiCore-postgres`（5432）和 `ZhiCore-redis`（6500）互通，需要先创建外部网络：

```bash
# 创建共享网络（如果不存在）
docker network create ZhiCore-network

# 将现有容器连接到网络
docker network connect ZhiCore-network ZhiCore-postgres
docker network connect ZhiCore-network ZhiCore-redis
```
    driver: bridge
```

### 配置文件

#### RocketMQ Broker 配置 (rocketmq/broker.conf)

```properties
# Broker 配置
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH

# 允许自动创建 Topic
autoCreateTopicEnable = true
autoCreateSubscriptionGroup = true

# 监听地址
namesrvAddr = rocketmq-namesrv:9876
brokerIP1 = rocketmq-broker

# 存储配置
storePathRootDir = /home/rocketmq/store
storePathCommitLog = /home/rocketmq/store/commitlog
```

#### Prometheus 配置 (prometheus/prometheus.yml)

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets: []

rule_files:
  - /etc/prometheus/rules/*.yml

scrape_configs:
  # Prometheus 自身
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # Spring Boot 应用
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8081'  # user-service
          - 'host.docker.internal:8082'  # post-service
          - 'host.docker.internal:8083'  # comment-service
          - 'host.docker.internal:8084'  # message-service
          - 'host.docker.internal:8085'  # notification-service
          - 'host.docker.internal:8086'  # search-service
          - 'host.docker.internal:8087'  # upload-service
          - 'host.docker.internal:8088'  # admin-service
          - 'host.docker.internal:8080'  # gateway

  # Redis（复用现有 ZhiCore-redis，端口 6500）
  - job_name: 'redis'
    static_configs:
      - targets: ['ZhiCore-redis:6379']  # 容器内部端口

  # PostgreSQL (需要 postgres_exporter)
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']

  # RocketMQ (需要 rocketmq_exporter)
  - job_name: 'rocketmq'
    static_configs:
      - targets: ['rocketmq-exporter:5557']
```

#### 数据库初始化脚本 (init-db/01-init.sql)

> **注意：** 由于复用现有 ZhiCore-postgres，此脚本需要手动在现有数据库中执行。

```sql
-- 创建各服务数据库
CREATE DATABASE ZhiCore_user;
CREATE DATABASE ZhiCore_post;
CREATE DATABASE ZhiCore_comment;
CREATE DATABASE ZhiCore_message;
CREATE DATABASE ZhiCore_notification;

-- 授权（假设用户为 ZhiCore）
GRANT ALL PRIVILEGES ON DATABASE ZhiCore_user TO ZhiCore;
GRANT ALL PRIVILEGES ON DATABASE ZhiCore_post TO ZhiCore;
GRANT ALL PRIVILEGES ON DATABASE ZhiCore_comment TO ZhiCore;
GRANT ALL PRIVILEGES ON DATABASE ZhiCore_message TO ZhiCore;
GRANT ALL PRIVILEGES ON DATABASE ZhiCore_notification TO ZhiCore;

-- 启用扩展
\c ZhiCore_user
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

\c ZhiCore_post
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

\c ZhiCore_comment
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c ZhiCore_message
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c ZhiCore_notification
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### 环境变量文件 (.env)

```bash
# 数据库（复用现有 ZhiCore-postgres）
DB_HOST=ZhiCore-postgres
DB_PORT=5432
DB_USER=ZhiCore
DB_PASSWORD=ZhiCore123

# Redis（复用现有 ZhiCore-redis，宿主机端口 6500）
REDIS_HOST=ZhiCore-redis
REDIS_PORT=6379  # 容器内部端口
REDIS_PASSWORD=redis123

# Nacos
NACOS_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789

# RustFS（S3 兼容对象存储）
RUSTFS_USER=rustfsadmin
RUSTFS_PASSWORD=rustfsadmin123
RUSTFS_ENDPOINT=http://rustfs:9000

# Grafana
GRAFANA_USER=admin
GRAFANA_PASSWORD=admin123

# JWT
JWT_SECRET=your-jwt-secret-key-at-least-256-bits-long

# RocketMQ
ROCKETMQ_NAMESRV=rocketmq-namesrv:9876
```

### 启动命令

```bash
# 创建共享网络（首次运行）
docker network create ZhiCore-network

# 将现有容器连接到网络
docker network connect ZhiCore-network ZhiCore-postgres
docker network connect ZhiCore-network ZhiCore-redis

# 启动所有新增服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f [service_name]

# 停止所有服务
docker-compose down

# 停止并删除数据卷（慎用）
docker-compose down -v

# 只启动核心基础设施（不含监控）
docker-compose up -d rocketmq-namesrv rocketmq-broker nacos elasticsearch rustfs

# 重启单个服务
docker-compose restart [service_name]
```

### 服务访问地址

| 服务 | 地址 | 默认账号 | 备注 |
|------|------|---------|------|
| PostgreSQL | localhost:5432 | ZhiCore/ZhiCore123 | 复用现有 |
| Redis | localhost:6500 | password: redis123 | 复用现有 |
| Nacos Console | http://localhost:8848/nacos | nacos/nacos | 新增 |
| RocketMQ Dashboard | http://localhost:8180 | - | 新增 |
| Kibana | http://localhost:5601 | - | 新增 |
| RustFS Console | http://localhost:9001 | rustfsadmin/rustfsadmin123 | 新增，S3 兼容 |
| RustFS S3 API | http://localhost:9000 | - | 新增 |
| Prometheus | http://localhost:9090 | - | 新增 |
| Grafana | http://localhost:3000 | admin/admin123 | 新增 |
| Elasticsearch | http://localhost:9200 | - | 新增 |

### 健康检查

```bash
# 检查 PostgreSQL（复用现有）
docker exec ZhiCore-postgres pg_isready -U ZhiCore

# 检查 Redis（复用现有）
docker exec ZhiCore-redis redis-cli -a redis123 ping

# 检查 Nacos
curl http://localhost:8848/nacos/v1/console/health/readiness

# 检查 Elasticsearch
curl http://localhost:9200/_cluster/health

# 检查 RocketMQ
curl http://localhost:8180/cluster/list.query

# 检查 RustFS
curl http://localhost:9000/minio/health/live
```

### Spring Boot 应用配置示例

```yaml
# application.yml - 连接复用的基础设施
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:ZhiCore-postgres}:${DB_PORT:5432}/ZhiCore_user
    username: ${DB_USER:ZhiCore}
    password: ${DB_PASSWORD:ZhiCore123}
  
  data:
    redis:
      host: ${REDIS_HOST:ZhiCore-redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis123}

# RocketMQ
rocketmq:
  name-server: ${ROCKETMQ_NAMESRV:rocketmq-namesrv:9876}

# RustFS（S3 兼容）
rustfs:
  endpoint: ${RUSTFS_ENDPOINT:http://rustfs:9000}
  access-key: ${RUSTFS_USER:rustfsadmin}
  secret-key: ${RUSTFS_PASSWORD:rustfsadmin123}
  bucket: ZhiCore-uploads
```
