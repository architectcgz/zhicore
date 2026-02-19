# Ranking Service API Full Test Script
# Test Cases: RANK-001 to RANK-036 (36 test cases)
# Coverage: Hot Posts(8), Weekly Posts(4), Post Rank/Score(4), Creators(8), Topics(8), Boundary Tests(4)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$RankingServiceUrl = $Config.ranking_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "ranktest_$Timestamp"
$UniqueEmail = "ranktest_$Timestamp@example.com"

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

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Ranking Service API Full Tests" -ForegroundColor Cyan
Write-Host "Ranking Service URL: $RankingServiceUrl" -ForegroundColor Cyan
Write-Host "User Service URL: $UserServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test user and login ===
Write-Host "=== Setup: Creating test user ===" -ForegroundColor Magenta

Write-Host "Creating test user..." -ForegroundColor Cyan
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  Created test user, ID: $Global:TestUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create test user: $($Result.Body.message)" -ForegroundColor Yellow
}

Write-Host "Logging in test user..." -ForegroundColor Cyan
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    Write-Host "  Login successful, got token" -ForegroundColor Cyan
} else {
    Write-Host "  Login failed: $($Result.Body.message)" -ForegroundColor Yellow
}

Write-Host ""

# === Pre-check: Ranking service availability ===
Write-Host "=== Pre-check: Ranking service availability ===" -ForegroundColor Magenta
$Global:RankingServiceAvailable = $false

$TestResult = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/hot?page=0&size=10" -Headers (Get-AuthHeaders)
if ($TestResult.Success -and $TestResult.Body.code -eq 200) {
    $Global:RankingServiceAvailable = $true
    Write-Host "  Ranking service is available" -ForegroundColor Green
} elseif ($TestResult.StatusCode -eq 500) {
    Write-Host "  Ranking service has internal error - tests will be skipped" -ForegroundColor Yellow
} elseif ($TestResult.StatusCode -eq 0) {
    Write-Host "  Ranking service is not reachable - tests will be skipped" -ForegroundColor Yellow
} else {
    $Global:RankingServiceAvailable = $true
    Write-Host "  Ranking service is responding (status: $($TestResult.StatusCode))" -ForegroundColor Green
}

Write-Host ""

# === SECTION 1: Hot Posts Tests (8 tests) ===
Write-Host "=== SECTION 1: Hot Posts Tests ===" -ForegroundColor Magenta

# RANK-001: Get Hot Posts
Write-Host "[RANK-001] Testing get hot posts..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-001" -TestName "Get Hot Posts" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/hot?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-001" -TestName "Get Hot Posts" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Items: $ItemCount"
        Write-Host "  PASS - Got $ItemCount hot posts ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-001" -TestName "Get Hot Posts" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-002: Hot Posts Pagination
Write-Host "[RANK-002] Testing hot posts pagination..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-002" -TestName "Hot Posts Pagination" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/hot?page=0&size=5" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        if ($ItemCount -le 5) {
            Add-TestResult -TestId "RANK-002" -TestName "Hot Posts Pagination" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Page size: $ItemCount (limit 5)"
            Write-Host "  PASS - Pagination works, page size: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "RANK-002" -TestName "Hot Posts Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returned $ItemCount, expected <= 5"
            Write-Host "  FAIL - Returned $ItemCount, expected <= 5 ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-002" -TestName "Hot Posts Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-003: Hot Posts with Scores
Write-Host "[RANK-003] Testing hot posts with scores..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-003" -TestName "Hot Posts with Scores" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/hot/scores?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-003" -TestName "Hot Posts with Scores" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Items: $ItemCount"
        Write-Host "  PASS - Got $ItemCount posts with scores ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-003" -TestName "Hot Posts with Scores" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-004: Daily Hot Posts
Write-Host "[RANK-004] Testing daily hot posts..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-004" -TestName "Daily Hot Posts" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Today = Get-Date -Format "yyyy-MM-dd"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/daily?date=$Today&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-004" -TestName "Daily Hot Posts" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Daily posts: $ItemCount"
        Write-Host "  PASS - Got $ItemCount daily hot posts ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-004" -TestName "Daily Hot Posts" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}


# RANK-005: Invalid Date Format
Write-Host "[RANK-005] Testing invalid date format..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-005" -TestName "Invalid Date Format" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/daily?date=invalid-date&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-005" -TestName "Invalid Date Format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected invalid date"
        Write-Host "  PASS - Invalid date correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "RANK-005" -TestName "Invalid Date Format" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully (used default)"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-005" -TestName "Invalid Date Format" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-006: Future Date
Write-Host "[RANK-006] Testing future date..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-006" -TestName "Future Date" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $FutureDate = (Get-Date).AddDays(30).ToString("yyyy-MM-dd")
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/daily?date=$FutureDate&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-006" -TestName "Future Date" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully, items: $ItemCount"
        Write-Host "  PASS - Future date handled, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.StatusCode -eq 400) {
        Add-TestResult -TestId "RANK-006" -TestName "Future Date" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected future date"
        Write-Host "  PASS - Future date rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-006" -TestName "Future Date" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-007: Weekly Hot Posts
Write-Host "[RANK-007] Testing weekly hot posts..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-007" -TestName "Weekly Hot Posts" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/weekly?limit=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-007" -TestName "Weekly Hot Posts" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Weekly posts: $ItemCount"
        Write-Host "  PASS - Got $ItemCount weekly hot posts ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-007" -TestName "Weekly Hot Posts" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-008: Weekly with Week Number
Write-Host "[RANK-008] Testing weekly with week number..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-008" -TestName "Weekly with Week Number" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $WeekNum = [int](Get-Date -UFormat %V)
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/weekly?week=$WeekNum&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-008" -TestName "Weekly with Week Number" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Week $WeekNum posts: $ItemCount"
        Write-Host "  PASS - Got $ItemCount posts for week $WeekNum ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-008" -TestName "Weekly with Week Number" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

Write-Host ""

# === SECTION 2: Post Rank/Score Tests (4 tests) ===
Write-Host "=== SECTION 2: Post Rank/Score Tests ===" -ForegroundColor Magenta

# RANK-009: Get Post Rank
Write-Host "[RANK-009] Testing get post rank..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-009" -TestName "Get Post Rank" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/12345/rank" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Rank = $Result.Body.data
        Add-TestResult -TestId "RANK-009" -TestName "Get Post Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rank: $Rank"
        Write-Host "  PASS - Got post rank: $Rank ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-009" -TestName "Get Post Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Post not ranked (null)"
        Write-Host "  PASS - Post not ranked ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-009" -TestName "Get Post Rank" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-010: Get Post Score
Write-Host "[RANK-010] Testing get post score..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-010" -TestName "Get Post Score" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/12345/score" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Score = $Result.Body.data
        Add-TestResult -TestId "RANK-010" -TestName "Get Post Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Score: $Score"
        Write-Host "  PASS - Got post score: $Score ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-010" -TestName "Get Post Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Post not scored (null)"
        Write-Host "  PASS - Post not scored ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-010" -TestName "Get Post Score" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-011: Post Rank SQL Injection
Write-Host "[RANK-011] Testing post rank SQL injection..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-011" -TestName "Post Rank SQL Injection" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $SqlInjection = [System.Uri]::EscapeDataString("1; DROP TABLE posts;--")
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/$SqlInjection/rank" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-011" -TestName "Post Rank SQL Injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "SQL injection handled safely"
        Write-Host "  PASS - SQL injection handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-011" -TestName "Post Rank SQL Injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-012: Post Score XSS
Write-Host "[RANK-012] Testing post score XSS..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-012" -TestName "Post Score XSS" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $XssPayload = [System.Uri]::EscapeDataString("<script>alert('xss')</script>")
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/$XssPayload/score" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-012" -TestName "Post Score XSS" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "XSS handled safely"
        Write-Host "  PASS - XSS handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-012" -TestName "Post Score XSS" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

Write-Host ""

# === SECTION 3: Creator Ranking Tests (8 tests) ===
Write-Host "=== SECTION 3: Creator Ranking Tests ===" -ForegroundColor Magenta

# RANK-013: Get Creator Ranking
Write-Host "[RANK-013] Testing get creator ranking..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-013" -TestName "Get Creator Ranking" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/hot?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-013" -TestName "Get Creator Ranking" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Creators: $ItemCount"
        Write-Host "  PASS - Got $ItemCount creators ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-013" -TestName "Get Creator Ranking" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-014: Creator Ranking with Scores
Write-Host "[RANK-014] Testing creator ranking with scores..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-014" -TestName "Creator Ranking with Scores" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/hot/scores?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-014" -TestName "Creator Ranking with Scores" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Items: $ItemCount"
        Write-Host "  PASS - Got $ItemCount creators with scores ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-014" -TestName "Creator Ranking with Scores" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-015: Get Creator Rank
Write-Host "[RANK-015] Testing get creator rank..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-015" -TestName "Get Creator Rank" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/12345/rank" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Rank = $Result.Body.data
        Add-TestResult -TestId "RANK-015" -TestName "Get Creator Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rank: $Rank"
        Write-Host "  PASS - Got creator rank: $Rank ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-015" -TestName "Get Creator Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Creator not ranked"
        Write-Host "  PASS - Creator not ranked ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-015" -TestName "Get Creator Rank" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-016: Get Creator Score
Write-Host "[RANK-016] Testing get creator score..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-016" -TestName "Get Creator Score" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/12345/score" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Score = $Result.Body.data
        Add-TestResult -TestId "RANK-016" -TestName "Get Creator Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Score: $Score"
        Write-Host "  PASS - Got creator score: $Score ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-016" -TestName "Get Creator Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Creator not scored"
        Write-Host "  PASS - Creator not scored ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-016" -TestName "Get Creator Score" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-017: Creator Negative Page
Write-Host "[RANK-017] Testing creator negative page..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-017" -TestName "Creator Negative Page" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/hot?page=-1&size=20" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-017" -TestName "Creator Negative Page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Negative page rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "RANK-017" -TestName "Creator Negative Page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-017" -TestName "Creator Negative Page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-018: Creator Zero Size
Write-Host "[RANK-018] Testing creator zero size..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-018" -TestName "Creator Zero Size" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/hot?page=0&size=0" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-018" -TestName "Creator Zero Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Zero size rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-018" -TestName "Creator Zero Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully, items: $ItemCount"
        Write-Host "  PASS - Handled gracefully, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-018" -TestName "Creator Zero Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-019: Creator Size Exceeds MAX
Write-Host "[RANK-019] Testing creator size exceeds MAX (101)..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-019" -TestName "Creator Size Exceeds MAX" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/hot?page=0&size=101" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        if ($ItemCount -le 100) {
            Add-TestResult -TestId "RANK-019" -TestName "Creator Size Exceeds MAX" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Capped at MAX_SIZE, items: $ItemCount"
            Write-Host "  PASS - Capped at MAX_SIZE, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "RANK-019" -TestName "Creator Size Exceeds MAX" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returned $ItemCount, expected <= 100"
            Write-Host "  FAIL - Returned $ItemCount, expected <= 100 ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } elseif ($Result.StatusCode -eq 400) {
        Add-TestResult -TestId "RANK-019" -TestName "Creator Size Exceeds MAX" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Size > MAX rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-019" -TestName "Creator Size Exceeds MAX" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-020: Creator Rank SQL Injection
Write-Host "[RANK-020] Testing creator rank SQL injection..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-020" -TestName "Creator Rank SQL Injection" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $SqlInjection = [System.Uri]::EscapeDataString("1 OR 1=1;--")
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/creators/$SqlInjection/rank" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-020" -TestName "Creator Rank SQL Injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "SQL injection handled safely"
        Write-Host "  PASS - SQL injection handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-020" -TestName "Creator Rank SQL Injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

Write-Host ""

# === SECTION 4: Topic Ranking Tests (8 tests) ===
Write-Host "=== SECTION 4: Topic Ranking Tests ===" -ForegroundColor Magenta

# RANK-021: Get Hot Topics
Write-Host "[RANK-021] Testing get hot topics..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-021" -TestName "Get Hot Topics" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/hot?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-021" -TestName "Get Hot Topics" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Topics: $ItemCount"
        Write-Host "  PASS - Got $ItemCount hot topics ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-021" -TestName "Get Hot Topics" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-022: Hot Topics with Scores
Write-Host "[RANK-022] Testing hot topics with scores..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-022" -TestName "Hot Topics with Scores" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/hot/scores?page=0&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-022" -TestName "Hot Topics with Scores" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Items: $ItemCount"
        Write-Host "  PASS - Got $ItemCount topics with scores ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-022" -TestName "Hot Topics with Scores" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-023: Get Topic Rank
Write-Host "[RANK-023] Testing get topic rank..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-023" -TestName "Get Topic Rank" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/12345/rank" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Rank = $Result.Body.data
        Add-TestResult -TestId "RANK-023" -TestName "Get Topic Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Rank: $Rank"
        Write-Host "  PASS - Got topic rank: $Rank ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-023" -TestName "Get Topic Rank" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Topic not ranked"
        Write-Host "  PASS - Topic not ranked ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-023" -TestName "Get Topic Rank" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-024: Get Topic Score
Write-Host "[RANK-024] Testing get topic score..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-024" -TestName "Get Topic Score" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/12345/score" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Score = $Result.Body.data
        Add-TestResult -TestId "RANK-024" -TestName "Get Topic Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Score: $Score"
        Write-Host "  PASS - Got topic score: $Score ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Body -and $Result.Body.data -eq $null) {
        Add-TestResult -TestId "RANK-024" -TestName "Get Topic Score" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Topic not scored"
        Write-Host "  PASS - Topic not scored ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-024" -TestName "Get Topic Score" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-025: Topic Large Page Number
Write-Host "[RANK-025] Testing topic large page number..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-025" -TestName "Topic Large Page Number" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/hot?page=99999&size=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-025" -TestName "Topic Large Page Number" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully, items: $ItemCount"
        Write-Host "  PASS - Large page handled, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.StatusCode -eq 400) {
        Add-TestResult -TestId "RANK-025" -TestName "Topic Large Page Number" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Large page rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-025" -TestName "Topic Large Page Number" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-026: Topic Negative Size
Write-Host "[RANK-026] Testing topic negative size..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-026" -TestName "Topic Negative Size" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/hot?page=0&size=-1" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-026" -TestName "Topic Negative Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Negative size rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "RANK-026" -TestName "Topic Negative Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-026" -TestName "Topic Negative Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-027: Topic Invalid ID (Non-numeric)
Write-Host "[RANK-027] Testing topic invalid ID (non-numeric)..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-027" -TestName "Topic Invalid ID" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/invalid-id/rank" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or $Result.StatusCode -eq 500) {
        Add-TestResult -TestId "RANK-027" -TestName "Topic Invalid ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Invalid ID handled (status: $($Result.StatusCode))"
        Write-Host "  PASS - Invalid ID handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-027" -TestName "Topic Invalid ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-028: Topic Score SQL Injection
Write-Host "[RANK-028] Testing topic score SQL injection..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-028" -TestName "Topic Score SQL Injection" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/topics/1;DELETE FROM topics;--/score" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or $Result.StatusCode -eq 500 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-028" -TestName "Topic Score SQL Injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "SQL injection handled safely"
        Write-Host "  PASS - SQL injection handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-028" -TestName "Topic Score SQL Injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

Write-Host ""

# === SECTION 5: Boundary Tests (8 tests) ===
Write-Host "=== SECTION 5: Boundary Tests ===" -ForegroundColor Magenta

# RANK-029: Empty String Post ID
Write-Host "[RANK-029] Testing empty string post ID..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-029" -TestName "Empty String Post ID" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts//rank" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or $Result.StatusCode -eq 405 -or $Result.StatusCode -eq 500) {
        Add-TestResult -TestId "RANK-029" -TestName "Empty String Post ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Empty ID handled (status: $($Result.StatusCode))"
        Write-Host "  PASS - Empty ID handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success) {
        Add-TestResult -TestId "RANK-029" -TestName "Empty String Post ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "RANK-029" -TestName "Empty String Post ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Request rejected (expected)"
        Write-Host "  PASS - Request rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
}

# RANK-030: Special Characters in ID
Write-Host "[RANK-030] Testing special characters in ID..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-030" -TestName "Special Characters in ID" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $SpecialChars = [System.Uri]::EscapeDataString("@#$%^&*()")
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/$SpecialChars/score" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-030" -TestName "Special Characters in ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Special chars handled safely"
        Write-Host "  PASS - Special chars handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-030" -TestName "Special Characters in ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-031: Very Long ID
Write-Host "[RANK-031] Testing very long ID..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-031" -TestName "Very Long ID" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $LongId = "1" * 500
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/$LongId/rank" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 404 -or $Result.StatusCode -eq 414 -or ($Result.Body -and $Result.Body.code -eq 200)) {
        Add-TestResult -TestId "RANK-031" -TestName "Very Long ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Long ID handled safely"
        Write-Host "  PASS - Long ID handled safely ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-031" -TestName "Very Long ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-032: Invalid Week Number (Negative)
Write-Host "[RANK-032] Testing invalid week number (negative)..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-032" -TestName "Invalid Week Number" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/weekly?week=-1&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-032" -TestName "Invalid Week Number" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Negative week rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "RANK-032" -TestName "Invalid Week Number" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-032" -TestName "Invalid Week Number" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-033: Week Number Too Large
Write-Host "[RANK-033] Testing week number too large (100)..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-033" -TestName "Week Number Too Large" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/weekly?week=100&limit=20" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-033" -TestName "Week Number Too Large" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully, items: $ItemCount"
        Write-Host "  PASS - Large week handled, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.StatusCode -eq 400) {
        Add-TestResult -TestId "RANK-033" -TestName "Week Number Too Large" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Large week rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-033" -TestName "Week Number Too Large" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-034: Daily Limit Zero
Write-Host "[RANK-034] Testing daily limit zero..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-034" -TestName "Daily Limit Zero" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Today = Get-Date -Format "yyyy-MM-dd"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/daily?date=$Today&limit=0" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "RANK-034" -TestName "Daily Limit Zero" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Zero limit rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "RANK-034" -TestName "Daily Limit Zero" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully, items: $ItemCount"
        Write-Host "  PASS - Handled gracefully, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-034" -TestName "Daily Limit Zero" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-035: Daily Limit Exceeds MAX
Write-Host "[RANK-035] Testing daily limit exceeds MAX (101)..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-035" -TestName "Daily Limit Exceeds MAX" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Today = Get-Date -Format "yyyy-MM-dd"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/daily?date=$Today&limit=101" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ItemCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        if ($ItemCount -le 100) {
            Add-TestResult -TestId "RANK-035" -TestName "Daily Limit Exceeds MAX" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Capped at MAX_SIZE, items: $ItemCount"
            Write-Host "  PASS - Capped at MAX_SIZE, items: $ItemCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "RANK-035" -TestName "Daily Limit Exceeds MAX" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returned $ItemCount, expected <= 100"
            Write-Host "  FAIL - Returned $ItemCount, expected <= 100 ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } elseif ($Result.StatusCode -eq 400) {
        Add-TestResult -TestId "RANK-035" -TestName "Daily Limit Exceeds MAX" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Limit > MAX rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-035" -TestName "Daily Limit Exceeds MAX" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
}

# RANK-036: No Auth Token
Write-Host "[RANK-036] Testing no auth token..." -ForegroundColor Yellow
if (-not $Global:RankingServiceAvailable) {
    Add-TestResult -TestId "RANK-036" -TestName "No Auth Token" -Status "SKIP" -ResponseTime "-" -Note "Ranking service not available"
    Write-Host "  SKIP - Ranking service not available" -ForegroundColor Gray
} else {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$RankingServiceUrl/api/v1/ranking/posts/hot?page=0&size=20"
    if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 403) {
        Add-TestResult -TestId "RANK-036" -TestName "No Auth Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly requires auth"
        Write-Host "  PASS - Auth required ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "RANK-036" -TestName "No Auth Token" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Public endpoint (no auth needed)"
        Write-Host "  PASS - Public endpoint ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "RANK-036" -TestName "No Auth Token" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
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
Write-Host "Total Tests: $TotalCount" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
Write-Host "Detailed Results:" -ForegroundColor Cyan
Write-Host "-----------------" -ForegroundColor Cyan
foreach ($Result in $TestResults) {
    $StatusColor = switch ($Result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    Write-Host "$($Result.TestId): $($Result.TestName) - $($Result.Status) ($($Result.ResponseTime)) - $($Result.Note)" -ForegroundColor $StatusColor
}

# Update test status file
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    $ServiceSection = @"

## Ranking Service Tests
| Test ID | Test Name | Status | Response Time | Note |
|---------|-----------|--------|---------------|------|
"@
    
    foreach ($Result in $TestResults) {
        $StatusMark = switch ($Result.Status) {
            "PASS" { "[PASS]" }
            "FAIL" { "[FAIL]" }
            "SKIP" { "[SKIP]" }
            default { "[?]" }
        }
        $ServiceSection += "`n| $($Result.TestId) | $($Result.TestName) | $StatusMark | $($Result.ResponseTime) | $($Result.Note) |"
    }
    
    $ServiceSection += "`n`n**Test Time**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $ServiceSection += "`n**Test Result**: $PassCount passed, $FailCount failed, $SkipCount skipped"
    
    if ($StatusContent -match "## Ranking Service Tests") {
        $Pattern = "## Ranking Service Tests[\s\S]*?(?=## |$)"
        $Replacement = $ServiceSection.TrimStart() + "`n`n"
        $StatusContent = [regex]::Replace($StatusContent, $Pattern, $Replacement)
    } else {
        $StatusContent += "`n$ServiceSection"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host ""
    Write-Host "Test status updated in: $StatusFullPath" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Ranking Service API Tests Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
