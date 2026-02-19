# global_announcements - 全局公告表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | global_announcements |
| 描述 | 面向所有用户的平台公告表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 公告ID（雪花算法） |
| Title | string(100) | 否 | - | 公告标题 |
| Content | string(2000) | 否 | - | 公告内容 |
| IsMarkdown | bool | 否 | false | 是否Markdown格式 |
| Type | int | 否 | - | 公告类型 |
| Link | string(200) | 是 | - | 相关链接 |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| CreatedById | string | 否 | - | 创建者ID |
| IsEnabled | bool | 否 | true | 是否启用 |
| ExpiresAt | DateTimeOffset | 是 | - | 过期时间 |
| Priority | int | 否 | 0 | 优先级 |
| Deleted | bool | 否 | false | 是否已删除 |

## 优先级说明

| 值 | 说明 |
|----|------|
| 0 | 普通 |
| 1 | 重要 |
| 2 | 紧急 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_global_announcements | Id | 主键 | 主键索引 |
| IX_GlobalAnnouncement_IsEnabled | IsEnabled | 普通 | 筛选启用的公告 |
| IX_GlobalAnnouncement_Priority | Priority | 普通 | 按优先级排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | CreatedById | 多对一 | 公告创建者 |

## 业务规则

1. 公告可以设置过期时间，过期后不再显示
2. 公告按优先级和创建时间排序
3. 只有管理员可以创建公告
