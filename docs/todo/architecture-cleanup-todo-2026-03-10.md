# 服务架构收敛 TODO

日期：2026-03-10

## 背景

本轮对 `zhicore-microservice` 做架构审查后，确认当前代码已经有明确的分层意图，但仍存在几类系统性边界泄漏：

- application 层仍直接依赖基础设施实现类型，如 `RedisTemplate`、`RedissonClient`、Mongo Repository、PG Entity / Mapper
- 缓存与锁的通用抽象已存在，但未成为默认入口，导致各服务继续手写缓存读写、模式删除和锁逻辑
- 跨服务调用契约未完全收敛到共享 client，多个服务仍维护本地 Feign 接口

## 已确认的优先问题

### TODO 1: 收敛 application 层的 `RedisTemplate.keys()` 调用

目标：

- 先消除最直接的 Redis 生产风险
- 将模式删除统一切到 `CacheStore.deletePattern()`

已确认点位：

1. `zhicore-user` `UserManageInternalService`
2. `zhicore-notification` `NotificationApplicationService`
3. `zhicore-notification` `NotificationAggregationService`
4. `zhicore-content` `TagApplicationService`

验收标准：

- application 层不再直接调用 `keys()`
- 模式删除通过统一缓存抽象完成
- 受影响模块编译和定向测试通过

### TODO 2: 收敛 application 层直接依赖基础设施缓存/锁实现

目标：

- 将 application 层对 `RedisTemplate` / `StringRedisTemplate` / `RedissonClient` 的直接依赖收口到通用抽象或模块内 adapter

当前进展：

- 已完成 `zhicore-search` `SuggestionService` -> `SuggestionCacheStore`
- 已完成 `zhicore-user` `AuthApplicationService` -> `RefreshTokenStore`
- 已完成 `zhicore-user` `CheckInApplicationService` -> `CheckInStore`
- 已完成 `zhicore-user` `FollowApplicationService` `RedissonClient/RedisTemplate` -> `LockManager + FollowStatsStore`
- 已完成 `zhicore-user` `BlockApplicationService` `RedissonClient/RedisTemplate` -> `LockManager + FollowStatsStore`
- 已完成 `zhicore-user` `UserApplicationService` / `CacheAsideUserQuery` /
  `FollowApplicationService` / `BlockApplicationService` / `UserManageInternalService`
  对 `UserRedisKeys` 的 application 侧直接依赖收敛：-> `UserCacheKeyResolver`
- 已完成 `zhicore-comment` `CommentDetailCacheService` `RedisTemplate/RedissonClient` -> `CommentDetailCacheStore + LockManager`
- 已完成 `zhicore-comment` `CommentApplicationService` `RedisTemplate` -> `CommentDetailCacheStore + CommentCounterStore`
- 已完成 `zhicore-comment` `CommentLikeApplicationService` `RedisTemplate` -> `CommentLikeStore`
- 已完成 `zhicore-comment` `CommentDetailCacheService` 对锁 key 生成实现的直接依赖收敛：
  `CommentRedisKeys` -> `CommentCacheKeyResolver`
- 已完成 `zhicore-notification` `NotificationApplicationService` `RedisTemplate` -> `NotificationUnreadCountStore + NotificationAggregationStore`
- 已完成 `zhicore-notification` `NotificationAggregationService` `RedisTemplate` -> `NotificationAggregationStore`
- 已完成 `zhicore-content` `PostLikeApplicationService` `RedisTemplate` -> `PostLikeStore`
- 已完成 `zhicore-content` `PostFavoriteApplicationService` `RedisTemplate` -> `PostFavoriteStore`
- 已完成 `zhicore-content` `CacheAsideDraftService` `RedisTemplate` -> `DraftCacheStore`
- 已完成 `zhicore-content` `TagApplicationService` `RedisTemplate` -> `TagHotTagsStore`
- 已完成 `zhicore-ranking` `RankingQueryService` 对月榜查询链路的收敛：
  `RankingArchive` / `RankingRedisRepository` / `RankingRedisKeys` / `RankingCacheProperties`
  -> `RankingArchiveStore` / `RankingMonthlyStore` / `RankingQueryPolicy`
- 已完成 `zhicore-ranking` `RankingSnapshotService` 对快照刷新链路的收敛：
  `RankingEventInboxRepository` / `RankingPostHotStateRepository` / `RankingRedisRepository` /
  `RankingSnapshotProperties` -> `RankingSnapshotSourceStore` / `RankingSnapshotCacheStore` /
  `RankingSnapshotPolicy`
- 已完成 `zhicore-ranking` `PostRankingService` / `CreatorRankingService` / `TopicRankingService`
  对 Redis key 与 repository 的直接依赖收敛：
  -> `PostRankingStore` / `CreatorRankingStore` / `TopicRankingStore`
- 已完成 `zhicore-ranking` `RankingEventInboxService` 对 Mongo inbox 文档与 repository 的直接依赖收敛：
  -> `RankingEventInboxStore`
- 已完成 `zhicore-ranking` `RankingInboxAggregationService` 对 claim / 状态读写 / 完成失败回写 /
  清理策略的直接依赖收敛：
  `MongoTemplate` / Mongo repository / `RankingInboxProperties`
  -> `RankingInboxAggregationStore`
- 已完成 `zhicore-ranking` `RankingArchiveService` 对 Redis 归档源、Mongo 归档持久化与归档配置的直接依赖收敛：
  `RankingRedisRepository` / `RankingRedisKeys` / `RankingArchiveRepository` / `RankingArchiveProperties`
  -> `RankingArchiveSourceStore` / `RankingArchiveStore` / `RankingArchivePolicy`
- 已将通用锁能力沉淀到 `zhicore-common` `LockManager`，补齐 fair / non-fair 选择，comment 不再保留本地锁抽象
- 已完成 `zhicore-content` `PostApplicationService` / `ScheduledPublishScanner`
  对定时发布 PG entity / repository 与 DLQ outbox mapper 的直接依赖收敛：
  `ScheduledPublishEventRepository` / `ScheduledPublishEventEntity` / `OutboxEventMapper` / `OutboxEventEntity`
  -> `ScheduledPublishEventStore` / `ScheduledPublishEventRecord` / `OutboxEventStore` / `OutboxEventRecord`
- 已完成 `zhicore-content` `OutboxAdminApplicationService`
  对 `OutboxEventMapper` / `OutboxRetryAuditMapper` / PG entity 的直接依赖收敛：
  -> `OutboxEventStore` / `OutboxRetryAuditStore` / `OutboxRetryAuditRecord`
- 已完成 `zhicore-content` `PostContentImageCleanupService`
  对 `MongoTemplate` / `PostContentDocument` 的直接依赖收敛：
  -> `PostContentStore.loadContent()` / `PostBody`
- 已完成 `zhicore-content` `PostApplicationService`
  对 `ScheduledPublishProperties` 的直接依赖收敛：
  -> `ScheduledPublishPolicy`

优先模块：

1. `zhicore-content`
2. `zhicore-ranking`

优先动作：

1. 统一替换锁获取为 `LockManager`
2. 通用 key-value 缓存优先使用 `CacheStore`
3. 剩余 Redis 特有结构操作收敛为模块内 `*CacheStore`

下一批候选：

1. `zhicore-ranking` 继续收敛 application 对 infra 查询实现的直接依赖
2. `zhicore-content` 继续处理 application 对 outbox mapper / entity 的直接依赖

### TODO 3: 修复 application port / service 对 infra entity / mapper / document 的反向依赖

目标：

- 避免表结构、Mongo 文档结构直接扩散到 application 层

已确认代表性问题：

1. `zhicore-content` `ScheduledPublishEventRepository` 暴露 `ScheduledPublishEventEntity`
2. `zhicore-content` `PostApplicationService` 直接使用 `OutboxEventEntity` / `OutboxEventMapper`
3. `zhicore-ranking` application service 直接依赖 Mongo Repository / 文档类型

当前进展：

- 已完成 `zhicore-comment` application service 对 `CommentStatsMapper` 的直接依赖收敛：
  通过 `CommentStatsRepository` port 回收到 domain repository 边界
- 已完成 `zhicore-ranking` `RankingQueryService` 对 Mongo 文档与 Redis 查询实现的直接依赖收敛：
  application 不再感知 `RankingArchive` 文档结构，也不再直接编排月榜 Redis key / 空结果 key / 回填 repository
- 已完成 `zhicore-ranking` `RankingSnapshotService` 对 Mongo 文档与 Redis 快照实现的直接依赖收敛：
  application 不再感知 `RankingEventInbox` / `RankingPostHotState` 文档结构，也不再直接拼接
  快照 Redis key 与 TTL
- 已完成 `zhicore-ranking` `RankingEventInboxService` 对 Mongo 文档写入实现的直接依赖收敛：
  application 不再直接构造 `RankingEventInbox` 文档，也不再感知 duplicate key 持久化细节
- 已完成 `zhicore-ranking` `RankingInboxAggregationService` 对 Mongo claim / 更新状态 / 权威状态文档的直接依赖收敛：
  application 仅保留聚合算法，不再感知 Mongo Query / Update / 文档结构
- 已完成 `zhicore-ranking` `RankingArchiveService` 对 Mongo 文档归档与 Redis 榜单读取的直接依赖收敛：
  application 仅保留归档编排与周期计算，不再感知 Redis key、归档文档结构与归档配置实现
- 已完成 `zhicore-content` 定时发布链路对 PG entity / repository / mapper 的直接依赖收敛：
  application 已改为基于 `ScheduledPublishEventStore` / `ScheduledPublishEventRecord` 编排，不再暴露
  `ScheduledPublishEventEntity`
- 已完成 `zhicore-content` outbox 管理链路对 PG entity / mapper 的直接依赖收敛：
  application 已改为基于 `OutboxEventStore` / `OutboxRetryAuditStore` / application model 编排
- 已完成 `zhicore-content` 内容图片清理链路对 Mongo 读实现的直接依赖收敛：
  application 已改为基于 `PostContentStore` / `PostBody` 编排，保留告警语义
- 已完成 `zhicore-content` 定时发布重试链路对 config properties 的直接依赖收敛：
  application 已改为基于 `ScheduledPublishPolicy` 判定重试上限与回退策略
- 已完成 `zhicore-content` 内容查询返回链路对 Mongo 文档类型的直接暴露收敛：
  `PostApplicationService` / `PostFacadeService` / `PostController` / `ContentSentinelHandlers`
  已改为基于 `PostContentVO` 返回，`PostViewAssembler` 已改为基于 `PostBody` 装配
- 已完成 `zhicore-content` 接口请求/响应 DTO 的 application 收口：
  `PostApplicationService` / `PostFacadeService` 不再依赖 `CreatePostRequest` / `UpdatePostRequest` /
  `SaveDraftRequest` / `interfaces.dto.response.DraftVO`，改为基于 application command/dto；
  controller 负责 DTO -> command 映射
- 已完成 `zhicore-content` outbox 管理链路响应 DTO 的 application 收口：
  `OutboxAdminApplicationService` / `ContentSentinelHandlers`
  不再依赖 `interfaces.dto.admin.outbox.*`，改为基于
  `application.dto.admin.outbox.OutboxFailedEventItem` /
  `OutboxFailedPageResponse` / `OutboxRetryResponse`；
  controller 直接复用 application 响应模型返回
- 已完成 `zhicore-content` application 对本地用户/上传 Feign wrapper 与 infra 告警类型的依赖收口：
  `ContentUserServiceClient` / `ContentUploadServiceClient` / `AlertService`
  -> `UserBatchSimpleClient` / `UploadFileClient` / `UploadFileDeleteClient` / `ContentAlertPort`
- 已完成 `zhicore-comment` create/update 请求命令化：
  `CommentApplicationService` 不再依赖 `CreateCommentRequest` / `UpdateCommentRequest`，
  controller 负责 DTO -> `CreateCommentCommand` / `UpdateCommentCommand` 映射
- 已完成 `zhicore-user` `FollowApplicationService` 对 infra MQ 发布接口的依赖收口：
  `infrastructure.mq.EventPublisher` -> `UserEventPort`
- 已完成 `zhicore-comment` `CommentApplicationService` / `AdminCommentApplicationService`
  对 infra MQ 发布器类型的依赖收口：
  `CommentEventPublisher` -> `CommentEventPort`
- 已完成 `zhicore-comment` 游标编解码能力的分层归位：
  `HotCursorCodec` / `TimeCursorCodec` 已从 `infrastructure.cursor` 迁至 `domain.cursor`，
  application / domain repository 不再反向依赖 infra package
- 已完成 `zhicore-user` / `zhicore-comment` Sentinel 方法级资源与 block handler 的分层归位：
  `UserSentinelResources` / `UserSentinelHandlers` /
  `CommentSentinelResources` / `CommentSentinelHandlers`
  已从 `infrastructure.sentinel` 迁至 `application.sentinel`，
  application 层不再反向依赖 infra sentinel package；infrastructure config 继续复用这些 application 常量
- 已完成 `zhicore-search` / `zhicore-notification` / `zhicore-message` /
  `zhicore-ranking` / `zhicore-admin` Sentinel 方法级资源与 block handler 的分层归位：
  `SearchSentinelResources` / `SearchSentinelHandlers` /
  `NotificationSentinelResources` / `NotificationSentinelHandlers` /
  `MessageSentinelResources` / `MessageSentinelHandlers` /
  `RankingSentinelResources` / `RankingSentinelHandlers` /
  `AdminSentinelResources` / `AdminSentinelHandlers`
  已从 `infrastructure.sentinel` 迁至 `application.sentinel`，
  application 层不再反向依赖 infra sentinel package；infrastructure config 继续复用这些 application 常量
- 已完成 `zhicore-upload` / `zhicore-id-generator` 旧风格 service 模块的 Sentinel 分层归位：
  两个模块尚未建立 `application` 层，因此
  `UploadSentinelResources` / `UploadSentinelHandlers` 与
  `IdGeneratorSentinelResources` / `IdGeneratorSentinelHandlers`
  已从 `infrastructure.sentinel` 迁至 `service.sentinel`，
  `service/impl` 不再反向依赖 infra sentinel package；`infrastructure.config` 继续复用这些 service 常量

验收标准：

- application port 不再引用 infra entity
- application service 不再直接依赖 mapper
- `ranking` 至少形成明确的 query/archive port 边界

`ranking` 当前状态：

1. `zhicore-ranking` application 层已完成对 `infrastructure.mongodb` / `infrastructure.redis` /
   `infrastructure.config` 的直接依赖收敛
2. 后续如继续优化，应转向 application 模型精简和跨模块契约统一，不再是本轮 repo/store 边界问题

`content` 当前状态：

1. `zhicore-content` application 层已完成对 `infrastructure.persistence.mongo.document.PostContent` /
   `MongoTemplate` / `infrastructure.persistence.pg.entity` / `infrastructure.config` 的直接依赖收敛
2. 后续如继续优化，应转向接口契约统一、查询 DTO 精简和本地 Feign client 去重，
   不再是本轮 application 与 infra 边界问题

### TODO 4: 统一跨服务调用契约到共享 client

目标：

- 将重复定义的本地 Feign client 收敛到 `zhicore-client`

已确认代表性问题：

1. `comment` / `message` / `content` 各自维护用户服务 client
2. 同一服务接口存在不同参数类型、不同方法命名、不同路径组合

当前进展：

- 已完成 `zhicore-client` 对用户/上传服务公共 Feign 契约的细分：
  新增 `UserSimpleBatchClient` / `UserMessagingClient` / `UploadFileClient` / `UploadMediaClient`
- 已完成 `zhicore-client` 对文章服务公共 Feign 契约的细分：
  新增 `PostBatchClient` / `PostCommentClient` / `PostSearchClient`
- 已完成 `zhicore-content` / `zhicore-comment` / `zhicore-message` / `zhicore-user`
  本地 Feign 接口对共享契约的继承收敛：
  本地接口只保留 `@FeignClient + fallbackFactory` 装配，方法签名回收到 `zhicore-client`
- 已修正 `message` 本地用户查询链路继续使用 `String userId` 的历史写法：
  现已统一回到 `Long` 契约，避免参数类型漂移
- 已完成 `zhicore-notification` 本地用户 client 对共享契约的收敛：
  旧的 GET `/users/batch/simple` 已切换为共享 POST 批量契约 `batchGetUsersSimple(Set<Long>)`
- 已完成 `zhicore-comment` / `zhicore-search` / `zhicore-ranking`
  本地文章 client 对共享契约的继承收敛：
  本地接口只保留 `@FeignClient + fallbackFactory` 装配，不再重复声明文章查询方法
- 已完成 `zhicore-admin` 管理侧 client 对共享契约的收敛：
  新增 `AdminUserClient` / `AdminPostClient` / `AdminCommentClient` 以及共享管理 DTO，
  `zhicore-admin` 本地接口只保留 `@FeignClient + fallbackFactory`，application 层不再依赖本地 Feign 内嵌 DTO；
  同时 `IdGeneratorClient` 已改为继承共享 `IdGeneratorFeignClient`
- 已完成 provider 侧管理 DTO 去重：
  `zhicore-user` / `zhicore-content` / `zhicore-comment` 已改为直接复用 `zhicore-client`
  中的 `UserManageDTO` / `PostManageDTO` / `CommentManageDTO`，删除各模块重复定义
- 已完成 `zhicore-search` 搜索结果 DTO 的 application 收口：
  `PostSearchVO` / `SearchResultVO` 已从 `interfaces.dto` 迁回 `application.dto`
- 已完成 `zhicore-notification` 聚合配置的 policy 化：
  `NotificationAggregationService` 不再直接依赖 `NotificationAggregationProperties`，
  改为通过 `NotificationAggregationPolicy` 读取缓存 TTL 和展示策略
- 已完成一批 application 对本地 Feign wrapper 类型的依赖收口：
  `zhicore-admin` / `zhicore-message` / `zhicore-notification` / `zhicore-ranking`
  application 已改为依赖 shared client contract，本地 wrapper 继续只承担 Feign 装配
- 已完成 `zhicore-content` application 对本地上传/用户 Feign wrapper 的进一步收口：
  `PostApplicationService` / `PostContentImageCleanupService`
  已改为依赖 shared client contract，controller 同步完成接口 DTO -> application command 映射
- 当前剩余候选主要转向确有领域差异的专用接口：
  已不再是基础 user/upload/post/admin 管理契约的重复定义问题

验收标准：

- 共享 client 作为默认契约入口
- 模块本地只保留 fallback 装配或确有差异的专用接口

## 建议执行顺序

1. TODO 1
2. TODO 2
3. TODO 3
4. TODO 4

原因：

- TODO 1 改动最小，能立即降低 Redis 风险
- TODO 2 能快速统一缓存/锁边界，为后续 port 化铺路
- TODO 3 是结构性重构，应建立在前两项已收口的前提上
- TODO 4 涉及接口契约与多模块联动，放在最后更稳妥
