# 准备压力测试数据脚本
# 创建测试用户、文章和评论

param(
    [string]$UserServiceUrl = "http://localhost:8101",
    [string]$PostServiceUrl = "http://localhost:8102",
    [string]$CommentServiceUrl = "http://localhost:8103"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Preparing Test Data" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 工具函数
function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{ 
            Method = $Method
            Uri = $Url
            ContentType = "application/json"
            Headers = $Headers
            ErrorAction = "Stop"
        }
        if ($Body) { 
            $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) 
        }
        $Response = Invoke-WebRequest @RequestParams
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        if ($Response.Content) {
            $Result.Body = $Response.Content | ConvertFrom-Json
        }
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $ErrorContent = $StreamReader.ReadToEnd()
                $StreamReader.Close()
                if ($ErrorContent) {
                    $Result.Body = $ErrorContent | ConvertFrom-Json
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

# 全局变量
$Global:TestUserId = ""
$Global:AccessToken = ""
$Global:TestPostId = ""
$Global:TestCommentId = ""

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TestUsername = "loadtest_$Timestamp"
$TestEmail = "loadtest_$Timestamp@example.com"
$TestPassword = "Test123456!"

# 步骤 1: 注册测试用户
Write-Host "[STEP 1] Registering test user..." -ForegroundColor Yellow
$RegisterBody = @{
    userName = $TestUsername
    email = $TestEmail
    password = $TestPassword
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data.userId
    Write-Host "  [SUCCESS] User registered: $Global:TestUserId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] Failed to register user: $ErrorMsg" -ForegroundColor Red
    exit 1
}

# 步骤 2: 登录获取 Token
Write-Host "[STEP 2] Logging in..." -ForegroundColor Yellow
$LoginBody = @{
    email = $TestEmail
    password = $TestPassword
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "  [SUCCESS] Logged in successfully" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] Failed to login: $ErrorMsg" -ForegroundColor Red
    exit 1
}

# 步骤 3: 创建测试文章
Write-Host "[STEP 3] Creating test post..." -ForegroundColor Yellow
$PostBody = @{
    title = "Load Test Post - $Timestamp"
    raw = "This is a test post for load testing. Created at $Timestamp."
    topicId = "1"
}
$Headers = @{ "Authorization" = "Bearer $Global:AccessToken" }
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers $Headers

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestPostId = $Result.Body.data.id
    Write-Host "  [SUCCESS] Post created: $Global:TestPostId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] Failed to create post: $ErrorMsg" -ForegroundColor Red
    exit 1
}

# 步骤 4: 发布文章
Write-Host "[STEP 4] Publishing test post..." -ForegroundColor Yellow
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/publish" -Headers $Headers

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [SUCCESS] Post published" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [WARN] Failed to publish post: $ErrorMsg (continuing anyway)" -ForegroundColor Yellow
}

# 步骤 5: 创建测试评论
Write-Host "[STEP 5] Creating test comment..." -ForegroundColor Yellow
$CommentBody = @{
    postId = $Global:TestPostId
    content = "This is a test comment for load testing."
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$CommentServiceUrl/api/v1/comments" -Body $CommentBody -Headers $Headers

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestCommentId = $Result.Body.data.id
    Write-Host "  [SUCCESS] Comment created: $Global:TestCommentId" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [WARN] Failed to create comment: $ErrorMsg (continuing anyway)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Data Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User ID:    $Global:TestUserId" -ForegroundColor White
Write-Host "Post ID:    $Global:TestPostId" -ForegroundColor White
Write-Host "Comment ID: $Global:TestCommentId" -ForegroundColor White
Write-Host "Token:      $($Global:AccessToken.Substring(0, [Math]::Min(20, $Global:AccessToken.Length)))..." -ForegroundColor White
Write-Host ""
Write-Host "[INFO] You can now run the stress test with these IDs:" -ForegroundColor Cyan
Write-Host "  .\run-cache-stress-test.ps1 -TestUserId $Global:TestUserId -TestPostId $Global:TestPostId -TestCommentId $Global:TestCommentId -AccessToken `"$Global:AccessToken`"" -ForegroundColor White
Write-Host ""
Write-Host "[SUCCESS] Test data preparation complete!" -ForegroundColor Green
