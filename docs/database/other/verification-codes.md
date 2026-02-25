# verification_codes - 验证码表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | verification_codes |
| 描述 | 邮箱验证码表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Email | string | 否 | - | 邮箱地址 |
| Code | string(6) | 否 | - | 验证码（6位） |
| CreatedAt | DateTimeOffset | 否 | UtcNow | 创建时间 |
| ExpiresAt | DateTimeOffset | 否 | - | 过期时间 |
| IsUsed | bool | 否 | false | 是否已使用 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_verification_codes | Id | 主键 | 主键索引 |
| IX_VerificationCode_Email | Email | 普通 | 按邮箱查询 |
| IX_VerificationCode_Email_Code | (Email, Code) | 普通 | 验证码校验 |

## 业务规则

1. 验证码有效期通常为 5-15 分钟
2. 验证码使用后标记为已使用，不可重复使用
3. 同一邮箱短时间内不能频繁发送验证码（防刷）
4. 验证码用于：
   - 用户注册
   - 密码重置
   - 邮箱绑定/换绑
