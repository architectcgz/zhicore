# Private File Ownership Verification Test
# Tests that private files can only be accessed by their owners

param(
    [string]$ConfigPath = "../config/test-env.json",
    [string]$StatusPath = "../results/test-status.md"
)

# === 初始化配置 ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UploadServiceUrl = $Config.upload_service_url
$UserServiceUrl = $Config.user_service_url

# === 全局变量 ===
$TestResults = @()
$Global:User1AccessToken = ""
$Global:User1Id = ""
$Global:User2AccessToken = ""
$Global:User2Id = ""
$Global:PrivateFileId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$User1Username = "privtest1_$Timestamp"
$User1Email = "privtest1_$Timestamp@example.com"
$User2Username = "privtest2_$Timestamp"
$User2Email = "privtest2_$Timestamp@example.com"
$TestPassword = "Test123456!"

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

function Get-User1AuthHeaders { return @{ "Authorization" = "Bearer $Global:User1AccessToken" } }
function Get-User2AuthHeaders { return @{ "Authorization" = "Bearer $Global:User2AccessToken" } }

# === 测试开始 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Private File Ownership Verification Tests" -ForegroundColor Cyan
Write-Host "Upload Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: 创建两个测试用户 ===
Write-Host "=== Setup: Creating Test Users ===" -ForegroundColor Magenta
Write-Host ""

# 创建用户1
Write-Host "Creating User 1: $User1Username..." -ForegroundColor Yellow
$RegisterBody1 = @{ userName = $User1Username; email = $User1Email; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody1

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User 1 registered successfully" -ForegroundColor Green
    
    # 登录用户1
    $LoginBody1 = @{ email = $User1Email; password = $TestPassword }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody1
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:User1AccessToken = $Result.Body.data.accessToken
        $Global:User1Id = $Result.Body.data.userId
        Write-Host "  User 1 logged in successfully (ID: $Global:User1Id)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL - User 1 login failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  FAIL - User 1 registration failed" -ForegroundColor Red
    exit 1
}

# 创建用户2
Write-Host "Creating User 2: $User2Username..." -ForegroundColor Yellow
$RegisterBody2 = @{ userName = $User2Username; email = $User2Email; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User 2 registered successfully" -ForegroundColor Green
    
    # 登录用户2
    $LoginBody2 = @{ email = $User2Email; password = $TestPassword }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody2
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:User2AccessToken = $Result.Body.data.accessToken
        $Global:User2Id = $Result.Body.data.userId
        Write-Host "  User 2 logged in successfully (ID: $Global:User2Id)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL - User 2 login failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  FAIL - User 2 registration failed" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: Upload Private File Tests ===
Write-Host "=== SECTION 1: Upload Private File Tests ===" -ForegroundColor Magenta
Write-Host ""

# [PRIV-001]: User 1 uploads a private file
Write-Host "[PRIV-001] User 1 uploading a private file..." -ForegroundColor Yellow
$ImageBytes = [System.IO.File]::ReadAllBytes("$PSScriptRoot/test-image.jpg")
$Base64Image = [Convert]::ToBase64String($ImageBytes)
$UploadBody = @{
    image = $Base64Image
    accessLevel = "PRIVATE"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/image" -Body $UploadBody -Headers (Get-User1AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:PrivateFileId = $Result.Body.data.fileId
    Add-TestResult -TestId "PRIV-001" -TestName "Upload private file" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "FileId: $Global:PrivateFileId"
    Write-Host "  PASS - Private file uploaded (FileId: $Global:PrivateFileId) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "PRIV-001" -TestName "Upload private file" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: Owner Access Tests ===
Write-Host "=== SECTION 2: Owner Access Tests ===" -ForegroundColor Magenta
Write-Host ""

# [PRIV-002]: User 1 (owner) can get file URL
Write-Host "[PRIV-002] User 1 (owner) getting file URL..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId/url" -Headers (Get-User1AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.url) {
        $IsPermanent = $Result.Body.data.permanent
        $ExpiresAt = $Result.Body.data.expiresAt
        Add-TestResult -TestId "PRIV-002" -TestName "Owner get file URL" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Permanent: $IsPermanent, ExpiresAt: $ExpiresAt"
        Write-Host "  PASS - Owner can get file URL (Permanent: $IsPermanent) ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "PRIV-002" -TestName "Owner get file URL" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-002" -TestName "Owner get file URL" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

# [PRIV-003]: User 1 (owner) can get file details
Write-Host "[PRIV-003] User 1 (owner) getting file details..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId" -Headers (Get-User1AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.fileId) {
        $AccessLevel = $Result.Body.data.accessLevel
        Add-TestResult -TestId "PRIV-003" -TestName "Owner get file details" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "AccessLevel: $AccessLevel"
        Write-Host "  PASS - Owner can get file details (AccessLevel: $AccessLevel) ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "PRIV-003" -TestName "Owner get file details" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-003" -TestName "Owner get file details" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

# [PRIV-004]: User 1 (owner) can update access level
Write-Host "[PRIV-004] User 1 (owner) updating access level to PUBLIC..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $UpdateBody = @{ accessLevel = "PUBLIC" }
    $Result = Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId/access-level" -Body $UpdateBody -Headers (Get-User1AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "PRIV-004" -TestName "Owner update access level" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Updated to PUBLIC"
        Write-Host "  PASS - Owner can update access level ($($Result.ResponseTime)ms)" -ForegroundColor Green
        
        # 恢复为 PRIVATE
        $UpdateBody = @{ accessLevel = "PRIVATE" }
        Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId/access-level" -Body $UpdateBody -Headers (Get-User1AuthHeaders) | Out-Null
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "PRIV-004" -TestName "Owner update access level" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-004" -TestName "Owner update access level" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 3: Non-Owner Access Tests (Should Fail) ===
Write-Host "=== SECTION 3: Non-Owner Access Tests (Should Fail) ===" -ForegroundColor Magenta
Write-Host ""

# [PRIV-005]: User 2 (non-owner) cannot get file URL
Write-Host "[PRIV-005] User 2 (non-owner) attempting to get file URL..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId/url" -Headers (Get-User2AuthHeaders)
    
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "PRIV-005" -TestName "Non-owner get file URL (should fail)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-owner correctly denied access ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "PRIV-005" -TestName "Non-owner get file URL (should fail)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have been rejected"
        Write-Host "  FAIL - Non-owner should not have access ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-005" -TestName "Non-owner get file URL (should fail)" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

# [PRIV-006]: User 2 (non-owner) cannot get file details
Write-Host "[PRIV-006] User 2 (non-owner) attempting to get file details..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId" -Headers (Get-User2AuthHeaders)
    
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "PRIV-006" -TestName "Non-owner get file details (should fail)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-owner correctly denied access ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "PRIV-006" -TestName "Non-owner get file details (should fail)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have been rejected"
        Write-Host "  FAIL - Non-owner should not have access ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-006" -TestName "Non-owner get file details (should fail)" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

# [PRIV-007]: User 2 (non-owner) cannot update access level
Write-Host "[PRIV-007] User 2 (non-owner) attempting to update access level..." -ForegroundColor Yellow
if ($Global:PrivateFileId) {
    $UpdateBody = @{ accessLevel = "PUBLIC" }
    $Result = Invoke-ApiRequest -Method "PUT" -Url "$UploadServiceUrl/api/v1/files/$Global:PrivateFileId/access-level" -Body $UpdateBody -Headers (Get-User2AuthHeaders)
    
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "PRIV-007" -TestName "Non-owner update access level (should fail)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-owner correctly denied access ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "PRIV-007" -TestName "Non-owner update access level (should fail)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have been rejected"
        Write-Host "  FAIL - Non-owner should not have access ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "PRIV-007" -TestName "Non-owner update access level (should fail)" -Status "SKIP" -ResponseTime "-" -Note "No private file ID"
    Write-Host "  SKIP - No private file ID" -ForegroundColor Gray
}

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

Write-Host "Total Tests: $TotalCount" -ForegroundColor Cyan
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# 输出详细结果
$TestResults | Format-Table -AutoSize

# 退出码
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
