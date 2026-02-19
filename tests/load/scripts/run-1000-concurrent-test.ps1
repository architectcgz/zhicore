# Complete 1000 Concurrent Users Load Test Workflow
# This script automates the entire process: prepare data -> run test -> analyze results

param(
    [string]$JMeterPath = "C:\WorkTools\apache-jmeter-5.6.3\bin\jmeter.bat",
    [int]$UserCount = 100,
    [switch]$SkipDataPreparation = $false
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1000 Concurrent Users Load Test" -ForegroundColor Cyan
Write-Host "Complete Workflow" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check prerequisites
Write-Host "[STEP 1] Checking prerequisites..." -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $JMeterPath)) {
    Write-Host "ERROR: JMeter not found at: $JMeterPath" -ForegroundColor Red
    Write-Host "Please install JMeter or update the path" -ForegroundColor Yellow
    exit 1
}
Write-Host "  [PASS] JMeter found" -ForegroundColor Green

# Check if services are running
Write-Host "  Checking services..." -ForegroundColor Cyan
$Services = @(
    @{ Name = "Gateway"; Port = 8000 }
    @{ Name = "User"; Port = 8081 }
    @{ Name = "Post"; Port = 8082 }
    @{ Name = "Comment"; Port = 8083 }
)

$AllServicesUp = $true
foreach ($Service in $Services) {
    try {
        $Connection = Test-NetConnection -ComputerName localhost -Port $Service.Port -WarningAction SilentlyContinue -ErrorAction Stop
        if ($Connection.TcpTestSucceeded) {
            Write-Host "    [PASS] $($Service.Name) service (port $($Service.Port))" -ForegroundColor Green
        } else {
            Write-Host "    [FAIL] $($Service.Name) service (port $($Service.Port)) not responding" -ForegroundColor Red
            $AllServicesUp = $false
        }
    }
    catch {
        Write-Host "    [FAIL] $($Service.Name) service (port $($Service.Port)) not accessible" -ForegroundColor Red
        $AllServicesUp = $false
    }
}

if (-not $AllServicesUp) {
    Write-Host ""
    Write-Host "ERROR: Not all services are running" -ForegroundColor Red
    Write-Host "Please start all services before running the load test" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Step 2: Prepare test data
if (-not $SkipDataPreparation) {
    Write-Host "[STEP 2] Preparing test data..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Creating $UserCount test users..." -ForegroundColor Cyan
    Write-Host "  This may take a few minutes..." -ForegroundColor Gray
    Write-Host ""
    
    $PrepareScript = Join-Path $ScriptDir "prepare-multiple-users.ps1"
    & $PrepareScript -UserCount $UserCount
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ERROR: Test data preparation failed" -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
} else {
    Write-Host "[STEP 2] Skipping test data preparation (using existing data)" -ForegroundColor Yellow
    Write-Host ""
}

# Step 3: Load test configuration
Write-Host "[STEP 3] Loading test configuration..." -ForegroundColor Yellow
Write-Host ""

$ConfigFile = Join-Path $ScriptDir "test-data-multi.txt"
if (-not (Test-Path $ConfigFile)) {
    Write-Host "ERROR: Test configuration not found: $ConfigFile" -ForegroundColor Red
    Write-Host "Please run prepare-multiple-users.ps1 first" -ForegroundColor Yellow
    exit 1
}

$Config = @{}
Get-Content $ConfigFile | ForEach-Object {
    if ($_ -match "^(.+?)=(.+)$") {
        $Config[$matches[1]] = $matches[2]
    }
}

$TestPostId = $Config["TEST_POST_ID"]
$TestCommentId = $Config["TEST_COMMENT_ID"]
$UserDataFile = $Config["USER_DATA_FILE"]

Write-Host "  Test Post ID: $TestPostId" -ForegroundColor White
Write-Host "  Test Comment ID: $TestCommentId" -ForegroundColor White
Write-Host "  User Data File: $UserDataFile" -ForegroundColor White
Write-Host ""

# Step 4: Run load test
Write-Host "[STEP 4] Running 1000 concurrent users load test..." -ForegroundColor Yellow
Write-Host ""
Write-Host "  Test Configuration:" -ForegroundColor Cyan
Write-Host "    - Article Detail: 400 concurrent users" -ForegroundColor White
Write-Host "    - Article List: 200 concurrent users" -ForegroundColor White
Write-Host "    - Article Like: 400 concurrent users" -ForegroundColor White
Write-Host "    - Total: 1000 concurrent users" -ForegroundColor White
Write-Host "    - Duration: 5 minutes" -ForegroundColor White
Write-Host "    - Ramp-up: 60 seconds" -ForegroundColor White
Write-Host ""
Write-Host "  Starting test at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
Write-Host "  Expected completion: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss' -Date (Get-Date).AddMinutes(6))" -ForegroundColor Gray
Write-Host ""

$TestScript = Join-Path $ScriptDir "run-load-test-1000.ps1"
& $TestScript -JMeterPath $JMeterPath -TestPostId $TestPostId -TestCommentId $TestCommentId -UserDataFile $UserDataFile

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Load test failed" -ForegroundColor Red
    exit 1
}

# Step 5: Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1000 Concurrent Users Load Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Open the HTML report in your browser" -ForegroundColor White
Write-Host "  2. Analyze the following metrics:" -ForegroundColor White
Write-Host "     - Error rate (target: < 1%)" -ForegroundColor Gray
Write-Host "     - Average response time (target: < 500ms)" -ForegroundColor Gray
Write-Host "     - Throughput (target: > 1000 req/s)" -ForegroundColor Gray
Write-Host "     - 95th percentile response time" -ForegroundColor Gray
Write-Host "  3. Check service logs for errors:" -ForegroundColor White
Write-Host "     docker logs blog-gateway" -ForegroundColor Gray
Write-Host "     docker logs blog-user" -ForegroundColor Gray
Write-Host "     docker logs blog-post" -ForegroundColor Gray
Write-Host "  4. Monitor system resources:" -ForegroundColor White
Write-Host "     - CPU usage" -ForegroundColor Gray
Write-Host "     - Memory usage" -ForegroundColor Gray
Write-Host "     - Database connections" -ForegroundColor Gray
Write-Host "     - Redis connections" -ForegroundColor Gray
Write-Host ""

exit 0
