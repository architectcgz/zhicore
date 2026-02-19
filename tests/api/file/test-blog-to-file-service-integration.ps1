# Blog 系统调用 File Service 集成测试
# 测试 blog-upload 服务能否成功调用外部 file-service

param(
    [string]$BlogUploadUrl = "http://localhost:8089",
    [string]$FileServiceUrl = "http://localhost:8089",
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestResults = @()

# 工具函数
function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId; TestName = $TestName; Status = $Status
        ResponseTime = $ResponseTime; Note = $Note
    }
}

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json"; Headers = $Headers; ErrorAction = "Stop" }
        if ($Body) { $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) }
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
            } catch { $Result.Error = $_.Exception.Message }
        } else { $Result.Error = $_.Exception.Message }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Blog to File Service Integration Test" -ForegroundColor Cyan
Write-Host "Blog Upload URL: $BlogUploadUrl" -ForegroundColor Cyan
Write-Host "File Service URL: $FileServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === SECTION 1: 服务健康检查 ===
Write-Host "=== SECTION 1: Service Health Checks ===" -ForegroundColor Magenta
Write-Host ""

# [INT-001]: 检查 Blog Upload 服务健康状态
Write-Host "[INT-001] Checking Blog Upload Service Health..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$BlogUploadUrl/actuator/health"
if ($Result.Success -and $Result.Body.status -eq "UP") {
    Add-TestResult -TestId "INT-001" -TestName "Blog Upload Health Check" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Service is UP"
    Write-Host "  PASS - Blog Upload service is healthy ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.status) { "Status: $($Result.Body.status)" } else { $Result.Error }
    Add-TestResult -TestId "INT-001" -TestName "Blog Upload Health Check" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# [INT-002]: 检查 File Service 健康状态
Write-Host "[INT-002] Checking File Service Health..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$FileServiceUrl/actuator/health"
if ($Result.Success -and $Result.Body.status -eq "UP") {
    Add-TestResult -TestId "INT-002" -TestName "File Service Health Check" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Service is UP"
    Write-Host "  PASS - File Service is healthy ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.status) { "Status: $($Result.Body.status)" } else { $Result.Error }
    Add-TestResult -TestId "INT-002" -TestName "File Service Health Check" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: 网络连通性测试 ===
Write-Host "=== SECTION 2: Network Connectivity Tests ===" -ForegroundColor Magenta
Write-Host ""

# [INT-003]: 测试 Docker 网络连通性
Write-Host "[INT-003] Testing Docker Network Connectivity..." -ForegroundColor Yellow
Write-Host "  Checking if services are in the same Docker network..." -ForegroundColor Gray

# 检查 blog-network 是否存在
$BlogNetworkExists = docker network ls --filter name=blog-network --format "{{.Name}}" 2>$null
if ($BlogNetworkExists -eq "blog-network") {
    Write-Host "  INFO - blog-network exists" -ForegroundColor Cyan
    
    # 检查 file-service-network 是否存在
    $FileNetworkExists = docker network ls --filter name=file-service-network --format "{{.Name}}" 2>$null
    if ($FileNetworkExists -eq "file-service-network") {
        Write-Host "  INFO - file-service-network exists" -ForegroundColor Cyan
        Write-Host "  WARN - Services are in DIFFERENT networks!" -ForegroundColor Yellow
        Add-TestResult -TestId "INT-003" -TestName "Docker Network Check" -Status "FAIL" -ResponseTime "-" -Note "Services in different networks"
        Write-Host "  FAIL - Blog and File Service are in separate networks" -ForegroundColor Red
    } else {
        Write-Host "  INFO - file-service-network does not exist" -ForegroundColor Cyan
        Add-TestResult -TestId "INT-003" -TestName "Docker Network Check" -Status "SKIP" -ResponseTime "-" -Note "File service network not found"
        Write-Host "  SKIP - File service network not found" -ForegroundColor Gray
    }
} else {
    Write-Host "  INFO - blog-network does not exist" -ForegroundColor Cyan
    Add-TestResult -TestId "INT-003" -TestName "Docker Network Check" -Status "SKIP" -ResponseTime "-" -Note "Blog network not found"
    Write-Host "  SKIP - Blog network not found" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 3: 依赖服务检查 ===
Write-Host "=== SECTION 3: Dependency Service Checks ===" -ForegroundColor Magenta
Write-Host ""

# [INT-004]: 检查 PostgreSQL 连接
Write-Host "[INT-004] Checking PostgreSQL Availability..." -ForegroundColor Yellow
$PostgresContainer = docker ps --filter name=file-service-postgres --format "{{.Names}}" 2>$null
if ($PostgresContainer -eq "file-service-postgres") {
    Write-Host "  INFO - PostgreSQL container is running" -ForegroundColor Cyan
    
    # 检查健康状态
    $PostgresHealth = docker inspect file-service-postgres --format "{{.State.Health.Status}}" 2>$null
    if ($PostgresHealth -eq "healthy") {
        Add-TestResult -TestId "INT-004" -TestName "PostgreSQL Check" -Status "PASS" -ResponseTime "-" -Note "Container healthy"
        Write-Host "  PASS - PostgreSQL is healthy" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "INT-004" -TestName "PostgreSQL Check" -Status "FAIL" -ResponseTime "-" -Note "Container unhealthy: $PostgresHealth"
        Write-Host "  FAIL - PostgreSQL health status: $PostgresHealth" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INT-004" -TestName "PostgreSQL Check" -Status "FAIL" -ResponseTime "-" -Note "Container not running"
    Write-Host "  FAIL - PostgreSQL container not found" -ForegroundColor Red
}

# [INT-005]: 检查 RustFS 连接
Write-Host "[INT-005] Checking RustFS Availability..." -ForegroundColor Yellow
$RustFSContainer = docker ps --filter name=file-service-rustfs --format "{{.Names}}" 2>$null
if ($RustFSContainer -eq "file-service-rustfs") {
    Write-Host "  INFO - RustFS container is running" -ForegroundColor Cyan
    
    # 检查健康状态
    $RustFSHealth = docker inspect file-service-rustfs --format "{{.State.Health.Status}}" 2>$null
    if ($RustFSHealth -eq "healthy") {
        Add-TestResult -TestId "INT-005" -TestName "RustFS Check" -Status "PASS" -ResponseTime "-" -Note "Container healthy"
        Write-Host "  PASS - RustFS is healthy" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "INT-005" -TestName "RustFS Check" -Status "FAIL" -ResponseTime "-" -Note "Container unhealthy: $RustFSHealth"
        Write-Host "  FAIL - RustFS health status: $RustFSHealth" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INT-005" -TestName "RustFS Check" -Status "FAIL" -ResponseTime "-" -Note "Container not running"
    Write-Host "  FAIL - RustFS container not found" -ForegroundColor Red
}

Write-Host ""

# === 测试结果汇总 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host "Total Tests: $($TestResults.Count)" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# 输出详细结果
$TestResults | Format-Table -AutoSize

# === 诊断建议 ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Diagnostic Recommendations" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($FailCount -gt 0) {
    Write-Host "[ISSUE DETECTED] Integration problems found!" -ForegroundColor Red
    Write-Host ""
    
    # 检查网络问题
    $NetworkIssue = $TestResults | Where-Object { $_.TestId -eq "INT-003" -and $_.Status -eq "FAIL" }
    if ($NetworkIssue) {
        Write-Host "[SOLUTION 1] Network Isolation Issue" -ForegroundColor Yellow
        Write-Host "  Problem: Blog services and File Service are in different Docker networks" -ForegroundColor White
        Write-Host "  Solution: Connect file-service containers to blog-network" -ForegroundColor White
        Write-Host ""
        Write-Host "  Run these commands:" -ForegroundColor Cyan
        Write-Host "    docker network connect blog-network file-service-postgres" -ForegroundColor Gray
        Write-Host "    docker network connect blog-network file-service-rustfs" -ForegroundColor Gray
        Write-Host "    docker network connect blog-network file-service-app" -ForegroundColor Gray
        Write-Host ""
    }
    
    # 检查依赖服务问题
    $PostgresIssue = $TestResults | Where-Object { $_.TestId -eq "INT-004" -and $_.Status -eq "FAIL" }
    if ($PostgresIssue) {
        Write-Host "[SOLUTION 2] PostgreSQL Not Available" -ForegroundColor Yellow
        Write-Host "  Problem: PostgreSQL container is not running or unhealthy" -ForegroundColor White
        Write-Host "  Solution: Start file-service infrastructure" -ForegroundColor White
        Write-Host ""
        Write-Host "  Run these commands:" -ForegroundColor Cyan
        Write-Host "    cd file-service/docker" -ForegroundColor Gray
        Write-Host "    docker-compose up -d postgres" -ForegroundColor Gray
        Write-Host ""
    }
    
    $RustFSIssue = $TestResults | Where-Object { $_.TestId -eq "INT-005" -and $_.Status -eq "FAIL" }
    if ($RustFSIssue) {
        Write-Host "[SOLUTION 3] RustFS Not Available" -ForegroundColor Yellow
        Write-Host "  Problem: RustFS container is not running or unhealthy" -ForegroundColor White
        Write-Host "  Solution: Start file-service infrastructure" -ForegroundColor White
        Write-Host ""
        Write-Host "  Run these commands:" -ForegroundColor Cyan
        Write-Host "    cd file-service/docker" -ForegroundColor Gray
        Write-Host "    docker-compose up -d rustfs" -ForegroundColor Gray
        Write-Host ""
    }
    
    Write-Host "[RECOMMENDED] Complete Setup Steps:" -ForegroundColor Yellow
    Write-Host "  1. Start file-service infrastructure:" -ForegroundColor White
    Write-Host "     cd file-service/docker" -ForegroundColor Gray
    Write-Host "     docker-compose up -d" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  2. Connect to blog network:" -ForegroundColor White
    Write-Host "     docker network connect blog-network file-service-postgres" -ForegroundColor Gray
    Write-Host "     docker network connect blog-network file-service-rustfs" -ForegroundColor Gray
    Write-Host "     docker network connect blog-network file-service-app" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  3. Verify connectivity:" -ForegroundColor White
    Write-Host "     .\test-blog-to-file-service-integration.ps1" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "[SUCCESS] All integration checks passed!" -ForegroundColor Green
    Write-Host "Blog system can successfully communicate with File Service" -ForegroundColor White
    Write-Host ""
}

# 退出码
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
