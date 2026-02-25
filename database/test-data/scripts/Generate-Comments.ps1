# =====================================================
# Generate Comments Script
# 生成评论数据脚本
# 
# 说明：此脚本用于生成文章评论数据
# 功能：
# 1. 为已发布文章生成一级评论
# 2. 为部分评论生成嵌套回复
# 3. 创建 comment_stats 记录
# 4. 更新 post_stats 的 comment_count
# 5. 为部分评论生成点赞记录
# 6. 更新 comment_stats 的 like_count 和 reply_count
# 
# Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [string]$AppId = "test-app",
    [int]$MinCommentsPerPost = 5,
    [int]$MaxCommentsPerPost = 30,
    [double]$ReplyRatio = 0.3,
    [switch]$DryRun
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 导入模块
$modulePath = Join-Path $PSScriptRoot "modules"
Import-Module (Join-Path $modulePath "IdGenerator.psm1") -Force
Import-Module (Join-Path $modulePath "ApiHelper.psm1") -Force
Import-Module (Join-Path $modulePath "DatabaseHelper.psm1") -Force

# 如果提供了配置文件路径，则加载配置
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    Write-ColorOutput "`n=== 加载配置文件 ===" "Cyan"
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    $ApiBaseUrl = $config.apiBaseUrl
    $IdGeneratorUrl = $config.idGeneratorUrl
    $AppId = $config.appId
    $MinCommentsPerPost = $config.comments.perPost.min
    $MaxCommentsPerPost = $config.comments.perPost.max
    $ReplyRatio = $config.comments.replyRatio
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  每篇文章评论数: $MinCommentsPerPost-$MaxCommentsPerPost" "Gray"
    Write-ColorOutput "  回复比例: $($ReplyRatio * 100)%" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $ApiBaseUrl = $config.apiBaseUrl
        $IdGeneratorUrl = $config.idGeneratorUrl
        $AppId = $config.appId
        $MinCommentsPerPost = $config.comments.perPost.min
        $MaxCommentsPerPost = $config.comments.perPost.max
        $ReplyRatio = $config.comments.replyRatio
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 预定义的评论内容模板
$commentTemplates = @(
    "写得很好，学到了很多！",
    "感谢分享，非常实用的内容。",
    "这篇文章解决了我的问题，太棒了！",
    "讲解得很清楚，期待更多这样的文章。",
    "内容很详细，收藏了！",
    "很有深度的文章，值得反复阅读。",
    "实战经验分享，非常有价值。",
    "代码示例很清晰，容易理解。",
    "这个方案很不错，我也遇到过类似的问题。",
    "文章质量很高，作者辛苦了！",
    "学习了，正好项目中需要用到。",
    "总结得很全面，赞一个！",
    "思路很清晰，逻辑性强。",
    "干货满满，已经在项目中实践了。",
    "这个技术点讲得很透彻。",
    "文章结构很好，易于理解。",
    "实用性很强，马上就能用上。",
    "深入浅出，适合初学者。",
    "作者的经验很丰富，学习了。",
    "这个解决方案很优雅。"
)

# 回复内容模板
$replyTemplates = @(
    "同意你的观点！",
    "说得对，我也是这么认为的。",
    "感谢补充，学习了。",
    "这个建议不错。",
    "确实是这样的。",
    "有道理，值得思考。",
    "我也遇到过类似的情况。",
    "这个角度很新颖。",
    "感谢分享经验。",
    "受教了！"
)

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
        # 获取已发布文章
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
    创建一级评论

.DESCRIPTION
    为文章创建一级评论

.PARAMETER PostId
    文章 ID

.PARAMETER AuthorId
    评论作者 ID

.PARAMETER Content
    评论内容

.EXAMPLE
    New-TopLevelComment -PostId 123 -AuthorId 456 -Content "很好的文章"
#>
function New-TopLevelComment {
    param(
        [Parameter(Mandatory = $true)]
        [long]$PostId,
        
        [Parameter(Mandatory = $true)]
        [long]$AuthorId,
        
        [Parameter(Mandatory = $true)]
        [string]$Content
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $AuthorId
    }
    
    $body = @{
        postId = $PostId
        content = $Content
    }
    
    try {
        $commentId = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/comments" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        return $commentId
    }
    catch {
        throw "创建评论失败: $_"
    }
}

<#
.SYNOPSIS
    创建回复评论

.DESCRIPTION
    为评论创建回复

.PARAMETER PostId
    文章 ID

.PARAMETER RootId
    根评论 ID

.PARAMETER ReplyToCommentId
    被回复的评论 ID

.PARAMETER AuthorId
    回复作者 ID

.PARAMETER Content
    回复内容

.EXAMPLE
    New-ReplyComment -PostId 123 -RootId 789 -ReplyToCommentId 789 -AuthorId 456 -Content "同意"
#>
function New-ReplyComment {
    param(
        [Parameter(Mandatory = $true)]
        [long]$PostId,
        
        [Parameter(Mandatory = $true)]
        [long]$RootId,
        
        [Parameter(Mandatory = $true)]
        [long]$ReplyToCommentId,
        
        [Parameter(Mandatory = $true)]
        [long]$AuthorId,
        
        [Parameter(Mandatory = $true)]
        [string]$Content
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $AuthorId
    }
    
    $body = @{
        postId = $PostId
        content = $Content
        rootId = $RootId
        replyToCommentId = $ReplyToCommentId
    }
    
    try {
        $commentId = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/comments" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        return $commentId
    }
    catch {
        throw "创建回复失败: $_"
    }
}

<#
.SYNOPSIS
    为评论点赞

.DESCRIPTION
    为指定评论添加点赞

.PARAMETER CommentId
    评论 ID

.PARAMETER UserId
    用户 ID

.EXAMPLE
    Add-CommentLike -CommentId 123 -UserId 456
#>
function Add-CommentLike {
    param(
        [Parameter(Mandatory = $true)]
        [long]$CommentId,
        
        [Parameter(Mandatory = $true)]
        [long]$UserId
    )
    
    $headers = @{
        "X-App-Id" = $AppId
        "X-User-Id" = $UserId
    }
    
    try {
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/comments/$CommentId/like" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
    }
    catch {
        throw "评论点赞失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          评论数据生成脚本                          ║" "Cyan"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Cyan"

# 步骤 1: 验证服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证服务可用性 ===" "Cyan"

# 验证 ID Generator 服务
Write-ColorOutput "  检查 ID Generator 服务..." "Gray"
$idGenStatus = Test-IdGeneratorService -IdGeneratorUrl $IdGeneratorUrl
if ($idGenStatus.Available) {
    Write-ColorOutput "✓ ID Generator 服务正常运行" "Green"
    Write-ColorOutput "  测试 ID: $($idGenStatus.TestId)" "Gray"
}
else {
    Write-ColorOutput "✗ ID Generator 服务不可用" "Red"
    Write-ColorOutput "  错误: $($idGenStatus.Message)" "Red"
    Write-ColorOutput "  请确保服务已启动: $IdGeneratorUrl" "Yellow"
    exit 1
}

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

# 步骤 2: 获取已发布文章
Write-ColorOutput "`n=== 步骤 2: 获取已发布文章 ===" "Cyan"

try {
    $posts = Get-PublishedPosts
}
catch {
    Write-ColorOutput "✗ 获取已发布文章失败: $_" "Red"
    Write-ColorOutput "  请确保已执行文章数据生成脚本" "Yellow"
    exit 1
}

# 步骤 3: 获取测试用户
Write-ColorOutput "`n=== 步骤 3: 获取测试用户 ===" "Cyan"

try {
    $users = Get-TestUsers
}
catch {
    Write-ColorOutput "✗ 获取测试用户失败: $_" "Red"
    Write-ColorOutput "  请确保已执行用户数据生成脚本" "Yellow"
    exit 1
}

# 步骤 4: 生成一级评论
Write-ColorOutput "`n=== 步骤 4: 生成一级评论 ===" "Cyan"

$allComments = @()
$totalTopLevelComments = 0
$failedComments = 0

foreach ($post in $posts) {
    $commentCount = Get-Random -Minimum $MinCommentsPerPost -Maximum ($MaxCommentsPerPost + 1)
    $totalTopLevelComments += $commentCount
    
    $postComments = @()
    
    for ($i = 0; $i -lt $commentCount; $i++) {
        try {
            $author = $users | Get-Random
            $content = $commentTemplates | Get-Random
            
            if (-not $DryRun) {
                $commentId = New-TopLevelComment -PostId $post.id -AuthorId $author.id -Content $content
                
                $postComments += @{
                    id = $commentId
                    postId = $post.id
                    authorId = $author.id
                    content = $content
                    isTopLevel = $true
                }
            }
            else {
                $postComments += @{
                    id = 0
                    postId = $post.id
                    authorId = $author.id
                    content = $content
                    isTopLevel = $true
                }
            }
        }
        catch {
            Write-ColorOutput "`n⚠ 创建评论失败: $_" "Yellow"
            $failedComments++
        }
    }
    
    $allComments += $postComments
    
    Show-Progress -Current ($posts.IndexOf($post) + 1) -Total $posts.Count `
        -Message "为文章生成评论 (文章 $($post.id): $commentCount 条评论)"
}

Write-ColorOutput "`n✓ 一级评论生成完成" "Green"
Write-ColorOutput "  成功: $($totalTopLevelComments - $failedComments)" "Green"
if ($failedComments -gt 0) {
    Write-ColorOutput "  失败: $failedComments" "Red"
}

# 步骤 5: 生成嵌套回复
Write-ColorOutput "`n=== 步骤 5: 生成嵌套回复 ===" "Cyan"

$topLevelComments = $allComments | Where-Object { $_.isTopLevel -eq $true }
$replyCount = [math]::Floor($topLevelComments.Count * $ReplyRatio)
$totalReplies = 0
$failedReplies = 0

Write-ColorOutput "  将为 $replyCount 条评论生成回复..." "Gray"

# 随机选择要回复的评论
$commentsToReply = $topLevelComments | Get-Random -Count $replyCount

foreach ($comment in $commentsToReply) {
    # 每条评论生成 1-5 条回复
    $replyCountForComment = Get-Random -Minimum 1 -Maximum 6
    $totalReplies += $replyCountForComment
    
    for ($i = 0; $i -lt $replyCountForComment; $i++) {
        try {
            $author = $users | Get-Random
            $content = $replyTemplates | Get-Random
            
            if (-not $DryRun) {
                $replyId = New-ReplyComment -PostId $comment.postId `
                    -RootId $comment.id `
                    -ReplyToCommentId $comment.id `
                    -AuthorId $author.id `
                    -Content $content
                
                $allComments += @{
                    id = $replyId
                    postId = $comment.postId
                    rootId = $comment.id
                    replyToCommentId = $comment.id
                    authorId = $author.id
                    content = $content
                    isTopLevel = $false
                }
            }
            else {
                $allComments += @{
                    id = 0
                    postId = $comment.postId
                    rootId = $comment.id
                    replyToCommentId = $comment.id
                    authorId = $author.id
                    content = $content
                    isTopLevel = $false
                }
            }
        }
        catch {
            Write-ColorOutput "`n⚠ 创建回复失败: $_" "Yellow"
            $failedReplies++
        }
    }
    
    Show-Progress -Current ($commentsToReply.IndexOf($comment) + 1) -Total $commentsToReply.Count `
        -Message "生成回复 (评论 $($comment.id): $replyCountForComment 条回复)"
}

Write-ColorOutput "`n✓ 嵌套回复生成完成" "Green"
Write-ColorOutput "  成功: $($totalReplies - $failedReplies)" "Green"
if ($failedReplies -gt 0) {
    Write-ColorOutput "  失败: $failedReplies" "Red"
}

# 步骤 6: 生成评论点赞
Write-ColorOutput "`n=== 步骤 6: 生成评论点赞 ===" "Cyan"

$totalLikes = 0
$failedLikes = 0

# 为 30% 的评论生成点赞
$commentsToLike = $allComments | Where-Object { $_.id -ne 0 } | Get-Random -Count ([math]::Floor($allComments.Count * 0.3))

Write-ColorOutput "  将为 $($commentsToLike.Count) 条评论生成点赞..." "Gray"

foreach ($comment in $commentsToLike) {
    # 每条评论生成 1-20 个点赞
    $likeCount = Get-Random -Minimum 1 -Maximum 21
    $totalLikes += $likeCount
    
    # 随机选择用户点赞
    $likingUsers = $users | Get-Random -Count ([math]::Min($likeCount, $users.Count))
    
    foreach ($user in $likingUsers) {
        try {
            if (-not $DryRun) {
                Add-CommentLike -CommentId $comment.id -UserId $user.id
            }
        }
        catch {
            $failedLikes++
        }
    }
    
    Show-Progress -Current ($commentsToLike.IndexOf($comment) + 1) -Total $commentsToLike.Count `
        -Message "为评论生成点赞 (评论 $($comment.id): $likeCount 个点赞)"
}

Write-ColorOutput "`n✓ 评论点赞生成完成" "Green"
Write-ColorOutput "  成功: $($totalLikes - $failedLikes)" "Green"
if ($failedLikes -gt 0) {
    Write-ColorOutput "  失败: $failedLikes" "Red"
}

# 步骤 7: 显示结果
Write-ColorOutput "`n=== 步骤 7: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 评论数据生成完成" "Green"

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  文章数: $($posts.Count)" "Gray"
Write-ColorOutput "  一级评论: $($totalTopLevelComments - $failedComments)" "Gray"
Write-ColorOutput "  回复评论: $($totalReplies - $failedReplies)" "Gray"
Write-ColorOutput "  总评论数: $(($totalTopLevelComments - $failedComments) + ($totalReplies - $failedReplies))" "Gray"
Write-ColorOutput "  评论点赞: $($totalLikes - $failedLikes)" "Gray"

if ($failedComments -gt 0 -or $failedReplies -gt 0 -or $failedLikes -gt 0) {
    Write-ColorOutput "`n  失败统计:" "Red"
    if ($failedComments -gt 0) {
        Write-ColorOutput "    一级评论失败: $failedComments" "Red"
    }
    if ($failedReplies -gt 0) {
        Write-ColorOutput "    回复失败: $failedReplies" "Red"
    }
    if ($failedLikes -gt 0) {
        Write-ColorOutput "    点赞失败: $failedLikes" "Red"
    }
}

if ($DryRun) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          Dry Run 模式 - 未实际创建数据            ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}
elseif ($failedComments -eq 0 -and $failedReplies -eq 0 -and $failedLikes -eq 0) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          评论数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          评论数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}


# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试评论数据

.DESCRIPTION
    此脚本为已发布文章生成评论数据，包括一级评论、嵌套回复和评论点赞。
    评论数据通过 API 创建，确保符合业务逻辑并正确维护统计数据。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinCommentsPerPost
    每篇文章最少评论数，默认为 5

.PARAMETER MaxCommentsPerPost
    每篇文章最多评论数，默认为 30

.PARAMETER ReplyRatio
    回复比例（0-1），默认为 0.3 (30%)

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-Comments.ps1
    使用默认配置生成评论数据

.EXAMPLE
    .\Generate-Comments.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-Comments.ps1 -MinCommentsPerPost 10 -MaxCommentsPerPost 50
    自定义每篇文章的评论数量范围

.EXAMPLE
    .\Generate-Comments.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-id-generator 服务已启动（端口 8088）
    2. ZhiCore-gateway 服务已启动（端口 8000）
    3. 已执行用户数据生成脚本
    4. 已执行文章数据生成脚本
    5. 网络连接正常

    生成数据：
    - 为每篇已发布文章生成 5-30 条一级评论
    - 为约 30% 的评论生成 1-5 条回复
    - 为约 30% 的评论生成 1-20 个点赞
    - 自动创建 comment_stats 记录
    - 自动更新 post_stats 的 comment_count
    - 自动更新 comment_stats 的 like_count 和 reply_count

    验证需求：
    - Requirements 6.1: 为每篇已发布文章生成 5-30 条评论
    - Requirements 6.2: 确保 post_id 和 author_id 都是有效的 ID
    - Requirements 6.3: 为至少 30% 的评论生成 1-5 条回复
    - Requirements 6.4: 正确设置 parent_id、root_id 和 reply_to_user_id
    - Requirements 6.5: 确保 parent_id 指向有效的评论
    - Requirements 6.6: 为每条评论创建 comment_stats 记录
    - Requirements 6.7: 为部分评论生成点赞记录
    - Requirements 6.8: 更新 comment_stats 表的 like_count
    - Requirements 6.9: 更新 post_stats 表的 comment_count
#>
