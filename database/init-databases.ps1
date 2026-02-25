# 数据库初始化脚本
# 自动为所有服务创建数据库表结构

param(
    [string]$Host = "localhost",
    [int]$Port = 5432,
    [string]$User = "postgres",
    [string]$Password = "postgres123456",
    [string]$SqlFile = "init-all-databases.sql"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFilePath = Join-Path $ScriptDir $SqlFile

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Database Initialization" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 SQL 文件是否存在
if (-not (Test-Path $SqlFilePath)) {
    Write-Host "[ERROR] SQL file not found: $SqlFilePath" -ForegroundColor Red
    exit 1
}

# 数据库列表
$Databases = @(
    @{ Name = "ZhiCore_user"; Description = "User Service" },
    @{ Name = "ZhiCore_post"; Description = "Post Service" },
    @{ Name = "ZhiCore_comment"; Description = "Comment Service" },
    @{ Name = "ZhiCore_message"; Description = "Message Service" },
    @{ Name = "ZhiCore_notification"; Description = "Notification Service" }
)

# 设置环境变量以避免密码提示
$env:PGPASSWORD = $Password

$SuccessCount = 0
$FailCount = 0

foreach ($Db in $Databases) {
    $DbName = $Db.Name
    $DbDesc = $Db.Description
    
    Write-Host "[INFO] Initializing $DbName ($DbDesc)..." -ForegroundColor Yellow
    
    try {
        # 执行 SQL 脚本
        $Output = & psql -h $Host -p $Port -U $User -d $DbName -f $SqlFilePath 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  [SUCCESS] $DbName initialized successfully" -ForegroundColor Green
            $SuccessCount++
        } else {
            Write-Host "  [FAIL] Failed to initialize $DbName" -ForegroundColor Red
            Write-Host "  Error: $Output" -ForegroundColor Red
            $FailCount++
        }
    }
    catch {
        Write-Host "  [FAIL] Exception occurred: $_" -ForegroundColor Red
        $FailCount++
    }
    
    Write-Host ""
}

# 清除密码环境变量
Remove-Item Env:\PGPASSWORD

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Initialization Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total Databases: $($Databases.Count)" -ForegroundColor White
Write-Host "Success: $SuccessCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor $(if ($FailCount -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($FailCount -eq 0) {
    Write-Host "[SUCCESS] All databases initialized successfully!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[WARNING] Some databases failed to initialize" -ForegroundColor Yellow
    exit 1
}
