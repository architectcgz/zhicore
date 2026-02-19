# =====================================================
# Generate Messages Script
# 生成私信数据脚本
# 
# 说明：此脚本用于生成测试私信数据
# 功能：
# 1. 生成私信会话
# 2. 确保参与者 ID 顺序正确
# 3. 为每个会话生成消息
# 4. 确保发送者和接收者是会话参与者
# 5. 设置部分消息为已读
# 6. 更新会话的最后消息信息和未读计数
# 
# Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [string]$AppId = "test-app",
    [int]$ConversationCount = 50,
    [int]$MinMessagesPerConversation = 5,
    [int]$MaxMessagesPerConversation = 20,
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
    $ConversationCount = $config.messages.conversationCount
    $MinMessagesPerConversation = $config.messages.messagesPerConversation.min
    $MaxMessagesPerConversation = $config.messages.messagesPerConversation.max
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  会话数量: $ConversationCount" "Gray"
    Write-ColorOutput "  每个会话消息数: $MinMessagesPerConversation-$MaxMessagesPerConversation" "Gray"
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
        $ConversationCount = $config.messages.conversationCount
        $MinMessagesPerConversation = $config.messages.messagesPerConversation.min
        $MaxMessagesPerConversation = $config.messages.messagesPerConversation.max
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 预定义的消息内容模板
$messageTemplates = @(
    "你好！",
    "最近怎么样？",
    "有空一起讨论一下技术问题吗？",
    "看到你的文章了，写得很不错！",
    "能分享一下你的学习经验吗？",
    "这个问题我也遇到过，可以交流一下",
    "感谢你的帮助！",
    "周末有时间吗？",
    "我有个项目想请教你",
    "你对这个技术栈怎么看？",
    "最近在学什么新技术？",
    "可以加个好友吗？",
    "你的博客更新了吗？",
    "这个方案我觉得可行",
    "我们可以合作一下",
    "有什么好的学习资源推荐吗？",
    "你的代码写得很优雅",
    "能帮我看看这个问题吗？",
    "谢谢你的建议！",
    "期待你的回复"
)

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
    生成会话对

.DESCRIPTION
    生成指定数量的会话对，确保参与者 ID 顺序正确

.PARAMETER Count
    要生成的会话数量

.PARAMETER Users
    用户列表

.EXAMPLE
    New-ConversationPairs -Count 50 -Users $users
#>
function New-ConversationPairs {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Count,
        
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成会话对 ===" "Cyan"
    
    $pairs = @()
    $usedPairs = @{}
    
    for ($i = 0; $i -lt $Count; $i++) {
        $maxAttempts = 100
        $attempt = 0
        $pairFound = $false
        
        while (-not $pairFound -and $attempt -lt $maxAttempts) {
            # 随机选择两个不同的用户
            $user1 = $Users | Get-Random
            $user2 = $Users | Get-Random
            
            # 确保不是同一个用户
            if ($user1.id -eq $user2.id) {
                $attempt++
                continue
            }
            
            # 确保 participant1_id < participant2_id (Requirements 7.3)
            if ($user1.id -gt $user2.id) {
                $temp = $user1
                $user1 = $user2
                $user2 = $temp
            }
            
            # 生成唯一键
            $pairKey = "$($user1.id)-$($user2.id)"
            
            # 检查是否已存在
            if (-not $usedPairs.ContainsKey($pairKey)) {
                $pairs += @{
                    participant1 = $user1
                    participant2 = $user2
                }
                $usedPairs[$pairKey] = $true
                $pairFound = $true
            }
            
            $attempt++
        }
        
        if (-not $pairFound) {
            Write-ColorOutput "⚠ 无法生成更多唯一的会话对，已生成 $($pairs.Count) 个" "Yellow"
            break
        }
    }
    
    Write-ColorOutput "✓ 会话对生成完成" "Green"
    Write-ColorOutput "  生成数量: $($pairs.Count)" "Gray"
    
    return $pairs
}

<#
.SYNOPSIS
    生成消息内容

.DESCRIPTION
    从模板中随机选择消息内容

.EXAMPLE
    New-MessageContent
#>
function New-MessageContent {
    return $messageTemplates | Get-Random
}

<#
.SYNOPSIS
    发送消息

.DESCRIPTION
    调用 API 发送消息，会话会自动创建

.PARAMETER SenderId
    发送者 ID

.PARAMETER ReceiverId
    接收者 ID

.PARAMETER Content
    消息内容

.EXAMPLE
    Send-Message -SenderId 123 -ReceiverId 456 -Content "你好"
#>
function Send-Message {
    param(
        [Parameter(Mandatory = $true)]
        [long]$SenderId,
        
        [Parameter(Mandatory = $true)]
        [long]$ReceiverId,
        
        [Parameter(Mandatory = $true)]
        [string]$Content
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $SenderId
    }
    
    $body = @{
        receiverId = $ReceiverId
        type = "TEXT"
        content = $Content
    }
    
    try {
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/messages" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        return $result
    }
    catch {
        throw "发送消息失败: $_"
    }
}

<#
.SYNOPSIS
    标记消息为已读

.DESCRIPTION
    调用 API 标记会话中的消息为已读

.PARAMETER ConversationId
    会话 ID

.PARAMETER UserId
    用户 ID

.EXAMPLE
    Mark-MessagesAsRead -ConversationId 123 -UserId 456
#>
function Mark-MessagesAsRead {
    param(
        [Parameter(Mandatory = $true)]
        [long]$ConversationId,
        
        [Parameter(Mandatory = $true)]
        [long]$UserId
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $UserId
    }
    
    try {
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/messages/conversation/$ConversationId/read" `
            -Method Post `
            -Headers $headers `
            -Body @{} | Out-Null
    }
    catch {
        throw "标记消息为已读失败: $_"
    }
}

<#
.SYNOPSIS
    获取会话信息

.DESCRIPTION
    根据对方用户 ID 获取会话信息

.PARAMETER UserId
    当前用户 ID

.PARAMETER OtherUserId
    对方用户 ID

.EXAMPLE
    Get-ConversationByUser -UserId 123 -OtherUserId 456
#>
function Get-ConversationByUser {
    param(
        [Parameter(Mandatory = $true)]
        [long]$UserId,
        
        [Parameter(Mandatory = $true)]
        [long]$OtherUserId
    )
    
    $headers = @{
        "X-App-Id" = $AppId
        "X-User-Id" = $UserId
    }
    
    try {
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/conversations/user/$OtherUserId" `
            -Method Get `
            -Headers $headers
        
        return $result
    }
    catch {
        throw "获取会话信息失败: $_"
    }
}

<#
.SYNOPSIS
    为会话生成消息

.DESCRIPTION
    为指定会话生成多条消息，并设置部分消息为已读

.PARAMETER Pair
    会话对（包含两个参与者）

.PARAMETER MessageCount
    要生成的消息数量

.EXAMPLE
    New-ConversationMessages -Pair $pair -MessageCount 10
#>
function New-ConversationMessages {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Pair,
        
        [Parameter(Mandatory = $true)]
        [int]$MessageCount
    )
    
    $participant1 = $Pair.participant1
    $participant2 = $Pair.participant2
    
    $messages = @()
    
    # 生成消息
    for ($i = 0; $i -lt $MessageCount; $i++) {
        # 随机选择发送者和接收者
        if ((Get-Random -Minimum 0 -Maximum 2) -eq 0) {
            $sender = $participant1
            $receiver = $participant2
        }
        else {
            $sender = $participant2
            $receiver = $participant1
        }
        
        $content = New-MessageContent
        
        try {
            $message = Send-Message -SenderId $sender.id -ReceiverId $receiver.id -Content $content
            $messages += @{
                message = $message
                sender = $sender
                receiver = $receiver
            }
            
            # 添加短暂延迟，避免时间戳完全相同
            Start-Sleep -Milliseconds 100
        }
        catch {
            Write-ColorOutput "⚠ 发送消息失败: $_" "Yellow"
        }
    }
    
    return $messages
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          私信数据生成脚本                          ║" "Cyan"
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

# 步骤 3: 生成会话对
try {
    $conversationPairs = New-ConversationPairs -Count $ConversationCount -Users $users
}
catch {
    Write-ColorOutput "✗ 生成会话对失败: $_" "Red"
    exit 1
}

# 步骤 4: 为每个会话生成消息
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "会话对已生成但未创建消息" "Yellow"
    Write-ColorOutput "`n生成的会话对预览（前 10 个）:" "Yellow"
    
    $previewCount = [math]::Min(10, $conversationPairs.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $pair = $conversationPairs[$i]
        Write-ColorOutput "  $($i + 1). 用户 $($pair.participant1.username) <-> 用户 $($pair.participant2.username)" "Gray"
        Write-ColorOutput "     ID: $($pair.participant1.id) <-> $($pair.participant2.id)" "Gray"
    }
    
    if ($conversationPairs.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($conversationPairs.Count - 10) 个会话对" "Gray"
    }
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 生成消息 ===" "Cyan"

$totalMessages = 0
$totalConversations = 0
$failedConversations = @()

for ($i = 0; $i -lt $conversationPairs.Count; $i++) {
    $pair = $conversationPairs[$i]
    
    try {
        $messageCount = Get-Random -Minimum $MinMessagesPerConversation -Maximum ($MaxMessagesPerConversation + 1)
        
        Show-Progress -Current ($i + 1) -Total $conversationPairs.Count `
            -Message "生成会话消息: $($pair.participant1.username) <-> $($pair.participant2.username) ($messageCount 条)"
        
        # 生成消息
        $messages = New-ConversationMessages -Pair $pair -MessageCount $messageCount
        
        if ($messages.Count -gt 0) {
            # 随机决定是否标记部分消息为已读
            # 50% 的概率标记 participant1 的消息为已读
            if ((Get-Random -Minimum 0 -Maximum 2) -eq 0) {
                try {
                    $conversation = Get-ConversationByUser -UserId $pair.participant1.id -OtherUserId $pair.participant2.id
                    Mark-MessagesAsRead -ConversationId $conversation.id -UserId $pair.participant1.id
                }
                catch {
                    Write-ColorOutput "`n⚠ 标记消息为已读失败: $_" "Yellow"
                }
            }
            
            # 50% 的概率标记 participant2 的消息为已读
            if ((Get-Random -Minimum 0 -Maximum 2) -eq 0) {
                try {
                    $conversation = Get-ConversationByUser -UserId $pair.participant2.id -OtherUserId $pair.participant1.id
                    Mark-MessagesAsRead -ConversationId $conversation.id -UserId $pair.participant2.id
                }
                catch {
                    Write-ColorOutput "`n⚠ 标记消息为已读失败: $_" "Yellow"
                }
            }
            
            $totalMessages += $messages.Count
            $totalConversations++
        }
    }
    catch {
        Write-ColorOutput "`n⚠ 生成会话消息失败: $($pair.participant1.username) <-> $($pair.participant2.username)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
        $failedConversations += $pair
    }
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 私信数据生成完成" "Green"
Write-ColorOutput "  成功会话: $totalConversations" "Green"
Write-ColorOutput "  总消息数: $totalMessages" "Green"

if ($failedConversations.Count -gt 0) {
    Write-ColorOutput "  失败会话: $($failedConversations.Count)" "Red"
}

# 显示统计信息
$avgMessagesPerConversation = if ($totalConversations -gt 0) { 
    [math]::Round($totalMessages / $totalConversations, 1) 
} else { 
    0 
}

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  目标会话数: $ConversationCount" "Gray"
Write-ColorOutput "  成功会话数: $totalConversations" "Green"
Write-ColorOutput "  失败会话数: $($failedConversations.Count)" "Red"
Write-ColorOutput "  总消息数: $totalMessages" "Gray"
Write-ColorOutput "  平均每会话消息数: $avgMessagesPerConversation" "Gray"
Write-ColorOutput "  成功率: $([math]::Round(($totalConversations / $ConversationCount) * 100, 2))%" "Gray"

if ($totalConversations -eq $ConversationCount) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          私信数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          私信数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试私信数据

.DESCRIPTION
    此脚本自动生成私信会话和消息数据。会话会在发送第一条消息时自动创建，
    确保参与者 ID 顺序正确，并设置部分消息为已读状态。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER ConversationCount
    要生成的会话数量，默认为 50

.PARAMETER MinMessagesPerConversation
    每个会话最少消息数，默认为 5

.PARAMETER MaxMessagesPerConversation
    每个会话最多消息数，默认为 20

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-Messages.ps1
    使用默认配置生成 50 个会话

.EXAMPLE
    .\Generate-Messages.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-Messages.ps1 -ConversationCount 100
    生成 100 个会话

.EXAMPLE
    .\Generate-Messages.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. blog-id-generator 服务已启动（端口 8088）
    2. blog-gateway 服务已启动（端口 8000）
    3. 已执行用户数据生成脚本
    4. 网络连接正常

    生成数据：
    - 50 个会话（默认）
    - 每个会话 5-20 条消息
    - 会话自动创建（发送第一条消息时）
    - 参与者 ID 按顺序排列（participant1_id < participant2_id）
    - 部分消息标记为已读
    - 自动更新会话的最后消息信息和未读计数

    验证需求：
    - Requirements 7.1: 生成至少 50 个私信会话
    - Requirements 7.2: 确保 participant1_id 和 participant2_id 都是有效的用户 ID
    - Requirements 7.3: 确保 participant1_id 小于 participant2_id
    - Requirements 7.4: 为每个会话生成 5-20 条消息
    - Requirements 7.5: 确保 conversation_id 指向有效的会话
    - Requirements 7.6: 确保 sender_id 和 receiver_id 是会话的参与者
    - Requirements 7.7: 为部分消息设置 is_read 为 true 并设置 read_at 时间戳
    - Requirements 7.8: 更新会话的 last_message_id、last_message_content 和 last_message_at
    - Requirements 7.9: 更新会话的未读计数
#>
