# Implementation Plan: Spring Cloud 微服务迁移

## Overview

本实施计划将博客系统从 ASP.NET Core 单体架构迁移到 Spring Cloud Alibaba 微服务架构。采用增量式开发，先搭建基础设施，再逐个实现业务服务，最后完成集成测试。

## Tasks

- [x] 1. 开发环境与基础设施搭建
  - [x] 1.1 创建 Docker Compose 开发环境
    - 创建 docker-compose.yml 包含新增基础设施服务
    - 复用现有 PostgreSQL（5432）、Redis（6500）
    - 配置 RocketMQ NameServer（9876）、Broker（10911）、Dashboard（8180）
    - 配置 Nacos（8848）服务注册与配置中心
    - 配置 Elasticsearch（9200）、Kibana（5601）
    - 配置 RustFS（9000/9001）S3 兼容对象存储
    - 配置 Prometheus（9090）、Grafana（3000）监控
    - 创建共享网络 ZhiCore-network 连接现有容器
    - 创建 .env 环境变量文件
    - 创建 RocketMQ Broker 配置（rocketmq/broker.conf）
    - 创建 Prometheus 配置（prometheus/prometheus.yml）
    - _Requirements: 13.6_

  - [x] 1.2 创建 Maven 多模块项目结构
    - 创建父 POM 定义 Spring Cloud Alibaba 版本依赖
    - 技术栈版本：Java 17、Spring Boot 3.2.x、Spring Cloud 2023.0.x、Spring Cloud Alibaba 2023.0.x
    - 创建 common 模块（通用工具、异常、响应封装）
    - 创建 api 模块（Feign 接口定义、DTO）
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.3 实现 Leaf 分布式 ID 服务
    - 创建 leaf-service 模块
    - 实现 IdGenerator 接口和 LeafIdGenerator 实现类
    - 实现基于 Nacos 的 WorkerId 自动分配（NacosWorkerIdAllocator）
    - 实现时钟回拨处理（ClockBackwardHandler）
    - 配置 ID 生成监控告警（Prometheus 规则）
    - 提供 REST API 和 SDK
    - _Requirements: 1.6_

  - [x] 1.4 配置 Nacos 服务注册与配置中心
    - 创建各服务的 bootstrap.yml 配置
    - 配置 Nacos 命名空间和分组
    - 实现配置热更新支持（@RefreshScope）
    - 配置敏感信息从环境变量读取
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.5 实现 API Gateway 网关服务
    - 创建 gateway-service 模块
    - 配置路由规则和负载均衡
    - 实现 JWT 认证过滤器（含 Token 黑名单机制）
    - 集成 Sentinel 限流熔断
    - 实现灰度路由过滤器（GrayRouteFilter）
    - _Requirements: 1.4, 1.5, 13.1, 13.2_

  - [x] 1.6 配置 RocketMQ 消息队列
    - 定义 Topic 规范（ZhiCore-post-events、ZhiCore-user-events、ZhiCore-comment-events、ZhiCore-message-events）
    - 定义 Tag 规范（published、liked、followed 等）
    - 创建 DomainEventPublisher（支持普通消息、顺序消息、事务消息、延迟消息）
    - 实现消费者幂等性处理（StatefulIdempotentHandler）
    - 配置消费者组规范（search-consumer-group、notification-consumer-group 等）
    - _Requirements: 12.1_

  - [x] 1.7 配置 SkyWalking 链路追踪
    - 集成 SkyWalking Agent
    - 配置 TraceId 传递
    - _Requirements: 13.4, 13.5_

- [x] 2. Checkpoint - 基础设施验证
  - 验证 Docker Compose 所有服务启动正常
  - 验证 Nacos 服务注册与配置加载
  - 验证 Gateway 路由转发
  - 验证 Leaf ID 生成
  - 验证 RocketMQ 消息发送与消费
  - 确保所有基础设施组件正常运行

- [x] 3. 通用模块与领域基础
  - [x] 3.1 实现通用异常处理
    - 创建 BusinessException、DomainException 等异常类
    - 实现 GlobalExceptionHandler
    - 定义统一响应格式 ApiResponse
    - 定义统一错误码（ResultCode 枚举）
    - _Requirements: 14.5_

  - [x] 3.2 实现领域事件基础设施
    - 创建 DomainEvent 基类
    - 实现 RocketMQ 事件发布器（DomainEventPublisher）
    - 实现事件消费者基类（含幂等处理）
    - 实现 TransactionSynchronization 事务提交后回调
    - _Requirements: 14.7, 12.1_

  - [x] 3.3 实现缓存基础设施
    - 创建 CachedRepository 装饰器基类
    - 实现缓存配置类（CacheConfig，TTL 从配置文件读取）
    - 实现缓存穿透、雪崩、击穿防护（随机抖动、空值缓存）
    - 实现统计类缓存对账任务（StatsReconciliationTask）
    - 定义 Redis Key 命名规范（{service}:{entity}:{id}:{field}）
    - _Requirements: 15.1, 15.2, 15.3, 15.14, 15.15, 15.16, 15.17, 15.22-15.27_

  - [x] 3.4 实现 Sentinel 熔断降级配置
    - 配置熔断规则（SLA 配置：超时、重试、熔断阈值）
    - 实现 Feign Fallback 工厂
    - _Requirements: 12.3, 12.4_

- [x] 4. User Service 用户服务
  - [x] 4.1 创建 user-service 模块与 DDD 分层结构
    - 创建 interfaces/application/domain/infrastructure 包结构
    - 定义 User 聚合根（充血模型）
    - 定义 UserFollow、UserCheckIn 等实体
    - _Requirements: 14.1, 14.2, 14.8_

  - [x] 4.2 实现用户注册与登录
    - 实现 UserApplicationService.register()
    - 实现 BCrypt 密码加密验证
    - 实现 JWT Token 生成（Access + Refresh）
    - _Requirements: 3.1, 3.2, 3.3, 3.11_

  - [x] 4.3 实现关注功能
    - 实现 FollowApplicationService.follow/unfollow()
    - 使用 TransactionTemplate 分离事务与 Redis 操作
    - 发布 UserFollowedEvent 事件
    - _Requirements: 3.4, 3.5, 3.6, 3.7_

  - [x] 4.4 实现签到功能
    - 使用 Redis Bitmap 存储签到记录
    - 实现连续签到天数计算
    - _Requirements: 3.8, 15.8_

  - [x] 4.5 实现用户缓存层
    - 实现 CachedUserRepository
    - 配置用户详情缓存 TTL
    - _Requirements: 15.11, 15.20_

  - [x] 4.6 编写 User Service 单元测试
    - 测试 User 聚合根领域行为
    - 测试 UserApplicationService
    - _Requirements: 3.1-3.11_

- [x] 5. Post Service 文章服务
  - [x] 5.1 创建 post-service 模块与 DDD 分层结构
    - 定义 Post 聚合根（充血模型，含状态机）
    - 定义 PostStats 值对象
    - 定义 Category 实体
    - _Requirements: 14.1, 14.2, 14.8_

  - [x] 5.2 实现文章 CRUD
    - 实现 PostApplicationService.createPost/updatePost/deletePost()
    - 实现草稿保存与发布
    - 支持 Markdown 和富文本格式
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

  - [x] 5.3 实现定时发布功能
    - 使用 RocketMQ 延迟消息实现定时发布
    - _Requirements: 4.4_

  - [x] 5.4 实现文章点赞功能
    - 使用 Redis INCR/DECR 原子操作
    - 使用 TransactionTemplate 分离事务与 Redis
    - 发布 PostLikedEvent 事件
    - _Requirements: 4.9, 15.4_

  - [x] 5.5 实现文章收藏功能
    - 实现 PostFavoriteApplicationService
    - 使用 TransactionTemplate 分离事务与 Redis
    - _Requirements: 4.10_

  - [x] 5.6 实现混合分页查询
    - 页码 ≤5 使用 Offset 分页
    - 页码 >5 自动切换 Cursor 分页
    - _Requirements: 4.11, 4.12_

  - [x] 5.7 实现文章缓存层
    - 实现 CachedPostRepository
    - 实现 Redis Pipeline 批量查询点赞状态
    - _Requirements: 4.13, 15.10, 15.19_

  - [x] 5.8 编写 Post Service 单元测试
    - 测试 Post 聚合根状态机
    - 测试点赞计数一致性
    - _Requirements: 4.1-4.13_

- [x] 6. Checkpoint - 核心服务验证
  - 验证用户注册登录流程
  - 验证文章发布与点赞流程
  - 验证事件发布与消费
  - 确保所有测试通过

- [x] 7. Comment Service 评论服务
  - [x] 7.1 创建 comment-service 模块与 DDD 分层结构
    - 定义 Comment 聚合根（支持嵌套回复）
    - 定义 CommentStats 值对象
    - _Requirements: 14.1, 14.2_

  - [x] 7.2 实现评论 CRUD
    - 实现 CommentApplicationService.createComment()
    - 实现嵌套回复（parent_id, root_id）
    - 发布 CommentCreatedEvent 事件
    - _Requirements: 5.1, 5.2, 5.6_

  - [x] 7.3 实现评论点赞功能
    - 使用 Redis INCR/DECR 原子操作
    - 使用 TransactionTemplate 分离事务与 Redis
    - _Requirements: 5.3, 15.5_

  - [x] 7.4 实现评论排序与分页
    - 实现时间排序和热度排序
    - 使用 Cursor 游标分页
    - 预加载热门子回复
    - _Requirements: 5.4, 5.5, 5.7, 5.8_

  - [x] 7.5 实现评论缓存层
    - 实现 CachedCommentRepository
    - _Requirements: 15.12_

  - [x] 7.6 编写 Comment Service 单元测试
    - 测试嵌套评论结构
    - 测试点赞计数一致性
    - _Requirements: 5.1-5.8_

- [x] 8. Message Service 消息服务
  - [x] 8.1 创建 message-service 模块与 DDD 分层结构
    - 定义 Message 聚合根
    - 定义 Conversation 实体
    - _Requirements: 14.1, 14.2_

  - [x] 8.2 实现私信发送
    - 实现 MessageApplicationService.sendMessage()
    - 支持文本、图片、文件消息类型
    - 实现会话自动创建
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 8.3 实现消息查询
    - 实现会话列表查询（按最后消息时间排序）
    - 实现消息历史分页查询
    - _Requirements: 6.4, 6.5_

  - [x] 8.4 实现实时推送
    - 集成 WebSocket 推送（Web 端）
    - 实现离线消息存储与推送
    - _Requirements: 6.6, 6.7_

  - [x] 8.5 实现消息限制
    - 实现拉黑用户消息阻止
    - 实现陌生人消息限制
    - _Requirements: 6.8, 6.9_

  - [x] 8.6 编写 Message Service 单元测试
    - 测试消息顺序性
    - 测试会话唯一性
    - _Requirements: 6.1-6.9_

- [x] 9. Notification Service 通知服务
  - [x] 9.1 创建 notification-service 模块与 DDD 分层结构
    - 定义 Notification 聚合根
    - 定义 NotificationType 枚举
    - _Requirements: 14.1, 14.2_

  - [x] 9.2 实现通知事件消费者
    - 实现 PostLikedNotificationConsumer
    - 实现 CommentCreatedNotificationConsumer
    - 实现 UserFollowedNotificationConsumer
    - _Requirements: 7.2, 7.3, 7.4_

  - [x] 9.3 实现通知聚合查询
    - 实现数据库层面聚合（先聚合再分页）
    - 实现聚合文案生成
    - 创建 NotificationController 暴露 REST API
    - _Requirements: 7.1, 7.8_

  - [x] 9.4 实现通知已读功能
    - 实现单条已读 POST /api/notifications/{id}/read
    - 实现批量全部已读 POST /api/notifications/read-all
    - _Requirements: 7.6, 7.7_

  - [x] 9.5 实现实时推送
    - 创建 WebSocketConfig 配置 STOMP 端点
    - 创建 WebSocketNotificationHandler 处理连接和推送
    - 创建 NotificationPushDTO 用于推送数据
    - 集成 NotificationPushService 到事件消费者
    - _Requirements: 7.5_

  - [x] 9.6 编写 Notification Service 单元测试
    - 测试 Notification 聚合根领域行为（工厂方法、标记已读、聚合组判断）
    - 测试 NotificationAggregationService（缓存、聚合查询、文案生成）
    - _Requirements: 7.1-7.8_

- [x] 10. Checkpoint - 交互服务验证
  - 验证评论发布与点赞流程
  - 验证私信发送与接收流程
  - 验证通知生成与推送流程
  - 确保所有测试通过

- [x] 11. Search Service 搜索服务
  - [x] 11.1 创建 search-service 模块
    - 配置 Elasticsearch 连接
    - 定义文章索引 Mapping（IK 分词 + 拼音）
    - _Requirements: 8.4_

  - [x] 11.2 实现索引同步
    - 实现 PostPublishedSearchConsumer
    - 实现 PostUpdatedSearchConsumer
    - 实现 PostDeletedSearchConsumer
    - _Requirements: 8.5, 8.6_

  - [x] 11.3 实现全文搜索
    - 实现 SearchApplicationService.searchPosts()
    - 支持标题、内容、标签多字段匹配
    - 实现关键词高亮
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 11.4 实现搜索建议
    - 实现前缀匹配建议
    - _Requirements: 8.7_

  - [x] 11.5 编写 Search Service 单元测试
    - 测试索引同步
    - 测试搜索结果相关性
    - _Requirements: 8.1-8.7_dw

- [x] 12. Ranking Service 排行榜服务
  - [x] 12.1 创建 ranking-service 模块
    - 定义热度计算算法
    - 配置 Redis ZSet 存储
    - _Requirements: 9.9, 15.6_

  - [x] 12.2 实现实时热度更新
    - 实现 PostViewedRankingConsumer
    - 实现 PostLikedRankingConsumer
    - 实现 CommentCreatedRankingConsumer
    - 使用 Redis ZINCRBY 原子增量更新
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 12.3 实现排行榜查询
    - 实现热门文章排行（总榜、日榜、周榜）
    - 实现创作者排行
    - 实现热门话题排行
    - _Requirements: 9.4, 9.5, 9.6, 9.7, 9.8_

  - [x] 12.4 实现定时刷新任务
    - 每小时刷新文章热度
    - 每天刷新创作者热度
    - _Requirements: 9.1-9.8_

  - [x] 12.5 编写 Ranking Service 单元测试
    - 测试热度计算算法
    - 测试排行榜有序性
    - _Requirements: 9.1-9.9_

- [x] 13. Upload Service 上传服务
  - [x] 13.1 创建 upload-service 模块
    - 配置对象存储（OSS/本地）
    - 实现存储策略模式
    - _Requirements: 10.1_

  - [x] 13.2 实现图片上传
    - 实现图片压缩
    - 实现缩略图生成
    - 实现 WebP 格式转换
    - _Requirements: 10.2, 10.3_

  - [x] 13.3 实现文件上传
    - 实现文件大小限制
    - 返回 CDN URL
    - _Requirements: 10.4, 10.5_

  - [x] 13.4 编写 Upload Service 单元测试
    - 测试图片处理
    - 测试文件大小限制
    - _Requirements: 10.1-10.5_

- [x] 14. Admin Service 管理服务
  - [x] 14.1 创建 admin-service 模块
    - 定义 AuditLog 实体
    - 配置 Feign 客户端
    - _Requirements: 14.1_

  - [x] 14.2 实现用户管理
    - 实现用户列表查询
    - 实现用户禁用/启用
    - _Requirements: 11.1, 11.2_

  - [x] 14.3 实现内容管理
    - 实现文章列表查询与删除
    - 实现评论列表查询与删除
    - _Requirements: 11.3, 11.4, 11.5, 11.6_

  - [x] 14.4 实现举报管理
    - 实现举报列表查询
    - 实现举报处理（删除内容/警告/封禁）
    - _Requirements: 11.7, 11.8_

  - [x] 14.5 编写 Admin Service 单元测试
    - 测试审计日志记录
    - _Requirements: 11.1-11.8_

- [x] 15. Checkpoint - 辅助服务验证
  - 验证搜索功能
  - 验证排行榜功能
  - 验证文件上传功能
  - 验证管理后台功能
  - 确保所有测试通过

- [x] 16. 数据迁移与 CDC 配置
  - [x] 16.1 创建数据库迁移脚本
    - 配置 Flyway 数据库迁移工具
    - 创建各服务数据库 Schema（V1__create_xxx_table.sql）
    - 移除 ASP.NET Identity 特有字段
    - _Requirements: 2.1-2.9_

  - [x] 16.2 实现数据迁移工具
    - 实现用户数据迁移
    - 实现文章数据迁移
    - 实现评论数据迁移
    - 保持原有雪花 ID 不变
    - 实现迁移后数据校验（MigrationValidator）
    - _Requirements: 2.7, 2.8_

  - [x] 16.3 配置 CDC 数据修复
    - 配置 PostgreSQL 逻辑复制（wal_level=logical）
    - 配置 Debezium PostgreSQL Connector
    - 实现 CDC 事件消费者（PostStatsCdcConsumer、PostLikesCdcConsumer 等）
    - 实现 Redis 数据修复逻辑
    - _Requirements: 12.5, 12.6_

  - [x] 16.4 配置灰度发布
    - 实现灰度发布配置（GrayReleaseConfig）
    - 实现灰度路由决策器（GrayRouter）
    - 实现 Gateway 灰度路由过滤器
    - 实现灰度期间数据对账任务（GrayDataReconciliationTask）
    - 实现快速回滚方案（GrayRollbackService）
    - 配置灰度监控告警规则
    - _Requirements: 12.7_DW3

- [x] 17. 集成测试与部署
  - [x] 17.1 编写集成测试
    - 编写 API 集成测试
    - 编写跨服务调用测试
    - _Requirements: 12.1-12.7_

  - [x] 17.2 配置 Docker 部署
    - 创建各服务 Dockerfile
    - 验证 docker-compose.yml 完整性
    - _Requirements: 13.6_

  - [x] 17.3 配置健康检查与监控
    - 配置 Spring Boot Actuator
    - 配置 Prometheus 指标收集
    - 配置 Grafana 监控面板
    - 配置 SLA 指标告警规则
    - 配置 Kubernetes 探针（可选）
    - _Requirements: 13.3_

- [x] 18. Final Checkpoint - 系统验收
  - 执行完整功能测试
  - 验证数据迁移完整性
  - 验证系统性能指标
  - 确保所有测试通过，系统可上线

## Notes

- 所有任务均为必需，包括单元测试
- 每个 Checkpoint 是验证阶段，确保前序任务完成后再继续
- 各服务开发可并行进行，但需遵循依赖顺序
- 所有 Redis 操作需在数据库事务提交后执行，使用 TransactionTemplate 或 TransactionSynchronization 分离
- 领域事件通过 RocketMQ 异步发布，消费者需实现幂等性
- 技术栈版本：Java 17、Spring Boot 3.2.x、Spring Cloud 2023.0.x、Spring Cloud Alibaba 2023.0.x
- Docker Compose 开发环境：复用现有 PostgreSQL（5432）、Redis（6500），新增 RocketMQ、Nacos、Elasticsearch、Kibana、RustFS、Prometheus、Grafana
- 对象存储使用 RustFS（S3 兼容），替代 MinIO
- 分布式 ID 使用美团 Leaf（Snowflake 模式），通过 Nacos 自动分配 WorkerId
- 数据迁移采用灰度发布策略，逐步切换流量（5% → 20% → 50% → 100%）
