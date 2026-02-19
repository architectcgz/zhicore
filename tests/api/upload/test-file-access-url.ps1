# File Access URL API Test Script
# Test Case: UPLOAD-FILE-URL-001 - Get file access URL

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
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestFileId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "fileaccess_test_$Timestamp"
$UniqueEmail = "fileaccess_test_$Timestamp@example.com"

# === 工具函数 ===
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
Write-Host "File Access URL API Test" -ForegroundColor Cyan
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
    Write-Host "  [PASS] - User registered successfully (ID: $Global:TestUserId) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
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
    Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === Test 1: Upload a test file ===
Write-Host "=== Test 1: Upload Test File ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[TEST-001] Uploading test image..." -ForegroundColor Yellow

# 创建测试图片文件
$TestImagePath = Join-Path $env:TEMP "test-image-$Timestamp.txt"
"Test image content for file access URL test" | Out-File -FilePath $TestImagePath -Encoding UTF8

try {
    # 使用 multipart/form-data 上传
    $Boundary = [System.Guid]::NewGuid().ToString()
    $FileContent = [System.IO.File]::ReadAllBytes($TestImagePath)
    $FileContentBase64 = [System.Convert]::ToBase64String($FileContent)
    
    $BodyLines = @(
        "--$Boundary",
        "Content-Disposition: form-data; name=`"file`"; filename=`"test-image.txt`"",
        "Content-Type: text/plain",
        "",
        [System.Text.Encoding]::UTF8.GetString($FileContent),
        "--$Boundary--"
    )
    $Body = $BodyLines -join "`r`n"
    
    $Headers = @{
        "Authorization" = "Bearer $Global:AccessToken"
        "Content-Type" = "multipart/form-data; boundary=$Boundary"
    }
    
    $Result = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/file" -Body $Body -Headers $Headers -ErrorAction Stop
    $ResponseBody = $Result.Content | ConvertFrom-Json
    
    if ($ResponseBody.code -eq 200) {
        $Global:TestFileId = $ResponseBody.data.fileId
        Write-Host "  [PASS] - File uploaded successfully (ID: $Global:TestFileId)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] - Upload failed: $($ResponseBody.message)" -ForegroundColor Red
        exit 1
    }
}
catch {
    Write-Host "  [FAIL] - Upload error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    # 清理测试文件
    if (Test-Path $TestImagePath) {
        Remove-Item $TestImagePath -Force
    }
}

Write-Host ""

# === Test 2: Get file access URL ===
Write-Host "=== Test 2: Get File Access URL ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[TEST-002] Getting file access URL..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId/url" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    $FileUrl = $Result.Body.data.url
    $IsPermanent = $Result.Body.data.permanent
    $ExpiresAt = $Result.Body.data.expiresAt
    
    Write-Host "  [PASS] - File URL retrieved successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "    URL: $FileUrl" -ForegroundColor Cyan
    Write-Host "    Permanent: $IsPermanent" -ForegroundColor Cyan
    if ($ExpiresAt) {
        Write-Host "    Expires At: $ExpiresAt" -ForegroundColor Cyan
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === Test 3: Get non-existent file URL ===
Write-Host "=== Test 3: Get Non-existent File URL ===" -ForegroundColor Magenta
Write-Host ""

Write-Host "[TEST-003] Getting non-existent file URL..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/non-existent-file-id/url" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Write-Host "  [PASS] - Correctly rejected non-existent file ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] - Should reject non-existent file ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === 测试结果汇总 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All tests completed successfully!" -ForegroundColor Green
Write-Host ""
