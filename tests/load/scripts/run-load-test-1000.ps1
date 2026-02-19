# 1000 Concurrent Users Load Test (5 minutes)

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat",
    [string]$TestPostId = "",
    [string]$TestCommentId = "",
    [string]$UserDataFile = "test-users.csv"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlanPath = Join-Path $ScriptDir "..\jmeter\blog-load-test-1000.jmx"
$ResultsDirPath = Join-Path $ScriptDir "..\results\load"
$UserDataFilePath = Join-Path $ScriptDir $UserDataFile

# Check if user data file exists
if (-not (Test-Path $UserDataFilePath)) {
    Write-Host "ERROR: User data file not found: $UserDataFilePath" -ForegroundColor Red
    Write-Host "Please run prepare-multiple-users.ps1 first to generate test users" -ForegroundColor Yellow
    exit 1
}

# Check user data file age
$FileAge = (Get-Date) - (Get-Item $UserDataFilePath).LastWriteTime
if ($FileAge.TotalHours -gt 1.5) {
    Write-Host "WARNING: User data file is $([math]::Round($FileAge.TotalHours, 1)) hours old" -ForegroundColor Yellow
    Write-Host "Access tokens may have expired (default TTL: 2 hours)" -ForegroundColor Yellow
    Write-Host "Consider regenerating test data with prepare-multiple-users.ps1" -ForegroundColor Yellow
    Write-Host ""
    $Continue = Read-Host "Continue anyway? (y/n)"
    if ($Continue -ne "y") {
        exit 0
    }
}

if (-not (Test-Path $ResultsDirPath)) {
    New-Item -ItemType Directory -Path $ResultsDirPath -Force | Out-Null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1000 Concurrent Users Load Test" -ForegroundColor Cyan
Write-Host "5 Minutes Duration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test Configuration:" -ForegroundColor Cyan
Write-Host "  - Article Detail: 400 concurrent users" -ForegroundColor White
Write-Host "  - Article List: 200 concurrent users" -ForegroundColor White
Write-Host "  - Article Like: 400 concurrent users" -ForegroundColor White
Write-Host "  - Total: 1000 concurrent users" -ForegroundColor White
Write-Host "  - Duration: 5 minutes" -ForegroundColor White
Write-Host "  - Ramp-up: 60 seconds" -ForegroundColor White
Write-Host ""

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ResultFile = Join-Path $ResultsDirPath "load-test-1000-$Timestamp.jtl"
$ReportDir = Join-Path $ResultsDirPath "report-1000-$Timestamp"

Write-Host "Starting 1000-user load test..." -ForegroundColor Yellow
Write-Host "Start Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
Write-Host ""

$JMeterArgs = @(
    "-n", "-t", "`"$TestPlanPath`"", "-l", "`"$ResultFile`"", "-e", "-o", "`"$ReportDir`""
    "-JBASE_URL=http://localhost:8000", "-JTEST_POST_ID=$TestPostId"
    "-JTEST_COMMENT_ID=$TestCommentId", "-JUSER_DATA_FILE=$UserDataFilePath"
)

try {
    & $JMeterPath $JMeterArgs
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "1000-User Load Test Completed" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "End Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
        Write-Host ""
        Write-Host "Results:" -ForegroundColor Cyan
        Write-Host "  - JTL File: $ResultFile" -ForegroundColor White
        Write-Host "  - HTML Report: $ReportDir\index.html" -ForegroundColor White
        Write-Host ""
        Write-Host "Next Steps:" -ForegroundColor Yellow
        Write-Host "  1. Open HTML report in browser" -ForegroundColor White
        Write-Host "  2. Check error rate and response times" -ForegroundColor White
        Write-Host "  3. Analyze throughput and latency" -ForegroundColor White
        Write-Host "  4. Review service logs for errors" -ForegroundColor White
    }
    else {
        Write-Host ""
        Write-Host "ERROR: JMeter test failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host ""
    Write-Host "ERROR: $_" -ForegroundColor Red
    exit 1
}

exit 0
