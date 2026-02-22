# =====================================================
# Execute User Generation SQL Script
# 执行用户数据生成 SQL 脚本
# 
# 说明：此脚本用于执行用户数据生成 SQL 脚本
# 功能：
# 1. 从 ID Generator 服务获取 58 个用户 ID
# 2. 替换 SQL 脚本中的 ID 占位符
# 3. 执行 SQL 脚本生成用户数据
# =====================================================

param(
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [string]$PostgresHost = "localhost",
    [int]$PostgresPort = 5432,
    [string]$PostgresDatabase = "ZhiCore_user",
    [string]$PostgresUser = "postgres",
    [string]$PostgresPassword = "postgres123456",
    [switch]$DryRun
)

# 注意：IdGeneratorUrl 指向 ZhiCore-id-generator 服务（端口 8088）
# 该服务是 id-generator-server 的代理层，提供统一的 ID 生成接口

# 设置错误处理
$ErrorActionPreference = "Stop"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

# 步骤 1: 验证 ID Generator 服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证 ID Generator 服务 ===" "Cyan"
try {
    # 尝试生成一个测试 ID 来验证服务可用性
    $testResponse = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake" -Method Get -TimeoutSec 5
    if ($testResponse.code -eq 200) {
        Write-ColorOutput "✓ ZhiCore-id-generator 服务正常运行" "Green"
        Write-ColorOutput "  测试 ID: $($testResponse.data)" "Gray"
    }
    else {
        throw "服务返回错误: $($testResponse.message)"
    }
}
catch {
    Write-ColorOutput "✗ 无法连接到 ZhiCore-id-generator 服务: $IdGeneratorUrl" "Red"
    Write-ColorOutput "  错误: $_" "Red"
    Write-ColorOutput "  请确保服务已启动（端口 8088）" "Yellow"
    exit 1
}

# 步骤 2: 获取 58 个用户 ID
Write-ColorOutput "`n=== 步骤 2: 获取用户 ID ===" "Cyan"
try {
    # 使用 ZhiCore-id-generator 服务的批量生成 Snowflake ID 接口
    $response = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake/batch?count=58" `
        -Method Get `
        -TimeoutSec 10

    if ($response.code -ne 200) {
        throw "ID 生成失败: $($response.message)"
    }

    $ids = $response.data
    
    if ($ids.Count -ne 58) {
        throw "ID 数量不正确: 期望 58，实际 $($ids.Count)"
    }

    Write-ColorOutput "✓ 成功获取 $($ids.Count) 个用户 ID" "Green"
    Write-ColorOutput "  ID 范围: $($ids[0]) - $($ids[-1])" "Gray"
}
catch {
    Write-ColorOutput "✗ 获取用户 ID 失败: $_" "Red"
    exit 1
}

# 步骤 3: 读取 SQL 模板
Write-ColorOutput "`n=== 步骤 3: 读取 SQL 模板 ===" "Cyan"
$sqlTemplatePath = Join-Path $PSScriptRoot "..\sql\generate-users.sql"
if (-not (Test-Path $sqlTemplatePath)) {
    Write-ColorOutput "✗ SQL 模板文件不存在: $sqlTemplatePath" "Red"
    exit 1
}

$sqlContent = Get-Content $sqlTemplatePath -Raw
Write-ColorOutput "✓ SQL 模板读取成功" "Green"
Write-ColorOutput "  文件大小: $([math]::Round($sqlContent.Length / 1024, 2)) KB" "Gray"

# 步骤 4: 替换 ID 占位符
Write-ColorOutput "`n=== 步骤 4: 替换 ID 占位符 ===" "Cyan"
for ($i = 0; $i -lt $ids.Count; $i++) {
    $placeholder = "{ID_$($i + 1)}"
    $sqlContent = $sqlContent -replace [regex]::Escape($placeholder), $ids[$i]
}

# 验证是否还有未替换的占位符
$remainingPlaceholders = [regex]::Matches($sqlContent, '\{ID_\d+\}').Count
if ($remainingPlaceholders -gt 0) {
    Write-ColorOutput "✗ 发现 $remainingPlaceholders 个未替换的占位符" "Red"
    exit 1
}

Write-ColorOutput "✓ 所有 ID 占位符替换完成" "Green"

# 步骤 5: 保存临时 SQL 文件
$tempSqlPath = Join-Path $PSScriptRoot "..\sql\generate-users-temp.sql"
$sqlContent | Out-File -FilePath $tempSqlPath -Encoding UTF8
Write-ColorOutput "✓ 临时 SQL 文件已保存: $tempSqlPath" "Green"

if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "SQL 脚本已生成但未执行" "Yellow"
    Write-ColorOutput "临时文件位置: $tempSqlPath" "Yellow"
    Write-ColorOutput "手动执行命令:" "Yellow"
    Write-ColorOutput "  psql -h $PostgresHost -p $PostgresPort -U $PostgresUser -d $PostgresDatabase -f `"$tempSqlPath`"" "Gray"
    exit 0
}

# 步骤 6: 执行 SQL 脚本
Write-ColorOutput "`n=== 步骤 6: 执行 SQL 脚本 ===" "Cyan"

try {
    # 检查 Docker 是否可用
    $dockerVersion = docker --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 命令不可用，请确保 Docker 已安装并正在运行"
    }
    Write-ColorOutput "  使用 Docker: $dockerVersion" "Gray"

    # 检查 PostgreSQL 容器是否运行
    $containerName = "ZhiCore-postgres"
    $containerStatus = docker ps --filter "name=$containerName" --format "{{.Names}}" 2>&1
    if ($containerStatus -ne $containerName) {
        throw "PostgreSQL 容器 '$containerName' 未运行，请先启动容器"
    }
    Write-ColorOutput "  PostgreSQL 容器: $containerName (运行中)" "Gray"

    # 读取 SQL 内容并通过 Docker 执行
    $sqlContent = Get-Content $tempSqlPath -Raw -Encoding UTF8
    
    # 使用 Docker exec 执行 SQL
    Write-ColorOutput "  执行 SQL 脚本..." "Gray"
    $output = $sqlContent | docker exec -i $containerName psql -U $PostgresUser -d $PostgresDatabase 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-ColorOutput "✗ SQL 脚本执行失败" "Red"
        Write-ColorOutput "错误输出:" "Red"
        $output | ForEach-Object { Write-ColorOutput "  $_" "Red" }
        exit 1
    }

    Write-ColorOutput "✓ SQL 脚本执行成功" "Green"
    
    # 显示执行输出
    Write-ColorOutput "`n=== 执行结果 ===" "Cyan"
    $output | ForEach-Object { 
        if ($_ -match "✓") {
            Write-ColorOutput $_ "Green"
        }
        elseif ($_ -match "NOTICE") {
            Write-ColorOutput $_ "Cyan"
        }
        elseif ($_ -match "WARNING") {
            Write-ColorOutput $_ "Yellow"
        }
        else {
            Write-ColorOutput $_ "Gray"
        }
    }
}
catch {
    Write-ColorOutput "✗ 执行过程中发生错误: $_" "Red"
    exit 1
}

# 步骤 7: 清理临时文件
Write-ColorOutput "`n=== 步骤 7: 清理临时文件 ===" "Cyan"
try {
    Remove-Item $tempSqlPath -Force
    Write-ColorOutput "✓ 临时文件已删除" "Green"
}
catch {
    Write-ColorOutput "⚠ 无法删除临时文件: $tempSqlPath" "Yellow"
}

# 步骤 8: 验证结果
Write-ColorOutput "`n=== 步骤 8: 验证结果 ===" "Cyan"
try {
    # 查询用户统计
    $query = @"
SELECT 
    COUNT(*) FILTER (WHERE username LIKE 'test_admin_%') AS admin_count,
    COUNT(*) FILTER (WHERE username LIKE 'test_moderator_%') AS moderator_count,
    COUNT(*) FILTER (WHERE username LIKE 'test_user_%') AS regular_count,
    COUNT(*) AS total_count
FROM users 
WHERE username LIKE 'test_%';
"@

    $result = $query | docker exec -i ZhiCore-postgres psql -U $PostgresUser -d $PostgresDatabase -t 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ 数据验证完成" "Green"
        Write-ColorOutput "  $result" "Gray"
    }
    else {
        Write-ColorOutput "⚠ 无法验证数据" "Yellow"
    }
}
catch {
    Write-ColorOutput "⚠ 验证过程中发生错误: $_" "Yellow"
}

Write-ColorOutput "`n=== 用户数据生成完成 ===" "Green"
Write-ColorOutput "默认密码: Test@123456" "Yellow"
Write-ColorOutput "用户名前缀: test_" "Yellow"

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    执行用户数据生成 SQL 脚本

.DESCRIPTION
    此脚本自动从 ZhiCore-id-generator 服务获取用户 ID，替换 SQL 模板中的占位符，
    并执行 SQL 脚本生成测试用户数据。
    
    ZhiCore-id-generator 服务是 id-generator-server 的代理层，提供统一的分布式 ID 生成接口。

.PARAMETER IdGeneratorUrl
    ZhiCore-id-generator 服务地址，默认为 http://localhost:8088

.PARAMETER PostgresHost
    PostgreSQL 主机地址，默认为 localhost

.PARAMETER PostgresPort
    PostgreSQL 端口，默认为 5432

.PARAMETER PostgresDatabase
    PostgreSQL 数据库名称，默认为 ZhiCore_user

.PARAMETER PostgresUser
    PostgreSQL 用户名，默认为 postgres

.PARAMETER PostgresPassword
    PostgreSQL 密码，默认为 postgres123456

.PARAMETER DryRun
    仅生成 SQL 文件但不执行，用于预览

.EXAMPLE
    .\Execute-UserGeneration.ps1
    使用默认参数执行脚本

.EXAMPLE
    .\Execute-UserGeneration.ps1 -DryRun
    生成 SQL 文件但不执行，用于预览

.EXAMPLE
    .\Execute-UserGeneration.ps1 -IdGeneratorUrl "http://192.168.1.100:8088"
    使用自定义 ZhiCore-id-generator 服务地址

.EXAMPLE
    .\Execute-UserGeneration.ps1 -PostgresPassword "mypassword"
    使用自定义数据库密码

.NOTES
    前置条件：
    1. ZhiCore-id-generator 服务已启动（端口 8088）
    2. PostgreSQL 容器已运行（ZhiCore-postgres）
    3. Docker 已安装并正在运行
    4. 网络连接正常

    生成数据：
    - 3 个管理员用户（test_admin_001 - test_admin_003）
    - 5 个审核员用户（test_moderator_001 - test_moderator_005）
    - 50 个普通用户（test_user_001 - test_user_050）

    默认密码：Test@123456
    
    服务说明：
    ZhiCore-id-generator 是 ZhiCore-microservice 的 ID 生成服务，
    它是 id-generator-server 的轻量级代理层，提供统一的分布式 ID 生成接口。
    
    数据库访问：
    使用 Docker exec 命令通过 ZhiCore-postgres 容器执行 SQL 脚本，
    无需在主机上安装 PostgreSQL 客户端工具。
#>
