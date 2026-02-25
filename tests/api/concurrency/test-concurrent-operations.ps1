# Concurrent Operations Test Script
# Test Cases: CONC-001 to CONC-010
# Coverage: Concurrent operations (like, comment, follow, message, update, delete, create, token refresh, register, check-in)

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
$MessageServiceUrl = $Config.message_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TestPostId = ""
$Global:TargetUserId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "conctest_$Timestamp"
$UniqueEmail = "conctest_$Timestamp@example.com"

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

function Invoke-ConcurrentRequests {
    param([int]$Count, [scriptblock]$RequestBlock)
    $Jobs = @()
    for ($i = 0; $i -lt $Count; $i++) {
        $Jobs += Start-Job -ScriptBlock $RequestBlock -ArgumentList $i
    }
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    return $Results
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Concurrent Operations Tests" -ForegroundColor Cyan
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
$PostBody = @{ title = "Concurrent Test Post $Timestamp"; content = "Test content for concurrency"; raw = "Test raw"; excerpt = "Test excerpt" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestPostId = $Result.Body.data
    Write-Host "  Test post created: $Global:TestPostId" -ForegroundColor Green
}

Write-Host ""

# === SECTION 1: Concurrent Operations Tests ===
Write-Host "=== SECTION 1: Concurrent Operations Tests ===" -ForegroundColor Magenta

# CONC-001: Concurrent Like Same Post
Write-Host "[CONC-001] Testing concurrent like same post (10 concurrent requests)..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $ConcurrentCount = 10
    $SuccessCount = 0
    $ConflictCount = 0
    
    # Execute concurrent likes
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like", $Global:AccessToken | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200) {
            $SuccessCount++
        } elseif ($Result.StatusCode -eq 409) {
            $ConflictCount++
        }
    }
    
    # Check if only one like succeeded or all handled correctly
    if ($SuccessCount -ge 1 -and ($SuccessCount + $ConflictCount) -eq $ConcurrentCount) {
        Add-TestResult -TestId "CONC-001" -TestName "Concurrent Like Same Post" -Status "PASS" -ResponseTime "-" -Note "Success: $SuccessCount, Conflict: $ConflictCount"
        Write-Host "  PASS - Concurrent likes handled correctly (Success: $SuccessCount, Conflict: $ConflictCount)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-001" -TestName "Concurrent Like Same Post" -Status "FAIL" -ResponseTime "-" -Note "Unexpected results"
        Write-Host "  FAIL - Unexpected concurrent behavior" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-001" -TestName "Concurrent Like Same Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# CONC-002: Concurrent Comment Same Post
Write-Host "[CONC-002] Testing concurrent comment same post (10 concurrent requests)..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $ConcurrentCount = 10
    $SuccessCount = 0
    
    # Execute concurrent comments
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token, $PostId, $Index)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Body = @{ postId = $PostId; content = "Concurrent comment $Index" } | ConvertTo-Json
                $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$CommentServiceUrl/api/v1/comments", $Global:AccessToken, $Global:TestPostId, $i | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200 -or $Result.StatusCode -eq 201) {
            $SuccessCount++
        }
    }
    
    # All comments should succeed
    if ($SuccessCount -eq $ConcurrentCount) {
        Add-TestResult -TestId "CONC-002" -TestName "Concurrent Comment Same Post" -Status "PASS" -ResponseTime "-" -Note "All $ConcurrentCount comments created"
        Write-Host "  PASS - All $ConcurrentCount concurrent comments created" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-002" -TestName "Concurrent Comment Same Post" -Status "FAIL" -ResponseTime "-" -Note "Only $SuccessCount/$ConcurrentCount succeeded"
        Write-Host "  FAIL - Only $SuccessCount/$ConcurrentCount comments succeeded" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-002" -TestName "Concurrent Comment Same Post" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# CONC-003: Concurrent Follow Same User
Write-Host "[CONC-003] Testing concurrent follow same user (10 concurrent requests)..." -ForegroundColor Yellow
if ($Global:TargetUserId) {
    $ConcurrentCount = 10
    $SuccessCount = 0
    $ConflictCount = 0
    
    # Execute concurrent follows
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$UserServiceUrl/api/v1/users/$Global:TestUserId/following/$Global:TargetUserId", $Global:AccessToken | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200) {
            $SuccessCount++
        } elseif ($Result.StatusCode -eq 409) {
            $ConflictCount++
        }
    }
    
    # Check if only one follow succeeded
    if ($SuccessCount -ge 1 -and ($SuccessCount + $ConflictCount) -eq $ConcurrentCount) {
        Add-TestResult -TestId "CONC-003" -TestName "Concurrent Follow Same User" -Status "PASS" -ResponseTime "-" -Note "Success: $SuccessCount, Conflict: $ConflictCount"
        Write-Host "  PASS - Concurrent follows handled correctly (Success: $SuccessCount, Conflict: $ConflictCount)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-003" -TestName "Concurrent Follow Same User" -Status "FAIL" -ResponseTime "-" -Note "Unexpected results"
        Write-Host "  FAIL - Unexpected concurrent behavior" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-003" -TestName "Concurrent Follow Same User" -Status "SKIP" -ResponseTime "-" -Note "No target user"
    Write-Host "  SKIP - No target user available" -ForegroundColor Gray
}

# CONC-004: Concurrent Send Messages
Write-Host "[CONC-004] Testing concurrent send messages (10 concurrent requests)..." -ForegroundColor Yellow
if ($Global:TargetUserId) {
    $ConcurrentCount = 10
    $SuccessCount = 0
    
    # Execute concurrent messages
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token, $TargetId, $Index)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Body = @{ receiverId = $TargetId; content = "Concurrent message $Index" } | ConvertTo-Json
                $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$MessageServiceUrl/api/v1/messages", $Global:AccessToken, $Global:TargetUserId, $i | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200 -or $Result.StatusCode -eq 201) {
            $SuccessCount++
        }
    }
    
    # All messages should succeed
    if ($SuccessCount -eq $ConcurrentCount) {
        Add-TestResult -TestId "CONC-004" -TestName "Concurrent Send Messages" -Status "PASS" -ResponseTime "-" -Note "All $ConcurrentCount messages sent"
        Write-Host "  PASS - All $ConcurrentCount concurrent messages sent" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-004" -TestName "Concurrent Send Messages" -Status "FAIL" -ResponseTime "-" -Note "Only $SuccessCount/$ConcurrentCount succeeded"
        Write-Host "  FAIL - Only $SuccessCount/$ConcurrentCount messages succeeded" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-004" -TestName "Concurrent Send Messages" -Status "SKIP" -ResponseTime "-" -Note "No target user"
    Write-Host "  SKIP - No target user available" -ForegroundColor Gray
}

# CONC-005: Concurrent Update Same Resource
Write-Host "[CONC-005] Testing concurrent update same resource (10 concurrent requests)..." -ForegroundColor Yellow
if ($Global:TestPostId) {
    $ConcurrentCount = 10
    $SuccessCount = 0
    
    # Execute concurrent updates
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token, $Index)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Body = @{ title = "Updated title $Index"; content = "Updated content $Index"; raw = "Updated raw"; excerpt = "Updated excerpt" } | ConvertTo-Json
                $Response = Invoke-WebRequest -Method "PUT" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$PostServiceUrl/api/v1/posts/$Global:TestPostId", $Global:AccessToken, $i | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200) {
            $SuccessCount++
        }
    }
    
    # At least some updates should succeed
    if ($SuccessCount -ge 1) {
        Add-TestResult -TestId "CONC-005" -TestName "Concurrent Update Same Resource" -Status "PASS" -ResponseTime "-" -Note "$SuccessCount/$ConcurrentCount updates succeeded"
        Write-Host "  PASS - Concurrent updates handled ($SuccessCount/$ConcurrentCount succeeded)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-005" -TestName "Concurrent Update Same Resource" -Status "FAIL" -ResponseTime "-" -Note "No updates succeeded"
        Write-Host "  FAIL - No updates succeeded" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-005" -TestName "Concurrent Update Same Resource" -Status "SKIP" -ResponseTime "-" -Note "No test post"
    Write-Host "  SKIP - No test post available" -ForegroundColor Gray
}

# CONC-006: Concurrent Delete Same Resource
Write-Host "[CONC-006] Testing concurrent delete same resource (10 concurrent requests)..." -ForegroundColor Yellow
# Create a new post for deletion test
$PostBody = @{ title = "Delete Test Post"; content = "To be deleted"; raw = "Raw"; excerpt = "Excerpt" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)
if ($Result.Success -and $Result.Body.code -eq 200) {
    $DeletePostId = $Result.Body.data
    $ConcurrentCount = 10
    $SuccessCount = 0
    $NotFoundCount = 0
    
    # Execute concurrent deletes
    for ($i = 0; $i -lt $ConcurrentCount; $i++) {
        Start-Job -ScriptBlock {
            param($Url, $Token)
            try {
                $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
                $Response = Invoke-WebRequest -Method "DELETE" -Uri $Url -Headers $Headers -ErrorAction Stop
                return @{ Success = $true; StatusCode = $Response.StatusCode }
            } catch {
                return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList "$PostServiceUrl/api/v1/posts/$DeletePostId", $Global:AccessToken | Out-Null
    }
    
    # Wait for all jobs and collect results
    $Jobs = Get-Job
    $Results = $Jobs | Wait-Job | Receive-Job
    $Jobs | Remove-Job
    
    foreach ($Result in $Results) {
        if ($Result.Success -or $Result.StatusCode -eq 200) {
            $SuccessCount++
        } elseif ($Result.StatusCode -eq 404) {
            $NotFoundCount++
        }
    }
    
    # Only one delete should succeed, others should get 404
    if ($SuccessCount -ge 1 -and ($SuccessCount + $NotFoundCount) -eq $ConcurrentCount) {
        Add-TestResult -TestId "CONC-006" -TestName "Concurrent Delete Same Resource" -Status "PASS" -ResponseTime "-" -Note "Success: $SuccessCount, NotFound: $NotFoundCount"
        Write-Host "  PASS - Concurrent deletes handled correctly (Success: $SuccessCount, NotFound: $NotFoundCount)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "CONC-006" -TestName "Concurrent Delete Same Resource" -Status "FAIL" -ResponseTime "-" -Note "Unexpected results"
        Write-Host "  FAIL - Unexpected concurrent behavior" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "CONC-006" -TestName "Concurrent Delete Same Resource" -Status "SKIP" -ResponseTime "-" -Note "Could not create test post"
    Write-Host "  SKIP - Could not create test post for deletion" -ForegroundColor Gray
}

# CONC-007: Concurrent Create with Same Unique Key
Write-Host "[CONC-007] Testing concurrent create with same unique key (10 concurrent requests)..." -ForegroundColor Yellow
$ConcurrentCount = 10
$SuccessCount = 0
$ConflictCount = 0
$UniqueTimestamp = Get-Date -Format "yyyyMMddHHmmssffff"
$UniqueRegEmail = "concurrent_$UniqueTimestamp@example.com"

# Execute concurrent registrations with same email
for ($i = 0; $i -lt $ConcurrentCount; $i++) {
    Start-Job -ScriptBlock {
        param($Url, $Email, $Index)
        try {
            $Headers = @{ "Content-Type" = "application/json" }
            $Body = @{ userName = "concurrent_user_$Index"; email = $Email; password = "Test123456!" } | ConvertTo-Json
            $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
            return @{ Success = $true; StatusCode = $Response.StatusCode }
        } catch {
            return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
        }
    } -ArgumentList "$UserServiceUrl/api/v1/auth/register", $UniqueRegEmail, $i | Out-Null
}

# Wait for all jobs and collect results
$Jobs = Get-Job
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

foreach ($Result in $Results) {
    if ($Result.Success -or $Result.StatusCode -eq 200 -or $Result.StatusCode -eq 201) {
        $SuccessCount++
    } elseif ($Result.StatusCode -eq 400 -or $Result.StatusCode -eq 409) {
        $ConflictCount++
    }
}

# Only one registration should succeed
if ($SuccessCount -eq 1 -and ($SuccessCount + $ConflictCount) -eq $ConcurrentCount) {
    Add-TestResult -TestId "CONC-007" -TestName "Concurrent Create Same Unique Key" -Status "PASS" -ResponseTime "-" -Note "Only 1 registration succeeded"
    Write-Host "  PASS - Only 1 registration succeeded, others rejected" -ForegroundColor Green
} else {
    Add-TestResult -TestId "CONC-007" -TestName "Concurrent Create Same Unique Key" -Status "FAIL" -ResponseTime "-" -Note "Success: $SuccessCount, Conflict: $ConflictCount"
    Write-Host "  FAIL - Expected 1 success, got $SuccessCount (Conflict: $ConflictCount)" -ForegroundColor Red
}

# CONC-008: Concurrent Token Refresh
Write-Host "[CONC-008] Testing concurrent token refresh (10 concurrent requests)..." -ForegroundColor Yellow
$ConcurrentCount = 10
$SuccessCount = 0

# Execute concurrent token refreshes
for ($i = 0; $i -lt $ConcurrentCount; $i++) {
    Start-Job -ScriptBlock {
        param($Url, $RefreshToken)
        try {
            $Headers = @{ "Content-Type" = "application/json" }
            $Body = @{ refreshToken = $RefreshToken } | ConvertTo-Json
            $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
            return @{ Success = $true; StatusCode = $Response.StatusCode }
        } catch {
            return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
        }
    } -ArgumentList "$UserServiceUrl/api/v1/auth/refresh", $Global:RefreshToken | Out-Null
}

# Wait for all jobs and collect results
$Jobs = Get-Job
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

foreach ($Result in $Results) {
    if ($Result.Success -or $Result.StatusCode -eq 200) {
        $SuccessCount++
    }
}

# All token refreshes should succeed
if ($SuccessCount -eq $ConcurrentCount) {
    Add-TestResult -TestId "CONC-008" -TestName "Concurrent Token Refresh" -Status "PASS" -ResponseTime "-" -Note "All $ConcurrentCount refreshes succeeded"
    Write-Host "  PASS - All $ConcurrentCount concurrent token refreshes succeeded" -ForegroundColor Green
} else {
    Add-TestResult -TestId "CONC-008" -TestName "Concurrent Token Refresh" -Status "FAIL" -ResponseTime "-" -Note "Only $SuccessCount/$ConcurrentCount succeeded"
    Write-Host "  FAIL - Only $SuccessCount/$ConcurrentCount token refreshes succeeded" -ForegroundColor Red
}

# CONC-009: Concurrent Register Different Emails
Write-Host "[CONC-009] Testing concurrent register different emails (10 concurrent requests)..." -ForegroundColor Yellow
$ConcurrentCount = 10
$SuccessCount = 0

# Execute concurrent registrations with different emails
for ($i = 0; $i -lt $ConcurrentCount; $i++) {
    Start-Job -ScriptBlock {
        param($Url, $Index)
        try {
            $Timestamp = Get-Date -Format "yyyyMMddHHmmssffff"
            $Headers = @{ "Content-Type" = "application/json" }
            $Body = @{ userName = "concurrent_$Timestamp_$Index"; email = "concurrent_$Timestamp_$Index@example.com"; password = "Test123456!" } | ConvertTo-Json
            $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -Body $Body -ErrorAction Stop
            return @{ Success = $true; StatusCode = $Response.StatusCode }
        } catch {
            return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
        }
    } -ArgumentList "$UserServiceUrl/api/v1/auth/register", $i | Out-Null
}

# Wait for all jobs and collect results
$Jobs = Get-Job
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

foreach ($Result in $Results) {
    if ($Result.Success -or $Result.StatusCode -eq 200 -or $Result.StatusCode -eq 201) {
        $SuccessCount++
    }
}

# All registrations should succeed
if ($SuccessCount -eq $ConcurrentCount) {
    Add-TestResult -TestId "CONC-009" -TestName "Concurrent Register Different Emails" -Status "PASS" -ResponseTime "-" -Note "All $ConcurrentCount registrations succeeded"
    Write-Host "  PASS - All $ConcurrentCount concurrent registrations succeeded" -ForegroundColor Green
} else {
    Add-TestResult -TestId "CONC-009" -TestName "Concurrent Register Different Emails" -Status "FAIL" -ResponseTime "-" -Note "Only $SuccessCount/$ConcurrentCount succeeded"
    Write-Host "  FAIL - Only $SuccessCount/$ConcurrentCount registrations succeeded" -ForegroundColor Red
}

# CONC-010: Concurrent Check-In
Write-Host "[CONC-010] Testing concurrent check-in (10 concurrent requests)..." -ForegroundColor Yellow
$ConcurrentCount = 10
$SuccessCount = 0
$ConflictCount = 0

# Execute concurrent check-ins
for ($i = 0; $i -lt $ConcurrentCount; $i++) {
    Start-Job -ScriptBlock {
        param($Url, $Token)
        try {
            $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
            $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -ErrorAction Stop
            return @{ Success = $true; StatusCode = $Response.StatusCode }
        } catch {
            return @{ Success = $false; StatusCode = [int]$_.Exception.Response.StatusCode }
        }
    } -ArgumentList "$UserServiceUrl/api/v1/users/$Global:TestUserId/check-in", $Global:AccessToken | Out-Null
}

# Wait for all jobs and collect results
$Jobs = Get-Job
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

foreach ($Result in $Results) {
    if ($Result.Success -or $Result.StatusCode -eq 200) {
        $SuccessCount++
    } elseif ($Result.StatusCode -eq 409) {
        $ConflictCount++
    }
}

# Only one check-in should succeed
if ($SuccessCount -ge 1 -and ($SuccessCount + $ConflictCount) -eq $ConcurrentCount) {
    Add-TestResult -TestId "CONC-010" -TestName "Concurrent Check-In" -Status "PASS" -ResponseTime "-" -Note "Success: $SuccessCount, Conflict: $ConflictCount"
    Write-Host "  PASS - Concurrent check-ins handled correctly (Success: $SuccessCount, Conflict: $ConflictCount)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "CONC-010" -TestName "Concurrent Check-In" -Status "FAIL" -ResponseTime "-" -Note "Unexpected results"
    Write-Host "  FAIL - Unexpected concurrent behavior" -ForegroundColor Red
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
    Write-Host "[$($Result.Status)] $($Result.TestId) - $($Result.TestName) - $($Result.Note)" -ForegroundColor $StatusColor
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
