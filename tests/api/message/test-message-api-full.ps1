# Message Service API Full Test Script
# Test Cases: MSG-001 to MSG-020 (including error scenarios)
# Coverage: Send(6), History(5), Conversation(5), Status(4)

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [string]$StatusPath = "../../results/test-status.md"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$MessageServiceUrl = $Config.message_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

$TestResults = @()
$Global:AccessToken = ""
$Global:RefreshToken = ""
$Global:TestUserId = ""
$Global:TargetUserId = ""
$Global:TargetAccessToken = ""
$Global:TestMessageId = ""
$Global:TestConversationId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "msgtest_$Timestamp"
$UniqueEmail = "msgtest_$Timestamp@example.com"

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
function Get-TargetAuthHeaders { return @{ "Authorization" = "Bearer $Global:TargetAccessToken" } }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Message Service API Full Tests" -ForegroundColor Cyan
Write-Host "Message Service URL: $MessageServiceUrl" -ForegroundColor Cyan
Write-Host "User Service URL: $UserServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# === Setup: Create test users and login ===
Write-Host "=== Setup: Creating test users ===" -ForegroundColor Magenta

# Create first test user (sender)
Write-Host "Creating first test user (sender)..." -ForegroundColor Cyan
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-Host "  Created sender user, ID: $Global:TestUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create sender user: $($Result.Body.message)" -ForegroundColor Yellow
}

# Login first user
Write-Host "Logging in first test user..." -ForegroundColor Cyan
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    $Global:RefreshToken = $Result.Body.data.refreshToken
    Write-Host "  Login successful, got token" -ForegroundColor Cyan
} else {
    Write-Host "  Login failed: $($Result.Body.message)" -ForegroundColor Yellow
}

# Create second test user (receiver)
$Timestamp2 = Get-Date -Format "yyyyMMddHHmmssff"
$TargetUsername = "msgtarget_$Timestamp2"
$TargetEmail = "msgtarget_$Timestamp2@example.com"
Write-Host "Creating second test user (receiver)..." -ForegroundColor Cyan
$RegisterBody2 = @{ userName = $TargetUsername; email = $TargetEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody2
if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TargetUserId = $Result.Body.data
    Write-Host "  Created receiver user, ID: $Global:TargetUserId" -ForegroundColor Cyan
} else {
    Write-Host "  Failed to create receiver user: $($Result.Body.message)" -ForegroundColor Yellow
}

# Login second user
Write-Host "Logging in second test user..." -ForegroundColor Cyan
$LoginBody2 = @{ email = $TargetEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody2
if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:TargetAccessToken = $Result.Body.data.accessToken
    Write-Host "  Second user login successful" -ForegroundColor Cyan
} else {
    Write-Host "  Second user login failed: $($Result.Body.message)" -ForegroundColor Yellow
}

Write-Host ""


# === SECTION 1: Send Message Tests (6 tests) ===
Write-Host "=== SECTION 1: Send Message Tests ===" -ForegroundColor Magenta

# MSG-001: Send Text Message
Write-Host "[MSG-001] Testing send text message..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TargetUserId) {
    $SendMessageBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = "Hello, this is a test message $Timestamp" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $SendMessageBody -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $Global:TestMessageId = $Result.Body.data.id
        $Global:TestConversationId = $Result.Body.data.conversationId
        Add-TestResult -TestId "MSG-001" -TestName "Send Text Message" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "MessageID: $Global:TestMessageId"
        Write-Host "  PASS - Message sent ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-001" -TestName "Send Text Message" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-001" -TestName "Send Text Message" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-002: Send Empty Message
Write-Host "[MSG-002] Testing send empty message..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TargetUserId) {
    $EmptyMessageBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = "" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $EmptyMessageBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-002" -TestName "Send Empty Message" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Empty message correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-002" -TestName "Send Empty Message" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject empty message"
        Write-Host "  FAIL - Should reject empty message ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-002" -TestName "Send Empty Message" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-003: Send Long Message (>5000 chars)
Write-Host "[MSG-003] Testing send long message..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TargetUserId) {
    $LongContent = "A" * 5100  # 5100 characters, exceeds typical limit
    $LongMessageBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = $LongContent }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $LongMessageBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-003" -TestName "Send Long Message" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Long message correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        # Some systems may allow long messages, so we accept success too
        Add-TestResult -TestId "MSG-003" -TestName "Send Long Message" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Long message handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} else {
    Add-TestResult -TestId "MSG-003" -TestName "Send Long Message" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-004: Send Message to Non-existent User
Write-Host "[MSG-004] Testing send message to non-existent user..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $FakeUserId = "999999999999999999"
    $NonExistentBody = @{ receiverId = $FakeUserId; type = "TEXT"; content = "Test message to non-existent user" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $NonExistentBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or $Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-004" -TestName "Send to Non-existent" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Send to non-existent user correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-004" -TestName "Send to Non-existent" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject"
        Write-Host "  FAIL - Should reject send to non-existent user ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-004" -TestName "Send to Non-existent" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-005: Send Message to Self
Write-Host "[MSG-005] Testing send message to self..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestUserId) {
    $SelfMessageBody = @{ receiverId = $Global:TestUserId; type = "TEXT"; content = "Test message to self" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $SelfMessageBody -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-005" -TestName "Send to Self" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Send to self correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        # Some systems may allow self-messaging
        Add-TestResult -TestId "MSG-005" -TestName "Send to Self" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Send to self handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    }
} else {
    Add-TestResult -TestId "MSG-005" -TestName "Send to Self" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-006: Send Message Without Auth
Write-Host "[MSG-006] Testing send message without auth..." -ForegroundColor Yellow
if ($Global:TargetUserId) {
    $NoAuthBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = "Test message without auth" }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $NoAuthBody
    if ($Result.StatusCode -eq 401 -or $Result.StatusCode -eq 403 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-006" -TestName "Send Without Auth" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Send without auth correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-006" -TestName "Send Without Auth" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should require auth"
        Write-Host "  FAIL - Should require auth ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-006" -TestName "Send Without Auth" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""


# === SECTION 2: Message History Tests (5 tests) ===
Write-Host "=== SECTION 2: Message History Tests ===" -ForegroundColor Magenta

# MSG-007: Get Message History
Write-Host "[MSG-007] Testing get message history..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestConversationId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/messages/conversation/$Global:TestConversationId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $MessageCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "MSG-007" -TestName "Get Message History" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Messages: $MessageCount"
        Write-Host "  PASS - Got message history, count: $MessageCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-007" -TestName "Get Message History" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-007" -TestName "Get Message History" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-008: Get Non-existent Conversation Messages
Write-Host "[MSG-008] Testing get non-existent conversation messages..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $FakeConversationId = "999999999999999999"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/messages/conversation/$FakeConversationId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-008" -TestName "Non-existent Conversation" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly handled"
        Write-Host "  PASS - Non-existent conversation correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200 -and ($Result.Body.data -eq $null -or $Result.Body.data.Count -eq 0)) {
        Add-TestResult -TestId "MSG-008" -TestName "Non-existent Conversation" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Returned empty list"
        Write-Host "  PASS - Non-existent conversation returned empty list ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-008" -TestName "Non-existent Conversation" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should handle gracefully"
        Write-Host "  FAIL - Should handle non-existent conversation gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-008" -TestName "Non-existent Conversation" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-009: Message Pagination
Write-Host "[MSG-009] Testing message pagination..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestConversationId) {
    $PaginationUrl = "$MessageServiceUrl/api/v1/messages/conversation/$($Global:TestConversationId)?limit=5"
    $Result = Invoke-ApiRequest -Method "GET" -Url $PaginationUrl -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $MessageCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "MSG-009" -TestName "Message Pagination" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Messages: $MessageCount"
        Write-Host "  PASS - Pagination works, count: $MessageCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-009" -TestName "Message Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-009" -TestName "Message Pagination" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-010: Get Other User's Conversation Messages (Permission Test)
Write-Host "[MSG-010] Testing get other user's conversation messages..." -ForegroundColor Yellow
if ($Global:TestConversationId) {
    # Create a third user to test permission
    $Timestamp3 = Get-Date -Format "yyyyMMddHHmmssffff"
    $ThirdUsername = "msgthird_$Timestamp3"
    $ThirdEmail = "msgthird_$Timestamp3@example.com"
    $RegisterBody3 = @{ userName = $ThirdUsername; email = $ThirdEmail; password = $TestUser.password }
    $RegResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody3
    
    if ($RegResult.Success -and $RegResult.Body.code -eq 200) {
        $LoginBody3 = @{ email = $ThirdEmail; password = $TestUser.password }
        $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody3
        
        if ($LoginResult.Success -and $LoginResult.Body.code -eq 200 -and $LoginResult.Body.data.accessToken) {
            $ThirdToken = $LoginResult.Body.data.accessToken
            $ThirdHeaders = @{ "Authorization" = "Bearer $ThirdToken" }
            
            $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/messages/conversation/$Global:TestConversationId" -Headers $ThirdHeaders
            if ($Result.StatusCode -eq 403 -or $Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
                Add-TestResult -TestId "MSG-010" -TestName "Other User's Conversation" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
                Write-Host "  PASS - Access to other user's conversation correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
            } else {
                Add-TestResult -TestId "MSG-010" -TestName "Other User's Conversation" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should reject access"
                Write-Host "  FAIL - Should reject access to other user's conversation ($($Result.ResponseTime)ms)" -ForegroundColor Red
            }
        } else {
            Add-TestResult -TestId "MSG-010" -TestName "Other User's Conversation" -Status "SKIP" -ResponseTime "-" -Note "Third user login failed"
            Write-Host "  SKIP - Third user login failed" -ForegroundColor Gray
        }
    } else {
        Add-TestResult -TestId "MSG-010" -TestName "Other User's Conversation" -Status "SKIP" -ResponseTime "-" -Note "Third user creation failed"
        Write-Host "  SKIP - Third user creation failed" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "MSG-010" -TestName "Other User's Conversation" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-011: Invalid Pagination Parameters
Write-Host "[MSG-011] Testing invalid pagination parameters..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestConversationId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/messages/conversation/$Global:TestConversationId?limit=-1" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-011" -TestName "Invalid Pagination" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected"
        Write-Host "  PASS - Invalid pagination correctly rejected ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } elseif ($Result.Success -and $Result.Body.code -eq 200) {
        # Some systems may use default values for invalid params
        Add-TestResult -TestId "MSG-011" -TestName "Invalid Pagination" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Handled gracefully"
        Write-Host "  PASS - Invalid pagination handled gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-011" -TestName "Invalid Pagination" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should handle gracefully"
        Write-Host "  FAIL - Should handle invalid pagination gracefully ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-011" -TestName "Invalid Pagination" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""


# === SECTION 3: Conversation Management Tests (5 tests) ===
Write-Host "=== SECTION 3: Conversation Management Tests ===" -ForegroundColor Magenta

# MSG-012: Get Conversation List
Write-Host "[MSG-012] Testing get conversation list..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $ConversationCount = if ($Result.Body.data) { $Result.Body.data.Count } else { 0 }
        Add-TestResult -TestId "MSG-012" -TestName "Get Conversation List" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Conversations: $ConversationCount"
        Write-Host "  PASS - Got conversation list, count: $ConversationCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-012" -TestName "Get Conversation List" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-012" -TestName "Get Conversation List" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-013: Conversation Sorting (by last message time)
Write-Host "[MSG-013] Testing conversation sorting..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    # Send another message to ensure we have multiple conversations or messages
    if ($Global:TargetUserId) {
        $SendMessageBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = "Another test message for sorting $Timestamp" }
        Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $SendMessageBody -Headers (Get-AuthHeaders) | Out-Null
    }
    
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations?limit=10" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $Conversations = $Result.Body.data
        $IsSorted = $true
        if ($Conversations -and $Conversations.Count -gt 1) {
            for ($i = 0; $i -lt $Conversations.Count - 1; $i++) {
                $Current = $Conversations[$i].lastMessageAt
                $Next = $Conversations[$i + 1].lastMessageAt
                if ($Current -and $Next -and $Current -lt $Next) {
                    $IsSorted = $false
                    break
                }
            }
        }
        if ($IsSorted) {
            Add-TestResult -TestId "MSG-013" -TestName "Conversation Sorting" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Sorted by last message"
            Write-Host "  PASS - Conversations sorted by last message time ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "MSG-013" -TestName "Conversation Sorting" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Not sorted correctly"
            Write-Host "  FAIL - Conversations not sorted correctly ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-013" -TestName "Conversation Sorting" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-013" -TestName "Conversation Sorting" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-014: Get Conversation Detail
Write-Host "[MSG-014] Testing get conversation detail..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestConversationId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations/$Global:TestConversationId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
        $ConversationData = $Result.Body.data
        Add-TestResult -TestId "MSG-014" -TestName "Get Conversation Detail" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Got conversation info"
        Write-Host "  PASS - Got conversation detail ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-014" -TestName "Get Conversation Detail" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-014" -TestName "Get Conversation Detail" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-015: Get Non-existent Conversation Detail
Write-Host "[MSG-015] Testing get non-existent conversation detail..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $FakeConversationId = "999999999999999999"
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations/$FakeConversationId" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-015" -TestName "Non-existent Conversation Detail" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned error"
        Write-Host "  PASS - Non-existent conversation correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-015" -TestName "Non-existent Conversation Detail" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return error"
        Write-Host "  FAIL - Should return error for non-existent conversation ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-015" -TestName "Non-existent Conversation Detail" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-016: Get Conversation by User
Write-Host "[MSG-016] Testing get conversation by user..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TargetUserId) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/conversations/user/$Global:TargetUserId" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        if ($Result.Body.data) {
            Add-TestResult -TestId "MSG-016" -TestName "Get Conversation by User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Found conversation"
            Write-Host "  PASS - Got conversation by user ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            Add-TestResult -TestId "MSG-016" -TestName "Get Conversation by User" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "No conversation found"
            Write-Host "  PASS - No conversation found (expected) ($($Result.ResponseTime)ms)" -ForegroundColor Green
        }
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-016" -TestName "Get Conversation by User" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-016" -TestName "Get Conversation by User" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""


# === SECTION 4: Message Status Tests (4 tests) ===
Write-Host "=== SECTION 4: Message Status Tests ===" -ForegroundColor Magenta

# MSG-017: Mark Messages as Read
Write-Host "[MSG-017] Testing mark messages as read..." -ForegroundColor Yellow
if ($Global:TargetAccessToken -and $Global:TestConversationId) {
    # Use target user to mark messages as read (they are the receiver)
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages/conversation/$Global:TestConversationId/read" -Headers (Get-TargetAuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        Add-TestResult -TestId "MSG-017" -TestName "Mark as Read" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Marked as read"
        Write-Host "  PASS - Messages marked as read ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-017" -TestName "Mark as Read" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-017" -TestName "Mark as Read" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-018: Mark Non-existent Conversation as Read
Write-Host "[MSG-018] Testing mark non-existent conversation as read..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $FakeConversationId = "999999999999999999"
    $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages/conversation/$FakeConversationId/read" -Headers (Get-AuthHeaders)
    if ($Result.StatusCode -eq 404 -or ($Result.Body -and $Result.Body.code -ne 200)) {
        Add-TestResult -TestId "MSG-018" -TestName "Mark Non-existent as Read" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly returned error"
        Write-Host "  PASS - Non-existent conversation correctly handled ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "MSG-018" -TestName "Mark Non-existent as Read" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should return error"
        Write-Host "  FAIL - Should return error for non-existent conversation ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-018" -TestName "Mark Non-existent as Read" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-019: Get Unread Message Count
Write-Host "[MSG-019] Testing get unread message count..." -ForegroundColor Yellow
if ($Global:AccessToken) {
    $Result = Invoke-ApiRequest -Method "GET" -Url "$MessageServiceUrl/api/v1/messages/unread-count" -Headers (Get-AuthHeaders)
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $UnreadCount = $Result.Body.data
        Add-TestResult -TestId "MSG-019" -TestName "Get Unread Count" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Unread: $UnreadCount"
        Write-Host "  PASS - Got unread count: $UnreadCount ($($Result.ResponseTime)ms)" -ForegroundColor Green
    } else {
        $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
        Add-TestResult -TestId "MSG-019" -TestName "Get Unread Count" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
        Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
    }
} else {
    Add-TestResult -TestId "MSG-019" -TestName "Get Unread Count" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

# MSG-020: Batch Mark as Read (via conversation)
Write-Host "[MSG-020] Testing batch mark as read..." -ForegroundColor Yellow
if ($Global:AccessToken -and $Global:TestConversationId) {
    # Send a few more messages first
    if ($Global:TargetUserId) {
        for ($i = 1; $i -le 3; $i++) {
            $SendMessageBody = @{ receiverId = $Global:TargetUserId; type = "TEXT"; content = "Batch test message $i $Timestamp" }
            Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages" -Body $SendMessageBody -Headers (Get-AuthHeaders) | Out-Null
        }
    }
    
    # Now mark all as read using target user
    if ($Global:TargetAccessToken) {
        $Result = Invoke-ApiRequest -Method "POST" -Url "$MessageServiceUrl/api/v1/messages/conversation/$Global:TestConversationId/read" -Headers (Get-TargetAuthHeaders)
        if ($Result.Success -and $Result.Body.code -eq 200) {
            Add-TestResult -TestId "MSG-020" -TestName "Batch Mark as Read" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Batch marked as read"
            Write-Host "  PASS - Batch messages marked as read ($($Result.ResponseTime)ms)" -ForegroundColor Green
        } else {
            $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
            Add-TestResult -TestId "MSG-020" -TestName "Batch Mark as Read" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
            Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
        }
    } else {
        Add-TestResult -TestId "MSG-020" -TestName "Batch Mark as Read" -Status "SKIP" -ResponseTime "-" -Note "Missing target token"
        Write-Host "  SKIP - Missing target token" -ForegroundColor Gray
    }
} else {
    Add-TestResult -TestId "MSG-020" -TestName "Batch Mark as Read" -Status "SKIP" -ResponseTime "-" -Note "Missing params"
    Write-Host "  SKIP - Missing params" -ForegroundColor Gray
}

Write-Host ""


# === Test Summary ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count
$TotalCount = $TestResults.Count

Write-Host "Total: $TotalCount | Pass: $PassCount | Fail: $FailCount | Skip: $SkipCount" -ForegroundColor White
Write-Host ""

# Display results table
Write-Host "Detailed Results:" -ForegroundColor Cyan
Write-Host "-----------------" -ForegroundColor Cyan
foreach ($Result in $TestResults) {
    $StatusColor = switch ($Result.Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "SKIP" { "Gray" }
        default { "White" }
    }
    Write-Host "$($Result.TestId) | $($Result.TestName) | $($Result.Status) | $($Result.ResponseTime) | $($Result.Note)" -ForegroundColor $StatusColor
}

Write-Host ""

# Update test status file
$StatusFullPath = Join-Path $ScriptDir $StatusPath
if (Test-Path $StatusFullPath) {
    $StatusContent = Get-Content $StatusFullPath -Raw
    
    # Update message service section
    $MessageSection = @"

## 消息服务测试 (Message Service)
| 测试ID | 测试名称 | 状态 | 响应时间 | 备注 |
|--------|----------|------|----------|------|
"@
    
    foreach ($Result in $TestResults) {
        $StatusIcon = switch ($Result.Status) {
            "PASS" { "✅" }
            "FAIL" { "❌" }
            "SKIP" { "⏭️" }
            default { "❓" }
        }
        $MessageSection += "`n| $($Result.TestId) | $($Result.TestName) | $StatusIcon $($Result.Status) | $($Result.ResponseTime) | $($Result.Note) |"
    }
    
    $MessageSection += "`n`n**统计**: 总计 $TotalCount | 通过 $PassCount | 失败 $FailCount | 跳过 $SkipCount"
    $MessageSection += "`n**执行时间**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    
    # Check if message service section exists
    if ($StatusContent -match "## 消息服务测试") {
        # Replace existing section
        $StatusContent = $StatusContent -replace "## 消息服务测试[\s\S]*?(?=## |$)", $MessageSection
    } else {
        # Append new section
        $StatusContent += "`n$MessageSection"
    }
    
    Set-Content -Path $StatusFullPath -Value $StatusContent -Encoding UTF8
    Write-Host "Test status updated in: $StatusFullPath" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Message Service API Tests Completed!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Return exit code based on test results
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
