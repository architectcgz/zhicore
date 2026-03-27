# Notification 第一阶段实现状态

## 已落地范围

本次在 `zhicore-notification` 内完成了第一阶段最小闭环实现，范围限定在交互通知中心，不包含第二阶段及之后的平台广播能力。

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

## 明确未做

以下仍属于设计文档中的后续阶段能力，本次未实现：

1. 作者级订阅
2. 发布广播链路、campaign/shard/delivery
3. digest 补发
4. group_state
5. 微批聚合与分片投递

## 当前一致性边界

1. PostgreSQL 仍是通知已读状态与通知明细的唯一真源。
2. Redis 未读数缓存采用“命中则原子增减，未命中则下次查询回源重建”的策略，且未读数增减与聚合缓存失效统一在事务提交后执行。
3. 聚合列表缓存仍按用户维度失效，因此本阶段解决了未读数抖动问题，但没有把聚合缓存细化到组级。
4. 历史 `notifications` 数据在脚本执行时会按 `type` 回填 `category/event_code`，旧记录恢复时也会在领域层做兼容回退。
