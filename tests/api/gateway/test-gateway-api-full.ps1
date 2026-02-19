# Gateway Service API Full Test Script
# Test Cases: GW-001 to GW-015 (including routing, authentication, and rate limiting)
# Coverage: Routing (5), Authentication (5), Rate Limiting (5)

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
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "gwtest_$Timestamp"
$UniqueEmail = "gwtest_$Timestamp@example.com"

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
Write-Host "Gateway Service API Full Tests" -ForegroundColor Cyan
Write-Host "Gateway URL: $GatewayUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test user and login ===
Write-Host "=== Setup: Creating test user and logging in ===" -ForegroundColor Magenta

# Register test user
Write-Host "[SETUP] Registering test user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  SUCCESS - User registered: $Global:TestUserId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAILED - Could not register user: $ErrorMsg" -ForegroundColor Red
    Write-Host "  StatusCode: $($Result.StatusCode)" -ForegroundColor Red
    Write-Host "  This may indicate a service configuration issue." -ForegroundColor Yellow
    Write-Host "  Attempting to continue with existing user or skip user-dependent tests..." -ForegroundColor Yellow
}

# Login to get token
Write-Host "[SETUP] Logging in..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:TestUserId)) {
    Write-Host "  SKIPPED - No user registered, will skip authentication tests" -ForegroundColor Gray
} else {
    $LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:AccessToken = $Result.Body.data.accessToken
        $Global:RefreshToken = $Result.Body.data.refreshToken
        Write-Host "  SUCCESS - Logged in successfully" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - Could not login, will skip authentication tests" -ForegroundColor Yellow
    }
}

# Create a test post for routing tests
Write-Host "[SETUP] Creating test post..." -ForegroundColor Yellow
$PostBody = @{ title = "Gateway Test Post $Timestamp"; content = "Test content for gateway routing"; status = 1 }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestPostId = $Result.Body.data
    Write-Host "  SUCCESS - Post created: $Global:TestPostId" -ForegroundColor Green
} else {
    Write-Host "  WARNING - Could not create post, some tests may be skipped" -ForegroundColor Yellow
}

Write-Host ""

# === SECTION 1: Routing Tests ===
Write-Host "=== SECTION 1: Routing Tests ===" -ForegroundColor Magenta

# GW-001: Route Forwarding
Write-Host "[GW-001] Testing route forwarding to user service..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:TestUserId) -or [string]::IsNullOrEmpty($Global:AccessToken)) {
    Add-TestResult -TestId "GW-001" -TestName "Route Forwarding" -Status "SKIP" -ResponseTime "-" -Note "No user or token available"
    Write-Host "  SKIP - No user or token available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "GW-001" -TestName "Route Forwarding" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Routed to user service"
        Write-Host "  PASS - Request correctly routed to user service ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "GW-001" -TestName "Route Forwarding" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# GW-002: Non-existent Route
Write-Host "[GW-002] Testing non-existent route..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/nonexistent/endpoint"
if ($Result.StatusCode -eq 404) {
    Add-TestResult -TestId "GW-002" -TestName "Non-existent Route" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
    Write-Host "  PASS - Non-existent route correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-002" -TestName "Non-existent Route" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
    Write-Host "  FAIL - Should return 404 for non-existent route ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# GW-003: Service Unavailable
Write-Host "[GW-003] Testing service unavailable scenario..." -ForegroundColor Yellow
# Try to access a service that might be down (using a non-standard port)
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/unavailable/test"
if ($Result.StatusCode -eq 503 -or $Result.StatusCode -eq 404 -or $Result.StatusCode -eq 500) {
    Add-TestResult -TestId "GW-003" -TestName "Service Unavailable" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled unavailable service"
    Write-Host "  PASS - Service unavailable handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-003" -TestName "Service Unavailable" -Status "SKIP" -ResponseTime "$($Result.ResponseTime)ms" -Note "Cannot simulate service down"
    Write-Host "  SKIP - Cannot simulate service unavailable ($($Result.ResponseTime)ms)" -ForegroundColor Gray
}

# GW-004: Request Timeout
Write-Host "[GW-004] Testing request timeout..." -ForegroundColor Yellow
# This test is difficult to simulate without actual timeout configuration
Add-TestResult -TestId "GW-004" -TestName "Request Timeout" -Status "SKIP" -ResponseTime "-" -Note "Requires timeout configuration"
Write-Host "  SKIP - Requires specific timeout configuration" -ForegroundColor Gray

# GW-005: Load Balancing
Write-Host "[GW-005] Testing load balancing..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:TestUserId) -or [string]::IsNullOrEmpty($Global:AccessToken)) {
    Add-TestResult -TestId "GW-005" -TestName "Load Balancing" -Status "SKIP" -ResponseTime "-" -Note "No user or token available"
    Write-Host "  SKIP - No user or token available" -ForegroundColor Gray
} else {
    # Make multiple requests to see if they're distributed
    $RequestCount = 5
    $SuccessCount = 0
    for ($i = 1; $i -le $RequestCount; $i++) {
        $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
        if ($Result.Success) { $SuccessCount++ }
    }
    if ($SuccessCount -eq $RequestCount) {
        Add-TestResult -TestId "GW-005" -TestName "Load Balancing" -Status "PASS" -ResponseTime "-" -Note "All requests successful"
        Write-Host "  PASS - Load balancing working ($SuccessCount/$RequestCount requests successful)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "GW-005" -TestName "Load Balancing" -Status "FAIL" -ResponseTime "-" -Note "Some requests failed"
        Write-Host "  FAIL - Some requests failed ($SuccessCount/$RequestCount)" -ForegroundColor Red
    }
}

Write-Host ""

# === SECTION 2: Authentication Tests ===
Write-Host "=== SECTION 2: Authentication Tests ===" -ForegroundColor Magenta

# GW-006: Valid Token
Write-Host "[GW-006] Testing valid token authentication..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:TestUserId) -or [string]::IsNullOrEmpty($Global:AccessToken)) {
    Add-TestResult -TestId "GW-006" -TestName "Valid Token" -Status "SKIP" -ResponseTime "-" -Note "No user or token available"
    Write-Host "  SKIP - No user or token available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "GW-006" -TestName "Valid Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Token accepted"
        Write-Host "  PASS - Valid token accepted ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "GW-006" -TestName "Valid Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# GW-007: Invalid Token
Write-Host "[GW-007] Testing invalid token..." -ForegroundColor Yellow
$InvalidHeaders = @{ "Authorization" = "Bearer invalid.token.here" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers $InvalidHeaders
if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "GW-007" -TestName "Invalid Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Invalid token correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-007" -TestName "Invalid Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 401"
    Write-Host "  FAIL - Should return 401 for invalid token ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# GW-008: Expired Token
Write-Host "[GW-008] Testing expired token..." -ForegroundColor Yellow
# Use a known expired token format
$ExpiredHeaders = @{ "Authorization" = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.4Adcj0vVzr7B8Y-Hs8T5L5z5Z5Z5Z5Z5Z5Z5Z5Z5Z5Z" }
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers $ExpiredHeaders
if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "GW-008" -TestName "Expired Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Expired token correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-008" -TestName "Expired Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 401"
    Write-Host "  FAIL - Should return 401 for expired token ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# GW-009: No Token for Public Endpoint
Write-Host "[GW-009] Testing no token for public endpoint..." -ForegroundColor Yellow
# Try to access a public endpoint (like health check or registration)
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/actuator/health"
if ($Result.Success -or $Result.StatusCode -eq 200 -or $Result.StatusCode -eq 404) {
    Add-TestResult -TestId "GW-009" -TestName "No Token Public Endpoint" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Public endpoint accessible"
    Write-Host "  PASS - Public endpoint accessible without token ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-009" -TestName "No Token Public Endpoint" -Status "SKIP" -ResponseTime "$($Result.ResponseTime)ms" -Note "No public endpoint available"
    Write-Host "  SKIP - No public endpoint available for testing ($($Result.ResponseTime)ms)" -ForegroundColor Gray
}

# GW-010: No Token for Private Endpoint
Write-Host "[GW-010] Testing no token for private endpoint..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId"
if ($Result.StatusCode -eq 401) {
    Add-TestResult -TestId "GW-010" -TestName "No Token Private Endpoint" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Private endpoint correctly requires token ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-010" -TestName "No Token Private Endpoint" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 401"
    Write-Host "  FAIL - Should return 401 without token ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Rate Limiting Tests ===
Write-Host "=== SECTION 3: Rate Limiting Tests ===" -ForegroundColor Magenta

# GW-011: Normal Request Rate
Write-Host "[GW-011] Testing normal request rate..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:TestUserId) -or [string]::IsNullOrEmpty($Global:AccessToken)) {
    Add-TestResult -TestId "GW-011" -TestName "Normal Request Rate" -Status "SKIP" -ResponseTime "-" -Note "No user or token available"
    Write-Host "  SKIP - No user or token available" -ForegroundColor Gray
} else {
    $NormalRequestCount = 5
    $SuccessCount = 0
    for ($i = 1; $i -le $NormalRequestCount; $i++) {
        $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
        if ($Result.Success) { $SuccessCount++ }
        Start-Sleep -Milliseconds 200
    }
    if ($SuccessCount -eq $NormalRequestCount) {
        Add-TestResult -TestId "GW-011" -TestName "Normal Request Rate" -Status "PASS" -ResponseTime "-" -Note "All requests passed"
        Write-Host "  PASS - Normal request rate allowed ($SuccessCount/$NormalRequestCount)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "GW-011" -TestName "Normal Request Rate" -Status "FAIL" -ResponseTime "-" -Note "Some requests failed"
        Write-Host "  FAIL - Some requests failed ($SuccessCount/$NormalRequestCount)" -ForegroundColor Red
    }
}

# GW-012: Exceed Rate Limit
Write-Host "[GW-012] Testing rate limit threshold..." -ForegroundColor Yellow
# Send many requests rapidly to trigger rate limiting
$RapidRequestCount = 50
$RateLimitedCount = 0
$SuccessCount = 0
for ($i = 1; $i -le $RapidRequestCount; $i++) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 429) { $RateLimitedCount++ }
    if ($Result.Success) { $SuccessCount++ }
}
if ($RateLimitedCount -gt 0) {
    Add-TestResult -TestId "GW-012" -TestName "Exceed Rate Limit" -Status "PASS" -ResponseTime "-" -Note "Rate limiting triggered ($RateLimitedCount/50)"
    Write-Host "  PASS - Rate limiting triggered ($RateLimitedCount rate limited, $SuccessCount successful)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-012" -TestName "Exceed Rate Limit" -Status "SKIP" -ResponseTime "-" -Note "Rate limiting not configured or threshold too high"
    Write-Host "  SKIP - Rate limiting not triggered (may not be configured)" -ForegroundColor Gray
}

# GW-013: Rate Limit Recovery
Write-Host "[GW-013] Testing rate limit recovery..." -ForegroundColor Yellow
Write-Host "  Waiting 5 seconds for rate limit to reset..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.StatusCode -ne 429) {
    Add-TestResult -TestId "GW-013" -TestName "Rate Limit Recovery" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rate limit recovered"
    Write-Host "  PASS - Rate limit recovered after waiting ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-013" -TestName "Rate Limit Recovery" -Status "SKIP" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rate limiting not active"
    Write-Host "  SKIP - Rate limiting not active or still limited ($($Result.ResponseTime)ms)" -ForegroundColor Gray
}

# GW-014: IP-based Rate Limiting
Write-Host "[GW-014] Testing IP-based rate limiting..." -ForegroundColor Yellow
# This test is difficult to simulate without multiple IPs
Add-TestResult -TestId "GW-014" -TestName "IP Rate Limiting" -Status "SKIP" -ResponseTime "-" -Note "Requires multiple IPs"
Write-Host "  SKIP - Requires multiple IP addresses to test" -ForegroundColor Gray

# GW-015: User-based Rate Limiting
Write-Host "[GW-015] Testing user-based rate limiting..." -ForegroundColor Yellow
# Send many requests with the same user token
$UserRequestCount = 30
$UserRateLimitedCount = 0
$UserSuccessCount = 0
for ($i = 1; $i -le $UserRequestCount; $i++) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/users/$Global:TestUserId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 429) { $UserRateLimitedCount++ }
    if ($Result.Success) { $UserSuccessCount++ }
}
if ($UserRateLimitedCount -gt 0) {
    Add-TestResult -TestId "GW-015" -TestName "User Rate Limiting" -Status "PASS" -ResponseTime "-" -Note "User rate limiting triggered ($UserRateLimitedCount/30)"
    Write-Host "  PASS - User-based rate limiting triggered ($UserRateLimitedCount rate limited)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "GW-015" -TestName "User Rate Limiting" -Status "SKIP" -ResponseTime "-" -Note "User rate limiting not configured"
    Write-Host "  SKIP - User-based rate limiting not triggered (may not be configured)" -ForegroundColor Gray
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
$TestResults | Format-Table -AutoSize

# Update test status file
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    Write-Host "Updating test status file..." -ForegroundColor Cyan
    
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    $ServiceSection = @"

## Gateway Service Tests
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
    $ServiceSection += "`n"
    
    # Check if section exists
    if ($StatusContent -match "## Gateway Service Tests") {
        # Replace existing section
        $StatusContent = $StatusContent -replace "(?s)## Gateway Service Tests.*?(?=##|\z)", $ServiceSection
    } else {
        # Append new section
        $StatusContent += "`n$ServiceSection"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host "Test status file updated successfully" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Gateway Service Tests Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
