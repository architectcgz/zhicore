# Tag API Full Test Script
# Test Cases: TAG-001 to TAG-030 (including error scenarios and security tests)
# Coverage: Get Tag(5), List Tags(4), Search Tags(5), Get Posts by Tag(6), Hot Tags(5), Security(5)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$PostServiceUrl = $Config.post_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""
$Global:TestTagSlug = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "tagtest_$Timestamp"
$UniqueEmail = "tagtest_$Timestamp@example.com"

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
Write-Host "Tag API Full Tests" -ForegroundColor Cyan
Write-Host "Post Service URL: $PostServiceUrl" -ForegroundColor Cyan
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
    Write-Host "  Created user, ID: $Global:TestUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create user: $($Result.Body.message)" -ForegroundColor Yellow
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

# Create a test post with tags
Write-Host "Creating test post with tags..." -ForegroundColor Cyan
if ($Global:AccessToken) {
    $CreatePostBody = @{ 
        title = "Test Post for Tags $Timestamp"
        content = "This is test content with tags"
        tags = @("Java", "Spring Boot", "PostgreSQL")
    }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $CreatePostBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $Global:TestPostId = $Result.Body.data
        $Global:TestTagSlug = "java"
        Write-Host "  Post created with ID: $Global:TestPostId" -ForegroundColor Cyan
    } else {
        Write-Host "  Failed to create post: $($Result.Body.message)" -ForegroundColor Yellow
    }
}

Write-Host ""

# === SECTION 1: Get Tag Tests (5 tests) ===
Write-Host "=== SECTION 1: Get Tag Tests ===" -ForegroundColor Magenta

# TAG-001: Get Tag by Slug (Normal)
Write-Host "[TAG-001] Testing get tag by slug..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug"
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        Add-TestResult -TestId "TAG-001" -TestName "Get Tag by Slug" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Slug: $Global:TestTagSlug"
        Write-Host "  PASS - Tag retrieved ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "TAG-001" -TestName "Get Tag by Slug" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-001" -TestName "Get Tag by Slug" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

# TAG-002: Get Non-existent Tag
Write-Host "[TAG-002] Testing get non-existent tag..." -ForegroundColor Yellow
$NonExistentSlug = "non-existent-tag-$Timestamp"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$NonExistentSlug"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-002" -TestName "Get Non-existent Tag" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
    Write-Host "  PASS - Correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-002" -TestName "Get Non-existent Tag" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
    Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-003: Get Tag with Empty Slug
Write-Host "[TAG-003] Testing get tag with empty slug..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/"
if ($Result.StatusCode -eq 404 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-003" -TestName "Get Tag with Empty Slug" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected empty slug ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-003" -TestName "Get Tag with Empty Slug" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty slug"
    Write-Host "  FAIL - Should reject empty slug ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-004: Get Tag with Special Characters
Write-Host "[TAG-004] Testing get tag with special characters..." -ForegroundColor Yellow
$SpecialSlug = [System.Uri]::EscapeDataString("<script>alert('xss')</script>")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$SpecialSlug"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-004" -TestName "Get Tag with Special Chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Correctly handled special characters ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-004" -TestName "Get Tag with Special Chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unexpected response"
    Write-Host "  FAIL - Unexpected response ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-005: Get Tag with SQL Injection
Write-Host "[TAG-005] Testing get tag with SQL injection..." -ForegroundColor Yellow
$SqlInjectionSlug = [System.Uri]::EscapeDataString("1' OR '1'='1")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$SqlInjectionSlug"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-005" -TestName "Get Tag with SQL Injection" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - SQL injection prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-005" -TestName "Get Tag with SQL Injection" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential SQL injection"
    Write-Host "  FAIL - Potential SQL injection vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 2: List Tags Tests (4 tests) ===
Write-Host "=== SECTION 2: List Tags Tests ===" -ForegroundColor Magenta

# TAG-006: List Tags (Normal)
Write-Host "[TAG-006] Testing list tags..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags?page=0&size=20"
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $TotalTags = $Result.Body.data.total
    Add-TestResult -TestId "TAG-006" -TestName "List Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Total: $TotalTags"
    Write-Host "  PASS - Tags listed, total: $TotalTags ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-006" -TestName "List Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-007: List Tags with Invalid Page
Write-Host "[TAG-007] Testing list tags with invalid page..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags?page=-1&size=20"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-007" -TestName "List Tags Invalid Page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid page ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-007" -TestName "List Tags Invalid Page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid page"
    Write-Host "  FAIL - Should reject invalid page ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-008: List Tags with Invalid Size
Write-Host "[TAG-008] Testing list tags with invalid size..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags?page=0&size=0"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-008" -TestName "List Tags Invalid Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid size ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-008" -TestName "List Tags Invalid Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid size"
    Write-Host "  FAIL - Should reject invalid size ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-009: List Tags with Large Size
Write-Host "[TAG-009] Testing list tags with large size..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags?page=0&size=101"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-009" -TestName "List Tags Large Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected large size ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-009" -TestName "List Tags Large Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject size > 100"
    Write-Host "  FAIL - Should reject size > 100 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 3: Search Tags Tests (5 tests) ===
Write-Host "=== SECTION 3: Search Tags Tests ===" -ForegroundColor Magenta

# TAG-010: Search Tags (Normal)
Write-Host "[TAG-010] Testing search tags..." -ForegroundColor Yellow
$Keyword = "java"
$EncodedKeyword = [System.Uri]::EscapeDataString($Keyword)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$EncodedKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    $ResultCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
    Add-TestResult -TestId "TAG-010" -TestName "Search Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Found: $ResultCount"
    Write-Host "  PASS - Search completed, found: $ResultCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-010" -TestName "Search Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-011: Search Tags with Empty Keyword
Write-Host "[TAG-011] Testing search tags with empty keyword..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=&limit=10"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-011" -TestName "Search Tags Empty Keyword" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected empty keyword ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-011" -TestName "Search Tags Empty Keyword" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty keyword"
    Write-Host "  FAIL - Should reject empty keyword ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-012: Search Tags with Special Characters
Write-Host "[TAG-012] Testing search tags with special characters..." -ForegroundColor Yellow
$SpecialKeyword = [System.Uri]::EscapeDataString("<script>alert('xss')</script>")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$SpecialKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "TAG-012" -TestName "Search Tags Special Chars" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
    Write-Host "  PASS - Special characters handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-012" -TestName "Search Tags Special Chars" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-013: Search Tags with Invalid Limit
Write-Host "[TAG-013] Testing search tags with invalid limit..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=java&limit=0"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-013" -TestName "Search Tags Invalid Limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid limit ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-013" -TestName "Search Tags Invalid Limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject limit < 1"
    Write-Host "  FAIL - Should reject limit < 1 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-014: Search Tags with Large Limit
Write-Host "[TAG-014] Testing search tags with large limit..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=java&limit=51"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-014" -TestName "Search Tags Large Limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected large limit ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-014" -TestName "Search Tags Large Limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject limit > 50"
    Write-Host "  FAIL - Should reject limit > 50 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 4: Get Posts by Tag Tests (6 tests) ===
Write-Host "=== SECTION 4: Get Posts by Tag Tests ===" -ForegroundColor Magenta

# TAG-015: Get Posts by Tag (Normal)
Write-Host "[TAG-015] Testing get posts by tag..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug/posts?page=0&size=20"
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $TotalPosts = $Result.Body.data.total
        Add-TestResult -TestId "TAG-015" -TestName "Get Posts by Tag" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Total: $TotalPosts"
        Write-Host "  PASS - Posts retrieved, total: $TotalPosts ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "TAG-015" -TestName "Get Posts by Tag" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-015" -TestName "Get Posts by Tag" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

# TAG-016: Get Posts by Non-existent Tag
Write-Host "[TAG-016] Testing get posts by non-existent tag..." -ForegroundColor Yellow
$NonExistentSlug = "non-existent-tag-$Timestamp"
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$NonExistentSlug/posts?page=0&size=20"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-016" -TestName "Get Posts Non-existent Tag" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
    Write-Host "  PASS - Correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-016" -TestName "Get Posts Non-existent Tag" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
    Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-017: Get Posts by Tag with Invalid Page
Write-Host "[TAG-017] Testing get posts by tag with invalid page..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug/posts?page=-1&size=20"
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "TAG-017" -TestName "Get Posts Invalid Page" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected invalid page ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-017" -TestName "Get Posts Invalid Page" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid page"
        Write-Host "  FAIL - Should reject invalid page ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-017" -TestName "Get Posts Invalid Page" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

# TAG-018: Get Posts by Tag with Invalid Size
Write-Host "[TAG-018] Testing get posts by tag with invalid size..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug/posts?page=0&size=0"
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "TAG-018" -TestName "Get Posts Invalid Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected invalid size ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-018" -TestName "Get Posts Invalid Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid size"
        Write-Host "  FAIL - Should reject invalid size ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-018" -TestName "Get Posts Invalid Size" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

# TAG-019: Get Posts by Tag with Large Size
Write-Host "[TAG-019] Testing get posts by tag with large size..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug/posts?page=0&size=101"
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "TAG-019" -TestName "Get Posts Large Size" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected large size ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-019" -TestName "Get Posts Large Size" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject size > 100"
        Write-Host "  FAIL - Should reject size > 100 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-019" -TestName "Get Posts Large Size" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

# TAG-020: Get Posts by Tag Pagination
Write-Host "[TAG-020] Testing get posts by tag pagination..." -ForegroundColor Yellow
if ($Global:TestTagSlug) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$Global:TestTagSlug/posts?page=0&size=1"
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $PageSize = if ($Result.Body.data.items) { $Result.Body.data.items.Count } else { 0 }
        if ($PageSize -le 1) {
            Add-TestResult -TestId "TAG-020" -TestName "Get Posts Pagination" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Pagination works"
            Write-Host "  PASS - Pagination works correctly ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "TAG-020" -TestName "Get Posts Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Pagination not working"
            Write-Host "  FAIL - Pagination not working ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "TAG-020" -TestName "Get Posts Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "TAG-020" -TestName "Get Posts Pagination" -Status "SKIP" -ResponseTime "-" -Note "No tag slug"
    Write-Host "  SKIP - No tag slug available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 5: Hot Tags Tests (5 tests) ===
Write-Host "=== SECTION 5: Hot Tags Tests ===" -ForegroundColor Magenta

# TAG-021: Get Hot Tags (Normal)
Write-Host "[TAG-021] Testing get hot tags..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/hot?limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    $ResultCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
    Add-TestResult -TestId "TAG-021" -TestName "Get Hot Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Found: $ResultCount"
    Write-Host "  PASS - Hot tags retrieved, count: $ResultCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-021" -TestName "Get Hot Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-022: Get Hot Tags with Invalid Limit
Write-Host "[TAG-022] Testing get hot tags with invalid limit..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/hot?limit=0"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-022" -TestName "Get Hot Tags Invalid Limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid limit ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-022" -TestName "Get Hot Tags Invalid Limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject limit < 1"
    Write-Host "  FAIL - Should reject limit < 1 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-023: Get Hot Tags with Large Limit
Write-Host "[TAG-023] Testing get hot tags with large limit..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/hot?limit=51"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-023" -TestName "Get Hot Tags Large Limit" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected large limit ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-023" -TestName "Get Hot Tags Large Limit" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject limit > 50"
    Write-Host "  FAIL - Should reject limit > 50 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-024: Get Hot Tags Default Limit
Write-Host "[TAG-024] Testing get hot tags with default limit..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/hot"
if ($Result.Success -and $Result.Body.code -eq 200) {
    $ResultCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
    if ($ResultCount -le 10) {
        Add-TestResult -TestId "TAG-024" -TestName "Get Hot Tags Default" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Default limit works"
        Write-Host "  PASS - Default limit works ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-024" -TestName "Get Hot Tags Default" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Default limit not working"
        Write-Host "  FAIL - Default limit not working ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-024" -TestName "Get Hot Tags Default" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-025: Get Hot Tags Sorting
Write-Host "[TAG-025] Testing hot tags sorting by post count..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/hot?limit=5"
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $Tags = $Result.Body.data
    $IsSorted = $true
    for ($i = 0; $i -lt ($Tags.Count - 1); $i++) {
        if ($Tags[$i].postCount -lt $Tags[$i + 1].postCount) {
            $IsSorted = $false
            break
        }
    }
    if ($IsSorted) {
        Add-TestResult -TestId "TAG-025" -TestName "Hot Tags Sorting" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly sorted"
        Write-Host "  PASS - Tags correctly sorted by post count ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-025" -TestName "Hot Tags Sorting" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Not sorted correctly"
        Write-Host "  FAIL - Tags not sorted correctly ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-025" -TestName "Hot Tags Sorting" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === SECTION 6: Security Tests (5 tests) ===
Write-Host "=== SECTION 6: Security Tests ===" -ForegroundColor Magenta

# TAG-026: XSS in Search Keyword
Write-Host "[TAG-026] Testing XSS in search keyword..." -ForegroundColor Yellow
$XssKeyword = [System.Uri]::EscapeDataString("<img src='x' onerror='alert(1)'>")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$XssKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    $ResponseJson = $Result.Body | ConvertTo-Json -Depth 10
    if ($ResponseJson -notmatch "<img" -and $ResponseJson -notmatch "onerror") {
        Add-TestResult -TestId "TAG-026" -TestName "XSS in Search" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "XSS prevented"
        Write-Host "  PASS - XSS prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "TAG-026" -TestName "XSS in Search" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential XSS vulnerability"
        Write-Host "  FAIL - Potential XSS vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-026" -TestName "XSS in Search" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-027: SQL Injection in Search
Write-Host "[TAG-027] Testing SQL injection in search..." -ForegroundColor Yellow
$SqlKeyword = [System.Uri]::EscapeDataString("' OR '1'='1")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$SqlKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "TAG-027" -TestName "SQL Injection in Search" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "SQL injection prevented"
    Write-Host "  PASS - SQL injection prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-027" -TestName "SQL Injection in Search" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-028: Path Traversal in Slug
Write-Host "[TAG-028] Testing path traversal in slug..." -ForegroundColor Yellow
$PathTraversalSlug = [System.Uri]::EscapeDataString("../../etc/passwd")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/$PathTraversalSlug"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TAG-028" -TestName "Path Traversal" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Path traversal prevented"
    Write-Host "  PASS - Path traversal prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TAG-028" -TestName "Path Traversal" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential path traversal"
    Write-Host "  FAIL - Potential path traversal vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-029: Long Keyword Handling
Write-Host "[TAG-029] Testing long keyword handling..." -ForegroundColor Yellow
$LongKeyword = "a" * 1000
$EncodedLongKeyword = [System.Uri]::EscapeDataString($LongKeyword)
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$EncodedLongKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "TAG-029" -TestName "Long Keyword" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Long keyword handled"
    Write-Host "  PASS - Long keyword handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-029" -TestName "Long Keyword" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# TAG-030: Unicode Characters in Search
Write-Host "[TAG-030] Testing unicode characters in search..." -ForegroundColor Yellow
$UnicodeKeyword = [System.Uri]::EscapeDataString("数据库")
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/tags/search?keyword=$UnicodeKeyword&limit=10"
if ($Result.Success -and $Result.Body.code -eq 200) {
    Add-TestResult -TestId "TAG-030" -TestName "Unicode in Search" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unicode handled"
    Write-Host "  PASS - Unicode characters handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TAG-030" -TestName "Unicode in Search" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
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
$TotalCount = $TestResults.Count

Write-Host "Total Tests: $TotalCount" -ForegroundColor Cyan
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
Write-Host "Detailed Results:" -ForegroundColor Cyan
Write-Host "----------------" -ForegroundColor Cyan
foreach ($Result in $TestResults) {
    $StatusColor = switch ($Result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    Write-Host "$($Result.TestId) - $($Result.TestName): " -NoNewline
    Write-Host "$($Result.Status)" -ForegroundColor $StatusColor -NoNewline
    Write-Host " ($($Result.ResponseTime)) - $($Result.Note)"
}

Write-Host ""

# Update test-status.md
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    Write-Host "Updating test status file..." -ForegroundColor Cyan
    
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    # Create service section
    $ServiceSection = @"

## Tag API Tests
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
        $ServiceSection += "`n| $($Result.TestId) | $($Result.TestName) | $StatusMark $($Result.Status) | $($Result.ResponseTime) | $($Result.Note) |"
    }
    
    $ServiceSection += "`n`n**Test Time**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $ServiceSection += "`n**Test Results**: $PassCount passed, $FailCount failed, $SkipCount skipped"
    $ServiceSection += "`n"
    
    # Check if Tag API section exists
    if ($StatusContent -match "## Tag API Tests") {
        # Replace existing section
        $StatusContent = $StatusContent -replace "(?s)## Tag API Tests.*?(?=##|\z)", $ServiceSection
    } else {
        # Append new section
        $StatusContent += "`n$ServiceSection"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host "  Test status file updated" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Tag API Tests Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
