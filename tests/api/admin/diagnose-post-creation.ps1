# Diagnose Post Creation Issue

$ConfigPath = "../../config/test-env.json"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$TestUser = $Config.test_user

# Create test user
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TestUsername = "diagtest_$Timestamp"
$TestEmail = "diagtest_$Timestamp@example.com"

Write-Host "Creating test user..." -ForegroundColor Cyan
$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $TestUser.password }
try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UserServiceUrl/api/v1/auth/register" -Body ($RegisterBody | ConvertTo-Json) -ContentType "application/json" -ErrorAction Stop
    $RegisterResult = $Response.Content | ConvertFrom-Json
    $UserId = $RegisterResult.data
    Write-Host "User created, ID: $UserId" -ForegroundColor Green
}
catch {
    Write-Host "Failed to create user: $_" -ForegroundColor Red
    exit 1
}

# Login
Write-Host "Logging in..." -ForegroundColor Cyan
$LoginBody = @{ email = $TestEmail; password = $TestUser.password }
try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UserServiceUrl/api/v1/auth/login" -Body ($LoginBody | ConvertTo-Json) -ContentType "application/json" -ErrorAction Stop
    $LoginResult = $Response.Content | ConvertFrom-Json
    $AccessToken = $LoginResult.data.accessToken
    Write-Host "Logged in successfully" -ForegroundColor Green
}
catch {
    Write-Host "Failed to login: $_" -ForegroundColor Red
    exit 1
}

# Try to create post
Write-Host "Creating post..." -ForegroundColor Cyan
$PostBody = @{ title = "Diagnostic Test Post"; content = "Test content"; status = "PUBLISHED" }
$Headers = @{ "Authorization" = "Bearer $AccessToken" }

try {
    $Response = Invoke-WebRequest -Method POST -Uri "$PostServiceUrl/api/v1/posts" -Body ($PostBody | ConvertTo-Json) -ContentType "application/json" -Headers $Headers -ErrorAction Stop
    $PostResult = $Response.Content | ConvertFrom-Json
    Write-Host "Post created successfully!" -ForegroundColor Green
    Write-Host "Post ID: $($PostResult.data)" -ForegroundColor Green
    Write-Host "Response: $($Response.Content)" -ForegroundColor Cyan
}
catch {
    Write-Host "Failed to create post" -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "Status Code: $StatusCode" -ForegroundColor Red
        
        try {
            $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $ErrorBody = $StreamReader.ReadToEnd()
            $StreamReader.Close()
            Write-Host "Error Response: $ErrorBody" -ForegroundColor Red
        }
        catch {
            Write-Host "Could not read error response" -ForegroundColor Red
        }
    }
}
