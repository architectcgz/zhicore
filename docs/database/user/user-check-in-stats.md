# user_check_in_stats - 用户签到统计表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | user_check_in_stats |
| 描述 | 用户签到统计信息表 |
| 主键 | UserId (string) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| UserId | string | 否 | - | 用户ID（主键） |
| TotalCheckIns | int | 否 | 0 | 总签到次数 |
| CurrentStreak | int | 否 | 0 | 当前连续签到天数 |
| MaxStreak | int | 否 | 0 | 最大连续签到天数 |
| LastCheckInDate | DateTimeOffset | 否 | - | 上次签到时间 |
| TotalPoints | int | 否 | 0 | 用户当前总积分 |
| UpdateTime | DateTimeOffset | 否 | - | 统计更新时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_user_check_in_stats | UserId | 主键 | 主键索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 一对一 | 关联用户 |
