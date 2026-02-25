# Test Post Creation Directly
param(
    [string]$UserServiceUrl = "http://localhost:8081",
    [string]$PostServiceUrl = "http://localhost:8082"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Direct Post Creation Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Register user
Write-Host "[1/4] Registering test user..." -ForegroundColor Yellow
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$RegisterBody = @{
    userName = "posttest_$Timestamp"
    email = "posttest_$Timestamp@example.com"
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
    Write-Host "  [SUCCESS] User registered: $UserId" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Registration failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 2: Login
Write-Host "[2/4] Logging in..." -ForegroundColor Yellow
$LoginBody = @{
    email = "posttest_$Timestamp@example.com"
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
    Write-Host "  [SUCCESS] Logged in" -ForegroundColor Green
    Write-Host "  Token: $($AccessToken.Substring(0, 50))..." -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] Login failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Create post
Write-Host "[3/4] Creating post..." -ForegroundColor Yellow
$PostBody = @{
    title = "Test Post $Timestamp"
    content = "This is a test post content created at $Timestamp"
} | ConvertTo-Json

Write-Host "  Request URL: $PostServiceUrl/api/v1/posts" -ForegroundColor Gray
Write-Host "  Request Body: $PostBody" -ForegroundColor Gray

try {
    $PostResponse = Invoke-WebRequest -Uri "$PostServiceUrl/api/v1/posts" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $AccessToken" } `
        -Body $PostBody `
        -UseBasicParsing
    
    $PostData = $PostResponse.Content | ConvertFrom-Json
    $PostId = $PostData.data
    Write-Host "  [SUCCESS] Post created: $PostId" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Post creation failed" -ForegroundColor Red
    Write-Host "  Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "  Response Body: $responseBody" -ForegroundColor Red
    }
    
    Write-Host "  Exception: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: Verify post
Write-Host "[4/4] Verifying post..." -ForegroundColor Yellow
try {
    $GetResponse = Invoke-WebRequest -Uri "$PostServiceUrl/api/v1/posts/my/$PostId" `
        -Method GET `
        -Headers @{ "Authorization" = "Bearer $AccessToken" } `
        -UseBasicParsing
    
    $GetData = $GetResponse.Content | ConvertFrom-Json
    Write-Host "  [SUCCESS] Post verified" -ForegroundColor Green
    Write-Host "  Title: $($GetData.data.title)" -ForegroundColor Gray
    Write-Host "  Status: $($GetData.data.status)" -ForegroundColor Gray
} catch {
    Write-Host "  [FAIL] Post verification failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
