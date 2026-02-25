# 缓存击穿防护压力测试脚本
# 模拟高并发场景（1000+ QPS）验证系统稳定性

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat",
    [string]$TestPlan = "../jmeter/cache-penetration-stress-test.jmx",
    [string]$ResultsDir = "../results/cache-stress",
    [string]$BaseUrl = "http://localhost:8000",
    [string]$PostServiceUrl = "http://localhost:8102",
    [string]$UserServiceUrl = "http://localhost:8101",
    [string]$CommentServiceUrl = "http://localhost:8103",
    [string]$TestPostId = "1",
    [string]$TestUserId = "1",
    [string]$TestCommentId = "1",
    [string]$AccessToken = "",
    [int]$Threads = 1000,
    [int]$RampTime = 60,
    [int]$Duration = 300,
    [switch]$GuiMode = $false
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlanFullPath = Join-Path $ScriptDir $TestPlan
$ResultsDirFullPath = Join-Path $ScriptDir $ResultsDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Cache Penetration Protection Stress Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 JMeter 是否存在
if (-not (Test-Path $JMeterPath)) {
    Write-Host "[ERROR] JMeter not found at: $JMeterPath" -ForegroundColor Red
    Write-Host "[INFO] Please install JMeter or specify correct path with -JMeterPath parameter" -ForegroundColor Yellow
    exit 1
}

# 检查测试计划文件
if (-not (Test-Path $TestPlanFullPath)) {
    Write-Host "[ERROR] Test plan not found at: $TestPlanFullPath" -ForegroundColor Red
    exit 1
}

# 检查服务是否运行
Write-Host "[INFO] Checking services..." -ForegroundColor Yellow

$Services = @(
    @{ Name = "Post Service"; Url = $PostServiceUrl; Port = 8102 },
    @{ Name = "User Service"; Url = $UserServiceUrl; Port = 8101 },
    @{ Name = "Comment Service"; Url = $CommentServiceUrl; Port = 8103 }
)

$AllServicesRunning = $true
foreach ($Service in $Services) {
    try {
        $TestConnection = Test-NetConnection -ComputerName localhost -Port $Service.Port -WarningAction SilentlyContinue
        if ($TestConnection.TcpTestSucceeded) {
            Write-Host "[PASS] $($Service.Name) is running on port $($Service.Port)" -ForegroundColor Green
        } else {
            Write-Host "[FAIL] $($Service.Name) is not running on port $($Service.Port)" -ForegroundColor Red
            $AllServicesRunning = $false
        }
    } catch {
        Write-Host "[ERROR] Failed to check $($Service.Name): $_" -ForegroundColor Red
        $AllServicesRunning = $false
    }
}

if (-not $AllServicesRunning) {
    Write-Host ""
    Write-Host "[ERROR] Not all services are running. Please start services first." -ForegroundColor Red
    Write-Host "[INFO] Use: cd docker; docker-compose -f docker-compose.services.yml up -d" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "[INFO] Test Configuration:" -ForegroundColor Cyan
Write-Host "  Threads: $Threads" -ForegroundColor White
Write-Host "  Ramp-up Time: $RampTime seconds" -ForegroundColor White
Write-Host "  Duration: $Duration seconds" -ForegroundColor White
Write-Host "  Test Post ID: $TestPostId" -ForegroundColor White
Write-Host "  Test User ID: $TestUserId" -ForegroundColor White
Write-Host "  Test Comment ID: $TestCommentId" -ForegroundColor White
Write-Host ""

# 创建结果目录
if (-not (Test-Path $ResultsDirFullPath)) {
    New-Item -ItemType Directory -Path $ResultsDirFullPath -Force | Out-Null
    Write-Host "[INFO] Created results directory: $ResultsDirFullPath" -ForegroundColor Green
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ResultFile = Join-Path $ResultsDirFullPath "results_$Timestamp.jtl"
$ReportDir = Join-Path $ResultsDirFullPath "report_$Timestamp"

if ($GuiMode) {
    Write-Host "[INFO] Starting JMeter in GUI mode..." -ForegroundColor Yellow
    Write-Host "[INFO] Test plan: $TestPlanFullPath" -ForegroundColor Cyan
    Write-Host ""
    
    & $JMeterPath -t $TestPlanFullPath
    
} else {
    Write-Host "[INFO] Starting JMeter stress test..." -ForegroundColor Yellow
    Write-Host "[INFO] This will take approximately $([math]::Ceiling(($RampTime + $Duration) / 60)) minutes..." -ForegroundColor Yellow
    Write-Host ""
    
    $JMeterArgs = @(
        "-n",
        "-t", $TestPlanFullPath,
        "-l", $ResultFile,
        "-e",
        "-o", $ReportDir,
        "-JBASE_URL=$BaseUrl",
        "-JPOST_SERVICE_URL=$PostServiceUrl",
        "-JUSER_SERVICE_URL=$UserServiceUrl",
        "-JCOMMENT_SERVICE_URL=$CommentServiceUrl",
        "-JTEST_POST_ID=$TestPostId",
        "-JTEST_USER_ID=$TestUserId",
        "-JTEST_COMMENT_ID=$TestCommentId",
        "-JACCESS_TOKEN=$AccessToken",
        "-JTHREADS=$Threads",
        "-JRAMP_TIME=$RampTime",
        "-JDURATION=$Duration"
    )
    
    Write-Host "[CMD] $JMeterPath $($JMeterArgs -join ' ')" -ForegroundColor Cyan
    Write-Host ""
    
    $StartTime = Get-Date
    
    try {
        & $JMeterPath $JMeterArgs
        $ExitCode = $LASTEXITCODE
        
        $EndTime = Get-Date
        $ElapsedTime = $EndTime - $StartTime
        
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Test Results" -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        
        if ($ExitCode -eq 0) {
            Write-Host "[PASS] Stress test completed successfully" -ForegroundColor Green
            Write-Host "[INFO] Elapsed time: $($ElapsedTime.ToString('hh\:mm\:ss'))" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "[INFO] Results saved to:" -ForegroundColor Cyan
            Write-Host "  JTL File: $ResultFile" -ForegroundColor White
            Write-Host "  HTML Report: $ReportDir\index.html" -ForegroundColor White
            Write-Host ""
            
            # 尝试解析结果文件获取关键指标
            if (Test-Path $ResultFile) {
                Write-Host "[INFO] Analyzing results..." -ForegroundColor Yellow
                
                $Results = Import-Csv $ResultFile
                $TotalRequests = $Results.Count
                $SuccessRequests = ($Results | Where-Object { $_.success -eq "true" }).Count
                $FailedRequests = $TotalRequests - $SuccessRequests
                $ErrorRate = [math]::Round(($FailedRequests / $TotalRequests) * 100, 2)
                
                $ResponseTimes = $Results | ForEach-Object { [int]$_.elapsed }
                $AvgResponseTime = [math]::Round(($ResponseTimes | Measure-Object -Average).Average, 2)
                $MaxResponseTime = ($ResponseTimes | Measure-Object -Maximum).Maximum
                $MinResponseTime = ($ResponseTimes | Measure-Object -Minimum).Minimum
                
                # 计算 P99
                $SortedTimes = $ResponseTimes | Sort-Object
                $P99Index = [math]::Floor($TotalRequests * 0.99)
                $P99ResponseTime = $SortedTimes[$P99Index]
                
                # 计算 QPS
                $TotalDurationSeconds = $Duration + $RampTime
                $QPS = [math]::Round($TotalRequests / $TotalDurationSeconds, 2)
                
                Write-Host ""
                Write-Host "Performance Metrics:" -ForegroundColor Cyan
                Write-Host "  Total Requests: $TotalRequests" -ForegroundColor White
                Write-Host "  Success: $SuccessRequests" -ForegroundColor Green
                Write-Host "  Failed: $FailedRequests" -ForegroundColor $(if ($FailedRequests -gt 0) { "Red" } else { "Green" })
                Write-Host "  Error Rate: $ErrorRate%" -ForegroundColor $(if ($ErrorRate -gt 1) { "Red" } else { "Green" })
                Write-Host ""
                Write-Host "Response Time (ms):" -ForegroundColor Cyan
                Write-Host "  Average: $AvgResponseTime ms" -ForegroundColor White
                Write-Host "  Min: $MinResponseTime ms" -ForegroundColor White
                Write-Host "  Max: $MaxResponseTime ms" -ForegroundColor White
                Write-Host "  P99: $P99ResponseTime ms" -ForegroundColor $(if ($P99ResponseTime -gt 200) { "Yellow" } else { "Green" })
                Write-Host ""
                Write-Host "Throughput:" -ForegroundColor Cyan
                Write-Host "  QPS: $QPS requests/sec" -ForegroundColor $(if ($QPS -lt 1000) { "Yellow" } else { "Green" })
                Write-Host ""
                
                # 性能目标检查
                Write-Host "Performance Goals Check:" -ForegroundColor Cyan
                $GoalsMet = $true
                
                if ($ErrorRate -gt 1) {
                    Write-Host "  [FAIL] Error rate ($ErrorRate%) exceeds 1%" -ForegroundColor Red
                    $GoalsMet = $false
                } else {
                    Write-Host "  [PASS] Error rate ($ErrorRate%) is within 1%" -ForegroundColor Green
                }
                
                if ($P99ResponseTime -gt 200) {
                    Write-Host "  [WARN] P99 response time ($P99ResponseTime ms) exceeds 200ms" -ForegroundColor Yellow
                } else {
                    Write-Host "  [PASS] P99 response time ($P99ResponseTime ms) is within 200ms" -ForegroundColor Green
                }
                
                if ($QPS -lt 1000) {
                    Write-Host "  [WARN] QPS ($QPS) is below 1000" -ForegroundColor Yellow
                } else {
                    Write-Host "  [PASS] QPS ($QPS) meets target (>1000)" -ForegroundColor Green
                }
                
                Write-Host ""
                if ($GoalsMet) {
                    Write-Host "[PASS] All performance goals met!" -ForegroundColor Green
                } else {
                    Write-Host "[WARN] Some performance goals not met. Check detailed report." -ForegroundColor Yellow
                }
            }
            
            Write-Host ""
            Write-Host "[INFO] Open HTML report in browser:" -ForegroundColor Cyan
            Write-Host "  Start-Process '$ReportDir\index.html'" -ForegroundColor White
            
        } else {
            Write-Host "[FAIL] Stress test failed with exit code: $ExitCode" -ForegroundColor Red
            Write-Host "[INFO] Check JMeter logs for details" -ForegroundColor Yellow
        }
        
    } catch {
        Write-Host "[ERROR] Failed to run JMeter: $_" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stress Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

exit $ExitCode
