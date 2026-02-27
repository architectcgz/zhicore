# 代码 Review：fb6e79d fix(ranking): 修复 code review 报告的 11 项问题

## Review 信息

| 项目 | 内容 |
|------|------|
| 审查范围 | commit fb6e79d 涉及的 8 个文件（135+/137-） |
| 审查基准 | 上次 review（code-review-92d9e9d.md）的 11 项问题 |
| 审查日期 | 2026-02-27 |
| 审查人 | Architecture Review (Claude Opus 4.6) |

---

## 上次问题修复状态

| 编号 | 级别 | 问题标题 | 状态 | 说明 |
|------|------|---------|------|------|
| 1 | 高 | 刷写目标写 Redis 而非 MongoDB | ⚠️ TODO 标记 | 添加了 TODO 注释，合理——MongoDB 持久化层未就绪 |
| 2 | 高 | Condition 未实际用于刷写线程协调 | ✅ 已修复 | 移除 Condition，阈值触发直接调用 flush() |
| 3 | 高 | WAL 兜底机制缺失 | ⚠️ TODO 标记 | 添加了连续失败计数器 + TODO 注释 |
| 4 | 中 | 缓存命中/未命中指标语义错误 | ✅ 已修复 | 改为基于 Redis 实际查询结果判断 |
| 5 | 中 | @Value 无法 Nacos 动态刷新 | ✅ 已修复 | 改用 @ConfigurationProperties |
| 6 | 中 | 只有一个定时任务接入 Timer | ✅ 已修复 | 三个任务各自使用带 type 标签的 Timer |
| 7 | 中 | eventConsumeCounter 缺少标签 | ✅ 已修复 | 添加 entityType 标签 |
| 8 | 中 | getAvatarUrl → getAvatarId 语义 | ✅ 已标注 | 补充了注释说明 |
| 9 | 低 | Counter 名称 .total 后缀 | ✅ 已修复 | 移除 .total 后缀 |
| 10 | 低 | 文件末尾缺少换行 | ✅ 已修复 | |
| 11 | 低 | 测试未验证 Timer | ⏭️ 跳过 | 可接受 |

---

## 本次修复引入的新问题

### 新问题 1：addScore() 热路径上每次都执行 Counter.builder().register()

- 级别：**高**
- 涉及文件：`ScoreBufferService.java:109-112`
- 问题描述：

```java
Counter.builder("ranking.event.consume")
        .tag("entityType", entityType)
        .register(meterRegistry)
        .increment();
```

每次 `addScore()` 调用都会执行 `Counter.builder().register()`。虽然 Micrometer 的 `register()` 内部有缓存（相同 name+tags 返回同一实例），但每次都要经过 builder 构建、tag 匹配、缓存查找的开销。在高并发场景下（预估峰值 2000 QPS），这个热路径上的额外开销不可忽视。

- 影响：`addScore()` 是消费线程的热路径，不必要的对象分配和缓存查找增加 GC 压力和延迟
- 修正建议：用 `ConcurrentHashMap<String, Counter>` 做本地缓存，`computeIfAbsent` 懒注册：

```java
private final Map<String, Counter> eventCounters = new ConcurrentHashMap<>();

// addScore() 中
eventCounters.computeIfAbsent(entityType, type ->
    Counter.builder("ranking.event.consume")
        .tag("entityType", type)
        .register(meterRegistry)
).increment();
```

### 新问题 2：阈值触发 flush() 在消费线程上同步执行，可能阻塞 MQ 消费

- 级别：**高**
- 涉及文件：`ScoreBufferService.java:117-118`
- 问题描述：当事件计数达到阈值时，`addScore()` 直接调用 `flush()`，而 `flush()` 内部会获取 `flushLock` 并执行 Redis 写入。触发阈值的 MQ 消费线程会被阻塞在刷写操作上。如果此时定时刷写也在执行，消费线程还会在 `flushLock.lock()` 上排队等待
- 影响：MQ 消费线程被阻塞 → 消费吞吐下降 → 消息积压 → RocketMQ 可能触发重平衡
- 修正建议：阈值触发时用 `tryLock()` 非阻塞尝试，获取不到说明已有刷写在执行，直接跳过：

```java
if (count >= bufferProperties.getBatchSize()) {
    if (flushLock.tryLock()) {
        try { doFlush(); } finally { flushLock.unlock(); }
    }
}
```

### 新问题 3：RankingQueryService 缓存 miss 回源 MongoDB 后未回填 Redis

- 级别：**中**
- 涉及文件：`RankingQueryService.java:65-70`
- 问题描述：修复后的逻辑在 Redis 查询为空时回源 MongoDB，但没有将 MongoDB 的结果回填到 Redis。下次相同查询仍然会 miss，持续穿透到 MongoDB。架构文档（02-核心功能设计 5 节）明确要求"miss 时回源 MongoDB 并回填缓存"
- 影响：同一月份的排行被反复查询时，每次都穿透到 MongoDB，缓存形同虚设
- 修正建议：回源 MongoDB 后将结果写回 Redis（通过 `postRankingService` 的写入方法），并设置合理 TTL

### 新问题 4：RankingRefreshScheduler.snapshotTimer() 每次调用都 register 新 Timer

- 级别：**中**
- 涉及文件：`RankingRefreshScheduler.java:47-50`
- 问题描述：

```java
private Timer snapshotTimer(String type) {
    return Timer.builder("ranking.snapshot.duration")
            .tag("type", type)
            .register(meterRegistry);
}
```

与新问题 1 类似，每次定时任务执行都会调用 `Timer.builder().register()`。虽然定时任务频率低（每小时/每天），性能影响不大，但风格上不一致——同一个 Timer 实例应该复用。

- 修正建议：在构造函数中预注册三个 Timer（post/creator/topic），存为字段

### 新问题 5：RankingWeightProperties 的 @ConfigurationProperties 动态刷新需要确认 Spring Cloud Alibaba 版本

- 级别：**低**
- 涉及文件：`RankingWeightProperties.java`
- 问题描述：`@ConfigurationProperties` 在 Spring Cloud Alibaba 2021.x+ 版本中支持 Nacos 配置变更自动刷新（无需 `@RefreshScope`），但在更早版本中需要额外配置。类注释声称"无需 @RefreshScope"，需确认项目使用的 Spring Cloud Alibaba 版本是否支持
- 修正建议：确认 `pom.xml` 中 Spring Cloud Alibaba 版本 >= 2021.0.1.0，或在集成测试中验证动态刷新生效

### 新问题 6：ScoreBufferServiceTest batchSize 设为 MAX_VALUE 导致阈值触发逻辑完全未被测试

- 级别：**低**
- 涉及文件：`ScoreBufferServiceTest.java:43`
- 问题描述：`bufferProperties.setBatchSize(Integer.MAX_VALUE)` 使得阈值触发永远不会发生，避免了并发测试中的干扰。但这也意味着阈值触发 flush 的路径完全没有测试覆盖
- 修正建议：新增一个专门的测试方法，设置小 batchSize（如 3），验证第 3 次 addScore 后自动触发 flush

---

## 问题汇总

| 编号 | 级别 | 问题标题 | 涉及文件 |
|------|------|---------|---------|
| 新-1 | 高 | addScore() 热路径每次 Counter.builder().register() | ScoreBufferService |
| 新-2 | 高 | 阈值触发 flush() 同步阻塞消费线程 | ScoreBufferService |
| 新-3 | 中 | 缓存 miss 回源后未回填 Redis | RankingQueryService |
| 新-4 | 中 | snapshotTimer() 每次调用都 register | RankingRefreshScheduler |
| 新-5 | 低 | @ConfigurationProperties 动态刷新需确认版本 | RankingWeightProperties |
| 新-6 | 低 | batchSize=MAX_VALUE 导致阈值触发未被测试 | ScoreBufferServiceTest |

| 级别 | 数量 |
|------|------|
| 高 | 2 |
| 中 | 2 |
| 低 | 2 |

---

*审查完成于 2026-02-27*
