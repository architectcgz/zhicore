# PowerShell 模块

本目录包含测试数据生成脚本使用的 PowerShell 模块。

## 模块列表

### IdGenerator.psm1

ID 生成器模块，提供与 blog-id-generator 服务交互的功能。

**功能**:
- `Get-NextId`: 获取单个 ID
- `Get-BatchIds`: 批量获取 ID
- `Test-IdGeneratorService`: 测试服务可用性

**使用示例**:
```powershell
Import-Module .\IdGenerator.psm1

# 获取单个 ID
$id = Get-NextId -IdGeneratorUrl "http://localhost:8088"

# 批量获取 ID
$ids = Get-BatchIds -IdGeneratorUrl "http://localhost:8088" -Count 30

# 测试服务
$status = Test-IdGeneratorService -IdGeneratorUrl "http://localhost:8088"
```

### ApiHelper.psm1

API 辅助模块，提供 API 调用和进度显示功能。

**功能**:
- `Invoke-ApiWithRetry`: 带重试机制的 API 调用
- `Show-Progress`: 显示进度信息
- `Write-ColorOutput`: 颜色输出
- `Test-ApiService`: 测试 API 服务可用性

**使用示例**:
```powershell
Import-Module .\ApiHelper.psm1

# 调用 API
$result = Invoke-ApiWithRetry -Uri "http://localhost:8000/api/tags" `
    -Method Post `
    -Headers @{"Content-Type"="application/json"} `
    -Body @{name="test"}

# 显示进度
Show-Progress -Current 10 -Total 30 -Message "处理中"

# 颜色输出
Write-ColorOutput "成功" "Green"

# 测试服务
$status = Test-ApiService -BaseUrl "http://localhost:8000"
```

## 导入模块

在脚本中导入模块：

```powershell
$modulePath = Join-Path $PSScriptRoot "modules"
Import-Module (Join-Path $modulePath "IdGenerator.psm1") -Force
Import-Module (Join-Path $modulePath "ApiHelper.psm1") -Force
```

## 开发规范

1. **错误处理**: 所有函数都应包含适当的错误处理和重试机制
2. **参数验证**: 使用 `[Parameter(Mandatory = $true)]` 标记必需参数
3. **文档注释**: 使用 PowerShell 注释帮助格式（`.SYNOPSIS`, `.DESCRIPTION`, `.EXAMPLE`）
4. **导出函数**: 使用 `Export-ModuleMember` 明确导出公共函数
5. **命名规范**: 使用 PowerShell 动词-名词命名约定（如 `Get-NextId`, `Test-Service`）

## 测试

可以使用 Pester 框架测试模块功能：

```powershell
# 安装 Pester（如果尚未安装）
Install-Module -Name Pester -Force -SkipPublisherCheck

# 运行测试
Invoke-Pester -Path .\tests\
```

## 相关文档

- [PowerShell 脚本规范](../../../../../.kiro/steering/09-powershell.md)
- [测试数据生成设计文档](../../../.kiro/specs/blog-test-data-generation/design.md)
