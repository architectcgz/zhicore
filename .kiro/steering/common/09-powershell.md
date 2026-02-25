---
inclusion: fileMatch
fileMatchPattern: '**/*.ps1'
---

# PowerShell 脚本规范

[返回索引](./README-zh.md)

---

## 脚本创建规范

**在创建任何脚本之前，必须先征得用户同意！**

### 创建脚本的正确流程

1. **说明脚本用途**：清楚地告诉用户脚本是做什么的
2. **等待用户确认**：等待用户明确同意后再创建
3. **使用 UTF-8 编码**：所有脚本必须使用 UTF-8 无 BOM 编码

### 示例对话

```
✅ 正确做法：
AI: "我可以创建一个测试脚本来验证所有 Swagger 端点是否正常工作。
     脚本会测试 5 个端点并显示测试结果。是否需要我创建这个脚本？"
用户: "好的，创建吧"
AI: [创建脚本]

❌ 错误做法：
AI: [直接创建脚本，不询问用户]
```

### 脚本编码要求

**所有 PowerShell 脚本必须使用 UTF-8 无 BOM 编码**

- 使用 `[System.IO.File]::WriteAllText()` 方法
- 指定 `[System.Text.UTF8Encoding]::new($false)` 参数
- 避免使用 `Out-File -Encoding UTF8`（会产生 BOM）

---

## 执行环境

- **操作系统**：Windows
- **Shell**：PowerShell（不是 cmd.exe）
- **编码**：UTF-8 无 BOM
- **用户语言**：中文

---

## HTTP 请求工具

| 必须使用 | 禁止使用 |
|----------|----------|
| `Invoke-WebRequest` | `curl` |
| `Invoke-RestMethod` | `wget` |
| `Invoke-ApiRequest` 函数 | 其他 HTTP 客户端 |

---

## 标准 API 请求函数

```powershell
function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{ 
            Method = $Method
            Uri = $Url
            ContentType = "application/json"
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) { 
            $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) 
        }
        $Response = Invoke-WebRequest @RequestParams
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $Result.Body = $StreamReader.ReadToEnd() | ConvertFrom-Json
                $StreamReader.Close()
            } catch { 
                $Result.Error = $_.Exception.Message 
            }
        } else { 
            $Result.Error = $_.Exception.Message 
        }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}
```

---

## 字符编码注意事项

- URL 参数使用 `[System.Uri]::EscapeDataString()` 编码
- JSON 请求体使用 `ConvertTo-Json -Depth 10`
- 响应内容使用 `ConvertFrom-Json` 解析

---

## 文件替换编码规范

**所有脚本文件必须使用 UTF-8 编码**

### 正确的文件替换方式

```powershell
# ✅ 正确 - 使用 Get-Content -Raw -Encoding utf8
$content = Get-Content -Path "file.txt" -Raw -Encoding utf8
$newContent = $content -replace "old", "new"
[System.IO.File]::WriteAllText("file.txt", $newContent, [System.Text.UTF8Encoding]::new($false))

# ✅ 正确 - 使用 .NET API 确保 UTF-8 无 BOM
$content = [System.IO.File]::ReadAllText("file.txt", [System.Text.UTF8Encoding]::new($false))
$newContent = $content -replace "old", "new"
[System.IO.File]::WriteAllText("file.txt", $newContent, [System.Text.UTF8Encoding]::new($false))
```

### 禁止的文件替换方式

```powershell
# ❌ 错误 - 使用 Out-File（默认编码可能不是 UTF-8）
$content -replace "old", "new" | Out-File "file.txt"

# ❌ 错误 - 使用 Set-Content 不带 -Encoding
$content -replace "old", "new" | Set-Content "file.txt"

# ❌ 错误 - 可能产生 UTF-8 BOM
$content | Out-File "file.txt" -Encoding UTF8
```

### 编码规范说明

| 方法 | 编码 | BOM | 推荐 |
|------|------|-----|------|
| `[System.IO.File]::WriteAllText(..., UTF8Encoding($false))` | UTF-8 | 无 | ✅ 推荐 |
| `Get-Content -Encoding utf8` + `[System.IO.File]::WriteAllText` | UTF-8 | 无 | ✅ 推荐 |
| `Out-File -Encoding UTF8` | UTF-8 | 有 | ❌ 禁止 |
| `Out-File`（无参数） | 系统默认 | - | ❌ 禁止 |
| `Set-Content`（无 -Encoding） | 系统默认 | - | ❌ 禁止 |

### 为什么要避免 BOM

- Java/Spring Boot 配置文件不支持 UTF-8 BOM
- Git 会将 BOM 视为文件内容变更
- 跨平台兼容性问题
- 某些工具无法正确处理 BOM

---

## 网络端口检查

### 端口占用检查

```powershell
# ✅ 正确 - 使用 Get-NetTCPConnection（PowerShell 原生命令）
Get-NetTCPConnection -LocalPort 8101 -ErrorAction SilentlyContinue | Select-Object LocalAddress, LocalPort, State

# ❌ 错误 - 使用 netstat（CMD 命令）
netstat -ano | findstr :8101
```

### 为什么使用 Get-NetTCPConnection

- PowerShell 原生命令，输出结构化对象
- 支持错误处理（`-ErrorAction SilentlyContinue`）
- 可以直接过滤和格式化输出
- 跨平台兼容性更好（PowerShell Core）

---

## 服务启动等待时间规范

**核心规则：服务启动后的等待时间不应超过 15 秒。**

### 等待时间标准

| 场景 | 最大等待时间 | 说明 |
|------|-------------|------|
| 单个微服务启动 | 15 秒 | Spring Boot 应用启动后的健康检查等待 |
| 多个微服务启动 | 15 秒 | 所有服务启动后的统一等待时间 |
| 数据库/中间件启动 | 10 秒 | PostgreSQL、Redis、RocketMQ 等 |
| 健康检查轮询间隔 | 2-3 秒 | 每次健康检查之间的间隔 |

### 正确的等待方式

```powershell
# ✅ 正确 - 使用 15 秒等待
Write-Host "Waiting for services to be healthy..." -ForegroundColor Cyan
Start-Sleep -Seconds 15

# ✅ 正确 - 使用健康检查轮询（推荐）
$MaxRetries = 5
$RetryInterval = 3
for ($i = 1; $i -le $MaxRetries; $i++) {
    try {
        $Response = Invoke-WebRequest -Uri "http://localhost:8101/actuator/health" -TimeoutSec 5
        if ($Response.StatusCode -eq 200) {
            Write-Host "Service is healthy" -ForegroundColor Green
            break
        }
    } catch {
        if ($i -lt $MaxRetries) {
            Write-Host "Waiting for service... (Attempt $i/$MaxRetries)" -ForegroundColor Yellow
            Start-Sleep -Seconds $RetryInterval
        }
    }
}

# ❌ 错误 - 使用 30 秒或更长的等待时间
Start-Sleep -Seconds 30  # 太长了！
```

### 为什么限制为 15 秒

1. **开发效率**：减少开发和测试时的等待时间
2. **快速反馈**：更快地发现启动问题
3. **合理性**：Spring Boot 应用通常在 10-15 秒内完成启动
4. **用户体验**：避免不必要的长时间等待

### 最佳实践

1. **优先使用健康检查轮询**而不是固定等待时间
2. **设置合理的超时时间**（如 5 秒）
3. **提供清晰的进度提示**（如 "Attempt 1/5"）
4. **失败时给出明确的错误信息**

---

## PowerShell vs Bash 命令对照

| Bash | PowerShell |
|------|------------|
| `cat file.txt` | `Get-Content file.txt` |
| `ls -la` | `Get-ChildItem` |
| `rm -rf dir` | `Remove-Item -Recurse -Force dir` |
| `mkdir -p path` | `New-Item -ItemType Directory -Path path -Force` |
| `grep pattern file` | `Select-String -Pattern pattern -Path file` |
| `netstat -ano \| findstr :8101` | `Get-NetTCPConnection -LocalPort 8101 -ErrorAction SilentlyContinue` |

---

**最后更新**：2026-02-01
