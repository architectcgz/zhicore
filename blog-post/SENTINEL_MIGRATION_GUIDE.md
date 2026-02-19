# 使用 Sentinel 实现熔断降级（Spring Cloud Alibaba）

## 为什么选择 Sentinel

既然你使用了 Spring Cloud Alibaba，Sentinel 是最佳选择：

- ✅ **阿里云生态**：与 Nacos、RocketMQ 等无缝集成
- ✅ **功能强大**：流量控制、熔断降级、系统负载保护、热点参数限流
- ✅ **可视化控制台**：实时监控和规则配置
- ✅ **生产验证**：阿里巴巴双十一验证
- ✅ **中文文档**：完善的中文文档和社区支持

## Sentinel vs 手动实现

| 特性 | 手动实现 | Sentinel |
|------|---------|----------|
| 流量控制 | ❌ | ✅ QPS/并发数/关联/链路 |
| 熔断降级 | ⚠️ 简单 | ✅ 慢调用比例/异常比例/异常数 |
| 系统保护 | ❌ | ✅ CPU/Load/RT/线程数/入口QPS |
| 热点限流 | ❌ | ✅ 参数级别限流 |
| 实时监控 | ❌ | ✅ 可视化控制台 |
| 规则持久化 | ❌ | ✅ Nacos/Apollo/ZK |
| 集群流控 | ❌ | ✅ Token Server |

## 迁移步骤

### 1. 添加依赖

```xml
<properties>
    <spring-cloud-alibaba.version>2022.0.0.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Sentinel 核心依赖 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    </dependency>
    
    <!-- Sentinel 数据源 - Nacos（规则持久化） -->
    <dependency>
        <groupId>com.alibaba.csp</groupId>
        <artifactId>sentinel-datasource-nacos</artifactId>
    </dependency>
    
    <!-- Sentinel 与 Spring Cloud Gateway 集成（如果使用网关） -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
    </dependency>
</dependencies>
```

### 2. 配置文件

```yaml
# application.yml
spring:
  cloud:
    sentinel:
      # 是否启用 Sentinel
      enabled: true
      
      # 是否饥饿加载（启动时立即初始化）
      eager: true
      
      # Sentinel 控制台地址
      transport:
        dashboard: localhost:8080
        port: 8719  # 与控制台通信的端口
      
      # 日志配置
      log:
        dir: logs/sentinel
        switch-pid: true
      
      # 数据源配置（规则持久化到 Nacos）
      datasource:
        # 流控规则
        flow:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            namespace: ${spring.cloud.nacos.config.namespace}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        
        # 降级规则
        degrade:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            namespace: ${spring.cloud.nacos.config.namespace}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade
        
        # 系统规则
        system:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            namespace: ${spring.cloud.nacos.config.namespace}
            data-id: ${spring.application.name}-system-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: system
        
        # 热点参数规则
        param-flow:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            namespace: ${spring.cloud.nacos.config.namespace}
            data-id: ${spring.application.name}-param-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: param-flow

# Nacos 配置
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        namespace: dev
```

### 3. 在 Nacos 中配置规则

#### 流控规则（blog-post-flow-rules）

```json
[
  {
    "resource": "getPostDetail",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  },
  {
    "resource": "createPost",
    "limitApp": "default",
    "grade": 1,
    "count": 50,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

**字段说明**：
- `resource`: 资源名称
- `grade`: 限流阈值类型（0=线程数，1=QPS）
- `count`: 限流阈值
- `strategy`: 流控模式（0=直接，1=关联，2=链路）
- `controlBehavior`: 流控效果（0=快速失败，1=Warm Up，2=排队等待）

#### 降级规则（blog-post-degrade-rules）

```json
[
  {
    "resource": "mongodbQuery",
    "grade": 0,
    "count": 500,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 1000,
    "slowRatioThreshold": 0.5
  },
  {
    "resource": "mongodbQuery",
    "grade": 1,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 1000
  }
]
```

**字段说明**：
- `grade`: 降级策略（0=慢调用比例，1=异常比例，2=异常数）
- `count`: 阈值（慢调用RT毫秒数/异常比例/异常数）
- `timeWindow`: 熔断时长（秒）
- `minRequestAmount`: 最小请求数
- `slowRatioThreshold`: 慢调用比例阈值

#### 系统规则（blog-post-system-rules）

```json
[
  {
    "avgRt": 200,
    "maxThread": -1,
    "qps": -1,
    "highestSystemLoad": 8.0,
    "highestCpuUsage": 0.8
  }
]
```

### 4. 重构代码使用 Sentinel

```java
package com.blog.post.infrastructure.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.blog.post.domain.exception.DualStorageException;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.repository.PostRepository;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import com.blog.post.infrastructure.mongodb.repository.PostContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 使用 Sentinel 的双存储管理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentinelDualStorageManager implements DualStorageManager {

    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;

    /**
     * 创建文章（带流控）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SentinelResource(
        value = "createPost",
        blockHandler = "createPostBlockHandler",
        fallback = "createPostFallback"
    )
    public String createPost(Post post, PostContent content) {
        String postId = String.valueOf(post.getId());
        
        // 阶段1: 写入 PostgreSQL
        postRepository.save(post);
        log.info("Saved post metadata to PostgreSQL: {}", postId);
        
        // 阶段2: 写入 MongoDB
        content.setPostId(postId);
        postContentRepository.save(content);
        log.info("Saved post content to MongoDB: {}", postId);
        
        return postId;
    }

    /**
     * 获取文章详情（带熔断降级）
     */
    @Override
    @SentinelResource(
        value = "getPostDetail",
        blockHandler = "getPostDetailBlockHandler",
        fallback = "getPostDetailFallback"
    )
    public PostDetail getPostFullDetail(String postId) {
        // 并行查询 PostgreSQL 和 MongoDB
        CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(() -> {
            Optional<Post> postOpt = postRepository.findById(postId);
            return postOpt.orElseThrow(() -> 
                new DualStorageException("Post not found: " + postId));
        });
        
        CompletableFuture<PostContent> contentFuture = CompletableFuture.supplyAsync(() -> 
            queryMongoDBWithSentinel(postId)
        );
        
        // 等待两个查询完成
        CompletableFuture.allOf(postFuture, contentFuture).join();
        
        try {
            Post post = postFuture.get();
            PostContent content = contentFuture.get();
            return new PostDetail(post, content);
        } catch (Exception e) {
            throw new DualStorageException("Failed to get post detail", e);
        }
    }

    /**
     * MongoDB 查询（带熔断）
     */
    @SentinelResource(
        value = "mongodbQuery",
        blockHandler = "mongodbQueryBlockHandler",
        fallback = "mongodbQueryFallback"
    )
    private PostContent queryMongoDBWithSentinel(String postId) {
        Optional<PostContent> contentOpt = postContentRepository.findByPostId(postId);
        return contentOpt.orElseThrow(() -> 
            new DualStorageException("Content not found: " + postId));
    }

    // ==================== Block Handlers（流控/熔断触发） ====================

    /**
     * 创建文章流控处理
     */
    public String createPostBlockHandler(Post post, PostContent content, BlockException ex) {
        log.warn("Create post blocked by Sentinel: {}", ex.getRule());
        throw new DualStorageException("Service busy, please try again later");
    }

    /**
     * 获取文章详情流控处理
     */
    public PostDetail getPostDetailBlockHandler(String postId, BlockException ex) {
        log.warn("Get post detail blocked by Sentinel for post: {}, rule: {}", 
            postId, ex.getRule());
        
        // 流控时返回降级数据
        return getPostDetailFallback(postId, ex);
    }

    /**
     * MongoDB 查询流控处理
     */
    public PostContent mongodbQueryBlockHandler(String postId, BlockException ex) {
        log.warn("MongoDB query blocked by Sentinel for post: {}", postId);
        throw new DualStorageException("MongoDB service busy: " + postId);
    }

    // ==================== Fallback Methods（异常降级） ====================

    /**
     * 创建文章异常降级
     */
    public String createPostFallback(Post post, PostContent content, Throwable ex) {
        log.error("Failed to create post, fallback triggered", ex);
        throw new DualStorageException("Service temporarily unavailable", ex);
    }

    /**
     * 获取文章详情异常降级（仅返回元数据）
     */
    public PostDetail getPostDetailFallback(String postId, Throwable ex) {
        log.warn("MongoDB unavailable, returning metadata only for post: {}", postId);
        
        try {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent()) {
                return new PostDetail(postOpt.get(), null, true);
            }
        } catch (Exception pgEx) {
            log.error("Failed to get metadata from PostgreSQL", pgEx);
        }
        
        throw new DualStorageException("Post not found: " + postId);
    }

    /**
     * MongoDB 查询异常降级
     */
    public PostContent mongodbQueryFallback(String postId, Throwable ex) {
        log.warn("MongoDB query failed for post: {}", postId, ex);
        throw new DualStorageException("Content temporarily unavailable: " + postId);
    }
}
```

### 5. Sentinel 注解说明

```java
@SentinelResource(
    value = "resourceName",           // 资源名称（必填）
    blockHandler = "blockMethod",     // 流控/熔断处理方法
    blockHandlerClass = Handler.class,// 流控处理类（可选）
    fallback = "fallbackMethod",      // 异常降级方法
    fallbackClass = Fallback.class,   // 异常降级类（可选）
    exceptionsToIgnore = {Exception.class} // 忽略的异常
)
```

**注意事项**：
- `blockHandler`: 处理 `BlockException`（流控、熔断触发）
- `fallback`: 处理业务异常（如 MongoDB 连接失败）
- 方法签名必须与原方法一致，最后加一个异常参数

### 6. 启动 Sentinel 控制台

```bash
# 下载 Sentinel 控制台
wget https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar

# 启动控制台
java -Dserver.port=8080 \
     -Dcsp.sentinel.dashboard.server=localhost:8080 \
     -Dproject.name=sentinel-dashboard \
     -jar sentinel-dashboard-1.8.6.jar
```

访问：http://localhost:8080（默认用户名/密码：sentinel/sentinel）

### 7. 控制台功能

#### 实时监控
- 查看每个资源的 QPS、响应时间、异常数
- 查看机器列表和资源调用链路

#### 流控规则
- 针对资源设置 QPS 或并发线程数限制
- 支持直接、关联、链路三种流控模式
- 支持快速失败、Warm Up、排队等待三种流控效果

#### 降级规则
- 慢调用比例：RT 超过阈值的比例
- 异常比例：异常占比超过阈值
- 异常数：异常数超过阈值

#### 热点规则
- 针对参数级别的限流
- 例如：限制单个用户的访问频率

#### 系统规则
- CPU 使用率
- 系统负载
- 平均 RT
- 入口 QPS
- 并发线程数

### 8. 与 Nacos 集成（规则持久化）

在 Nacos 中创建配置：

**Data ID**: `blog-post-flow-rules`  
**Group**: `SENTINEL_GROUP`  
**配置格式**: JSON

```json
[
  {
    "resource": "getPostDetail",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

规则会自动从 Nacos 加载，并在 Nacos 中修改后实时生效。

### 9. 监控指标集成

Sentinel 自动暴露 Actuator 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: sentinel
```

访问：`/actuator/sentinel`

### 10. 与 Spring Cloud Gateway 集成

如果使用网关，可以在网关层统一限流：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: blog-post
          uri: lb://blog-post
          predicates:
            - Path=/api/posts/**
          filters:
            - name: Sentinel
              args:
                resource-name: blog-post-gateway
                fallback-uri: forward:/fallback
```

## 对比总结

| 特性 | 手动实现 | Sentinel |
|------|---------|----------|
| 流量控制 | ❌ | ✅ 多种策略 |
| 熔断降级 | ⚠️ 简单 | ✅ 三种策略 |
| 系统保护 | ⚠️ 简单 | ✅ 全面保护 |
| 热点限流 | ❌ | ✅ 参数级别 |
| 实时监控 | ❌ | ✅ 可视化控制台 |
| 规则管理 | ⚠️ 代码 | ✅ 控制台/Nacos |
| 规则持久化 | ❌ | ✅ Nacos/Apollo/ZK |
| 集群流控 | ❌ | ✅ Token Server |
| 中文支持 | - | ✅ 完善文档 |

## 迁移建议

1. **保留查询优化代码**：并行查询、批量查询等性能优化逻辑保留
2. **删除手动降级代码**：`DegradationService`、`DegradableDualStorageManager` 等可以删除
3. **使用 Sentinel 注解**：在关键方法上添加 `@SentinelResource`
4. **配置规则到 Nacos**：实现规则持久化和动态更新
5. **部署控制台**：方便实时监控和规则调整

## 最佳实践

1. **资源粒度**：按业务方法划分资源，不要太粗也不要太细
2. **规则持久化**：生产环境必须使用 Nacos 等持久化方案
3. **监控告警**：集成 Prometheus + Grafana 监控
4. **降级策略**：优先使用慢调用比例，避免误降级
5. **测试验证**：压测验证流控和降级规则的有效性

## 结论

使用 Sentinel 是 Spring Cloud Alibaba 生态的标准做法，它提供了完整的流量控制和熔断降级能力，比手动实现更专业、更可靠。建议尽快迁移到 Sentinel。
