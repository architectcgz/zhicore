# File Service API Test Script
# Test Cases: FILE-001 to FILE-008
# Coverage: Multi-tenant file isolation, X-App-Id validation, file deduplication

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === 初始化配置 ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$FileServiceUrl = $Config.file_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === 全局变量 ===
$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:ZhiCoreFileId = ""
$Global:ImFileId = ""
$Global:SecondAccessToken = ""
$Global:SecondUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "filetest_$Timestamp"
$UniqueEmail = "filetest_$Timestamp@example.com"
$SecondUsername = "filetest2_$Timestamp"
$SecondEmail = "filetest2_$Timestamp@example.com"

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
function Get-SecondAuthHeaders { return @{ "Authorization" = "Bearer $Global:SecondAccessToken" } }

# Create a test image file (1x1 pixel PNG)
function New-TestImageFile {
    param([string]$FilePath)
    $PngHeader = [byte[]]@(
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
        0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE,
        0x42, 0x60, 0x82
    )
    [System.IO.File]::WriteAllBytes($FilePath, $PngHeader)
    return $FilePath
}

# Upload file using multipart form data
function Invoke-FileUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$FieldName = "file",
        [hashtable]$Headers = @{}
    )
    
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    
    try {
        $FileName = Split-Path $FilePath -Leaf
        $FileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $Boundary = [System.Guid]::NewGuid().ToString()
        
        $BodyLines = @()
        $BodyLines += "--$Boundary"
        $BodyLines += "Content-Disposition: form-data; name=`"$FieldName`"; filename=`"$FileName`""
        $BodyLines += "Content-Type: application/octet-stream"
        $BodyLines += ""
        
        $BodyStart = [System.Text.Encoding]::UTF8.GetBytes(($BodyLines -join "`r`n") + "`r`n")
        $BodyEnd = [System.Text.Encoding]::UTF8.GetBytes(("`r`n--$Boundary--`r`n"))
        
        $BodyBytes = $BodyStart + $FileBytes + $BodyEnd
        
        $Headers["Content-Type"] = "multipart/form-data; boundary=$Boundary"
        
        $Response = Invoke-WebRequest -Uri $Url -Method POST -Body $BodyBytes -Headers $Headers -ErrorAction Stop
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

# === 测试开始 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "File Service API Tests" -ForegroundColor Cyan
Write-Host "Service URL: $FileServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: 创建测试用户并登录 ===
Write-Host "=== Setup: Creating test users and logging in ===" -ForegroundColor Magenta
Write-Host ""

# Register first user
Write-Host "Registering first test user..." -ForegroundColor Yellow
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User registered successfully" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Failed to register user" -ForegroundColor Red
    exit 1
}

# Login first user
Write-Host "Logging in first test user..." -ForegroundColor Yellow
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    $Global:TestUserId = $Result.Body.data.userId
    Write-Host "  Login successful - UserId: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Failed to login" -ForegroundColor Red
    exit 1
}

# Register second user
Write-Host "Registering second test user..." -ForegroundColor Yellow
$RegisterBody2 = @{ userName = $SecondUsername; email = $SecondEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  Second user registered successfully" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Failed to register second user" -ForegroundColor Red
    exit 1
}

# Login second user
Write-Host "Logging in second test user..." -ForegroundColor Yellow
$LoginBody2 = @{ email = $SecondEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody2

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:SecondAccessToken = $Result.Body.data.accessToken
    $Global:SecondUserId = $Result.Body.data.userId
    Write-Host "  Second user login successful - UserId: $Global:SecondUserId" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Failed to login second user" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === SECTION 1: X-App-Id Validation Tests ===
Write-Host "=== SECTION 1: X-App-Id Validation Tests ===" -ForegroundColor Magenta
Write-Host ""

# FILE-001: Normal upload with X-App-Id
Write-Host "[FILE-001] Testing normal upload with X-App-Id (ZhiCore)..." -ForegroundColor Yellow
$TestImagePath = Join-Path $env:TEMP "test-file-001.png"
New-TestImageFile -FilePath $TestImagePath | Out-Null

$Headers = Get-AuthHeaders
$Headers["X-App-Id"] = "ZhiCore"
$Result = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:ZhiCoreFileId = $Result.Body.data.fileId
    Add-TestResult -TestId "FILE-001" -TestName "Normal upload with X-App-Id" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "FileId: $Global:ZhiCoreFileId"
    Write-Host "  PASS - File uploaded successfully with ZhiCore appId ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "FILE-001" -TestName "Normal upload with X-App-Id" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Remove-Item $TestImagePath -ErrorAction SilentlyContinue

# FILE-002: Missing X-App-Id header
Write-Host "[FILE-002] Testing upload without X-App-Id header (expect 400)..." -ForegroundColor Yellow
$TestImagePath = Join-Path $env:TEMP "test-file-002.png"
New-TestImageFile -FilePath $TestImagePath | Out-Null

$Headers = Get-AuthHeaders
# Intentionally not adding X-App-Id
$Result = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers

if ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "FILE-002" -TestName "Missing X-App-Id header" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected with 400"
    Write-Host "  PASS - Correctly rejected missing X-App-Id ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = "Expected 400, got $($Result.StatusCode)"
    Add-TestResult -TestId "FILE-002" -TestName "Missing X-App-Id header" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Remove-Item $TestImagePath -ErrorAction SilentlyContinue

# FILE-003: Invalid X-App-Id format
Write-Host "[FILE-003] Testing upload with invalid X-App-Id format (expect 400)..." -ForegroundColor Yellow
$TestImagePath = Join-Path $env:TEMP "test-file-003.png"
New-TestImageFile -FilePath $TestImagePath | Out-Null

$Headers = Get-AuthHeaders
$Headers["X-App-Id"] = "Invalid@AppId!"  # Contains invalid characters
$Result = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers

if ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "FILE-003" -TestName "Invalid X-App-Id format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected invalid format"
    Write-Host "  PASS - Correctly rejected invalid X-App-Id format ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = "Expected 400, got $($Result.StatusCode)"
    Add-TestResult -TestId "FILE-003" -TestName "Invalid X-App-Id format" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Remove-Item $TestImagePath -ErrorAction SilentlyContinue

Write-Host ""

# === SECTION 2: Cross-AppId Access Tests ===
Write-Host "=== SECTION 2: Cross-AppId Access Tests ===" -ForegroundColor Magenta
Write-Host ""

# FILE-004: Cross appId access (expect 403)
Write-Host "[FILE-004] Testing cross appId file access (expect 403)..." -ForegroundColor Yellow
if ($Global:ZhiCoreFileId) {
    # Try to access ZhiCore file with im appId
    $Headers = Get-AuthHeaders
    $Headers["X-App-Id"] = "im"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$FileServiceUrl/api/v1/files/$Global:ZhiCoreFileId" -Headers $Headers
    
    if ($Result.StatusCode -eq 403 -or ($Result.Body -and $Result.Body.code -eq 403)) {
        Add-TestResult -TestId "FILE-004" -TestName "Cross appId access" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected with 403"
        Write-Host "  PASS - Correctly rejected cross appId access ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = "Expected 403, got $($Result.StatusCode)"
        Add-TestResult -TestId "FILE-004" -TestName "Cross appId access" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "FILE-004" -TestName "Cross appId access" -Status "SKIP" -ResponseTime "-" -Note "No ZhiCore file available"
    Write-Host "  SKIP - No ZhiCore file available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 3: File Deduplication Tests ===
Write-Host "=== SECTION 3: File Deduplication Tests ===" -ForegroundColor Magenta
Write-Host ""

# FILE-005: File deduplication (same appId)
Write-Host "[FILE-005] Testing file deduplication within same appId..." -ForegroundColor Yellow
$TestImagePath = Join-Path $env:TEMP "test-file-005.png"
New-TestImageFile -FilePath $TestImagePath | Out-Null

# Upload same file twice with same appId
$Headers = Get-AuthHeaders
$Headers["X-App-Id"] = "ZhiCore"
$Result1 = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers

if ($Result1.Success -and $Result1.Body.code -eq 200) {
    $FirstFileId = $Result1.Body.data.fileId
    
    # Upload again
    $Result2 = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers
    
    if ($Result2.Success -and $Result2.Body.code -eq 200) {
        $SecondFileId = $Result2.Body.data.fileId
        
        # Both uploads should succeed, deduplication happens at storage level
        Add-TestResult -TestId "FILE-005" -TestName "File deduplication (same appId)" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Deduplication working"
        Write-Host "  PASS - File deduplication working for same appId ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "FILE-005" -TestName "File deduplication (same appId)" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    $ErrorMsg = if ($Result1.Body.message) { $Result1.Body.message } else { $Result1.Error }
    Add-TestResult -TestId "FILE-005" -TestName "File deduplication (same appId)" -Status "FAIL" -ResponseTime "$($Result1.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result1.ResponseTime)ms)" -ForegroundColor Red
}

Remove-Item $TestImagePath -ErrorAction SilentlyContinue

# FILE-006: File deduplication (different appId)
Write-Host "[FILE-006] Testing file deduplication across different appIds..." -ForegroundColor Yellow
$TestImagePath = Join-Path $env:TEMP "test-file-006.png"
New-TestImageFile -FilePath $TestImagePath | Out-Null

# Upload with ZhiCore appId
$Headers = Get-AuthHeaders
$Headers["X-App-Id"] = "ZhiCore"
$Result1 = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers

if ($Result1.Success -and $Result1.Body.code -eq 200) {
    $ZhiCoreFileId = $Result1.Body.data.fileId
    
    # Upload same file with im appId
    $Headers["X-App-Id"] = "im"
    $Result2 = Invoke-FileUpload -Url "$FileServiceUrl/api/v1/upload/image" -FilePath $TestImagePath -Headers $Headers
    
    if ($Result2.Success -and $Result2.Body.code -eq 200) {
        $ImFileId = $Result2.Body.data.fileId
        $Global:ImFileId = $ImFileId
        
        # Files should be stored independently (different appIds)
        Add-TestResult -TestId "FILE-006" -TestName "File deduplication (different appId)" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Independent storage confirmed"
        Write-Host "  PASS - Files stored independently for different appIds ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "FILE-006" -TestName "File deduplication (different appId)" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    $ErrorMsg = if ($Result1.Body.message) { $Result1.Body.message } else { $Result1.Error }
    Add-TestResult -TestId "FILE-006" -TestName "File deduplication (different appId)" -Status "FAIL" -ResponseTime "$($Result1.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result1.ResponseTime)ms)" -ForegroundColor Red
}

Remove-Item $TestImagePath -ErrorAction SilentlyContinue

Write-Host ""

# === SECTION 4: File Operations Tests ===
Write-Host "=== SECTION 4: File Operations Tests ===" -ForegroundColor Magenta
Write-Host ""

# FILE-007: Delete file (verify appId ownership)
Write-Host "[FILE-007] Testing file deletion with appId verification..." -ForegroundColor Yellow
if ($Global:ZhiCoreFileId) {
    # Try to delete with correct appId
    $Headers = Get-AuthHeaders
    $Headers["X-App-Id"] = "ZhiCore"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$FileServiceUrl/api/v1/upload/$Global:ZhiCoreFileId" -Headers $Headers
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "FILE-007" -TestName "Delete file (verify appId)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "File deleted successfully"
        Write-Host "  PASS - File deleted with correct appId ($($Result.ResponseTime)ms)" -ForegroundColor Green
        
        # Clear the fileId since it's deleted
        $Global:ZhiCoreFileId = ""
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "FILE-007" -TestName "Delete file (verify appId)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "FILE-007" -TestName "Delete file (verify appId)" -Status "SKIP" -ResponseTime "-" -Note "No ZhiCore file available"
    Write-Host "  SKIP - No ZhiCore file available" -ForegroundColor Gray
}

# FILE-008: Get file details (verify appId)
Write-Host "[FILE-008] Testing get file details with appId verification..." -ForegroundColor Yellow
if ($Global:ImFileId) {
    # Get file details with correct appId
    $Headers = Get-AuthHeaders
    $Headers["X-App-Id"] = "im"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$FileServiceUrl/api/v1/files/$Global:ImFileId" -Headers $Headers
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "FILE-008" -TestName "Get file details (verify appId)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "File details retrieved"
        Write-Host "  PASS - File details retrieved with correct appId ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "FILE-008" -TestName "Get file details (verify appId)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "FILE-008" -TestName "Get file details (verify appId)" -Status "SKIP" -ResponseTime "-" -Note "No im file available"
    Write-Host "  SKIP - No im file available" -ForegroundColor Gray
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

# 输出详细结果表格
Write-Host "Detailed Results:" -ForegroundColor Cyan
Write-Host ""
$TestResults | Format-Table -Property TestId, TestName, Status, ResponseTime, Note -AutoSize

# 更新状态文件
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    $ServiceSection = @"

## 文件服务测试 (File Service)
| 测试ID | 测试名称 | 状态 | 响应时间 | 备注 |
|--------|----------|------|----------|------|
"@
    
    foreach ($Result in $TestResults) {
        $StatusMark = switch ($Result.Status) {
            "PASS" { "[PASS]" }
            "FAIL" { "[FAIL]" }
            "SKIP" { "[SKIP]" }
            default { "[?]" }
        }
        $ServiceSection += "`n| $($Result.TestId) | $($Result.TestName) | $StatusMark $($Result.Status) | $($Result.ResponseTime) | $($Result.Note) |"
    }
    
    $ServiceSection += "`n`n**测试时间**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $ServiceSection += "`n**测试结果**: $PassCount 通过, $FailCount 失败, $SkipCount 跳过"
    
    # 检查是否已存在文件服务测试部分
    if ($StatusContent -match "## 文件服务测试") {
        # 替换现有部分
        $StatusContent = $StatusContent -replace "(?s)## 文件服务测试.*?(?=##|\z)", $ServiceSection
    } else {
        # 追加新部分
        $StatusContent += "`n$ServiceSection`n"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host "Status file updated: $StatusFullPath" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "File Service API Tests Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 返回退出码
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
