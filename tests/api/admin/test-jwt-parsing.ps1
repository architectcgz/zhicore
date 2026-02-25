# Test JWT Parsing
param(
    [string]$UserServiceUrl = "http://localhost:8081",
    [string]$PostServiceUrl = "http://localhost:8082"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JWT Parsing Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Register and login
Write-Host "[1/2] Creating test user and logging in..." -ForegroundColor Yellow
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$RegisterBody = @{
    userName = "jwttest_$Timestamp"
    email = "jwttest_$Timestamp@example.com"
    password = "Test123456!"
} | ConvertTo-Json

try {
    $RegisterResponse = Invoke-WebRequest -Uri "$UserServiceUrl/api/v1/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body $RegisterBody `
        -UseBasicParsing
    
    $RegisterData = $RegisterResponse.Content | ConvertFrom-Json
    $UserId = $RegisterData.data
    Write-Host "  User ID: $UserId" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Registration failed" -ForegroundColor Red
    exit 1
}

$LoginBody = @{
    email = "jwttest_$Timestamp@example.com"
    password = "Test123456!"
} | ConvertTo-Json

try {
    $LoginResponse = Invoke-WebRequest -Uri "$UserServiceUrl/api/v1/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body $LoginBody `
        -UseBasicParsing
    
    $LoginData = $LoginResponse.Content | ConvertFrom-Json
    $AccessToken = $LoginData.data.accessToken
    Write-Host "  Access Token: $($AccessToken.Substring(0, 50))..." -ForegroundColor Green
    
    # Decode JWT payload (base64)
    $parts = $AccessToken.Split('.')
    if ($parts.Length -ge 2) {
        $payload = $parts[1]
        # Add padding if needed
        while ($payload.Length % 4 -ne 0) {
            $payload += "="
        }
        $payloadBytes = [System.Convert]::FromBase64String($payload)
        $payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
        Write-Host "  JWT Payload: $payloadJson" -ForegroundColor Gray
    }
} catch {
    Write-Host "  [FAIL] Login failed" -ForegroundColor Red
    exit 1
}

# Step 2: Test post creation with detailed logging
Write-Host ""
Write-Host "[2/2] Testing post creation..." -ForegroundColor Yellow
$PostBody = @{
    title = "JWT Test Post $Timestamp"
    content = "Testing JWT parsing"
} | ConvertTo-Json

Write-Host "  Authorization Header: Bearer $($AccessToken.Substring(0, 30))..." -ForegroundColor Gray

try {
    $PostResponse = Invoke-WebRequest -Uri "$PostServiceUrl/api/v1/posts" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $AccessToken" } `
        -Body $PostBody `
        -UseBasicParsing
    
    $PostData = $PostResponse.Content | ConvertFrom-Json
    Write-Host "  [SUCCESS] Post created: $($PostData.data)" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Post creation failed" -ForegroundColor Red
    Write-Host "  Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "  Response: $responseBody" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
