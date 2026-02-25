# AspNetUserRoles - 用户角色关联表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | AspNetUserRoles |
| 描述 | 用户与角色的多对多关联表 |
| 主键 | (UserId, RoleId) 复合主键 |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| UserId | string | 否 | - | 用户ID |
| RoleId | string | 否 | - | 角色ID |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_AspNetUserRoles | (UserId, RoleId) | 主键 | 复合主键索引 |
| IX_AspNetUserRoles_RoleId | RoleId | 普通 | 角色ID索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 关联用户 |
| AspNetRoles | RoleId | 多对一 | 关联角色 |
