# =====================================================
# Generate Post Favorites Script
# 生成文章收藏记录脚本
# 
# 说明：此脚本用于生成文章收藏数据
# 功能：
# 1. 为已发布文章生成收藏记录
# 2. 更新 post_stats 的 favorite_count
# 3. 确保用户不重复收藏
# 
# Requirements: 5.6, 5.7, 5.8, 5.9
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$AppId = "test-app",
    [int]$MinFavorites = 2,
    [int]$MaxFavorites = 30,
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
    $MinFavorites = $config.interactions.favoritesPerPost.min
    $MaxFavorites = $config.interactions.favoritesPerPost.max
    
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
        $MinFavorites = $config.interactions.favoritesPerPost.min
        $MaxFavorites = $config.interactions.favoritesPerPost.max
        
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
    为文章添加收藏

.DESCRIPTION
    调用 API 为指定文章添加收藏

.PARAMETER PostId
    文章 ID

.PARAMETER UserId
    用户 ID

.EXAMPLE
    Add-PostFavorite -PostId 123 -UserId 456
#>
function Add-PostFavorite {
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
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts/$PostId/favorite" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
        
        return $true
    }
    catch {
        # 如果是重复收藏错误，忽略
        if ($_.Exception.Message -like "*已经收藏*" -or $_.Exception.Message -like "*already favorited*") {
            return $false
        }
        throw "添加收藏失败: $_"
    }
}

<#
.SYNOPSIS
    生成文章收藏数据

.DESCRIPTION
    为每篇已发布文章生成随机数量的收藏记录

.PARAMETER Posts
    文章列表

.PARAMETER Users
    用户列表

.EXAMPLE
    New-PostFavorites -Posts $posts -Users $users
#>
function New-PostFavorites {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Posts,
        
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成文章收藏数据 ===" "Cyan"
    
    $totalFavorites = 0
    $failedFavorites = 0
    
    for ($i = 0; $i -lt $Posts.Count; $i++) {
        $post = $Posts[$i]
        
        # 为每篇文章生成随机数量的收藏（MinFavorites 到 MaxFavorites）
        $favoriteCount = Get-Random -Minimum $MinFavorites -Maximum ($MaxFavorites + 1)
        
        # 随机选择不重复的用户
        $selectedUsers = $Users | Get-Random -Count ([Math]::Min($favoriteCount, $Users.Count))
        
        Show-Progress -Current ($i + 1) -Total $Posts.Count -Message "为文章 $($post.id) 生成 $favoriteCount 个收藏"
        
        foreach ($user in $selectedUsers) {
            try {
                $success = Add-PostFavorite -PostId $post.id -UserId $user.id
                if ($success) {
                    $totalFavorites++
                }
            }
            catch {
                $failedFavorites++
                Write-ColorOutput "`n⚠ 为文章 $($post.id) 添加收藏失败（用户 $($user.id)）: $_" "Yellow"
            }
        }
    }
    
    return @{
        TotalFavorites = $totalFavorites
        FailedFavorites = $failedFavorites
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          文章收藏数据生成脚本                      ║" "Cyan"
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

# 步骤 4: 生成收藏数据
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "收藏数据已计算但未创建" "Yellow"
    
    $totalEstimatedFavorites = 0
    foreach ($post in $posts) {
        $favoriteCount = Get-Random -Minimum $MinFavorites -Maximum ($MaxFavorites + 1)
        $totalEstimatedFavorites += $favoriteCount
    }
    
    Write-ColorOutput "`n预计生成:" "Yellow"
    Write-ColorOutput "  文章数量: $($posts.Count)" "Gray"
    Write-ColorOutput "  每篇文章收藏数: $MinFavorites - $MaxFavorites" "Gray"
    Write-ColorOutput "  预计总收藏数: 约 $totalEstimatedFavorites" "Gray"
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 生成收藏数据 ===" "Cyan"

try {
    $result = New-PostFavorites -Posts $posts -Users $users
}
catch {
    Write-ColorOutput "✗ 生成收藏数据失败: $_" "Red"
    exit 1
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 收藏数据生成完成" "Green"
Write-ColorOutput "  成功: $($result.TotalFavorites)" "Green"

if ($result.FailedFavorites -gt 0) {
    Write-ColorOutput "  失败: $($result.FailedFavorites)" "Red"
}

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  文章数量: $($posts.Count)" "Gray"
Write-ColorOutput "  总收藏数: $($result.TotalFavorites)" "Gray"
Write-ColorOutput "  平均每篇: $([math]::Round($result.TotalFavorites / $posts.Count, 2))" "Gray"

if ($result.FailedFavorites -eq 0) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          收藏数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          收藏数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成文章收藏测试数据

.DESCRIPTION
    此脚本为已发布文章生成收藏记录，调用 API 创建收藏，
    确保用户不重复收藏同一篇文章。

.PARAMETER ConfigPath
    配置文件路径（可选）

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinFavorites
    每篇文章最少收藏数，默认为 2

.PARAMETER MaxFavorites
    每篇文章最多收藏数，默认为 30

.PARAMETER DryRun
    仅计算数据但不创建，用于预览

.EXAMPLE
    .\Generate-PostFavorites.ps1
    使用默认配置生成收藏数据

.EXAMPLE
    .\Generate-PostFavorites.ps1 -DryRun
    预览模式

.EXAMPLE
    .\Generate-PostFavorites.ps1 -MinFavorites 5 -MaxFavorites 50
    自定义收藏数量范围

.NOTES
    前置条件：
    1. blog-gateway 服务已启动（端口 8000）
    2. 已执行用户数据生成脚本
    3. 已执行文章数据生成脚本
    4. 已执行 post_stats 初始化脚本

    验证需求：
    - Requirements 5.6: 为每篇已发布文章生成 2-30 个收藏记录
    - Requirements 5.7: 确保 post_id 和 user_id 都是有效的 ID
    - Requirements 5.8: 更新 post_stats 表的 favorite_count
    - Requirements 5.9: 确保同一用户不会重复收藏同一篇文章
#>
