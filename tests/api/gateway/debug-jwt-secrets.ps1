# Debug JWT Secrets - Compare User Service and Gateway
# This script generates tokens with different secrets to identify which one gateway is using

$UserServiceUrl = "http://localhost:8081"
$GatewayUrl = "http://localhost:8000"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JWT Secret Debug Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Create test user
$Timestamp = Get-Date -Format "yyyyMMddHHmmssff"
$Username = "jwtdebug_$Timestamp"
$Email = "jwtdebug_$Timestamp@example.com"
$Password = "Test123456!"

Write-Host "[1] Creating test user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $Username; email = $Email; password = $Password }
try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UserServiceUrl/api/v1/auth/register" -Body ($RegisterBody | ConvertTo-Json) -ContentType "application/json" -ErrorAction Stop
    $RegisterResult = $Response.Content | ConvertFrom-Json
    $UserId = $RegisterResult.data.userId
    Write-Host "   User ID: $UserId" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2] Logging in to get token..." -ForegroundColor Yellow
$LoginBody = @{ email = $Email; password = $Password }
try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UserServiceUrl/api/v1/auth/login" -Body ($LoginBody | ConvertTo-Json) -ContentType "application/json" -ErrorAction Stop
    $LoginResult = $Response.Content | ConvertFrom-Json
    $Token = $LoginResult.data.accessToken
    Write-Host "   Token obtained (length: $($Token.Length) chars)" -ForegroundColor Green
    Write-Host "   First 100 chars: $($Token.Substring(0, [Math]::Min(100, $Token.Length)))..." -ForegroundColor Gray
} catch {
    Write-Host "   FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[3] Testing direct access to user service..." -ForegroundColor Yellow
$Headers = @{ "Authorization" = "Bearer $Token" }
try {
    $Response = Invoke-WebRequest -Method GET -Uri "$UserServiceUrl/api/v1/users/$UserId" -Headers $Headers -ErrorAction Stop
    Write-Host "   [PASS] Direct access works (Status: $($Response.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "   [FAIL] Direct access failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "[4] Testing gateway access..." -ForegroundColor Yellow
try {
    $Response = Invoke-WebRequest -Method GET -Uri "$GatewayUrl/api/v1/users/$UserId" -Headers $Headers -ErrorAction Stop
    Write-Host "   [PASS] Gateway access works (Status: $($Response.StatusCode))" -ForegroundColor Green
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "SUCCESS: JWT secrets match!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} catch {
    Write-Host "   [FAIL] Gateway rejected token" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $StatusCode = [int]$_.Exception.Response.StatusCode
        Write-Host "   Status Code: $StatusCode" -ForegroundColor Red
        
        try {
            $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $ErrorBody = $StreamReader.ReadToEnd()
            $StreamReader.Close()
            Write-Host "   Response: $ErrorBody" -ForegroundColor Red
        } catch {}
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "FAILURE: JWT secrets DO NOT match!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Diagnosis:" -ForegroundColor Yellow
    Write-Host "  - User service generated a token with its JWT secret" -ForegroundColor White
    Write-Host "  - Gateway rejected the token (401 Unauthorized)" -ForegroundColor White
    Write-Host "  - This means gateway is using a DIFFERENT JWT secret" -ForegroundColor White
    Write-Host ""
    Write-Host "Possible causes:" -ForegroundColor Yellow
    Write-Host "  1. Gateway did not load configuration from Nacos" -ForegroundColor White
    Write-Host "  2. Gateway loaded wrong configuration" -ForegroundColor White
    Write-Host "  3. JwtProperties still has hardcoded default" -ForegroundColor White
    Write-Host "  4. Configuration precedence issue" -ForegroundColor White
    Write-Host ""
    Write-Host "Check gateway startup logs for:" -ForegroundColor Yellow
    Write-Host "  - 'Located property source' messages from Nacos" -ForegroundColor White
    Write-Host "  - JWT secret value being loaded" -ForegroundColor White
    Write-Host ""
}
