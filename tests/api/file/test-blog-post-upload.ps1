# Blog Post 服务上传图片测试
# 测试 blog-post 通过 file-api 调用 file-service 上传图片

param(
    [string]$BlogPostUrl = "http://localhost:8082",
    [string]$UserServiceUrl = "http://localhost:8081"
)

$TestResults = @()
$Global:AccessToken = ""
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"

function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId; TestName = $TestName; Status = $Status
        ResponseTime = $ResponseTime; Note = $Note
    }
}

function Get-AuthHeaders { return @{ "Authorization" = "Bearer $Global:AccessToken" } }

# 创建测试图片文件 (1x1 PNG)
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

# 上传文件
function Invoke-FileUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [hashtable]$Headers = @{}
    )
    
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    
    try {
        $FileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $FileName = [System.IO.Path]::GetFileName($FilePath)
        $Boundary = [System.Guid]::NewGuid().ToString()
        
        $LF = "`r`n"
        $BodyLines = @(
            "--$Boundary",
            "Content-Disposition: form-data; name=`"file`"; filename=`"$FileName`"",
            "Content-Type: image/png",
            ""
        )
        
        $HeaderBytes = [System.Text.Encoding]::UTF8.GetBytes(($BodyLines -join $LF) + $LF)
        $FooterBytes = [System.Text.Encoding]::UTF8.GetBytes("$LF--$Boundary--$LF")
        
        $BodyBytes = New-Object byte[] ($HeaderBytes.Length + $FileBytes.Length + $FooterBytes.Length)
        [System.Buffer]::BlockCopy($HeaderBytes, 0, $BodyBytes, 0, $HeaderBytes.Length)
        [System.Buffer]::BlockCopy($FileBytes, 0, $BodyBytes, $HeaderBytes.Length, $FileBytes.Length)
        [System.Buffer]::BlockCopy($FooterBytes, 0, $BodyBytes, $HeaderBytes.Length + $FileBytes.Length, $FooterBytes.Length)
        
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
                $ErrorContent = $StreamReader.ReadToEnd()
                $StreamReader.Close()
                try {
                    $Result.Body = $ErrorContent | ConvertFrom-Json
                } catch {
                    $Result.Error = $ErrorContent
                }
            } catch { 
                $Result.Error = $_.Exception.Message 
            }
        } else { 
            $Result.Error = $_.Exception.Message 
        }
    }
    
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
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

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Blog Post Image Upload Integration Test" -ForegroundColor Cyan
Write-Host "Blog Post URL: $BlogPostUrl" -ForegroundColor Cyan
Write-Host "User Service URL: $UserServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 创建临时目录
$TempDir = Join-Path $env:TEMP "blog_post_test_$Timestamp"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

# === Setup: 创建测试用户并登录 ===
Write-Host "=== Setup: Creating test user ===" -ForegroundColor Magenta

$UniqueUsername = "posttest_$Timestamp"
$UniqueEmail = "posttest_$Timestamp@example.com"
$Password = "Test123456!"

Write-Host "Registering user..." -ForegroundColor Cyan
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User registered successfully" -ForegroundColor Green
} else {
    Write-Host "  Failed to register: $($Result.Body.message)" -ForegroundColor Red
}

Write-Host "Logging in..." -ForegroundColor Cyan
$LoginBody = @{ email = $UniqueEmail; password = $Password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  Login successful" -ForegroundColor Green
} else {
    Write-Host "  Login failed: $($Result.Body.message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# === TEST 1: 上传封面图 ===
Write-Host "=== TEST 1: Upload Cover Image ===" -ForegroundColor Magenta

Write-Host "[TEST-001] Uploading cover image via blog-post service..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $CoverImagePath = Join-Path $TempDir "cover_image.png"
    New-TestImageFile -FilePath $CoverImagePath
    
    $Result = Invoke-FileUpload -Url "$BlogPostUrl/api/v1/posts/images/cover" -FilePath $CoverImagePath -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        Add-TestResult -TestId "TEST-001" -TestName "Upload Cover Image" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "FileId: $($Result.Body.data.fileId)"
        Write-Host "  PASS - Cover image uploaded successfully" -ForegroundColor Green
        Write-Host "    File ID: $($Result.Body.data.fileId)" -ForegroundColor Cyan
        Write-Host "    URL: $($Result.Body.data.url)" -ForegroundColor Cyan
        Write-Host "    Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } elseif ($Result.Error) { $Result.Error } else { "Unknown error" }
        Add-TestResult -TestId "TEST-001" -TestName "Upload Cover Image" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg" -ForegroundColor Red
        Write-Host "    Status Code: $($Result.StatusCode)" -ForegroundColor Red
        Write-Host "    Response Time: $($Result.ResponseTime)ms" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TEST-001" -TestName "Upload Cover Image" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No authentication token" -ForegroundColor Gray
}

Write-Host ""

# === TEST 2: 上传内容图 ===
Write-Host "=== TEST 2: Upload Content Image ===" -ForegroundColor Magenta

Write-Host "[TEST-002] Uploading content image via blog-post service..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $ContentImagePath = Join-Path $TempDir "content_image.png"
    New-TestImageFile -FilePath $ContentImagePath
    
    $Result = Invoke-FileUpload -Url "$BlogPostUrl/api/v1/posts/images/content" -FilePath $ContentImagePath -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        Add-TestResult -TestId "TEST-002" -TestName "Upload Content Image" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "FileId: $($Result.Body.data.fileId)"
        Write-Host "  PASS - Content image uploaded successfully" -ForegroundColor Green
        Write-Host "    File ID: $($Result.Body.data.fileId)" -ForegroundColor Cyan
        Write-Host "    URL: $($Result.Body.data.url)" -ForegroundColor Cyan
        Write-Host "    Response Time: $($Result.ResponseTime)ms" -ForegroundColor Cyan
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } elseif ($Result.Error) { $Result.Error } else { "Unknown error" }
        Add-TestResult -TestId "TEST-002" -TestName "Upload Content Image" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg" -ForegroundColor Red
        Write-Host "    Status Code: $($Result.StatusCode)" -ForegroundColor Red
        Write-Host "    Response Time: $($Result.ResponseTime)ms" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TEST-002" -TestName "Upload Content Image" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No authentication token" -ForegroundColor Gray
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

Write-Host "Total Tests: $($TestResults.Count)" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

$TestResults | Format-Table -AutoSize

# 清理临时文件
Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue

# === RustFS 凭据信息 ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RustFS Configuration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Default Configuration (application.yml):" -ForegroundColor Yellow
Write-Host "  Access Key: admin" -ForegroundColor White
Write-Host "  Secret Key: admin123456" -ForegroundColor White
Write-Host ""
Write-Host "Docker Configuration (docker-compose.yml):" -ForegroundColor Yellow
Write-Host "  Access Key: fileservice" -ForegroundColor White
Write-Host "  Secret Key: fileservice123" -ForegroundColor White
Write-Host ""
Write-Host "RustFS Console: http://localhost:9002" -ForegroundColor Cyan
Write-Host "RustFS API: http://localhost:9001" -ForegroundColor Cyan
Write-Host ""

# 退出码
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
