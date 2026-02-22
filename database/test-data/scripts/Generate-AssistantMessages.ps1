# =====================================================
# Generate Assistant Messages Script
# 生成小助手消息脚本
# 
# 说明：此脚本用于生成测试小助手消息数据
# 功能：
# 1. 为每个用户生成小助手消息
# 2. 生成不同类型的消息
# 3. 设置部分消息为已读
# 
# Requirements: 9.4, 9.5, 9.6
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [int]$MinMessagesPerUser = 3,
    [int]$MaxMessagesPerUser = 10,
    [double]$ReadRatio = 0.6,
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
    # 注意：配置文件中没有小助手消息的配置，使用默认值
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $IdGeneratorUrl = $config.idGeneratorUrl
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

Write-ColorOutput "  每用户消息数: $MinMessagesPerUser-$MaxMessagesPerUser" "Gray"
Write-ColorOutput "  已读比例: $($ReadRatio * 100)%" "Gray"

# 消息类型和内容模板
$MessageTemplates = @{
    WELCOME = @{
        type = "welcome"
        messages = @(
            "欢迎加入博客平台！我是你的小助手，有任何问题都可以问我哦~",
            "你好！欢迎来到我们的社区。开始你的第一篇文章吧！",
            "欢迎！让我们一起开启精彩的写作之旅。"
        )
    }
    TIP = @{
        type = "tip"
        messages = @(
            "小贴士：使用 Markdown 语法可以让你的文章更加美观哦！",
            "提示：定期发布文章可以获得更多关注者。",
            "建议：为文章添加合适的标签，让更多人发现你的内容。",
            "技巧：使用代码块功能可以分享你的代码片段。",
            "提醒：记得给喜欢的文章点赞和收藏哦！",
            "小窍门：关注感兴趣的作者，第一时间看到他们的新文章。",
            "建议：完善你的个人资料，让其他用户更了解你。"
        )
        link = "/help/markdown"
    }
    ACHIEVEMENT = @{
        type = "achievement"
        messages = @(
            "恭喜你！你的文章获得了第一个点赞！",
            "太棒了！你已经发布了 5 篇文章！",
            "厉害！你的文章被收藏了 10 次！",
            "恭喜！你获得了 10 个关注者！",
            "成就达成：你的评论获得了 20 个赞！",
            "里程碑：你的文章总浏览量突破 1000 次！"
        )
    }
    REMINDER = @{
        type = "reminder"
        messages = @(
            "你有一段时间没有发布新文章了，分享一下最近的想法吧！",
            "提醒：你有 3 条未读评论，快去看看吧！",
            "别忘了回复你的粉丝留言哦~",
            "你关注的作者发布了新文章，快去看看吧！",
            "提醒：你的草稿箱里还有 2 篇未完成的文章。"
        )
    }
    UPDATE = @{
        type = "update"
        messages = @(
            "系统更新：我们优化了编辑器性能，写作更流畅了！",
            "新功能：现在可以在文章中插入视频了！",
            "更新通知：我们改进了搜索功能，查找内容更方便。",
            "功能升级：评论支持表情符号啦！",
            "新特性：现在可以设置文章的发布时间了。"
        )
        link = "/updates"
    }
    ACTIVITY = @{
        type = "activity"
        messages = @(
            "活动通知：本月写作挑战开始了，参与即有机会获得奖励！",
            "征文活动：「我的编程之路」主题征文进行中，快来投稿吧！",
            "社区活动：周末线上分享会，欢迎参加！",
            "限时活动：邀请好友注册，双方都可获得积分奖励！"
        )
        link = "/activities"
    }
    SECURITY = @{
        type = "security"
        messages = @(
            "安全提醒：检测到异地登录，如非本人操作请及时修改密码。",
            "账号安全：建议开启两步验证，保护你的账号安全。",
            "提醒：你的密码已经 90 天未修改，建议定期更换密码。"
        )
    }
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
        $output = docker exec -i ZhiCore-postgres psql -U postgres -d ZhiCore_user -t -A -F "," -c $query 2>&1
        
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
    生成用户的小助手消息

.DESCRIPTION
    为指定用户生成各种类型的小助手消息

.PARAMETER User
    用户对象

.PARAMETER MessageCount
    要生成的消息数量

.EXAMPLE
    New-UserAssistantMessages -User $user -MessageCount 5
#>
function New-UserAssistantMessages {
    param(
        [Parameter(Mandatory = $true)]
        [object]$User,
        
        [Parameter(Mandatory = $true)]
        [int]$MessageCount
    )
    
    $messages = @()
    
    # 获取所有消息类型
    $messageTypes = $MessageTemplates.Keys | ForEach-Object { $_ }
    
    # 确保第一条消息是欢迎消息
    if ($MessageCount -gt 0) {
        $welcomeTemplate = $MessageTemplates.WELCOME
        $welcomeContent = $welcomeTemplate.messages | Get-Random
        
        $messageId = Get-NextId -IdGeneratorUrl $IdGeneratorUrl
        
        $message = @{
            id = $messageId
            user_id = $User.id
            content = $welcomeContent
            type = $welcomeTemplate.type
            is_read = $true  # 欢迎消息默认已读
            link = $null
        }
        
        $messages += $message
    }
    
    # 生成其他消息
    for ($i = 1; $i -lt $MessageCount; $i++) {
        # 随机选择消息类型（排除 WELCOME）
        $typeKey = $messageTypes | Where-Object { $_ -ne "WELCOME" } | Get-Random
        $template = $MessageTemplates[$typeKey]
        
        # 随机选择消息内容
        $content = $template.messages | Get-Random
        
        # 生成消息 ID
        $messageId = Get-NextId -IdGeneratorUrl $IdGeneratorUrl
        
        # 随机决定是否已读
        $isRead = (Get-Random -Minimum 0.0 -Maximum 1.0) -lt $ReadRatio
        
        $message = @{
            id = $messageId
            user_id = $User.id
            content = $content
            type = $template.type
            is_read = $isRead
            link = $template.link
        }
        
        $messages += $message
    }
    
    return $messages
}

<#
.SYNOPSIS
    生成 SQL 插入语句

.DESCRIPTION
    将小助手消息数据转换为 SQL INSERT 语句

.PARAMETER Messages
    消息列表

.EXAMPLE
    New-InsertStatements -Messages $messages
#>
function New-InsertStatements {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Messages
    )
    
    $statements = @()
    
    foreach ($message in $Messages) {
        $link = if ($message.link) { "'$($message.link)'" } else { "NULL" }
        $readAt = if ($message.is_read) { "CURRENT_TIMESTAMP" } else { "NULL" }
        $isRead = if ($message.is_read) { "TRUE" } else { "FALSE" }
        
        # 转义单引号
        $content = $message.content -replace "'", "''"
        
        $statement = @"
INSERT INTO assistant_messages (id, user_id, content, type, is_read, link, read_at, created_at)
VALUES ($($message.id), $($message.user_id), '$content', '$($message.type)', $isRead, $link, $readAt, CURRENT_TIMESTAMP);
"@
        
        $statements += $statement
    }
    
    return $statements
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          小助手消息数据生成脚本                    ║" "Cyan"
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

# 步骤 2: 获取测试用户
Write-ColorOutput "`n=== 步骤 2: 获取测试用户 ===" "Cyan"

try {
    $users = Get-TestUsers
}
catch {
    Write-ColorOutput "✗ 获取测试用户失败: $_" "Red"
    exit 1
}

# 步骤 3: 生成小助手消息数据
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "小助手消息数据已生成但未插入数据库" "Yellow"
    
    $sampleUser = $users | Select-Object -First 1
    $sampleCount = Get-Random -Minimum $MinMessagesPerUser -Maximum ($MaxMessagesPerUser + 1)
    $sampleMessages = New-UserAssistantMessages -User $sampleUser -MessageCount $sampleCount
    
    Write-ColorOutput "`n示例消息（用户: $($sampleUser.username)，共 $($sampleMessages.Count) 条）:" "Yellow"
    
    foreach ($message in $sampleMessages) {
        $readText = if ($message.is_read) { "已读" } else { "未读" }
        Write-ColorOutput "  - [$($message.type)] $($message.content)" "Gray"
        Write-ColorOutput "    状态: $readText" "DarkGray"
    }
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 3: 生成小助手消息数据 ===" "Cyan"

$allMessages = @()
$totalMessages = 0

for ($i = 0; $i -lt $users.Count; $i++) {
    $user = $users[$i]
    
    $messageCount = Get-Random -Minimum $MinMessagesPerUser -Maximum ($MaxMessagesPerUser + 1)
    
    Show-Progress -Current ($i + 1) -Total $users.Count `
        -Message "生成消息: $($user.username) ($messageCount 条)"
    
    try {
        $messages = New-UserAssistantMessages -User $user -MessageCount $messageCount
        $allMessages += $messages
        $totalMessages += $messages.Count
    }
    catch {
        Write-ColorOutput "`n⚠ 生成消息失败: $($user.username)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
    }
}

# 步骤 4: 生成 SQL 文件
Write-ColorOutput "`n=== 步骤 4: 生成 SQL 文件 ===" "Cyan"

$sqlOutputPath = Join-Path $PSScriptRoot "..\sql\generated-assistant-messages.sql"

try {
    $sqlStatements = New-InsertStatements -Messages $allMessages
    
    $sqlContent = @"
-- =====================================================
-- Generated Assistant Messages Data
-- 生成的小助手消息数据
-- 
-- 生成时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
-- 总消息数: $totalMessages
-- =====================================================

\c ZhiCore_notification;

BEGIN;

$($sqlStatements -join "`n`n")

COMMIT;

-- =====================================================
-- 小助手消息数据生成完成
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

Write-ColorOutput "✓ 小助手消息数据生成完成" "Green"
Write-ColorOutput "  用户数: $($users.Count)" "Green"
Write-ColorOutput "  总消息数: $totalMessages" "Green"
Write-ColorOutput "  平均每用户消息数: $([math]::Round($totalMessages / $users.Count, 1))" "Gray"

# 统计消息类型
$typeStats = @{}
foreach ($message in $allMessages) {
    $type = $message.type
    if (-not $typeStats.ContainsKey($type)) {
        $typeStats[$type] = 0
    }
    $typeStats[$type]++
}

Write-ColorOutput "`n=== 消息类型统计 ===" "Cyan"
foreach ($type in $typeStats.Keys | Sort-Object) {
    $count = $typeStats[$type]
    $percentage = [math]::Round(($count / $totalMessages) * 100, 1)
    Write-ColorOutput "  $type : $count ($percentage%)" "Gray"
}

# 统计已读/未读
$readCount = ($allMessages | Where-Object { $_.is_read }).Count
$unreadCount = $totalMessages - $readCount
$readPercentage = [math]::Round(($readCount / $totalMessages) * 100, 1)

Write-ColorOutput "`n=== 已读状态统计 ===" "Cyan"
Write-ColorOutput "  已读: $readCount ($readPercentage%)" "Gray"
Write-ColorOutput "  未读: $unreadCount ($([math]::Round(100 - $readPercentage, 1))%)" "Gray"

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
Write-ColorOutput "║          小助手消息数据生成成功！                  ║" "Green"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"

Write-ColorOutput "`n下一步：执行 SQL 文件插入数据" "Yellow"
Write-ColorOutput "  psql -h localhost -p 5432 -U postgres -f `"$sqlOutputPath`"" "Gray"

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试小助手消息数据

.DESCRIPTION
    此脚本为每个测试用户生成小助手消息，包括欢迎消息、提示、成就、提醒等不同类型的消息。
    消息数据会生成 SQL 文件，需要手动执行 SQL 文件插入数据库。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER MinMessagesPerUser
    每个用户最少消息数，默认为 3

.PARAMETER MaxMessagesPerUser
    每个用户最多消息数，默认为 10

.PARAMETER ReadRatio
    已读消息比例，默认为 0.6（60%）

.PARAMETER DryRun
    仅生成数据但不创建 SQL 文件，用于预览

.EXAMPLE
    .\Generate-AssistantMessages.ps1
    使用默认配置生成小助手消息数据

.EXAMPLE
    .\Generate-AssistantMessages.ps1 -DryRun
    预览模式，生成数据但不创建 SQL 文件

.EXAMPLE
    .\Generate-AssistantMessages.ps1 -MinMessagesPerUser 5 -MaxMessagesPerUser 15
    为每个用户生成 5-15 条消息

.EXAMPLE
    .\Generate-AssistantMessages.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-id-generator 服务已启动（端口 8088）
    2. PostgreSQL 数据库已启动（端口 5432）
    3. 已执行用户数据生成脚本

    生成数据：
    - 每个用户 3-10 条消息（默认）
    - 包含 7 种类型：welcome、tip、achievement、reminder、update、activity、security
    - 60% 的消息标记为已读（默认）
    - 第一条消息始终是欢迎消息
    - 生成 SQL 文件需要手动执行

    验证需求：
    - Requirements 9.4: 为每个用户生成 3-10 条小助手消息
    - Requirements 9.5: 生成不同类型的消息
    - Requirements 9.6: 为至少 60% 的小助手消息设置 is_read 为 true
#>
