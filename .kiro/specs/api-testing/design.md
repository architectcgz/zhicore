# 测试方案设计文档

## Overview

本文档设计博客微服务系统的完整测试方案，包括API功能测试、集成测试和压力测试。所有测试脚本和配置文件统一放置在 `tests/` 目录下，测试结果记录在 `tests/results/` 目录。

## 测试目录结构

```
tests/
├── api/                          # API功能测试
│   ├── user/                     # 用户服务测试
│   ├── post/                     # 文章服务测试
│   ├── comment/                  # 评论服务测试
│   ├── message/                  # 消息服务测试
│   ├── notification/             # 通知服务测试
│   ├── search/                   # 搜索服务测试
│   ├── ranking/                  # 排行榜服务测试
│   ├── upload/                   # 上传服务测试
│   ├── admin/                    # 管理后台测试
│   └── gateway/                  # 网关服务测试
├── load/                         # 压力测试
│   ├── jmeter/                   # JMeter测试计划
│   └── scripts/                  # 压测脚本
├── results/                      # 测试结果
│   └── test-status.md            # 测试状态追踪
├── config/                       # 测试配置
│   └── test-env.json             # 环境配置
├── run-all-tests.ps1             # Windows测试运行脚本
└── run-all-tests.sh              # Linux测试运行脚本
```

## 测试工具选型

| 测试类型 | 工具 | 说明 |
|---------|------|------|
| API功能测试 | curl + PowerShell/Bash | 轻量级，无需额外依赖 |
| 压力测试 | Apache JMeter | 业界标准，支持复杂场景 |
| 结果报告 | Markdown | 易于阅读和版本控制 |

## 测试环境配置

```json
{
  "gateway_url": "http://localhost:8000",
  "user_service_url": "http://localhost:8001",
  "post_service_url": "http://localhost:8002",
  "comment_service_url": "http://localhost:8003",
  "message_service_url": "http://localhost:8004",
  "notification_service_url": "http://localhost:8005",
  "search_service_url": "http://localhost:8006",
  "ranking_service_url": "http://localhost:8007",
  "upload_service_url": "http://localhost:8008",
  "admin_service_url": "http://localhost:8009",
  "leaf_service_url": "http://localhost:8010"
}
```

## API测试设计

### 1. 用户服务测试 (ZhiCore-user)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| USER-001 | /api/v1/auth/register | POST | 201 | 用户注册 |
| USER-002 | /api/v1/auth/login | POST | 200 | 用户登录 |
| USER-003 | /api/v1/auth/refresh | POST | 200 | Token刷新 |
| USER-004 | /api/v1/users/{id} | GET | 200 | 获取用户信息 |
| USER-005 | /api/v1/users/{id}/follow | POST | 200 | 关注用户 |
| USER-006 | /api/v1/users/{id}/follow | DELETE | 200 | 取消关注 |
| USER-007 | /api/v1/users/{id}/followers | GET | 200 | 获取粉丝列表 |
| USER-008 | /api/v1/users/{id}/following | GET | 200 | 获取关注列表 |
| USER-009 | /api/v1/users/check-in | POST | 200 | 签到 |
| USER-010 | /api/v1/users/check-in/status | GET | 200 | 获取签到状态 |

### 2. 文章服务测试 (ZhiCore-post)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| POST-001 | /api/v1/posts | POST | 201 | 创建文章 |
| POST-002 | /api/v1/posts/{id} | GET | 200 | 获取文章详情 |
| POST-003 | /api/v1/posts/{id} | PUT | 200 | 更新文章 |
| POST-004 | /api/v1/posts/{id} | DELETE | 200 | 删除文章 |
| POST-005 | /api/v1/posts/{id}/publish | POST | 200 | 发布文章 |
| POST-006 | /api/v1/posts | GET | 200 | 获取文章列表 |
| POST-007 | /api/v1/posts/{id}/like | POST | 200 | 点赞文章 |
| POST-008 | /api/v1/posts/{id}/like | DELETE | 200 | 取消点赞 |
| POST-009 | /api/v1/posts/{id}/favorite | POST | 200 | 收藏文章 |
| POST-010 | /api/v1/posts/{id}/favorite | DELETE | 200 | 取消收藏 |

### 3. 评论服务测试 (ZhiCore-comment)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| COMMENT-001 | /api/comments | POST | 201 | 创建评论 |
| COMMENT-002 | /api/comments/post/{postId} | GET | 200 | 获取评论列表 |
| COMMENT-003 | /api/comments/{id}/replies | GET | 200 | 获取子评论 |
| COMMENT-004 | /api/comments/{id} | DELETE | 200 | 删除评论 |
| COMMENT-005 | /api/comments/{id}/like | POST | 200 | 点赞评论 |
| COMMENT-006 | /api/comments/{id}/like | DELETE | 200 | 取消点赞 |
| COMMENT-007 | /api/comments | POST | 201 | 回复评论（带parentId） |
| COMMENT-008 | /api/comments/post/{postId}?sort=hot | GET | 200 | 按热度排序获取评论 |
| COMMENT-009 | /api/comments/post/{postId}?sort=time | GET | 200 | 按时间排序获取评论 |
| COMMENT-010 | /api/comments/{id} | GET | 200 | 获取单条评论详情 |

### 4. 消息服务测试 (ZhiCore-message)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| MSG-001 | /api/messages | POST | 201 | 发送消息 |
| MSG-002 | /api/messages/conversation/{id} | GET | 200 | 获取消息历史 |
| MSG-003 | /api/conversations | GET | 200 | 获取会话列表 |
| MSG-004 | /api/messages/{id}/read | POST | 200 | 标记已读 |

### 5. 通知服务测试 (ZhiCore-notification)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| NOTIF-001 | /api/notifications | GET | 200 | 获取通知列表 |
| NOTIF-002 | /api/notifications/{id}/read | POST | 200 | 标记已读 |
| NOTIF-003 | /api/notifications/read-all | POST | 200 | 全部已读 |
| NOTIF-004 | /api/notifications/unread-count | GET | 200 | 获取未读数 |

### 6. 搜索服务测试 (ZhiCore-search)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| SEARCH-001 | /api/v1/search | GET | 200 | 搜索文章 |
| SEARCH-002 | /api/v1/search/suggestions | GET | 200 | 搜索建议 |

### 7. 排行榜服务测试 (ZhiCore-ranking)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| RANK-001 | /api/v1/ranking/posts/hot | GET | 200 | 热门文章 |
| RANK-002 | /api/v1/ranking/creators | GET | 200 | 创作者排行 |
| RANK-003 | /api/v1/ranking/topics | GET | 200 | 热门话题 |

### 8. 上传服务测试 (ZhiCore-upload)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| UPLOAD-001 | /api/upload/image | POST | 200 | 上传图片 |
| UPLOAD-002 | /api/upload/file | POST | 200 | 上传文件 |

### 9. 管理后台测试 (ZhiCore-admin)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| ADMIN-001 | /admin/users | GET | 200 | 用户列表 |
| ADMIN-002 | /admin/users/{id}/disable | POST | 200 | 禁用用户 |
| ADMIN-003 | /admin/posts | GET | 200 | 文章列表 |
| ADMIN-004 | /admin/posts/{id} | DELETE | 200 | 删除文章 |
| ADMIN-005 | /admin/comments | GET | 200 | 评论列表 |
| ADMIN-006 | /admin/comments/{id} | DELETE | 200 | 删除评论 |
| ADMIN-007 | /admin/reports | GET | 200 | 举报列表 |
| ADMIN-008 | /admin/reports/{id}/handle | POST | 200 | 处理举报 |

### 10. 网关服务测试 (ZhiCore-gateway)

| 测试ID | 接口 | 方法 | 预期状态码 | 说明 |
|--------|------|------|-----------|------|
| GW-001 | /api/v1/posts | GET | 200 | 路由转发测试 |
| GW-002 | /api/v1/posts | POST | 401 | 无Token认证测试 |
| GW-003 | /api/v1/posts | POST | 429 | 限流测试 |

## 压力测试设计

### 测试场景

| 场景ID | 接口 | 并发用户 | 持续时间 | P99目标 | QPS目标 |
|--------|------|---------|---------|---------|---------|
| LOAD-001 | GET /api/v1/posts/{id} | 500 | 5分钟 | <100ms | >1000 |
| LOAD-002 | GET /api/v1/posts | 300 | 5分钟 | <200ms | >500 |
| LOAD-003 | POST /api/v1/posts/{id}/like | 500 | 5分钟 | <100ms | >2000 |
| LOAD-004 | GET /api/v1/search | 200 | 5分钟 | <300ms | >200 |
| LOAD-005 | GET /api/notifications | 300 | 5分钟 | <200ms | >500 |
| LOAD-006 | GET /api/comments/post/{postId} | 400 | 5分钟 | <150ms | >800 |
| LOAD-007 | POST /api/comments | 300 | 5分钟 | <200ms | >500 |
| LOAD-008 | POST /api/comments/{id}/like | 400 | 5分钟 | <100ms | >1500 |

### JMeter测试计划配置

```xml
<!-- 线程组配置 -->
<ThreadGroup>
  <stringProp name="ThreadGroup.num_threads">${CONCURRENT_USERS}</stringProp>
  <stringProp name="ThreadGroup.ramp_time">60</stringProp>
  <longProp name="ThreadGroup.duration">300</longProp>
</ThreadGroup>

<!-- 响应断言 -->
<ResponseAssertion>
  <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
  <stringProp name="Assertion.test_strings">200</stringProp>
</ResponseAssertion>

<!-- 响应时间断言 -->
<DurationAssertion>
  <stringProp name="DurationAssertion.duration">${MAX_RESPONSE_TIME}</stringProp>
</DurationAssertion>
```

## 测试状态追踪

测试状态记录在 `tests/results/test-status.md` 文件中，格式如下：

```markdown
# 测试状态追踪

## 用户服务测试
- [x] USER-001 用户注册 - PASS (2024-01-14)
- [ ] USER-002 用户登录 - PENDING
- [ ] USER-003 Token刷新 - PENDING
...

## 压力测试
- [ ] LOAD-001 文章详情压测 - PENDING
...
```

## 测试执行流程

1. **环境检查** - 验证所有服务健康状态
2. **API功能测试** - 按模块顺序执行
3. **压力测试** - 在功能测试通过后执行
4. **结果汇总** - 更新测试状态文件

## Error Handling

- 服务不可用时跳过该模块测试，记录为SKIP
- 测试失败时记录错误信息和响应内容
- 压力测试失败时记录P99延迟和实际QPS

## Testing Strategy

- 使用curl进行API测试，轻量无依赖
- 使用JMeter进行压力测试，支持复杂场景
- 测试结果使用Markdown记录，便于版本控制
- 每个测试用例独立执行，互不影响

## 边界测试设计

### 1. 数值边界测试

| 测试ID | 测试场景 | 测试值 | 预期结果 |
|--------|---------|--------|----------|
| BOUND-001 | 页码为0 | page=0 | 返回第一页或400 |
| BOUND-002 | 页码为负数 | page=-1 | 返回400错误 |
| BOUND-003 | 页码为最大整数 | page=2147483647 | 返回空列表或400 |
| BOUND-004 | 页面大小为0 | size=0 | 返回400或使用默认值 |
| BOUND-005 | 页面大小为负数 | size=-1 | 返回400错误 |
| BOUND-006 | 页面大小为1000 | size=1000 | 返回限制后数据或400 |
| BOUND-007 | ID为0 | id=0 | 返回404错误 |
| BOUND-008 | ID为负数 | id=-1 | 返回400或404错误 |
| BOUND-009 | ID为最大Long值 | id=9223372036854775807 | 返回404错误 |
| BOUND-010 | 数量参数为0 | count=0 | 返回空结果或400 |
| BOUND-011 | 数量参数为负数 | count=-1 | 返回400错误 |
| BOUND-012 | 偏移量为负数 | offset=-1 | 返回400错误 |
| BOUND-013 | 限制数为超大值 | limit=10000 | 返回限制后数据 |
| BOUND-014 | 时间戳为0 | timestamp=0 | 返回400或正确处理 |
| BOUND-015 | 时间戳为未来时间 | timestamp=future | 返回400或正确处理 |

### 2. 字符串边界测试

| 测试ID | 测试场景 | 测试值 | 预期结果 |
|--------|---------|--------|----------|
| BOUND-016 | 空字符串 | "" | 返回400错误 |
| BOUND-017 | 纯空格字符串 | "   " | 返回400或正确处理 |
| BOUND-018 | 单字符输入 | "a" | 正确处理或验证错误 |
| BOUND-019 | 最大长度字符串 | 200字符 | 正确存储或400 |
| BOUND-020 | 超过最大长度 | 201字符 | 返回400错误 |
| BOUND-021 | 包含换行符 | "test\ntest" | 正确处理 |
| BOUND-022 | 包含制表符 | "test\ttest" | 正确处理 |
| BOUND-023 | Unicode字符 | "测试™®©" | 正确存储和显示 |
| BOUND-024 | Emoji字符 | "测试😀🎉" | 正确存储和显示 |
| BOUND-025 | 中文字符 | "中文测试" | 正确存储和显示 |
| BOUND-026 | 日文字符 | "テスト" | 正确存储和显示 |
| BOUND-027 | 韩文字符 | "테스트" | 正确存储和显示 |
| BOUND-028 | 阿拉伯文 | "اختبار" | 正确存储和显示 |
| BOUND-029 | 零宽字符 | "test\u200Btest" | 正确处理 |
| BOUND-030 | 控制字符 | "test\x00test" | 正确过滤或拒绝 |

### 3. 集合边界测试

| 测试ID | 测试场景 | 测试值 | 预期结果 |
|--------|---------|--------|----------|
| BOUND-031 | 空数组参数 | ids=[] | 返回400或空结果 |
| BOUND-032 | 单元素数组 | ids=[1] | 正确处理 |
| BOUND-033 | 超大数组 | ids=[1..1000] | 返回400或限制处理 |
| BOUND-034 | 重复元素数组 | ids=[1,1,1] | 去重或正确处理 |
| BOUND-035 | 包含null元素 | ids=[1,null,2] | 过滤或返回400 |
| BOUND-036 | 批量操作空列表 | items=[] | 返回400或空结果 |
| BOUND-037 | 批量操作单个元素 | items=[item] | 正确处理 |
| BOUND-038 | 批量操作超限 | items=[100+] | 返回400或部分处理 |
| BOUND-039 | 批量查询不存在ID | ids=[999999] | 返回空或部分结果 |
| BOUND-040 | 批量删除混合ID | ids=[exist,notexist] | 正确处理存在的项 |

### 4. 时间边界测试

| 测试ID | 测试场景 | 测试值 | 预期结果 |
|--------|---------|--------|----------|
| BOUND-041 | 开始>结束时间 | start>end | 返回400错误 |
| BOUND-042 | 开始=结束时间 | start=end | 返回空或正确处理 |
| BOUND-043 | 跨多年时间范围 | 2020-2026 | 正确处理 |
| BOUND-044 | 1毫秒时间范围 | 1ms | 正确处理 |
| BOUND-045 | 无效日期格式 | "invalid" | 返回400错误 |
| BOUND-046 | 闰年日期 | 2024-02-29 | 正确处理 |
| BOUND-047 | 时区边界 | UTC+14/-12 | 正确处理 |
| BOUND-048 | 夏令时切换 | DST transition | 正确处理 |
| BOUND-049 | Unix时间戳最大值 | 2147483647 | 正确处理或错误 |
| BOUND-050 | ISO8601格式 | 2026-01-16T12:00:00Z | 正确解析 |

## 安全注入测试设计

### 1. XSS注入测试

| 测试ID | 测试场景 | 测试Payload | 预期结果 |
|--------|---------|-------------|----------|
| SEC-001 | 基本script标签 | `<script>alert(1)</script>` | 转义或过滤 |
| SEC-002 | 大小写混合script | `<ScRiPt>alert(1)</ScRiPt>` | 转义或过滤 |
| SEC-003 | 编码后script | `%3Cscript%3Ealert(1)%3C/script%3E` | 转义或过滤 |
| SEC-004 | onclick事件 | `<div onclick="alert(1)">` | 事件被过滤 |
| SEC-005 | onerror事件 | `<img onerror="alert(1)">` | 事件被过滤 |
| SEC-006 | onload事件 | `<body onload="alert(1)">` | 事件被过滤 |
| SEC-007 | img标签onerror | `<img src=x onerror=alert(1)>` | 脚本被过滤 |
| SEC-008 | svg标签onload | `<svg onload=alert(1)>` | 脚本被过滤 |
| SEC-009 | iframe标签 | `<iframe src="javascript:alert(1)">` | 标签被过滤 |
| SEC-010 | javascript协议 | `javascript:alert(1)` | 协议被过滤 |
| SEC-011 | data协议 | `data:text/html,<script>alert(1)</script>` | 协议被过滤 |
| SEC-012 | vbscript协议 | `vbscript:msgbox(1)` | 协议被过滤 |
| SEC-013 | expression()CSS | `<div style="width:expression(alert(1))">` | 表达式被过滤 |
| SEC-014 | style标签 | `<style>body{background:url(javascript:alert(1))}</style>` | 样式被过滤 |
| SEC-015 | base64编码XSS | `<img src="data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+">` | 正确处理 |

### 2. SQL注入测试

| 测试ID | 测试场景 | 测试Payload | 预期结果 |
|--------|---------|-------------|----------|
| SEC-016 | 单引号注入 | `' OR '1'='1` | 注入被拒绝 |
| SEC-017 | 双引号注入 | `" OR "1"="1` | 注入被拒绝 |
| SEC-018 | 注释符-- | `admin'--` | 注入被拒绝 |
| SEC-019 | 注释符# | `admin'#` | 注入被拒绝 |
| SEC-020 | 注释符/* | `admin'/*` | 注入被拒绝 |
| SEC-021 | UNION SELECT | `' UNION SELECT * FROM users--` | 注入被拒绝 |
| SEC-022 | OR 1=1 | `' OR 1=1--` | 注入被拒绝 |
| SEC-023 | AND 1=1 | `' AND 1=1--` | 注入被拒绝 |
| SEC-024 | DROP TABLE | `'; DROP TABLE users;--` | 注入被拒绝 |
| SEC-025 | INSERT INTO | `'; INSERT INTO users VALUES(...)--` | 注入被拒绝 |
| SEC-026 | UPDATE SET | `'; UPDATE users SET password='hack'--` | 注入被拒绝 |
| SEC-027 | DELETE FROM | `'; DELETE FROM users;--` | 注入被拒绝 |
| SEC-028 | EXEC | `'; EXEC xp_cmdshell('dir');--` | 注入被拒绝 |
| SEC-029 | 时间盲注SLEEP | `' AND SLEEP(5)--` | 注入被拒绝 |
| SEC-030 | 布尔盲注 | `' AND 1=1 AND '1'='1` | 注入被拒绝 |

### 3. NoSQL注入测试

| 测试ID | 测试场景 | 测试Payload | 预期结果 |
|--------|---------|-------------|----------|
| SEC-031 | MongoDB $where | `{$where: "this.password == 'x'"}` | 注入被拒绝 |
| SEC-032 | MongoDB $gt/$lt | `{"password": {"$gt": ""}}` | 注入被拒绝 |
| SEC-033 | MongoDB $regex | `{"username": {"$regex": ".*"}}` | 注入被拒绝 |
| SEC-034 | MongoDB $ne | `{"password": {"$ne": ""}}` | 注入被拒绝 |
| SEC-035 | Redis命令注入 | `FLUSHALL` | 注入被拒绝 |
| SEC-036 | Elasticsearch查询 | `{"query": {"match_all": {}}}` | 注入被拒绝 |
| SEC-037 | JSON注入 | `{"key": "value", "admin": true}` | 注入被拒绝 |
| SEC-038 | LDAP注入 | `*)(uid=*))(|(uid=*` | 注入被拒绝 |
| SEC-039 | XPath注入 | `' or '1'='1` | 注入被拒绝 |
| SEC-040 | XML注入 | `<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>` | 注入被拒绝 |

### 4. 命令注入测试

| 测试ID | 测试场景 | 测试Payload | 预期结果 |
|--------|---------|-------------|----------|
| SEC-041 | Shell分号注入 | `; ls -la` | 注入被拒绝 |
| SEC-042 | Shell管道注入 | `| cat /etc/passwd` | 注入被拒绝 |
| SEC-043 | Shell与号注入 | `& whoami` | 注入被拒绝 |
| SEC-044 | Shell $()注入 | `$(whoami)` | 注入被拒绝 |
| SEC-045 | Shell反引号注入 | `` `whoami` `` | 注入被拒绝 |
| SEC-046 | 路径遍历../ | `../../../etc/passwd` | 注入被拒绝 |
| SEC-047 | 路径遍历..\ | `..\..\..\windows\system32` | 注入被拒绝 |
| SEC-048 | 空字节注入 | `file.txt%00.jpg` | 注入被拒绝 |
| SEC-049 | CRLF注入 | `header\r\nX-Injected: true` | 注入被拒绝 |
| SEC-050 | HTTP头注入 | `value\r\nSet-Cookie: admin=true` | 注入被拒绝 |

### 5. 特殊字符注入测试

| 测试ID | 测试场景 | 测试字符 | 预期结果 |
|--------|---------|----------|----------|
| SEC-051 | 反斜杠 | `\` | 正确转义或过滤 |
| SEC-052 | 正斜杠 | `/` | 正确处理 |
| SEC-053 | 尖括号 | `<>` | 正确转义 |
| SEC-054 | 花括号 | `{}` | 正确处理 |
| SEC-055 | 方括号 | `[]` | 正确处理 |
| SEC-056 | 百分号 | `%` | 正确处理 |
| SEC-057 | 井号 | `#` | 正确处理 |
| SEC-058 | 美元符号 | `$` | 正确处理 |
| SEC-059 | 管道符 | `|` | 正确处理 |
| SEC-060 | 波浪号 | `~` | 正确处理 |

## 认证授权边界测试设计

### 1. Token边界测试

| 测试ID | 测试场景 | 测试值 | 预期结果 |
|--------|---------|--------|----------|
| AUTH-001 | 空Token | Authorization: Bearer | 返回401 |
| AUTH-002 | 格式错误Token | Authorization: Bearer invalid | 返回401 |
| AUTH-003 | 过期Token | expired JWT | 返回401 |
| AUTH-004 | 被篡改Token | modified payload | 返回401 |
| AUTH-005 | 签名错误Token | wrong signature | 返回401 |
| AUTH-006 | 缺少声明Token | missing claims | 返回401 |
| AUTH-007 | 超长Token | 10000+ chars | 返回400或401 |
| AUTH-008 | 特殊字符Token | special chars | 返回401 |
| AUTH-009 | 已注销用户Token | logged out user | 返回401 |
| AUTH-010 | 黑名单Token | blacklisted | 返回401 |

### 2. 权限边界测试

| 测试ID | 测试场景 | 测试操作 | 预期结果 |
|--------|---------|----------|----------|
| AUTH-011 | 普通用户访问管理接口 | GET /admin/* | 返回403 |
| AUTH-012 | 访问他人私有资源 | GET /users/{otherId}/private | 返回403 |
| AUTH-013 | 修改他人资源 | PUT /posts/{otherId} | 返回403 |
| AUTH-014 | 删除他人资源 | DELETE /posts/{otherId} | 返回403 |
| AUTH-015 | 越权提升权限 | PUT /users/{id}/role | 返回403 |
| AUTH-016 | 访问已删除用户资源 | GET /users/{deleted}/posts | 返回404或403 |
| AUTH-017 | 访问被禁用用户资源 | GET /users/{disabled}/posts | 返回403 |
| AUTH-018 | 批量操作含无权限资源 | DELETE /posts?ids=[own,other] | 返回403或部分成功 |
| AUTH-019 | ID猜测访问资源 | GET /posts/{guessedId} | 返回404或403 |
| AUTH-020 | URL参数绕过权限 | GET /posts?userId=other | 返回403 |

### 3. 会话边界测试

| 测试ID | 测试场景 | 测试操作 | 预期结果 |
|--------|---------|----------|----------|
| AUTH-021 | 并发登录同一账号 | 多设备登录 | 正确处理多设备 |
| AUTH-022 | 登出后使用旧Token | POST /logout then use token | 返回401 |
| AUTH-023 | 密码修改后使用旧Token | change password then use old token | 返回401 |
| AUTH-024 | RefreshToken过期 | use expired refresh token | 返回401 |
| AUTH-025 | RefreshToken被撤销 | use revoked refresh token | 返回401 |

## 并发与幂等性测试设计

### 1. 幂等性测试

| 测试ID | 测试场景 | 测试操作 | 预期结果 |
|--------|---------|----------|----------|
| IDEM-001 | 重复点赞 | POST /posts/{id}/like x2 | 只记录一次 |
| IDEM-002 | 重复收藏 | POST /posts/{id}/favorite x2 | 只记录一次 |
| IDEM-003 | 重复关注 | POST /users/{id}/follow x2 | 只记录一次 |
| IDEM-004 | 重复签到 | POST /check-in x2 | 只记录一次 |
| IDEM-005 | 重复标记通知已读 | POST /notifications/{id}/read x2 | 幂等处理 |
| IDEM-006 | 重复标记消息已读 | POST /messages/{id}/read x2 | 幂等处理 |
| IDEM-007 | 重复取消点赞 | DELETE /posts/{id}/like x2 | 幂等处理 |
| IDEM-008 | 重复取消收藏 | DELETE /posts/{id}/favorite x2 | 幂等处理 |
| IDEM-009 | 重复取消关注 | DELETE /users/{id}/follow x2 | 幂等处理 |
| IDEM-010 | 重复删除资源 | DELETE /posts/{id} x2 | 幂等处理 |

### 2. 并发测试

| 测试ID | 测试场景 | 并发数 | 预期结果 |
|--------|---------|--------|----------|
| CONC-001 | 并发点赞同一文章 | 10 | 点赞数正确 |
| CONC-002 | 并发评论同一文章 | 10 | 评论数正确 |
| CONC-003 | 并发关注同一用户 | 10 | 粉丝数正确 |
| CONC-004 | 并发发送消息 | 10 | 消息顺序正确 |
| CONC-005 | 并发更新同一资源 | 10 | 最终状态一致 |
| CONC-006 | 并发删除同一资源 | 10 | 只删除一次 |
| CONC-007 | 并发创建相同唯一键 | 10 | 只创建一个 |
| CONC-008 | 并发刷新Token | 10 | Token正确生成 |
| CONC-009 | 并发注册相同邮箱 | 10 | 只注册一个 |
| CONC-010 | 并发签到 | 10 | 只签到一次 |
