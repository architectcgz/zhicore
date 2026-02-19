# Feign Client Diagnostic Script
param([string]$ConfigPath = "../config/test-env.json")

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json

$GatewayUrl = $Config.gateway_url
$UserServiceUrl = $Config.user_service_url

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Feign Client Diagnostic Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

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

Write-Host ""
Write-Host "[STEP 1] Creating test user..." -ForegroundColor Yellow

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TestUsername = "diagtest_$Timestamp"
$TestEmail = "diagtest_$Timestamp@example.com"
$TestPassword = "Test123456!"

$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $UserId = $Result.Body.data
    Write-Host "  [PASS] User registered: $UserId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Registration failed" -ForegroundColor Red
    exit 1
}

$LoginBody = @{ email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $AccessToken = $Result.Body.data.accessToken
    Write-Host "  [PASS] User logged in" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Login failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[STEP 2] Assigning ADMIN role..." -ForegroundColor Yellow

$Headers = @{ "Authorization" = "Bearer $AccessToken" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$UserId/roles/ADMIN" -Headers $Headers

Write-Host "  URL: $UserServiceUrl/api/v1/users/$UserId/roles/ADMIN" -ForegroundColor Cyan
Write-Host "  Status: $($Result.StatusCode)" -ForegroundColor Cyan

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] ADMIN role assigned" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Role assignment failed" -ForegroundColor Red
    if ($Result.Body) {
        Write-Host "  Error: $($Result.Body.message)" -ForegroundColor Red
        Write-Host "  Response: $($Result.Body | ConvertTo-Json)" -ForegroundColor Gray
    } else {
        Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
    }
    Write-Host "  Continuing anyway..." -ForegroundColor Yellow
}

$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $AccessToken = $Result.Body.data.accessToken
    Write-Host "  [PASS] Re-logged in with ADMIN role" -ForegroundColor Green
} else {
    Write-Host "  [WARN] Re-login failed, using old token" -ForegroundColor Yellow
}

$Headers = @{ "Authorization" = "Bearer $AccessToken" }

Write-Host ""
Write-Host "[STEP 3] Testing direct call to blog-user..." -ForegroundColor Yellow

$Result = Invoke-ApiRequest -Method "GET" -Url "$UserServiceUrl/admin/users?page=1&size=10" -Headers $Headers

Write-Host "  URL: $UserServiceUrl/admin/users" -ForegroundColor Cyan
Write-Host "  Status: $($Result.StatusCode)" -ForegroundColor Cyan

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] Direct call works" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Direct call failed: $($Result.Body.message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "[STEP 4] Testing call through gateway..." -ForegroundColor Yellow

$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/admin/users?page=1&size=10" -Headers $Headers

Write-Host "  URL: $GatewayUrl/api/v1/admin/users" -ForegroundColor Cyan
Write-Host "  Status: $($Result.StatusCode)" -ForegroundColor Cyan

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] Gateway call works" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Gateway call failed: $($Result.Body.message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
