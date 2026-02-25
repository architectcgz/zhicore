# Idempotency Test Script
# Test Cases: IDEM-001 to IDEM-010
# Coverage: Repeat operations (like, favorite, follow, check-in, mark read, cancel, delete)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url
$NotificationServiceUrl = $Config.notification_service_url
$MessageServiceUrl = $Config.message_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""
$Global:TestCommentId = ""
$Global:TargetUserId = ""
$Global:NotificationId = ""
$Global:MessageId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "idemtest_$Timestamp"
$UniqueEmail = "idemtest_$Timestamp@example.com"

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
Write-Host "Idempotency Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test user and login ===
Write-Host "=== Setup: Creating test user and logging in ===" -ForegroundColor Magenta
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  User registered: $Global:TestUserId" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Could not register user" -ForegroundColor Red
    exit 1
}

$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    Write-Host "  Login successful" -ForegroundColor Green
} else {
    Write-Host "  FAIL - Could not login" -ForegroundColor Red
    exit 1
}

# Create target user for follow tests
$Timestamp2 = Get-Date -Format "yyyyMMddHHmmssff"
$TargetUsername = "target_$Timestamp2"
$TargetEmail = "target_$Timestamp2@example.com"
$RegisterBody2 = @{ userName = $TargetUsername; email = $TargetEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TargetUserId = $Result.Body.data
    Write-Host "  Target user created: $Global:TargetUserId" -ForegroundColor Green
}

# Create test post
$PostBody = @{ title = "Idempotency Test Post $Timestamp"; content = "Test content for idempotency"; raw = "Test raw"; excerpt = "Test excerpt" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestPostId = $Result.Body.data
    Write-Host "  Test post created: $Global:TestPostId" -ForegroundColor Green
}

# Create test comment
if ($Global:TestPostId) {
    $CommentBody = @{ postId = $Global:TestPostId; content = "Test comment for idempotency" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$CommentServiceUrl/api/v1/comments" -Body $CommentBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:TestCommentId = $Result.Body.data
        Write-Host "  Test comment created: $Global:TestCommentId" -ForegroundColor Green
    }
}

Write-Host ""

# === SECTION 1: Idempotency Tests ===
Write-Host "=== SECTION 1: Idempotency Tests ===" -ForegroundColor Magenta

# IDEM-001: Repeat Like Post
Write-Host "[IDEM-001] Testing repeat like post..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    # First like
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second like (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 409)) {
        Add-TestResult -TestId "IDEM-001" -TestName "Repeat Like Post" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat like handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-001" -TestName "Repeat Like Post" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-001" -TestName "Repeat Like Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# IDEM-002: Repeat Favorite Post
Write-Host "[IDEM-002] Testing repeat favorite post..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    # First favorite
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/favorite" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second favorite (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/favorite" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 409)) {
        Add-TestResult -TestId "IDEM-002" -TestName "Repeat Favorite Post" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat favorite handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-002" -TestName "Repeat Favorite Post" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-002" -TestName "Repeat Favorite Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# IDEM-003: Repeat Follow User
Write-Host "[IDEM-003] Testing repeat follow user..." -ForegroundColor Yellow
if ($Global:TargetUserId) {
    # First follow
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second follow (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 409)) {
        Add-TestResult -TestId "IDEM-003" -TestName "Repeat Follow User" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat follow handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-003" -TestName "Repeat Follow User" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-003" -TestName "Repeat Follow User" -Status "SKIP" -ResponseTime "-" -Note "No target user"
    Write-Host "  SKIP - No target user available" -ForegroundColor Gray
}

# IDEM-004: Repeat Check-In
Write-Host "[IDEM-004] Testing repeat check-in..." -ForegroundColor Yellow
# First check-in
$Result1 = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/check-in" -Headers (Get-AuthHeaders)
Start-Sleep -Milliseconds 100
# Second check-in (should be idempotent)
$Result2 = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/check-in" -Headers (Get-AuthHeaders)

if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 409)) {
    Add-TestResult -TestId "IDEM-004" -TestName "Repeat Check-In" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
    Write-Host "  PASS - Repeat check-in handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
    Add-TestResult -TestId "IDEM-004" -TestName "Repeat Check-In" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
}

# IDEM-005: Repeat Mark Notification Read
Write-Host "[IDEM-005] Testing repeat mark notification read..." -ForegroundColor Yellow
# Get notifications first
$Result = Invoke-ApiRequest -Method "GET" -Url "$NotificationServiceUrl/api/v1/notifications?page=0&size=10" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.content -and $Result.Body.data.content.Count -gt 0) {
    $Global:NotificationId = $Result.Body.data.content[0].id
    # First mark read
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$NotificationServiceUrl/api/v1/notifications/$Global:NotificationId/read" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second mark read (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$NotificationServiceUrl/api/v1/notifications/$Global:NotificationId/read" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200)) {
        Add-TestResult -TestId "IDEM-005" -TestName "Repeat Mark Notification Read" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat mark read handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-005" -TestName "Repeat Mark Notification Read" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-005" -TestName "Repeat Mark Notification Read" -Status "SKIP" -ResponseTime "-" -Note "No notifications"
    Write-Host "  SKIP - No notifications available" -ForegroundColor Gray
}

# IDEM-006: Repeat Mark Message Read
Write-Host "[IDEM-006] Testing repeat mark message read..." -ForegroundColor Yellow
# Get conversations first
$Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations?page=0&size=10" -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.content -and $Result.Body.data.content.Count -gt 0) {
    $ConversationId = $Result.Body.data.content[0].id
    # First mark read
    $Result1 = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/conversations/$ConversationId/read" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second mark read (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/conversations/$ConversationId/read" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.Success -or $Result2.StatusCode -eq 200)) {
        Add-TestResult -TestId "IDEM-006" -TestName "Repeat Mark Message Read" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat mark message read handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-006" -TestName "Repeat Mark Message Read" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-006" -TestName "Repeat Mark Message Read" -Status "SKIP" -ResponseTime "-" -Note "No conversations"
    Write-Host "  SKIP - No conversations available" -ForegroundColor Gray
}

# IDEM-007: Repeat Unlike Post
Write-Host "[IDEM-007] Testing repeat unlike post..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    # First unlike
    $Result1 = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second unlike (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200 -or $Result1.StatusCode -eq 404) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 404)) {
        Add-TestResult -TestId "IDEM-007" -TestName "Repeat Unlike Post" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat unlike handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-007" -TestName "Repeat Unlike Post" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-007" -TestName "Repeat Unlike Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# IDEM-008: Repeat Unfavorite Post
Write-Host "[IDEM-008] Testing repeat unfavorite post..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    # First unfavorite
    $Result1 = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/favorite" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second unfavorite (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/favorite" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200 -or $Result1.StatusCode -eq 404) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 404)) {
        Add-TestResult -TestId "IDEM-008" -TestName "Repeat Unfavorite Post" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat unfavorite handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-008" -TestName "Repeat Unfavorite Post" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-008" -TestName "Repeat Unfavorite Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# IDEM-009: Repeat Unfollow User
Write-Host "[IDEM-009] Testing repeat unfollow user..." -ForegroundColor Yellow
if ($Global:TargetUserId) {
    # First unfollow
    $Result1 = Invoke-ApiRequest -Method "DELETE" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second unfollow (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "DELETE" -Url "$UserServiceUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200 -or $Result1.StatusCode -eq 404) -and ($Result2.Success -or $Result2.StatusCode -eq 200 -or $Result2.StatusCode -eq 404)) {
        Add-TestResult -TestId "IDEM-009" -TestName "Repeat Unfollow User" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat unfollow handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-009" -TestName "Repeat Unfollow User" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-009" -TestName "Repeat Unfollow User" -Status "SKIP" -ResponseTime "-" -Note "No target user"
    Write-Host "  SKIP - No target user available" -ForegroundColor Gray
}

# IDEM-010: Repeat Delete Resource
Write-Host "[IDEM-010] Testing repeat delete resource..." -ForegroundColor Yellow
if ($Global:TestCommentId) {
    # First delete
    $Result1 = Invoke-ApiRequest -Method "DELETE" -Url "$CommentServiceUrl/api/v1/comments/$Global:TestCommentId" -Headers (Get-AuthHeaders)
    Start-Sleep -Milliseconds 100
    # Second delete (should be idempotent)
    $Result2 = Invoke-ApiRequest -Method "DELETE" -Url "$CommentServiceUrl/api/v1/comments/$Global:TestCommentId" -Headers (Get-AuthHeaders)
    
    if (($Result1.Success -or $Result1.StatusCode -eq 200) -and ($Result2.StatusCode -eq 404 -or $Result2.Success)) {
        Add-TestResult -TestId "IDEM-010" -TestName "Repeat Delete Resource" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Idempotent operation"
        Write-Host "  PASS - Repeat delete handled correctly ($($Result2.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
        Add-TestResult -TestId "IDEM-010" -TestName "Repeat Delete Resource" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "IDEM-010" -TestName "Repeat Delete Resource" -Status "SKIP" -ResponseTime "-" -Note "No test comment"
    Write-Host "  SKIP - No test comment available" -ForegroundColor Gray
}

# === Test Results Summary ===
Write-Host ""
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
foreach ($Result in $TestResults) {
    $StatusColor = switch ($Result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    Write-Host "[$($Result.Status)] $($Result.TestId) - $($Result.TestName) ($($Result.ResponseTime)) - $($Result.Note)" -ForegroundColor $StatusColor
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
