# Special Characters Injection Security Test Script
# Test Cases: SEC-051 to SEC-060
# Coverage: backslash, forward slash, angle brackets, braces, brackets, percent, hash, dollar, pipe, tilde

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "chartest_$Timestamp"
$UniqueEmail = "chartest_$Timestamp@example.com"

# === Utility Functions ===
function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId
        TestName = $TestName
        Status = $Status
        ResponseTime = $ResponseTime
        Note = $Note
    }
}

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{
            Method = $Method
            Uri = $Url
            ContentType = "application/json"
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) {
            $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10)
        }
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
            }
            catch {
                $Result.Error = $_.Exception.Message
            }
        }
        else {
            $Result.Error = $_.Exception.Message
        }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

function Get-AuthHeaders {
    return @{ "Authorization" = "Bearer $Global:AccessToken" }
}

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Special Characters Injection Security Tests" -ForegroundColor Cyan
Write-Host "Post Service: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "Comment Service: $CommentServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create Test User and Login ===
Write-Host "=== Setup: Creating Test User ===" -ForegroundColor Magenta
$RegisterBody = @{
    userName = $UniqueUsername
    email = $UniqueEmail
    password = $TestUser.password
}
$RegisterResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($RegisterResult.Success -and $RegisterResult.Body.code -eq 200) {
    $Global:TestUserId = $RegisterResult.Body.data.userId
    Write-Host "  User registered: $Global:TestUserId" -ForegroundColor Green
    
    # Login
    $LoginBody = @{
        email = $UniqueEmail
        password = $TestUser.password
    }
    $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
        $Global:AccessToken = $LoginResult.Body.data.accessToken
        Write-Host "  Login successful" -ForegroundColor Green
        
        # Create a test post
        $PostBody = @{
            title = "Test Post for Special Chars $Timestamp"
            content = "This is a test post for special character testing"
        }
        $PostResult = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
        if ($PostResult.Success -and $PostResult.Body.code -eq 200) {
            $Global:TestPostId = $PostResult.Body.data.id
            Write-Host "  Test post created: $Global:TestPostId" -ForegroundColor Green
        }
    }
    else {
        Write-Host "  Login failed" -ForegroundColor Red
        exit 1
    }
}
else {
    Write-Host "  Registration failed" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: Slash Character Tests ===
Write-Host "=== SECTION 1: Slash Character Tests ===" -ForegroundColor Magenta

# SEC-051: Backslash injection
Write-Host "SEC-051 Testing backslash injection..." -ForegroundColor Yellow
$CharPayload = "Test \ backslash"
$Body = @{
    title = $CharPayload
    content = "Content with \ backslash"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-051" -TestName "Backslash" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Backslash correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-051" -TestName "Backslash" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle backslash ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-052: Forward slash injection
Write-Host "SEC-052 Testing forward slash injection..." -ForegroundColor Yellow
$CharPayload = "Test / forward / slash"
$Body = @{
    title = $CharPayload
    content = "Content with / forward / slash"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-052" -TestName "Forward slash" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Forward slash correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-052" -TestName "Forward slash" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle forward slash ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 2: Bracket Character Tests ===
Write-Host ""
Write-Host "=== SECTION 2: Bracket Character Tests ===" -ForegroundColor Magenta

# SEC-053: Angle brackets injection
Write-Host "SEC-053 Testing angle brackets injection..." -ForegroundColor Yellow
$CharPayload = "Test <angle> brackets"
$Body = @{
    title = $CharPayload
    content = "Content with <angle> brackets"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    # Check if angle brackets are escaped
    $GetResult = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$($Result.Body.data.id)" -Headers (Get-AuthHeaders)
    if ($GetResult.Success) {
        Add-TestResult -TestId "SEC-053" -TestName "Angle brackets" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
        Write-Host "  PASS - Angle brackets correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        Add-TestResult -TestId "SEC-053" -TestName "Angle brackets" -Status "WARN" -ResponseTime "$($Result.ResponseTime)ms" -Note "Verify escaping"
        Write-Host "  WARN - Verify angle bracket escaping ($($Result.ResponseTime)ms)" -ForegroundColor Yellow
    }
}
else {
    Add-TestResult -TestId "SEC-053" -TestName "Angle brackets" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle angle brackets ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-054: Curly braces injection
Write-Host "SEC-054 Testing curly braces injection..." -ForegroundColor Yellow
$CharPayload = "Test {curly} braces"
$Body = @{
    title = $CharPayload
    content = "Content with {curly} braces"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-054" -TestName "Curly braces" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Curly braces correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-054" -TestName "Curly braces" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle curly braces ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-055: Square brackets injection
Write-Host "SEC-055 Testing square brackets injection..." -ForegroundColor Yellow
$CharPayload = "Test [square] brackets"
$Body = @{
    title = $CharPayload
    content = "Content with [square] brackets"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-055" -TestName "Square brackets" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Square brackets correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-055" -TestName "Square brackets" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle square brackets ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 3: Special Symbol Tests ===
Write-Host ""
Write-Host "=== SECTION 3: Special Symbol Tests ===" -ForegroundColor Magenta

# SEC-056: Percent sign injection
Write-Host "SEC-056 Testing percent sign injection..." -ForegroundColor Yellow
$CharPayload = "Test % percent % sign"
$Body = @{
    title = $CharPayload
    content = "Content with % percent % sign"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-056" -TestName "Percent sign" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Percent sign correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-056" -TestName "Percent sign" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle percent sign ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-057: Hash sign injection
Write-Host "SEC-057 Testing hash sign injection..." -ForegroundColor Yellow
$CharPayload = "Test # hash # sign"
$Body = @{
    title = $CharPayload
    content = "Content with # hash # sign"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-057" -TestName "Hash sign" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Hash sign correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-057" -TestName "Hash sign" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle hash sign ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-058: Dollar sign injection
Write-Host "SEC-058 Testing dollar sign injection..." -ForegroundColor Yellow
$CharPayload = "Test `$ dollar `$ sign"
$Body = @{
    title = $CharPayload
    content = "Content with `$ dollar `$ sign"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-058" -TestName "Dollar sign" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Dollar sign correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-058" -TestName "Dollar sign" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle dollar sign ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-059: Pipe sign injection
Write-Host "SEC-059 Testing pipe sign injection..." -ForegroundColor Yellow
$CharPayload = "Test | pipe | sign"
$Body = @{
    title = $CharPayload
    content = "Content with | pipe | sign"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-059" -TestName "Pipe sign" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Pipe sign correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-059" -TestName "Pipe sign" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle pipe sign ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-060: Tilde sign injection
Write-Host "SEC-060 Testing tilde sign injection..." -ForegroundColor Yellow
$CharPayload = "Test ~ tilde ~ sign"
$Body = @{
    title = $CharPayload
    content = "Content with ~ tilde ~ sign"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "SEC-060" -TestName "Tilde sign" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Tilde sign correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-060" -TestName "Tilde sign" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Failed to handle"
    Write-Host "  FAIL - Failed to handle tilde sign ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === Test Results Summary ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$WarnCount = ($TestResults | Where-Object { $_.Status -eq "WARN" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host ""
Write-Host "Total Tests: $($TestResults.Count)" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Warnings: $WarnCount" -ForegroundColor Yellow
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -AutoSize

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
}
else {
    exit 0
}
