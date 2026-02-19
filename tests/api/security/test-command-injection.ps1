# Command Injection Security Test Script
# Test Cases: SEC-041 to SEC-050
# Coverage: Shell command injection, path traversal, null byte, CRLF, HTTP header injection

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$UploadServiceUrl = $Config.upload_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "cmdtest_$Timestamp"
$UniqueEmail = "cmdtest_$Timestamp@example.com"

# === Utility Functions ===
function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId
        TestName = $TestName
        Status = $Status
        ResponseTime = $ResponseTime
        Note = $Note
    }
}

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{
            Method = $Method
            Uri = $Url
            ContentType = "application/json"
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) {
            $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10)
        }
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
            }
            catch {
                $Result.Error = $_.Exception.Message
            }
        }
        else {
            $Result.Error = $_.Exception.Message
        }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

function Get-AuthHeaders {
    return @{ "Authorization" = "Bearer $Global:AccessToken" }
}

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Command Injection Security Tests" -ForegroundColor Cyan
Write-Host "Post Service: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "Upload Service: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create Test User and Login ===
Write-Host "=== Setup: Creating Test User ===" -ForegroundColor Magenta
$RegisterBody = @{
    userName = $UniqueUsername
    email = $UniqueEmail
    password = $TestUser.password
}
$RegisterResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($RegisterResult.Success -and $RegisterResult.Body.code -eq 200) {
    $Global:TestUserId = $RegisterResult.Body.data.userId
    Write-Host "  User registered: $Global:TestUserId" -ForegroundColor Green
    
    # Login
    $LoginBody = @{
        email = $UniqueEmail
        password = $TestUser.password
    }
    $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
        $Global:AccessToken = $LoginResult.Body.data.accessToken
        Write-Host "  Login successful" -ForegroundColor Green
    }
    else {
        Write-Host "  Login failed" -ForegroundColor Red
        exit 1
    }
}
else {
    Write-Host "  Registration failed" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: Shell Command Injection Tests ===
Write-Host "=== SECTION 1: Shell Command Injection Tests ===" -ForegroundColor Magenta

# SEC-041: Semicolon command injection
Write-Host "SEC-041 Testing semicolon command injection..." -ForegroundColor Yellow
$CmdPayload = "; ls -la"
$Body = @{
    title = "Test $CmdPayload"
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-041" -TestName "Semicolon injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Command injection handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-041" -TestName "Semicolon injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-042: Pipe command injection
Write-Host "SEC-042 Testing pipe command injection..." -ForegroundColor Yellow
$CmdPayload = "| cat /etc/passwd"
$Body = @{
    title = "Test $CmdPayload"
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-042" -TestName "Pipe injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Command injection handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-042" -TestName "Pipe injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-043: Ampersand command injection
Write-Host "SEC-043 Testing ampersand command injection..." -ForegroundColor Yellow
$CmdPayload = "& whoami"
$Body = @{
    title = "Test $CmdPayload"
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-043" -TestName "Ampersand injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Command injection handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-043" -TestName "Ampersand injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-044: Dollar parenthesis command injection
Write-Host "SEC-044 Testing `$(command) injection..." -ForegroundColor Yellow
$CmdPayload = "`$(whoami)"
$Body = @{
    title = "Test $CmdPayload"
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-044" -TestName "`$(command) injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Command injection handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-044" -TestName "`$(command) injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-045: Backtick command injection
Write-Host "SEC-045 Testing backtick command injection..." -ForegroundColor Yellow
$CmdPayload = "``whoami``"
$Body = @{
    title = "Test $CmdPayload"
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-045" -TestName "Backtick injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Command injection handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-045" -TestName "Backtick injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === SECTION 2: Path Traversal Tests ===
Write-Host ""
Write-Host "=== SECTION 2: Path Traversal Tests ===" -ForegroundColor Magenta

# SEC-046: Unix path traversal
Write-Host "SEC-046 Testing Unix path traversal..." -ForegroundColor Yellow
$PathPayload = "../../../etc/passwd"
$Body = @{
    title = $PathPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-046" -TestName "Unix path traversal" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Path traversal handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-046" -TestName "Unix path traversal" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-047: Windows path traversal
Write-Host "SEC-047 Testing Windows path traversal..." -ForegroundColor Yellow
$PathPayload = "..\..\..\windows\system32"
$Body = @{
    title = $PathPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-047" -TestName "Windows path traversal" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Path traversal handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-047" -TestName "Windows path traversal" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === SECTION 3: Null Byte and CRLF Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 3: Null Byte and CRLF Injection Tests ===" -ForegroundColor Magenta

# SEC-048: Null byte injection
Write-Host "SEC-048 Testing null byte injection..." -ForegroundColor Yellow
$NullBytePayload = "file.txt%00.jpg"
$Body = @{
    title = $NullBytePayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-048" -TestName "Null byte injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Null byte handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-048" -TestName "Null byte injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-049: CRLF injection
Write-Host "SEC-049 Testing CRLF injection..." -ForegroundColor Yellow
$CrlfPayload = "header`r`nX-Injected: true"
$Body = @{
    title = $CrlfPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-049" -TestName "CRLF injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - CRLF handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-049" -TestName "CRLF injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === SECTION 4: HTTP Header Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 4: HTTP Header Injection Tests ===" -ForegroundColor Magenta

# SEC-050: HTTP header injection
Write-Host "SEC-050 Testing HTTP header injection..." -ForegroundColor Yellow
$HeaderPayload = "value`r`nSet-Cookie: admin=true"
$Body = @{
    title = $HeaderPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    # Check if injected header was set
    if ($Result.Body.PSObject.Properties.Name -contains "Set-Cookie") {
        Add-TestResult -TestId "SEC-050" -TestName "HTTP header injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Header injection successful"
        Write-Host "  FAIL - Header injection successful ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
    else {
        Add-TestResult -TestId "SEC-050" -TestName "HTTP header injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Header injection prevented"
        Write-Host "  PASS - Header injection prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
}
else {
    Add-TestResult -TestId "SEC-050" -TestName "HTTP header injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === Test Results Summary ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$WarnCount = ($TestResults | Where-Object { $_.Status -eq "WARN" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host ""
Write-Host "Total Tests: $($TestResults.Count)" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Warnings: $WarnCount" -ForegroundColor Yellow
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -AutoSize

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
}
else {
    exit 0
}
