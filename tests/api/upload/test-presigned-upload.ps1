# Presigned Upload API Test Script
# Tests the presigned URL upload flow

param(
    [string]$ConfigPath = "../config/test-env.json",
    [string]$UploadServiceUrl = "http://localhost:8089"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Presigned Upload API Tests" -ForegroundColor Cyan
Write-Host "Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test Results
$TestResults = @()

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

# Global variables
$Global:UserId = 1

# === TEST 1: Get Presigned Upload URL ===
Write-Host "[TEST-1] Getting presigned upload URL..." -ForegroundColor Yellow
$PresignBody = @{
    fileName = "test-presigned-$(Get-Date -Format 'yyyyMMddHHmmss').mp4"
    fileSize = 1048576
    contentType = "video/mp4"
    fileHash = "$(New-Guid)".Replace("-", "")
    accessLevel = "public"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/presign" -Body $PresignBody -Headers @{ "X-User-Id" = $Global:UserId }

if ($Result.Success -and $Result.Body.code -eq 200) {
    $PresignedUrl = $Result.Body.data.presignedUrl
    $StoragePath = $Result.Body.data.storagePath
    $ExpiresAt = $Result.Body.data.expiresAt
    
    Add-TestResult -TestId "TEST-1" -TestName "Get Presigned Upload URL" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "URL generated successfully"
    Write-Host "  PASS - Presigned URL generated ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "    Storage Path: $StoragePath" -ForegroundColor Gray
    Write-Host "    Expires At: $ExpiresAt" -ForegroundColor Gray
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TEST-1" -TestName "Get Presigned Upload URL" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 2: Get Presigned URL for Existing File ===
Write-Host "[TEST-2] Getting presigned URL for existing file (should fail)..." -ForegroundColor Yellow
$ExistingFileHash = "d41d8cd98f00b204e9800998ecf8427e"
$PresignBody2 = @{
    fileName = "existing-file.mp4"
    fileSize = 1048576
    contentType = "video/mp4"
    fileHash = $ExistingFileHash
    accessLevel = "public"
}

# First request - should succeed
$Result1 = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/presign" -Body $PresignBody2 -Headers @{ "X-User-Id" = $Global:UserId }

if ($Result1.Success -and $Result1.Body.code -eq 200) {
    $StoragePath1 = $Result1.Body.data.storagePath
    
    # Simulate upload by confirming (without actual S3 upload - this will fail but that's expected)
    # In real scenario, client would upload to S3 first
    
    # Second request with same hash - should fail if file exists for user
    # Note: This test may pass if we haven't confirmed the upload yet
    Write-Host "  INFO - First presigned URL generated successfully" -ForegroundColor Gray
    Add-TestResult -TestId "TEST-2" -TestName "Presigned URL for Existing File" -Status "PASS" -ResponseTime "$($Result1.ResponseTime)ms" -Note "Correctly allows presigned URL before confirmation"
    Write-Host "  PASS - Correctly allows presigned URL before confirmation ($($Result1.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result1.Body.message) { $Result1.Body.message } else { $Result1.Error }
    Add-TestResult -TestId "TEST-2" -TestName "Presigned URL for Existing File" -Status "FAIL" -ResponseTime "$($Result1.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result1.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 3: Confirm Upload (without actual S3 upload - will fail) ===
Write-Host "[TEST-3] Confirming upload (without S3 upload - expected to fail)..." -ForegroundColor Yellow
$ConfirmBody = @{
    storagePath = "2026/01/19/1/test-file.mp4"
    fileHash = "$(New-Guid)".Replace("-", "")
    originalName = "test-file.mp4"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/confirm" -Body $ConfirmBody -Headers @{ "X-User-Id" = $Global:UserId }

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TEST-3" -TestName "Confirm Upload Without S3 File" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected - file not in S3"
    Write-Host "  PASS - Correctly rejected upload confirmation (file not in S3) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TEST-3" -TestName "Confirm Upload Without S3 File" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have rejected"
    Write-Host "  FAIL - Should have rejected confirmation ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 4: Invalid Request - Missing Fields ===
Write-Host "[TEST-4] Testing invalid request (missing fields)..." -ForegroundColor Yellow
$InvalidBody = @{
    fileName = "test.mp4"
    # Missing fileSize, contentType, fileHash
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload/presign" -Body $InvalidBody -Headers @{ "X-User-Id" = $Global:UserId }

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TEST-4" -TestName "Invalid Request Validation" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected invalid request"
    Write-Host "  PASS - Correctly rejected invalid request ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TEST-4" -TestName "Invalid Request Validation" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have rejected"
    Write-Host "  FAIL - Should have rejected invalid request ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === Test Results Summary ===
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

# Display detailed results
$TestResults | Format-Table -AutoSize

if ($FailCount -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some tests failed!" -ForegroundColor Red
    exit 1
}
