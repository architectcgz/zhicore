# Admin Service API Full Test Script
# Test Cases: ADMIN-001 to ADMIN-025 (including error scenarios)
# Coverage: User Management(7), Post Management(6), Comment Management(6), Report Management(6)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

# === Initialize Configuration ===
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$GatewayUrl = $Config.gateway_url
$AdminServiceUrl = "$GatewayUrl/api/v1"  # 通过网关访问，路径会被 StripPrefix 处理
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url
$TestUser = $Config.test_user
$AdminUser = $Config.admin_user

# === Global Variables ===
$TestResults = @()
$Global:AdminAccessToken = ""
$Global:AdminUserId = ""
$Global:TestAccessToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""
$Global:TestCommentId = ""
$Global:TestReportId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$AdminUsername = "admin_$Timestamp"
$AdminEmail = "admin_$Timestamp@example.com"
$TestUsername = "testuser_$Timestamp"
$TestEmail = "testuser_$Timestamp@example.com"

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

function Get-AdminHeaders { 
    return @{ 
        "Authorization" = "Bearer $Global:AdminAccessToken"
    } 
}

function Get-TestHeaders { return @{ "Authorization" = "Bearer $Global:TestAccessToken" } }

# === Test Start ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Admin Service API Full Tests" -ForegroundColor Cyan
Write-Host "Service URL: $AdminServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create Admin and Test Users ===
Write-Host "=== Setup: Creating Admin and Test Users ===" -ForegroundColor Magenta

# Create admin user
Write-Host "Creating admin user..." -ForegroundColor Cyan
$AdminRegisterBody = @{ userName = $AdminUsername; email = $AdminEmail; password = $AdminUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $AdminRegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AdminUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Admin user created, ID: $Global:AdminUserId" -ForegroundColor Green
    
    # Login admin user to get initial token
    $AdminLoginBody = @{ email = $AdminEmail; password = $AdminUser.password }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $AdminLoginBody
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $TempAdminToken = $Result.Body.data.accessToken
        Write-Host "  Admin user logged in" -ForegroundColor Green
        
        # Assign ADMIN role
        Write-Host "  Assigning ADMIN role..." -ForegroundColor Cyan
        $TempHeaders = @{ "Authorization" = "Bearer $TempAdminToken" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/users/$Global:AdminUserId/roles/ADMIN" -Headers $TempHeaders
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Write-Host "  ADMIN role assigned" -ForegroundColor Green
            
            # Re-login to get token with ADMIN role
            $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $AdminLoginBody
            if ($Result.Success -and $Result.Body.code -eq 200) {
                $Global:AdminAccessToken = $Result.Body.data.accessToken
                Write-Host "  Admin user re-logged in with ADMIN role" -ForegroundColor Green
            }
        } else {
            Write-Host "  Failed to assign ADMIN role" -ForegroundColor Yellow
            $Global:AdminAccessToken = $TempAdminToken
        }
    }
} else {
    Write-Host "  Failed to create admin user" -ForegroundColor Red
}

# Create test user
Write-Host "Creating test user..." -ForegroundColor Cyan
$TestRegisterBody = @{ userName = $TestUsername; email = $TestEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $TestRegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data  # data is the userId string directly
    Write-Host "  Test user created, ID: $Global:TestUserId" -ForegroundColor Green
    
    # Login test user
    $TestLoginBody = @{ email = $TestEmail; password = $TestUser.password }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $TestLoginBody
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:TestAccessToken = $Result.Body.data.accessToken
        Write-Host "  Test user logged in" -ForegroundColor Green
    }
} else {
    Write-Host "  Failed to create test user" -ForegroundColor Red
}

# Create test post
Write-Host "Creating test post..." -ForegroundColor Cyan
if ($Global:TestAccessToken) {
    $PostBody = @{ title = "Test Post $Timestamp"; content = "Test content for admin tests"; status = "PUBLISHED" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-TestHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:TestPostId = $Result.Body.data
        Write-Host "  Test post created, ID: $Global:TestPostId" -ForegroundColor Green
    } else {
        Write-Host "  Failed to create test post" -ForegroundColor Yellow
    }
}

# Create test comment
Write-Host "Creating test comment..." -ForegroundColor Cyan
if ($Global:TestAccessToken -and $Global:TestPostId) {
    $CommentBody = @{ postId = $Global:TestPostId; content = "Test comment for admin tests" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$CommentServiceUrl/api/v1/comments" -Body $CommentBody -Headers (Get-TestHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Global:TestCommentId = $Result.Body.data
        Write-Host "  Test comment created, ID: $Global:TestCommentId" -ForegroundColor Green
    } else {
        Write-Host "  Failed to create test comment" -ForegroundColor Yellow
    }
}

Write-Host ""

# === SECTION 1: User Management Tests ===
Write-Host "=== SECTION 1: User Management Tests ===" -ForegroundColor Magenta

# ADMIN-001: Get User List
Write-Host "[ADMIN-001] Testing get user list..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/users?page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $UserCount = if ($Result.Body.data.items) { $Result.Body.data.items.Count } else { 0 }
        Add-TestResult -TestId "ADMIN-001" -TestName "Get User List" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Users: $UserCount"
        Write-Host "  PASS - Got user list, count: $UserCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-001" -TestName "Get User List" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-001" -TestName "Get User List" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-002: Search Users
Write-Host "[ADMIN-002] Testing search users..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/users?keyword=test&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-002" -TestName "Search Users" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Search successful"
        Write-Host "  PASS - User search successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-002" -TestName "Search Users" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-002" -TestName "Search Users" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-003: Disable User
Write-Host "[ADMIN-003] Testing disable user..." -ForegroundColor Yellow
if ($Global:AdminAccessToken -and $Global:TestUserId) {
    $DisableBody = @{ reason = "Test disable reason" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/users/$Global:TestUserId/disable" -Body $DisableBody -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-003" -TestName "Disable User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "User disabled"
        Write-Host "  PASS - User disabled successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-003" -TestName "Disable User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-003" -TestName "Disable User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# ADMIN-004: Enable User
Write-Host "[ADMIN-004] Testing enable user..." -ForegroundColor Yellow
if ($Global:AdminAccessToken -and $Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/users/$Global:TestUserId/enable" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-004" -TestName "Enable User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "User enabled"
        Write-Host "  PASS - User enabled successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-004" -TestName "Enable User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-004" -TestName "Enable User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# ADMIN-005: Disable Non-existent User
Write-Host "[ADMIN-005] Testing disable non-existent user..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $FakeUserId = "999999999999999999"
    $DisableBody = @{ reason = "Test reason" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/users/$FakeUserId/disable" -Body $DisableBody -Headers (Get-AdminHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-005" -TestName "Disable Non-existent" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-existent user correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-005" -TestName "Disable Non-existent" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-005" -TestName "Disable Non-existent" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-006: Non-admin Access
Write-Host "[ADMIN-006] Testing non-admin access..." -ForegroundColor Yellow
if ($Global:TestAccessToken -and $Global:TestUserId) {
    $TestAdminHeaders = @{ 
        "Authorization" = "Bearer $Global:TestAccessToken"
        "X-Admin-Id" = $Global:TestUserId
    }
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/users?page=1&size=20" -Headers $TestAdminHeaders
    if ($Result.StatusCode -eq 403 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-006" -TestName "Non-admin Access" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-admin correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-006" -TestName "Non-admin Access" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 403"
        Write-Host "  FAIL - Should return 403 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-006" -TestName "Non-admin Access" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# ADMIN-007: Get User Details
Write-Host "[ADMIN-007] Testing get user details..." -ForegroundColor Yellow
if ($Global:AdminAccessToken -and $Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/users?keyword=$TestUsername&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-007" -TestName "Get User Details" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Details retrieved"
        Write-Host "  PASS - User details retrieved ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-007" -TestName "Get User Details" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-007" -TestName "Get User Details" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 2: Post Management Tests ===
Write-Host "=== SECTION 2: Post Management Tests ===" -ForegroundColor Magenta

# ADMIN-008: Get Post List
Write-Host "[ADMIN-008] Testing get post list..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/posts?page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $PostCount = if ($Result.Body.data.items) { $Result.Body.data.items.Count } else { 0 }
        Add-TestResult -TestId "ADMIN-008" -TestName "Get Post List" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Posts: $PostCount"
        Write-Host "  PASS - Got post list, count: $PostCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-008" -TestName "Get Post List" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-008" -TestName "Get Post List" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-009: Search Posts
Write-Host "[ADMIN-009] Testing search posts..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/posts?keyword=test&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-009" -TestName "Search Posts" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Search successful"
        Write-Host "  PASS - Post search successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-009" -TestName "Search Posts" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-009" -TestName "Search Posts" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-010: Delete Post
Write-Host "[ADMIN-010] Testing delete post..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    # Ensure we have a post to delete - create one if needed
    if (-not $Global:TestPostId -and $Global:TestAccessToken) {
        Write-Host "  Creating post for delete test..." -ForegroundColor Cyan
        $PostBody = @{ title = "Post for Delete Test $Timestamp"; content = "Content for delete test"; status = "PUBLISHED" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-TestHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            $Global:TestPostId = $Result.Body.data
            Write-Host "  Post created for delete test, ID: $Global:TestPostId" -ForegroundColor Green
        }
    }
    
    if ($Global:TestPostId) {
        $DeleteBody = @{ reason = "Test delete reason" }
        $Result = Invoke-ApiRequest -Method "DELETE" -Url "$AdminServiceUrl/admin/posts/$Global:TestPostId" -Body $DeleteBody -Headers (Get-AdminHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "ADMIN-010" -TestName "Delete Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Post deleted"
            Write-Host "  PASS - Post deleted successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
            # Clear the post ID since it's been deleted
            $Global:TestPostId = ""
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "ADMIN-010" -TestName "Delete Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "ADMIN-010" -TestName "Delete Post" -Status "SKIP" -ResponseTime "-" -Note "Could not create test post"
        Write-Host "  SKIP - Could not create test post" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "ADMIN-010" -TestName "Delete Post" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-011: Delete Non-existent Post
Write-Host "[ADMIN-011] Testing delete non-existent post..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $FakePostId = "999999999999999999"
    $DeleteBody = @{ reason = "Test reason" }
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$AdminServiceUrl/admin/posts/$FakePostId" -Body $DeleteBody -Headers (Get-AdminHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-011" -TestName "Delete Non-existent Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-existent post correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-011" -TestName "Delete Non-existent Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-011" -TestName "Delete Non-existent Post" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-012: Filter Posts by Author
Write-Host "[ADMIN-012] Testing filter posts by author..." -ForegroundColor Yellow
if ($Global:AdminAccessToken -and $Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/posts?authorId=$Global:TestUserId&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-012" -TestName "Filter Posts by Author" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Filter successful"
        Write-Host "  PASS - Filter by author successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-012" -TestName "Filter Posts by Author" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-012" -TestName "Filter Posts by Author" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# ADMIN-013: Filter Posts by Status
Write-Host "[ADMIN-013] Testing filter posts by status..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/posts?status=PUBLISHED&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-013" -TestName "Filter Posts by Status" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Filter successful"
        Write-Host "  PASS - Filter by status successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-013" -TestName "Filter Posts by Status" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-013" -TestName "Filter Posts by Status" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 3: Comment Management Tests ===
Write-Host "=== SECTION 3: Comment Management Tests ===" -ForegroundColor Magenta

# ADMIN-014: Get Comment List
Write-Host "[ADMIN-014] Testing get comment list..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/comments?page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $CommentCount = if ($Result.Body.data.items) { $Result.Body.data.items.Count } else { 0 }
        Add-TestResult -TestId "ADMIN-014" -TestName "Get Comment List" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Comments: $CommentCount"
        Write-Host "  PASS - Got comment list, count: $CommentCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-014" -TestName "Get Comment List" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-014" -TestName "Get Comment List" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-015: Search Comments
Write-Host "[ADMIN-015] Testing search comments..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/comments?keyword=test&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-015" -TestName "Search Comments" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Search successful"
        Write-Host "  PASS - Comment search successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-015" -TestName "Search Comments" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-015" -TestName "Search Comments" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-016: Delete Comment
Write-Host "[ADMIN-016] Testing delete comment..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    # Ensure we have a post and comment to delete - create them if needed
    if (-not $Global:TestPostId -and $Global:TestAccessToken) {
        Write-Host "  Creating post for comment delete test..." -ForegroundColor Cyan
        $PostBody = @{ title = "Post for Comment Delete Test $Timestamp"; content = "Content for comment delete test"; status = "PUBLISHED" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-TestHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            $Global:TestPostId = $Result.Body.data
            Write-Host "  Post created, ID: $Global:TestPostId" -ForegroundColor Green
        }
    }
    
    if (-not $Global:TestCommentId -and $Global:TestAccessToken -and $Global:TestPostId) {
        Write-Host "  Creating comment for delete test..." -ForegroundColor Cyan
        $CommentBody = @{ postId = $Global:TestPostId; content = "Comment for delete test" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$CommentServiceUrl/api/v1/comments" -Body $CommentBody -Headers (Get-TestHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            $Global:TestCommentId = $Result.Body.data
            Write-Host "  Comment created, ID: $Global:TestCommentId" -ForegroundColor Green
        }
    }
    
    if ($Global:TestCommentId) {
        $DeleteBody = @{ reason = "Test delete reason" }
        $Result = Invoke-ApiRequest -Method "DELETE" -Url "$AdminServiceUrl/admin/comments/$Global:TestCommentId" -Body $DeleteBody -Headers (Get-AdminHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "ADMIN-016" -TestName "Delete Comment" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Comment deleted"
            Write-Host "  PASS - Comment deleted successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
            # Clear the comment ID since it's been deleted
            $Global:TestCommentId = ""
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "ADMIN-016" -TestName "Delete Comment" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "ADMIN-016" -TestName "Delete Comment" -Status "SKIP" -ResponseTime "-" -Note "Could not create test comment"
        Write-Host "  SKIP - Could not create test comment" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "ADMIN-016" -TestName "Delete Comment" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-017: Delete Non-existent Comment
Write-Host "[ADMIN-017] Testing delete non-existent comment..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $FakeCommentId = "999999999999999999"
    $DeleteBody = @{ reason = "Test reason" }
    $Result = Invoke-ApiRequest -Method "DELETE" -Url "$AdminServiceUrl/admin/comments/$FakeCommentId" -Body $DeleteBody -Headers (Get-AdminHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-017" -TestName "Delete Non-existent Comment" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-existent comment correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-017" -TestName "Delete Non-existent Comment" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-017" -TestName "Delete Non-existent Comment" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-018: Filter Comments by Post
Write-Host "[ADMIN-018] Testing filter comments by post..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    # Ensure we have a post - create one if needed
    if (-not $Global:TestPostId -and $Global:TestAccessToken) {
        Write-Host "  Creating post for filter test..." -ForegroundColor Cyan
        $PostBody = @{ title = "Post for Filter Test $Timestamp"; content = "Content for filter test"; status = "PUBLISHED" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-TestHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            $Global:TestPostId = $Result.Body.data
            Write-Host "  Post created, ID: $Global:TestPostId" -ForegroundColor Green
        }
    }
    
    if ($Global:TestPostId) {
        $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/comments?postId=$Global:TestPostId&page=1&size=20" -Headers (Get-AdminHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "ADMIN-018" -TestName "Filter Comments by Post" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Filter successful"
            Write-Host "  PASS - Filter by post successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "ADMIN-018" -TestName "Filter Comments by Post" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "ADMIN-018" -TestName "Filter Comments by Post" -Status "SKIP" -ResponseTime "-" -Note "Could not create test post"
        Write-Host "  SKIP - Could not create test post" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "ADMIN-018" -TestName "Filter Comments by Post" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-019: Filter Comments by User
Write-Host "[ADMIN-019] Testing filter comments by user..." -ForegroundColor Yellow
if ($Global:AdminAccessToken -and $Global:TestUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/comments?userId=$Global:TestUserId&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-019" -TestName "Filter Comments by User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Filter successful"
        Write-Host "  PASS - Filter by user successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-019" -TestName "Filter Comments by User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-019" -TestName "Filter Comments by User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""

# === SECTION 4: Report Management Tests ===
Write-Host "=== SECTION 4: Report Management Tests ===" -ForegroundColor Magenta

# ADMIN-020: Get Pending Reports
Write-Host "[ADMIN-020] Testing get pending reports..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/reports/pending?page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ReportCount = if ($Result.Body.data.items) { $Result.Body.data.items.Count } else { 0 }
        Add-TestResult -TestId "ADMIN-020" -TestName "Get Pending Reports" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Reports: $ReportCount"
        Write-Host "  PASS - Got pending reports, count: $ReportCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-020" -TestName "Get Pending Reports" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-020" -TestName "Get Pending Reports" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-021: Filter Reports by Status
Write-Host "[ADMIN-021] Testing filter reports by status..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/reports?status=PENDING&page=1&size=20" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "ADMIN-021" -TestName "Filter Reports by Status" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Filter successful"
        Write-Host "  PASS - Filter by status successful ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "ADMIN-021" -TestName "Filter Reports by Status" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-021" -TestName "Filter Reports by Status" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-022: Handle Report (Approve)
Write-Host "[ADMIN-022] Testing handle report (approve)..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    # Note: This test will skip if no reports exist
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/reports/pending?page=1&size=1" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.items -and $Result.Body.data.items.Count -gt 0) {
        $ReportId = $Result.Body.data.items[0].id
        $HandleBody = @{ action = "APPROVE"; remark = "Test approval" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/reports/$ReportId/handle" -Body $HandleBody -Headers (Get-AdminHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "ADMIN-022" -TestName "Handle Report (Approve)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Report handled"
            Write-Host "  PASS - Report handled successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "ADMIN-022" -TestName "Handle Report (Approve)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "ADMIN-022" -TestName "Handle Report (Approve)" -Status "SKIP" -ResponseTime "-" -Note "No reports available"
        Write-Host "  SKIP - No reports available" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "ADMIN-022" -TestName "Handle Report (Approve)" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-023: Handle Non-existent Report
Write-Host "[ADMIN-023] Testing handle non-existent report..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $FakeReportId = "999999999999999999"
    $HandleBody = @{ action = "APPROVE"; remark = "Test remark" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/reports/$FakeReportId/handle" -Body $HandleBody -Headers (Get-AdminHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-023" -TestName "Handle Non-existent Report" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Non-existent report correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-023" -TestName "Handle Non-existent Report" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 404"
        Write-Host "  FAIL - Should return 404 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-023" -TestName "Handle Non-existent Report" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-024: Handle Report (Reject)
Write-Host "[ADMIN-024] Testing handle report (reject)..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    # Note: This test will skip if no reports exist
    $Result = Invoke-ApiRequest -Method "GET" -Url "$AdminServiceUrl/admin/reports/pending?page=1&size=1" -Headers (Get-AdminHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.items -and $Result.Body.data.items.Count -gt 0) {
        $ReportId = $Result.Body.data.items[0].id
        $HandleBody = @{ action = "REJECT"; remark = "Test rejection" }
        $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/reports/$ReportId/handle" -Body $HandleBody -Headers (Get-AdminHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "ADMIN-024" -TestName "Handle Report (Reject)" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Report rejected"
            Write-Host "  PASS - Report rejected successfully ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "ADMIN-024" -TestName "Handle Report (Reject)" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "ADMIN-024" -TestName "Handle Report (Reject)" -Status "SKIP" -ResponseTime "-" -Note "No reports available"
        Write-Host "  SKIP - No reports available" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "ADMIN-024" -TestName "Handle Report (Reject)" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

# ADMIN-025: Invalid Report Action
Write-Host "[ADMIN-025] Testing invalid report action..." -ForegroundColor Yellow
if ($Global:AdminAccessToken) {
    $FakeReportId = "123456789"
    $HandleBody = @{ action = "INVALID_ACTION"; remark = "Test remark" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$AdminServiceUrl/admin/reports/$FakeReportId/handle" -Body $HandleBody -Headers (Get-AdminHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "ADMIN-025" -TestName "Invalid Report Action" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Invalid action correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "ADMIN-025" -TestName "Invalid Report Action" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return 400"
        Write-Host "  FAIL - Should return 400 ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "ADMIN-025" -TestName "Invalid Report Action" -Status "SKIP" -ResponseTime "-" -Note "No admin token"
    Write-Host "  SKIP - No admin token" -ForegroundColor Gray
}

Write-Host ""

# === Test Results Summary ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$TotalTests = $TestResults.Count
$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host ""
Write-Host "Total Tests: $TotalTests" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display failed tests
if ($FailCount -gt 0) {
    Write-Host "Failed Tests:" -ForegroundColor Red
    $TestResults | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  [$($_.TestId)] $($_.TestName) - $($_.Note)" -ForegroundColor Red
    }
    Write-Host ""
}

# Display test results table
Write-Host "Detailed Results:" -ForegroundColor Cyan
$TestResults | Format-Table -Property TestId, TestName, Status, ResponseTime, Note -AutoSize

# Update test status file
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    Write-Host "Updating test status file..." -ForegroundColor Cyan
    
    $ServiceSection = @"

## Admin Service Tests (Admin Service)
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
    $ServiceSection += "`n"
    
    Add-Content -Path $StatusFullPath -Value $ServiceSection
    Write-Host "  Test status file updated" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Admin Service API Tests Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
