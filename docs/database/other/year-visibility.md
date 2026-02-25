# year_visibility - 年度可见性表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | year_visibility |
| 描述 | 用户年度内容可见性设置表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Year | int | 否 | - | 年份 |
| Visibility | Visibility | 否 | Private | 可见性设置 |
| OwnerId | string | 否 | - | 所属用户ID |

## 可见性枚举 (Visibility)

| 值 | 说明 |
|----|------|
| Public | 公开 |
| Private | 私密 |
| FollowersOnly | 仅关注者可见 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_year_visibility | Id | 主键 | 主键索引 |
| IX_YearVisibility_OwnerId_Year | (OwnerId, Year) | 唯一 | 每用户每年一条 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | OwnerId | 多对一 | 设置所属用户 |

## 业务用途

用于控制用户某一年的所有内容（文章、总结等）的默认可见性。
