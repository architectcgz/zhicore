# user_check_ins - 用户签到表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | user_check_ins |
| 描述 | 用户每日签到记录表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| UserId | string | 否 | - | 签到用户ID |
| CheckInDate | DateTimeOffset | 否 | - | 签到日期（用于唯一索引） |
| CheckInTime | DateTimeOffset | 否 | - | 具体签到时间 |
| CheckInIp | string | 是 | - | 签到IP地址 |
| Points | int | 否 | - | 签到获得的积分 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_user_check_ins | Id | 主键 | 主键索引 |
| IX_UserCheckIn_UserId_CheckInDate | (UserId, CheckInDate) | 唯一 | 每用户每天只能签到一次 |
| IX_UserCheckIn_UserId | UserId | 普通 | 查询用户签到记录 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 签到用户 |

## 业务规则

1. 每个用户每天只能签到一次
2. 签到可获得积分奖励
3. 连续签到可获得额外奖励
