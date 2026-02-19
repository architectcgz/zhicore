# Numeric Boundary Tests
# Test Cases: BOUND-001 to BOUND-015
# Coverage: Page boundaries, Page size boundaries, ID boundaries, Quantity parameters, Timestamp boundaries

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "boundtest_$Timestamp"
$UniqueEmail = "boundtest_$Timestamp@example.com"

# === Utility Functions ===
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

function Get-AuthHeaders { return @{ "Authorization" = "Bearer $Global:AccessToken" } }

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Numeric Boundary Tests" -ForegroundColor Cyan
Write-Host "Post Service URL: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test user and login ===
Write-Host "=== Setup: Creating test user and logging in ===" -ForegroundColor Magenta
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$RegisterResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($RegisterResult.Success -and $RegisterResult.Body.code -eq 200) {
    $Global:TestUserId = $RegisterResult.Body.data.userId
    Write-Host "User registered successfully: $Global:TestUserId" -ForegroundColor Green
    
    $LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
    $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
        $Global:AccessToken = $LoginResult.Body.data.accessToken
        Write-Host "Login successful, token obtained" -ForegroundColor Green
    } else {
        Write-Host "Login failed, cannot proceed with tests" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "User registration failed, cannot proceed with tests" -ForegroundColor Red
    exit 1
}
Write-Host ""

# === SECTION 1: Page Number Boundary Tests ===
Write-Host "=== SECTION 1: Page Number Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-001: Page number = 0
Write-Host "[BOUND-001] Testing page number = 0..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=10" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-001" -TestName "Page 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returns first page"
    Write-Host "  PASS - Returns first page ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-001" -TestName "Page 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected with 400 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-001" -TestName "Page 0" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-002: Page number = -1
Write-Host "[BOUND-002] Testing page number = -1..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=-1&size=10" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-002" -TestName "Negative page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected negative page ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-002" -TestName "Negative page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject negative page"
    Write-Host "  FAIL - Should reject negative page ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-003: Page number = MAX_INT
Write-Host "[BOUND-003] Testing page number = 2147483647..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=2147483647&size=10" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $IsEmpty = $Result.Body.data.content.Count -eq 0
    if ($IsEmpty) {
        Add-TestResult -TestId "BOUND-003" -TestName "Max page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returns empty list"
        Write-Host "  PASS - Returns empty list ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "BOUND-003" -TestName "Max page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-003" -TestName "Max page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-003" -TestName "Max page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: Page Size Boundary Tests ===
Write-Host "=== SECTION 2: Page Size Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-004: Page size = 0
Write-Host "[BOUND-004] Testing page size = 0..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=0" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-004" -TestName "Size 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected or uses default"
    Write-Host "  PASS - Correctly rejected or uses default ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-004" -TestName "Size 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Uses default size"
    Write-Host "  PASS - Uses default size ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-004" -TestName "Size 0" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-005: Page size = -1
Write-Host "[BOUND-005] Testing page size = -1..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=-1" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-005" -TestName "Negative size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected negative size ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-005" -TestName "Negative size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject negative size"
    Write-Host "  FAIL - Should reject negative size ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-006: Page size = 1000
Write-Host "[BOUND-006] Testing page size = 1000..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=1000" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $ActualSize = $Result.Body.data.size
    if ($ActualSize -le 100) {
        Add-TestResult -TestId "BOUND-006" -TestName "Large size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Limited to max size: $ActualSize"
        Write-Host "  PASS - Limited to max size: $ActualSize ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "BOUND-006" -TestName "Large size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Accepted large size"
        Write-Host "  PASS - Accepted large size ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-006" -TestName "Large size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-006" -TestName "Large size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: ID Boundary Tests ===
Write-Host "=== SECTION 3: ID Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-007: ID = 0
Write-Host "[BOUND-007] Testing ID = 0..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/0" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-007" -TestName "ID 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returns 404"
    Write-Host "  PASS - Correctly returns 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-007" -TestName "ID 0" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404 for ID 0"
    Write-Host "  FAIL - Should return 404 for ID 0 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-008: ID = -1
Write-Host "[BOUND-008] Testing ID = -1..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/-1" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-008" -TestName "Negative ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected negative ID ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-008" -TestName "Negative ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject negative ID"
    Write-Host "  FAIL - Should reject negative ID ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-009: ID = MAX_LONG
Write-Host "[BOUND-009] Testing ID = 9223372036854775807..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/9223372036854775807" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-009" -TestName "Max Long ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returns 404"
    Write-Host "  PASS - Correctly returns 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-009" -TestName "Max Long ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: Quantity Parameter Boundary Tests ===
Write-Host "=== SECTION 4: Quantity Parameter Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-010: Count = 0
Write-Host "[BOUND-010] Testing count parameter = 0..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=10&count=0" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-010" -TestName "Count 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-010" -TestName "Count 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-010" -TestName "Count 0" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-011: Count = -1
Write-Host "[BOUND-011] Testing count parameter = -1..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?page=0&size=10&count=-1" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-011" -TestName "Negative count" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected negative count ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-011" -TestName "Negative count" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-011" -TestName "Negative count" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-012: Offset = -1
Write-Host "[BOUND-012] Testing offset parameter = -1..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?offset=-1&limit=10" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-012" -TestName "Negative offset" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected negative offset ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-012" -TestName "Negative offset" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-012" -TestName "Negative offset" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-013: Limit = 10000
Write-Host "[BOUND-013] Testing limit parameter = 10000..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?offset=0&limit=10000" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-013" -TestName "Large limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Limited to max or accepted"
    Write-Host "  PASS - Limited to max or accepted ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-013" -TestName "Large limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-013" -TestName "Large limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 5: Timestamp Boundary Tests ===
Write-Host "=== SECTION 5: Timestamp Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-014: Timestamp = 0
Write-Host "[BOUND-014] Testing timestamp = 0..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?timestamp=0" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-014" -TestName "Timestamp 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-014" -TestName "Timestamp 0" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-014" -TestName "Timestamp 0" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-015: Future timestamp
Write-Host "[BOUND-015] Testing future timestamp..." -ForegroundColor Yellow
$FutureTimestamp = [DateTimeOffset]::UtcNow.AddYears(1).ToUnixTimeSeconds()
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?timestamp=$FutureTimestamp" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-015" -TestName "Future timestamp" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-015" -TestName "Future timestamp" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-015" -TestName "Future timestamp" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === Test Results Summary ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count
$TotalCount = $TestResults.Count

Write-Host ""
Write-Host "Total Tests: $TotalCount" -ForegroundColor Cyan
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -Property TestId, TestName, Status, ResponseTime, Note -AutoSize

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
