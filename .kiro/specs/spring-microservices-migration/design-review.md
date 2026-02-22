# 设计文档审查报告

## 审查日期
2024年审查

## 总体评价
整体设计文档结构清晰，采用了DDD充血模型、微服务架构等最佳实践。但在一些细节设计上存在可以优化的地方。

---

## 一、架构设计问题

### 1.1 Gateway路由配置不一致
**问题位置**: `02-gateway.md`

**问题描述**:
- Gateway路由配置中使用了`StripPrefix=1`，但实际路径是`/api/users/**`
- 如果Gateway接收的是`/api/users/**`，strip后变成`/users/**`，但服务内部可能期望`/api/users/**`
- 与`15-enhancements.md`中的API版本控制(`/api/v1/*`)不一致

**建议**:
```yaml
# 统一使用版本化路径
spring:
  cloud:
    gateway:
      routes:
        - id: user-service-v1
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**
          filters:
            - StripPrefix=2  # 去掉 /api/v1，保留 /users/**
```

### 1.2 服务间通信模式不明确
**问题位置**: `01-architecture.md`

**问题描述**:
- 文档中提到了同步调用(OpenFeign)和异步调用(RocketMQ)，但没有明确说明什么场景用同步、什么场景用异步
- 缺少服务间依赖关系的说明

**建议**:
- 明确同步调用场景：需要立即返回结果的查询操作
- 明确异步调用场景：事件通知、最终一致性操作
- 添加服务依赖关系图

---

## 二、数据库设计问题

### 2.1 评论表设计中的parentId逻辑不一致
**问题位置**: `05-comment-service.md`

**问题描述**:
- 文档说明使用"扁平化"结构，所有回复的`parentId`都指向顶级评论
- 但在`createReply`方法中，代码显示`parentId = rootId`，这与"扁平化"描述一致
- 但数据库表设计中缺少对`parentId`的索引优化

**建议**:
```sql
-- 添加复合索引优化查询
CREATE INDEX idx_comments_root_parent ON comments(root_id, parent_id, status, created_at);
```

### 2.2 消息服务缺少消息ID唯一性约束
**问题位置**: `06-message-service.md`

**问题描述**:
- `messages`表只有主键约束，但没有对`(conversation_id, sender_id, receiver_id, created_at)`的唯一性约束
- 在高并发场景下，可能产生重复消息

**建议**:
```sql
-- 添加唯一性约束防止重复消息（可选，根据业务需求）
CREATE UNIQUE INDEX idx_messages_unique ON messages(
    conversation_id, sender_id, receiver_id, created_at
) WHERE created_at > NOW() - INTERVAL '1 minute';
```

### 2.3 通知聚合查询性能问题
**问题位置**: `07-notification-service.md`

**问题描述**:
- 聚合查询使用了窗口函数和CTE，在大数据量下性能可能不佳
- 缺少对聚合结果的缓存机制

**建议**:
- 考虑使用物化视图或定期预计算聚合结果
- 添加Redis缓存聚合后的通知列表

---

## 三、缓存设计问题

### 3.1 Redis Key命名不一致
**问题位置**: `11-data-models.md` vs `04-post-service.md`

**问题描述**:
- `PostRedisKeys.userLiked()`在`11-data-models.md`中是`post:{postId}:liked:{userId}`
- 但在`04-post-service.md`中可能是`post:like:user:{userId}:{postId}`
- 命名规范不统一

**建议**:
统一使用`{service}:{entity}:{id}:{field}:{subId}`格式：
```java
// 统一格式
public static String userLiked(String userId, Long postId) {
    return PREFIX + postId + ":liked:" + userId;  // post:{postId}:liked:{userId}
}
```

### 3.2 缓存穿透防护不完整
**问题位置**: `10-infrastructure.md`

**问题描述**:
- 文档中提到了缓存空值防止穿透，但没有提到布隆过滤器的使用场景
- 对于ID生成器生成的ID，应该使用布隆过滤器预先过滤

**建议**:
- 在文章/评论创建时，将ID加入布隆过滤器
- 查询时先检查布隆过滤器，再查缓存/数据库

### 3.3 缓存更新策略不一致
**问题位置**: `03-user-service.md`, `04-post-service.md`

**问题描述**:
- 关注统计使用"数据库更新后更新Redis"
- 点赞统计也使用相同策略
- 但缺少统一的缓存更新策略说明

**建议**:
统一缓存更新策略文档，明确：
1. 何时使用Cache-Aside
2. 何时使用Write-Through
3. 何时使用Write-Behind

---

## 四、消息队列设计问题

### 4.1 RocketMQ Topic设计过于集中
**问题位置**: `10-infrastructure.md`

**问题描述**:
- 所有事件都使用同一个Topic `ZhiCore-events`，通过Tag区分
- 在高并发场景下，单个Topic可能成为瓶颈

**建议**:
```java
// 按业务域拆分Topic
public static final String TOPIC_POST = "ZhiCore-post-events";
public static final String TOPIC_USER = "ZhiCore-user-events";
public static final String TOPIC_COMMENT = "ZhiCore-comment-events";
```

### 4.2 消息幂等性处理不完善
**问题位置**: `10-infrastructure.md`

**问题描述**:
- 文档中提到了幂等性处理，但使用的是Redis存储处理状态
- 如果Redis故障，幂等性检查会失效

**建议**:
- 考虑使用数据库存储消息处理记录（作为兜底）
- 或者使用RocketMQ的MessageId作为幂等性key（更可靠）

### 4.3 消息顺序性保证缺失
**问题位置**: `06-message-service.md`

**问题描述**:
- 消息服务要求消息顺序性（CP-MSG-01），但没有说明如何保证
- RocketMQ默认不保证顺序，需要特殊配置

**建议**:
```java
// 使用顺序消息
rocketMQTemplate.syncSendOrderly(
    destination, 
    message, 
    conversationId.toString(),  // 使用会话ID作为shardingKey
    timeout
);
```

---

## 五、服务设计问题

### 5.1 用户服务关注统计的并发问题
**问题位置**: `03-user-service.md`

**问题描述**:
- 关注操作使用了分布式锁，但统计更新在事务提交后执行
- 如果Redis更新失败，统计会不一致
- 虽然有定时任务修复，但修复周期可能太长

**建议**:
- 考虑使用RocketMQ事务消息，确保Redis更新成功
- 或者使用CDC实时同步（已在文档中提到，但未明确说明优先级）

### 5.2 文章服务点赞的Redis操作时机
**问题位置**: `04-post-service.md`

**问题描述**:
- 点赞操作先查Redis判断是否已点赞，再操作数据库
- 如果Redis和数据库不一致（Redis有但数据库没有），会导致问题

**建议**:
```java
// 改进：先查数据库，再查Redis（数据库是数据源）
boolean existsInDB = likeRepository.exists(postId, userId);
if (existsInDB) {
    // 如果数据库有但Redis没有，回填Redis
    redisTemplate.opsForValue().set(likeKey, "1");
    return; // 已点赞
}
// 继续点赞流程...
```

### 5.3 评论服务缺少热门回复预加载策略
**问题位置**: `05-comment-service.md`

**问题描述**:
- 文档中提到"预加载热门子回复"，但没有说明如何定义"热门"
- 没有说明预加载的数量限制

**建议**:
```java
// 明确热门回复定义
private static final int HOT_REPLY_LIKE_THRESHOLD = 10;  // 点赞数>=10
private static final int HOT_REPLY_MAX_COUNT = 3;  // 最多预加载3条
```

---

## 六、安全性问题

### 6.1 JWT Token刷新机制不明确
**问题位置**: `02-gateway.md`

**问题描述**:
- Gateway只验证Token，但没有说明Token刷新机制
- 缺少Token黑名单机制（用户登出、禁用用户时）

**建议**:
- 添加Redis存储Token黑名单
- Gateway验证Token时同时检查黑名单

### 6.2 权限控制粒度不够细
**问题位置**: `15-enhancements.md`

**问题描述**:
- RBAC权限模型定义了角色和权限，但没有说明资源级权限控制
- 例如：用户只能删除自己的文章，管理员可以删除任何文章

**建议**:
```java
// 添加资源级权限检查
@RequirePermission(value = "post:delete", resourceType = "post", resourceId = "#postId")
public void deletePost(@PathVariable Long postId) {
    // 检查用户是否有权限删除该文章
    permissionService.checkResourcePermission(userId, "post", postId, "delete");
}
```

---

## 七、性能优化问题

### 7.1 分页策略不统一
**问题位置**: `04-post-service.md`, `05-comment-service.md`

**问题描述**:
- 文章服务使用混合分页（前5页Offset，之后Cursor）
- 评论服务Web端用Offset，移动端用Cursor
- 策略不统一，增加维护成本

**建议**:
统一分页策略文档，明确：
1. Web端统一使用Offset分页（支持跳页）
2. 移动端统一使用Cursor分页（无限滚动）
3. 阈值统一配置化

### 7.2 批量查询优化不足
**问题位置**: `15-enhancements.md`

**问题描述**:
- DataLoader设计很好，但缺少对批量查询大小的限制
- 如果一次性查询1000个用户信息，可能导致超时

**建议**:
```java
// 添加批量查询大小限制
private static final int MAX_BATCH_SIZE = 100;

public Map<String, UserDTO> batchLoad(Set<String> userIds) {
    if (userIds.size() > MAX_BATCH_SIZE) {
        // 分批查询
        return batchLoadInChunks(userIds);
    }
    // 正常查询
}
```

### 7.3 排行榜实时性不足
**问题位置**: `08-search-ranking-service.md`

**问题描述**:
- 排行榜使用定时任务每小时刷新，实时性较差
- 虽然有事件驱动的增量更新，但定时任务和增量更新可能冲突

**建议**:
- 明确增量更新为主，定时任务为兜底
- 定时任务只刷新最近N天的活跃内容

---

## 八、可维护性问题

### 8.1 领域事件定义分散
**问题位置**: 各服务设计文档

**问题描述**:
- 领域事件定义分散在各个服务文档中
- 缺少统一的事件目录和版本管理

**建议**:
- 在`11-data-models.md`中集中定义所有领域事件
- 添加事件版本号，支持事件演进

### 8.2 错误码定义缺失
**问题位置**: `13-error-handling.md`

**问题描述**:
- 异常处理中使用了错误码，但没有统一的错误码定义文档
- 各服务可能使用不同的错误码格式

**建议**:
```java
// 统一错误码格式
public enum ErrorCode {
    // 通用错误 1xxx
    INTERNAL_ERROR("1000", "服务器内部错误"),
    PARAM_INVALID("1001", "参数无效"),
    
    // 用户服务 2xxx
    USER_NOT_FOUND("2001", "用户不存在"),
    EMAIL_ALREADY_EXISTS("2002", "邮箱已存在"),
    
    // 文章服务 3xxx
    POST_NOT_FOUND("3001", "文章不存在"),
    POST_ALREADY_PUBLISHED("3002", "文章已发布"),
}
```

### 8.3 配置管理不统一
**问题位置**: 各服务设计文档

**问题描述**:
- 各服务的配置项分散在各自的文档中
- 缺少统一的配置管理规范

**建议**:
- 创建统一的配置管理文档
- 明确哪些配置应该在Nacos中管理，哪些在本地配置文件

---

## 九、测试策略问题

### 9.1 集成测试覆盖不足
**问题位置**: `14-testing-strategy.md`

**问题描述**:
- 集成测试只提到了Repository和API测试
- 缺少跨服务的集成测试（如：发布文章→搜索索引→通知）

**建议**:
- 添加跨服务集成测试场景
- 使用Testcontainers模拟完整的微服务环境

### 9.2 性能测试缺失
**问题位置**: `14-testing-strategy.md`

**问题描述**:
- 文档中没有提到性能测试和压力测试
- 缺少对缓存、数据库、消息队列的性能基准测试

**建议**:
- 添加性能测试章节
- 定义关键接口的SLA指标（响应时间、吞吐量）

---

## 十、文档一致性问题

### 10.1 技术栈版本不明确
**问题位置**: `design.md`

**问题描述**:
- 文档中提到了技术栈，但没有说明具体版本号
- Spring Cloud Alibaba、RocketMQ等都有多个版本

**建议**:
```yaml
# 添加技术栈版本说明
技术栈版本:
  Spring Boot: 2.7.x
  Spring Cloud Alibaba: 2021.x
  RocketMQ: 5.x
  PostgreSQL: 15.x
  Redis: 7.x
```

### 10.2 数据库迁移策略缺失
**问题位置**: `requirements.md`

**问题描述**:
- 需求文档中提到了数据迁移，但设计文档中没有详细的迁移策略
- 缺少数据迁移的步骤和回滚方案

**建议**:
- 添加数据迁移设计文档
- 说明迁移工具、迁移步骤、数据校验、回滚方案

---

## 十一、优化建议总结

### 高优先级（必须修复）
1. ✅ ~~统一Gateway路由配置和API版本控制~~ **[已修复]** - 02-gateway.md
2. ✅ ~~统一Redis Key命名规范~~ **[已修复]** - 04-post-service.md, 11-data-models.md
3. ✅ ~~完善消息幂等性和顺序性保证~~ **[已修复]** - 10-infrastructure.md, 06-message-service.md
4. ✅ ~~添加JWT Token黑名单机制~~ **[已修复]** - 02-gateway.md
5. ✅ ~~统一错误码定义~~ **[已修复]** - 13-error-handling.md

### 中优先级（建议优化）
1. ✅ ~~优化通知聚合查询性能~~ **[已修复]** - 07-notification-service.md
2. ✅ ~~完善缓存更新策略文档~~ **[已修复]** - 10-infrastructure.md
3. ✅ ~~添加批量查询大小限制~~ **[已修复]** - 15-enhancements.md
4. ✅ ~~统一分页策略~~ **[已修复]** - 15-enhancements.md
5. ✅ ~~添加资源级权限控制~~ **[已修复]** - 15-enhancements.md

### 低优先级（可选优化）
1. ✅ ~~拆分RocketMQ Topic~~ **[已修复]** - 10-infrastructure.md
2. ✅ ~~添加性能测试策略~~ **[已修复]** - 14-testing-strategy.md
3. ✅ ~~完善跨服务集成测试~~ **[已修复]** - 14-testing-strategy.md
4. ✅ ~~添加配置管理规范~~ **[已修复]** - 10-infrastructure.md
5. ✅ ~~添加数据迁移策略文档~~ **[已修复]** - 10-infrastructure.md

---

## 十二、总体评价

### 优点
1. ✅ DDD充血模型设计规范，领域模型设计清晰
2. ✅ 微服务拆分合理，服务边界明确
3. ✅ 缓存、消息队列等基础设施设计完善
4. ✅ 考虑了数据一致性和容错机制
5. ✅ 文档结构清晰，覆盖全面

### 需要改进
1. ✅ ~~部分设计细节不一致，需要统一规范~~ **[已修复]**
2. ✅ ~~缺少一些关键的设计文档（如数据迁移、配置管理）~~ **[已修复]**
3. ✅ ~~性能优化策略可以更加明确~~ **[已修复]**
4. ✅ ~~安全性设计可以更加完善~~ **[已修复]**

### 建议
1. 创建统一的设计规范文档，明确命名、配置、错误码等标准
2. 补充缺失的设计文档（数据迁移、配置管理等）
3. 添加架构决策记录（ADR），记录重要设计决策的原因
4. 定期review设计文档，保持文档与代码的一致性

---

## 审查人
AI Assistant

## 审查日期
2024年

---

## 修复记录

### 2026-01-13 修复

**1. Gateway路由配置统一 (02-gateway.md)**
- 将所有路由路径统一为 `/api/v1/*` 格式
- 将 `StripPrefix=1` 改为 `StripPrefix=2`，去掉 `/api/v1` 前缀
- 更新白名单路径为版本化格式

**2. JWT Token黑名单机制 (02-gateway.md)**
- 新增 `TokenBlacklistService` 服务
- 支持单个 Token 黑名单和用户级别黑名单
- 在 `JwtAuthenticationFilter` 中集成黑名单检查

**3. Redis Key命名规范统一 (04-post-service.md, 11-data-models.md)**
- 统一使用 `{service}:{entity}:{id}:{field}:{subId}` 格式
- 修复 `04-post-service.md` 中的 Key 格式与 `11-data-models.md` 保持一致
- 添加命名规范说明文档

**4. 消息顺序性保证 (06-message-service.md, 10-infrastructure.md)**
- 在 `06-message-service.md` 中添加消息顺序性设计说明
- 在 `10-infrastructure.md` 中添加 `publishOrderly` 方法支持顺序消息
- 说明使用 `conversationId` 作为 shardingKey

**5. 统一错误码定义 (13-error-handling.md)**
- 新增 `ErrorCode` 枚举类
- 定义通用错误码 (1xxx)、用户服务错误码 (2xxx)、文章服务错误码 (3xxx)、评论服务错误码 (4xxx)、消息服务错误码 (5xxx)、通知服务错误码 (6xxx)
- 添加权限错误码 (x3xx)

**6. 服务间通信模式说明 (01-architecture.md)**
- 添加同步 vs 异步通信选择原则表格
- 添加服务依赖关系图
- 明确各场景的通信方式选择

### 2026-01-13 修复（中优先级）

**1. 通知聚合查询性能优化 (07-notification-service.md)**
- 添加 Redis 缓存聚合通知列表，TTL 5分钟
- 添加 `getCachedResult` 和 `cacheResult` 方法
- 添加 `invalidateCache` 方法用于新通知到达时清除缓存
- 添加性能优化说明文档

**2. 缓存更新策略文档 (10-infrastructure.md)**
- 新增"缓存更新策略规范"章节
- 定义 Cache-Aside、Write-Through、Write-Behind、Refresh-Ahead 四种策略
- 明确各策略的适用场景和实现方式
- 添加代码示例

**3. 批量查询大小限制 (15-enhancements.md)**
- 新增 `DataLoaderConfig` 配置类，支持配置最大批量大小
- 新增 `AbstractDataLoader` 基类，提供自动分批查询能力
- 单次批量查询最大 100 条记录
- 超过限制时自动分批查询

**4. 统一分页策略 (15-enhancements.md)**
- 新增"统一分页策略"章节（第7节）
- 定义 Offset、Cursor、混合分页三种策略
- 新增 `PaginationConfig` 配置类
- 新增 `PageRequest` 和 `PageResponse` 统一分页模型
- 新增 `AbstractPaginationService` 基类

**5. 资源级权限控制 (15-enhancements.md)**
- 新增 `@RequireResourcePermission` 注解
- 新增 `PermissionService` 权限服务
- 新增 `ResourceOwnershipService` 资源所有权服务
- 新增 `ResourceOwnerChecker` 接口及 Post、Comment 实现
- 支持管理员、资源所有者、角色权限三级检查

**6. Notification Redis Keys 更新 (11-data-models.md)**
- 添加 `aggregatedList` 方法用于缓存聚合通知列表
- 添加 `aggregatedListPattern` 方法用于批量删除缓存

### 2026-01-13 修复（低优先级）

**1. 拆分 RocketMQ Topic (10-infrastructure.md)**
- 将单一 `ZhiCore-events` Topic 拆分为按业务域划分的多个 Topic
- `ZhiCore-post-events`：文章相关事件
- `ZhiCore-user-events`：用户相关事件
- `ZhiCore-comment-events`：评论相关事件
- `ZhiCore-message-events`：私信相关事件（顺序消息）
- 添加 `getTopicForEvent` 方法自动路由事件到对应 Topic
- 更新 Topic 拓扑图

**2. 添加性能测试策略 (14-testing-strategy.md)**
- 新增"性能测试"章节
- 定义关键接口的 SLA 指标（响应时间、吞吐量、并发用户数）
- 添加 JMeter 测试计划示例
- 添加 Gatling 测试脚本示例
- 添加缓存、数据库、消息队列性能基准测试

**3. 完善跨服务集成测试 (14-testing-strategy.md)**
- 新增"跨服务集成测试"章节
- 添加 Testcontainers 测试环境配置
- 添加"文章发布→搜索索引→通知"集成测试
- 添加"点赞→通知→排行榜"集成测试
- 添加"评论→通知"集成测试
- 添加消息幂等性集成测试

**4. 添加配置管理规范 (10-infrastructure.md)**
- 新增"配置管理规范"章节
- 定义配置分类原则（静态配置、动态配置、敏感配置、服务发现）
- 定义配置命名规范
- 说明 Nacos 配置结构和加载优先级
- 添加动态配置刷新和变更监听示例
- 添加敏感配置管理方案

**5. 添加数据迁移策略文档 (10-infrastructure.md)**
- 新增"数据迁移策略"章节
- 选型 Flyway 作为迁移工具
- 定义迁移脚本命名规范
- 添加迁移脚本示例
- 定义数据迁移流程（开发→测试→预发布→生产→验证）
- 添加大表迁移策略
- 添加回滚方案
- 添加数据校验机制

### 2026-01-13 修复（新增优化项）

**1. 技术栈版本矩阵 (design.md)**
- 添加完整的技术栈版本矩阵表格
- JDK 17、Spring Boot 3.2.x、Spring Cloud 2023.0.x、Spring Cloud Alibaba 2023.0.x
- 添加 Maven BOM 依赖管理配置
- 添加"公共规范索引"章节，链接到错误码、Redis Key、事件、配置、缓存策略等规范文档

**2. SLA/超时/重试/熔断配置 (01-architecture.md)**
- 新增"同步调用 SLA 与容错配置"表格
- 定义各服务调用的超时时间、重试次数、熔断阈值
- 添加 Sentinel 熔断配置示例
- 添加 OpenFeign 超时/重试 YAML 配置
- 添加 Fallback 处理器示例
- 新增"异步事件投递与补偿策略"表格
- 添加死信队列配置
- 添加补偿任务示例
- 更新服务依赖图，标注强/弱依赖关系

**3. 缓存策略矩阵 (10-infrastructure.md)**
- 新增缓存策略矩阵表格（实体→策略→TTL→失效触发→重建来源）
- 新增"统计类缓存对账与重建机制"章节
- 添加 `StatsReconciliationTask` 对账任务代码
- 添加 Prometheus 缓存对账告警规则
- 完善 Write-Through 模式的失败补偿机制

**4. 消费者配置规范 (10-infrastructure.md)**
- 新增消费者配置矩阵表格（ConsumeMode、并发度、最大重试）
- 添加 ORDERLY 顺序消费者示例（私信消息）
- 添加 CONCURRENTLY 并发消费者示例（通知）
- 修复事件发布器使用 `getTopicForEvent()` 自动路由到对应 Topic

**5. SLA 指标与告警 (14-testing-strategy.md)**
- 新增"关键接口 SLA 指标"章节
- 添加接口性能 SLA 表格（P50/P95/P99、QPS、并发数、告警阈值）
- 添加跨服务链路 SLA 表格
- 添加容量规划指标表格
- 添加 Prometheus 告警规则配置

**6. 数据迁移 SOP 完善 (10-infrastructure.md)**
- 新增"灰度发布流程"章节
- 定义灰度发布三阶段（准备期→灰度期→收尾期）
- 添加 `GrayReleaseConfig` 灰度配置类
- 添加 `GrayRouter` 灰度路由决策器
- 添加 Gateway 灰度路由过滤器
- 新增"流量切换与监控"章节
- 定义流量切换步骤表格（操作→验证指标→回滚条件）
- 添加灰度监控 Prometheus 告警规则
- 添加 `GrayDataReconciliationTask` 数据对账任务
- 添加 `GrayRollbackService` 快速回滚方案

**7. 分布式ID生成器设计 (10-infrastructure.md)**
- 新增"分布式ID生成器"章节
- 方案选型对比（UUID、数据库自增、Snowflake、Leaf）
- 选用美团 Leaf（Snowflake 模式）
- 添加 64-bit Snowflake ID 结构图解
- 添加 Leaf 服务配置（Maven 依赖、YAML 配置）
- 添加 `IdGenerator` 接口和 `LeafIdGenerator` 实现
- 添加基于 Nacos 的 WorkerId 自动分配器
- 添加时钟回拨处理策略
- 添加 ID 生成监控告警规则
- 定义各服务 ID 使用规范表格

**8. Docker Compose 开发环境 (10-infrastructure.md)**
- 新增"Docker Compose 开发环境"章节
- 添加基础设施服务清单表格
- 完整的 `docker-compose.yml` 配置，包含：
  - PostgreSQL 15（主数据库）
  - Redis 7（缓存、分布式锁）
  - RocketMQ 5.1.4（消息队列 NameServer + Broker + Dashboard）
  - Nacos 2.3.0（服务注册与配置中心）
  - Elasticsearch 8.11.0 + Kibana（全文搜索）
  - MinIO（对象存储）
  - Prometheus + Grafana（监控）
- 添加 RocketMQ Broker 配置文件
- 添加 Prometheus 配置文件
- 添加数据库初始化脚本
- 添加环境变量文件模板
- 添加启动命令和健康检查命令

