# Test script for GET /api/v1/files/{fileId} endpoint
# Tests file detail retrieval functionality

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UploadServiceUrl = $Config.upload_service_url
$UserServiceUrl = $Config.user_service_url

# Global variables
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestFileId = ""

# Utility function
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

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "File Detail API Test" -ForegroundColor Cyan
Write-Host "Upload Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Setup: Login
Write-Host "[SETUP] Logging in..." -ForegroundColor Yellow
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TestEmail = "filedetailtest_$Timestamp@example.com"
$TestUsername = "filedetailtest_$Timestamp"
$TestPassword = "Test123456!"

# Register
$RegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  User registered successfully" -ForegroundColor Green
} else {
    Write-Host "  Failed to register user" -ForegroundColor Red
    exit 1
}

# Login
$LoginBody = @{ email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:TestUserId = $Result.Body.data.userId
    Write-Host "  Login successful, userId: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  Failed to login" -ForegroundColor Red
    exit 1
}

# Upload a test file to get a fileId
Write-Host "[SETUP] Uploading test file..." -ForegroundColor Yellow
$TestImagePath = Join-Path $ScriptDir "test-image.jpg"

# Create a simple test image if it doesn't exist
if (-not (Test-Path $TestImagePath)) {
    # Create a minimal valid JPEG (1x1 pixel)
    $JpegBytes = @(
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08,
        0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12,
        0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20,
        0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27,
        0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01,
        0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xDA, 0x00, 0x08,
        0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0xFF, 0xD9
    )
    [System.IO.File]::WriteAllBytes($TestImagePath, $JpegBytes)
}

try {
    $Boundary = [System.Guid]::NewGuid().ToString()
    $FileContent = [System.IO.File]::ReadAllBytes($TestImagePath)
    $FileContentBase64 = [System.Convert]::ToBase64String($FileContent)
    
    $BodyLines = @(
        "--$Boundary",
        "Content-Disposition: form-data; name=`"file`"; filename=`"test.jpg`"",
        "Content-Type: image/jpeg",
        "",
        $FileContentBase64,
        "--$Boundary--"
    )
    $BodyString = $BodyLines -join "`r`n"
    
    $Headers = @{
        "Authorization" = "Bearer $Global:AccessToken"
        "Content-Type" = "multipart/form-data; boundary=$Boundary"
    }
    
    $Result = Invoke-WebRequest -Method POST -Uri "$UploadServiceUrl/api/v1/upload/image" -Body $BodyString -Headers $Headers -ErrorAction Stop
    $ResponseData = $Result.Content | ConvertFrom-Json
    
    if ($ResponseData.code -eq 200 -and $ResponseData.data.fileId) {
        $Global:TestFileId = $ResponseData.data.fileId
        Write-Host "  File uploaded successfully, fileId: $Global:TestFileId" -ForegroundColor Green
    } else {
        Write-Host "  Failed to upload file" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "  Error uploading file: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Test 1: Get file detail - success
Write-Host "[TEST-1] Get file detail - owner access" -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $FileDetail = $Result.Body.data
    Write-Host "  [PASS] File detail retrieved successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "    - File ID: $($FileDetail.fileId)" -ForegroundColor Gray
    Write-Host "    - Original Name: $($FileDetail.originalName)" -ForegroundColor Gray
    Write-Host "    - File Size: $($FileDetail.fileSize) bytes" -ForegroundColor Gray
    Write-Host "    - Content Type: $($FileDetail.contentType)" -ForegroundColor Gray
    Write-Host "    - Status: $($FileDetail.status)" -ForegroundColor Gray
    Write-Host "    - Access Level: $($FileDetail.accessLevel)" -ForegroundColor Gray
} else {
    Write-Host "  [FAIL] Failed to get file detail ($($Result.ResponseTime)ms)" -ForegroundColor Red
    Write-Host "    Error: $($Result.Body.message)" -ForegroundColor Red
}

# Test 2: Get file detail - non-existent file
Write-Host "[TEST-2] Get file detail - non-existent file" -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/non-existent-file-id" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 500 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Write-Host "  [PASS] Correctly rejected non-existent file ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Should reject non-existent file ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# Test 3: Get file detail - without authentication
Write-Host "[TEST-3] Get file detail - without authentication" -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/files/$Global:TestFileId"
if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 403) {
    Write-Host "  [PASS] Correctly rejected unauthenticated request ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Should reject unauthenticated request ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
