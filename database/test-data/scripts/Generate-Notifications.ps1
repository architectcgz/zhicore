# =====================================================
# Generate Notifications Script
# 生成通知数据脚本
# 
# 说明：此脚本用于生成测试通知数据
# 功能：
# 1. 为每个用户生成不同类型的通知
# 2. 确保 actor_id 和 target_id 有效
# 3. 设置部分通知为已读
# 
# Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [int]$MinNotificationsPerUser = 10,
    [int]$MaxNotificationsPerUser = 50,
    [double]$ReadRatio = 0.5,
    [switch]$DryRun
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 导入模块
$modulePath = Join-Path $PSScriptRoot "modules"
Import-Module (Join-Path $modulePath "IdGenerator.psm1") -Force
Import-Module (Join-Path $modulePath "ApiHelper.psm1") -Force

# 如果提供了配置文件路径，则加载配置
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    Write-ColorOutput "`n=== 加载配置文件 ===" "Cyan"
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    $IdGeneratorUrl = $config.idGeneratorUrl
    $MinNotificationsPerUser = $config.notifications.perUser.min
    $MaxNotificationsPerUser = $config.notifications.perUser.max
    $ReadRatio = $config.notifications.readRatio
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  每用户通知数: $MinNotificationsPerUser-$MaxNotificationsPerUser" "Gray"
    Write-ColorOutput "  已读比例: $($ReadRatio * 100)%" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $IdGeneratorUrl = $config.idGeneratorUrl
        $MinNotificationsPerUser = $config.notifications.perUser.min
        $MaxNotificationsPerUser = $config.notifications.perUser.max
        $ReadRatio = $config.notifications.readRatio
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 通知类型枚举
$NotificationTypes = @{
    LIKE = 0
    COMMENT = 1
    REPLY = 2
    FOLLOW = 3
    SYSTEM = 4
}

# 通知内容模板
$NotificationTemplates = @{
    LIKE_POST = "赞了你的文章"
    LIKE_COMMENT = "赞了你的评论"
    COMMENT = "评论了你的文章"
    REPLY = "回复了你的评论"
    FOLLOW = "关注了你"
    SYSTEM = @(
        "欢迎使用博客系统！",
        "你的文章已通过审核",
        "系统将于今晚进行维护",
        "恭喜你获得优秀作者称号！",
        "你的评论收到了很多赞",
        "系统功能更新通知",
        "你的文章被推荐到首页",
        "账号安全提醒",
        "新功能上线通知",
        "活动邀请通知"
    )
}

<#
.SYNOPSIS
    从数据库获取测试用户

.DESCRIPTION
    使用 psql 命令查询所有测试用户的 ID

.EXAMPLE
    Get-TestUsers
#>
function Get-TestUsers {
    Write-ColorOutput "  查询测试用户..." "Gray"
    
    try {
        $query = "SELECT id, username FROM users WHERE username LIKE 'test_%' ORDER BY id"
        $output = docker exec -i blog-postgres psql -U postgres -d blog_user -t -A -F "," -c $query 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            throw "SQL 查询失败: $output"
        }
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    username = $values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($results.Count) 个测试用户" "Green"
            return $results
        }
        else {
            throw "未找到测试用户"
        }
    }
    catch {
        throw "获取测试用户失败: $_"
    }
}

<#
.SYNOPSIS
    从数据库获取已发布文章

.DESCRIPTION
    使用 psql 命令查询所有已发布的文章

.EXAMPLE
    Get-PublishedPosts
#>
function Get-PublishedPosts {
    Write-ColorOutput "  查询已发布文章..." "Gray"
    
    try {
        $query = "SELECT id, owner_id FROM posts WHERE status = 1 ORDER BY id"
        $output = docker exec -i blog-postgres psql -U postgres -d blog_post -t -A -F "," -c $query 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            throw "SQL 查询失败: $output"
        }
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    owner_id = [long]$values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($results.Count) 篇已发布文章" "Green"
            return $results
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
    从数据库获取评论

.DESCRIPTION
    使用 psql 命令查询所有评论

.EXAMPLE
    Get-Comments
#>
function Get-Comments {
    Write-ColorOutput "  查询评论..." "Gray"
    
    try {
        $query = "SELECT id, post_id, author_id FROM comments WHERE status = 0 ORDER BY id"
        $output = docker exec -i blog-postgres psql -U postgres -d blog_comment -t -A -F "," -c $query 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            Write-ColorOutput "⚠ 查询评论失败，将跳过评论相关通知" "Yellow"
            return @()
        }
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 3) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    post_id = [long]$values[1]
                    author_id = [long]$values[2]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($results.Count) 条评论" "Green"
        }
        else {
            Write-ColorOutput "⚠ 未找到评论，将跳过评论相关通知" "Yellow"
        }
        
        return $results
    }
    catch {
        Write-ColorOutput "⚠ 获取评论失败: $_" "Yellow"
        return @()
    }
}

<#
.SYNOPSIS
    从数据库获取关注关系

.DESCRIPTION
    使用 psql 命令查询所有关注关系

.EXAMPLE
    Get-UserFollows
#>
function Get-UserFollows {
    Write-ColorOutput "  查询关注关系..." "Gray"
    
    try {
        $query = "SELECT follower_id, following_id FROM user_follows ORDER BY created_at"
        $output = docker exec -i blog-postgres psql -U postgres -d blog_user -t -A -F "," -c $query 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            Write-ColorOutput "⚠ 查询关注关系失败，将跳过关注通知" "Yellow"
            return @()
        }
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    follower_id = [long]$values[0]
                    following_id = [long]$values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($results.Count) 条关注关系" "Green"
        }
        else {
            Write-ColorOutput "⚠ 未找到关注关系，将跳过关注通知" "Yellow"
        }
        
        return $results
    }
    catch {
        Write-ColorOutput "⚠ 获取关注关系失败: $_" "Yellow"
        return @()
    }
}

<#
.SYNOPSIS
    生成通知数据

.DESCRIPTION
    为指定用户生成各种类型的通知

.PARAMETER User
    用户对象

.PARAMETER Users
    所有用户列表

.PARAMETER Posts
    文章列表

.PARAMETER Comments
    评论列表

.PARAMETER Follows
    关注关系列表

.PARAMETER NotificationCount
    要生成的通知数量

.EXAMPLE
    New-UserNotifications -User $user -Users $users -Posts $posts -Comments $comments -Follows $follows -NotificationCount 30
#>
function New-UserNotifications {
    param(
        [Parameter(Mandatory = $true)]
        [object]$User,
        
        [Parameter(Mandatory = $true)]
        [array]$Users,
        
        [Parameter(Mandatory = $true)]
        [array]$Posts,
        
        [array]$Comments = @(),
        
        [array]$Follows = @(),
        
        [Parameter(Mandatory = $true)]
        [int]$NotificationCount
    )
    
    $notifications = @()
    
    # 获取用户的文章
    $userPosts = $Posts | Where-Object { $_.owner_id -eq $User.id }
    
    # 获取用户的评论
    $userComments = $Comments | Where-Object { $_.author_id -eq $User.id }
    
    # 获取关注该用户的人
    $followers = $Follows | Where-Object { $_.following_id -eq $User.id }
    
    # 生成通知
    for ($i = 0; $i -lt $NotificationCount; $i++) {
        # 随机选择通知类型
        $typeValue = Get-Random -Minimum 0 -Maximum 5
        
        $notification = $null
        
        switch ($typeValue) {
            0 {
                # LIKE 通知
                if ($userPosts.Count -gt 0) {
                    $post = $userPosts | Get-Random
                    $actor = $Users | Where-Object { $_.id -ne $User.id } | Get-Random
                    
                    if ($actor) {
                        $notification = @{
                            type = $NotificationTypes.LIKE
                            actor_id = $actor.id
                            target_type = "post"
                            target_id = $post.id
                            content = $NotificationTemplates.LIKE_POST
                        }
                    }
                }
                elseif ($userComments.Count -gt 0) {
                    $comment = $userComments | Get-Random
                    $actor = $Users | Where-Object { $_.id -ne $User.id } | Get-Random
                    
                    if ($actor) {
                        $notification = @{
                            type = $NotificationTypes.LIKE
                            actor_id = $actor.id
                            target_type = "comment"
                            target_id = $comment.id
                            content = $NotificationTemplates.LIKE_COMMENT
                        }
                    }
                }
            }
            1 {
                # COMMENT 通知
                if ($userPosts.Count -gt 0) {
                    $post = $userPosts | Get-Random
                    $actor = $Users | Where-Object { $_.id -ne $User.id } | Get-Random
                    
                    if ($actor) {
                        $notification = @{
                            type = $NotificationTypes.COMMENT
                            actor_id = $actor.id
                            target_type = "post"
                            target_id = $post.id
                            content = $NotificationTemplates.COMMENT
                        }
                    }
                }
            }
            2 {
                # REPLY 通知
                if ($userComments.Count -gt 0) {
                    $comment = $userComments | Get-Random
                    $actor = $Users | Where-Object { $_.id -ne $User.id } | Get-Random
                    
                    if ($actor) {
                        $notification = @{
                            type = $NotificationTypes.REPLY
                            actor_id = $actor.id
                            target_type = "comment"
                            target_id = $comment.id
                            content = $NotificationTemplates.REPLY
                        }
                    }
                }
            }
            3 {
                # FOLLOW 通知
                if ($followers.Count -gt 0) {
                    $follower = $followers | Get-Random
                    $actor = $Users | Where-Object { $_.id -eq $follower.follower_id } | Select-Object -First 1
                    
                    if ($actor) {
                        $notification = @{
                            type = $NotificationTypes.FOLLOW
                            actor_id = $actor.id
                            target_type = $null
                            target_id = $null
                            content = $NotificationTemplates.FOLLOW
                        }
                    }
                }
            }
            4 {
                # SYSTEM 通知
                $notification = @{
                    type = $NotificationTypes.SYSTEM
                    actor_id = $null
                    target_type = $null
                    target_id = $null
                    content = $NotificationTemplates.SYSTEM | Get-Random
                }
            }
        }
        
        if ($notification) {
            # 生成通知 ID
            $notificationId = Get-NextId -IdGeneratorUrl $IdGeneratorUrl
            
            # 随机决定是否已读
            $isRead = (Get-Random -Minimum 0.0 -Maximum 1.0) -lt $ReadRatio
            
            $notification.id = $notificationId
            $notification.recipient_id = $User.id
            $notification.is_read = $isRead
            
            $notifications += $notification
        }
    }
    
    return $notifications
}

<#
.SYNOPSIS
    生成 SQL 插入语句

.DESCRIPTION
    将通知数据转换为 SQL INSERT 语句

.PARAMETER Notifications
    通知列表

.EXAMPLE
    New-InsertStatements -Notifications $notifications
#>
function New-InsertStatements {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Notifications
    )
    
    $statements = @()
    
    foreach ($notification in $Notifications) {
        $actorId = if ($notification.actor_id) { $notification.actor_id } else { "NULL" }
        $targetType = if ($notification.target_type) { "'$($notification.target_type)'" } else { "NULL" }
        $targetId = if ($notification.target_id) { $notification.target_id } else { "NULL" }
        $readAt = if ($notification.is_read) { "CURRENT_TIMESTAMP" } else { "NULL" }
        $isRead = if ($notification.is_read) { "TRUE" } else { "FALSE" }
        
        $statement = @"
INSERT INTO notifications (id, recipient_id, type, actor_id, target_type, target_id, content, is_read, read_at, created_at)
VALUES ($($notification.id), $($notification.recipient_id), $($notification.type), $actorId, $targetType, $targetId, '$($notification.content)', $isRead, $readAt, CURRENT_TIMESTAMP);
"@
        
        $statements += $statement
    }
    
    return $statements
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          通知数据生成脚本                          ║" "Cyan"
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

# 步骤 2: 获取基础数据
Write-ColorOutput "`n=== 步骤 2: 获取基础数据 ===" "Cyan"

try {
    $users = Get-TestUsers
    $posts = Get-PublishedPosts
    $comments = Get-Comments
    $follows = Get-UserFollows
}
catch {
    Write-ColorOutput "✗ 获取基础数据失败: $_" "Red"
    exit 1
}

# 步骤 3: 生成通知数据
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "通知数据已生成但未插入数据库" "Yellow"
    
    $sampleUser = $users | Select-Object -First 1
    $sampleCount = Get-Random -Minimum $MinNotificationsPerUser -Maximum ($MaxNotificationsPerUser + 1)
    $sampleNotifications = New-UserNotifications -User $sampleUser -Users $users -Posts $posts -Comments $comments -Follows $follows -NotificationCount $sampleCount
    
    Write-ColorOutput "`n示例通知（用户: $($sampleUser.username)，共 $($sampleNotifications.Count) 条）:" "Yellow"
    
    $previewCount = [math]::Min(5, $sampleNotifications.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $notif = $sampleNotifications[$i]
        $typeName = ($NotificationTypes.GetEnumerator() | Where-Object { $_.Value -eq $notif.type }).Name
        Write-ColorOutput "  $($i + 1). 类型: $typeName, 内容: $($notif.content), 已读: $($notif.is_read)" "Gray"
    }
    
    if ($sampleNotifications.Count -gt 5) {
        Write-ColorOutput "  ... 还有 $($sampleNotifications.Count - 5) 条通知" "Gray"
    }
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 3: 生成通知数据 ===" "Cyan"

$allNotifications = @()
$totalNotifications = 0

for ($i = 0; $i -lt $users.Count; $i++) {
    $user = $users[$i]
    
    $notificationCount = Get-Random -Minimum $MinNotificationsPerUser -Maximum ($MaxNotificationsPerUser + 1)
    
    Show-Progress -Current ($i + 1) -Total $users.Count `
        -Message "生成通知: $($user.username) ($notificationCount 条)"
    
    try {
        $notifications = New-UserNotifications -User $user -Users $users -Posts $posts -Comments $comments -Follows $follows -NotificationCount $notificationCount
        $allNotifications += $notifications
        $totalNotifications += $notifications.Count
    }
    catch {
        Write-ColorOutput "`n⚠ 生成通知失败: $($user.username)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
    }
}

# 步骤 4: 生成 SQL 文件
Write-ColorOutput "`n=== 步骤 4: 生成 SQL 文件 ===" "Cyan"

$sqlOutputPath = Join-Path $PSScriptRoot "..\sql\generated-notifications.sql"

try {
    $sqlStatements = New-InsertStatements -Notifications $allNotifications
    
    $sqlContent = @"
-- =====================================================
-- Generated Notifications Data
-- 生成的通知数据
-- 
-- 生成时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
-- 总通知数: $totalNotifications
-- =====================================================

\c blog_notification;

BEGIN;

$($sqlStatements -join "`n`n")

COMMIT;

-- =====================================================
-- 通知数据生成完成
-- =====================================================
"@
    
    $sqlContent | Out-File -FilePath $sqlOutputPath -Encoding UTF8
    
    Write-ColorOutput "✓ SQL 文件生成成功" "Green"
    Write-ColorOutput "  文件路径: $sqlOutputPath" "Gray"
}
catch {
    Write-ColorOutput "✗ 生成 SQL 文件失败: $_" "Red"
    exit 1
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 通知数据生成完成" "Green"
Write-ColorOutput "  用户数: $($users.Count)" "Green"
Write-ColorOutput "  总通知数: $totalNotifications" "Green"
Write-ColorOutput "  平均每用户通知数: $([math]::Round($totalNotifications / $users.Count, 1))" "Gray"

# 统计各类型通知数量
$typeStats = @{}
foreach ($notif in $allNotifications) {
    $typeName = ($NotificationTypes.GetEnumerator() | Where-Object { $_.Value -eq $notif.type }).Name
    if (-not $typeStats.ContainsKey($typeName)) {
        $typeStats[$typeName] = 0
    }
    $typeStats[$typeName]++
}

Write-ColorOutput "`n=== 通知类型统计 ===" "Cyan"
foreach ($type in $typeStats.Keys | Sort-Object) {
    $count = $typeStats[$type]
    $percentage = [math]::Round(($count / $totalNotifications) * 100, 1)
    Write-ColorOutput "  $type : $count ($percentage%)" "Gray"
}

# 统计已读/未读
$readCount = ($allNotifications | Where-Object { $_.is_read }).Count
$unreadCount = $totalNotifications - $readCount
$readPercentage = [math]::Round(($readCount / $totalNotifications) * 100, 1)

Write-ColorOutput "`n=== 已读状态统计 ===" "Cyan"
Write-ColorOutput "  已读: $readCount ($readPercentage%)" "Gray"
Write-ColorOutput "  未读: $unreadCount ($([math]::Round(100 - $readPercentage, 1))%)" "Gray"

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
Write-ColorOutput "║          通知数据生成成功！                        ║" "Green"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"

Write-ColorOutput "`n下一步：执行 SQL 文件插入数据" "Yellow"
Write-ColorOutput "  psql -h localhost -p 5432 -U postgres -f `"$sqlOutputPath`"" "Gray"

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试通知数据

.DESCRIPTION
    此脚本为每个测试用户生成各种类型的通知数据，包括点赞、评论、回复、关注和系统通知。
    通知数据会生成 SQL 文件，需要手动执行 SQL 文件插入数据库。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER MinNotificationsPerUser
    每个用户最少通知数，默认为 10

.PARAMETER MaxNotificationsPerUser
    每个用户最多通知数，默认为 50

.PARAMETER ReadRatio
    已读通知比例，默认为 0.5（50%）

.PARAMETER DryRun
    仅生成数据但不创建 SQL 文件，用于预览

.EXAMPLE
    .\Generate-Notifications.ps1
    使用默认配置生成通知数据

.EXAMPLE
    .\Generate-Notifications.ps1 -DryRun
    预览模式，生成数据但不创建 SQL 文件

.EXAMPLE
    .\Generate-Notifications.ps1 -MinNotificationsPerUser 20 -MaxNotificationsPerUser 100
    为每个用户生成 20-100 条通知

.EXAMPLE
    .\Generate-Notifications.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. blog-id-generator 服务已启动（端口 8088）
    2. PostgreSQL 数据库已启动（端口 5432）
    3. 已执行用户数据生成脚本
    4. 已执行文章数据生成脚本
    5. 已执行评论数据生成脚本（可选）
    6. 已执行关注关系生成脚本（可选）

    生成数据：
    - 每个用户 10-50 条通知（默认）
    - 包含 5 种类型：点赞、评论、回复、关注、系统
    - 50% 的通知标记为已读（默认）
    - 确保 actor_id 和 target_id 有效
    - 生成 SQL 文件需要手动执行

    验证需求：
    - Requirements 8.1: 为每个用户生成 10-50 条通知
    - Requirements 8.2: 生成不同类型的通知（点赞、评论、关注等）
    - Requirements 8.3: 确保 recipient_id 是有效的用户 ID
    - Requirements 8.4: 确保 actor_id 是有效的用户 ID（如果适用）
    - Requirements 8.5: 确保 target_id 指向有效的目标对象
    - Requirements 8.6: 为至少 50% 的通知设置 is_read 为 true
    - Requirements 8.7: 当通知已读时，设置 read_at 时间戳
#>
