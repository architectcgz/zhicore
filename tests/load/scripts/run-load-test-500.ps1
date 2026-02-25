# 500 Concurrent Users Load Test (5 minutes)

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat",
    [string]$TestPostId = "",
    [string]$TestCommentId = "",
    [string]$UserDataFile = "test-users.csv"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlanPath = Join-Path $ScriptDir "..\jmeter\ZhiCore-load-test-500.jmx"
$ResultsDirPath = Join-Path $ScriptDir "..\results\load"
$UserDataFilePath = Join-Path $ScriptDir $UserDataFile

if (-not (Test-Path $UserDataFilePath)) {
    Write-Host "ERROR: User data file not found: $UserDataFilePath" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $ResultsDirPath)) {
    New-Item -ItemType Directory -Path $ResultsDirPath -Force | Out-Null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "500 Concurrent Users Load Test" -ForegroundColor Cyan
Write-Host "5 Minutes Duration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ResultFile = Join-Path $ResultsDirPath "load-test-500-$Timestamp.jtl"
$ReportDir = Join-Path $ResultsDirPath "report-500-$Timestamp"

Write-Host "Starting 500-user load test..." -ForegroundColor Yellow
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
        Write-Host "500-User Load Test Completed" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Report: $ReportDir\index.html" -ForegroundColor Cyan
    }
}
catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}

exit 0
