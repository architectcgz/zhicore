# todos - 待办事项表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | todos |
| 描述 | 用户待办事项表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| OwnerId | string | 否 | - | 所属用户ID |
| Title | string | 否 | - | Todo标题 |
| Description | string | 是 | - | Todo描述 |
| Completed | bool | 否 | false | 完成标识 |
| CompleteTime | DateTimeOffset | 否 | - | 完成时间 |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_todos | Id | 主键 | 主键索引 |
| IX_Todo_OwnerId | OwnerId | 普通 | 查询用户的待办 |
| IX_Todo_Completed | Completed | 普通 | 按完成状态筛选 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | OwnerId | 多对一 | 待办所属用户 |
