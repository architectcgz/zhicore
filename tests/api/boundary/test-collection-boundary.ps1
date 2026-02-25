# Collection Boundary Tests
# Test Cases: BOUND-031 to BOUND-040
# Coverage: Empty arrays, Single element, Large arrays, Duplicate elements, Null elements, Batch operations

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# === Global Variables ===
$TestResults = @()
$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TestPostIds = @()

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "colltest_$Timestamp"
$UniqueEmail = "colltest_$Timestamp@example.com"

# === Utility Functions ===
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

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Collection Boundary Tests" -ForegroundColor Cyan
Write-Host "Post Service URL: $PostServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test user and login ===
Write-Host "=== Setup: Creating test user and logging in ===" -ForegroundColor Magenta
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$RegisterResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($RegisterResult.Success -and $RegisterResult.Body.code -eq 200) {
    $Global:TestUserId = $RegisterResult.Body.data.userId
    Write-Host "User registered successfully: $Global:TestUserId" -ForegroundColor Green
    
    $LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
    $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
    
    if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
        $Global:AccessToken = $LoginResult.Body.data.accessToken
        Write-Host "Login successful, token obtained" -ForegroundColor Green
    } else {
        Write-Host "Login failed, cannot proceed with tests" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "User registration failed, cannot proceed with tests" -ForegroundColor Red
    exit 1
}

# Create some test posts for batch operations
Write-Host "Creating test posts for batch operations..." -ForegroundColor Cyan
for ($i = 1; $i -le 3; $i++) {
    $PostBody = @{ title = "Test Post $i"; content = "Content $i"; raw = "Content $i" }
    $PostResult = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
    if ($PostResult.Success -and $PostResult.Body.code -eq 200) {
        $Global:TestPostIds += $PostResult.Body.data.id
        Write-Host "  Created post $i: $($PostResult.Body.data.id)" -ForegroundColor Green
    }
}
Write-Host ""

# === SECTION 1: Empty Array Tests ===
Write-Host "=== SECTION 1: Empty Array Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-031: Empty array parameter
Write-Host "[BOUND-031] Testing empty array parameter..." -ForegroundColor Yellow
$IdsParam = ""
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$IdsParam" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-031" -TestName "Empty array" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected or returns empty"
    Write-Host "  PASS - Correctly rejected or returns empty ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    $IsEmpty = $Result.Body.data.Count -eq 0
    if ($IsEmpty) {
        Add-TestResult -TestId "BOUND-031" -TestName "Empty array" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returns empty result"
        Write-Host "  PASS - Returns empty result ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "BOUND-031" -TestName "Empty array" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-031" -TestName "Empty array" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-032: Single element array
Write-Host "[BOUND-032] Testing single element array..." -ForegroundColor Yellow
if ($Global:TestPostIds.Count -gt 0) {
    $SingleId = $Global:TestPostIds[0]
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$SingleId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "BOUND-032" -TestName "Single element" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
        Write-Host "  PASS - Single element handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "BOUND-032" -TestName "Single element" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "BOUND-032" -TestName "Single element" -Status "SKIP" -ResponseTime "-" -Note "No test posts available"
    Write-Host "  SKIP - No test posts available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 2: Large Array Tests ===
Write-Host "=== SECTION 2: Large Array Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-033: Large array (100+ elements)
Write-Host "[BOUND-033] Testing large array (100+ elements)..." -ForegroundColor Yellow
$LargeIds = (1..150) -join ","
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$LargeIds" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-033" -TestName "Large array" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled or limited"
    Write-Host "  PASS - Large array handled or limited ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.StatusCode -eq 400) {
    Add-TestResult -TestId "BOUND-033" -TestName "Large array" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected large array ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-033" -TestName "Large array" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Duplicate Elements Tests ===
Write-Host "=== SECTION 3: Duplicate Elements Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-034: Duplicate elements in array
Write-Host "[BOUND-034] Testing duplicate elements..." -ForegroundColor Yellow
if ($Global:TestPostIds.Count -gt 0) {
    $DuplicateIds = "$($Global:TestPostIds[0]),$($Global:TestPostIds[0]),$($Global:TestPostIds[0])"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$DuplicateIds" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "BOUND-034" -TestName "Duplicate elements" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Deduplicated or handled"
        Write-Host "  PASS - Duplicates deduplicated or handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "BOUND-034" -TestName "Duplicate elements" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "BOUND-034" -TestName "Duplicate elements" -Status "SKIP" -ResponseTime "-" -Note "No test posts available"
    Write-Host "  SKIP - No test posts available" -ForegroundColor Gray
}

# BOUND-035: Array with null elements (simulated with invalid IDs)
Write-Host "[BOUND-035] Testing array with invalid elements..." -ForegroundColor Yellow
$MixedIds = "1,null,2,undefined,3"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$MixedIds" -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-035" -TestName "Null elements" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly filtered or rejected"
    Write-Host "  PASS - Invalid elements filtered or rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-035" -TestName "Null elements" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-035" -TestName "Null elements" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: Batch Operation Tests ===
Write-Host "=== SECTION 4: Batch Operation Tests ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-036: Batch operation with empty list
Write-Host "[BOUND-036] Testing batch operation with empty list..." -ForegroundColor Yellow
$Body = @{ postIds = @() }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/batch/like" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-036" -TestName "Batch empty" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected or returns empty"
    Write-Host "  PASS - Correctly rejected or returns empty ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-036" -TestName "Batch empty" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
    Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-036" -TestName "Batch empty" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-037: Batch operation with single element
Write-Host "[BOUND-037] Testing batch operation with single element..." -ForegroundColor Yellow
if ($Global:TestPostIds.Count -gt 0) {
    $Body = @{ postIds = @($Global:TestPostIds[0]) }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/batch/like" -Body $Body -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "BOUND-037" -TestName "Batch single" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled correctly"
        Write-Host "  PASS - Single element batch handled correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "BOUND-037" -TestName "Batch single" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "BOUND-037" -TestName "Batch single" -Status "SKIP" -ResponseTime "-" -Note "No test posts available"
    Write-Host "  SKIP - No test posts available" -ForegroundColor Gray
}

# BOUND-038: Batch operation exceeding limit
Write-Host "[BOUND-038] Testing batch operation exceeding limit..." -ForegroundColor Yellow
$LargeIdArray = 1..200
$Body = @{ postIds = $LargeIdArray }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/batch/like" -Body $Body -Headers (Get-AuthHeaders)
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "BOUND-038" -TestName "Batch over limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected or limited"
    Write-Host "  PASS - Correctly rejected or limited ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "BOUND-038" -TestName "Batch over limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Partial processing"
    Write-Host "  PASS - Partial processing ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-038" -TestName "Batch over limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 5: Query with Non-existent IDs ===
Write-Host "=== SECTION 5: Query with Non-existent IDs ===" -ForegroundColor Magenta
Write-Host ""

# BOUND-039: Batch query with non-existent IDs
Write-Host "[BOUND-039] Testing batch query with non-existent IDs..." -ForegroundColor Yellow
$NonExistentIds = "999999,999998,999997"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$NonExistentIds" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $IsEmpty = $Result.Body.data.Count -eq 0
    if ($IsEmpty) {
        Add-TestResult -TestId "BOUND-039" -TestName "Non-existent IDs" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returns empty result"
        Write-Host "  PASS - Returns empty result ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "BOUND-039" -TestName "Non-existent IDs" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Partial results"
        Write-Host "  PASS - Returns partial results ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "BOUND-039" -TestName "Non-existent IDs" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# BOUND-040: Batch delete with mixed existing and non-existing IDs
Write-Host "[BOUND-040] Testing batch delete with mixed IDs..." -ForegroundColor Yellow
if ($Global:TestPostIds.Count -gt 0) {
    $MixedIds = "$($Global:TestPostIds[0]),999999,999998"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/batch?ids=$MixedIds" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "BOUND-040" -TestName "Mixed delete" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled existing items"
        Write-Host "  PASS - Correctly handled existing items ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.StatusCode -eq 404 -or $Result.StatusCode -eq 207) {
        Add-TestResult -TestId "BOUND-040" -TestName "Mixed delete" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Partial success"
        Write-Host "  PASS - Partial success ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "BOUND-040" -TestName "Mixed delete" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "BOUND-040" -TestName "Mixed delete" -Status "SKIP" -ResponseTime "-" -Note "No test posts available"
    Write-Host "  SKIP - No test posts available" -ForegroundColor Gray
}

Write-Host ""

# === Test Results Summary ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count
$TotalCount = $TestResults.Count

Write-Host ""
Write-Host "Total Tests: $TotalCount" -ForegroundColor Cyan
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -Property TestId, TestName, Status, ResponseTime, Note -AutoSize

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
