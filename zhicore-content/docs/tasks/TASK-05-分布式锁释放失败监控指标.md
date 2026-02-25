# TASK-05: 分布式锁释放失败监控指标埋点

## 背景

`RedissonLockManagerImpl.java:141` 中，释放锁失败时仅记录日志，缺少监控指标埋点。
锁释放失败是严重的运行时异常信号，需要通过 Prometheus 指标暴露以便告警。

## 涉及文件

- `com.zhicore.content.infrastructure.cache.RedissonLockManagerImpl` — 第 141 行 TODO
- `com.zhicore.content.infrastructure.monitoring.MetricsCollector` — 已有 Micrometer 指标收集器

## 实现要求

### 1. 注入 MeterRegistry

在 `RedissonLockManagerImpl` 中注入 `MeterRegistry`（或复用 `MetricsCollector`）。

### 2. 定义 Counter

```java
private final Counter lockReleaseFailureCounter;

// 构造函数中初始化
this.lockReleaseFailureCounter = Counter.builder("content.lock.release.failure")
    .description("分布式锁释放失败次数")
    .tag("component", "redisson")
    .register(meterRegistry);
```

### 3. 在异常处理中递增计数器

```java
} catch (Exception e) {
    log.error("Failed to release lock: key={}, error={}", key, e.getMessage(), e);
    lockReleaseFailureCounter.increment();
}
```

### 4. 可选：增加 lock key 维度的 tag

如果需要区分不同业务锁的失败情况，可以使用动态 tag：

```java
Metrics.counter("content.lock.release.failure", "key", key).increment();
```

注意：动态 tag 会增加指标基数，仅在锁 key 种类有限时使用。

## 验收标准

- [ ] 锁释放失败时 `content.lock.release.failure` 计数器递增
- [ ] 指标可通过 `/actuator/prometheus` 端点查询到
- [ ] 原有日志保留不变
- [ ] 单元测试：模拟释放锁异常，验证计数器递增

## 回滚策略

- 纯新增逻辑，revert commit 即可
- 指标不影响业务逻辑
