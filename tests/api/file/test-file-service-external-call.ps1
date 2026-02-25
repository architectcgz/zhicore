# File Service External API Call Test
# Simple test to verify file-service can be called externally
# Test Cases: EXTERNAL-001 to EXTERNAL-003

param(
    [string]$FileServiceUrl = "http://localhost:8089"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "File Service External Call Test" -ForegroundColor Cyan
Write-Host "Service URL: $FileServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

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
        if ($Response.Content) {
            try {
                $Result.Body = $Response.Content | ConvertFrom-Json
            } catch {
                $Result.Body = $Response.Content
            }
        }
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
                    $Result.Body = $ErrorContent
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

$TestResults = @()

# === Test 1: Health Check ===
Write-Host "[EXTERNAL-001] Testing health check endpoint..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$FileServiceUrl/actuator/health"

if ($Result.Success -and $Result.StatusCode -eq 200) {
    Write-Host "  [PASS] Health check successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "  Response: $($Result.Body | ConvertTo-Json -Compress)" -ForegroundColor Gray
    $TestResults += [PSCustomObject]@{
        TestId = "EXTERNAL-001"
        TestName = "Health Check"
        Status = "PASS"
        ResponseTime = "$($Result.ResponseTime)ms"
    }
} else {
    Write-Host "  [FAIL] Health check failed - Status: $($Result.StatusCode), Error: $($Result.Error)" -ForegroundColor Red
    $TestResults += [PSCustomObject]@{
        TestId = "EXTERNAL-001"
        TestName = "Health Check"
        Status = "FAIL"
        ResponseTime = "$($Result.ResponseTime)ms"
    }
}

Write-Host ""

# === Test 2: API Info Endpoint ===
Write-Host "[EXTERNAL-002] Testing API info endpoint..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$FileServiceUrl/actuator/info"

if ($Result.Success -and $Result.StatusCode -eq 200) {
    Write-Host "  [PASS] API info endpoint accessible ($($Result.ResponseTime)ms)" -ForegroundColor Green
    if ($Result.Body) {
        Write-Host "  Response: $($Result.Body | ConvertTo-Json -Compress)" -ForegroundColor Gray
    }
    $TestResults += [PSCustomObject]@{
        TestId = "EXTERNAL-002"
        TestName = "API Info"
        Status = "PASS"
        ResponseTime = "$($Result.ResponseTime)ms"
    }
} else {
    Write-Host "  [FAIL] API info endpoint failed - Status: $($Result.StatusCode), Error: $($Result.Error)" -ForegroundColor Red
    $TestResults += [PSCustomObject]@{
        TestId = "EXTERNAL-002"
        TestName = "API Info"
        Status = "FAIL"
        ResponseTime = "$($Result.ResponseTime)ms"
    }
}

Write-Host ""

# === Test 3: Upload API Without Auth (expect 400 or 401) ===
Write-Host "[EXTERNAL-003] Testing upload API without authentication (expect error)..." -ForegroundColor Yellow

# Create a minimal test file
$TestImagePath = Join-Path $env:TEMP "test-external.png"
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
[System.IO.File]::WriteAllBytes($TestImagePath, $PngHeader)

try {
    $FileBytes = [System.IO.File]::ReadAllBytes($TestImagePath)
    $Boundary = [System.Guid]::NewGuid().ToString()
    
    $BodyLines = @()
    $BodyLines += "--$Boundary"
    $BodyLines += "Content-Disposition: form-data; name=`"file`"; filename=`"test.png`""
    $BodyLines += "Content-Type: image/png"
    $BodyLines += ""
    
    $BodyStart = [System.Text.Encoding]::UTF8.GetBytes(($BodyLines -join "`r`n") + "`r`n")
    $BodyEnd = [System.Text.Encoding]::UTF8.GetBytes(("`r`n--$Boundary--`r`n"))
    $BodyBytes = $BodyStart + $FileBytes + $BodyEnd
    
    $Headers = @{
        "Content-Type" = "multipart/form-data; boundary=$Boundary"
    }
    
    $StartTime = Get-Date
    try {
        $Response = Invoke-WebRequest -Uri "$FileServiceUrl/api/v1/upload/image" -Method POST -Body $BodyBytes -Headers $Headers -ErrorAction Stop
        $ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
        
        # If we get here, the API is accessible (might be 400 for missing X-App-Id)
        Write-Host "  [PASS] Upload API is accessible - Status: $($Response.StatusCode) ($($ResponseTime)ms)" -ForegroundColor Green
        Write-Host "  Note: API responded (expected error for missing auth/app-id)" -ForegroundColor Gray
        $TestResults += [PSCustomObject]@{
            TestId = "EXTERNAL-003"
            TestName = "Upload API Accessibility"
            Status = "PASS"
            ResponseTime = "$($ResponseTime)ms"
        }
    }
    catch {
        $ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
        $StatusCode = 0
        if ($_.Exception.Response) {
            $StatusCode = [int]$_.Exception.Response.StatusCode
        }
        
        # 400 (Bad Request) or 401 (Unauthorized) means API is working but rejecting request
        if ($StatusCode -eq 400 -or $StatusCode -eq 401) {
            Write-Host "  [PASS] Upload API is accessible - Status: $StatusCode ($($ResponseTime)ms)" -ForegroundColor Green
            Write-Host "  Note: API correctly rejected request without proper auth/headers" -ForegroundColor Gray
            $TestResults += [PSCustomObject]@{
                TestId = "EXTERNAL-003"
                TestName = "Upload API Accessibility"
                Status = "PASS"
                ResponseTime = "$($ResponseTime)ms"
            }
        } else {
            Write-Host "  [FAIL] Upload API error - Status: $StatusCode, Error: $($_.Exception.Message)" -ForegroundColor Red
            $TestResults += [PSCustomObject]@{
                TestId = "EXTERNAL-003"
                TestName = "Upload API Accessibility"
                Status = "FAIL"
                ResponseTime = "$($ResponseTime)ms"
            }
        }
    }
}
finally {
    Remove-Item $TestImagePath -ErrorAction SilentlyContinue
}

Write-Host ""

# === 测试结果汇总 ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$TotalCount = $TestResults.Count

Write-Host "Total Tests: $TotalCount" -ForegroundColor Cyan
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host ""

$TestResults | Format-Table -Property TestId, TestName, Status, ResponseTime -AutoSize

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

if ($FailCount -eq 0) {
    Write-Host "[SUCCESS] File service can be called externally!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "1. Ensure file-service is running (mvn spring-boot:run)" -ForegroundColor Gray
    Write-Host "2. Test with authentication using test-file-service-api.ps1" -ForegroundColor Gray
    Write-Host "3. Test integration with ZhiCore services" -ForegroundColor Gray
    exit 0
} else {
    Write-Host "[FAILED] Some tests failed. Check if file-service is running." -ForegroundColor Red
    Write-Host ""
    Write-Host "To start file-service:" -ForegroundColor Yellow
    Write-Host "  cd file-service" -ForegroundColor Gray
    Write-Host "  mvn spring-boot:run" -ForegroundColor Gray
    exit 1
}
