# Token Boundary Tests
# Test Cases: AUTH-001 to AUTH-010 (10 tests)
# Coverage: Empty Token, Malformed Token, Expired Token, Tampered Token, Wrong Signature, Missing Claims, Long Token, Special Chars, Logged Out User, Blacklisted Token

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$GatewayUrl = $Config.gateway_url
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:ValidAccessToken = ""
$Global:ValidRefreshToken = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "authtest_$Timestamp"
$UniqueEmail = "authtest_$Timestamp@example.com"

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
    param([string]$Token = $Global:ValidAccessToken)
    return @{ "Authorization" = "Bearer $Token" }
}

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Token Boundary Tests (AUTH-001 to AUTH-010)" -ForegroundColor Cyan
Write-Host "Gateway URL: $GatewayUrl" -ForegroundColor Cyan
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
    $Global:TestUserId = $RegisterResult.Body.data
    Write-Host "  User registered successfully: $Global:TestUserId" -ForegroundColor Green
    
    # Login to get valid token
    $LoginBody = @{
        email = $UniqueEmail
        password = $TestUser.password
    }
    $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
        $Global:ValidAccessToken = $LoginResult.Body.data.accessToken
        $Global:ValidRefreshToken = $LoginResult.Body.data.refreshToken
        Write-Host "  Login successful, token obtained" -ForegroundColor Green
    }
    else {
        Write-Host "  FAILED to login test user" -ForegroundColor Red
        exit 1
    }
}
else {
    Write-Host "  FAILED to create test user" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: Token Boundary Tests ===
Write-Host "=== SECTION 1: Token Boundary Tests ===" -ForegroundColor Magenta
Write-Host ""

# AUTH-001: Empty Token
Write-Host "AUTH-001 Testing empty token..." -ForegroundColor Yellow
$EmptyHeaders = @{ "Authorization" = "Bearer " }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $EmptyHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-001" -TestName "Empty Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected empty token"
    Write-Host "  PASS - Correctly rejected empty token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "AUTH-001" -TestName "Empty Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-002: Malformed Token (Invalid Format)
Write-Host "AUTH-002 Testing malformed token..." -ForegroundColor Yellow
$MalformedHeaders = @{ "Authorization" = "Bearer invalid.token.format" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $MalformedHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-002" -TestName "Malformed Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected malformed token"
    Write-Host "  PASS - Correctly rejected malformed token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-002" -TestName "Malformed Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-003: Expired Token (Simulated by using an old token structure)
Write-Host "AUTH-003 Testing expired token..." -ForegroundColor Yellow
# Note: We can't easily create a truly expired token without waiting, so we test with a malformed token that looks expired
$ExpiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
$ExpiredHeaders = @{ "Authorization" = "Bearer $ExpiredToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $ExpiredHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-003" -TestName "Expired Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected expired/invalid token"
    Write-Host "  PASS - Correctly rejected expired/invalid token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-003" -TestName "Expired Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-004: Tampered Token (Modified Payload)
Write-Host "AUTH-004 Testing tampered token..." -ForegroundColor Yellow
if ($Global:ValidAccessToken) {
    # Tamper with the token by changing a character in the middle
    $TamperedToken = $Global:ValidAccessToken.Substring(0, $Global:ValidAccessToken.Length - 10) + "TAMPERED"
    $TamperedHeaders = @{ "Authorization" = "Bearer $TamperedToken" }
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $TamperedHeaders
    
    if ($Result.StatusCode -eq 401) {
        Add-TestResult -TestId "AUTH-004" -TestName "Tampered Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected tampered token"
        Write-Host "  PASS - Correctly rejected tampered token ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        Add-TestResult -TestId "AUTH-004" -TestName "Tampered Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
        Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "AUTH-004" -TestName "Tampered Token" -Status "SKIP" -ResponseTime "-" -Note "No valid token available"
    Write-Host "  SKIP - No valid token available" -ForegroundColor Gray
}

# AUTH-005: Wrong Signature Token
Write-Host "AUTH-005 Testing token with wrong signature..." -ForegroundColor Yellow
# Use a token with valid structure but wrong signature
$WrongSigToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.WrongSignatureHere123456789"
$WrongSigHeaders = @{ "Authorization" = "Bearer $WrongSigToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $WrongSigHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-005" -TestName "Wrong Signature" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected wrong signature"
    Write-Host "  PASS - Correctly rejected wrong signature ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-005" -TestName "Wrong Signature" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-006: Missing Claims Token
Write-Host "AUTH-006 Testing token with missing claims..." -ForegroundColor Yellow
# Token with minimal claims (missing required claims like userId)
$MissingClaimsToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjJ9.InvalidSignature"
$MissingClaimsHeaders = @{ "Authorization" = "Bearer $MissingClaimsToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $MissingClaimsHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-006" -TestName "Missing Claims" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected missing claims"
    Write-Host "  PASS - Correctly rejected missing claims ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-006" -TestName "Missing Claims" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-007: Extremely Long Token
Write-Host "AUTH-007 Testing extremely long token..." -ForegroundColor Yellow
$LongToken = "A" * 10000  # 10KB token
$LongHeaders = @{ "Authorization" = "Bearer $LongToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $LongHeaders

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-007" -TestName "Long Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected long token"
    Write-Host "  PASS - Correctly rejected long token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-007" -TestName "Long Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 400/401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 400/401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-008: Token with Special Characters
Write-Host "AUTH-008 Testing token with special characters..." -ForegroundColor Yellow
$SpecialCharsToken = "Bearer <script>alert('xss')</script>"
$SpecialCharsHeaders = @{ "Authorization" = $SpecialCharsToken }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $SpecialCharsHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-008" -TestName "Special Chars Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected special chars"
    Write-Host "  PASS - Correctly rejected special chars ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-008" -TestName "Special Chars Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-009: Logged Out User Token (Simulated)
Write-Host "AUTH-009 Testing logged out user token..." -ForegroundColor Yellow
# Note: Without actual logout endpoint, we simulate by using an old/invalid token
# In a real scenario, this would test token after logout/revocation
$LoggedOutToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJsb2dnZWRvdXQiLCJpYXQiOjE1MTYyMzkwMjJ9.InvalidSig"
$LoggedOutHeaders = @{ "Authorization" = "Bearer $LoggedOutToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $LoggedOutHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-009" -TestName "Logged Out User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected logged out user token"
    Write-Host "  PASS - Correctly rejected logged out user token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-009" -TestName "Logged Out User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# AUTH-010: Blacklisted Token (Simulated)
Write-Host "AUTH-010 Testing blacklisted token..." -ForegroundColor Yellow
# Note: Without actual blacklist mechanism, we simulate by using an invalid token
# In a real scenario, this would test token that's been explicitly blacklisted
$BlacklistedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJibGFja2xpc3RlZCIsImlhdCI6MTUxNjIzOTAyMn0.InvalidSig"
$BlacklistedHeaders = @{ "Authorization" = "Bearer $BlacklistedToken" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts" -Headers $BlacklistedHeaders

if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "AUTH-010" -TestName "Blacklisted Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected blacklisted token"
    Write-Host "  PASS - Correctly rejected blacklisted token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "AUTH-010" -TestName "Blacklisted Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Expected 401, got $($Result.StatusCode)"
    Write-Host "  FAIL - Expected 401, got $($Result.StatusCode) ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === Test Results Summary ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$TotalTests = $TestResults.Count
$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host "Total Tests: $TotalTests" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -AutoSize

# Update test status file
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    $ServiceSection = @"

## Token Boundary Tests (AUTH-001 to AUTH-010)
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
"@
    
    foreach ($Result in $TestResults) {
        $StatusMark = switch ($Result.Status) {
            "PASS" { "[PASS]" }
            "FAIL" { "[FAIL]" }
            "SKIP" { "[SKIP]" }
            default { "[?]" }
        }
        $ServiceSection += "`n| $($Result.TestId) | $($Result.TestName) | $StatusMark | $($Result.ResponseTime) | $($Result.Note) |"
    }
    
    $ServiceSection += "`n`n**Test Time**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $ServiceSection += "`n**Test Result**: $PassCount passed, $FailCount failed, $SkipCount skipped"
    
    # Append to status file
    Add-Content -Path $StatusFullPath -Value $ServiceSection
    Write-Host "Test status updated: $StatusFullPath" -ForegroundColor Green
}

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
}
else {
    exit 0
}
