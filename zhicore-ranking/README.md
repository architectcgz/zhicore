# ZhiCore Ranking Service

博客排行榜服务，提供文章、创作者和话题的热度排行榜功能，支持总榜、日榜、周榜和月榜。

## 功能特性

### 核心功能

- **多维度排行榜**：支持总榜、日榜、周榜、月榜
- **多实体类型**：文章、创作者、话题
- **事件驱动更新**：消费浏览、点赞、收藏、评论等 MQ 事件
- **可靠处理**：基于 MongoDB `ranking_event_inbox` 的 durable inbox
- **权威热度状态**：基于 MongoDB `ranking_post_hot_state` 存储净计数与元数据
- **快照刷新**：定时从权威状态重建 Redis 当前排行
- **历史归档**：MongoDB 永久保存历史排行榜数据
- **智能路由**：自动选择 Redis（热数据）或 MongoDB（冷数据）

### 性能优化

- **消费者快速落盘**：MQ 消费后先写 durable inbox，再进入异步聚合
- **按文章批量聚合**：聚合作业按 `postId` 合并事件，减少热度状态写放大
- **快照全量替换**：Redis 排行统一由快照任务全量刷新，避免未衰减增量和已衰减分数混算
- **原子替换排行榜**：使用临时 key + `RENAME` 覆盖 Sorted Set，避免半刷新的中间态

## API 端点

### 基础端点（返回 ID 列表）

#### 1. 获取总榜

```http
GET /api/v1/ranking/posts/hot?limit=20
```

**参数**：
- `limit`（可选）：数量限制，默认 20，最大 100

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    "1234567890",
    "0987654321",
    "1122334455"
  ]
}
```

#### 2. 获取日榜

```http
GET /api/v1/ranking/posts/daily?date=2024-01-28&limit=20
```

**参数**：
- `date`（可选）：日期（格式：yyyy-MM-dd），默认今天
- `limit`（可选）：数量限制，默认 20，最大 100

#### 3. 获取周榜

```http
GET /api/v1/ranking/posts/weekly?week=4&limit=20
```

**参数**：
- `week`（可选）：周数，默认本周
- `limit`（可选）：数量限制，默认 20，最大 100

#### 4. 获取月榜

```http
GET /api/v1/ranking/posts/monthly?year=2024&month=1&limit=20
```

**参数**：
- `year`（可选）：年份，默认当前年份
- `month`（可选）：月份（1-12），默认当前月份
- `limit`（可选）：数量限制，默认 20，最大 100

**参数验证**：
- 年份：2020 <= year <= 当前年份 + 1
- 月份：1 <= month <= 12

**错误响应示例**：
```json
{
  "code": 400,
  "message": "月份参数无效，必须在1-12之间",
  "data": null
}
```

### 带分数端点（返回 HotScore 对象）

#### 1. 获取总榜（带分数）

```http
GET /api/v1/ranking/posts/hot/scores?limit=20
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "entityId": "1234567890",
      "score": 1250.5,
      "rank": 1,
      "updatedAt": "2024-01-28T10:30:00"
    },
    {
      "entityId": "0987654321",
      "score": 980.3,
      "rank": 2,
      "updatedAt": "2024-01-28T10:25:00"
    }
  ]
}
```

#### 2. 获取日榜（带分数）

```http
GET /api/v1/ranking/posts/daily/scores?date=2024-01-28&limit=20
```

**参数**：
- `date`（可选）：日期（格式：yyyy-MM-dd），默认今天
- `limit`（可选）：数量限制，默认 20，最大 100

#### 3. 获取周榜（带分数）

```http
GET /api/v1/ranking/posts/weekly/scores?week=4&limit=20
```

**参数**：
- `week`（可选）：周数，默认本周
- `limit`（可选）：数量限制，默认 20，最大 100

#### 4. 获取月榜（带分数）

```http
GET /api/v1/ranking/posts/monthly/scores?year=2024&month=1&limit=20
```

**参数**：
- `year`（可选）：年份，默认当前年份
- `month`（可选）：月份（1-12），默认当前月份
- `limit`（可选）：数量限制，默认 20，最大 100

## 数据模型

### HotScore

```json
{
  "entityId": "1234567890",    // 实体ID（文章ID、用户ID、话题ID等）
  "score": 1250.5,             // 热度分数
  "rank": 1,                   // 排名（从1开始）
  "updatedAt": "2024-01-28T10:30:00"  // 最后更新时间
}
```

**注意**：字段名为 `entityId`（不是 `id`），前端对接时 JSON 序列化后的字段名也是 `entityId`。

## MongoDB 归档系统

### 归档策略

为了永久保留历史排行榜数据，系统采用 **Redis + MongoDB 混合存储**策略：

**Redis（热数据）**：
- 日榜：保留 2 天
- 周榜：保留 14 天
- 月榜：保留 365 天
- 优势：高性能查询、实时更新

**MongoDB（冷数据归档）**：
- 永久保留所有历史排行榜数据
- 支持多种排行榜类型（文章、创作者、话题）
- 灵活的文档结构，易于扩展
- 优势：历史数据查询、趋势分析、数据统计

### 归档时间表

| 排行榜类型 | 实体类型 | 归档时间 | Cron 表达式 | 归档数据 | 数据来源 |
|-----------|---------|---------|------------|---------|---------|
| 日榜 | 文章 | 每天凌晨 2:00 | `0 0 2 * * ?` | 昨天的 Top N | Redis 日榜 |
| 日榜 | 创作者 | 每天凌晨 2:00 | `0 0 2 * * ?` | 当前 Top N | Redis 总榜快照 |
| 日榜 | 话题 | 每天凌晨 2:00 | `0 0 2 * * ?` | 当前 Top N | Redis 总榜快照 |
| 周榜 | 文章 | 每周一凌晨 3:00 | `0 0 3 ? * MON` | 上周的 Top N | Redis 周榜 |
| 周榜 | 创作者 | 每周一凌晨 3:00 | `0 0 3 ? * MON` | 当前 Top N | Redis 总榜快照 |
| 周榜 | 话题 | 每周一凌晨 3:00 | `0 0 3 ? * MON` | 当前 Top N | Redis 总榜快照 |
| 月榜 | 文章 | 每月1号凌晨 4:00 | `0 0 4 1 * ?` | 上月的 Top N | Redis 月榜 |
| 月榜 | 创作者 | 每月1号凌晨 4:00 | `0 0 4 1 * ?` | 当前 Top N | Redis 总榜快照 |
| 月榜 | 话题 | 每月1号凌晨 4:00 | `0 0 4 1 * ?` | 当前 Top N | Redis 总榜快照 |

**说明**：
- 文章排行榜有独立的日榜、周榜、月榜数据
- 创作者和话题排行榜目前只有总榜，归档时取总榜快照作为对应时间段的排行榜
- N 值通过配置项 `ranking.archive.limit` 控制，默认 100

### MongoDB 文档结构

```javascript
{
  _id: ObjectId("..."),
  entityId: "1234567890",      // 实体ID（文章ID、用户ID、话题ID）
  entityType: "post",          // 实体类型：post, creator, topic
  score: 1250.5,               // 热度分数
  rank: 1,                     // 排名
  rankingType: "monthly",      // 排行榜类型：daily, weekly, monthly
  period: {                    // 时间周期
    year: 2024,                // 年份（必需）
    month: 1,                  // 月份（1-12，月榜使用）
    week: 4,                   // 周数（周榜使用）
    date: ISODate("2024-01-28") // 日期（日榜使用）
  },
  metadata: {},                // 扩展字段（灵活存储额外信息）
  archivedAt: ISODate("2024-02-01T04:00:00"), // 归档时间
  version: 1                   // 数据版本（用于兼容性）
}
```

### 智能查询路由

系统会根据查询日期自动选择数据源：

```java
// 查询月榜（自动路由）
List<HotScore> scores = rankingQueryService.getMonthlyRanking(2024, 1, 20);

// 路由逻辑：
// - 如果查询日期在 Redis TTL 范围内（365天）→ 从 Redis 查询
// - 如果查询日期超出 Redis TTL 范围 → 从 MongoDB 查询
```

**优势**：
- 统一的查询接口，无需关心数据存储位置
- 自动选择最优数据源
- 返回类型统一为 `HotScore`

## 配置说明

### application.yml

```yaml
spring:
  # Redis 配置
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123456
    
    # MongoDB 配置
    mongodb:
      uri: mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin
      database: ZhiCore

# 排行榜配置
ranking:
  # 默认查询数量
  default-size: 20
  # 最大查询数量
  max-size: 100
  
  # 归档配置
  archive:
    limit: 100  # 归档数量限制，默认 100
  
  inbox:
    scan-interval: 5000          # inbox 聚合扫描间隔（毫秒）
    batch-size: 500              # 单次 claim 的 inbox 条数上限
    lease-seconds: 60            # PROCESSING 租约时长
    max-retry: 10                # 最大重试次数
    applied-event-window-size: 2048  # recentAppliedEventIds 保留窗口
    done-retention-days: 400     # DONE inbox 保留天数

  snapshot:
    refresh-interval: 60000      # 当前快照刷新间隔（毫秒）
    top-size: 10000              # Redis 中保留的 Top N
```

### MongoDB 索引

系统会自动创建以下索引：

1. **复合索引**：按类型和时间查询排行榜
   ```javascript
   {
     entityType: 1,
     rankingType: 1,
     "period.year": -1,
     "period.month": -1,
     "period.week": -1,
     rank: 1
   }
   ```

2. **实体查询索引**：查询某实体的历史排名
   ```javascript
   {
     entityId: 1,
     entityType: 1,
     rankingType: 1,
     "period.year": -1
   }
   ```

3. **归档时间索引**：用于数据清理和统计
   ```javascript
   {
     archivedAt: 1
   }
   ```

4. **唯一索引**：防止重复归档
   ```javascript
   {
     entityId: 1,
     entityType: 1,
     rankingType: 1,
     "period.year": 1,
     "period.month": 1,
     "period.week": 1,
     "period.date": 1
   }
   // 使用 partial filter: { rank: { $type: "int" } }
   ```

## 性能指标

### 目标性能

- **API 响应时间**：P95 < 100ms
- **并发查询**：支持 1000+ QPS
- **inbox 聚合**：减少对权威热度状态的细粒度写入
- **快照替换**：避免对 Redis 排行做高频逐条增量更新

### Redis Key 设计

```
# 总榜（永久保留）
ranking:posts:hot
ranking:creators:hot
ranking:topics:hot

# 日榜（TTL: 2 天）
ranking:posts:daily:2024-01-28

# 周榜（TTL: 14 天）
ranking:posts:weekly:4

# 月榜（TTL: 365 天）
ranking:posts:monthly:2024:01
```

## 热度更新流程

### 1. 事件写入与聚合

```
文章浏览/点赞/收藏/评论事件
    ↓
RocketMQ Consumer
    ↓
RankingEventInboxService.saveEvent()
    ↓
MongoDB: ranking_event_inbox
    ↓
RankingInboxScheduler
    ↓
RankingInboxAggregationService
    ↓
MongoDB: ranking_post_hot_state
```

### 2. 当前排行快照刷新

```
ranking_post_hot_state + 当前周期 DONE inbox 事件
    ↓
RankingSnapshotScheduler
    ↓
RankingSnapshotService
    ↓
RankingRedisRepository.replaceRanking()
    ↓
Redis Sorted Set
```

### 3. 归档流程

```
定时任务触发（每天/每周/每月）
    ↓
从 Redis 获取排行榜数据
    ↓
转换为 RankingArchive 文档
    ↓
检查是否已归档（防止重复）
    ↓
保存到 MongoDB
```

## 开发指南

### 本地开发环境

1. **启动 Redis**：
   ```bash
   docker run -d --name ZhiCore-redis -p 6379:6379 redis:7-alpine
   ```

2. **启动 MongoDB**：
   ```bash
   docker run -d --name ZhiCore-mongodb \
     -p 27017:27017 \
     -e MONGO_INITDB_ROOT_USERNAME=admin \
     -e MONGO_INITDB_ROOT_PASSWORD=mongo123456 \
     mongo:7
   ```

3. **启动服务**：
   ```bash
   mvn spring-boot:run
   ```

### 测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=RankingRedisRepositoryTest

# 运行属性测试
mvn test -Dtest=RankingPropertiesTest
```

### API 文档

启动服务后访问：
- Knife4j 文档：`http://localhost:8108/doc.html`
- Swagger UI：`http://localhost:8108/swagger-ui.html`

## 监控和运维

### 健康检查

```bash
# 服务健康检查
curl http://localhost:8108/actuator/health

# Redis 连接检查
redis-cli -h localhost -p 6379 -a redis123456 ping

# MongoDB 连接检查
mongosh --host localhost:27017 --eval "db.adminCommand('ping')"
```

### 日志级别

```yaml
logging:
  level:
    com.ZhiCore.ranking: DEBUG
    org.springframework.data.mongodb: DEBUG
    org.springframework.data.redis: DEBUG
```

### 监控指标

系统当前重点关注：

- inbox 聚合调度日志与失败重试
- snapshot 刷新日志
- 浏览去重计数 `ranking.view.dedup`
- Redis 和 MongoDB 的连接健康状态

## 故障排查

### 常见问题

1. **月榜查询返回 404**
   - 检查端点路径是否正确：`/api/v1/ranking/posts/monthly`
   - 检查参数格式：year 和 month 必须是整数

2. **归档任务未执行**
   - 检查 `@EnableScheduling` 注解是否添加到主类
   - 检查 MongoDB 连接是否正常
   - 查看日志中的归档执行记录

3. **Redis 连接失败**
   - 检查 Redis 服务是否启动
   - 检查连接配置（host、port、password）
   - 检查防火墙设置

4. **MongoDB 连接失败**
   - 检查 MongoDB 服务是否启动
   - 检查连接字符串格式
   - 检查认证信息（username、password）

### 日志查看

```bash
# 查看归档日志
grep "归档" logs/ZhiCore-ranking.log

# 查看错误日志
grep "ERROR" logs/ZhiCore-ranking.log

# 查看 inbox 聚合日志
grep "ranking inbox" logs/ZhiCore-ranking.log

# 查看快照刷新日志
grep "快照" logs/ZhiCore-ranking.log
```

## 扩展性

### 支持的实体类型

**当前阶段**：
- ✅ 文章（post）：日榜、周榜、月榜
- ✅ 创作者（creator）：日榜快照、周榜快照、月榜快照
- ✅ 话题（topic）：日榜快照、周榜快照、月榜快照

**未来扩展**：
- 用户排行榜（如果需要与创作者区分）
- 其他业务实体

### 添加新实体类型

1. 在 `RankingRedisKeys` 中添加新的 key 生成方法
2. 在 `RankingArchiveService` 中添加归档方法
3. 更新定时任务以包含新实体类型
4. 添加相应的 API 端点

## 相关文档

- [设计文档](.kiro/specs/ZhiCore-ranking-time-based-endpoints/design.md)
- [需求文档](.kiro/specs/ZhiCore-ranking-time-based-endpoints/requirements.md)
- [任务列表](.kiro/specs/ZhiCore-ranking-time-based-endpoints/tasks.md)
- [端口分配文档](../../docs/port-allocation-plan.md)

## 技术栈

- Spring Boot 3.x
- Spring Data Redis
- Spring Data MongoDB
- Lettuce（Redis 客户端）
- Lombok
- Knife4j（API 文档）

## 许可证

Copyright © 2024 ZhiCore Team. All rights reserved.
