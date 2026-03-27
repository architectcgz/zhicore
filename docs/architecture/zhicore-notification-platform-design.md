# ZhiCore 通知平台设计

## 文档定位

本文同时描述 `ZhiCore` 通知模块的当前实现基线与通知平台的后续目标设计，重点覆盖以下主题：

1. 统一通知中心与分类模型
2. 交互通知链路优化
3. 高粉作者发布作品时的广播通知设计
4. 用户偏好、免打扰、作者订阅与通道投递策略
5. 站内、App Push、邮件、短信的统一扩展模型
6. 未读数、聚合、缓存、幂等、失败补偿与可观测性

阅读约定：

1. 标注“当前实现”的内容，以 `zhicore-notification` 仓内代码为准。
2. 标注“建议”或“目标设计”的内容，表示后续平台化演进方向。
3. 除特别说明外，本文以 2026-03-27 仓内实现为基线；当前“交互通知中心 + 平台化一期扩展位 + 偏好/DND”范围已完成，“广播通知 / 作者订阅 / 多通道平台化”仍未落地。

## 设计输入

本次设计基于以下已确认决策：

1. 目标是完整通知平台方案，不仅覆盖作品发布，还覆盖点赞、评论、回复、关注、系统公告等通知。
2. 通道能力按 `站内通知 + App Push + 邮件/短信预留` 设计。
3. 作者发布作品时采用分层通知策略，而不是对全部粉丝做同策略全量广播。
4. 免打扰按通知类型和通道生效，安全或风控类通知允许穿透。
5. 用户偏好做到 `类型 x 通道 x 作者订阅` 粒度。
6. 高粉场景按 `100 万粉丝级` 设计，结构上保留扩展到更高规模的能力。

## 现状与问题

当前 `zhicore-notification` 已具备以下基础能力：

1. `notifications` 表承载站内通知真相源。
2. 点赞、评论、回复、关注通过 MQ 消费后逐条落库。
3. 未读数和聚合查询已有 Redis 与数据库层聚合能力。
4. WebSocket 已可承载通知实时推送。

当前主要缺口和风险如下：

1. 作品发布通知粉丝的链路尚未真正落地。
2. 交互通知采用“事件到达即逐条写库 + 用户级缓存失效”的模式，热点用户下会产生写放大和缓存抖动。
3. 用户偏好、免打扰、作者级订阅、通道策略尚无统一模型。
4. 邮件和短信没有统一的通道抽象与投递记录模型。
5. 高粉作者发布作品时，如果直接对粉丝逐条同步落库，会把数据库、缓存和推送链路一起打爆。

## 当前实现基线（2026-03-27）

### 已完成范围

当前 `zhicore-notification` 已完成的不是“完整通知平台”，而是面向交互通知的一期通知中心实现，具体包括：

1. 站内通知真相源已经落地。
   - 使用 `notifications` 表保存接收者、类型、触发者、目标对象、内容、已读状态和创建时间。
   - 一期已为 `notifications` 增加 `category`、`event_code`、`metadata` 扩展位，兼容现有 `LIKE / COMMENT / FOLLOW / REPLY / SYSTEM` 枚举。

2. 交互通知消费链路已经落地。
   - `PostLikedNotificationConsumer` 消费 `PostLikedEvent`，为文章作者创建点赞通知。
   - `CommentCreatedNotificationConsumer` 消费 `CommentCreatedIntegrationEvent`，分别处理“通知文章作者”和“通知被回复者”两条分支。
   - `UserFollowedNotificationConsumer` 消费 `UserFollowedIntegrationEvent`，为被关注者创建关注通知。
   - 写入侧通过事件派生的通知 ID + `ON CONFLICT (id) DO NOTHING` 实现输入事件级幂等。

3. 用户侧查询和已读接口已经落地。
   - `GET /api/v1/notifications` 返回聚合通知列表。
   - `GET /api/v1/notifications/unread/count` 和 `GET /api/v1/notifications/unread-count` 返回未读总数。
   - `POST /api/v1/notifications/{notificationId}/read` 标记单条已读。
   - `POST /api/v1/notifications/read-all` 和 `POST /api/v1/notifications/mark-all-read` 标记全部已读。
   - `GET /api/v1/notifications/preferences` / `PUT /api/v1/notifications/preferences` 支持通知偏好配置。
   - `GET /api/v1/notifications/dnd` / `PUT /api/v1/notifications/dnd` 支持免打扰配置。

4. 聚合查询、缓存和推送已经落地。
   - `NotificationAggregationService` 在 PostgreSQL 中按 `(type, target_type, target_id)` 聚合，再分页返回代表通知。
   - 聚合列表会批量调用用户服务补齐最近触发者信息，并生成聚合文案。
   - 未读数采用 Redis cache-aside，TTL 读取 `cache.ttl.unread-count`；聚合列表按页缓存，TTL 由 `zhicore.notification.aggregation.cache.ttl` 控制。
   - 创建通知成功后优先原子递增未读缓存；单条已读与全部已读按真实更新行数原子递减；缓存未命中则由查询链路回源重建。
   - 未读数变更与聚合列表缓存失效统一在事务提交后执行。
   - `NotificationPushService` 会在写库成功后推送聚合通知和最新未读数。

5. WebSocket 和保护性配置已经落地。
   - STOMP 入口为 `/ws/notification`，用户通知目的地为 `/user/queue/notifications`，未读角标目的地为 `/user/queue/unread-count`，系统广播目的地为 `/topic/announcements`。
   - WebSocket 入站链路接入了 JWT 解析拦截。
   - 聚合查询与未读数查询已接入 Sentinel 资源保护。

### 当前仍未落地范围

以下内容仍属于本文后续章节中的平台化设计，不应视为当前代码现状：

1. `PostPublishedIntegrationEvent` 对粉丝广播的 `campaign + shard + delivery` 链路。
2. 作者级订阅模型及对应 API。
3. 邮件、短信等独立通道抽象和投递账本。
4. `notification_group_state`、用户级微批和广播侧物化链路。
5. 面向高粉作者的 fan-out 分层、摘要补发和运营侧控制面。

## 设计目标

通知平台需要同时满足以下目标：

1. 对前端暴露统一通知中心，用户只看到一套通知体验。
2. 对内按负载模型拆分为交互通知与广播通知两类生产链路。
3. 用户可按类型、通道、作者配置通知偏好。
4. 免打扰影响通道打扰，不破坏通知数据沉淀。
5. 高粉作者发作品时支持分片 fan-out、分层触达、削峰填谷、失败重试和摘要补发。
6. 未读数、聚合通知和实时推送保持清晰的一致性边界。
7. 数据库仍然是真相源，Redis 只做缓存和加速。
8. 邮件和短信先按能力预留设计，不把一期复杂度绑定在具体供应商上。

## 非目标

本设计当前不包含以下内容：

1. 具体第三方 Push、邮件、短信供应商接入实现。
2. 多租户隔离设计。
3. 海外手机号、邮件合规、退订法规的细化实现。
4. 前端 UI 视觉稿。

## 总体架构

### 架构原则

整体采用 `统一通知中心 + 双写入链路 + 独立通道层` 模式。

1. 通知中心统一管理收件箱、未读数、聚合、已读和偏好。
2. 交互通知和广播通知共享用户中心与通道层，但不共享同一种生产方式。
3. 交互通知保持“按用户沉淀”的模型。
4. 广播通知保持“先建广播任务，再异步物化与投递”的模型。

### 模块边界

建议在现有 `zhicore-notification` 基础上演进出以下内部模块职责：

1. `Notification Center`
   - 统一通知查询接口
   - 统一未读数
   - 聚合通知视图
   - 已读状态与列表管理

2. `Preference Service`
   - 通知类型偏好
   - 通道偏好
   - 作者订阅偏好
   - 免打扰配置

3. `Interaction Pipeline`
   - 点赞、评论、回复、关注、提及等点对点通知
   - 轻量异步化、微批聚合、精确缓存失效

4. `Broadcast Pipeline`
   - 作品发布、系统公告、活动通知
   - 广播任务、粉丝分片、投递计划、摘要补发

5. `Channel Delivery`
   - 站内投递
   - WebSocket / App Push
   - 邮件预留
   - 短信预留

6. `Delivery Ledger`
   - 投递结果记录
   - 重试状态
   - 幂等去重
   - 审计和运营追踪

### 逻辑图

```text
Content/User/Admin Services
          |
          | MQ Integration Events
          v
+-------------------------------+
| Notification Platform         |
|                               |
|  +-------------------------+  |
|  | Preference Service      |  |
|  +-------------------------+  |
|  | Interaction Pipeline    |  |
|  +-------------------------+  |
|  | Broadcast Pipeline      |  |
|  +-------------------------+  |
|  | Channel Delivery        |  |
|  +-------------------------+  |
|  | Notification Center     |  |
|  +-------------------------+  |
+-------------------------------+
          |
          +--> PostgreSQL
          +--> Redis
          +--> WebSocket / Push Provider
          +--> Email/SMS Provider (reserved)
```

## 通知分类模型

### 一级分类

通知中心建议按以下一级分类建模：

1. `INTERACTION`
   - 点赞
   - 评论
   - 回复
   - 关注
   - 提及

2. `CONTENT`
   - 关注作者发布作品
   - 关注作者更新合集或专栏
   - 平台推荐内容

3. `SYSTEM`
   - 系统公告
   - 活动通知
   - 审核结果
   - 运营消息

4. `SECURITY`
   - 异地登录
   - 密码修改
   - 绑定变更
   - 风险提醒

### 二级类型

建议统一枚举而不是只保留当前 `LIKE / COMMENT / FOLLOW / REPLY / SYSTEM` 五类：

1. `POST_LIKED`
2. `POST_COMMENTED`
3. `COMMENT_REPLIED`
4. `USER_FOLLOWED`
5. `USER_MENTIONED`
6. `POST_PUBLISHED_BY_FOLLOWING`
7. `POST_PUBLISHED_DIGEST`
8. `SYSTEM_ANNOUNCEMENT`
9. `MODERATION_RESULT`
10. `SECURITY_ALERT`

设计原则：

1. 一级分类面向前端分栏和偏好分组。
2. 二级类型面向规则、模板、通道策略和埋点。
3. 聚合展示不依赖一级分类，而依赖单独的聚合键。

## 用户偏好与免打扰模型

### 用户偏好粒度

偏好做到三层：

1. 类型级
   - 某类通知是否接收

2. 类型 x 通道级
   - 站内开关
   - Push 开关
   - 邮件开关
   - 短信开关

3. 作者级
   - 特别关注
   - 普通关注
   - 静默关注
   - 取消内容通知但不取消关注关系

### 免打扰语义

免打扰不是“删除通知”，而是“禁止某些通道在某些时间窗口打扰用户”。

建议支持以下能力：

1. 全局免打扰时间窗
   - 如每日 22:00 到次日 08:00

2. 类型级免打扰
   - 互动类静默
   - 内容更新类静默

3. 通道级免打扰
   - Push 静默
   - 邮件延迟
   - 短信关闭

4. 穿透规则
   - `SECURITY` 默认允许穿透
   - 重大系统故障通知可由运营配置穿透
   - 内容发布默认不穿透

### 用户偏好优先级

通知是否投递，按以下顺序决策：

1. 系统级强制屏蔽
   - 用户拉黑
   - 账号注销
   - 合规封禁

2. 安全穿透规则
   - `SECURITY_ALERT` 可绕过普通免打扰

3. 作者级规则
   - 作者被设为静默时，内容更新不走主动推送

4. 类型级开关
   - 该类型已关闭则直接不生成对应通道任务

5. 通道级开关
   - 站内可开但 Push 关闭

6. 免打扰时窗
   - 如果命中静默时间窗，则改为延迟或摘要

### 建议数据表

建议新增以下模型：

#### 1. `notification_user_preference`

保存用户对通知类型与通道的默认偏好。

建议字段：

1. `user_id`
2. `notification_type`
3. `inbox_enabled`
4. `push_enabled`
5. `email_enabled`
6. `sms_enabled`
7. `digest_mode`
8. `updated_at`

#### 2. `notification_user_dnd`

保存用户免打扰配置。

建议字段：

1. `user_id`
2. `scope_type`
3. `channel`
4. `start_time_local`
5. `end_time_local`
6. `timezone`
7. `allow_security_override`
8. `updated_at`

#### 3. `notification_author_subscription`

保存“作者级通知订阅偏好”，与关注关系解耦。

建议字段：

1. `user_id`
2. `author_id`
3. `subscription_level`
   - `PRIORITY`
   - `NORMAL`
   - `MUTED`
   - `UNSUBSCRIBED`
4. `push_enabled`
5. `digest_only`
6. `updated_at`

## 统一通知数据模型

### 设计原则

统一通知中心建议拆成四类数据：

1. 通知事实
2. 收件箱物化
3. 广播任务
4. 通道投递记录

当前 `notifications` 表更接近“收件箱物化 + 已读状态”模型，不适合直接承载所有广播任务语义。

### 1. 收件箱主表

建议在现有 `notifications` 基础上扩展为更明确的收件箱模型，例如 `user_notifications`。

建议字段：

1. `id`
2. `recipient_id`
3. `category`
4. `notification_type`
5. `source_event_id`
6. `source_actor_id`
7. `source_object_type`
8. `source_object_id`
9. `group_key`
10. `title`
11. `content`
12. `payload_json`
13. `importance`
14. `is_read`
15. `read_at`
16. `inbox_status`
17. `created_at`

说明：

1. `group_key` 用于聚合，不再完全依赖 `(type, target_type, target_id)`。
2. `payload_json` 存收件箱渲染所需的轻量快照，如文章标题、作者昵称、封面、评论摘要。
3. `source_event_id` 用于幂等关联原始事件。

### 2. 广播定义表

建议新增 `notification_campaign`，承载一次广播型通知的逻辑主记录。

适用场景：

1. 作者发布作品通知粉丝
2. 全站公告
3. 大型活动通知

建议字段：

1. `campaign_id`
2. `campaign_type`
3. `trigger_event_id`
4. `sender_id`
5. `object_type`
6. `object_id`
7. `payload_json`
8. `audience_strategy`
9. `scheduled_at`
10. `status`
11. `estimated_audience_count`
12. `created_at`

### 3. 广播分片表

建议新增 `notification_campaign_shard`，用于高粉 fan-out。

建议字段：

1. `shard_id`
2. `campaign_id`
3. `author_id`
4. `range_start`
5. `range_end`
6. `cursor_follow_id`
7. `planned_recipient_count`
8. `processed_recipient_count`
9. `success_count`
10. `skipped_count`
11. `failed_count`
12. `status`
13. `next_retry_at`
14. `created_at`
15. `updated_at`

### 4. 通道投递表

建议新增 `notification_delivery`，统一站内、Push、邮件、短信的投递审计。

建议字段：

1. `delivery_id`
2. `recipient_id`
3. `campaign_id`
4. `notification_id`
5. `channel`
6. `notification_type`
7. `dedupe_key`
8. `delivery_status`
9. `provider_code`
10. `provider_message_id`
11. `scheduled_at`
12. `sent_at`
13. `failed_at`
14. `failure_reason`
15. `retry_count`
16. `created_at`

### 5. 摘要任务表

建议新增 `notification_digest_job`，承载免打扰结束后的摘要补发与日/周摘要。

建议字段：

1. `digest_job_id`
2. `user_id`
3. `digest_scope`
4. `channel`
5. `window_start`
6. `window_end`
7. `digest_payload_json`
8. `status`
9. `scheduled_at`
10. `sent_at`

## 交互通知链路设计

### 当前问题

现有交互通知模型的主要问题不是“逐条落库一定错误”，而是：

1. 每条事件都会触发较重的缓存失效。
2. 高互动用户会在短窗口内收到大量重复推送。
3. 聚合信息主要靠查询时扫描构建，读放大明显。
4. 未读数更偏向回源重算，而不是增量维护优先。

### 目标设计

交互通知仍然保留“逐用户沉淀”的真相语义，但写路径做以下优化。

### 1. 精确缓存失效

不要再对用户整页聚合缓存做粗暴失效，改为：

1. 未读总数 key 精确失效或原子递增
2. 对应 `group_key` 聚合缓存失效
3. 列表缓存使用短 TTL 或局部修补

### 2. 用户级微批

对热点用户启用 1 到 3 秒级微批窗口：

1. 同一用户连续收到多条点赞
2. 同一用户连续收到多条关注
3. 同一对象下的评论回复

微批窗口内先写事件缓冲，再合并生成一条推送动作，站内收件箱可保留多条明细，也可以按组增量更新。

### 3. 通知组快照

建议新增轻量聚合快照，例如 `notification_group_state`：

1. `recipient_id`
2. `group_key`
3. `notification_type`
4. `latest_notification_id`
5. `latest_actor_ids`
6. `total_count`
7. `unread_count`
8. `latest_created_at`

这样列表页不必每次都从 `notifications` 全量聚合。

### 4. 未读数增量优先

建议未读数采用：

1. DB 仍为最终真相源
2. Redis 用 Hash 保存一级分类未读数和总数
3. 新通知进来时优先原子 `HINCRBY`
4. 标记已读时优先原子扣减
5. 发现不一致或缓存丢失时回源 DB 重建

### 5. 推送节流

交互通知的实时 Push 不应等于“每条通知都打一枪”。

建议策略：

1. 高优先级回复或提及即时推送
2. 点赞和关注类 30 秒窗口内可合并推送
3. 命中免打扰则只入站内或转摘要

### 6. 幂等

交互通知幂等建议从“主键幂等”升级为“业务幂等键”：

1. 点赞：`post-liked:{postId}:{actorId}:{recipientId}`
2. 关注：`user-followed:{actorId}:{recipientId}`
3. 评论：`comment-created:{commentId}:{recipientId}`

这样即使上游重复事件 ID 发生变化，也不会在短期内无限放大重复通知。

## 广播通知链路设计

### 为什么单独建链路

作者发布作品通知粉丝，本质上不是普通通知，而是：

1. 受众大
2. 粉丝偏好差异大
3. 通道成本差异大
4. 时效要求分层
5. 需要削峰、重试、摘要、回补

因此必须采用 `广播任务主导，收件箱异步物化` 模式。

### 作品发布主流程

以 `PostPublishedIntegrationEvent` 为入口，建议主流程如下：

1. 内容服务发布作品，发送 `PostPublishedIntegrationEvent`
2. 通知平台消费后创建 `notification_campaign`
3. 查询作者粉丝规模与分层统计
4. 生成 `notification_campaign_shard`
5. 分片 worker 拉取粉丝关系
6. 对每个粉丝执行偏好与免打扰决策
7. 决策结果分流到：
   - 站内收件箱物化
   - Push 实时投递
   - 邮件/短信预留投递
   - 摘要队列
   - 直接跳过
8. 记录 `notification_delivery`
9. 汇总 campaign 与 shard 状态

### 粉丝分层策略

对作者发布作品，建议把粉丝分为四层：

1. `PRIORITY`
   - 特别关注作者
   - 明确开启 Push
   - 活跃度高
   - 目标：分钟级触达

2. `NORMAL`
   - 普通订阅粉丝
   - 站内必进收件箱
   - Push 视偏好和免打扰决定

3. `DIGEST`
   - 开启摘要或命中免打扰的粉丝
   - 不做即时打扰
   - 进入摘要任务

4. `MUTED`
   - 关闭该作者内容提醒或关闭该类型通知
   - 不建主动通道任务
   - 是否入站内收件箱由全局站内开关决定

### 收件箱物化策略

广播通知不建议对所有粉丝都立即同步建完整明细。

建议采用：

1. 对 `PRIORITY` 和 `NORMAL` 用户直接物化站内通知
2. 对 `DIGEST` 用户不立即建普通收件箱明细，改建摘要候选记录
3. 如产品要求“所有内容更新都可在站内中心查看”，可为 `DIGEST` 用户建低优先级站内记录，但不触发实时推送

### 广播分片策略

建议按粉丝关系表稳定排序分片，例如按 `following_id = authorId` 的粉丝列表做游标分页：

1. 单分片建议 2000 到 10000 用户
2. 每个分片独立重试
3. 分片状态单独审计
4. 粉丝数据读取只走必要字段，避免全量用户详情回源

### 高粉保护策略

对高粉作者增加保护阈值：

1. 超过阈值后 Push 不再对全量 `NORMAL` 粉丝即时触达
2. `NORMAL` 粉丝默认站内 + 摘要
3. `PRIORITY` 粉丝保留即时 Push
4. 邮件和短信默认只允许 `SECURITY` 或明确订阅场景使用

## 通道层设计

### 通道抽象

建议统一 `channel` 枚举：

1. `INBOX`
2. `PUSH`
3. `EMAIL`
4. `SMS`

所有通知类型都先经过“通道规划”而不是直接调用某个 provider。

### 通道职责

#### 1. INBOX

1. 是通知中心主通道
2. 大多数通知默认至少进入站内
3. 已读、聚合、未读数都依赖它

#### 2. PUSH

1. 用于实时触达
2. 受用户偏好、作者订阅、活跃度和免打扰影响最大
3. 高频低价值通知必须节流和合并

#### 3. EMAIL

1. 一期只做能力预留
2. 默认用于摘要、系统通知、运营通知
3. 不建议用于高频互动通知

#### 4. SMS

1. 一期只做能力预留
2. 默认只允许安全或高优先级系统通知使用
3. 普通内容更新不应默认走短信

### 通道模板

建议每类通知建立模板注册表：

1. 模板按 `notification_type + channel + locale` 组合查找
2. 模板渲染依赖事件快照，不依赖强实时回源
3. 模板版本记录到 `notification_delivery`，方便排查

## 未读数、聚合与查询设计

### 未读数模型

建议对前端暴露以下未读维度：

1. 总未读数
2. 一级分类未读数
3. 高优先级未读数

站内未读数计算原则：

1. 只统计 `INBOX` 已物化记录
2. 摘要候选但未生成正式收件箱记录的内容，不计入普通未读
3. 安全类通知可单独暴露角标

### 聚合键设计

当前按 `(type, target_type, target_id)` 聚合仍然过于底层。

建议统一 `group_key`：

1. 点赞同一文章：`post_like:{postId}`
2. 同一评论回复：`comment_reply:{commentId}`
3. 同一作者内容更新摘要：`author_publish_digest:{authorId}:{yyyyMMdd}`
4. 同一公告：`system_announcement:{announcementId}`

### 读路径

通知查询建议分三层：

1. `notification_group_state`
   - 用于列表摘要

2. `user_notifications`
   - 用于查看某组详情或具体明细

3. Redis
   - 缓存未读数和热点列表页结果

### 缓存原则

1. Redis 中不直接落盘 rich domain model
2. 只缓存稳定快照对象
3. 用户级列表缓存 TTL 短
4. 组级缓存和未读数缓存可精确失效

## 关键事件设计

### 输入事件

通知平台建议消费以下输入事件：

1. `UserFollowedIntegrationEvent`
2. `CommentCreatedIntegrationEvent`
3. `PostLikedEvent`
4. `PostPublishedIntegrationEvent`
5. 后续扩展：
   - `PostMentionedIntegrationEvent`
   - `ModerationResultIntegrationEvent`
   - `SecurityAlertIntegrationEvent`

### 新增内部事件

建议在通知平台内部定义内部事件，而不是把外部集成事件直接耦合到通道层：

1. `InteractionNotificationPlanned`
2. `BroadcastCampaignCreated`
3. `BroadcastShardPlanned`
4. `NotificationDeliveryPlanned`
5. `NotificationDeliverySucceeded`
6. `NotificationDeliveryFailed`
7. `NotificationDigestScheduled`

## API 设计建议

### 当前已实现用户侧 API

1. `GET /api/v1/notifications`
   - 获取聚合通知列表

2. `GET /api/v1/notifications/unread/count`
   - 获取未读统计

3. `GET /api/v1/notifications/unread-count`
   - 未读统计兼容路由

4. `POST /api/v1/notifications/{notificationId}/read`
   - 标记单条已读

5. `POST /api/v1/notifications/read-all`
   - 批量标记全部已读

6. `POST /api/v1/notifications/mark-all-read`
   - 批量已读兼容路由

7. `WS /ws/notification`
   - 建立 STOMP 连接，消费 `/user/queue/notifications`、`/user/queue/unread-count` 和 `/topic/announcements`

### 后续扩展 API

1. `GET /api/v1/notification-preferences`
   - 获取偏好

2. `PUT /api/v1/notification-preferences`
   - 修改类型与通道偏好

3. `GET /api/v1/notification-authors/{authorId}/subscription`
   - 获取作者级订阅偏好

4. `PUT /api/v1/notification-authors/{authorId}/subscription`
   - 修改作者级订阅偏好

5. `GET /api/v1/notification-dnd`
   - 获取免打扰配置

6. `PUT /api/v1/notification-dnd`
   - 修改免打扰配置

### 运营侧 API

建议提供运营和后台接口：

1. 创建系统公告
2. 查询 campaign 投递状态
3. 重试失败 shard
4. 强制停止 campaign
5. 查看通道失败详情

## 一致性、幂等与失败补偿

### 一致性边界

1. PostgreSQL 是通知真相源
2. Redis 是可丢弃缓存副本
3. Push、邮件、短信是最佳努力投递结果，不作为通知是否存在的真相

### 幂等设计

幂等至少需要三层：

1. 输入事件幂等
   - 基于 `event_id`

2. 业务语义幂等
   - 基于 `dedupe_key`

3. 通道投递幂等
   - 基于 `recipient_id + channel + dedupe_key`

### 失败补偿

#### 交互通知

1. 写库成功但 Push 失败
   - 站内仍然保留
   - 通道任务重试

2. Redis 增量失败
   - 不回滚 DB
   - 异步重建未读数

#### 广播通知

1. shard 执行失败
   - 只重试失败 shard

2. provider 超时
   - delivery 进入可重试状态

3. campaign 被取消
   - 未开始 shard 不再调度
   - 已发送站内通知不回滚

## 可观测性设计

建议至少监控以下指标：

1. 交互通知入库 TPS
2. 广播 campaign 创建速率
3. shard 执行耗时
4. 用户偏好命中率
5. 免打扰拦截率
6. Push 成功率
7. Email/SMS 成功率
8. 未读数缓存命中率
9. 聚合列表缓存命中率
10. delivery 重试率
11. 高粉作者发布通知耗时分布
12. 每类通知的跳过原因分布

日志维度建议统一带上：

1. `eventId`
2. `campaignId`
3. `shardId`
4. `recipientId`
5. `notificationType`
6. `channel`
7. `dedupeKey`

## 风险与缓解

### 高风险

1. 高粉作者发布导致 fan-out 风暴
   - 缓解：分片、优先级队列、Push 分层、摘要化

2. 通知偏好判断链路过重
   - 缓解：偏好缓存、本地快照、批量读取

3. 未读数增量与真实值漂移
   - 缓解：定时对账重建、回源兜底

4. 站内和 Push 语义耦合
   - 缓解：收件箱物化和通道投递彻底分离

### 中风险

1. 作者订阅偏好与关注关系不一致
   - 缓解：取消关注时同步清理或冻结作者订阅记录

2. 聚合组状态与明细不一致
   - 缓解：组状态异步修复任务

3. 免打扰跨时区错误
   - 缓解：显式保存用户时区和本地时间窗

## 分阶段落地建议

### 第一阶段

目标：先把平台关键语义搭起来，不追求一次到位。

1. 扩展通知类型模型
2. 增加用户偏好表
3. 增加免打扰表
4. 交互通知改为精确缓存失效
5. 交互通知加未读数增量维护

### 第二阶段

目标：落地作品发布广播链路。

1. 消费 `PostPublishedIntegrationEvent`
2. 引入 `notification_campaign`
3. 引入 `notification_campaign_shard`
4. 建立作者级订阅配置
5. 实现 `PRIORITY / NORMAL / DIGEST / MUTED` 分层

### 第三阶段

目标：完善通道层与摘要。

1. 抽象 Push、邮件、短信通道接口
2. 建立统一 `notification_delivery`
3. 支持免打扰延迟与摘要补发
4. 打通运营侧查询与重试

### 第四阶段

目标：进一步提升性能和运营能力。

1. 引入 `notification_group_state`
2. 增加用户级微批
3. 增加聚合快照修复任务
4. 建立对账报表与投递分析

## 与现有仓内实现的对应关系

### 已实现并可直接复用

1. `NotificationCommandService + NotificationRepository + notifications` 组成的站内通知写模型
2. `NotificationAggregationService + NotificationMapper` 组成的数据库聚合查询链路
3. `RedisNotificationUnreadCountStore + RedisNotificationAggregationStore` 组成的 cache-aside 缓存层
4. `PostLikedNotificationConsumer / CommentCreatedNotificationConsumer / UserFollowedNotificationConsumer` 组成的 MQ 接入层
5. `NotificationPushService + WebSocketNotificationHandler + WebSocketConfig` 组成的实时推送通道
6. `zhicore-user` 的关注关系与粉丝统计能力
7. `PostPublishedIntegrationEvent` 作为后续内容发布通知入口

### 需要重点改造

1. `notifications` 表结构偏简单，需要支持更明确的类型、来源和聚合键
2. 当前交互通知缓存失效粒度过粗
3. 尚无用户偏好、免打扰、作者订阅模型
4. 尚无 campaign、shard、delivery、digest 相关表
5. 尚无针对高粉作者 fan-out 的专门 worker 和调度链路

## 结论

`zhicore-notification` 当前已经完成“交互通知中心”这一层能力，但还没有完成完整通知平台的全部平台化目标。

当前已落地的范围是：

1. 交互事件消费入库
2. 聚合通知列表查询
3. 未读数查询与已读操作
4. WebSocket 实时推送

在这个基础上，`ZhiCore` 后续通知平台不应继续把所有通知都当作“到一条事件就写一条普通通知”处理。

推荐的目标架构是：

1. 对外统一成一个通知中心
2. 对内拆成交互通知链路和广播通知链路
3. 交互通知保留逐用户沉淀，但补齐微批、聚合快照、精确缓存失效和未读数增量维护
4. 作品发布通知采用 `campaign + shard + delivery` 的广播模式
5. 用户偏好做到 `类型 x 通道 x 作者订阅`
6. 免打扰作用于通道打扰与摘要回补，不破坏站内通知沉淀
7. 邮件和短信先做能力预留，不把一期落地复杂度绑定在供应商实现上

本文前半部分用于描述当前已完成模块的实现基线，后半部分保留为平台化扩展设计。这样既能支持当前模块交付和联调，也能为后续更高粉丝规模、更多通知类型和更多投递通道留出清晰演进路径。

## 联调与观测建议

为了降低高粉 fan-out 上线风险，建议在预发环境按固定脚本做验证：

1. 准备一个 10k+ 粉丝作者样本，并分组配置 `PRIORITY / NORMAL / DIGEST / MUTED`。
2. 发布一篇新作品后，检查 `campaign -> shard -> delivery` 三层数据完整性。
3. 命中免打扰窗口时，确认不走即时推送，转入 digest。
4. 免打扰结束后执行摘要任务，确认只生成一条摘要站内通知并回写 delivery 状态。

建议重点监控：

1. `notification_campaign_shard` 的执行耗时与成功率。
2. `notification_delivery` 的状态分布与失败原因。
3. 未读总数与分类未读统计的一致性。
4. WebSocket 推送成功率与降级日志。
