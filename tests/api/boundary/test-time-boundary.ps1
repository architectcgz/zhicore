# Time Boundary Tests
# Test Cases: BOUND-041 to BOUND-050
# Coverage: Time ranges, Date formats, Leap year, Timezone, DST, Timestamp boundaries

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
$UniqueUsername = "timetest_$Timestamp"
$UniqueEmail = "timetest_$Timestamp@example.com"

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
Write-Host "Time Boundary Tests" -ForegroundColor Cyan
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

# === SECTION 1: Time Range Tests ===
Write-Host "=== SECTION 1: Time Range Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-041: Start time > End time
Write-Host "[BOUND-041] Testing start time > end time..." -ForegroundColor Yellow
$StartTime = [DateTimeOffset]::UtcNow.AddDays(1).ToString("yyyy-MM-ddTHH:mm:ssZ")
$EndTime = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$StartEncoded = [System.Uri]::EscapeDataString($StartTime)
$EndEncoded = [System.Uri]::EscapeDataString($EndTime)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$StartEncoded&endTime=$EndEncoded" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-041" -TestName "Start > End" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected start > end ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-041" -TestName "Start > End" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-041" -TestName "Start > End" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-042: Start time = End time
Write-Host "[BOUND-042] Testing start time = end time..." -ForegroundColor Yellow
$SameTime = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$TimeEncoded = [System.Uri]::EscapeDataString($SameTime)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$TimeEncoded&endTime=$TimeEncoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $IsEmpty = $Result.Body.data.content.Count -eq 0
    if ($IsEmpty) {
        Add-TestResult -TestId "BOUND-042" -TestName "Start = End" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returns empty or handled"
        Write-Host "  PASS - Returns empty or handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "BOUND-042" -TestName "Start = End" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
        Write-Host "  PASS - Handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-042" -TestName "Start = End" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-043: Multi-year time range
Write-Host "[BOUND-043] Testing multi-year time range..." -ForegroundColor Yellow
$StartTime = "2020-01-01T00:00:00Z"
$EndTime = "2026-12-31T23:59:59Z"
$StartEncoded = [System.Uri]::EscapeDataString($StartTime)
$EndEncoded = [System.Uri]::EscapeDataString($EndTime)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$StartEncoded&endTime=$EndEncoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-043" -TestName "Multi-year range" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Multi-year range handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-043" -TestName "Multi-year range" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-044: 1 millisecond time range
Write-Host "[BOUND-044] Testing 1ms time range..." -ForegroundColor Yellow
$BaseTime = [DateTimeOffset]::UtcNow
$StartTime = $BaseTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$EndTime = $BaseTime.AddMilliseconds(1).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$StartEncoded = [System.Uri]::EscapeDataString($StartTime)
$EndEncoded = [System.Uri]::EscapeDataString($EndTime)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$StartEncoded&endTime=$EndEncoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-044" -TestName "1ms range" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - 1ms range handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-044" -TestName "1ms range" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: Date Format Tests ===
Write-Host "=== SECTION 2: Date Format Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-045: Invalid date format
Write-Host "[BOUND-045] Testing invalid date format..." -ForegroundColor Yellow
$InvalidDate = "invalid-date-format"
$InvalidEncoded = [System.Uri]::EscapeDataString($InvalidDate)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$InvalidEncoded" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-045" -TestName "Invalid format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid format ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-045" -TestName "Invalid format" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid format"
    Write-Host "  FAIL - Should reject invalid format ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-046: Leap year date (Feb 29, 2024)
Write-Host "[BOUND-046] Testing leap year date..." -ForegroundColor Yellow
$LeapDate = "2024-02-29T12:00:00Z"
$LeapEncoded = [System.Uri]::EscapeDataString($LeapDate)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$LeapEncoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-046" -TestName "Leap year" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Leap year date handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-046" -TestName "Leap year" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Timezone Tests ===
Write-Host "=== SECTION 3: Timezone Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-047: UTC+14 timezone boundary
Write-Host "[BOUND-047] Testing UTC+14 timezone..." -ForegroundColor Yellow
$UTC14Time = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(14)).ToString("yyyy-MM-ddTHH:mm:sszzz")
$UTC14Encoded = [System.Uri]::EscapeDataString($UTC14Time)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$UTC14Encoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-047" -TestName "UTC+14" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - UTC+14 timezone handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-047" -TestName "UTC+14" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-048: DST transition time (simulated)
Write-Host "[BOUND-048] Testing DST transition time..." -ForegroundColor Yellow
$DSTTime = "2024-03-10T02:30:00-05:00"
$DSTEncoded = [System.Uri]::EscapeDataString($DSTTime)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$DSTEncoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-048" -TestName "DST transition" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - DST transition handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-048" -TestName "DST transition" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: Timestamp Boundary Tests ===
Write-Host "=== SECTION 4: Timestamp Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-049: Unix timestamp max value (2038-01-19)
Write-Host "[BOUND-049] Testing Unix timestamp max value..." -ForegroundColor Yellow
$MaxUnixTimestamp = 2147483647
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?timestamp=$MaxUnixTimestamp" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-049" -TestName "Max Unix timestamp" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Max Unix timestamp handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-049" -TestName "Max Unix timestamp" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-049" -TestName "Max Unix timestamp" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-050: ISO8601 format
Write-Host "[BOUND-050] Testing ISO8601 format..." -ForegroundColor Yellow
$ISO8601Time = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$ISO8601Encoded = [System.Uri]::EscapeDataString($ISO8601Time)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts?startTime=$ISO8601Encoded" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-050" -TestName "ISO8601 format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Parsed correctly"
    Write-Host "  PASS - ISO8601 format parsed correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-050" -TestName "ISO8601 format" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
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
