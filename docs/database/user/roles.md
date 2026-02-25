# AspNetRoles - 角色表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | AspNetRoles |
| 描述 | 系统角色表，继承自 ASP.NET Identity |
| 主键 | Id (string) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | string | 否 | - | 角色ID（GUID格式） |
| Name | string | 否 | - | 角色名称 |
| NormalizedName | string | 否 | - | 标准化角色名（大写） |
| ConcurrencyStamp | string | 是 | - | 并发戳 |
| Description | string | 是 | - | 角色描述 |
| CreateTime | DateTimeOffset | 否 | UtcNow | 创建时间 |
| UpdateTime | DateTimeOffset | 否 | UtcNow | 更新时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_AspNetRoles | Id | 主键 | 主键索引 |
| IX_AspNetRoles_NormalizedName | NormalizedName | 唯一 | 角色名唯一索引 |

## 预定义角色

| 角色名 | 说明 |
|--------|------|
| Admin | 系统管理员 |
| Moderator | 内容审核员 |
| User | 普通用户 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUserRoles | RoleId | 一对多 | 拥有该角色的用户 |
