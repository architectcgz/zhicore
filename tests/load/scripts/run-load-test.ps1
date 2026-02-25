# ZhiCore Microservices Load Test Runner
# This script runs JMeter load tests for the ZhiCore microservices system

param(
    [string]$JMeterPath = "C:\apache-jmeter\bin\jmeter.bat",
    [string]$TestPlan = "../jmeter/ZhiCore-load-test.jmx",
    [string]$ResultsDir = "../results/load",
    [string]$BaseUrl = "http://localhost:8000",
    [string]$TestPostId = "1",
    [string]$TestCommentId = "1",
    [string]$AccessToken = "",
    [switch]$GuiMode = $false,
    [string]$Scenario = "all"
)

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlanPath = Join-Path $ScriptDir $TestPlan
$ResultsDirPath = Join-Path $ScriptDir $ResultsDir

# Create results directory if it doesn't exist
if (-not (Test-Path $ResultsDirPath)) {
    New-Item -ItemType Directory -Path $ResultsDirPath -Force | Out-Null
}

# Check if JMeter exists
if (-not (Test-Path $JMeterPath)) {
    Write-Host "ERROR: JMeter not found at: $JMeterPath" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Apache JMeter and update the JMeterPath parameter." -ForegroundColor Yellow
    Write-Host "Download from: https://jmeter.apache.org/download_jmeter.cgi" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Example usage:" -ForegroundColor Yellow
    Write-Host "  .\run-load-test.ps1 -JMeterPath 'C:\apache-jmeter-5.6.3\bin\jmeter.bat'" -ForegroundColor Cyan
    exit 1
}

# Check if test plan exists
if (-not (Test-Path $TestPlanPath)) {
    Write-Host "ERROR: Test plan not found at: $TestPlanPath" -ForegroundColor Red
    exit 1
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ZhiCore Microservices Load Test Runner" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  JMeter Path: $JMeterPath" -ForegroundColor White
Write-Host "  Test Plan: $TestPlanPath" -ForegroundColor White
Write-Host "  Results Dir: $ResultsDirPath" -ForegroundColor White
Write-Host "  Base URL: $BaseUrl" -ForegroundColor White
Write-Host "  Test Post ID: $TestPostId" -ForegroundColor White
Write-Host "  Test Comment ID: $TestCommentId" -ForegroundColor White
Write-Host "  Scenario: $Scenario" -ForegroundColor White
Write-Host "  GUI Mode: $GuiMode" -ForegroundColor White
Write-Host ""

# Check if access token is provided
if ([string]::IsNullOrEmpty($AccessToken)) {
    Write-Host "WARNING: No access token provided. Tests requiring authentication will fail." -ForegroundColor Yellow
    Write-Host "Please provide an access token using -AccessToken parameter." -ForegroundColor Yellow
    Write-Host ""
    $Response = Read-Host "Continue anyway? (y/n)"
    if ($Response -ne "y") {
        Write-Host "Test cancelled." -ForegroundColor Yellow
        exit 0
    }
}

# Generate timestamp for results
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ResultFile = Join-Path $ResultsDirPath "load-test-results-$Timestamp.jtl"
$ReportDir = Join-Path $ResultsDirPath "report-$Timestamp"

# Build JMeter command
$JMeterArgs = @()

if ($GuiMode) {
    # GUI mode for test development
    $JMeterArgs += "-t", $TestPlanPath
    $JMeterArgs += "-JBASE_URL=$BaseUrl"
    $JMeterArgs += "-JTEST_POST_ID=$TestPostId"
    $JMeterArgs += "-JTEST_COMMENT_ID=$TestCommentId"
    $JMeterArgs += "-JACCESS_TOKEN=$AccessToken"
    
    Write-Host "Starting JMeter in GUI mode..." -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop the test." -ForegroundColor Yellow
    Write-Host ""
} else {
    # Non-GUI mode for actual load testing
    $JMeterArgs += "-n"
    $JMeterArgs += "-t", $TestPlanPath
    $JMeterArgs += "-l", $ResultFile
    $JMeterArgs += "-e"
    $JMeterArgs += "-o", $ReportDir
    $JMeterArgs += "-JBASE_URL=$BaseUrl"
    $JMeterArgs += "-JTEST_POST_ID=$TestPostId"
    $JMeterArgs += "-JTEST_COMMENT_ID=$TestCommentId"
    $JMeterArgs += "-JACCESS_TOKEN=$AccessToken"
    
    Write-Host "Starting JMeter load test..." -ForegroundColor Green
    Write-Host "Results will be saved to: $ResultFile" -ForegroundColor Cyan
    Write-Host "HTML report will be generated at: $ReportDir" -ForegroundColor Cyan
    Write-Host ""
}

# Run JMeter
try {
    $StartTime = Get-Date
    
    & $JMeterPath $JMeterArgs
    
    $EndTime = Get-Date
    $Duration = $EndTime - $StartTime
    
    if (-not $GuiMode) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Load Test Completed" -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Duration: $($Duration.ToString('hh\:mm\:ss'))" -ForegroundColor White
        Write-Host "Results: $ResultFile" -ForegroundColor White
        Write-Host "Report: $ReportDir\index.html" -ForegroundColor White
        Write-Host ""
        
        # Check if results file exists
        if (Test-Path $ResultFile) {
            $ResultsSize = (Get-Item $ResultFile).Length / 1MB
            Write-Host "Results file size: $([math]::Round($ResultsSize, 2)) MB" -ForegroundColor White
        }
        
        # Check if report was generated
        $ReportIndex = Join-Path $ReportDir "index.html"
        if (Test-Path $ReportIndex) {
            Write-Host ""
            Write-Host "HTML report generated successfully!" -ForegroundColor Green
            Write-Host "Open in browser: $ReportIndex" -ForegroundColor Cyan
        } else {
            Write-Host ""
            Write-Host "WARNING: HTML report was not generated." -ForegroundColor Yellow
        }
    }
    
    Write-Host ""
    Write-Host "Test execution completed." -ForegroundColor Green
    exit 0
}
catch {
    Write-Host ""
    Write-Host "ERROR: Failed to run JMeter test" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
