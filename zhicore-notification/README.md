# zhicore-notification

## 第一阶段范围

当前模块已完成：

1. 第一阶段：交互通知中心 + 平台化扩展位 + 偏好 / DND
2. 第二阶段最小闭环：`POST_PUBLISHED` 发布广播骨架，包含 `campaign/shard/delivery`、粉丝游标扫描、收件箱物化和投递审计

当前仍未包含 digest、group_state、作者订阅、多通道真实投递，以及系统公告执行链路重构。

本阶段落地内容：

1. 扩展通知元数据：`notifications` 增加 `category`、`event_code`、`metadata`
2. 新增用户偏好：`notification_user_preference`
3. 新增免打扰：`notification_user_dnd`
4. 新增接口：
   - `GET /api/v1/notifications/preferences`
   - `PUT /api/v1/notifications/preferences`
   - `GET /api/v1/notifications/dnd`
   - `PUT /api/v1/notifications/dnd`
5. 未读数缓存改为“命中时 Redis 原子增减，未命中时查询链路回源重建”
6. 新增发布广播骨架：
   - `PostPublishedIntegrationEvent` 带 `authorId`
   - `notification_campaign`
   - `notification_campaign_shard`
   - `notification_delivery`
   - `GET /api/v1/users/{userId}/followers/cursor`
   - `POST_PUBLISHED` 收件箱通知

## 迁移兼容

- 初始化脚本会为历史 `notifications` 记录按 `type` 回填 `category` 与 `event_code`
- 领域恢复时会把空白或旧哨兵 `event_code` 视为历史数据，并按 `NotificationType` 回退 `category/event_code`

## 配置

- `notification.preference.default-timezone`: 偏好/DND 默认时区，当前默认 `Asia/Shanghai`
- `cache.ttl.unread-count`: 未读数缓存 TTL，当前配置为 `30`

## 默认行为

- 用户不存在偏好记录时，默认所有通知开关为开启
- 关注作者发布通知使用独立 `publishEnabled` 开关，默认开启
- 用户不存在 DND 记录时，默认 DND 关闭，默认时区为 `Asia/Shanghai`
- 启用 DND 时必须提供 `startTime` 和 `endTime`
- 通知创建/已读后的未读数缓存增减与聚合列表缓存失效统一在事务提交后执行
- 单条已读仅在 `is_read = false` 时更新，避免并发重复扣减未读数
- 全部已读按实际更新行数递减未读数缓存
- 发布广播命中 DND 时仍写入 inbox `notifications`，但不走 WebSocket 主动推送
- 系统公告仍使用 `global_announcements` + `/topic/announcements`，不复用本阶段发布广播链路

## 相关文档

- 架构设计：[docs/architecture/zhicore-notification-platform-design.md](../docs/architecture/zhicore-notification-platform-design.md)
- 实现状态：[docs/architecture/notification-phase1-implementation-status.md](../docs/architecture/notification-phase1-implementation-status.md)

## 验证

```bash
mvn -pl zhicore-notification -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=NotificationApplicationServiceTest,NotificationUnreadCountContractTest,NotificationPreferenceServiceTest,NotificationPreferenceControllerTest,NotificationPlatformMetadataContractTest test
mvn -pl zhicore-notification -am test
```
