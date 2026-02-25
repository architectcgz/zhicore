# feedbacks - 反馈表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | feedbacks |
| 描述 | 用户反馈建议表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Title | string(200) | 否 | - | 反馈标题 |
| Type | FeedbackType | 否 | - | 反馈类型 |
| Content | string(2000) | 否 | - | 反馈内容详情 |
| UserId | string | 否 | - | 反馈用户ID |
| ContactEmail | string(100) | 是 | - | 联系邮箱（可选） |
| Status | FeedbackStatus | 否 | Pending | 反馈状态 |
| ProcessorId | string | 是 | - | 处理人ID |
| ProcessReply | string(1000) | 是 | - | 处理回复 |
| CreateTime | DateTimeOffset | 否 | - | 反馈创建时间 |
| ProcessTime | DateTimeOffset | 是 | - | 处理时间 |
| IpAddress | string(45) | 是 | - | 反馈用户IP地址 |
| UserAgent | string(500) | 是 | - | 反馈用户代理 |
| Priority | FeedbackPriority | 否 | Normal | 反馈优先级 |
| IsRead | bool | 否 | false | 是否已读 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 枚举定义

### FeedbackType - 反馈类型
| 值 | 说明 |
|----|------|
| Bug | Bug报告 |
| Feature | 功能建议 |
| Improvement | 改进建议 |
| Question | 问题咨询 |
| Other | 其他 |

### FeedbackStatus - 反馈状态
| 值 | 说明 |
|----|------|
| Pending | 待处理 |
| Processing | 处理中 |
| Resolved | 已解决 |
| Closed | 已关闭 |

### FeedbackPriority - 反馈优先级
| 值 | 说明 |
|----|------|
| Low | 低 |
| Normal | 普通 |
| High | 高 |
| Urgent | 紧急 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_feedbacks | Id | 主键 | 主键索引 |
| IX_Feedback_Status | Status | 普通 | 按状态筛选 |
| IX_Feedback_UserId | UserId | 普通 | 查询用户的反馈 |
| IX_Feedback_Priority | Priority | 普通 | 按优先级排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 反馈用户 |
| AspNetUsers | ProcessorId | 多对一 | 处理人 |
