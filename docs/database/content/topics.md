# topics - 话题表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | topics |
| 描述 | 话题/标签表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Name | string(50) | 否 | - | 话题名称 |
| Description | string(200) | 是 | - | 话题描述 |
| CreateTime | DateTimeOffset | 否 | - | 话题创建时间 |
| LastActiveTime | DateTimeOffset | 否 | - | 话题最后活跃时间 |
| CreatorId | string | 否 | - | 话题创建者ID |
| HotnessScore | double | 否 | 0 | 话题热度分数 |
| Status | int | 否 | 0 | 话题状态：0-正常，1-禁用 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_topics | Id | 主键 | 主键索引 |
| IX_Topic_Name | Name | 唯一 | 话题名称唯一索引 |
| IX_Topic_CreatorId | CreatorId | 普通 | 查询用户创建的话题 |
| IX_Topic_HotnessScore | HotnessScore | 普通 | 热门话题排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | CreatorId | 多对一 | 话题创建者 |
| topic_stats | TopicId | 一对一 | 话题统计 |
| posts | TopicId | 一对多 | 话题下的文章 |

## 业务规则

1. 话题名称全局唯一
2. 话题热度根据文章数、讨论数、浏览数等计算
3. 禁用的话题不会在列表中显示
