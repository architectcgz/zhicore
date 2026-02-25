---
inclusion: fileMatch
fileMatchPattern: '**/*{test,Test}*.ps1'
---

# API 测试规范

[返回索引](./README-zh.md)

---

## 测试脚本结构

```
tests/
├── api/
│   ├── user/
│   │   └── test-user-api-full.ps1
│   ├── post/
│   │   └── test-post-api-full.ps1
│   └── ...
├── config/
│   └── test-env.json
└── results/
    └── test-status.md
```

---

## 测试 ID 命名规则

| 服务 | 前缀 | 示例 |
|------|------|------|
| 用户服务 | USER | USER-001, USER-002 |
| 文章服务 | POST | POST-001, POST-002 |
| 评论服务 | COMMENT | COMMENT-001, COMMENT-002 |
| 消息服务 | MSG | MSG-001, MSG-002 |
| 通知服务 | NOTIF | NOTIF-001, NOTIF-002 |

---

## 测试分类

每个服务必须覆盖：

1. **正常功能测试**（Happy Path）
2. **输入验证测试**（Input Validation）
3. **错误处理测试**（Error Handling）
4. **边界测试**（Boundary Tests）
5. **安全测试**（Security Tests）
6. **幂等性测试**（Idempotency）

---

## 测试用例模板

```powershell
# [TEST-ID]: [测试名称]
Write-Host "[TEST-ID] Testing [测试描述]..." -ForegroundColor Yellow
if ([前置条件检查]) {
    $Body = @{ ... }
    $Result = Invoke-ApiRequest -Method "[METHOD]" -Url "[URL]" -Body $Body -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "[TEST-ID]" -TestName "[测试名称]" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "[成功备注]"
        Write-Host "  PASS - [成功描述] ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "[TEST-ID]" -TestName "[测试名称]" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
```

---

## API 响应格式

### 标准成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 标准错误响应
```json
{
  "code": [错误码],
  "message": "[错误信息]",
  "data": null
}
```

---

**最后更新**：2026-02-01
