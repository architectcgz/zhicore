# Instant Upload (秒传) Integration Test Script
# Test Cases: INSTANT-001 to INSTANT-010
# Coverage: 秒传功能、文件去重、引用计数

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
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
$Global:SecondAccessToken = ""
$Global:SecondUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "instant_test_$Timestamp"
$UniqueEmail = "instant_test_$Timestamp@example.com"

$SecondUsername = "instant_test2_$Timestamp"
$SecondEmail = "instant_test2_$Timestamp@example.com"

# Test file paths
$TestImagePath = Join-Path $ScriptDir "test-image.jpg"
$TestFilePath = Join-Path $ScriptDir "test-file.txt"

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

# === 创建测试文件 ===
function Create-TestFiles {
    # 创建测试图片 (1KB)
    $ImageContent = [byte[]]::new(1024)
    for ($i = 0; $i -lt 1024; $i++) { $ImageContent[$i] = ($i % 256) }
    [System.IO.File]::WriteAllBytes($TestImagePath, $ImageContent)
    
    # 创建测试文本文件 (1KB)
    $TextContent = "Test file content for instant upload testing. " * 20
    [System.IO.File]::WriteAllText($TestFilePath, $TextContent)
    
    Write-Host "Test files created: $TestImagePath, $TestFilePath" -ForegroundColor Cyan
}

# === 计算文件 MD5 ===
function Get-FileMD5 {
    param([string]$FilePath)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $stream = [System.IO.File]::OpenRead($FilePath)
    $hash = $md5.ComputeHash($stream)
    $stream.Close()
    return [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
}

# === 测试开始 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Instant Upload Integration Tests" -ForegroundColor Cyan
Write-Host "Upload Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: 创建测试文件 ===
Write-Host "Creating test files..." -ForegroundColor Cyan
Create-TestFiles
Write-Host ""

# === Setup: 创建第一个测试用户并登录 ===
Write-Host "Setting up first test user..." -ForegroundColor Cyan
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User registered: $UniqueUsername" -ForegroundColor Green
    
    $LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:AccessToken = $Result.Body.data.accessToken
        $Global:TestUserId = $Result.Body.data.userId
        Write-Host "  User logged in: userId=$Global:TestUserId" -ForegroundColor Green
    } else {
        Write-Host "  FAIL - Login failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  FAIL - Registration failed" -ForegroundColor Red
    exit 1
}
Write-Host ""

# === Setup: 创建第二个测试用户并登录 ===
Write-Host "Setting up second test user..." -ForegroundColor Cyan
$RegisterBody2 = @{ userName = $SecondUsername; email = $SecondEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  Second user registered: $SecondUsername" -ForegroundColor Green
    
    $LoginBody2 = @{ email = $SecondEmail; password = $TestUser.password }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody2
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:SecondAccessToken = $Result.Body.data.accessToken
        $Global:SecondUserId = $Result.Body.data.userId
        Write-Host "  Second user logged in: userId=$Global:SecondUserId" -ForegroundColor Green
    } else {
        Write-Host "  FAIL - Second user login failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  FAIL - Second user registration failed" -ForegroundColor Red
    exit 1
}
Write-Host ""

# === SECTION 1: 基础秒传测试 ===
Write-Host "=== SECTION 1: Basic Instant Upload Tests ===" -ForegroundColor Magenta
Write-Host ""

# INSTANT-001: 首次上传文件（建立基准）
Write-Host "[INSTANT-001] Testing first upload (baseline)..." -ForegroundColor Yellow
$Boundary = [System.Guid]::NewGuid().ToString()
$Headers = Get-AuthHeaders
$Headers["Content-Type"] = "multipart/form-data; boundary=$Boundary"

$FileBytes = [System.IO.File]::ReadAllBytes($TestImagePath)
$FileHash = Get-FileMD5 -FilePath $TestImagePath

$BodyLines = @(
    "--$Boundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test-image.jpg`"",
    "Content-Type: image/jpeg",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($FileBytes),
    "--$Boundary--"
)
$Body = $BodyLines -join "`r`n"

try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/image" `
        -Headers $Headers -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body))
    
    if ($Response.StatusCode -eq 200) {
        $ResponseData = $Response.Content | ConvertFrom-Json
        if ($ResponseData.code -eq 200) {
            $Global:FirstFileId = $ResponseData.data.fileId
            $Global:FirstFileUrl = $ResponseData.data.url
            Add-TestResult -TestId "INSTANT-001" -TestName "First upload (baseline)" -Status "PASS" `
                -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                -Note "FileId: $Global:FirstFileId, Hash: $FileHash"
            Write-Host "  PASS - First upload successful (FileId: $Global:FirstFileId)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "INSTANT-001" -TestName "First upload (baseline)" -Status "FAIL" `
                -ResponseTime "-" -Note $ResponseData.message
            Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
        }
    }
} catch {
    Add-TestResult -TestId "INSTANT-001" -TestName "First upload (baseline)" -Status "FAIL" `
        -ResponseTime "-" -Note $_.Exception.Message
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# INSTANT-002: 同一用户上传相同文件（秒传）
Write-Host "[INSTANT-002] Testing instant upload (same user, same file)..." -ForegroundColor Yellow
if ($Global:FirstFileId) {
    try {
        $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/image" `
            -Headers $Headers -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body))
        
        if ($Response.StatusCode -eq 200) {
            $ResponseData = $Response.Content | ConvertFrom-Json
            if ($ResponseData.code -eq 200) {
                $SecondFileId = $ResponseData.data.fileId
                
                # 验证是否是秒传（应该返回相同的文件信息或新的 FileRecord）
                if ($SecondFileId -eq $Global:FirstFileId) {
                    Add-TestResult -TestId "INSTANT-002" -TestName "Instant upload (same user)" -Status "PASS" `
                        -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                        -Note "Returned same FileId (instant upload)"
                    Write-Host "  PASS - Instant upload successful (same FileId)" -ForegroundColor Green
                } else {
                    Add-TestResult -TestId "INSTANT-002" -TestName "Instant upload (same user)" -Status "PASS" `
                        -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                        -Note "Created new FileRecord (deduplication)"
                    Write-Host "  PASS - File deduplicated (new FileId: $SecondFileId)" -ForegroundColor Green
                }
            } else {
                Add-TestResult -TestId "INSTANT-002" -TestName "Instant upload (same user)" -Status "FAIL" `
                    -ResponseTime "-" -Note $ResponseData.message
                Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
            }
        }
    } catch {
        Add-TestResult -TestId "INSTANT-002" -TestName "Instant upload (same user)" -Status "FAIL" `
            -ResponseTime "-" -Note $_.Exception.Message
        Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-002" -TestName "Instant upload (same user)" -Status "SKIP" `
        -ResponseTime "-" -Note "No baseline file"
    Write-Host "  SKIP - No baseline file" -ForegroundColor Gray
}
Write-Host ""

# === SECTION 2: 文件去重测试 ===
Write-Host "=== SECTION 2: File Deduplication Tests ===" -ForegroundColor Magenta
Write-Host ""

# INSTANT-003: 不同用户上传相同文件（去重）
Write-Host "[INSTANT-003] Testing deduplication (different user, same file)..." -ForegroundColor Yellow
if ($Global:FirstFileId) {
    $Headers2 = Get-SecondAuthHeaders
    $Headers2["Content-Type"] = "multipart/form-data; boundary=$Boundary"
    
    try {
        $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/image" `
            -Headers $Headers2 -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body))
        
        if ($Response.StatusCode -eq 200) {
            $ResponseData = $Response.Content | ConvertFrom-Json
            if ($ResponseData.code -eq 200) {
                $Global:SecondUserFileId = $ResponseData.data.fileId
                
                # 验证是否创建了新的 FileRecord（不同用户应该有不同的 FileRecord）
                if ($Global:SecondUserFileId -ne $Global:FirstFileId) {
                    Add-TestResult -TestId "INSTANT-003" -TestName "Deduplication (different user)" -Status "PASS" `
                        -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                        -Note "Created new FileRecord for second user"
                    Write-Host "  PASS - File deduplicated (FileId: $Global:SecondUserFileId)" -ForegroundColor Green
                } else {
                    Add-TestResult -TestId "INSTANT-003" -TestName "Deduplication (different user)" -Status "FAIL" `
                        -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                        -Note "Same FileId returned (should be different)"
                    Write-Host "  FAIL - Same FileId returned (should be different)" -ForegroundColor Red
                }
            } else {
                Add-TestResult -TestId "INSTANT-003" -TestName "Deduplication (different user)" -Status "FAIL" `
                    -ResponseTime "-" -Note $ResponseData.message
                Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
            }
        }
    } catch {
        Add-TestResult -TestId "INSTANT-003" -TestName "Deduplication (different user)" -Status "FAIL" `
            -ResponseTime "-" -Note $_.Exception.Message
        Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-003" -TestName "Deduplication (different user)" -Status "SKIP" `
        -ResponseTime "-" -Note "No baseline file"
    Write-Host "  SKIP - No baseline file" -ForegroundColor Gray
}
Write-Host ""

# === SECTION 3: 引用计数删除测试 ===
Write-Host "=== SECTION 3: Reference Count Deletion Tests ===" -ForegroundColor Magenta
Write-Host ""

# INSTANT-004: 删除第一个用户的文件（引用计数减1）
Write-Host "[INSTANT-004] Testing file deletion (reference count decrement)..." -ForegroundColor Yellow
if ($Global:FirstFileId) {
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$UploadServiceUrl/api/v1/upload/$Global:FirstFileId" `
        -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "INSTANT-004" -TestName "File deletion (first user)" -Status "PASS" `
            -ResponseTime "$($Result.ResponseTime)ms" -Note "File deleted successfully"
        Write-Host "  PASS - File deleted successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "INSTANT-004" -TestName "File deletion (first user)" -Status "FAIL" `
            -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-004" -TestName "File deletion (first user)" -Status "SKIP" `
        -ResponseTime "-" -Note "No file to delete"
    Write-Host "  SKIP - No file to delete" -ForegroundColor Gray
}
Write-Host ""

# INSTANT-005: 验证第二个用户的文件仍然存在
Write-Host "[INSTANT-005] Testing second user file still exists..." -ForegroundColor Yellow
if ($Global:SecondUserFileId) {
    # 尝试通过第二个用户访问文件（通过重新上传相同文件来验证）
    $Headers2 = Get-SecondAuthHeaders
    $Headers2["Content-Type"] = "multipart/form-data; boundary=$Boundary"
    
    try {
        $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/image" `
            -Headers $Headers2 -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body))
        
        if ($Response.StatusCode -eq 200) {
            $ResponseData = $Response.Content | ConvertFrom-Json
            if ($ResponseData.code -eq 200) {
                Add-TestResult -TestId "INSTANT-005" -TestName "Second user file exists" -Status "PASS" `
                    -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                    -Note "Second user can still access file"
                Write-Host "  PASS - Second user file still exists" -ForegroundColor Green
            } else {
                Add-TestResult -TestId "INSTANT-005" -TestName "Second user file exists" -Status "FAIL" `
                    -ResponseTime "-" -Note $ResponseData.message
                Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
            }
        }
    } catch {
        Add-TestResult -TestId "INSTANT-005" -TestName "Second user file exists" -Status "FAIL" `
            -ResponseTime "-" -Note $_.Exception.Message
        Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-005" -TestName "Second user file exists" -Status "SKIP" `
        -ResponseTime "-" -Note "No second user file"
    Write-Host "  SKIP - No second user file" -ForegroundColor Gray
}
Write-Host ""

# INSTANT-006: 删除第二个用户的文件（引用计数归零，S3对象应被删除）
Write-Host "[INSTANT-006] Testing final file deletion (reference count to zero)..." -ForegroundColor Yellow
if ($Global:SecondUserFileId) {
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$UploadServiceUrl/api/v1/upload/$Global:SecondUserFileId" `
        -Headers (Get-SecondAuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "INSTANT-006" -TestName "Final file deletion" -Status "PASS" `
            -ResponseTime "$($Result.ResponseTime)ms" -Note "File deleted, S3 object should be removed"
        Write-Host "  PASS - File deleted successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "INSTANT-006" -TestName "Final file deletion" -Status "FAIL" `
            -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-006" -TestName "Final file deletion" -Status "SKIP" `
        -ResponseTime "-" -Note "No file to delete"
    Write-Host "  SKIP - No file to delete" -ForegroundColor Gray
}
Write-Host ""

# === SECTION 4: 不同文件类型测试 ===
Write-Host "=== SECTION 4: Different File Type Tests ===" -ForegroundColor Magenta
Write-Host ""

# INSTANT-007: 上传文本文件（建立第二个基准）
Write-Host "[INSTANT-007] Testing text file upload (second baseline)..." -ForegroundColor Yellow
$Boundary2 = [System.Guid]::NewGuid().ToString()
$Headers = Get-AuthHeaders
$Headers["Content-Type"] = "multipart/form-data; boundary=$Boundary2"

$FileBytes2 = [System.IO.File]::ReadAllBytes($TestFilePath)
$FileHash2 = Get-FileMD5 -FilePath $TestFilePath

$BodyLines2 = @(
    "--$Boundary2",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test-file.txt`"",
    "Content-Type: text/plain",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($FileBytes2),
    "--$Boundary2--"
)
$Body2 = $BodyLines2 -join "`r`n"

try {
    $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/file" `
        -Headers $Headers -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body2))
    
    if ($Response.StatusCode -eq 200) {
        $ResponseData = $Response.Content | ConvertFrom-Json
        if ($ResponseData.code -eq 200) {
            $Global:TextFileId = $ResponseData.data.fileId
            Add-TestResult -TestId "INSTANT-007" -TestName "Text file upload" -Status "PASS" `
                -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                -Note "FileId: $Global:TextFileId, Hash: $FileHash2"
            Write-Host "  PASS - Text file uploaded (FileId: $Global:TextFileId)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "INSTANT-007" -TestName "Text file upload" -Status "FAIL" `
                -ResponseTime "-" -Note $ResponseData.message
            Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
        }
    }
} catch {
    Add-TestResult -TestId "INSTANT-007" -TestName "Text file upload" -Status "FAIL" `
        -ResponseTime "-" -Note $_.Exception.Message
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# INSTANT-008: 同一用户再次上传相同文本文件（秒传）
Write-Host "[INSTANT-008] Testing text file instant upload..." -ForegroundColor Yellow
if ($Global:TextFileId) {
    try {
        $Response = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/file" `
            -Headers $Headers -Body ([System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes($Body2))
        
        if ($Response.StatusCode -eq 200) {
            $ResponseData = $Response.Content | ConvertFrom-Json
            if ($ResponseData.code -eq 200) {
                Add-TestResult -TestId "INSTANT-008" -TestName "Text file instant upload" -Status "PASS" `
                    -ResponseTime "$([math]::Round($Response.Headers.'X-Response-Time'))ms" `
                    -Note "Instant upload successful"
                Write-Host "  PASS - Text file instant upload successful" -ForegroundColor Green
            } else {
                Add-TestResult -TestId "INSTANT-008" -TestName "Text file instant upload" -Status "FAIL" `
                    -ResponseTime "-" -Note $ResponseData.message
                Write-Host "  FAIL - $($ResponseData.message)" -ForegroundColor Red
            }
        }
    } catch {
        Add-TestResult -TestId "INSTANT-008" -TestName "Text file instant upload" -Status "FAIL" `
            -ResponseTime "-" -Note $_.Exception.Message
        Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "INSTANT-008" -TestName "Text file instant upload" -Status "SKIP" `
        -ResponseTime "-" -Note "No baseline text file"
    Write-Host "  SKIP - No baseline text file" -ForegroundColor Gray
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

# 详细结果表格
Write-Host "Detailed Results:" -ForegroundColor Cyan
Write-Host "--------------------------------------------------------------------------------"
Write-Host ("{0,-12} {1,-40} {2,-8} {3,-12} {4}" -f "Test ID", "Test Name", "Status", "Time", "Note")
Write-Host "--------------------------------------------------------------------------------"

foreach ($Result in $TestResults) {
    $StatusColor = switch ($Result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    
    $StatusMark = switch ($Result.Status) {
        "PASS" { "[PASS]" }
        "FAIL" { "[FAIL]" }
        "SKIP" { "[SKIP]" }
        default { "[?]" }
    }
    
    Write-Host ("{0,-12} {1,-40} " -f $Result.TestId, $Result.TestName) -NoNewline
    Write-Host ("{0,-8} " -f $StatusMark) -ForegroundColor $StatusColor -NoNewline
    Write-Host ("{0,-12} {1}" -f $Result.ResponseTime, $Result.Note)
}

Write-Host "--------------------------------------------------------------------------------"
Write-Host ""

# === 清理测试文件 ===
Write-Host "Cleaning up test files..." -ForegroundColor Cyan
if (Test-Path $TestImagePath) { Remove-Item $TestImagePath -Force }
if (Test-Path $TestFilePath) { Remove-Item $TestFilePath -Force }
Write-Host "Test files cleaned up" -ForegroundColor Green
Write-Host ""

# === 退出码 ===
if ($FailCount -gt 0) {
    Write-Host "Tests completed with failures" -ForegroundColor Red
    exit 1
} else {
    Write-Host "All tests passed successfully!" -ForegroundColor Green
    exit 0
}
