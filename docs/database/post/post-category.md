# post_category - 文章分类关联表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | post_category |
| 描述 | 文章与分类的多对多关联表 |
| 主键 | (PostId, CategoryId) 复合主键 |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| PostId | long | 否 | - | 文章ID |
| CategoryId | long | 否 | - | 分类ID |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_post_category | (PostId, CategoryId) | 主键 | 复合主键索引 |
| IX_PostCategory_CategoryId | CategoryId | 普通 | 查询分类下的文章 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| posts | PostId | 多对一 | 关联文章 |
| categories | CategoryId | 多对一 | 关联分类 |

## 业务规则

1. 一篇文章可以属于多个分类
2. 一个分类可以包含多篇文章
