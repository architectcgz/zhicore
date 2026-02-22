# 监控和告警指南

本文档描述了 Post MongoDB Hybrid Storage 的监控和告警实现方案。

## 架构概述

监控和告警系统基于 Spring Cloud Alibaba 生态，集成了以下组件：

1. **Micrometer + Prometheus**: 指标收集和暴露
2. **Sentinel**: 流量控制和降级
3. **Grafana**: 可视化监控面板
4. **日志告警**: 基于日志的告警通知

## 监控指标

### 数据库查询指标

#### PostgreSQL 指标

- `post.database.query.duration{database="postgresql"}`: 查询响应时间（Timer）
  - P50, P95, P99 百分位数
- `post.database.query.total{database="postgresql",status="success"}`: 成功查询数（Counter）
- `post.database.query.total{database="postgresql",status="failure"}`: 失败查询数（Counter）
- `post.database.query.slow.total{database="postgresql"}`: 慢查询数（Counter）

#### MongoDB 指标

- `post.database.query.duration{database="mongodb"}`: 查询响应时间（Timer）
  - P50, P95, P99 百分位数
- `post.database.query.total{database="mongodb",status="success"}`: 成功查询数（Counter）
- `post.database.query.total{database="mongodb",status="failure"}`: 失败查询数（Counter）
- `post.database.query.slow.total{database="mongodb"}`: 慢查询数（Counter）

### 双写操作指标

- `post.dual.write.duration`: 双写操作响应时间（Timer）
  - P50, P95, P99 百分位数
- `post.dual.write.total{status="success"}`: 双写成功数（Counter）
- `post.dual.write.total{status="failure"}`: 双写失败数（Counter）

### 数据一致性指标

- `post.data.inconsistency.total`: 数据不一致检测数（Counter）
- `post.consistency.check.total`: 一致性检查执行数（Counter）

## Prometheus 配置

### 端点配置

Prometheus 指标通过 Spring Boot Actuator 暴露：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

### 访问端点

- 健康检查: `http://localhost:8080/actuator/health`
- 指标列表: `http://localhost:8080/actuator/metrics`
- Prometheus 格式: `http://localhost:8080/actuator/prometheus`

### Prometheus 抓取配置

在 `prometheus.yml` 中添加：

```yaml
scrape_configs:
  - job_name: 'ZhiCore-post-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['ZhiCore-post:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
```

## Grafana 监控面板

### 面板 1: PostgreSQL 性能监控

**指标查询**:

1. **平均查询时间**:
   ```promql
   rate(post_database_query_duration_seconds_sum{database="postgresql"}[5m]) 
   / 
   rate(post_database_query_duration_seconds_count{database="postgresql"}[5m]) * 1000
   ```

2. **查询成功率**:
   ```promql
   rate(post_database_query_total{database="postgresql",status="success"}[5m]) 
   / 
   (rate(post_database_query_total{database="postgresql",status="success"}[5m]) 
   + rate(post_database_query_total{database="postgresql",status="failure"}[5m])) * 100
   ```

3. **慢查询数量**:
   ```promql
   increase(post_database_query_slow_total{database="postgresql"}[5m])
   ```

4. **P95 响应时间**:
   ```promql
   histogram_quantile(0.95, rate(post_database_query_duration_seconds_bucket{database="postgresql"}[5m])) * 1000
   ```

### 面板 2: MongoDB 性能监控

**指标查询**:

1. **平均查询时间**:
   ```promql
   rate(post_database_query_duration_seconds_sum{database="mongodb"}[5m]) 
   / 
   rate(post_database_query_duration_seconds_count{database="mongodb"}[5m]) * 1000
   ```

2. **查询成功率**:
   ```promql
   rate(post_database_query_total{database="mongodb",status="success"}[5m]) 
   / 
   (rate(post_database_query_total{database="mongodb",status="success"}[5m]) 
   + rate(post_database_query_total{database="mongodb",status="failure"}[5m])) * 100
   ```

3. **慢查询数量**:
   ```promql
   increase(post_database_query_slow_total{database="mongodb"}[5m])
   ```

4. **P95 响应时间**:
   ```promql
   histogram_quantile(0.95, rate(post_database_query_duration_seconds_bucket{database="mongodb"}[5m])) * 1000
   ```

### 面板 3: 双写操作监控

**指标查询**:

1. **双写成功率**:
   ```promql
   rate(post_dual_write_total{status="success"}[5m]) 
   / 
   (rate(post_dual_write_total{status="success"}[5m]) 
   + rate(post_dual_write_total{status="failure"}[5m])) * 100
   ```

2. **双写失败率**:
   ```promql
   rate(post_dual_write_total{status="failure"}[5m]) 
   / 
   (rate(post_dual_write_total{status="success"}[5m]) 
   + rate(post_dual_write_total{status="failure"}[5m])) * 100
   ```

3. **双写平均响应时间**:
   ```promql
   rate(post_dual_write_duration_seconds_sum[5m]) 
   / 
   rate(post_dual_write_duration_seconds_count[5m]) * 1000
   ```

4. **双写 P99 响应时间**:
   ```promql
   histogram_quantile(0.99, rate(post_dual_write_duration_seconds_bucket[5m])) * 1000
   ```

5. **数据不一致数量**:
   ```promql
   increase(post_data_inconsistency_total[5m])
   ```

### 告警规则

在 Grafana 中配置告警规则：

1. **PostgreSQL 查询性能告警**:
   - 条件: 平均查询时间 > 500ms 持续 5 分钟
   - 严重程度: Warning

2. **MongoDB 查询性能告警**:
   - 条件: 平均查询时间 > 500ms 持续 5 分钟
   - 严重程度: Warning

3. **双写失败率告警**:
   - 条件: 失败率 > 5% 持续 5 分钟
   - 严重程度: Critical

4. **数据不一致告警**:
   - 条件: 不一致数量 > 0
   - 严重程度: High

## Sentinel 降级配置

### 降级规则

Sentinel 规则已配置在 Nacos 中：

- 流控规则: `ZhiCore-post-flow-rules.json`
- 降级规则: `ZhiCore-post-degrade-rules.json`

### 降级场景

1. **MongoDB 连接失败降级**:
   - 触发条件: MongoDB 连接异常率 > 50%
   - 降级策略: 仅使用 PostgreSQL
   - 恢复时间: 10 秒

2. **双写失败率过高降级**:
   - 触发条件: 双写失败率 > 10%
   - 降级策略: 暂停双写，仅写入 PostgreSQL
   - 恢复时间: 30 秒

3. **查询性能下降降级**:
   - 触发条件: 平均响应时间 > 1000ms
   - 降级策略: 启用缓存，减少数据库查询
   - 恢复时间: 60 秒

### Sentinel Dashboard

访问 Sentinel Dashboard 查看实时监控：

```
http://localhost:8858
```

## 告警通知

### 告警类型

1. **数据不一致告警** (HIGH)
2. **MongoDB 连接失败告警** (CRITICAL)
3. **PostgreSQL 连接失败告警** (CRITICAL)
4. **查询性能下降告警** (MEDIUM)
5. **存储空间不足告警** (HIGH)
6. **双写失败率过高告警** (HIGH)
7. **慢查询告警** (MEDIUM)

### 告警去重

告警系统实现了去重机制，相同类型的告警在 5 分钟内只会发送一次。

### 告警通知渠道

当前实现了基于日志的告警通知。可以扩展以下通知渠道：

- 邮件通知
- 短信通知
- 钉钉/企业微信通知
- Slack/Teams 通知

## 配置参数

在 `application.yml` 中配置监控参数：

```yaml
monitoring:
  # 查询性能阈值（毫秒）
  query:
    performance:
      threshold: 500
  
  # 双写失败率阈值（0-1之间）
  dual-write:
    failure-rate:
      threshold: 0.05
  
  # 慢查询数量阈值
  slow-query:
    count:
      threshold: 100
  
  # 存储空间告警阈值（百分比）
  storage:
    threshold: 80
```

## 使用示例

### 在代码中使用监控

```java
@Service
@RequiredArgsConstructor
public class PostService {
    
    private final MonitoringService monitoringService;
    
    public Post getPost(String postId) {
        // 监控 PostgreSQL 查询
        return monitoringService.monitorPostgresQuery("selectById", () -> {
            return postRepository.selectById(postId);
        });
    }
    
    public void createPost(Post post) {
        // 监控双写操作
        monitoringService.monitorDualWrite("createPost", () -> {
            dualStorageManager.createPost(post.getMetadata(), post.getContent());
        });
    }
}
```

### 使用 @Timed 注解

```java
@Service
public class PostService {
    
    @Timed(value = "post.service.getPost", description = "Get post by ID")
    public Post getPost(String postId) {
        return postRepository.selectById(postId);
    }
}
```

### 使用 @SentinelResource 注解

```java
@Service
public class DualStorageManagerImpl implements DualStorageManager {
    
    @SentinelResource(
        value = "dualWrite",
        fallback = "handleDualWriteFailure",
        fallbackClass = DualStorageFallbackHandler.class
    )
    public String createPost(PostMetadata metadata, PostContent content) {
        // 双写逻辑
    }
}
```

## 故障排查

### 查看监控指标

1. 访问 Actuator 端点查看指标：
   ```bash
   curl http://localhost:8080/actuator/metrics/post.database.query.duration
   ```

2. 查看 Prometheus 格式的所有指标：
   ```bash
   curl http://localhost:8080/actuator/prometheus | grep post_
   ```

### 查看告警日志

告警信息会记录在应用日志中，可以通过以下方式查看：

```bash
# 查看所有告警
grep "告警通知" logs/application.log

# 查看特定类型的告警
grep "数据不一致告警" logs/application.log
```

### 查看 Sentinel 降级日志

```bash
# 查看降级日志
grep "Sentinel Fallback" logs/application.log
```

## 最佳实践

1. **定期检查监控面板**: 每天查看 Grafana 面板，了解系统运行状况
2. **及时响应告警**: 收到告警后及时处理，避免问题扩大
3. **调整阈值**: 根据实际情况调整告警阈值，避免误报
4. **定期清理数据**: 定期清理过期的监控数据和日志
5. **备份配置**: 定期备份 Grafana 面板配置和 Sentinel 规则

## 参考资料

- [Micrometer 文档](https://micrometer.io/docs)
- [Prometheus 文档](https://prometheus.io/docs/)
- [Grafana 文档](https://grafana.com/docs/)
- [Sentinel 文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
