# Quick Load Test Runner
# This script runs a quick 2-minute load test to verify configuration

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Read test data
$DataFile = Join-Path $ScriptDir "test-data.txt"
if (-not (Test-Path $DataFile)) {
    Write-Host "ERROR: Test data file not found. Run prepare-test-data.ps1 first." -ForegroundColor Red
    exit 1
}

$TestData = Get-Content $DataFile | ConvertFrom-StringData
$TestPostId = $TestData.TEST_POST_ID
$TestCommentId = $TestData.TEST_COMMENT_ID
$AccessToken = $TestData.ACCESS_TOKEN

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Quick Load Test (2 minutes)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test Data:" -ForegroundColor Yellow
Write-Host "  Post ID: $TestPostId" -ForegroundColor White
Write-Host "  Comment ID: $TestCommentId" -ForegroundColor White
Write-Host "  Token: $($AccessToken.Substring(0, 20))..." -ForegroundColor White
Write-Host ""

# Run the full load test script with a 2-minute timeout
Write-Host "Starting quick test..." -ForegroundColor Green
Write-Host "This will run for approximately 2 minutes" -ForegroundColor Yellow
Write-Host ""

& "$ScriptDir\run-load-test.ps1" `
    -JMeterPath $JMeterPath `
    -TestPostId $TestPostId `
    -TestCommentId $TestCommentId `
    -AccessToken $AccessToken

exit $LASTEXITCODE
