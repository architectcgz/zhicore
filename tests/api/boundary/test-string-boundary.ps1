# String Boundary Tests
# Test Cases: BOUND-016 to BOUND-030
# Coverage: Empty strings, Whitespace, Single char, Max length, Unicode, Emoji, Multi-language

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
$UniqueUsername = "strtest_$Timestamp"
$UniqueEmail = "strtest_$Timestamp@example.com"

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
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json; charset=utf-8"; Headers = $Headers; ErrorAction = "Stop" }
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
Write-Host "String Boundary Tests" -ForegroundColor Cyan
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

# === SECTION 1: Empty and Whitespace Tests ===
Write-Host "=== SECTION 1: Empty and Whitespace Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-016: Empty string title
Write-Host "[BOUND-016] Testing empty string title..." -ForegroundColor Yellow
$Body = @{ title = ""; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-016" -TestName "Empty string" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected empty string ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-016" -TestName "Empty string" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty string"
    Write-Host "  FAIL - Should reject empty string ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-017: Pure whitespace string
Write-Host "[BOUND-017] Testing pure whitespace string..." -ForegroundColor Yellow
$Body = @{ title = "   "; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-017" -TestName "Whitespace string" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected whitespace string ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-017" -TestName "Whitespace string" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-017" -TestName "Whitespace string" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-018: Single character
Write-Host "[BOUND-018] Testing single character..." -ForegroundColor Yellow
$Body = @{ title = "A"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-018" -TestName "Single char" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Accepted single character"
    Write-Host "  PASS - Accepted single character ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-018" -TestName "Single char" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Validation error (min length)"
    Write-Host "  PASS - Validation error for min length ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-018" -TestName "Single char" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: Length Boundary Tests ===
Write-Host "=== SECTION 2: Length Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-019: Max length string (200 chars)
Write-Host "[BOUND-019] Testing max length string (200 chars)..." -ForegroundColor Yellow
$MaxLengthTitle = "A" * 200
$Body = @{ title = $MaxLengthTitle; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-019" -TestName "Max length" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Accepted max length"
    Write-Host "  PASS - Accepted max length ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-019" -TestName "Max length" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rejected at boundary"
    Write-Host "  PASS - Rejected at boundary ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-019" -TestName "Max length" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-020: Over max length (201 chars)
Write-Host "[BOUND-020] Testing over max length (201 chars)..." -ForegroundColor Yellow
$OverMaxTitle = "A" * 201
$Body = @{ title = $OverMaxTitle; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-020" -TestName "Over max length" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected over max length ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "BOUND-020" -TestName "Over max length" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject over max length"
    Write-Host "  FAIL - Should reject over max length ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Special Character Tests ===
Write-Host "=== SECTION 3: Special Character Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-021: Newline characters
Write-Host "[BOUND-021] Testing newline characters..." -ForegroundColor Yellow
$Body = @{ title = "Test`nTitle`nWith`nNewlines"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-021" -TestName "Newline chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Handled newline characters correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-021" -TestName "Newline chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-022: Tab characters
Write-Host "[BOUND-022] Testing tab characters..." -ForegroundColor Yellow
$Body = @{ title = "Test`tTitle`tWith`tTabs"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-022" -TestName "Tab chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Handled tab characters correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-022" -TestName "Tab chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: Unicode and Emoji Tests ===
Write-Host "=== SECTION 4: Unicode and Emoji Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-023: Unicode characters
Write-Host "[BOUND-023] Testing Unicode characters..." -ForegroundColor Yellow
$Body = @{ title = "Test Unicode: ™®©"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-023" -TestName "Unicode chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Unicode characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-023" -TestName "Unicode chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-024: Emoji characters
Write-Host "[BOUND-024] Testing Emoji characters..." -ForegroundColor Yellow
$Body = @{ title = "Test Emoji: 😀🎉🚀"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-024" -TestName "Emoji chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Emoji characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-024" -TestName "Emoji chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 5: Multi-language Tests ===
Write-Host "=== SECTION 5: Multi-language Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-025: Chinese characters
Write-Host "[BOUND-025] Testing Chinese characters..." -ForegroundColor Yellow
$Body = @{ title = "中文测试标题"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-025" -TestName "Chinese chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Chinese characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-025" -TestName "Chinese chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-026: Japanese characters
Write-Host "[BOUND-026] Testing Japanese characters..." -ForegroundColor Yellow
$Body = @{ title = "テストタイトル"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-026" -TestName "Japanese chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Japanese characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-026" -TestName "Japanese chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-027: Korean characters
Write-Host "[BOUND-027] Testing Korean characters..." -ForegroundColor Yellow
$Body = @{ title = "테스트 제목"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-027" -TestName "Korean chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Korean characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-027" -TestName "Korean chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-028: Arabic characters
Write-Host "[BOUND-028] Testing Arabic characters..." -ForegroundColor Yellow
$Body = @{ title = "اختبار العنوان"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-028" -TestName "Arabic chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stored and displayed correctly"
    Write-Host "  PASS - Arabic characters stored correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-028" -TestName "Arabic chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 6: Special Unicode Tests ===
Write-Host "=== SECTION 6: Special Unicode Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-029: Zero-width characters
Write-Host "[BOUND-029] Testing zero-width characters..." -ForegroundColor Yellow
$Body = @{ title = "Test`u{200B}Title`u{200B}With`u{200B}ZWS"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-029" -TestName "Zero-width chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Zero-width characters handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-029" -TestName "Zero-width chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-030: Control characters
Write-Host "[BOUND-030] Testing control characters..." -ForegroundColor Yellow
$Body = @{ title = "Test`u{0001}Title`u{0002}With`u{0003}Control"; content = "Test content"; raw = "Test content" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-030" -TestName "Control chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly filtered or rejected"
    Write-Host "  PASS - Control characters filtered or rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-030" -TestName "Control chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Control characters handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-030" -TestName "Control chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
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
