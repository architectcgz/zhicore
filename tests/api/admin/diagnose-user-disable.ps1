# 诊断用户禁用/启用功能
# 直接调用 ZhiCore-user 服务，查看详细错误信息

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UserServiceUrl = $Config.user_service_url

$Global:AccessToken = ""
$Global:AdminUserId = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$AdminUsername = "admin_$Timestamp"
$AdminEmail = "admin_$Timestamp@example.com"
$TestUsername = "testuser_$Timestamp"
$TestEmail = "testuser_$Timestamp@example.com"
$Password = "Test123456!"

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
Write-Host "诊断用户禁用/启用功能" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 创建管理员用户
Write-Host "[1] 注册管理员用户..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminUserId = $Result.Body.data
    Write-Host "  成功 - 管理员 ID: $Global:AdminUserId" -ForegroundColor Green
} else {
    Write-Host "  失败 - $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

# 登录
Write-Host "[2] 登录管理员..." -ForegroundColor Yellow
$LoginBody = @{ email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  成功" -ForegroundColor Green
} else {
    Write-Host "  失败 - $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

# 分配 ADMIN 角色
Write-Host "[3] 分配 ADMIN 角色..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:AdminUserId/roles/ADMIN" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  成功" -ForegroundColor Green
} else {
    Write-Host "  失败 - $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

# 重新登录获取新 Token
Write-Host "[4] 重新登录获取新 Token..." -ForegroundColor Yellow
$LoginBody = @{ email = $AdminEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  成功" -ForegroundColor Green
} else {
    Write-Host "  失败 - $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

# 创建测试用户
Write-Host "[5] 注册测试用户..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  成功 - 测试用户 ID: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  失败 - $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "开始测试禁用/启用功能" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试禁用用户（直接调用 ZhiCore-user 服务）
Write-Host "[TEST-1] 直接调用 ZhiCore-user 服务禁用用户..." -ForegroundColor Yellow
Write-Host "  URL: $UserServiceUrl/api/v1/admin/users/$Global:TestUserId/disable" -ForegroundColor Gray
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/admin/users/$Global:TestUserId/disable" -Headers (Get-AuthHeaders)

Write-Host "  状态码: $($Result.StatusCode)" -ForegroundColor Gray
Write-Host "  响应时间: $($Result.ResponseTime)ms" -ForegroundColor Gray

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] 用户禁用成功" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] 用户禁用失败" -ForegroundColor Red
    Write-Host "  错误信息: $($Result.Body.message)" -ForegroundColor Red
    if ($Result.Body.data) {
        Write-Host "  详细信息: $($Result.Body.data | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    }
}

Write-Host ""

# 测试启用用户（直接调用 ZhiCore-user 服务）
Write-Host "[TEST-2] 直接调用 ZhiCore-user 服务启用用户..." -ForegroundColor Yellow
Write-Host "  URL: $UserServiceUrl/api/v1/admin/users/$Global:TestUserId/enable" -ForegroundColor Gray
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/admin/users/$Global:TestUserId/enable" -Headers (Get-AuthHeaders)

Write-Host "  状态码: $($Result.StatusCode)" -ForegroundColor Gray
Write-Host "  响应时间: $($Result.ResponseTime)ms" -ForegroundColor Gray

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] 用户启用成功" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] 用户启用失败" -ForegroundColor Red
    Write-Host "  错误信息: $($Result.Body.message)" -ForegroundColor Red
    if ($Result.Body.data) {
        Write-Host "  详细信息: $($Result.Body.data | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "诊断完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
