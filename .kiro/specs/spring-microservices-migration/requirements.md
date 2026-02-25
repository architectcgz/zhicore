# Requirements Document

## Introduction

本文档定义了将现有ASP.NET Core单体博客系统迁移到Spring Cloud微服务架构的需求规范。该博客系统包含用户管理、文章管理、评论系统、实时消息、搜索推荐、通知系统、排行榜、管理后台等核心功能模块。迁移目标是实现服务解耦、独立部署、弹性伸缩，同时保持现有功能完整性。

**架构设计原则**：采用DDD（领域驱动设计）架构，每个微服务按照领域边界划分，内部采用分层架构（接口层、应用层、领域层、基础设施层），确保业务逻辑的高内聚和低耦合。

**技术栈选型**：
- 微服务框架：Spring Cloud Alibaba
- 数据库：PostgreSQL（延续现有选型）
- ORM框架：MyBatis-Plus
- 分布式ID：美团Leaf（雪花算法模式）
- 服务注册与配置：Nacos（服务发现 + 配置中心）
- 服务网关：Spring Cloud Gateway
- 服务调用：OpenFeign + Sentinel（熔断降级）
- 消息队列：RocketMQ
- 缓存：Redis
- 搜索引擎：Elasticsearch
- 链路追踪：SkyWalking 或 Sleuth + Zipkin

## Glossary

### 基础设施术语
- **API_Gateway**: API网关服务，基于Spring Cloud Gateway实现请求路由、认证鉴权、限流熔断
- **Config_Server**: 配置中心，基于Nacos统一管理各服务配置，支持配置热更新
- **Service_Registry**: 服务注册中心，基于Nacos实现服务发现与注册
- **Sentinel**: 流量控制组件，实现熔断降级、流量控制、系统保护
- **JWT**: JSON Web Token，用于身份认证的令牌
- **Leaf_Service**: 美团Leaf分布式ID生成服务，提供全局唯一的雪花ID
- **MyBatis_Plus**: ORM框架，提供数据持久化能力
- **SkyWalking**: 分布式链路追踪系统，实现全链路监控

### 业务服务术语
- **User_Service**: 用户服务，处理用户注册、登录、认证、关注、签到等功能
- **Post_Service**: 文章服务，处理文章CRUD、草稿、分类、标签等功能
- **Comment_Service**: 评论服务，处理评论CRUD、点赞、嵌套回复等功能
- **Message_Service**: 消息服务，处理私信、实时通信等功能
- **Notification_Service**: 通知服务，处理系统通知、消息推送等功能
- **Search_Service**: 搜索服务，处理全文搜索功能
- **Ranking_Service**: 排行榜服务，处理文章热度、创作者热度、排行榜等功能
- **Upload_Service**: 上传服务，处理文件上传、图片处理等功能
- **Admin_Service**: 管理服务，处理后台管理、内容审核、用户管理等功能

### 数据库术语
- **User_DB**: 用户服务数据库，存储用户、角色、关注、拉黑、签到等数据
- **Post_DB**: 文章服务数据库，存储文章、分类、点赞、收藏等数据
- **Comment_DB**: 评论服务数据库，存储评论、评论点赞、评论统计等数据
- **Message_DB**: 消息服务数据库，存储私信、会话等数据
- **Notification_DB**: 通知服务数据库，存储通知、公告等数据
- **Content_DB**: 内容服务数据库，存储话题、举报、反馈等数据

### DDD架构术语
- **Domain_Layer**: 领域层，包含领域模型、领域服务、领域事件，是业务逻辑的核心
- **Application_Layer**: 应用层，编排领域服务，处理用例流程，不包含业务逻辑
- **Interface_Layer**: 接口层，处理HTTP请求、参数校验、DTO转换
- **Infrastructure_Layer**: 基础设施层，实现仓储接口、外部服务集成、消息队列等
- **Aggregate_Root**: 聚合根，领域模型的入口点，保证聚合内的一致性
- **Domain_Event**: 领域事件，表示领域中发生的业务事件
- **Repository**: 仓储，提供聚合根的持久化和查询能力
- **Value_Object**: 值对象，无唯一标识的不可变对象
- **Entity**: 实体，具有唯一标识的领域对象

## Requirements

### Requirement 1: 微服务基础架构搭建

**User Story:** As a 系统架构师, I want 搭建Spring Cloud微服务基础架构, so that 各业务服务可以独立部署和扩展。

#### Acceptance Criteria

1. WHEN 系统启动时, THE Config_Server SHALL 从Nacos加载配置并支持配置热更新
2. WHEN 服务实例启动时, THE Service_Registry SHALL 自动将服务实例注册到Nacos
3. WHEN 服务实例下线时, THE Service_Registry SHALL 自动从Nacos移除服务实例
4. WHEN 外部请求到达时, THE API_Gateway SHALL 根据路径规则将请求路由到对应的微服务
5. WHEN 目标服务存在多个实例时, THE API_Gateway SHALL 使用负载均衡策略分发请求
6. WHEN 需要生成分布式ID时, THE Leaf_Service SHALL 使用雪花算法生成全局唯一的64位ID

### Requirement 2: 数据库架构与数据迁移

**User Story:** As a 数据库管理员, I want 按微服务边界拆分数据库并完成数据迁移, so that 各服务拥有独立的数据存储且数据完整迁移。

#### Acceptance Criteria

1. WHEN 部署User_Service时, THE User_DB SHALL 包含users、roles、user_roles、user_follows、user_follow_stats、user_blocks、user_check_ins、user_check_in_stats、user_check_in_bitmaps、user_action_histories表
2. WHEN 部署Post_Service时, THE Post_DB SHALL 包含posts、post_stats、post_likes、post_favorites、categories、post_category表
3. WHEN 部署Comment_Service时, THE Comment_DB SHALL 包含comments、comment_stats、comment_likes表
4. WHEN 部署Message_Service时, THE Message_DB SHALL 包含messages、conversations表
5. WHEN 部署Notification_Service时, THE Notification_DB SHALL 包含notifications、global_announcements、assistant_messages表
6. WHEN 部署Content_Service时, THE Content_DB SHALL 包含topics、topic_stats、reports、feedbacks表
7. WHEN 迁移用户数据时, THE 迁移工具 SHALL 移除ASP.NET Identity特有字段（NormalizedUserName、NormalizedEmail、SecurityStamp、ConcurrencyStamp、TwoFactorEnabled、LockoutEnd、LockoutEnabled、AccessFailedCount）
8. WHEN 迁移ID字段时, THE 迁移工具 SHALL 保持原有雪花ID值不变以确保数据关联完整性
9. WHEN 配置MyBatis_Plus时, THE 各服务 SHALL 使用snake_case命名约定与现有表结构保持一致

### Requirement 3: 用户服务实现

**User Story:** As a 用户, I want 完整的用户管理功能, so that 我可以注册、登录并管理我的账户。

#### Acceptance Criteria

1. WHEN 用户提交注册表单时, THE User_Service SHALL 验证邮箱唯一性并创建用户账户
2. WHEN 用户提交登录凭证时, THE User_Service SHALL 验证密码并返回Access Token和Refresh Token
3. WHEN Access Token过期且Refresh Token有效时, THE User_Service SHALL 签发新的Access Token
4. WHEN 用户点击关注按钮时, THE User_Service SHALL 创建关注关系并更新双方的关注统计
5. WHEN 用户点击取消关注按钮时, THE User_Service SHALL 删除关注关系并更新双方的关注统计
6. WHEN 用户请求粉丝列表时, THE User_Service SHALL 返回分页的粉丝用户信息
7. WHEN 用户请求关注列表时, THE User_Service SHALL 返回分页的关注用户信息
8. WHEN 用户执行签到操作时, THE User_Service SHALL 记录签到并更新连续签到天数
9. WHEN 用户信息更新时, THE User_Service SHALL 发布UserProfileUpdatedEvent到消息队列
10. WHEN 管理员分配角色时, THE User_Service SHALL 更新用户的角色关联
11. WHEN 用户登录时, THE User_Service SHALL 使用BCrypt验证密码

### Requirement 4: 文章服务实现

**User Story:** As a 作者, I want 完整的文章管理功能, so that 我可以创建、编辑和发布我的文章。

#### Acceptance Criteria

1. WHEN 用户提交文章内容时, THE Post_Service SHALL 创建文章记录并返回文章ID
2. WHEN 用户保存草稿时, THE Post_Service SHALL 将文章状态设为Draft并持久化内容
3. WHEN 用户发布文章时, THE Post_Service SHALL 将文章状态设为Published并设置发布时间
4. WHEN 用户设置定时发布时, THE Post_Service SHALL 在指定时间自动将文章状态改为Published
5. WHEN 用户选择编辑格式时, THE Post_Service SHALL 支持Markdown和富文本两种格式存储
6. WHEN 用户创建分类时, THE Post_Service SHALL 支持多级分类的父子关系
7. WHEN 用户添加标签时, THE Post_Service SHALL 关联文章与标签
8. WHEN 文章发布成功时, THE Post_Service SHALL 发布PostPublishedEvent到消息队列
9. WHEN 用户点赞文章时, THE Post_Service SHALL 使用Redis INCR原子操作更新点赞计数
10. WHEN 用户收藏文章时, THE Post_Service SHALL 创建收藏记录并更新收藏计数
11. WHEN 查询文章列表且页码小于等于5时, THE Post_Service SHALL 使用Offset分页
12. WHEN 查询文章列表且页码大于5时, THE Post_Service SHALL 自动切换为Cursor游标分页
13. WHEN 批量查询文章的点赞状态时, THE Post_Service SHALL 使用Redis Pipeline批量获取

### Requirement 5: 评论服务实现

**User Story:** As a 读者, I want 评论文章并与其他用户互动, so that 我可以参与讨论和交流。

#### Acceptance Criteria

1. WHEN 用户提交评论内容时, THE Comment_Service SHALL 创建评论记录并更新文章评论计数
2. WHEN 用户回复评论时, THE Comment_Service SHALL 创建子评论并设置parent_id关联
3. WHEN 用户点赞评论时, THE Comment_Service SHALL 创建点赞记录并更新评论点赞计数
4. WHEN 用户选择按时间排序时, THE Comment_Service SHALL 按创建时间降序返回评论
5. WHEN 用户选择按热度排序时, THE Comment_Service SHALL 按点赞数降序返回评论
6. WHEN 新评论创建成功时, THE Comment_Service SHALL 发布CommentCreatedEvent到消息队列
7. WHEN 查询评论列表时, THE Comment_Service SHALL 使用Cursor游标分页
8. WHEN 加载评论时, THE Comment_Service SHALL 预加载热门子回复（按点赞数排序，数量可配置）

### Requirement 6: 消息服务实现

**User Story:** As a 用户, I want 与其他用户进行私信交流, so that 我可以进行一对一沟通。

#### Acceptance Criteria

1. WHEN 用户发送私信时, THE Message_Service SHALL 创建消息记录并更新会话最后消息时间
2. WHEN 用户发送图片消息时, THE Message_Service SHALL 存储图片URL并标记消息类型为Image
3. WHEN 用户发送文件消息时, THE Message_Service SHALL 存储文件URL并标记消息类型为File
4. WHEN 用户请求会话列表时, THE Message_Service SHALL 返回按最后消息时间排序的会话列表
5. WHEN 用户请求消息历史时, THE Message_Service SHALL 返回指定会话的分页消息记录
6. WHEN 消息发送成功时, THE Message_Service SHALL 通过多端推送实时通知接收方（Web端WebSocket，移动端TCP）
7. WHEN 用户阅读消息时, THE Message_Service SHALL 更新消息已读状态并同步给发送方
8. WHEN 用户拉黑对方时, THE Message_Service SHALL 阻止被拉黑用户发送消息
9. WHEN 陌生人发送消息时, THE Message_Service SHALL 检查陌生人消息限制配置

### Requirement 7: 通知服务实现

**User Story:** As a 用户, I want 接收系统通知, so that 我可以及时了解与我相关的动态。

#### Acceptance Criteria

1. WHEN 用户请求通知列表时, THE Notification_Service SHALL 返回分页的通知记录
2. WHEN 收到PostLikedEvent时, THE Notification_Service SHALL 创建点赞类型的通知记录
3. WHEN 收到CommentCreatedEvent时, THE Notification_Service SHALL 创建评论类型的通知记录
4. WHEN 收到UserFollowedEvent时, THE Notification_Service SHALL 创建关注类型的通知记录
5. WHEN 通知创建成功时, THE Notification_Service SHALL 通过多端推送实时通知用户（Web端WebSocket，移动端TCP）
6. WHEN 用户点击通知时, THE Notification_Service SHALL 将该通知标记为已读
7. WHEN 用户点击全部已读时, THE Notification_Service SHALL 批量更新所有未读通知为已读
8. WHEN 同类通知数量超过阈值时, THE Notification_Service SHALL 聚合显示（如"张三等5人赞了你的文章"）

### Requirement 8: 搜索服务实现

**User Story:** As a 用户, I want 搜索文章和用户, so that 我可以快速找到感兴趣的内容。

#### Acceptance Criteria

1. WHEN 用户输入搜索关键词时, THE Search_Service SHALL 在Elasticsearch中执行全文搜索
2. WHEN 搜索文章时, THE Search_Service SHALL 同时匹配标题、内容和标签字段
3. WHEN 返回搜索结果时, THE Search_Service SHALL 对匹配关键词进行高亮标记
4. WHEN 索引中文内容时, THE Search_Service SHALL 使用IK分词器进行分词
5. WHEN 收到PostPublishedEvent时, THE Search_Service SHALL 将文章索引到Elasticsearch
6. WHEN 收到PostUpdatedEvent时, THE Search_Service SHALL 更新Elasticsearch中的文章索引
7. WHEN 用户搜索话题时, THE Search_Service SHALL 返回匹配的话题列表

### Requirement 9: 排行榜服务实现

**User Story:** As a 用户, I want 查看热门内容和创作者排行, so that 我可以发现优质内容和活跃用户。

#### Acceptance Criteria

1. WHEN 文章被浏览时, THE Ranking_Service SHALL 更新文章的热度分数
2. WHEN 文章被点赞时, THE Ranking_Service SHALL 增加文章的热度分数
3. WHEN 文章被评论时, THE Ranking_Service SHALL 增加文章的热度分数
4. WHEN 用户请求热门文章时, THE Ranking_Service SHALL 返回按热度分数排序的文章列表
5. WHEN 计算创作者热度时, THE Ranking_Service SHALL 综合考虑粉丝数、文章数、互动数
6. WHEN 用户请求创作者排行时, THE Ranking_Service SHALL 返回按创作者热度排序的用户列表
7. WHEN 话题下有新文章时, THE Ranking_Service SHALL 更新话题的热度分数
8. WHEN 用户请求热门话题时, THE Ranking_Service SHALL 返回按热度排序的话题列表
9. WHILE 热度数据存在时, THE Ranking_Service SHALL 使用Redis ZSet存储排行数据

### Requirement 10: 文件上传服务实现

**User Story:** As a 用户, I want 上传图片和文件, so that 我可以在文章和消息中使用多媒体内容。

#### Acceptance Criteria

1. WHEN 用户上传文件时, THE Upload_Service SHALL 将文件存储到对象存储并返回访问URL
2. WHEN 用户上传图片时, THE Upload_Service SHALL 自动压缩图片并生成缩略图
3. WHEN 图片格式不支持时, THE Upload_Service SHALL 自动转换为WebP格式
4. WHEN 文件上传成功时, THE Upload_Service SHALL 返回可访问的CDN URL
5. IF 上传文件大小超过限制, THEN THE Upload_Service SHALL 返回文件过大的错误提示

### Requirement 11: 管理后台服务实现

**User Story:** As a 管理员, I want 管理用户和内容, so that 我可以维护平台秩序和内容质量。

#### Acceptance Criteria

1. WHEN 管理员查询用户列表时, THE Admin_Service SHALL 返回分页的用户信息含角色和状态
2. WHEN 管理员禁用用户时, THE Admin_Service SHALL 更新用户状态并使其Token失效
3. WHEN 管理员查询文章列表时, THE Admin_Service SHALL 返回分页的文章信息含审核状态
4. WHEN 管理员删除文章时, THE Admin_Service SHALL 软删除文章并发布PostDeletedEvent
5. WHEN 管理员查询评论列表时, THE Admin_Service SHALL 返回分页的评论信息
6. WHEN 管理员删除评论时, THE Admin_Service SHALL 软删除评论并更新文章评论计数
7. WHEN 管理员查询举报列表时, THE Admin_Service SHALL 返回待处理的举报记录
8. WHEN 管理员处理举报时, THE Admin_Service SHALL 更新举报状态并执行相应处罚

### Requirement 12: 服务间通信与数据一致性

**User Story:** As a 系统架构师, I want 服务间可靠通信和数据一致性保障, so that 系统整体运行稳定可靠。

#### Acceptance Criteria

1. WHEN 服务需要异步通信时, THE 微服务架构 SHALL 使用RocketMQ发送和消费消息
2. WHEN 服务需要同步调用时, THE 微服务架构 SHALL 使用OpenFeign进行HTTP调用
3. WHEN 服务调用连续失败超过阈值时, THE Sentinel SHALL 触发熔断并返回降级响应
4. WHEN 服务QPS超过限制时, THE Sentinel SHALL 执行流量控制拒绝超额请求
5. WHEN 跨服务操作需要事务时, THE 微服务架构 SHALL 使用最终一致性模式保证数据一致
6. WHEN 服务需要缓存数据时, THE 微服务架构 SHALL 使用Redis作为分布式缓存
7. WHEN 并发操作共享资源时, THE 微服务架构 SHALL 使用Redis实现分布式锁

### Requirement 13: 安全与监控

**User Story:** As a 运维人员, I want 完善的安全机制和监控能力, so that 我可以保障系统安全并及时发现问题。

#### Acceptance Criteria

1. WHEN 请求到达API_Gateway时, THE API_Gateway SHALL 验证JWT Token的有效性
2. WHEN 请求频率超过限制时, THE Sentinel SHALL 返回429状态码拒绝请求
3. WHEN 服务运行时, THE 各服务 SHALL 通过Spring Boot Actuator暴露健康检查端点
4. WHEN 请求跨服务调用时, THE SkyWalking SHALL 自动注入TraceId实现全链路追踪
5. WHEN 服务发生异常时, THE 监控系统 SHALL 记录异常堆栈和上下文信息
6. WHEN 部署服务时, THE 微服务架构 SHALL 支持Docker容器化部署

### Requirement 14: DDD分层架构规范

**User Story:** As a 开发人员, I want 清晰的DDD分层架构规范, so that 代码结构清晰、职责分明、易于维护。

#### Acceptance Criteria

1. WHEN 定义领域模型时, THE Domain_Layer SHALL 包含聚合根、实体、值对象、领域服务和领域事件
2. WHEN 实现领域层时, THE Domain_Layer SHALL 不依赖任何外部框架和基础设施
3. WHEN 实现用例时, THE Application_Layer SHALL 编排领域服务完成业务流程
4. WHEN 定义接口时, THE Application_Layer SHALL 定义应用服务接口和DTO对象
5. WHEN 处理HTTP请求时, THE Interface_Layer SHALL 负责参数校验和异常处理
6. WHEN 实现数据访问时, THE Infrastructure_Layer SHALL 实现Repository接口
7. WHEN 领域状态变更时, THE Domain_Layer SHALL 发布领域事件通知其他组件
8. WHEN 访问聚合时, THE Aggregate_Root SHALL 作为聚合的唯一入口点
9. WHEN 持久化数据时, THE Repository SHALL 只针对聚合根提供操作
10. WHEN 跨服务通信时, THE 各服务 SHALL 通过领域事件实现最终一致性

### Requirement 15: 缓存架构规范

**User Story:** As a 开发人员, I want 清晰的缓存架构规范, so that 缓存逻辑与业务逻辑分离且易于维护。

#### Acceptance Criteria

##### 缓存分层原则
1. WHEN 实现缓存逻辑时, THE Application_Layer SHALL 不直接操作Redis，而是通过Repository或CachedService访问数据
2. WHEN 需要缓存单实体查询时, THE Infrastructure_Layer SHALL 使用CachedRepository装饰器模式实现
3. WHEN 需要缓存复杂业务结果时, THE Infrastructure_Layer SHALL 使用CacheDecorator装饰器包装原有Service

##### 必须使用Redis的场景（业务功能依赖）
4. WHEN 实现点赞计数时, THE Post_Service SHALL 使用Redis INCR/DECR原子操作保证计数准确性
5. WHEN 实现评论点赞计数时, THE Comment_Service SHALL 使用Redis INCR/DECR原子操作保证计数准确性
6. WHEN 存储排行榜数据时, THE Ranking_Service SHALL 使用Redis ZSet存储热度分数和排名
7. WHEN 实现分布式锁时, THE 各服务 SHALL 使用Redis SETNX实现互斥锁
8. WHEN 存储用户签到位图时, THE User_Service SHALL 使用Redis Bitmap存储签到记录
9. WHEN 实现请求限流时, THE API_Gateway SHALL 使用Redis计数器或令牌桶算法

##### 可选使用Redis的场景（性能优化缓存）
10. WHEN 查询文章详情时, THE CachedPostRepository SHALL 先查Redis缓存，未命中再查数据库
11. WHEN 查询用户信息时, THE CachedUserRepository SHALL 先查Redis缓存，未命中再查数据库
12. WHEN 查询评论列表时, THE CachedCommentRepository SHALL 先查Redis缓存，未命中再查数据库
13. WHEN Redis不可用时, THE CachedRepository SHALL 降级为直接查询数据库

##### 缓存一致性策略
14. WHEN 更新数据时, THE CachedRepository SHALL 先更新数据库再删除缓存（Cache-Aside模式）
15. WHEN 删除数据时, THE CachedRepository SHALL 先删除数据库记录再删除缓存
16. WHEN 查询不存在的数据时, THE CachedRepository SHALL 缓存空值防止缓存穿透
17. WHEN 设置缓存过期时间时, THE CachedRepository SHALL 添加随机抖动防止缓存雪崩

##### Redis Key命名规范
18. WHEN 设计Redis Key时, THE 各服务 SHALL 遵循"{service}:{entity}:{id}:{field}"命名规范
19. WHEN 存储文章缓存时, THE Post_Service SHALL 使用"post:{postId}"作为Key前缀
20. WHEN 存储用户缓存时, THE User_Service SHALL 使用"user:{userId}"作为Key前缀
21. WHEN 存储统计数据时, THE 各服务 SHALL 使用"{entity}:stats:{id}"作为Key前缀

##### 缓存过期时间规范
22. WHEN 配置缓存过期时间时, THE 各服务 SHALL 从配置文件（application.yml）读取TTL值，禁止硬编码
23. WHEN 缓存实体详情时, THE CachedRepository SHALL 使用配置项"cache.ttl.entity-detail"（默认10分钟）
24. WHEN 缓存列表数据时, THE CachedRepository SHALL 使用配置项"cache.ttl.list"（默认5分钟）
25. WHEN 缓存统计数据时, THE 各服务 SHALL 使用配置项"cache.ttl.stats"（默认-1表示永久）
26. WHEN 缓存会话数据时, THE User_Service SHALL 使用配置项"cache.ttl.session"（默认7天）
27. WHEN 配置中心更新缓存配置时, THE 各服务 SHALL 支持配置热更新无需重启
