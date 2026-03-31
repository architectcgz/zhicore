# Notification 第一阶段实现状态

## 已落地范围

本文件保留第一阶段落地记录，同时补记 2026-03-27 已并入的第二阶段最小广播骨架。当前状态是：第一阶段已完成，第二阶段已落地 `POST_PUBLISHED` 的最小闭环，其余平台化能力仍在后续阶段。

已完成项：

1. 扩展通知类型元数据
   - `NotificationType` 保留现有 `LIKE/COMMENT/FOLLOW/REPLY/SYSTEM`
   - 新增 `NotificationCategory` 与事件编码
   - `notifications` 表新增 `category`、`event_code`、`metadata`
2. 增加用户偏好能力
   - 新增 `notification_user_preference`
   - 提供 `GET/PUT /api/v1/notifications/preferences`
3. 增加免打扰能力
   - 新增 `notification_user_dnd`
   - 提供 `GET/PUT /api/v1/notifications/dnd`
4. 未读数增量维护
   - `NotificationUnreadCountStore` 新增原子 `increment/decrement`
   - 创建通知成功时优先递增未读缓存
   - 单条已读和全部已读时按真实更新行数递减未读缓存
5. 缓存失效优化
   - 去掉“创建/已读后直接删除未读缓存”的粗粒度策略
   - 聚合列表仍按用户维度失效，本阶段未引入组级快照缓存
6. 第二阶段最小广播骨架
   - `PostPublishedIntegrationEvent` 补齐 `authorId`
   - 新增 `notification_campaign`、`notification_campaign_shard`、`notification_delivery`
   - 用户服务新增 `GET /api/v1/users/{userId}/followers/cursor`
   - 通知服务新增 `PostPublishedNotificationConsumer`
   - 偏好新增 `publishEnabled`
   - 规则明确为“偏好关闭则不生成通知；DND 命中仍写 inbox，但跳过 WebSocket 主动推送”

## 明确未做

以下仍属于设计文档中的后续阶段能力，本次未实现：

1. 作者级订阅
2. digest 补发
3. group_state
4. 多通道真实投递（Push/邮件/短信）
5. 系统公告执行链路整合

## 当前一致性边界

1. PostgreSQL 仍是通知已读状态与通知明细的唯一真源。
2. Redis 未读数缓存采用“命中则原子增减，未命中则下次查询回源重建”的策略，且未读数增减与聚合缓存失效统一在事务提交后执行。
3. 聚合列表缓存仍按用户维度失效，因此本阶段解决了未读数抖动问题，但没有把聚合缓存细化到组级。
4. 历史 `notifications` 数据在脚本执行时会按 `type` 回填 `category/event_code`，旧记录恢复时也会在领域层做兼容回退。
5. 系统公告仍走 `global_announcements` 和 `/topic/announcements`，不进入本阶段的发布广播链路。
