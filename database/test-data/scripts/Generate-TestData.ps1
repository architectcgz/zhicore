# =====================================================
# Generate Test Data - Main Script
# 测试数据生成主脚本
# 
# 说明：协调执行所有测试数据生成脚本
# 功能：按正确顺序生成所有测试数据
# =====================================================

param(
    [string]$ConfigFile = "..\test-data-config.json",
    [switch]$OnlyUsers,
    [switch]$OnlyPosts,
    [switch]$CleanOldData,
    [switch]$SkipValidation,
    [switch]$DryRun
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

# 显示标题
function Show-Title {
    param([string]$Title)
    Write-ColorOutput "`n$('=' * 60)" "Cyan"
    Write-ColorOutput "  $Title" "Cyan"
    Write-ColorOutput "$('=' * 60)" "Cyan"
}

# 显示步骤
function Show-Step {
    param([string]$Step)
    Write-ColorOutput "`n>>> $Step" "Yellow"
}

# 检查服务可用性
function Test-ServiceAvailability {
    param(
        [string]$ServiceName,
        [string]$Url,
        [int]$TimeoutSec = 5
    )
    
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec $TimeoutSec -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-ColorOutput "✓ $ServiceName 服务正常" "Green"
            return $true
        }
    }
    catch {
        Write-ColorOutput "✗ $ServiceName 服务不可用: $Url" "Red"
        Write-ColorOutput "  错误: $_" "Red"
        return $false
    }
}

# 开始执行
Show-Title "Blog 测试数据生成工具"
Write-ColorOutput "开始时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" "Gray"

# 步骤 1: 加载配置
Show-Step "步骤 1: 加载配置文件"
$configPath = Join-Path $PSScriptRoot $ConfigFile
if (-not (Test-Path $configPath)) {
    Write-ColorOutput "✗ 配置文件不存在: $configPath" "Red"
    exit 1
}

try {
    $config = Get-Content $configPath -Raw | ConvertFrom-Json
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $($config.apiBaseUrl)" "Gray"
    Write-ColorOutput "  ID Generator: $($config.idGeneratorUrl)" "Gray"
}
catch {
    Write-ColorOutput "✗ 配置文件解析失败: $_" "Red"
    exit 1
}

# 步骤 2: 验证服务可用性
Show-Step "步骤 2: 验证服务可用性"
$servicesOk = $true

# 检查 ID Generator
if (-not (Test-ServiceAvailability "ID Generator" "$($config.idGeneratorUrl)/actuator/health")) {
    $servicesOk = $false
}

# 检查 Gateway
if (-not (Test-ServiceAvailability "Blog Gateway" "$($config.apiBaseUrl)/actuator/health")) {
    $servicesOk = $false
}

if (-not $servicesOk) {
    Write-ColorOutput "`n✗ 部分服务不可用，请先启动所有必需的服务" "Red"
    Write-ColorOutput "  提示: 运行 docker-compose up -d 启动所有服务" "Yellow"
    exit 1
}

Write-ColorOutput "`n✓ 所有服务验证通过" "Green"

if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "配置验证完成，未执行数据生成" "Yellow"
    exit 0
}

# 步骤 3: 生成用户数据
if (-not $OnlyPosts) {
    Show-Step "步骤 3: 生成用户数据"
    Write-ColorOutput "  管理员: $($config.users.adminCount) 个" "Gray"
    Write-ColorOutput "  审核员: $($config.users.moderatorCount) 个" "Gray"
    Write-ColorOutput "  普通用户: $($config.users.regularCount) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Execute-UserGeneration.ps1" `
            -IdGeneratorUrl $config.idGeneratorUrl
        
        Write-ColorOutput "✓ 用户数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 用户数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 4: 生成标签数据
if (-not $OnlyUsers) {
    Show-Step "步骤 4: 生成标签数据"
    Write-ColorOutput "  标签数量: $($config.tags.count) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Tags-DB.ps1" `
            -IdGeneratorUrl $config.idGeneratorUrl `
            -TagCount $config.tags.count
        
        Write-ColorOutput "✓ 标签数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 标签数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 5: 生成文章数据
if (-not $OnlyUsers) {
    Show-Step "步骤 5: 生成文章数据"
    Write-ColorOutput "  文章总数: $($config.posts.totalCount) 篇" "Gray"
    Write-ColorOutput "  已发布: $([math]::Round($config.posts.totalCount * $config.posts.publishedRatio)) 篇" "Gray"
    Write-ColorOutput "  草稿: $([math]::Round($config.posts.totalCount * $config.posts.draftRatio)) 篇" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Posts.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -IdGeneratorUrl $config.idGeneratorUrl `
            -TotalCount $config.posts.totalCount `
            -PublishedRatio $config.posts.publishedRatio `
            -DraftRatio $config.posts.draftRatio
        
        Write-ColorOutput "✓ 文章数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 文章数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 6: 关联文章标签
if (-not $OnlyUsers) {
    Show-Step "步骤 6: 关联文章标签"
    Write-ColorOutput "  每篇文章标签数: $($config.tags.tagsPerPost.min)-$($config.tags.tagsPerPost.max) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Associate-PostTags.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinTags $config.tags.tagsPerPost.min `
            -MaxTags $config.tags.tagsPerPost.max
        
        Write-ColorOutput "✓ 文章标签关联完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 文章标签关联失败: $_" "Red"
        exit 1
    }
}

# 步骤 7: 生成文章点赞数据
if (-not $OnlyUsers) {
    Show-Step "步骤 7: 生成文章点赞数据"
    Write-ColorOutput "  每篇文章点赞数: $($config.interactions.likesPerPost.min)-$($config.interactions.likesPerPost.max) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-PostLikes.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinLikes $config.interactions.likesPerPost.min `
            -MaxLikes $config.interactions.likesPerPost.max
        
        Write-ColorOutput "✓ 文章点赞数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 文章点赞数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 8: 生成文章收藏数据
if (-not $OnlyUsers) {
    Show-Step "步骤 8: 生成文章收藏数据"
    Write-ColorOutput "  每篇文章收藏数: $($config.interactions.favoritesPerPost.min)-$($config.interactions.favoritesPerPost.max) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-PostFavorites.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinFavorites $config.interactions.favoritesPerPost.min `
            -MaxFavorites $config.interactions.favoritesPerPost.max
        
        Write-ColorOutput "✓ 文章收藏数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 文章收藏数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 9: 生成评论数据
if (-not $OnlyUsers) {
    Show-Step "步骤 9: 生成评论数据"
    Write-ColorOutput "  每篇文章评论数: $($config.comments.perPost.min)-$($config.comments.perPost.max) 个" "Gray"
    Write-ColorOutput "  回复比例: $($config.comments.replyRatio * 100)%" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Comments.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinComments $config.comments.perPost.min `
            -MaxComments $config.comments.perPost.max `
            -ReplyRatio $config.comments.replyRatio
        
        Write-ColorOutput "✓ 评论数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 评论数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 10: 生成用户关注关系
if (-not $OnlyPosts) {
    Show-Step "步骤 10: 生成用户关注关系"
    Write-ColorOutput "  每个用户关注数: $($config.userRelations.followsPerUser.min)-$($config.userRelations.followsPerUser.max) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-UserFollows.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinFollows $config.userRelations.followsPerUser.min `
            -MaxFollows $config.userRelations.followsPerUser.max
        
        Write-ColorOutput "✓ 用户关注关系生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 用户关注关系生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 11: 生成用户拉黑关系
if (-not $OnlyPosts) {
    Show-Step "步骤 11: 生成用户拉黑关系"
    Write-ColorOutput "  每个用户拉黑数: $($config.userRelations.blocksPerUser.min)-$($config.userRelations.blocksPerUser.max) 个" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-UserBlocks.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -MinBlocks $config.userRelations.blocksPerUser.min `
            -MaxBlocks $config.userRelations.blocksPerUser.max
        
        Write-ColorOutput "✓ 用户拉黑关系生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 用户拉黑关系生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 12: 生成私信数据
if (-not $OnlyPosts) {
    Show-Step "步骤 12: 生成私信数据"
    Write-ColorOutput "  会话数量: $($config.messages.conversationCount) 个" "Gray"
    Write-ColorOutput "  每个会话消息数: $($config.messages.messagesPerConversation.min)-$($config.messages.messagesPerConversation.max) 条" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Messages.ps1" `
            -ApiBaseUrl $config.apiBaseUrl `
            -ConversationCount $config.messages.conversationCount `
            -MinMessages $config.messages.messagesPerConversation.min `
            -MaxMessages $config.messages.messagesPerConversation.max
        
        Write-ColorOutput "✓ 私信数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 私信数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 13: 生成通知数据
if (-not $OnlyPosts) {
    Show-Step "步骤 13: 生成通知数据"
    Write-ColorOutput "  每个用户通知数: $($config.notifications.perUser.min)-$($config.notifications.perUser.max) 条" "Gray"
    Write-ColorOutput "  已读比例: $($config.notifications.readRatio * 100)%" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Notifications.ps1" `
            -IdGeneratorUrl $config.idGeneratorUrl `
            -MinNotificationsPerUser $config.notifications.perUser.min `
            -MaxNotificationsPerUser $config.notifications.perUser.max `
            -ReadRatio $config.notifications.readRatio
        
        Write-ColorOutput "✓ 通知数据生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 通知数据生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 14: 生成全局公告
if (-not $OnlyPosts) {
    Show-Step "步骤 14: 生成全局公告"
    Write-ColorOutput "  公告数量: $($config.announcements.count) 条" "Gray"
    Write-ColorOutput "  启用比例: $($config.announcements.enabledRatio * 100)%" "Gray"
    
    try {
        & "$PSScriptRoot\Generate-Announcements.ps1" `
            -IdGeneratorUrl $config.idGeneratorUrl `
            -AnnouncementCount $config.announcements.count `
            -EnabledRatio $config.announcements.enabledRatio
        
        Write-ColorOutput "✓ 全局公告生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 全局公告生成失败: $_" "Red"
        exit 1
    }
}

# 步骤 15: 生成小助手消息
if (-not $OnlyPosts) {
    Show-Step "步骤 15: 生成小助手消息"
    
    try {
        & "$PSScriptRoot\Generate-AssistantMessages.ps1" `
            -IdGeneratorUrl $config.idGeneratorUrl
        
        Write-ColorOutput "✓ 小助手消息生成完成" "Green"
    }
    catch {
        Write-ColorOutput "✗ 小助手消息生成失败: $_" "Red"
        exit 1
    }
}

# 完成
Show-Title "测试数据生成完成"
Write-ColorOutput "结束时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" "Gray"
Write-ColorOutput "`n✓ 所有测试数据已成功生成" "Green"
Write-ColorOutput "`n提示:" "Yellow"
Write-ColorOutput "  - 默认用户密码: Test@123456" "Yellow"
Write-ColorOutput "  - 用户名前缀: test_" "Yellow"
Write-ColorOutput "  - 可以通过 Blog Gateway (http://localhost:8100) 访问 API" "Yellow"

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成 Blog 微服务测试数据

.DESCRIPTION
    按正确顺序执行所有测试数据生成脚本，创建完整的测试数据集。
    包括用户、文章、评论、互动、关系、消息、通知等数据。

.PARAMETER ConfigFile
    配置文件路径，默认为 ..\test-data-config.json

.PARAMETER OnlyUsers
    仅生成用户数据

.PARAMETER OnlyPosts
    仅生成文章相关数据（跳过用户和关系数据）

.PARAMETER CleanOldData
    清理旧数据后重新生成

.PARAMETER SkipValidation
    跳过数据验证步骤

.PARAMETER DryRun
    仅验证配置和服务可用性，不执行数据生成

.EXAMPLE
    .\Generate-TestData.ps1
    使用默认配置生成所有测试数据

.EXAMPLE
    .\Generate-TestData.ps1 -OnlyUsers
    仅生成用户数据

.EXAMPLE
    .\Generate-TestData.ps1 -DryRun
    验证配置和服务可用性

.EXAMPLE
    .\Generate-TestData.ps1 -ConfigFile "custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. 所有 Blog 微服务已启动
    2. PostgreSQL、MongoDB、Redis 已启动
    3. ID Generator 服务已启动
    4. 数据库已初始化

    执行时间：约 5-15 分钟（取决于配置和硬件）
#>
