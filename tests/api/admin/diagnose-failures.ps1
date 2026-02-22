# Diagnose Admin API Failures
# 诊断管理后台 API 失败原因

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json

$GatewayUrl = $Config.gateway_url
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url
$LeafServiceUrl = $Config.leaf_service_url
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
Write-Host "Diagnosing Admin API Failures" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check Leaf Service
Write-Host "[1] Checking Leaf Service..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$LeafServiceUrl/api/v1/leaf/id"
if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] Leaf service is working: $($Result.Body.data)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Leaf service failed: $($Result.Body.message)" -ForegroundColor Red
    Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
}
Write-Host ""

# 2. Setup: Create admin user and login
Write-Host "[2] Setting up admin user..." -ForegroundColor Yellow
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$AdminUsername = "admin_diag_$Timestamp"
$AdminEmail = "admin_diag_$Timestamp@example.com"

$RegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $AdminUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Admin user created: $Global:AdminUserId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to create admin user" -ForegroundColor Red
    exit 1
}

$LoginBody = @{ email = $AdminEmail; password = $AdminUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminAccessToken = $Result.Body.data.accessToken
    Write-Host "  Admin user logged in" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to login" -ForegroundColor Red
    exit 1
}

# Assign ADMIN role
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:AdminUserId/roles/ADMIN" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  ADMIN role assigned" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to assign ADMIN role" -ForegroundColor Red
}

# Re-login to get new token with ADMIN role
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminAccessToken = $Result.Body.data.accessToken
    Write-Host "  Admin re-logged in with ADMIN role" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to re-login" -ForegroundColor Red
}
Write-Host ""

# 3. Create test user
Write-Host "[3] Creating test user..." -ForegroundColor Yellow
$TestUsername = "testuser_diag_$Timestamp"
$TestEmail = "testuser_diag_$Timestamp@example.com"

$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = "Test123456!" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Test user created: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Failed to create test user" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 4. Test disable user through gateway
Write-Host "[4] Testing disable user through gateway..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$GatewayUrl/api/v1/admin/users/$Global:TestUserId/disable" -Headers (Get-AuthHeaders)
Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Cyan
Write-Host "  Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan
if ($Result.Body) {
    Write-Host "  Response Body:" -ForegroundColor Cyan
    Write-Host "    code: $($Result.Body.code)" -ForegroundColor Cyan
    Write-Host "    message: $($Result.Body.message)" -ForegroundColor Cyan
    if ($Result.Body.data) {
        Write-Host "    data: $($Result.Body.data | ConvertTo-Json -Compress)" -ForegroundColor Cyan
    }
}
if ($Result.Error) {
    Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
}
Write-Host ""

# 5. Test disable user directly to ZhiCore-user
Write-Host "[5] Testing disable user directly to ZhiCore-user..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/admin/users/$Global:TestUserId/disable" -Headers (Get-AuthHeaders)
Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Cyan
Write-Host "  Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan
if ($Result.Body) {
    Write-Host "  Response Body:" -ForegroundColor Cyan
    Write-Host "    code: $($Result.Body.code)" -ForegroundColor Cyan
    Write-Host "    message: $($Result.Body.message)" -ForegroundColor Cyan
}
if ($Result.Error) {
    Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
}
Write-Host ""

# 6. Test filter posts by status
Write-Host "[6] Testing filter posts by status..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$GatewayUrl/api/v1/admin/posts?status=PUBLISHED&page=1&size=20" -Headers (Get-AuthHeaders)
Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Cyan
Write-Host "  Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan
if ($Result.Body) {
    Write-Host "  Response Body:" -ForegroundColor Cyan
    Write-Host "    code: $($Result.Body.code)" -ForegroundColor Cyan
    Write-Host "    message: $($Result.Body.message)" -ForegroundColor Cyan
}
if ($Result.Error) {
    Write-Host "  Error: $($Result.Error)" -ForegroundColor Red
}
Write-Host ""

# 7. Check Nacos registration
Write-Host "[7] Checking Nacos service registration..." -ForegroundColor Yellow
$NacosUrl = "http://localhost:8848"
$LoginBody = @{ username = "nacos"; password = "nacos" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$NacosUrl/nacos/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.accessToken) {
    $NacosToken = $Result.Body.accessToken
    Write-Host "  Logged in to Nacos" -ForegroundColor Green
    
    $Services = @("ZhiCore-user", "ZhiCore-admin", "ZhiCore-post", "ZhiCore-comment", "ZhiCore-leaf")
    foreach ($ServiceName in $Services) {
        $Result = Invoke-ApiRequest -Method "GET" -Url "$NacosUrl/nacos/v1/ns/instance/list?serviceName=$ServiceName&groupName=ZhiCore_SERVICE&accessToken=$NacosToken"
        if ($Result.Success -and $Result.Body.hosts -and $Result.Body.hosts.Count -gt 0) {
            Write-Host "  [PASS] $ServiceName is registered" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] $ServiceName is NOT registered" -ForegroundColor Red
        }
    }
} else {
    Write-Host "  [FAIL] Failed to login to Nacos" -ForegroundColor Red
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Diagnosis Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
