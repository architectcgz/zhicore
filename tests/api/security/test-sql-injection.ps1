# SQL Injection Security Test Script
# Test Cases: SEC-016 to SEC-030
# Coverage: quote injection, comment chars, UNION/OR/AND injection, DDL injection, blind injection

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "sqltest_$Timestamp"
$UniqueEmail = "sqltest_$Timestamp@example.com"

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
Write-Host "SQL Injection Security Tests" -ForegroundColor Cyan
Write-Host "Post Service: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "Comment Service: $CommentServiceUrl" -ForegroundColor Cyan
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
        
        # Create a test post for ID-based injection tests
        $PostBody = @{
            title = "Test Post for SQL Injection $Timestamp"
            content = "This is a test post for SQL injection testing"
        }
        $PostResult = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
        if ($PostResult.Success -and $PostResult.Body.code -eq 200) {
            $Global:TestPostId = $PostResult.Body.data.id
            Write-Host "  Test post created: $Global:TestPostId" -ForegroundColor Green
        }
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

# === SECTION 1: Quote Injection Tests ===
Write-Host "=== SECTION 1: Quote Injection Tests ===" -ForegroundColor Magenta

# SEC-016: Single quote injection
Write-Host "SEC-016 Testing single quote SQL injection..." -ForegroundColor Yellow
$SqlPayload = "' OR '1'='1"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-016" -TestName "Single quote injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-016" -TestName "Single quote injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-017: Double quote injection
Write-Host "SEC-017 Testing double quote SQL injection..." -ForegroundColor Yellow
$SqlPayload = '" OR "1"="1'
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-017" -TestName "Double quote injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-017" -TestName "Double quote injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 2: Comment Character Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 2: Comment Character Injection Tests ===" -ForegroundColor Magenta

# SEC-018: Double dash comment
Write-Host "SEC-018 Testing double dash comment injection..." -ForegroundColor Yellow
$SqlPayload = "admin'--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-018" -TestName "Double dash comment" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-018" -TestName "Double dash comment" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-019: Hash comment
Write-Host "SEC-019 Testing hash comment injection..." -ForegroundColor Yellow
$SqlPayload = "admin'#"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-019" -TestName "Hash comment" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-019" -TestName "Hash comment" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-020: Block comment
Write-Host "SEC-020 Testing block comment injection..." -ForegroundColor Yellow
$SqlPayload = "admin'/*"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-020" -TestName "Block comment" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-020" -TestName "Block comment" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 3: UNION/OR/AND Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 3: UNION/OR/AND Injection Tests ===" -ForegroundColor Magenta

# SEC-021: UNION SELECT injection
Write-Host "SEC-021 Testing UNION SELECT injection..." -ForegroundColor Yellow
$SqlPayload = "' UNION SELECT * FROM users--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-021" -TestName "UNION SELECT" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-021" -TestName "UNION SELECT" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-022: OR 1=1 injection
Write-Host "SEC-022 Testing OR 1=1 injection..." -ForegroundColor Yellow
$SqlPayload = "' OR 1=1--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-022" -TestName "OR 1=1" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-022" -TestName "OR 1=1" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-023: AND 1=1 injection
Write-Host "SEC-023 Testing AND 1=1 injection..." -ForegroundColor Yellow
$SqlPayload = "' AND 1=1--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-023" -TestName "AND 1=1" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-023" -TestName "AND 1=1" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 4: DDL Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 4: DDL Injection Tests ===" -ForegroundColor Magenta

# SEC-024: DROP TABLE injection
Write-Host "SEC-024 Testing DROP TABLE injection..." -ForegroundColor Yellow
$SqlPayload = "'; DROP TABLE users;--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-024" -TestName "DROP TABLE" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-024" -TestName "DROP TABLE" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-025: INSERT INTO injection
Write-Host "SEC-025 Testing INSERT INTO injection..." -ForegroundColor Yellow
$SqlPayload = "'; INSERT INTO users VALUES('hacker','hack@evil.com');--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-025" -TestName "INSERT INTO" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-025" -TestName "INSERT INTO" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-026: UPDATE SET injection
Write-Host "SEC-026 Testing UPDATE SET injection..." -ForegroundColor Yellow
$SqlPayload = "'; UPDATE users SET password='hacked';--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-026" -TestName "UPDATE SET" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-026" -TestName "UPDATE SET" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-027: DELETE FROM injection
Write-Host "SEC-027 Testing DELETE FROM injection..." -ForegroundColor Yellow
$SqlPayload = "'; DELETE FROM users;--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-027" -TestName "DELETE FROM" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-027" -TestName "DELETE FROM" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-028: EXEC injection
Write-Host "SEC-028 Testing EXEC injection..." -ForegroundColor Yellow
$SqlPayload = "'; EXEC xp_cmdshell('dir');--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-028" -TestName "EXEC" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-028" -TestName "EXEC" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 5: Blind Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 5: Blind Injection Tests ===" -ForegroundColor Magenta

# SEC-029: Time-based blind injection (SLEEP)
Write-Host "SEC-029 Testing time-based blind injection..." -ForegroundColor Yellow
$SqlPayload = "' AND SLEEP(5)--"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-029" -TestName "Time-based blind" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
elseif ($Result.ResponseTime -gt 4000) {
    Add-TestResult -TestId "SEC-029" -TestName "Time-based blind" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential time-based SQL injection"
    Write-Host "  FAIL - Potential time-based SQL injection ($($Result.ResponseTime)ms)" -ForegroundColor Red
}
else {
    Add-TestResult -TestId "SEC-029" -TestName "Time-based blind" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "No delay detected"
    Write-Host "  PASS - No delay detected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-030: Boolean-based blind injection
Write-Host "SEC-030 Testing boolean-based blind injection..." -ForegroundColor Yellow
$SqlPayload = "' AND 1=1 AND '1'='1"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$SqlPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-030" -TestName "Boolean-based blind" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - SQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-030" -TestName "Boolean-based blind" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection vulnerability"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
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
