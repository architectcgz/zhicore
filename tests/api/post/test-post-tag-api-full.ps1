# Post Tag API Full Test Script
# Test Cases: POSTTAG-001 to POSTTAG-025 (including error scenarios and security tests)
# Coverage: Attach Tags(8), Detach Tag(7), Get Post Tags(5), Security(5)

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
$Global:SecondUserId = ""
$Global:SecondAccessToken = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "posttagtest_$Timestamp"
$UniqueEmail = "posttagtest_$Timestamp@example.com"

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

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Post Tag API Full Tests" -ForegroundColor Cyan
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

# Create a second user for permission tests
Write-Host "Creating second test user..." -ForegroundColor Cyan
$Timestamp2 = Get-Date -Format "yyyyMMddHHmmssff"
$SecondUsername = "posttagtest2_$Timestamp2"
$SecondEmail = "posttagtest2_$Timestamp2@example.com"
$RegisterBody2 = @{ userName = $SecondUsername; email = $SecondEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:SecondUserId = $Result.Body.data
    Write-Host "  Created second user, ID: $Global:SecondUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create second user: $($Result.Body.message)" -ForegroundColor Yellow
}

Write-Host "Logging in second test user..." -ForegroundColor Cyan
$LoginBody2 = @{ email = $SecondEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody2
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:SecondAccessToken = $Result.Body.data.accessToken
    Write-Host "  Second user login successful" -ForegroundColor Cyan
} else {
    Write-Host "  Second user login failed: $($Result.Body.message)" -ForegroundColor Yellow
}

# Create a test post
Write-Host "Creating test post..." -ForegroundColor Cyan
if ($Global:AccessToken) {
    $CreatePostBody = @{ 
        title = "Test Post for Tag Management $Timestamp"
        content = "This is test content for tag management"
        tags = @("Initial", "Test")
    }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $CreatePostBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $Global:TestPostId = $Result.Body.data
        Write-Host "  Post created with ID: $Global:TestPostId" -ForegroundColor Cyan
    } else {
        Write-Host "  Failed to create post: $($Result.Body.message)" -ForegroundColor Yellow
    }
}

Write-Host ""

# === SECTION 1: Attach Tags Tests (8 tests) ===
Write-Host "=== SECTION 1: Attach Tags Tests ===" -ForegroundColor Magenta

# POSTTAG-001: Attach Tags (Normal)
Write-Host "[POSTTAG-001] Testing attach tags to post..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $AttachBody = @{ tags = @("Java", "Spring Boot", "PostgreSQL") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-001" -TestName "Attach Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Tags attached"
        Write-Host "  PASS - Tags attached successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-001" -TestName "Attach Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-001" -TestName "Attach Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-002: Attach Empty Tags
Write-Host "[POSTTAG-002] Testing attach empty tags..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $AttachBody = @{ tags = @() }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-002" -TestName "Attach Empty Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Empty tags accepted"
        Write-Host "  PASS - Empty tags accepted ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-002" -TestName "Attach Empty Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-002" -TestName "Attach Empty Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-003: Attach Too Many Tags
Write-Host "[POSTTAG-003] Testing attach too many tags (>10)..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $TooManyTags = @("Tag1", "Tag2", "Tag3", "Tag4", "Tag5", "Tag6", "Tag7", "Tag8", "Tag9", "Tag10", "Tag11")
    $AttachBody = @{ tags = $TooManyTags }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-003" -TestName "Attach Too Many Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected too many tags ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-003" -TestName "Attach Too Many Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject >10 tags"
        Write-Host "  FAIL - Should reject >10 tags ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-003" -TestName "Attach Too Many Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-004: Attach Tags to Non-existent Post
Write-Host "[POSTTAG-004] Testing attach tags to non-existent post..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $NonExistentPostId = 999999999
    $AttachBody = @{ tags = @("Java") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$NonExistentPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-004" -TestName "Attach Tags Non-existent Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
        Write-Host "  PASS - Correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-004" -TestName "Attach Tags Non-existent Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-004" -TestName "Attach Tags Non-existent Post" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
}

# POSTTAG-005: Attach Tags Without Auth
Write-Host "[POSTTAG-005] Testing attach tags without authentication..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $AttachBody = @{ tags = @("Java") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody
    if ($Result.StatusCode -eq 401 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-005" -TestName "Attach Tags No Auth" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 401"
        Write-Host "  PASS - Correctly returned 401 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-005" -TestName "Attach Tags No Auth" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 401"
        Write-Host "  FAIL - Should return 401 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-005" -TestName "Attach Tags No Auth" -Status "SKIP" -ResponseTime "-" -Note "No post"
    Write-Host "  SKIP - No post available" -ForegroundColor Gray
}

# POSTTAG-006: Attach Tags to Other User's Post
Write-Host "[POSTTAG-006] Testing attach tags to other user's post..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:SecondAccessToken) {
    $AttachBody = @{ tags = @("Unauthorized") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-SecondAuthHeaders)
    if ($Result.StatusCode -eq 403 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-006" -TestName "Attach Tags Other User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 403"
        Write-Host "  PASS - Correctly returned 403 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-006" -TestName "Attach Tags Other User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 403"
        Write-Host "  FAIL - Should return 403 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-006" -TestName "Attach Tags Other User" -Status "SKIP" -ResponseTime "-" -Note "No post or second token"
    Write-Host "  SKIP - No post or second token available" -ForegroundColor Gray
}

# POSTTAG-007: Attach Tags with Invalid Post ID
Write-Host "[POSTTAG-007] Testing attach tags with invalid post ID..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $InvalidPostId = -1
    $AttachBody = @{ tags = @("Java") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$InvalidPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-007" -TestName "Attach Tags Invalid ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-007" -TestName "Attach Tags Invalid ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid ID"
        Write-Host "  FAIL - Should reject invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-007" -TestName "Attach Tags Invalid ID" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
}

# POSTTAG-008: Replace Tags (Verify Replacement)
Write-Host "[POSTTAG-008] Testing replace tags (verify replacement)..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    # First attach some tags
    $AttachBody1 = @{ tags = @("OldTag1", "OldTag2") }
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody1 -Headers (Get-AuthHeaders)
    
    # Then replace with new tags
    $AttachBody2 = @{ tags = @("NewTag1", "NewTag2") }
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody2 -Headers (Get-AuthHeaders)
    
    # Get tags to verify
    $Result3 = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags"
    
    if ($Result3.Success -and $Result3.Body.code -eq 200 -and $Result3.Body.data) {
        $TagNames = $Result3.Body.data | ForEach-Object { $_.name }
        $HasNewTags = ($TagNames -contains "NewTag1") -and ($TagNames -contains "NewTag2")
        $HasOldTags = ($TagNames -contains "OldTag1") -or ($TagNames -contains "OldTag2")
        
        if ($HasNewTags -and -not $HasOldTags) {
            Add-TestResult -TestId "POSTTAG-008" -TestName "Replace Tags" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Tags replaced correctly"
            Write-Host "  PASS - Tags replaced correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "POSTTAG-008" -TestName "Replace Tags" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Tags not replaced"
            Write-Host "  FAIL - Tags not replaced correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        $ErrorMsg = if ($Result3.Body.message) { $Result3.Body.message } else { $Result3.Error }
        Add-TestResult -TestId "POSTTAG-008" -TestName "Replace Tags" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-008" -TestName "Replace Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 2: Detach Tag Tests (7 tests) ===
Write-Host "=== SECTION 2: Detach Tag Tests ===" -ForegroundColor Magenta

# Setup: Attach tags for detach tests
if ($Global:TestPostId -and $Global:AccessToken) {
    $AttachBody = @{ tags = @("Java", "Spring Boot", "PostgreSQL") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
}

# POSTTAG-009: Detach Tag (Normal)
Write-Host "[POSTTAG-009] Testing detach tag from post..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $TagSlug = "java"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/$TagSlug" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-009" -TestName "Detach Tag" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Tag detached"
        Write-Host "  PASS - Tag detached successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-009" -TestName "Detach Tag" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-009" -TestName "Detach Tag" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-010: Detach Non-existent Tag
Write-Host "[POSTTAG-010] Testing detach non-existent tag..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $NonExistentSlug = "non-existent-tag-$Timestamp"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/$NonExistentSlug" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-010" -TestName "Detach Non-existent Tag" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Non-existent tag handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-010" -TestName "Detach Non-existent Tag" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-010" -TestName "Detach Non-existent Tag" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-011: Detach Tag from Non-existent Post
Write-Host "[POSTTAG-011] Testing detach tag from non-existent post..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $NonExistentPostId = 999999999
    $TagSlug = "java"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$NonExistentPostId/tags/$TagSlug" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-011" -TestName "Detach Tag Non-existent Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
        Write-Host "  PASS - Correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-011" -TestName "Detach Tag Non-existent Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-011" -TestName "Detach Tag Non-existent Post" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
}

# POSTTAG-012: Detach Tag Without Auth
Write-Host "[POSTTAG-012] Testing detach tag without authentication..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $TagSlug = "spring-boot"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/$TagSlug"
    if ($Result.StatusCode -eq 401 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-012" -TestName "Detach Tag No Auth" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 401"
        Write-Host "  PASS - Correctly returned 401 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-012" -TestName "Detach Tag No Auth" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 401"
        Write-Host "  FAIL - Should return 401 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-012" -TestName "Detach Tag No Auth" -Status "SKIP" -ResponseTime "-" -Note "No post"
    Write-Host "  SKIP - No post available" -ForegroundColor Gray
}

# POSTTAG-013: Detach Tag from Other User's Post
Write-Host "[POSTTAG-013] Testing detach tag from other user's post..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:SecondAccessToken) {
    $TagSlug = "spring-boot"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/$TagSlug" -Headers (Get-SecondAuthHeaders)
    if ($Result.StatusCode -eq 403 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-013" -TestName "Detach Tag Other User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 403"
        Write-Host "  PASS - Correctly returned 403 ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-013" -TestName "Detach Tag Other User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 403"
        Write-Host "  FAIL - Should return 403 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-013" -TestName "Detach Tag Other User" -Status "SKIP" -ResponseTime "-" -Note "No post or second token"
    Write-Host "  SKIP - No post or second token available" -ForegroundColor Gray
}

# POSTTAG-014: Detach Tag with Invalid Post ID
Write-Host "[POSTTAG-014] Testing detach tag with invalid post ID..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $InvalidPostId = -1
    $TagSlug = "java"
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$InvalidPostId/tags/$TagSlug" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-014" -TestName "Detach Tag Invalid ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Correctly rejected invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-014" -TestName "Detach Tag Invalid ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid ID"
        Write-Host "  FAIL - Should reject invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-014" -TestName "Detach Tag Invalid ID" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
}

# POSTTAG-015: Verify Tag Detachment
Write-Host "[POSTTAG-015] Testing verify tag detachment..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    # Attach tags first
    $AttachBody = @{ tags = @("VerifyTag1", "VerifyTag2", "VerifyTag3") }
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
    
    # Detach one tag
    $Result2 = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/verifytag2" -Headers (Get-AuthHeaders)
    
    # Get tags to verify
    $Result3 = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags"
    
    if ($Result3.Success -and $Result3.Body.code -eq 200 -and $Result3.Body.data) {
        $TagNames = $Result3.Body.data | ForEach-Object { $_.name }
        $HasRemainingTags = ($TagNames -contains "VerifyTag1") -and ($TagNames -contains "VerifyTag3")
        $HasDetachedTag = $TagNames -contains "VerifyTag2"
        
        if ($HasRemainingTags -and -not $HasDetachedTag) {
            Add-TestResult -TestId "POSTTAG-015" -TestName "Verify Tag Detachment" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Tag detached correctly"
            Write-Host "  PASS - Tag detached correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "POSTTAG-015" -TestName "Verify Tag Detachment" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Tag not detached"
            Write-Host "  FAIL - Tag not detached correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        $ErrorMsg = if ($Result3.Body.message) { $Result3.Body.message } else { $Result3.Error }
        Add-TestResult -TestId "POSTTAG-015" -TestName "Verify Tag Detachment" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-015" -TestName "Verify Tag Detachment" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 3: Get Post Tags Tests (5 tests) ===
Write-Host "=== SECTION 3: Get Post Tags Tests ===" -ForegroundColor Magenta

# Setup: Attach tags for get tests
if ($Global:TestPostId -and $Global:AccessToken) {
    $AttachBody = @{ tags = @("GetTest1", "GetTest2", "GetTest3") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $AttachBody -Headers (Get-AuthHeaders)
}

# POSTTAG-016: Get Post Tags (Normal)
Write-Host "[POSTTAG-016] Testing get post tags..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags"
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $TagCount = $Result.Body.data.Count
        Add-TestResult -TestId "POSTTAG-016" -TestName "Get Post Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Tags: $TagCount"
        Write-Host "  PASS - Tags retrieved, count: $TagCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-016" -TestName "Get Post Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-016" -TestName "Get Post Tags" -Status "SKIP" -ResponseTime "-" -Note "No post"
    Write-Host "  SKIP - No post available" -ForegroundColor Gray
}

# POSTTAG-017: Get Tags from Non-existent Post
Write-Host "[POSTTAG-017] Testing get tags from non-existent post..." -ForegroundColor Yellow
$NonExistentPostId = 999999999
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$NonExistentPostId/tags"
if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "POSTTAG-017" -TestName "Get Tags Non-existent Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned 404"
    Write-Host "  PASS - Correctly returned 404 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "POSTTAG-017" -TestName "Get Tags Non-existent Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
    Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# POSTTAG-018: Get Tags with Invalid Post ID
Write-Host "[POSTTAG-018] Testing get tags with invalid post ID..." -ForegroundColor Yellow
$InvalidPostId = -1
$Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$InvalidPostId/tags"
if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "POSTTAG-018" -TestName "Get Tags Invalid ID" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
    Write-Host "  PASS - Correctly rejected invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "POSTTAG-018" -TestName "Get Tags Invalid ID" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject invalid ID"
    Write-Host "  FAIL - Should reject invalid ID ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

# POSTTAG-019: Get Tags from Post with No Tags
Write-Host "[POSTTAG-019] Testing get tags from post with no tags..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    # Create a new post without tags
    $CreatePostBody = @{ 
        title = "Post Without Tags $Timestamp"
        content = "This post has no tags"
    }
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $CreatePostBody -Headers (Get-AuthHeaders)
    
    if ($Result1.Success -and $Result1.Body.code -eq 200 -and $Result1.Body.data) {
        $NoTagsPostId = $Result1.Body.data
        $Result2 = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$NoTagsPostId/tags"
        
        if ($Result2.Success -and $Result2.Body.code -eq 200) {
            $TagCount = if ($Result2.Body.data) { $Result2.Body.data.Count } else { 0 }
            if ($TagCount -eq 0) {
                Add-TestResult -TestId "POSTTAG-019" -TestName "Get Tags No Tags" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Empty list returned"
                Write-Host "  PASS - Empty list returned ($($Result2.ResponseTime)ms)" -ForegroundColor Green
            } else {
                Add-TestResult -TestId "POSTTAG-019" -TestName "Get Tags No Tags" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Should return empty list"
                Write-Host "  FAIL - Should return empty list ($($Result2.ResponseTime)ms)" -ForegroundColor Red
            }
        } else {
            $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
            Add-TestResult -TestId "POSTTAG-019" -TestName "Get Tags No Tags" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "POSTTAG-019" -TestName "Get Tags No Tags" -Status "SKIP" -ResponseTime "-" -Note "Failed to create post"
        Write-Host "  SKIP - Failed to create post" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "POSTTAG-019" -TestName "Get Tags No Tags" -Status "SKIP" -ResponseTime "-" -Note "No token"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
}

# POSTTAG-020: Verify Tag Data Structure
Write-Host "[POSTTAG-020] Testing verify tag data structure..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags"
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data -and $Result.Body.data.Count -gt 0) {
        $FirstTag = $Result.Body.data[0]
        $HasId = $null -ne $FirstTag.id
        $HasName = $null -ne $FirstTag.name
        $HasSlug = $null -ne $FirstTag.slug
        
        if ($HasId -and $HasName -and $HasSlug) {
            Add-TestResult -TestId "POSTTAG-020" -TestName "Verify Tag Structure" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Structure correct"
            Write-Host "  PASS - Tag structure correct ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "POSTTAG-020" -TestName "Verify Tag Structure" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Missing fields"
            Write-Host "  FAIL - Tag structure missing fields ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "POSTTAG-020" -TestName "Verify Tag Structure" -Status "SKIP" -ResponseTime "-" -Note "No tags to verify"
        Write-Host "  SKIP - No tags to verify" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "POSTTAG-020" -TestName "Verify Tag Structure" -Status "SKIP" -ResponseTime "-" -Note "No post"
    Write-Host "  SKIP - No post available" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 4: Security Tests (5 tests) ===
Write-Host "=== SECTION 4: Security Tests ===" -ForegroundColor Magenta

# POSTTAG-021: XSS in Tag Name
Write-Host "[POSTTAG-021] Testing XSS in tag name..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $XssBody = @{ tags = @("<script>alert('xss')</script>", "NormalTag") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $XssBody -Headers (Get-AuthHeaders)
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        # Get tags to verify XSS was sanitized
        $Result2 = Invoke-ApiRequest -Method "GET" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags"
        if ($Result2.Success -and $Result2.Body.code -eq 200) {
            $ResponseJson = $Result2.Body | ConvertTo-Json -Depth 10
            if ($ResponseJson -notmatch "<script" -and $ResponseJson -notmatch "alert") {
                Add-TestResult -TestId "POSTTAG-021" -TestName "XSS in Tag Name" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "XSS prevented"
                Write-Host "  PASS - XSS prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
            } else {
                Add-TestResult -TestId "POSTTAG-021" -TestName "XSS in Tag Name" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Potential XSS"
                Write-Host "  FAIL - Potential XSS vulnerability ($($Result.ResponseTime)ms)" -ForegroundColor Red
            }
        } else {
            Add-TestResult -TestId "POSTTAG-021" -TestName "XSS in Tag Name" -Status "SKIP" -ResponseTime "-" -Note "Could not verify"
            Write-Host "  SKIP - Could not verify XSS prevention" -ForegroundColor Gray
        }
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-021" -TestName "XSS in Tag Name" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-021" -TestName "XSS in Tag Name" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-022: SQL Injection in Tag Slug
Write-Host "[POSTTAG-022] Testing SQL injection in tag slug..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $SqlSlug = [System.Uri]::EscapeDataString("' OR '1'='1")
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags/$SqlSlug" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-022" -TestName "SQL Injection in Slug" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "SQL injection prevented"
        Write-Host "  PASS - SQL injection prevented ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-022" -TestName "SQL Injection in Slug" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-022" -TestName "SQL Injection in Slug" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-023: Special Characters in Tag Names
Write-Host "[POSTTAG-023] Testing special characters in tag names..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $SpecialBody = @{ tags = @("C++", "C#", ".NET", "Node.js") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $SpecialBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-023" -TestName "Special Chars in Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Special chars handled"
        Write-Host "  PASS - Special characters handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-023" -TestName "Special Chars in Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-023" -TestName "Special Chars in Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-024: Unicode Characters in Tag Names
Write-Host "[POSTTAG-024] Testing unicode characters in tag names..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $UnicodeBody = @{ tags = @("数据库", "编程", "日本語") }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $UnicodeBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "POSTTAG-024" -TestName "Unicode in Tags" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unicode handled"
        Write-Host "  PASS - Unicode characters handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "POSTTAG-024" -TestName "Unicode in Tags" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-024" -TestName "Unicode in Tags" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
}

# POSTTAG-025: Long Tag Names
Write-Host "[POSTTAG-025] Testing long tag names..." -ForegroundColor Yellow
if ($Global:TestPostId -and $Global:AccessToken) {
    $LongTagName = "a" * 100
    $LongBody = @{ tags = @($LongTagName) }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/tags" -Body $LongBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "POSTTAG-025" -TestName "Long Tag Names" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Long tag name correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "POSTTAG-025" -TestName "Long Tag Names" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject long names"
        Write-Host "  FAIL - Should reject long tag names ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "POSTTAG-025" -TestName "Long Tag Names" -Status "SKIP" -ResponseTime "-" -Note "No post or token"
    Write-Host "  SKIP - No post or token available" -ForegroundColor Gray
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

## Post Tag API Tests
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
    
    # Check if Post Tag API section exists
    if ($StatusContent -match "## Post Tag API Tests") {
        # Replace existing section
        $StatusContent = $StatusContent -replace "(?s)## Post Tag API Tests.*?(?=##|\z)", $ServiceSection
    } else {
        # Append new section
        $StatusContent += "`n$ServiceSection"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host "  Test status file updated" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Post Tag API Tests Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
