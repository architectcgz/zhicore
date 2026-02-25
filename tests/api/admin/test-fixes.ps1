# Quick Test for ADMIN-003, ADMIN-004, ADMIN-013 Fixes
# Test Cases: Verify the fixes for user enable/disable and post status filter

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$GatewayUrl = $Config.gateway_url
$UserServiceUrl = $Config.user_service_url

# === Global Variables ===
$Global:AccessToken = ""
$Global:AdminUserId = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$AdminUsername = "admin_$Timestamp"
$AdminEmail = "admin_$Timestamp@example.com"
$TestUsername = "testuser_$Timestamp"
$TestEmail = "testuser_$Timestamp@example.com"
$Password = "Test123456!"

# === Utility Functions ===
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
Write-Host "Testing Fixes for ADMIN-003, ADMIN-004, ADMIN-013" -ForegroundColor Cyan
Write-Host "Gateway URL: $GatewayUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create Admin User ===
Write-Host "=== Setup: Creating Admin User ===" -ForegroundColor Magenta
Write-Host ""

# Register admin user
Write-Host "[SETUP-01] Registering admin user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminUserId = $Result.Body.data
    Write-Host "  SUCCESS - Admin registered: userId=$Global:AdminUserId ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# Login admin user
Write-Host "[SETUP-02] Logging in admin user..." -ForegroundColor Yellow
$LoginBody = @{ email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  SUCCESS - Admin logged in ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# Assign ADMIN role
Write-Host "[SETUP-03] Assigning ADMIN role..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:AdminUserId/roles/ADMIN" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  SUCCESS - ADMIN role assigned ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# Re-login to get new token with ADMIN role
Write-Host "[SETUP-04] Re-logging in to get new token with ADMIN role..." -ForegroundColor Yellow
$LoginBody = @{ email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  SUCCESS - Admin re-logged in with ADMIN role ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# === Setup: Create Test User ===
Write-Host ""
Write-Host "=== Setup: Creating Test User ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[SETUP-05] Registering test user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  SUCCESS - Test user registered: userId=$Global:TestUserId ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# === Test ADMIN-003: Disable User ===
Write-Host ""
Write-Host "=== Test ADMIN-003: Disable User ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[ADMIN-003] Testing disable user..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$GatewayUrl/api/v1/admin/users/$Global:TestUserId/disable" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  PASS - User disabled successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === Test ADMIN-004: Enable User ===
Write-Host ""
Write-Host "=== Test ADMIN-004: Enable User ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[ADMIN-004] Testing enable user..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$GatewayUrl/api/v1/admin/users/$Global:TestUserId/enable" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  PASS - User enabled successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === Test ADMIN-013: Filter Posts by Status ===
Write-Host ""
Write-Host "=== Test ADMIN-013: Filter Posts by Status ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[ADMIN-013] Testing filter posts by status=PUBLISHED..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/admin/posts?status=PUBLISHED&page=0&size=20" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  PASS - Posts filtered by status successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "  Total posts: $($Result.Body.data.total)" -ForegroundColor Gray
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
