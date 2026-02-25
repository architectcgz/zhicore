# =====================================================
# Generate User Follows Script
# 生成用户关注关系脚本
# 
# 说明：此脚本用于生成测试用户的关注关系数据
# 功能：
# 1. 为每个用户生成 5-20 个关注关系
# 2. 更新 user_follow_stats 统计
# 3. 确保用户不关注自己
# 
# Requirements: 2.1, 2.2, 2.3, 2.6
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$AppId = "test-app",
    [int]$MinFollowsPerUser = 5,
    [int]$MaxFollowsPerUser = 20,
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
    $MinFollowsPerUser = $config.userRelations.followsPerUser.min
    $MaxFollowsPerUser = $config.userRelations.followsPerUser.max
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  每用户关注数: $MinFollowsPerUser-$MaxFollowsPerUser" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $ApiBaseUrl = $config.apiBaseUrl
        $AppId = $config.appId
        $MinFollowsPerUser = $config.userRelations.followsPerUser.min
        $MaxFollowsPerUser = $config.userRelations.followsPerUser.max
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

<#
.SYNOPSIS
    获取所有测试用户

.DESCRIPTION
    从数据库获取所有测试用户的 ID

.EXAMPLE
    Get-TestUsers
#>
function Get-TestUsers {
    return Get-TestUsersFromDB
}

<#
.SYNOPSIS
    生成用户关注关系数据

.DESCRIPTION
    为每个用户生成随机数量的关注关系，确保用户不关注自己

.PARAMETER Users
    用户列表

.EXAMPLE
    New-UserFollowsData -Users $users
#>
function New-UserFollowsData {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成关注关系数据 ===" "Cyan"
    
    $follows = @()
    $totalFollows = 0
    
    foreach ($user in $Users) {
        # 为每个用户生成随机数量的关注
        $followCount = Get-Random -Minimum $MinFollowsPerUser -Maximum ($MaxFollowsPerUser + 1)
        
        # 获取可以关注的用户（排除自己）
        $availableUsers = $Users | Where-Object { $_.id -ne $user.id }
        
        # 如果可关注用户数量少于需要关注的数量，则调整关注数量
        if ($availableUsers.Count -lt $followCount) {
            $followCount = $availableUsers.Count
        }
        
        # 随机选择要关注的用户
        $followingUsers = $availableUsers | Get-Random -Count $followCount
        
        foreach ($followingUser in $followingUsers) {
            $follows += @{
                followerId = $user.id
                followingId = $followingUser.id
            }
            $totalFollows++
        }
    }
    
    Write-ColorOutput "✓ 关注关系数据生成完成" "Green"
    Write-ColorOutput "  总关注关系数: $totalFollows" "Gray"
    Write-ColorOutput "  平均每用户关注数: $([math]::Round($totalFollows / $Users.Count, 1))" "Gray"
    
    return $follows
}

<#
.SYNOPSIS
    创建关注关系

.DESCRIPTION
    调用 API 创建用户关注关系

.PARAMETER Follow
    关注关系数据对象

.EXAMPLE
    New-UserFollow -Follow @{followerId=123; followingId=456}
#>
function New-UserFollow {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Follow
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $Follow.followerId
    }
    
    try {
        # 创建关注关系 - 正确的 API 端点: POST /api/v1/users/{userId}/following/{targetUserId}
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/users/$($Follow.followerId)/following/$($Follow.followingId)" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
        
        return $true
    }
    catch {
        throw "创建关注关系失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          用户关注关系生成脚本                      ║" "Cyan"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Cyan"

# 步骤 1: 验证服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证服务可用性 ===" "Cyan"

# 验证 API 服务
Write-ColorOutput "  检查 API 服务..." "Gray"
$apiStatus = Test-ApiService -BaseUrl $ApiBaseUrl
if ($apiStatus.Available) {
    Write-ColorOutput "✓ API 服务正常运行" "Green"
    Write-ColorOutput "  状态: $($apiStatus.Status)" "Gray"
}
else {
    Write-ColorOutput "✗ API 服务不可用" "Red"
    Write-ColorOutput "  错误: $($apiStatus.Message)" "Red"
    Write-ColorOutput "  请确保服务已启动: $ApiBaseUrl" "Yellow"
    exit 1
}

# 步骤 2: 获取测试用户
Write-ColorOutput "`n=== 步骤 2: 获取测试用户 ===" "Cyan"

try {
    $users = Get-TestUsers
}
catch {
    Write-ColorOutput "✗ 获取测试用户失败: $_" "Red"
    Write-ColorOutput "  请确保已执行用户数据生成脚本" "Yellow"
    exit 1
}

# 步骤 3: 生成关注关系数据
try {
    $follows = New-UserFollowsData -Users $users
}
catch {
    Write-ColorOutput "✗ 生成关注关系数据失败: $_" "Red"
    exit 1
}

# 步骤 4: 创建关注关系
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "关注关系数据已生成但未创建" "Yellow"
    Write-ColorOutput "`n生成的关注关系预览（前 20 个）:" "Yellow"
    
    $previewCount = [math]::Min(20, $follows.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $follow = $follows[$i]
        $follower = $users | Where-Object { $_.id -eq $follow.followerId } | Select-Object -First 1
        $following = $users | Where-Object { $_.id -eq $follow.followingId } | Select-Object -First 1
        Write-ColorOutput "  $($i + 1). $($follower.username) 关注 $($following.username)" "Gray"
    }
    
    if ($follows.Count -gt 20) {
        Write-ColorOutput "  ... 还有 $($follows.Count - 20) 个关注关系" "Gray"
    }
    
    # 显示统计信息
    Write-ColorOutput "`n统计信息:" "Yellow"
    Write-ColorOutput "  总关注关系数: $($follows.Count)" "Gray"
    Write-ColorOutput "  用户数: $($users.Count)" "Gray"
    Write-ColorOutput "  平均每用户关注数: $([math]::Round($follows.Count / $users.Count, 1))" "Gray"
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 创建关注关系 ===" "Cyan"

$createdFollows = 0
$failedFollows = @()

for ($i = 0; $i -lt $follows.Count; $i++) {
    $follow = $follows[$i]
    
    try {
        Show-Progress -Current ($i + 1) -Total $follows.Count -Message "创建关注关系"
        
        New-UserFollow -Follow $follow | Out-Null
        $createdFollows++
    }
    catch {
        $failedFollows += @{
            follow = $follow
            error = $_.Exception.Message
        }
    }
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 关注关系创建完成" "Green"
Write-ColorOutput "  成功: $createdFollows" "Green"

if ($failedFollows.Count -gt 0) {
    Write-ColorOutput "  失败: $($failedFollows.Count)" "Red"
    Write-ColorOutput "`n失败的关注关系（前 10 个）:" "Red"
    $failurePreviewCount = [math]::Min(10, $failedFollows.Count)
    for ($i = 0; $i -lt $failurePreviewCount; $i++) {
        $failed = $failedFollows[$i]
        Write-ColorOutput "  - 关注者ID: $($failed.follow.followerId), 被关注者ID: $($failed.follow.followingId)" "Red"
        Write-ColorOutput "    错误: $($failed.error)" "Red"
    }
    if ($failedFollows.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($failedFollows.Count - 10) 个失败" "Gray"
    }
}

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  总数: $($follows.Count)" "Gray"
Write-ColorOutput "  成功: $createdFollows" "Green"
Write-ColorOutput "  失败: $($failedFollows.Count)" "Red"
Write-ColorOutput "  成功率: $([math]::Round(($createdFollows / $follows.Count) * 100, 2))%" "Gray"
Write-ColorOutput "  用户数: $($users.Count)" "Gray"
Write-ColorOutput "  平均每用户关注数: $([math]::Round($createdFollows / $users.Count, 1))" "Gray"

if ($createdFollows -eq $follows.Count) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          用户关注关系生成成功！                    ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          用户关注关系生成完成（部分失败）          ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试用户关注关系数据

.DESCRIPTION
    此脚本为每个测试用户生成 5-20 个关注关系，确保用户不关注自己，
    并通过 API 创建关注关系，自动更新 user_follow_stats 统计。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinFollowsPerUser
    每用户最小关注数，默认为 5

.PARAMETER MaxFollowsPerUser
    每用户最大关注数，默认为 20

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-UserFollows.ps1
    使用默认配置生成用户关注关系

.EXAMPLE
    .\Generate-UserFollows.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-UserFollows.ps1 -MinFollowsPerUser 10 -MaxFollowsPerUser 30
    自定义每用户关注数范围

.EXAMPLE
    .\Generate-UserFollows.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-gateway 服务已启动（端口 8000）
    2. 已执行用户数据生成脚本
    3. 网络连接正常

    生成数据：
    - 每个用户 5-20 个关注关系（默认）
    - 确保用户不关注自己
    - 自动更新 user_follow_stats 统计

    验证需求：
    - Requirements 2.1: 为每个用户生成 5-20 个关注关系
    - Requirements 2.2: 确保 follower_id 和 following_id 都是有效的用户 ID
    - Requirements 2.3: 更新 user_follow_stats 表的统计数据
    - Requirements 2.6: 确保用户不会关注自己
#>
