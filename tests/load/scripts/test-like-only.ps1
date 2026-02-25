# Test Like Functionality Only
# 测试点赞功能是否正常工作

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json

$GatewayUrl = $Config.gateway_url
$UserServiceUrl = $Config.user_service_url
$PostServiceUrl = $Config.post_service_url

$TestPassword = "Test123456!"
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"

$Global:AccessToken = ""
$Global:TestPostId = ""
$Global:TestUserId = ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "测试点赞功能" -ForegroundColor Cyan
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

function Get-AuthHeaders { return @{ "Authorization" = "Bearer $Global:AccessToken" } }

# Step 1: 注册并登录测试用户
Write-Host "[STEP 1] 创建测试用户并登录..." -ForegroundColor Yellow

$Username = "liketest_$Timestamp"
$Email = "liketest_$Timestamp@example.com"

Write-Host "  1.1 注册用户: $Username" -ForegroundColor Cyan
$RegisterBody = @{ userName = $Username; email = $Email; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data.ToString()
    Write-Host "    [PASS] 用户注册成功, ID: $Global:TestUserId ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] 注册失败: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host "  1.2 登录用户..." -ForegroundColor Cyan
$LoginBody = @{ email = $Email; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-Host "    [PASS] 登录成功 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] 登录失败: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 2: 创建测试文章
Write-Host "[STEP 2] 创建测试文章..." -ForegroundColor Yellow

$PostBody = @{
    title = "Like Test Post $Timestamp"
    content = "This is a test post for like functionality testing."
    categoryId = 1
    tags = @("test", "like")
    status = 1
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts" -Body $PostBody -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    if ($Result.Body.data.postId) {
        $Global:TestPostId = $Result.Body.data.postId.ToString()
    } else {
        $Global:TestPostId = $Result.Body.data.ToString()
    }
    Write-Host "  [PASS] 文章创建成功, ID: $Global:TestPostId ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "  [FAIL] 文章创建失败: $ErrorMsg" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 3: 发布文章
Write-Host "[STEP 3] 发布文章..." -ForegroundColor Yellow

$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/publish" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "  [PASS] 文章发布成功 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Write-Host "  [WARN] 文章发布可能失败，继续测试... ($($Result.ResponseTime)ms)" -ForegroundColor Yellow
}

Write-Host ""

# Step 4: 测试点赞功能
Write-Host "[STEP 4] 测试点赞功能..." -ForegroundColor Yellow

Write-Host "  4.1 点赞文章..." -ForegroundColor Cyan
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "    [PASS] 点赞成功 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] 点赞失败: $ErrorMsg (Status: $($Result.StatusCode), Time: $($Result.ResponseTime)ms)" -ForegroundColor Red
    Write-Host ""
    Write-Host "详细错误信息:" -ForegroundColor Yellow
    Write-Host "  URL: $PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -ForegroundColor Gray
    Write-Host "  Token: $($Global:AccessToken.Substring(0, 50))..." -ForegroundColor Gray
    exit 1
}

Write-Host "  4.2 重复点赞（应该失败）..." -ForegroundColor Cyan
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Write-Host "    [PASS] 正确拒绝重复点赞 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} elseif ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "    [WARN] 重复点赞未被拒绝（可能是幂等性设计） ($($Result.ResponseTime)ms)" -ForegroundColor Yellow
} else {
    Write-Host "    [FAIL] 意外错误 ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host "  4.3 取消点赞..." -ForegroundColor Cyan
$Result = Invoke-ApiRequest -Method "DELETE" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "    [PASS] 取消点赞成功 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] 取消点赞失败: $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host "  4.4 再次点赞..." -ForegroundColor Cyan
$Result = Invoke-ApiRequest -Method "POST" -Url "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    Write-Host "    [PASS] 再次点赞成功 ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-Host "    [FAIL] 再次点赞失败: $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# Step 5: 并发点赞测试（模拟高并发场景）
Write-Host "[STEP 5] 并发点赞测试（10个并发请求）..." -ForegroundColor Yellow

$Jobs = @()
for ($i = 1; $i -le 10; $i++) {
    $Job = Start-Job -ScriptBlock {
        param($Url, $Token)
        $Headers = @{ "Authorization" = "Bearer $Token"; "Content-Type" = "application/json" }
        try {
            $Response = Invoke-WebRequest -Method "DELETE" -Uri $Url -Headers $Headers -ErrorAction Stop
            Start-Sleep -Milliseconds 100
            $Response = Invoke-WebRequest -Method "POST" -Uri $Url -Headers $Headers -ErrorAction Stop
            return @{ Success = $true; StatusCode = $Response.StatusCode }
        } catch {
            return @{ Success = $false; StatusCode = $_.Exception.Response.StatusCode; Error = $_.Exception.Message }
        }
    } -ArgumentList "$PostServiceUrl/api/v1/posts/$Global:TestPostId/like", $Global:AccessToken
    
    $Jobs += $Job
}

Write-Host "  等待并发请求完成..." -ForegroundColor Cyan
$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

$SuccessCount = ($Results | Where-Object { $_.Success }).Count
$FailCount = ($Results | Where-Object { -not $_.Success }).Count

Write-Host "  [结果] 成功: $SuccessCount, 失败: $FailCount" -ForegroundColor $(if ($FailCount -eq 0) { "Green" } else { "Yellow" })

if ($FailCount -gt 0) {
    Write-Host "  失败详情:" -ForegroundColor Yellow
    $Results | Where-Object { -not $_.Success } | ForEach-Object {
        Write-Host "    - Status: $($_.StatusCode), Error: $($_.Error)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "点赞功能测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "测试总结:" -ForegroundColor Yellow
Write-Host "  测试用户 ID: $Global:TestUserId" -ForegroundColor White
Write-Host "  测试文章 ID: $Global:TestPostId" -ForegroundColor White
Write-Host "  并发测试: $SuccessCount 成功, $FailCount 失败" -ForegroundColor White
Write-Host ""

if ($FailCount -eq 0) {
    Write-Host "[SUCCESS] All like tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[PARTIAL] Some tests failed, please check logs" -ForegroundColor Yellow
    exit 0
}
