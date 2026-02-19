# Quick Admin API Test
# 快速测试管理后台API是否正常工作

$Config = Get-Content "../../config/test-env.json" | ConvertFrom-Json
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "=== Quick Admin API Test ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Register admin user
Write-Host "1. Registering admin user..." -ForegroundColor Yellow
$AdminUsername = "quickadmin_$Timestamp"
$AdminEmail = "quickadmin_$Timestamp@example.com"
$AdminPassword = "Admin123456!"
$RegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json

try {
    $RegisterResponse = Invoke-WebRequest -Uri "$($Config.user_service_url)/api/v1/auth/register" -Method POST -Body $RegisterBody -ContentType "application/json"
    $RegisterData = $RegisterResponse.Content | ConvertFrom-Json
    $UserId = $RegisterData.data
    Write-Host "   SUCCESS - User ID: $UserId" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 2: Login
Write-Host "2. Logging in..." -ForegroundColor Yellow
$LoginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json

try {
    $LoginResponse = Invoke-WebRequest -Uri "$($Config.user_service_url)/api/v1/auth/login" -Method POST -Body $LoginBody -ContentType "application/json"
    $LoginData = $LoginResponse.Content | ConvertFrom-Json
    $Token = $LoginData.data.accessToken
    Write-Host "   SUCCESS - Token obtained" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Assign ADMIN role
Write-Host "3. Assigning ADMIN role..." -ForegroundColor Yellow
$Headers = @{ "Authorization" = "Bearer $Token" }

try {
    $RoleResponse = Invoke-WebRequest -Uri "$($Config.user_service_url)/api/v1/users/$UserId/roles/ADMIN" -Method POST -Headers $Headers
    Write-Host "   SUCCESS - Role assigned" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: Re-login to get new token with ADMIN role
Write-Host "4. Re-logging in..." -ForegroundColor Yellow

try {
    $LoginResponse2 = Invoke-WebRequest -Uri "$($Config.user_service_url)/api/v1/auth/login" -Method POST -Body $LoginBody -ContentType "application/json"
    $LoginData2 = $LoginResponse2.Content | ConvertFrom-Json
    $NewToken = $LoginData2.data.accessToken
    Write-Host "   SUCCESS - New token obtained" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 5: Test direct admin service access
Write-Host "5. Testing direct admin service (localhost:8090)..." -ForegroundColor Yellow
$DirectHeaders = @{ 
    "Authorization" = "Bearer $NewToken"
    "X-User-Id" = $UserId
    "X-User-Roles" = "ADMIN"
}
$DirectUrl = "http://localhost:8090/admin/users?page=1&size=20"

try {
    $DirectResponse = Invoke-WebRequest -Uri $DirectUrl -Method GET -Headers $DirectHeaders
    Write-Host "   SUCCESS - Status: $($DirectResponse.StatusCode)" -ForegroundColor Green
    $DirectData = $DirectResponse.Content | ConvertFrom-Json
    Write-Host "   Response code: $($DirectData.code)" -ForegroundColor Cyan
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Yellow
}

# Step 6: Test through gateway
Write-Host "6. Testing through gateway (localhost:8000)..." -ForegroundColor Yellow
$GatewayHeaders = @{ "Authorization" = "Bearer $NewToken" }
$GatewayUrl = "$($Config.gateway_url)/api/v1/admin/users?page=1&size=20"

try {
    $GatewayResponse = Invoke-WebRequest -Uri $GatewayUrl -Method GET -Headers $GatewayHeaders
    Write-Host "   SUCCESS - Status: $($GatewayResponse.StatusCode)" -ForegroundColor Green
    $GatewayData = $GatewayResponse.Content | ConvertFrom-Json
    Write-Host "   Response code: $($GatewayData.code)" -ForegroundColor Cyan
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
