# categories - 分类表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | categories |
| 描述 | 文章分类表，支持层级结构 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Name | string(20) | 否 | "" | 分类名称 |
| Description | string(100) | 是 | "" | 分类描述 |
| OwnerId | string(100) | 否 | - | 分类创建者ID |
| ParentId | long | 是 | - | 父分类ID（支持层级） |
| CreateTime | DateTimeOffset | 否 | - | 分类创建时间 |
| UpdateTime | DateTimeOffset | 否 | - | 分类更新时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_categories | Id | 主键 | 主键索引 |
| IX_Category_OwnerId | OwnerId | 普通 | 查询用户的分类 |
| IX_Category_ParentId | ParentId | 普通 | 查询子分类 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | OwnerId | 多对一 | 分类创建者 |
| categories | ParentId | 自关联 | 父分类 |
| post_category | CategoryId | 一对多 | 分类下的文章 |

## 业务规则

1. 分类支持层级结构（通过 ParentId 实现）
2. 每个用户可以创建自己的分类
3. 删除分类时使用软删除
