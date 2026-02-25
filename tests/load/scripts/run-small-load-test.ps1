# Small Scale Load Test (50 concurrent users, 2 minutes)
# This script runs a small-scale load test to verify system stability

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat",
    [string]$TestPostId = "",
    [string]$TestCommentId = "",
    [string]$UserDataFile = "test-users.csv"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlanPath = Join-Path $ScriptDir "..\jmeter\ZhiCore-load-test-small.jmx"
$ResultsDirPath = Join-Path $ScriptDir "..\results\load"
$UserDataFilePath = Join-Path $ScriptDir $UserDataFile

# Check if user data file exists
if (-not (Test-Path $UserDataFilePath)) {
    Write-Host "ERROR: User data file not found: $UserDataFilePath" -ForegroundColor Red
    Write-Host "Please run prepare-multiple-users.ps1 first" -ForegroundColor Yellow
    exit 1
}

# Create results directory
if (-not (Test-Path $ResultsDirPath)) {
    New-Item -ItemType Directory -Path $ResultsDirPath -Force | Out-Null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Small Scale Load Test" -ForegroundColor Cyan
Write-Host "50 Concurrent Users × 2 Minutes" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Test Post ID: $TestPostId" -ForegroundColor White
Write-Host "  Test Comment ID: $TestCommentId" -ForegroundColor White
Write-Host "  User Data File: $UserDataFilePath" -ForegroundColor White
Write-Host "  Concurrent Users: 50" -ForegroundColor White
Write-Host "  Duration: 2 minutes" -ForegroundColor White
Write-Host ""

# Generate timestamp
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ResultFile = Join-Path $ResultsDirPath "small-load-test-$Timestamp.jtl"
$ReportDir = Join-Path $ResultsDirPath "small-report-$Timestamp"

Write-Host "Starting small-scale load test..." -ForegroundColor Yellow
Write-Host ""

# Build JMeter command
$JMeterArgs = @(
    "-n"
    "-t", "`"$TestPlanPath`""
    "-l", "`"$ResultFile`""
    "-e"
    "-o", "`"$ReportDir`""
    "-JBASE_URL=http://localhost:8000"
    "-JTEST_POST_ID=$TestPostId"
    "-JTEST_COMMENT_ID=$TestCommentId"
    "-JUSER_DATA_FILE=$UserDataFilePath"
)

$JMeterCommand = "& `"$JMeterPath`" $($JMeterArgs -join ' ')"

try {
    Invoke-Expression $JMeterCommand
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "Small Load Test Completed" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Results: $ResultFile" -ForegroundColor Cyan
        Write-Host "Report: $ReportDir\index.html" -ForegroundColor Cyan
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "Test failed with exit code: $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host ""
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}

exit 0
