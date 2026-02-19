# Test User Service Directly (bypass Gateway)

$Timestamp = Get-Date -Format "yyyyMMddHHmmssff"
$TestUsername = "directtest_$Timestamp"
$TestEmail = "directtest_$Timestamp@example.com"
$TestPassword = "Test123456!"

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    try {
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json"; Headers = $Headers; ErrorAction = "Stop" }
        if ($Body) { $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) }
        $Response = Invoke-WebRequest @RequestParams
        return @{ Success = $true; StatusCode = $Response.StatusCode; Body = ($Response.Content | ConvertFrom-Json) }
    }
    catch {
        $StatusCode = 0
        $ErrorMsg = $_.Exception.Message
        if ($_.Exception.Response) {
            $StatusCode = [int]$_.Exception.Response.StatusCode
        }
        return @{ Success = $false; StatusCode = $StatusCode; Error = $ErrorMsg }
    }
}

Write-Host "Testing User Service Directly (Port 8081)" -ForegroundColor Cyan
Write-Host ""

# Register
Write-Host "[1] Register..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "http://localhost:8081/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success) {
    Write-Host "    [PASS] Registered" -ForegroundColor Green
} else {
    Write-Host "    [FAIL] $($Result.Error)" -ForegroundColor Red
    exit 1
}

# Login
Write-Host "[2] Login..." -ForegroundColor Yellow
$LoginBody = @{ email = $TestEmail; password = $TestPassword }
$LoginResult = Invoke-ApiRequest -Method "POST" -Url "http://localhost:8081/api/v1/auth/login" -Body $LoginBody

if ($LoginResult.Success) {
    Write-Host "    [PASS] Login successful" -ForegroundColor Green
    
    $Token = $LoginResult.Body.data.accessToken
    
    # Extract userId from JWT token (it's in the 'sub' claim)
    # JWT format: header.payload.signature
    $TokenParts = $Token.Split('.')
    if ($TokenParts.Length -ge 2) {
        # Decode base64 payload (add padding if needed)
        $Payload = $TokenParts[1]
        $Padding = 4 - ($Payload.Length % 4)
        if ($Padding -ne 4) {
            $Payload += "=" * $Padding
        }
        $PayloadJson = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Payload))
        $PayloadObj = $PayloadJson | ConvertFrom-Json
        $UserId = $PayloadObj.sub
        
        Write-Host "    User ID (from token): $UserId" -ForegroundColor Cyan
        Write-Host "    Token: $($Token.Substring(0, 50))..." -ForegroundColor Cyan
    } else {
        Write-Host "    [ERROR] Invalid token format!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "    [FAIL] $($LoginResult.Error)" -ForegroundColor Red
    exit 1
}

# Get user info directly from User service
Write-Host "[3] Get user info from User service (port 8081)..." -ForegroundColor Yellow
$Headers = @{ "Authorization" = "Bearer $Token" }
$UserResult = Invoke-ApiRequest -Method "GET" -Url "http://localhost:8081/api/v1/users/$UserId" -Headers $Headers

if ($UserResult.Success) {
    Write-Host "    [PASS] Got user info" -ForegroundColor Green
    Write-Host "    Username: $($UserResult.Body.data.userName)" -ForegroundColor Cyan
} else {
    Write-Host "    [FAIL] $($UserResult.Error)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Done" -ForegroundColor Cyan

exit 0
