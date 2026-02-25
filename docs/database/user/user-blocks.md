# user_blocks - 用户拉黑表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | user_blocks |
| 描述 | 用户拉黑关系表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| BlockerId | string | 否 | - | 拉黑者ID（谁拉黑了别人） |
| BlockedUserId | string | 否 | - | 被拉黑者ID（被谁拉黑了） |
| BlockTime | DateTimeOffset | 否 | - | 拉黑时间 |
| Reason | string(500) | 是 | - | 拉黑原因 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_user_blocks | Id | 主键 | 主键索引 |
| IX_UserBlock_BlockerId_BlockedUserId | (BlockerId, BlockedUserId) | 唯一 | 防止重复拉黑 |
| IX_UserBlock_BlockerId | BlockerId | 普通 | 查询拉黑列表 |
| IX_UserBlock_BlockedUserId | BlockedUserId | 普通 | 查询被谁拉黑 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | BlockerId | 多对一 | 拉黑者 |
| AspNetUsers | BlockedUserId | 多对一 | 被拉黑者 |

## 业务规则

1. 用户不能拉黑自己
2. 拉黑后：
   - 双方无法互相发送私信
   - 被拉黑者无法评论拉黑者的文章
   - 被拉黑者无法点赞拉黑者的内容
   - 被拉黑者无法关注拉黑者
3. 拉黑关系是单向的
