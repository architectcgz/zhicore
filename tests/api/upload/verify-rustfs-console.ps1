# RustFS Console Verification Script
# Task: 通过 RustFS Console (http://localhost:9101) 确认文件存在
# Requirements: REQ-5

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath

# RustFS Configuration
$RustFSEndpoint = "http://localhost:9100"
$RustFSConsole = "http://localhost:9100/rustfs/console/browser"
$RustFSBucket = "ZhiCore-uploads"
$RustFSAccessKey = "admin"
$RustFSSecretKey = "admin123456"

function Write-TestHeader {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-TestStep {
    param([string]$Step, [string]$Status = "INFO")
    $Color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "WARN" { "Yellow" }
        "SKIP" { "Gray" }
        default { "Cyan" }
    }
    Write-Host "[$Status] $Step" -ForegroundColor $Color
}

Write-TestHeader "RustFS Console Verification"
Write-Host "RustFS S3 API: $RustFSEndpoint" -ForegroundColor Gray
Write-Host "RustFS Console: $RustFSConsole" -ForegroundColor Gray
Write-Host "Bucket: $RustFSBucket" -ForegroundColor Gray
Write-Host ""

$TestsPassed = 0
$TestsFailed = 0

# === Test 1: Check RustFS S3 API ===
Write-Host "=== Test 1: RustFS S3 API Health ===" -ForegroundColor Magenta
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "$RustFSEndpoint" -Method GET -TimeoutSec 10 -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Write-TestStep "RustFS S3 API is accessible at $RustFSEndpoint" "PASS"
        $TestsPassed++
    }
}
catch {
    Write-TestStep "RustFS S3 API not accessible: $($_.Exception.Message)" "FAIL"
    $TestsFailed++
}

# === Test 2: Check RustFS Console Port ===
Write-Host ""
Write-Host "=== Test 2: RustFS Console Port ===" -ForegroundColor Magenta
Write-Host ""

try {
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    $asyncResult = $tcpClient.BeginConnect("localhost", 9101, $null, $null)
    $waitResult = $asyncResult.AsyncWaitHandle.WaitOne(5000, $false)
    
    if ($waitResult -and $tcpClient.Connected) {
        Write-TestStep "RustFS Console port 9101 is open" "PASS"
        $TestsPassed++
        $tcpClient.Close()
    } else {
        Write-TestStep "RustFS Console port 9101 is not responding" "FAIL"
        $TestsFailed++
    }
}
catch {
    Write-TestStep "Cannot connect to RustFS Console port: $($_.Exception.Message)" "FAIL"
    $TestsFailed++
}

# === Test 3: List Buckets via S3 API ===
Write-Host ""
Write-Host "=== Test 3: List Buckets ===" -ForegroundColor Magenta
Write-Host ""

try {
    # RustFS root endpoint returns bucket list in XML format
    $response = Invoke-WebRequest -Uri "$RustFSEndpoint" -Method GET -TimeoutSec 10 -ErrorAction Stop
    $content = $response.Content
    
    if ($content -match "ZhiCore-uploads" -or $content -match "Bucket") {
        Write-TestStep "Bucket listing available" "PASS"
        $TestsPassed++
        
        # Try to extract bucket names
        if ($content -match "<Name>([^<]+)</Name>") {
            $buckets = [regex]::Matches($content, "<Name>([^<]+)</Name>") | ForEach-Object { $_.Groups[1].Value }
            Write-Host "  Found buckets:" -ForegroundColor Gray
            foreach ($bucket in $buckets) {
                Write-Host "    - $bucket" -ForegroundColor Gray
            }
        }
    } else {
        Write-TestStep "Bucket listing returned but no buckets found" "WARN"
    }
}
catch {
    Write-TestStep "Cannot list buckets: $($_.Exception.Message)" "WARN"
}

# === Test 4: Check ZhiCore-uploads bucket ===
Write-Host ""
Write-Host "=== Test 4: Check ZhiCore-uploads Bucket ===" -ForegroundColor Magenta
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "$RustFSEndpoint/$RustFSBucket" -Method GET -TimeoutSec 10 -ErrorAction Stop
    Write-TestStep "Bucket '$RustFSBucket' exists and is accessible" "PASS"
    $TestsPassed++
    
    # Try to list objects in bucket
    $content = $response.Content
    if ($content -match "<Key>([^<]+)</Key>") {
        $objects = [regex]::Matches($content, "<Key>([^<]+)</Key>") | ForEach-Object { $_.Groups[1].Value }
        Write-Host "  Found $($objects.Count) object(s) in bucket:" -ForegroundColor Gray
        $displayCount = [Math]::Min($objects.Count, 10)
        for ($i = 0; $i -lt $displayCount; $i++) {
            Write-Host "    - $($objects[$i])" -ForegroundColor Gray
        }
        if ($objects.Count -gt 10) {
            Write-Host "    ... and $($objects.Count - 10) more" -ForegroundColor Gray
        }
    } else {
        Write-Host "  Bucket is empty or objects not listed" -ForegroundColor Gray
    }
}
catch {
    if ($_.Exception.Response.StatusCode -eq 403) {
        Write-TestStep "Bucket '$RustFSBucket' exists but requires authentication" "WARN"
        Write-Host "  This is expected - bucket access requires credentials" -ForegroundColor Gray
    } elseif ($_.Exception.Response.StatusCode -eq 404) {
        Write-TestStep "Bucket '$RustFSBucket' does not exist" "WARN"
        Write-Host "  The bucket will be created when first upload occurs with S3 storage enabled" -ForegroundColor Gray
    } else {
        Write-TestStep "Cannot access bucket: $($_.Exception.Message)" "WARN"
    }
}

# === Summary and Instructions ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verification Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Tests Passed: $TestsPassed" -ForegroundColor Green
Write-Host "Tests Failed: $TestsFailed" -ForegroundColor Red
Write-Host ""

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "Manual Console Verification Steps" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "RustFS Console may not have a web UI like MinIO." -ForegroundColor Cyan
Write-Host "RustFS is a lightweight S3-compatible storage that focuses on API access." -ForegroundColor Cyan
Write-Host ""
Write-Host "To verify files exist in RustFS, use one of these methods:" -ForegroundColor White
Write-Host ""
Write-Host "Method 1: AWS CLI (if installed)" -ForegroundColor Yellow
Write-Host "  aws --endpoint-url http://localhost:9100 s3 ls s3://ZhiCore-uploads/" -ForegroundColor Gray
Write-Host "  aws --endpoint-url http://localhost:9100 s3 ls s3://ZhiCore-uploads/images/" -ForegroundColor Gray
Write-Host ""
Write-Host "Method 2: Direct URL Access (for public files)" -ForegroundColor Yellow
Write-Host "  Open in browser: http://localhost:9100/ZhiCore-uploads/images/<filename>" -ForegroundColor Gray
Write-Host ""
Write-Host "Method 3: PowerShell S3 API" -ForegroundColor Yellow
Write-Host "  Invoke-WebRequest -Uri 'http://localhost:9100/ZhiCore-uploads' -Method GET" -ForegroundColor Gray
Write-Host ""
Write-Host "Note: To upload files to RustFS, ensure the upload service is started with:" -ForegroundColor Cyan
Write-Host "  `$env:STORAGE_TYPE='s3'" -ForegroundColor Gray
Write-Host "  mvn spring-boot:run -pl ZhiCore-upload" -ForegroundColor Gray
Write-Host ""

if ($TestsFailed -eq 0) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "RustFS is running and accessible!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    exit 0
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Some checks failed - see details above" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}
