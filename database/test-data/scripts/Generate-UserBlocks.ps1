# =====================================================
# Generate User Blocks Script
# 生成用户拉黑关系脚本
# 
# 说明：此脚本用于生成测试用户的拉黑关系数据
# 功能：
# 1. 为部分用户生成拉黑关系
# 2. 确保用户不拉黑自己
# 
# Requirements: 2.4, 2.5, 2.6
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$AppId = "test-app",
    [int]$MinBlocksPerUser = 0,
    [int]$MaxBlocksPerUser = 5,
    [double]$BlockRatio = 0.3,
    [switch]$DryRun
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 导入模块
$modulePath = Join-Path $PSScriptRoot "modules"
Import-Module (Join-Path $modulePath "ApiHelper.psm1") -Force

# 如果提供了配置文件路径，则加载配置
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    Write-ColorOutput "`n=== 加载配置文件 ===" "Cyan"
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    $ApiBaseUrl = $config.apiBaseUrl
    $AppId = $config.appId
    $MinBlocksPerUser = $config.userRelations.blocksPerUser.min
    $MaxBlocksPerUser = $config.userRelations.blocksPerUser.max
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  每用户拉黑数: $MinBlocksPerUser-$MaxBlocksPerUser" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $ApiBaseUrl = $config.apiBaseUrl
        $AppId = $config.appId
        $MinBlocksPerUser = $config.userRelations.blocksPerUser.min
        $MaxBlocksPerUser = $config.userRelations.blocksPerUser.max
        
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
    生成用户拉黑关系数据

.DESCRIPTION
    为部分用户生成随机数量的拉黑关系，确保用户不拉黑自己

.PARAMETER Users
    用户列表

.EXAMPLE
    New-UserBlocksData -Users $users
#>
function New-UserBlocksData {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成拉黑关系数据 ===" "Cyan"
    
    $blocks = @()
    $totalBlocks = 0
    
    # 计算有拉黑行为的用户数量
    $usersWithBlocks = [math]::Floor($Users.Count * $BlockRatio)
    Write-ColorOutput "  将为 $usersWithBlocks 个用户生成拉黑关系（$([math]::Round($BlockRatio * 100, 1))% 的用户）" "Gray"
    
    # 随机选择有拉黑行为的用户
    $selectedUsers = $Users | Get-Random -Count $usersWithBlocks
    
    foreach ($user in $selectedUsers) {
        # 为每个用户生成随机数量的拉黑关系
        $blockCount = Get-Random -Minimum $MinBlocksPerUser -Maximum ($MaxBlocksPerUser + 1)
        
        # 如果随机到 0，跳过此用户
        if ($blockCount -eq 0) {
            continue
        }
        
        # 获取可以拉黑的用户（排除自己）
        $availableUsers = $Users | Where-Object { $_.id -ne $user.id }
        
        # 如果可拉黑用户数量少于需要拉黑的数量，则调整拉黑数量
        if ($availableUsers.Count -lt $blockCount) {
            $blockCount = $availableUsers.Count
        }
        
        # 随机选择要拉黑的用户
        $blockedUsers = $availableUsers | Get-Random -Count $blockCount
        
        foreach ($blockedUser in $blockedUsers) {
            $blocks += @{
                blockerId = $user.id
                blockedId = $blockedUser.id
            }
            $totalBlocks++
        }
    }
    
    Write-ColorOutput "✓ 拉黑关系数据生成完成" "Green"
    Write-ColorOutput "  总拉黑关系数: $totalBlocks" "Gray"
    Write-ColorOutput "  有拉黑行为的用户数: $($selectedUsers.Count)" "Gray"
    if ($selectedUsers.Count -gt 0) {
        Write-ColorOutput "  平均每个拉黑用户拉黑数: $([math]::Round($totalBlocks / $selectedUsers.Count, 1))" "Gray"
    }
    
    return $blocks
}

<#
.SYNOPSIS
    创建拉黑关系

.DESCRIPTION
    调用 API 创建用户拉黑关系

.PARAMETER Block
    拉黑关系数据对象

.EXAMPLE
    New-UserBlock -Block @{blockerId=123; blockedId=456}
#>
function New-UserBlock {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Block
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $Block.blockerId
    }
    
    try {
        # 创建拉黑关系 - 正确的 API 端点: POST /api/v1/users/{blockerId}/blocking/{blockedId}
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/users/$($Block.blockerId)/blocking/$($Block.blockedId)" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
        
        return $true
    }
    catch {
        throw "创建拉黑关系失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          用户拉黑关系生成脚本                      ║" "Cyan"
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

# 步骤 3: 生成拉黑关系数据
try {
    $blocks = New-UserBlocksData -Users $users
}
catch {
    Write-ColorOutput "✗ 生成拉黑关系数据失败: $_" "Red"
    exit 1
}

# 检查是否有拉黑关系需要创建
if ($blocks.Count -eq 0) {
    Write-ColorOutput "`n⚠ 未生成任何拉黑关系" "Yellow"
    Write-ColorOutput "  这是正常的，因为拉黑关系是随机生成的" "Yellow"
    Write-ColorOutput "  可以调整 -BlockRatio 参数增加拉黑用户比例" "Yellow"
    exit 0
}

# 步骤 4: 创建拉黑关系
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "拉黑关系数据已生成但未创建" "Yellow"
    Write-ColorOutput "`n生成的拉黑关系预览（前 20 个）:" "Yellow"
    
    $previewCount = [math]::Min(20, $blocks.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $block = $blocks[$i]
        $blocker = $users | Where-Object { $_.id -eq $block.blockerId } | Select-Object -First 1
        $blocked = $users | Where-Object { $_.id -eq $block.blockedId } | Select-Object -First 1
        Write-ColorOutput "  $($i + 1). $($blocker.username) 拉黑 $($blocked.username)" "Gray"
    }
    
    if ($blocks.Count -gt 20) {
        Write-ColorOutput "  ... 还有 $($blocks.Count - 20) 个拉黑关系" "Gray"
    }
    
    # 显示统计信息
    Write-ColorOutput "`n统计信息:" "Yellow"
    Write-ColorOutput "  总拉黑关系数: $($blocks.Count)" "Gray"
    Write-ColorOutput "  用户数: $($users.Count)" "Gray"
    
    # 计算有拉黑行为的用户数
    $blockersCount = ($blocks | Select-Object -Property blockerId -Unique).Count
    Write-ColorOutput "  有拉黑行为的用户数: $blockersCount" "Gray"
    Write-ColorOutput "  平均每个拉黑用户拉黑数: $([math]::Round($blocks.Count / $blockersCount, 1))" "Gray"
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 创建拉黑关系 ===" "Cyan"

$createdBlocks = 0
$failedBlocks = @()

for ($i = 0; $i -lt $blocks.Count; $i++) {
    $block = $blocks[$i]
    
    try {
        Show-Progress -Current ($i + 1) -Total $blocks.Count -Message "创建拉黑关系"
        
        New-UserBlock -Block $block | Out-Null
        $createdBlocks++
    }
    catch {
        $failedBlocks += @{
            block = $block
            error = $_.Exception.Message
        }
    }
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 拉黑关系创建完成" "Green"
Write-ColorOutput "  成功: $createdBlocks" "Green"

if ($failedBlocks.Count -gt 0) {
    Write-ColorOutput "  失败: $($failedBlocks.Count)" "Red"
    Write-ColorOutput "`n失败的拉黑关系（前 10 个）:" "Red"
    $failurePreviewCount = [math]::Min(10, $failedBlocks.Count)
    for ($i = 0; $i -lt $failurePreviewCount; $i++) {
        $failed = $failedBlocks[$i]
        Write-ColorOutput "  - 拉黑者ID: $($failed.block.blockerId), 被拉黑者ID: $($failed.block.blockedId)" "Red"
        Write-ColorOutput "    错误: $($failed.error)" "Red"
    }
    if ($failedBlocks.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($failedBlocks.Count - 10) 个失败" "Gray"
    }
}

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  总数: $($blocks.Count)" "Gray"
Write-ColorOutput "  成功: $createdBlocks" "Green"
Write-ColorOutput "  失败: $($failedBlocks.Count)" "Red"
Write-ColorOutput "  成功率: $([math]::Round(($createdBlocks / $blocks.Count) * 100, 2))%" "Gray"
Write-ColorOutput "  用户数: $($users.Count)" "Gray"

# 计算有拉黑行为的用户数
$blockersCount = ($blocks | Select-Object -Property blockerId -Unique).Count
Write-ColorOutput "  有拉黑行为的用户数: $blockersCount" "Gray"
if ($blockersCount -gt 0) {
    Write-ColorOutput "  平均每个拉黑用户拉黑数: $([math]::Round($createdBlocks / $blockersCount, 1))" "Gray"
}

if ($createdBlocks -eq $blocks.Count) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          用户拉黑关系生成成功！                    ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          用户拉黑关系生成完成（部分失败）          ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试用户拉黑关系数据

.DESCRIPTION
    此脚本为部分测试用户生成 0-5 个拉黑关系，确保用户不拉黑自己，
    并通过 API 创建拉黑关系。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinBlocksPerUser
    每用户最小拉黑数，默认为 0

.PARAMETER MaxBlocksPerUser
    每用户最大拉黑数，默认为 5

.PARAMETER BlockRatio
    有拉黑行为的用户比例，默认为 0.3 (30%)

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-UserBlocks.ps1
    使用默认配置生成用户拉黑关系

.EXAMPLE
    .\Generate-UserBlocks.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-UserBlocks.ps1 -MinBlocksPerUser 1 -MaxBlocksPerUser 10
    自定义每用户拉黑数范围

.EXAMPLE
    .\Generate-UserBlocks.ps1 -BlockRatio 0.5
    设置 50% 的用户有拉黑行为

.EXAMPLE
    .\Generate-UserBlocks.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. blog-gateway 服务已启动（端口 8000）
    2. 已执行用户数据生成脚本
    3. 网络连接正常

    生成数据：
    - 部分用户（默认 30%）生成拉黑关系
    - 每个拉黑用户 0-5 个拉黑关系（默认）
    - 确保用户不拉黑自己

    验证需求：
    - Requirements 2.4: 为部分用户生成 1-5 个拉黑关系
    - Requirements 2.5: 确保 blocker_id 和 blocked_id 都是有效的用户 ID
    - Requirements 2.6: 确保用户不会拉黑自己
#>
