# =====================================================
# Generate Post Likes Script
# 生成文章点赞记录脚本
# 
# 说明：此脚本用于生成文章点赞数据
# 功能：
# 1. 为已发布文章生成点赞记录
# 2. 更新 post_stats 的 like_count
# 3. 确保用户不重复点赞
# 
# Requirements: 5.3, 5.4, 5.5, 5.9
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$AppId = "test-app",
    [int]$MinLikes = 5,
    [int]$MaxLikes = 50,
    [switch]$DryRun
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 导入模块
$modulePath = Join-Path $PSScriptRoot "modules"
Import-Module (Join-Path $modulePath "ApiHelper.psm1") -Force
Import-Module (Join-Path $modulePath "DatabaseHelper.psm1") -Force

# 如果提供了配置文件路径，则加载配置
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    Write-ColorOutput "`n=== 加载配置文件 ===" "Cyan"
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    $ApiBaseUrl = $config.apiBaseUrl
    $AppId = $config.appId
    $MinLikes = $config.interactions.likesPerPost.min
    $MaxLikes = $config.interactions.likesPerPost.max
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $ApiBaseUrl = $config.apiBaseUrl
        $AppId = $config.appId
        $MinLikes = $config.interactions.likesPerPost.min
        $MaxLikes = $config.interactions.likesPerPost.max
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
    }
}

<#
.SYNOPSIS
    获取所有已发布文章

.DESCRIPTION
    从 API 获取所有已发布状态的文章

.EXAMPLE
    Get-PublishedPosts
#>
function Get-PublishedPosts {
    Write-ColorOutput "  获取已发布文章列表..." "Gray"
    
    try {
        # 获取已发布文章（status=1 表示已发布）
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts?status=PUBLISHED&page=1&size=1000" `
            -Method Get `
            -Headers @{
                "X-App-Id" = $AppId
            }
        
        if ($result -and $result.records -and $result.records.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($result.records.Count) 篇已发布文章" "Green"
            return $result.records
        }
        else {
            throw "未找到已发布文章"
        }
    }
    catch {
        throw "获取已发布文章失败: $_"
    }
}

<#
.SYNOPSIS
    获取所有测试用户

.DESCRIPTION
    从 API 获取所有测试用户

.EXAMPLE
    Get-TestUsers
#>
function Get-TestUsers {
    return Get-TestUsersFromDB
}

<#
.SYNOPSIS
    为文章添加点赞

.DESCRIPTION
    调用 API 为指定文章添加点赞

.PARAMETER PostId
    文章 ID

.PARAMETER UserId
    用户 ID

.EXAMPLE
    Add-PostLike -PostId 123 -UserId 456
#>
function Add-PostLike {
    param(
        [Parameter(Mandatory = $true)]
        [long]$PostId,
        
        [Parameter(Mandatory = $true)]
        [long]$UserId
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $UserId
    }
    
    try {
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts/$PostId/like" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
        
        return $true
    }
    catch {
        # 如果是重复点赞错误，忽略
        if ($_.Exception.Message -like "*已经点赞*" -or $_.Exception.Message -like "*already liked*") {
            return $false
        }
        throw "添加点赞失败: $_"
    }
}

<#
.SYNOPSIS
    生成文章点赞数据

.DESCRIPTION
    为每篇已发布文章生成随机数量的点赞记录

.PARAMETER Posts
    文章列表

.PARAMETER Users
    用户列表

.EXAMPLE
    New-PostLikes -Posts $posts -Users $users
#>
function New-PostLikes {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Posts,
        
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成文章点赞数据 ===" "Cyan"
    
    $totalLikes = 0
    $failedLikes = 0
    
    for ($i = 0; $i -lt $Posts.Count; $i++) {
        $post = $Posts[$i]
        
        # 为每篇文章生成随机数量的点赞（MinLikes 到 MaxLikes）
        $likeCount = Get-Random -Minimum $MinLikes -Maximum ($MaxLikes + 1)
        
        # 随机选择不重复的用户
        $selectedUsers = $Users | Get-Random -Count ([Math]::Min($likeCount, $Users.Count))
        
        Show-Progress -Current ($i + 1) -Total $Posts.Count -Message "为文章 $($post.id) 生成 $likeCount 个点赞"
        
        foreach ($user in $selectedUsers) {
            try {
                $success = Add-PostLike -PostId $post.id -UserId $user.id
                if ($success) {
                    $totalLikes++
                }
            }
            catch {
                $failedLikes++
                Write-ColorOutput "`n⚠ 为文章 $($post.id) 添加点赞失败（用户 $($user.id)）: $_" "Yellow"
            }
        }
    }
    
    return @{
        TotalLikes = $totalLikes
        FailedLikes = $failedLikes
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          文章点赞数据生成脚本                      ║" "Cyan"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Cyan"

# 步骤 1: 验证服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证服务可用性 ===" "Cyan"

Write-ColorOutput "  检查 API 服务..." "Gray"
$apiStatus = Test-ApiService -BaseUrl $ApiBaseUrl
if ($apiStatus.Available) {
    Write-ColorOutput "✓ API 服务正常运行" "Green"
}
else {
    Write-ColorOutput "✗ API 服务不可用" "Red"
    Write-ColorOutput "  错误: $($apiStatus.Message)" "Red"
    exit 1
}

# 步骤 2: 获取已发布文章
Write-ColorOutput "`n=== 步骤 2: 获取已发布文章 ===" "Cyan"

try {
    $posts = Get-PublishedPosts
}
catch {
    Write-ColorOutput "✗ 获取已发布文章失败: $_" "Red"
    exit 1
}

# 步骤 3: 获取测试用户
Write-ColorOutput "`n=== 步骤 3: 获取测试用户 ===" "Cyan"

try {
    $users = Get-TestUsers
}
catch {
    Write-ColorOutput "✗ 获取测试用户失败: $_" "Red"
    exit 1
}

# 步骤 4: 生成点赞数据
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "点赞数据已计算但未创建" "Yellow"
    
    $totalEstimatedLikes = 0
    foreach ($post in $posts) {
        $likeCount = Get-Random -Minimum $MinLikes -Maximum ($MaxLikes + 1)
        $totalEstimatedLikes += $likeCount
    }
    
    Write-ColorOutput "`n预计生成:" "Yellow"
    Write-ColorOutput "  文章数量: $($posts.Count)" "Gray"
    Write-ColorOutput "  每篇文章点赞数: $MinLikes - $MaxLikes" "Gray"
    Write-ColorOutput "  预计总点赞数: 约 $totalEstimatedLikes" "Gray"
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 生成点赞数据 ===" "Cyan"

try {
    $result = New-PostLikes -Posts $posts -Users $users
}
catch {
    Write-ColorOutput "✗ 生成点赞数据失败: $_" "Red"
    exit 1
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 点赞数据生成完成" "Green"
Write-ColorOutput "  成功: $($result.TotalLikes)" "Green"

if ($result.FailedLikes -gt 0) {
    Write-ColorOutput "  失败: $($result.FailedLikes)" "Red"
}

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  文章数量: $($posts.Count)" "Gray"
Write-ColorOutput "  总点赞数: $($result.TotalLikes)" "Gray"
Write-ColorOutput "  平均每篇: $([math]::Round($result.TotalLikes / $posts.Count, 2))" "Gray"

if ($result.FailedLikes -eq 0) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          点赞数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          点赞数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成文章点赞测试数据

.DESCRIPTION
    此脚本为已发布文章生成点赞记录，调用 API 创建点赞，
    确保用户不重复点赞同一篇文章。

.PARAMETER ConfigPath
    配置文件路径（可选）

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinLikes
    每篇文章最少点赞数，默认为 5

.PARAMETER MaxLikes
    每篇文章最多点赞数，默认为 50

.PARAMETER DryRun
    仅计算数据但不创建，用于预览

.EXAMPLE
    .\Generate-PostLikes.ps1
    使用默认配置生成点赞数据

.EXAMPLE
    .\Generate-PostLikes.ps1 -DryRun
    预览模式

.EXAMPLE
    .\Generate-PostLikes.ps1 -MinLikes 10 -MaxLikes 100
    自定义点赞数量范围

.NOTES
    前置条件：
    1. ZhiCore-gateway 服务已启动（端口 8000）
    2. 已执行用户数据生成脚本
    3. 已执行文章数据生成脚本
    4. 已执行 post_stats 初始化脚本

    验证需求：
    - Requirements 5.3: 为每篇已发布文章生成 5-50 个点赞记录
    - Requirements 5.4: 确保 post_id 和 user_id 都是有效的 ID
    - Requirements 5.5: 更新 post_stats 表的 like_count
    - Requirements 5.9: 确保同一用户不会重复点赞同一篇文章
#>
