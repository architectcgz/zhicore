# User Service API Test Script
# Test Cases: USER-001 to USER-010
# Requirements: 1.1-1.10

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$StatusFullPath = Join-Path $ScriptDir $StatusPath

# Load configuration
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$BaseUrl = $Config.user_service_url
$TestUser = $Config.test_user

# Test results storage
$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TargetUserId = ""

# Generate unique username
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "testuser_$Timestamp"
$UniqueEmail = "testuser_$Timestamp@example.com"

# Helper function: Record test result
function Add-TestResult {
    param(
        [string]$TestId,
        [string]$TestName,
        [string]$Status,
        [string]$ResponseTime,
        [string]$Note
    )
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId
        TestName = $TestName
        Status = $Status
        ResponseTime = $ResponseTime
        ExecutionTime = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
        Note = $Note
    }
}


# Helper function: Send HTTP request
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )
    
    $StartTime = Get-Date
    $Result = @{
        Success = $false
        StatusCode = 0
        Body = $null
        ResponseTime = 0
        Error = ""
    }
    
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
        $EndTime = Get-Date
        
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
        $Result.ResponseTime = [math]::Round(($EndTime - $StartTime).TotalMilliseconds)
    }
    catch {
        $EndTime = Get-Date
        $Result.ResponseTime = [math]::Round(($EndTime - $StartTime).TotalMilliseconds)
        
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
    
    return $Result
}

# Helper function: Get auth headers
function Get-AuthHeaders {
    return @{
        "Authorization" = "Bearer $Global:AccessToken"
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User Service API Tests" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""


# ============================================
# USER-001: User Registration
# ============================================
Write-Host "[USER-001] Testing user registration..." -ForegroundColor Yellow

$RegisterBody = @{
    userName = $UniqueUsername
    email = $UniqueEmail
    password = $TestUser.password
}

$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Add-TestResult -TestId "USER-001" -TestName "User Registration" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "UserID: $Global:TestUserId"
    Write-Host "  PASS - Registration successful, ID: $Global:TestUserId ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "USER-001" -TestName "User Registration" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# ============================================
# USER-002: User Login
# ============================================
Write-Host "[USER-002] Testing user login..." -ForegroundColor Yellow

$LoginBody = @{
    email = $UniqueEmail
    password = $TestUser.password
}

$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    Add-TestResult -TestId "USER-002" -TestName "User Login" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got JWT Token"
    Write-Host "  PASS - Login successful, got Token ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "USER-002" -TestName "User Login" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# ============================================
# USER-003: Token Refresh
# ============================================
Write-Host "[USER-003] Testing token refresh..." -ForegroundColor Yellow

if ($Global:RefreshToken) {
    $RefreshBody = @{
        refreshToken = $Global:RefreshToken
    }
    
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/refresh" -Body $RefreshBody
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
        $Global:AccessToken = $Result.Body.data.accessToken
        $Global:RefreshToken = $Result.Body.data.refreshToken
        Add-TestResult -TestId "USER-003" -TestName "Token Refresh" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got new Token"
        Write-Host "  PASS - Token refresh successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-003" -TestName "Token Refresh" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-003" -TestName "Token Refresh" -Status "SKIP" -ResponseTime "-" -Note "No RefreshToken, skipped"
    Write-Host "  SKIP - No RefreshToken, skipped" -ForegroundColor Gray
}

# ============================================
# USER-004: Get User Info
# ============================================
Write-Host "[USER-004] Testing get user info..." -ForegroundColor Yellow

if ($Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $UserData = $Result.Body.data
        Add-TestResult -TestId "USER-004" -TestName "Get User Info" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Username: $($UserData.username)"
        Write-Host "  PASS - Got user info, username: $($UserData.username) ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-004" -TestName "Get User Info" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-004" -TestName "Get User Info" -Status "SKIP" -ResponseTime "-" -Note "No UserID, skipped"
    Write-Host "  SKIP - No UserID, skipped" -ForegroundColor Gray
}


# Create second test user for follow tests
Write-Host ""
Write-Host "Creating second test user for follow tests..." -ForegroundColor Cyan

$Timestamp2 = Get-Date -Format "yyyyMMddHHmmssff"
$TargetUsername = "targetuser_$Timestamp2"
$TargetEmail = "targetuser_$Timestamp2@example.com"

$RegisterBody2 = @{
    userName = $TargetUsername
    email = $TargetEmail
    password = $TestUser.password
}

$Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body $RegisterBody2

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TargetUserId = $Result.Body.data
    Write-Host "  Created target user, ID: $Global:TargetUserId" -ForegroundColor Cyan
}
else {
    Write-Host "  Failed to create target user, follow tests may be skipped" -ForegroundColor Yellow
}

Write-Host ""

# ============================================
# USER-005: Follow User
# ============================================
Write-Host "[USER-005] Testing follow user..." -ForegroundColor Yellow

if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-005" -TestName "Follow User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Follow successful"
        Write-Host "  PASS - Follow user successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-005" -TestName "Follow User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-005" -TestName "Follow User" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}

# ============================================
# USER-006: Unfollow User
# ============================================
Write-Host "[USER-006] Testing unfollow user..." -ForegroundColor Yellow

if ($Global:TestUserId -and $Global:TargetUserId -and $Global:AccessToken) {
    # Ensure already following
    Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders) | Out-Null
    
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "USER-006" -TestName "Unfollow User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unfollow successful"
        Write-Host "  PASS - Unfollow user successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-006" -TestName "Unfollow User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-006" -TestName "Unfollow User" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}

# ============================================
# USER-007: Get Followers List
# ============================================
Write-Host "[USER-007] Testing get followers list..." -ForegroundColor Yellow

if ($Global:TargetUserId -and $Global:AccessToken) {
    # First follow the target user so they have a follower
    Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders) | Out-Null
    
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TargetUserId/followers?page=1&size=20" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $FollowerCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "USER-007" -TestName "Get Followers" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Followers: $FollowerCount"
        Write-Host "  PASS - Got followers list, count: $FollowerCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-007" -TestName "Get Followers" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-007" -TestName "Get Followers" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}

# ============================================
# USER-008: Get Following List
# ============================================
Write-Host "[USER-008] Testing get following list..." -ForegroundColor Yellow

if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/following?page=1&size=20" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $FollowingCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "USER-008" -TestName "Get Following" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Following: $FollowingCount"
        Write-Host "  PASS - Got following list, count: $FollowingCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-008" -TestName "Get Following" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-008" -TestName "Get Following" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}


# ============================================
# USER-009: Check In
# ============================================
Write-Host "[USER-009] Testing check-in..." -ForegroundColor Yellow

if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $CheckInData = $Result.Body.data
        $ContinuousDays = if ($CheckInData.continuousDays) { $CheckInData.continuousDays } else { 1 }
        $Note = "Continuous days: $ContinuousDays"
        Add-TestResult -TestId "USER-009" -TestName "Check In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note $Note
        Write-Host "  PASS - Check-in successful, $Note ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        # If already checked in today, also consider it as pass
        if ($ErrorMsg -match "already|checked") {
            Add-TestResult -TestId "USER-009" -TestName "Check In" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Already checked in today"
            Write-Host "  PASS - Already checked in today ($($Result.ResponseTime)ms)" -ForegroundColor Green
        }
        else {
            Add-TestResult -TestId "USER-009" -TestName "Check In" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    }
}
else {
    Add-TestResult -TestId "USER-009" -TestName "Check In" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}

# ============================================
# USER-010: Get Check-In Status
# ============================================
Write-Host "[USER-010] Testing get check-in status..." -ForegroundColor Yellow

if ($Global:TestUserId -and $Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$BaseUrl/api/v1/users/$Global:TestUserId/check-in/stats" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $StatsData = $Result.Body.data
        $TotalDays = if ($StatsData.totalDays) { $StatsData.totalDays } else { 0 }
        $ContinuousDays = if ($StatsData.continuousDays) { $StatsData.continuousDays } else { 0 }
        $Note = "Total: ${TotalDays}d, Continuous: ${ContinuousDays}d"
        Add-TestResult -TestId "USER-010" -TestName "Check-In Status" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note $Note
        Write-Host "  PASS - Got check-in status, $Note ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "USER-010" -TestName "Check-In Status" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}
else {
    Add-TestResult -TestId "USER-010" -TestName "Check-In Status" -Status "SKIP" -ResponseTime "-" -Note "Missing params, skipped"
    Write-Host "  SKIP - Missing params, skipped" -ForegroundColor Gray
}

# ============================================
# Test Results Summary
# ============================================
Write-Host ""
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

$PassRate = if ($TotalCount -gt 0) { [math]::Round(($PassCount / $TotalCount) * 100, 1) } else { 0 }
$PassRateColor = if ($PassRate -ge 80) { "Green" } elseif ($PassRate -ge 50) { "Yellow" } else { "Red" }
Write-Host "Pass Rate: $PassRate%" -ForegroundColor $PassRateColor

# Output detailed results
Write-Host ""
Write-Host "Detailed Results:" -ForegroundColor Cyan
foreach ($TestResult in $TestResults) {
    $StatusColor = switch ($TestResult.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    $StatusIcon = switch ($TestResult.Status) {
        "PASS" { "[PASS]" }
        "FAIL" { "[FAIL]" }
        "SKIP" { "[SKIP]" }
        default { "[????]" }
    }
    Write-Host "  $StatusIcon [$($TestResult.TestId)] $($TestResult.TestName): $($TestResult.Note)" -ForegroundColor $StatusColor
}


# Update test status file
Write-Host ""
Write-Host "Updating test status file..." -ForegroundColor Cyan

try {
    $StatusContent = Get-Content $StatusFullPath -Raw -Encoding UTF8
    $UpdateTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    
    # Update last update time
    $StatusContent = $StatusContent -replace "Last update time: .*", "Last update time: $UpdateTime"
    
    # Update user service test summary
    $StatusContent = $StatusContent -replace "\| User Service \| 10 \| \d+ \| \d+ \| \d+ \| \d+% \|", "| User Service | 10 | $PassCount | $FailCount | $SkipCount | $PassRate% |"
    
    # Update each test case status
    foreach ($TestResult in $TestResults) {
        $StatusEmoji = switch ($TestResult.Status) {
            "PASS" { "PASS" }
            "FAIL" { "FAIL" }
            "SKIP" { "SKIP" }
            default { "PENDING" }
        }
        
        $OldPattern = "\| $($TestResult.TestId) \| [^|]+ \| [^|]+ \| [^|]+ \| [^|]+ \| [^|]+ \|"
        $NewValue = "| $($TestResult.TestId) | $($TestResult.TestName) | $StatusEmoji | $($TestResult.ResponseTime) | $($TestResult.ExecutionTime) | $($TestResult.Note) |"
        
        $StatusContent = $StatusContent -replace $OldPattern, $NewValue
    }
    
    $StatusContent | Set-Content $StatusFullPath -Encoding UTF8
    Write-Host "Test status file updated: $StatusFullPath" -ForegroundColor Green
}
catch {
    Write-Host "Failed to update test status file: $_" -ForegroundColor Red
}

# Return test results
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User Service API Tests Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Return exit code
if ($FailCount -gt 0) {
    exit 1
}
else {
    exit 0
}
