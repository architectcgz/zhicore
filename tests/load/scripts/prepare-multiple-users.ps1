# Prepare Multiple Test Users for Load Testing
# This script creates multiple test users to support concurrent like operations

param(
    [int]$UserCount = 100,
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json

$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url
$CommentServiceUrl = $Config.comment_service_url

$TestPassword = "Test123456!"
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"

$CreatedUsers = @()
$Global:TestPostId = ""
$Global:TestCommentId = ""
$Global:MainAccessToken = ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Preparing Multiple Test Users" -ForegroundColor Cyan
Write-Host "User Count: $UserCount" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

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

function Get-AuthHeaders { 
    param([string]$Token)
    return @{ "Authorization" = "Bearer $Token" }
}

# Step 1: Create main user and test post
Write-Host "[STEP 1] Creating main test user and post..." -ForegroundColor Yellow
Write-Host ""

$MainUsername = "loadtest_main_$Timestamp"
$MainEmail = "loadtest_main_$Timestamp@example.com"

Write-Host "  1.1 Registering main user: $MainUsername" -ForegroundColor Cyan
$RegisterBody = @{ userName = $MainUsername; email = $MainEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $MainUserId = $Result.Body.data.ToString()
    Write-Host "    [PASS] User registered, ID: $MainUserId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] Registration failed: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host "  1.2 Logging in main user..." -ForegroundColor Cyan
$LoginBody = @{ email = $MainEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:MainAccessToken = $Result.Body.data.accessToken
    Write-Host "    [PASS] Got access token" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] Login failed: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host "  1.3 Creating test post..." -ForegroundColor Cyan
$PostBody = @{
    title = "Load Test Post $Timestamp"
    content = "This is a test post for load testing with multiple users. Created at $Timestamp."
    categoryId = 1
    tags = @("test", "loadtest", "multiuser")
    status = 1
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders -Token $Global:MainAccessToken)

if ($Result.Success -and $Result.Body.code -eq 200) {
    if ($Result.Body.data.postId) {
        $Global:TestPostId = $Result.Body.data.postId.ToString()
    } else {
        $Global:TestPostId = $Result.Body.data.ToString()
    }
    Write-Host "    [PASS] Post created, ID: $Global:TestPostId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] Post creation failed: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host "  1.4 Publishing test post..." -ForegroundColor Cyan
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/publish" -Headers (Get-AuthHeaders -Token $Global:MainAccessToken)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "    [PASS] Post published" -ForegroundColor Green
} else {
    Write-Host "    [WARN] Post publish may have failed, continuing..." -ForegroundColor Yellow
}

Write-Host "  1.5 Creating test comment..." -ForegroundColor Cyan
$CommentBody = @{
    postId = $Global:TestPostId
    content = "This is a test comment for load testing."
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$CommentServiceUrl/api/v1/comments" -Body $CommentBody -Headers (Get-AuthHeaders -Token $Global:MainAccessToken)

if ($Result.Success -and $Result.Body.code -eq 200) {
    if ($Result.Body.data.commentId) {
        $Global:TestCommentId = $Result.Body.data.commentId.ToString()
    } else {
        $Global:TestCommentId = $Result.Body.data.ToString()
    }
    Write-Host "    [PASS] Comment created, ID: $Global:TestCommentId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] Comment creation failed: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 2: Create multiple test users
Write-Host "[STEP 2] Creating $UserCount test users..." -ForegroundColor Yellow
Write-Host ""

$SuccessCount = 0
$FailCount = 0

for ($i = 1; $i -le $UserCount; $i++) {
    $Username = "loadtest_user${i}_$Timestamp"
    $Email = "loadtest_user${i}_$Timestamp@example.com"
    
    if ($i % 10 -eq 0) {
        Write-Host "  Progress: $i / $UserCount users created..." -ForegroundColor Cyan
    }
    
    # Register user
    $RegisterBody = @{ userName = $Username; email = $Email; password = $TestPassword }
    $Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody
    
    if ($Result.Success -and $Result.Body.code -eq 200) {
        $UserId = $Result.Body.data.ToString()
        
        # Login to get token
        $LoginBody = @{ email = $Email; password = $TestPassword }
        $LoginResult = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody
        
        if ($LoginResult.Success -and $LoginResult.Body.code -eq 200) {
            $AccessToken = $LoginResult.Body.data.accessToken
            
            $CreatedUsers += [PSCustomObject]@{
                Index = $i
                UserId = $UserId
                Username = $Username
                Email = $Email
                AccessToken = $AccessToken
            }
            
            $SuccessCount++
        } else {
            $FailCount++
        }
    } else {
        $FailCount++
    }
    
    # Small delay to avoid overwhelming the server
    Start-Sleep -Milliseconds 50
}

Write-Host ""
Write-Host "  [DONE] Created $SuccessCount users successfully, $FailCount failed" -ForegroundColor Green
Write-Host ""

# Step 3: Save results
Write-Host "[STEP 3] Saving test data..." -ForegroundColor Yellow
Write-Host ""

$DataFile = Join-Path $ScriptDir "test-users.csv"
$CreatedUsers | Export-Csv -Path $DataFile -NoTypeInformation -Encoding UTF8
Write-Host "  User data saved to: $DataFile" -ForegroundColor Green

$ConfigFile = Join-Path $ScriptDir "test-data-multi.txt"
@"
TEST_POST_ID=$Global:TestPostId
TEST_COMMENT_ID=$Global:TestCommentId
MAIN_ACCESS_TOKEN=$Global:MainAccessToken
USER_COUNT=$SuccessCount
USER_DATA_FILE=$DataFile
"@ | Out-File -FilePath $ConfigFile -Encoding UTF8
Write-Host "  Config saved to: $ConfigFile" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Data Preparation Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "  Main User ID: $MainUserId" -ForegroundColor White
Write-Host "  Test Post ID: $Global:TestPostId" -ForegroundColor White
Write-Host "  Test Comment ID: $Global:TestCommentId" -ForegroundColor White
Write-Host "  Test Users Created: $SuccessCount" -ForegroundColor White
Write-Host "  User Data File: $DataFile" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Update JMeter test plan to use CSV data from: $DataFile" -ForegroundColor Cyan
Write-Host "  2. Configure CSV Data Set Config in JMeter:" -ForegroundColor Cyan
Write-Host "     - Filename: $DataFile" -ForegroundColor Gray
Write-Host "     - Variable Names: Index,UserId,Username,Email,AccessToken" -ForegroundColor Gray
Write-Host "     - Recycle on EOF: True" -ForegroundColor Gray
Write-Host "     - Stop thread on EOF: False" -ForegroundColor Gray
Write-Host "     - Sharing mode: All threads" -ForegroundColor Gray
Write-Host "  3. Use variable \${AccessToken} in HTTP Header Manager" -ForegroundColor Cyan
Write-Host ""

exit 0
