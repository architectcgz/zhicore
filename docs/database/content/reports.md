# reports - 举报表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | reports |
| 描述 | 内容举报表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Type | ReportType | 否 | - | 举报类型 |
| TargetId | long | 否 | - | 被举报内容的ID |
| Reason | ReportReason | 否 | - | 举报原因 |
| Description | string(500) | 是 | - | 举报详细描述 |
| ReporterId | string | 否 | - | 举报人ID |
| Status | ReportStatus | 否 | Pending | 举报状态 |
| ProcessorId | string | 是 | - | 处理人ID |
| ProcessNote | string(500) | 是 | - | 处理备注 |
| ActionType | ReportActionType | 是 | - | 处理动作类型 |
| BanDays | int | 是 | - | 封禁天数（临时封禁时使用） |
| CreateTime | DateTimeOffset | 否 | - | 举报创建时间 |
| ProcessTime | DateTimeOffset | 是 | - | 处理时间 |
| IpAddress | string(45) | 是 | - | 举报人IP地址 |
| UserAgent | string(500) | 是 | - | 举报人用户代理 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 枚举定义

### ReportType - 举报类型
| 值 | 说明 |
|----|------|
| Post | 文章 |
| Topic | 话题 |
| Comment | 评论 |

### ReportReason - 举报原因
| 值 | 说明 |
|----|------|
| Spam | 垃圾内容 |
| Harassment | 骚扰 |
| Violence | 暴力内容 |
| Pornography | 色情内容 |
| FalseInfo | 虚假信息 |
| Copyright | 侵权 |
| Other | 其他 |

### ReportStatus - 举报状态
| 值 | 说明 |
|----|------|
| Pending | 待处理 |
| Processing | 处理中 |
| Resolved | 已处理 |
| Rejected | 已驳回 |

### ReportActionType - 处理动作类型
| 值 | 说明 |
|----|------|
| None | 无操作 |
| Warning | 警告 |
| DeleteContent | 删除内容 |
| TempBan | 临时封禁 |
| PermBan | 永久封禁 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_reports | Id | 主键 | 主键索引 |
| IX_Report_Status | Status | 普通 | 按状态筛选 |
| IX_Report_ReporterId | ReporterId | 普通 | 查询用户的举报 |
| IX_Report_Type_TargetId | (Type, TargetId) | 普通 | 查询某内容的举报 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | ReporterId | 多对一 | 举报人 |
| AspNetUsers | ProcessorId | 多对一 | 处理人 |
