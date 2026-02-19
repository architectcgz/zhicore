# 通知表新增已读状态迁移说明

## 目标

为 `notifications` 表引入逐条已读状态列 `is_read`、`read_at`，为后续替换检查点策略做准备。迁移同时会根据 `user_notification_checkpoints` 中的现有数据对历史通知做回填。

## 迁移步骤

1. **拉取代码并还原依赖**
   ```bash
   cd blog-api
   dotnet build
   ```

2. **执行数据库迁移**
   ```bash
   dotnet ef database update 20251103103748_AddNotificationReadState
   ```

   迁移会完成以下操作：
   - 新增 `notifications.is_read`（默认 `false`）、`notifications.read_at`
   - 创建索引 `IX_Notifications_User_IsRead_Type_CreateTime`
   - 根据 `user_notification_checkpoints` 把旧检查点覆盖的通知标记为已读，并回填 `read_at = create_time`

3. **验证数据正确性**
   ```sql
   -- 检查是否仍存在位于检查点之前但 is_read = false 的通知
   SELECT n.user_id, n.type, MIN(n.id) AS first_unread_id, c.comment_notification_checkpoint_id
   FROM notifications n
   JOIN user_notification_checkpoints c ON n.user_id = c.user_id
   WHERE n.type = 1
     AND n.id <= c.comment_notification_checkpoint_id
     AND n.is_read = false
   GROUP BY 1,2,4;
   ```

   将 `type` 与对应的检查点字段替换后重复查询，期望结果均为空。

4. **（可选）更新统计缓存**
   如果生产环境存在通知未读数的缓存（Redis 等），迁移后建议主动重建或失效相关键，以便新列生效。

## 回滚说明

执行
```bash
dotnet ef database update <上一版本迁移名>
```
会移除新增的列和索引，但不会还原 `is_read` 的数据。若需要完整回滚，需要同时清理应用逻辑中对新列的依赖。

## 后续改造建议

迁移完成后，可逐步将后端/前端的未读统计逻辑切换为基于 `is_read` 字段，实现：
- 点对点地标记单条通知已读
- 聚合通知下的部分已读/未读场景
- 更灵活的缓存与同步策略

在完全迁移至逐条状态后，即可移除 `user_notification_checkpoints` 表和相关代码。
