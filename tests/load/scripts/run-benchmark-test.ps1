# 缓存击穿防护基准测试脚本
# 使用 JMH 测量锁的性能开销

param(
    [string]$OutputDir = "../results"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Join-Path $ScriptDir "../../.."
$OutputFullPath = Join-Path $ScriptDir $OutputDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Cache Penetration Protection Benchmark" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Redis 是否运行
Write-Host "[INFO] Checking Redis connection..." -ForegroundColor Yellow
try {
    $RedisTest = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
    if (-not $RedisTest.TcpTestSucceeded) {
        Write-Host "[ERROR] Redis is not running on localhost:6379" -ForegroundColor Red
        Write-Host "[INFO] Please start Redis using: cd docker; docker-compose up -d redis" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "[PASS] Redis is running" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to check Redis connection: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[INFO] Starting JMH Benchmark Tests..." -ForegroundColor Yellow
Write-Host "[INFO] This may take several minutes..." -ForegroundColor Yellow
Write-Host ""

# 切换到项目根目录
Push-Location $ProjectRoot

try {
    # 运行基准测试
    $Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $ResultFile = Join-Path $OutputFullPath "benchmark_results_$Timestamp.txt"
    
    Write-Host "[INFO] Running benchmarks in ZhiCore-common module..." -ForegroundColor Yellow
    
    # 使用 Maven 运行基准测试
    $MavenCmd = "mvn -pl ZhiCore-common test-compile exec:java -Dexec.mainClass=`"com.zhicore.common.cache.CacheLockBenchmark`" -Dexec.classpathScope=test"
    
    Write-Host "[CMD] $MavenCmd" -ForegroundColor Cyan
    
    $Output = & cmd /c $MavenCmd 2>&1
    $ExitCode = $LASTEXITCODE
    
    # 保存结果
    if (-not (Test-Path $OutputFullPath)) {
        New-Item -ItemType Directory -Path $OutputFullPath -Force | Out-Null
    }
    
    $Output | Out-File -FilePath $ResultFile -Encoding UTF8
    
    if ($ExitCode -eq 0) {
        Write-Host ""
        Write-Host "[PASS] Benchmark completed successfully" -ForegroundColor Green
        Write-Host "[INFO] Results saved to: $ResultFile" -ForegroundColor Cyan
        
        # 提取关键指标
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Benchmark Results Summary" -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        
        $Output | Select-String "Benchmark" | ForEach-Object {
            Write-Host $_.Line -ForegroundColor White
        }
        
        Write-Host ""
        Write-Host "[INFO] Full results available at: $ResultFile" -ForegroundColor Cyan
    } else {
        Write-Host ""
        Write-Host "[FAIL] Benchmark failed with exit code: $ExitCode" -ForegroundColor Red
        Write-Host "[INFO] Error log saved to: $ResultFile" -ForegroundColor Yellow
        
        # 显示最后几行错误
        Write-Host ""
        Write-Host "Last 20 lines of output:" -ForegroundColor Yellow
        $Output | Select-Object -Last 20 | ForEach-Object {
            Write-Host $_ -ForegroundColor Gray
        }
    }
    
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Benchmark Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

exit $ExitCode
