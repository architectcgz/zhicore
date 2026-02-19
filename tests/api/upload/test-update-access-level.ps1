# Test Script for Update File Access Level API
# Tests the PUT /api/v1/files/{fileId}/access-level endpoint

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

# === 初始化配置 ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UploadServiceUrl = $Config.upload_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === 全局变量 ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestFileId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "accesstest_$Timestamp"
$UniqueEmail = "accesstest_$Timestamp@example.com"

# === 工具函数 ===
function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId; TestName = $TestName; Status = $Status
        ResponseTime = $ResponseTime; Note = $Note
    }
}

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

# === 测试开始 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Update File Access Level API Tests" -ForegroundColor Cyan
Write-Host "Upload Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: 创建测试用户并登录 ===
Write-Host "=== Setup: Creating test user and logging in ===" -ForegroundColor Magenta
Write-Host ""

# 注册测试用户
Write-Host "[SETUP-001] Registering test user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data.userId
    Write-Host "  [PASS] - User registered successfully (UserId: $Global:TestUserId) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] - Registration failed: $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# 登录获取 Token
Write-Host "[SETUP-002] Logging in..." -ForegroundColor Yellow
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  [PASS] - Login successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] - Login failed: $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

# 上传测试文件
Write-Host "[SETUP-003] Uploading test image..." -ForegroundColor Yellow
$TestImagePath = Join-Path $ScriptDir "test-image.jpg"

# 创建一个简单的测试图片（1x1 像素 JPEG）
$Base64Image = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k="
$ImageBytes = [System.Convert]::FromBase64String($Base64Image)
[System.IO.File]::WriteAllBytes($TestImagePath, $ImageBytes)

$Boundary = [System.Guid]::NewGuid().ToString()
$LF = "`r`n"
$BodyLines = @(
    "--$Boundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test.jpg`"",
    "Content-Type: image/jpeg",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($ImageBytes),
    "--$Boundary--"
) -join $LF

$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/image" -Body $BodyLines -Headers (@{ "Authorization" = "Bearer $Global:AccessToken"; "Content-Type" = "multipart/form-data; boundary=$Boundary" })

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestFileId = $Result.Body.data.fileId
    Write-Host "  [PASS] - Image uploaded successfully (FileId: $Global:TestFileId) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] - Upload failed: $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: Update Access Level Tests ===
Write-Host "=== SECTION 1: Update Access Level Tests ===" -ForegroundColor Magenta
Write-Host ""

# [ACCESS-001]: 修改访问级别为 PRIVATE
Write-Host "[ACCESS-001] Testing update access level to PRIVATE..." -ForegroundColor Yellow
if ($Global:TestFileId) {
    $Body = @{ accessLevel = "PRIVATE" }
    $Result = Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId/access-level" -Body $Body -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ACCESS-001" -TestName "Update to PRIVATE" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Successfully updated"
        Write-Host "  [PASS] - Access level updated to PRIVATE ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ACCESS-001" -TestName "Update to PRIVATE" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ACCESS-001" -TestName "Update to PRIVATE" -Status "SKIP" -ResponseTime "-" -Note "No test file available"
    Write-Host "  [SKIP] - No test file available" -ForegroundColor Gray
}

# [ACCESS-002]: 验证访问级别已更新
Write-Host "[ACCESS-002] Testing verify access level updated..." -ForegroundColor Yellow
if ($Global:TestFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessLevel -eq "PRIVATE") {
        Add-TestResult -TestId "ACCESS-002" -TestName "Verify PRIVATE" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Access level is PRIVATE"
        Write-Host "  [PASS] - Access level verified as PRIVATE ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.data.accessLevel) { "Expected PRIVATE, got $($Result.Body.data.accessLevel)" } else { $Result.Error }
        Add-TestResult -TestId "ACCESS-002" -TestName "Verify PRIVATE" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ACCESS-002" -TestName "Verify PRIVATE" -Status "SKIP" -ResponseTime "-" -Note "No test file available"
    Write-Host "  [SKIP] - No test file available" -ForegroundColor Gray
}

# [ACCESS-003]: 修改访问级别为 PUBLIC
Write-Host "[ACCESS-003] Testing update access level to PUBLIC..." -ForegroundColor Yellow
if ($Global:TestFileId) {
    $Body = @{ accessLevel = "PUBLIC" }
    $Result = Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId/access-level" -Body $Body -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ACCESS-003" -TestName "Update to PUBLIC" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Successfully updated"
        Write-Host "  [PASS] - Access level updated to PUBLIC ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ACCESS-003" -TestName "Update to PUBLIC" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ACCESS-003" -TestName "Update to PUBLIC" -Status "SKIP" -ResponseTime "-" -Note "No test file available"
    Write-Host "  [SKIP] - No test file available" -ForegroundColor Gray
}

# [ACCESS-004]: 验证访问级别已更新
Write-Host "[ACCESS-004] Testing verify access level updated..." -ForegroundColor Yellow
if ($Global:TestFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId" -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessLevel -eq "PUBLIC") {
        Add-TestResult -TestId "ACCESS-004" -TestName "Verify PUBLIC" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Access level is PUBLIC"
        Write-Host "  [PASS] - Access level verified as PUBLIC ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.data.accessLevel) { "Expected PUBLIC, got $($Result.Body.data.accessLevel)" } else { $Result.Error }
        Add-TestResult -TestId "ACCESS-004" -TestName "Verify PUBLIC" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ACCESS-004" -TestName "Verify PUBLIC" -Status "SKIP" -ResponseTime "-" -Note "No test file available"
    Write-Host "  [SKIP] - No test file available" -ForegroundColor Gray
}

# [ACCESS-005]: 修改不存在的文件
Write-Host "[ACCESS-005] Testing update non-existent file..." -ForegroundColor Yellow
$Body = @{ accessLevel = "PRIVATE" }
$Result = Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/non-existent-file/access-level" -Body $Body -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "ACCESS-005" -TestName "Update non-existent" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  [PASS] - Correctly rejected non-existent file ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = "Expected error, but got success"
    Add-TestResult -TestId "ACCESS-005" -TestName "Update non-existent" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# [ACCESS-006]: 修改他人文件（需要第二个用户）
Write-Host "[ACCESS-006] Testing update other user's file..." -ForegroundColor Yellow
Write-Host "  [SKIP] - Requires second user setup (not implemented in this test)" -ForegroundColor Gray
Add-TestResult -TestId "ACCESS-006" -TestName "Update other's file" -Status "SKIP" -ResponseTime "-" -Note "Requires second user"

Write-Host ""

# === 测试结果汇总 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count
$TotalCount = $TestResults.Count

Write-Host "Total Tests: $TotalCount" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# 显示详细结果
$TestResults | Format-Table -AutoSize

# 清理测试图片
if (Test-Path $TestImagePath) {
    Remove-Item $TestImagePath -Force
}

# 退出码
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
