# year_summary - 年度总结表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | year_summary |
| 描述 | 用户年度总结表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| OwnerId | string | 否 | - | 所属用户ID |
| Year | int | 否 | - | 年份 |
| Summary | string | 否 | "" | 年度总结内容 |
| ShortSummary | string | 是 | - | 年度简短描述 |
| BgImage | string | 是 | - | 年度封面图片URL |
| Visibility | Visibility | 否 | - | 可见性设置 |
| CreateTime | DateTimeOffset | 否 | UtcNow | 创建时间 |
| UpdateTime | DateTimeOffset | 否 | UtcNow | 更新时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_year_summary | Id | 主键 | 主键索引 |
| IX_YearSummary_OwnerId_Year | (OwnerId, Year) | 唯一 | 每用户每年一条 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | OwnerId | 多对一 | 总结所属用户 |

## 业务规则

1. 每个用户每年只能有一条年度总结
2. 可以设置可见性（公开/私密/仅关注者）
