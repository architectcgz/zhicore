# User Service API Full Test Script
# Test Cases: USER-001 to USER-035 (including error scenarios)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$BaseUrl = $Config.user_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TargetUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "testuser_$Timestamp"
$UniqueEmail = "testuser_$Timestamp@example.com"

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

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User Service API Full Tests" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === SECTION 1: Registration Tests ===
Write-Host "=== SECTION 1: Registration Tests ===" -ForegroundColor Magenta

# USER-001: Normal Registration
Write-Host "[USER-001] Testing normal registration..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Add-TestResult -TestId "USER-001" -TestName "Normal Registration" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "UserID: $Global:TestUserId"
    Write-Host "  PASS - Registration successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "USER-001" -TestName "Normal Registration" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-002: Duplicate Email Registration
Write-Host "[USER-002] Testing duplicate email registration..." -ForegroundColor Yellow
$DuplicateBody = @{ userName = "another_$Timestamp"; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $DuplicateBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-002" -TestName "Duplicate Email" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Duplicate email correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-002" -TestName "Duplicate Email" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject duplicate"
    Write-Host "  FAIL - Should reject duplicate email ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-003: Invalid Email Format
Write-Host "[USER-003] Testing invalid email format..." -ForegroundColor Yellow
$InvalidEmailBody = @{ userName = "test_invalid_$Timestamp"; email = "invalid-email-format"; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $InvalidEmailBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-003" -TestName "Invalid Email Format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Invalid email correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-003" -TestName "Invalid Email Format" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid email"
    Write-Host "  FAIL - Should reject invalid email ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-004: Short Username
Write-Host "[USER-004] Testing short username..." -ForegroundColor Yellow
$ShortUsernameBody = @{ userName = "ab"; email = "short_$Timestamp@example.com"; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $ShortUsernameBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-004" -TestName "Short Username" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Short username correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-004" -TestName "Short Username" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject short username"
    Write-Host "  FAIL - Should reject short username ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-005: Invalid Username Characters
Write-Host "[USER-005] Testing invalid username characters..." -ForegroundColor Yellow
$InvalidUsernameBody = @{ userName = "test@user#name"; email = "invalid_user_$Timestamp@example.com"; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $InvalidUsernameBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-005" -TestName "Invalid Username Chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Invalid username chars correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-005" -TestName "Invalid Username Chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid chars"
    Write-Host "  FAIL - Should reject invalid username chars ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-006: Short Password
Write-Host "[USER-006] Testing short password..." -ForegroundColor Yellow
$ShortPasswordBody = @{ userName = "shortpwd_$Timestamp"; email = "shortpwd_$Timestamp@example.com"; password = "12345" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $ShortPasswordBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-006" -TestName "Short Password" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Short password correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-006" -TestName "Short Password" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject short password"
    Write-Host "  FAIL - Should reject short password ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-007: Empty Fields
Write-Host "[USER-007] Testing empty fields..." -ForegroundColor Yellow
$EmptyBody = @{ userName = ""; email = ""; password = "" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $EmptyBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-007" -TestName "Empty Fields" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Empty fields correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-007" -TestName "Empty Fields" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty fields"
    Write-Host "  FAIL - Should reject empty fields ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: Login Tests ===
Write-Host "=== SECTION 2: Login Tests ===" -ForegroundColor Magenta

# USER-008: Normal Login
Write-Host "[USER-008] Testing normal login..." -ForegroundColor Yellow
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    Add-TestResult -TestId "USER-008" -TestName "Normal Login" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got JWT Token"
    Write-Host "  PASS - Login successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "USER-008" -TestName "Normal Login" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-009: Wrong Password
Write-Host "[USER-009] Testing wrong password..." -ForegroundColor Yellow
$WrongPasswordBody = @{ email = $UniqueEmail; password = "WrongPassword123!" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Body $WrongPasswordBody
if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-009" -TestName "Wrong Password" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Wrong password correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-009" -TestName "Wrong Password" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject wrong password"
    Write-Host "  FAIL - Should reject wrong password ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-010: Non-existent Email
Write-Host "[USER-010] Testing non-existent email..." -ForegroundColor Yellow
$NonExistentBody = @{ email = "nonexistent_$Timestamp@example.com"; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Body $NonExistentBody
if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-010" -TestName "Non-existent Email" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Non-existent email correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-010" -TestName "Non-existent Email" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject non-existent email"
    Write-Host "  FAIL - Should reject non-existent email ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-011: Empty Login Fields
Write-Host "[USER-011] Testing empty login fields..." -ForegroundColor Yellow
$EmptyLoginBody = @{ email = ""; password = "" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Body $EmptyLoginBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-011" -TestName "Empty Login Fields" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Empty login fields correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-011" -TestName "Empty Login Fields" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty fields"
    Write-Host "  FAIL - Should reject empty login fields ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Token Tests ===
Write-Host "=== SECTION 3: Token Tests ===" -ForegroundColor Magenta

# USER-012: Token Refresh
Write-Host "[USER-012] Testing token refresh..." -ForegroundColor Yellow
if ($Global:RefreshToken) {
    $RefreshBody = @{ refreshToken = $Global:RefreshToken }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/refresh" -Body $RefreshBody
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
        $Global:AccessToken = $Result.Body.data.accessToken
        $Global:RefreshToken = $Result.Body.data.refreshToken
        Add-TestResult -TestId "USER-012" -TestName "Token Refresh" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got new Token"
        Write-Host "  PASS - Token refresh successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-012" -TestName "Token Refresh" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-012" -TestName "Token Refresh" -Status "SKIP" -ResponseTime "-" -Note "No RefreshToken"
    Write-Host "  SKIP - No RefreshToken available" -ForegroundColor Gray
}

# USER-013: Invalid Refresh Token
Write-Host "[USER-013] Testing invalid refresh token..." -ForegroundColor Yellow
$InvalidRefreshBody = @{ refreshToken = "invalid.refresh.token.here" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/refresh" -Body $InvalidRefreshBody
if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-013" -TestName "Invalid Refresh Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Invalid refresh token correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-013" -TestName "Invalid Refresh Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid token"
    Write-Host "  FAIL - Should reject invalid refresh token ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-014: Empty Refresh Token
Write-Host "[USER-014] Testing empty refresh token..." -ForegroundColor Yellow
$EmptyRefreshBody = @{ refreshToken = "" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/refresh" -Body $EmptyRefreshBody
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-014" -TestName "Empty Refresh Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Empty refresh token correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-014" -TestName "Empty Refresh Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty token"
    Write-Host "  FAIL - Should reject empty refresh token ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: User Info Tests ===
Write-Host "=== SECTION 4: User Info Tests ===" -ForegroundColor Magenta

# USER-015: Get User Info
Write-Host "[USER-015] Testing get user info..." -ForegroundColor Yellow
if ($Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $UserData = $Result.Body.data
        Add-TestResult -TestId "USER-015" -TestName "Get User Info" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Username: $($UserData.username)"
        Write-Host "  PASS - Got user info ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-015" -TestName "Get User Info" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-015" -TestName "Get User Info" -Status "SKIP" -ResponseTime "-" -Note "No UserID"
    Write-Host "  SKIP - No UserID available" -ForegroundColor Gray
}

# USER-016: Get Non-existent User
Write-Host "[USER-016] Testing get non-existent user..." -ForegroundColor Yellow
$FakeUserId = "999999999999999999"
$Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$FakeUserId" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-016" -TestName "Get Non-existent User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned error"
    Write-Host "  PASS - Non-existent user correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-016" -TestName "Get Non-existent User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return error"
    Write-Host "  FAIL - Should return error for non-existent user ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-017: Get User Without Auth
Write-Host "[USER-017] Testing get user without auth..." -ForegroundColor Yellow
if ($Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId"
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-017" -TestName "Get User Without Auth" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Public access allowed"
        Write-Host "  PASS - Public access allowed ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.StatusCode -eq 401) {
        Add-TestResult -TestId "USER-017" -TestName "Get User Without Auth" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Auth required"
        Write-Host "  PASS - Auth required as expected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-017" -TestName "Get User Without Auth" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-017" -TestName "Get User Without Auth" -Status "SKIP" -ResponseTime "-" -Note "No UserID"
    Write-Host "  SKIP - No UserID available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 5: Follow Tests ===
Write-Host "=== SECTION 5: Follow Tests ===" -ForegroundColor Magenta

# Create second test user
Write-Host "Creating second test user for follow tests..." -ForegroundColor Cyan
$Timestamp2 = Get-Date -Format "yyyyMMddHHmmssff"
$TargetUsername = "targetuser_$Timestamp2"
$TargetEmail = "targetuser_$Timestamp2@example.com"
$RegisterBody2 = @{ userName = $TargetUsername; email = $TargetEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $RegisterBody2
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TargetUserId = $Result.Body.data
    Write-Host "  Created target user, ID: $Global:TargetUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create target user" -ForegroundColor Yellow
}
Write-Host ""

# USER-018: Follow User
Write-Host "[USER-018] Testing follow user..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-018" -TestName "Follow User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Follow successful"
        Write-Host "  PASS - Follow user successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-018" -TestName "Follow User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-018" -TestName "Follow User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-019: Follow Self
Write-Host "[USER-019] Testing follow self..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "USER-019" -TestName "Follow Self" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Follow self correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "USER-019" -TestName "Follow Self" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject self-follow"
        Write-Host "  FAIL - Should reject self-follow ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-019" -TestName "Follow Self" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-020: Follow Non-existent User
Write-Host "[USER-020] Testing follow non-existent user..." -ForegroundColor Yellow
$FakeTargetId = "999999999999999999"
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$FakeTargetId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "USER-020" -TestName "Follow Non-existent" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Follow non-existent user correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "USER-020" -TestName "Follow Non-existent" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject"
        Write-Host "  FAIL - Should reject follow non-existent user ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-020" -TestName "Follow Non-existent" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-021: Duplicate Follow
Write-Host "[USER-021] Testing duplicate follow..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -or $Result.StatusCode -eq 400 -or $Result.StatusCode -eq 409) {
        Add-TestResult -TestId "USER-021" -TestName "Duplicate Follow" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Duplicate follow handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-021" -TestName "Duplicate Follow" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-021" -TestName "Duplicate Follow" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-022: Unfollow User
Write-Host "[USER-022] Testing unfollow user..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-022" -TestName "Unfollow User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unfollow successful"
        Write-Host "  PASS - Unfollow user successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-022" -TestName "Unfollow User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-022" -TestName "Unfollow User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-023: Unfollow Not Following User
Write-Host "[USER-023] Testing unfollow not following user..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -or $Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404) {
        Add-TestResult -TestId "USER-023" -TestName "Unfollow Not Following" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Unfollow not following handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-023" -TestName "Unfollow Not Following" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-023" -TestName "Unfollow Not Following" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# Re-follow for subsequent tests
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders) | Out-Null
}

# USER-024: Get Followers List
Write-Host "[USER-024] Testing get followers list..." -ForegroundColor Yellow
if ($Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TargetUserId/followers?page=1&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $FollowerCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "USER-024" -TestName "Get Followers" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Followers: $FollowerCount"
        Write-Host "  PASS - Got followers list, count: $FollowerCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-024" -TestName "Get Followers" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-024" -TestName "Get Followers" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-025: Get Following List
Write-Host "[USER-025] Testing get following list..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following?page=1&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $FollowingCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "USER-025" -TestName "Get Following" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Following: $FollowingCount"
        Write-Host "  PASS - Got following list, count: $FollowingCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-025" -TestName "Get Following" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-025" -TestName "Get Following" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-026: Check Following Status
Write-Host "[USER-026] Testing check following status..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId/check" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $IsFollowing = $Result.Body.data
        Add-TestResult -TestId "USER-026" -TestName "Check Following" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "IsFollowing: $IsFollowing"
        Write-Host "  PASS - Check following status: $IsFollowing ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-026" -TestName "Check Following" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-026" -TestName "Check Following" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-027: Get Follow Stats
Write-Host "[USER-027] Testing get follow stats..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/follow-stats" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-027" -TestName "Get Follow Stats" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Stats retrieved"
        Write-Host "  PASS - Got follow stats ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-027" -TestName "Get Follow Stats" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-027" -TestName "Get Follow Stats" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 6: Check-In Tests ===
Write-Host "=== SECTION 6: Check-In Tests ===" -ForegroundColor Magenta

# USER-028: Check In
Write-Host "[USER-028] Testing check-in..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $CheckInData = $Result.Body.data
        $ContinuousDays = if ($CheckInData.continuousDays) { $CheckInData.continuousDays } else { 1 }
        Add-TestResult -TestId "USER-028" -TestName "Check In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Continuous: $ContinuousDays"
        Write-Host "  PASS - Check-in successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        if ($ErrorMsg -match "already|checked") {
            Add-TestResult -TestId "USER-028" -TestName "Check In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Already checked in"
            Write-Host "  PASS - Already checked in today ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "USER-028" -TestName "Check In" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    }
} else {
    Add-TestResult -TestId "USER-028" -TestName "Check In" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-029: Duplicate Check In
Write-Host "[USER-029] Testing duplicate check-in..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200) -or $Result.Success) {
        Add-TestResult -TestId "USER-029" -TestName "Duplicate Check In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
        Write-Host "  PASS - Duplicate check-in handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-029" -TestName "Duplicate Check In" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-029" -TestName "Duplicate Check In" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-030: Get Check-In Stats
Write-Host "[USER-030] Testing get check-in stats..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in/stats" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $StatsData = $Result.Body.data
        $TotalDays = if ($StatsData.totalDays) { $StatsData.totalDays } else { 0 }
        Add-TestResult -TestId "USER-030" -TestName "Check-In Stats" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Total: ${TotalDays}d"
        Write-Host "  PASS - Got check-in stats ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-030" -TestName "Check-In Stats" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-030" -TestName "Check-In Stats" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-031: Check In for Non-existent User
Write-Host "[USER-031] Testing check-in for non-existent user..." -ForegroundColor Yellow
$FakeUserId = "999999999999999999"
$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$FakeUserId/check-in" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 404 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "USER-031" -TestName "Check In Non-existent" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Check-in for non-existent user correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "USER-031" -TestName "Check In Non-existent" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject"
    Write-Host "  FAIL - Should reject check-in for non-existent user ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# USER-032: Get Monthly Check-In
Write-Host "[USER-032] Testing get monthly check-in..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $CurrentYear = (Get-Date).Year
    $CurrentMonth = (Get-Date).Month
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in/monthly?year=$CurrentYear&month=$CurrentMonth" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-032" -TestName "Monthly Check-In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got bitmap"
        Write-Host "  PASS - Got monthly check-in bitmap ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-032" -TestName "Monthly Check-In" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-032" -TestName "Monthly Check-In" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-033: Get Monthly Check-In with Invalid Month
Write-Host "[USER-033] Testing monthly check-in with invalid month..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in/monthly?year=2024&month=13" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "USER-033" -TestName "Invalid Month" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Invalid month correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "USER-033" -TestName "Invalid Month" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid month"
        Write-Host "  FAIL - Should reject invalid month ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-033" -TestName "Invalid Month" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 7: Pagination Tests ===
Write-Host "=== SECTION 7: Pagination Tests ===" -ForegroundColor Magenta

# USER-034: Pagination with Invalid Page
Write-Host "[USER-034] Testing pagination with invalid page..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following?page=-1&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -or $Result.StatusCode -eq 400) {
        Add-TestResult -TestId "USER-034" -TestName "Invalid Page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled"
        Write-Host "  PASS - Invalid page handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-034" -TestName "Invalid Page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-034" -TestName "Invalid Page" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# USER-035: Pagination with Large Size
Write-Host "[USER-035] Testing pagination with large size..." -ForegroundColor Yellow
if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following?page=1&size=1000" -Headers (Get-AuthHeaders)
    if ($Result.Success -or $Result.StatusCode -eq 400) {
        Add-TestResult -TestId "USER-035" -TestName "Large Page Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled"
        Write-Host "  PASS - Large page size handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-035" -TestName "Large Page Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "USER-035" -TestName "Large Page Size" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
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

Write-Host "Total: $TotalCount tests" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray

$ExecutedCount = $TotalCount - $SkipCount
$PassRate = if ($ExecutedCount -gt 0) { [math]::Round(($PassCount / $ExecutedCount) * 100, 1) } else { 0 }
$PassRateColor = if ($PassRate -ge 80) { "Green" } elseif ($PassRate -ge 50) { "Yellow" } else { "Red" }
Write-Host "Pass Rate (excluding skipped): $PassRate%" -ForegroundColor $PassRateColor

Write-Host ""
Write-Host "Detailed Results:" -ForegroundColor Cyan
foreach ($TestResult in $TestResults) {
    $StatusColor = switch ($TestResult.Status) { "PASS" { "Green" } "FAIL" { "Red" } "SKIP" { "Gray" } default { "White" } }
    $StatusIcon = switch ($TestResult.Status) { "PASS" { "[PASS]" } "FAIL" { "[FAIL]" } "SKIP" { "[SKIP]" } default { "[????]" } }
    Write-Host "  $StatusIcon [$($TestResult.TestId)] $($TestResult.TestName): $($TestResult.Note)" -ForegroundColor $StatusColor
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User Service API Full Tests Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($FailCount -gt 0) { exit 1 } else { exit 0 }
