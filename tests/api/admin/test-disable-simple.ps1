# Simple test for disable user functionality
# 简单测试禁用用户功能

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json

$UserServiceUrl = $Config.user_service_url
$AdminUser = $Config.admin_user

$Global:AdminAccessToken = ""
$Global:AdminUserId = ""
$Global:TestUserId = ""

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

function Get-AuthHeaders { return @{ "Authorization" = "Bearer $Global:AdminAccessToken" } }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Simple Disable User Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Create admin user
Write-Host "[1] Creating admin user..." -ForegroundColor Yellow
$Timestamp = Get-Date -Format "yyyyMMddHHmmssff"
$AdminUsername = "admin_simple_$Timestamp"
$AdminEmail = "admin_simple_$Timestamp@example.com"

$RegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $AdminUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Admin user created: $Global:AdminUserId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to create admin user: $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

# 2. Login
$LoginBody = @{ email = $AdminEmail; password = $AdminUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminAccessToken = $Result.Body.data.accessToken
    Write-Host "  Admin logged in" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to login: $($Result.Body.message)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 3. Create test user
Write-Host "[2] Creating test user..." -ForegroundColor Yellow
$TestUsername = "testuser_simple_$Timestamp"
$TestEmail = "testuser_simple_$Timestamp@example.com"

$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = "Test123456!" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Test user created: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to create test user: $($Result.Body.message)" -ForegroundColor Red
    Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Red
    Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 4. Test disable directly on ZhiCore-user (without admin role check)
Write-Host "[3] Testing disable user on ZhiCore-user service..." -ForegroundColor Yellow
Write-Host "  URL: $UserServiceUrl/admin/users/$Global:TestUserId/disable" -ForegroundColor Cyan
Write-Host "  Method: POST" -ForegroundColor Cyan
Write-Host "  Headers: Authorization: Bearer <token>" -ForegroundColor Cyan
Write-Host ""

$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/admin/users/$Global:TestUserId/disable" -Headers (Get-AuthHeaders)

Write-Host "  Response:" -ForegroundColor Cyan
Write-Host "    Status Code: $($Result.StatusCode)" -ForegroundColor Cyan
Write-Host "    Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan

if ($Result.Body) {
    Write-Host "    Body:" -ForegroundColor Cyan
    Write-Host "      code: $($Result.Body.code)" -ForegroundColor Cyan
    Write-Host "      message: $($Result.Body.message)" -ForegroundColor Cyan
    if ($Result.Body.data) {
        Write-Host "      data: $($Result.Body.data | ConvertTo-Json -Compress)" -ForegroundColor Cyan
    }
}

if ($Result.Error) {
    Write-Host "    Error: $($Result.Error)" -ForegroundColor Red
}

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host ""
    Write-Host "  [PASS] User disabled successfully" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "  [FAIL] Failed to disable user" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Please check ZhiCore-user service logs for detailed error information" -ForegroundColor Yellow
