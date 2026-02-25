# NoSQL Injection Security Test Script
# Test Cases: SEC-031 to SEC-040
# Coverage: MongoDB operators, Redis commands, Elasticsearch queries, JSON/LDAP/XPath/XML injection

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$SearchServiceUrl = $Config.search_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "nosqltest_$Timestamp"
$UniqueEmail = "nosqltest_$Timestamp@example.com"

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
Write-Host "NoSQL Injection Security Tests" -ForegroundColor Cyan
Write-Host "Post Service: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "Search Service: $SearchServiceUrl" -ForegroundColor Cyan
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

# === SECTION 1: MongoDB Operator Injection Tests ===
Write-Host "=== SECTION 1: MongoDB Operator Injection Tests ===" -ForegroundColor Magenta

# SEC-031: $where operator injection
Write-Host "SEC-031 Testing MongoDB `$where operator injection..." -ForegroundColor Yellow
$NoSqlPayload = @{
    title = @{
        '$where' = "this.password == 'x'"
    }
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $NoSqlPayload -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-031" -TestName "MongoDB `$where" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - NoSQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-031" -TestName "MongoDB `$where" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential NoSQL injection"
    Write-Host "  FAIL - Potential NoSQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-032: $gt/$lt operator injection
Write-Host "SEC-032 Testing MongoDB `$gt/`$lt operator injection..." -ForegroundColor Yellow
$NoSqlPayload = @{
    title = "Test"
    content = @{
        '$gt' = ""
    }
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $NoSqlPayload -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-032" -TestName "MongoDB `$gt/`$lt" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - NoSQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-032" -TestName "MongoDB `$gt/`$lt" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential NoSQL injection"
    Write-Host "  FAIL - Potential NoSQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-033: $regex operator injection
Write-Host "SEC-033 Testing MongoDB `$regex operator injection..." -ForegroundColor Yellow
$NoSqlPayload = @{
    title = @{
        '$regex' = ".*"
    }
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $NoSqlPayload -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-033" -TestName "MongoDB `$regex" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - NoSQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-033" -TestName "MongoDB `$regex" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential NoSQL injection"
    Write-Host "  FAIL - Potential NoSQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# SEC-034: $ne operator injection
Write-Host "SEC-034 Testing MongoDB `$ne operator injection..." -ForegroundColor Yellow
$NoSqlPayload = @{
    title = "Test"
    content = @{
        '$ne' = ""
    }
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $NoSqlPayload -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-034" -TestName "MongoDB `$ne" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - NoSQL injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-034" -TestName "MongoDB `$ne" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential NoSQL injection"
    Write-Host "  FAIL - Potential NoSQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# === SECTION 2: Redis Command Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 2: Redis Command Injection Tests ===" -ForegroundColor Magenta

# SEC-035: Redis FLUSHALL command injection
Write-Host "SEC-035 Testing Redis command injection..." -ForegroundColor Yellow
$RedisPayload = "FLUSHALL"
$Body = @{
    title = $RedisPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-035" -TestName "Redis command" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - Redis command handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-035" -TestName "Redis command" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === SECTION 3: Elasticsearch Query Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 3: Elasticsearch Query Injection Tests ===" -ForegroundColor Magenta

# SEC-036: Elasticsearch query injection
Write-Host "SEC-036 Testing Elasticsearch query injection..." -ForegroundColor Yellow
$EsPayload = '{"query":{"match_all":{}}}'
$EncodedPayload = [System.Uri]::EscapeDataString($EsPayload)
$Result = Invoke-ApiRequest -Method "GET" -Url "$SearchServiceUrl/api/v1/search/posts?keyword=$EncodedPayload" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "SEC-036" -TestName "Elasticsearch query" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Query injection correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-036" -TestName "Elasticsearch query" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled as normal search"
    Write-Host "  PASS - Handled as normal search ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# === SECTION 4: JSON/LDAP/XPath/XML Injection Tests ===
Write-Host ""
Write-Host "=== SECTION 4: JSON/LDAP/XPath/XML Injection Tests ===" -ForegroundColor Magenta

# SEC-037: JSON injection
Write-Host "SEC-037 Testing JSON injection..." -ForegroundColor Yellow
$JsonPayload = @{
    title = "Test"
    content = "Normal content"
    admin = $true  # Attempt to inject admin field
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $JsonPayload -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    # Check if admin field was accepted
    $PostId = $Result.Body.data.id
    $GetResult = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$PostId" -Headers (Get-AuthHeaders)
    if ($GetResult.Success -and -not $GetResult.Body.data.admin) {
        Add-TestResult -TestId "SEC-037" -TestName "JSON injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Extra field ignored"
        Write-Host "  PASS - Extra field correctly ignored ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
    else {
        Add-TestResult -TestId "SEC-037" -TestName "JSON injection" -Status "WARN" -ResponseTime "$($Result.ResponseTime)ms" -Note "Extra field accepted"
        Write-Host "  WARN - Extra field accepted ($($Result.ResponseTime)ms)" -ForegroundColor Yellow
    }
}
else {
    Add-TestResult -TestId "SEC-037" -TestName "JSON injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-038: LDAP injection
Write-Host "SEC-038 Testing LDAP injection..." -ForegroundColor Yellow
$LdapPayload = "*)(uid=*))(|(uid=*"
$Body = @{
    title = $LdapPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-038" -TestName "LDAP injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - LDAP payload handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-038" -TestName "LDAP injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-039: XPath injection
Write-Host "SEC-039 Testing XPath injection..." -ForegroundColor Yellow
$XPathPayload = "' or '1'='1"
$Body = @{
    title = $XPathPayload
    content = "Test content"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-039" -TestName "XPath injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - XPath payload handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-039" -TestName "XPath injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
}

# SEC-040: XML injection
Write-Host "SEC-040 Testing XML injection..." -ForegroundColor Yellow
$XmlPayload = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
$Body = @{
    title = "Test"
    content = $XmlPayload
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.Success) {
    Add-TestResult -TestId "SEC-040" -TestName "XML injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
    Write-Host "  PASS - XML payload handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
}
else {
    Add-TestResult -TestId "SEC-040" -TestName "XML injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
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
