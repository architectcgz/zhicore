# =====================================================
# Generate Announcements Script
# 生成全局公告脚本
# 
# 说明：此脚本用于生成测试全局公告数据
# 功能：
# 1. 生成全局公告
# 2. 设置部分公告为启用状态
# 3. 为部分公告设置过期时间
# 
# Requirements: 9.1, 9.2, 9.3
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [int]$AnnouncementCount = 5,
    [double]$EnabledRatio = 0.6,
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
    $AnnouncementCount = $config.announcements.count
    $EnabledRatio = $config.announcements.enabledRatio
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  公告数量: $AnnouncementCount" "Gray"
    Write-ColorOutput "  启用比例: $($EnabledRatio * 100)%" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $IdGeneratorUrl = $config.idGeneratorUrl
        $AnnouncementCount = $config.announcements.count
        $EnabledRatio = $config.announcements.enabledRatio
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 公告类型枚举
$AnnouncementTypes = @{
    INFO = 0
    WARNING = 1
    IMPORTANT = 2
    MAINTENANCE = 3
}

# 公告内容模板
$AnnouncementTemplates = @(
    @{
        title = "欢迎使用博客系统"
        content = "欢迎来到我们的博客平台！在这里你可以分享你的想法、经验和知识。我们致力于为用户提供最好的写作和阅读体验。"
        type = $AnnouncementTypes.INFO
        priority = 1
    },
    @{
        title = "系统维护通知"
        content = "系统将于今晚 23:00-01:00 进行例行维护，届时部分功能可能暂时不可用。感谢您的理解与支持！"
        type = $AnnouncementTypes.MAINTENANCE
        priority = 5
        hasExpiry = $true
    },
    @{
        title = "新功能上线"
        content = "我们很高兴地宣布，博客系统新增了 Markdown 编辑器、代码高亮、图片上传等功能。快来体验吧！"
        type = $AnnouncementTypes.INFO
        priority = 3
        link = "/features"
    },
    @{
        title = "社区规范提醒"
        content = "请遵守社区规范，发布健康、积极的内容。我们将对违规内容进行处理。让我们共同维护良好的社区环境！"
        type = $AnnouncementTypes.WARNING
        priority = 4
    },
    @{
        title = "优秀作者评选活动"
        content = "本月优秀作者评选活动开始啦！发布高质量文章，获得更多点赞和收藏，就有机会获得优秀作者称号和奖励。"
        type = $AnnouncementTypes.IMPORTANT
        priority = 4
        link = "/events/author-of-month"
        hasExpiry = $true
    },
    @{
        title = "账号安全提醒"
        content = "为了保护您的账号安全，请定期修改密码，不要在公共场所登录账号，不要将密码告诉他人。"
        type = $AnnouncementTypes.WARNING
        priority = 3
    },
    @{
        title = "内容推荐算法优化"
        content = "我们优化了内容推荐算法，现在您将看到更多符合您兴趣的优质内容。同时，我们也会推荐一些新作者的文章，帮助他们获得更多关注。"
        type = $AnnouncementTypes.INFO
        priority = 2
    },
    @{
        title = "服务器升级通知"
        content = "为了提供更好的服务，我们将在本周末对服务器进行升级。升级期间可能会有短暂的服务中断，预计不超过 30 分钟。"
        type = $AnnouncementTypes.MAINTENANCE
        priority = 5
        hasExpiry = $true
    },
    @{
        title = "隐私政策更新"
        content = "我们更新了隐私政策，以更好地保护您的个人信息。请查看最新的隐私政策了解详情。"
        type = $AnnouncementTypes.IMPORTANT
        priority = 4
        link = "/privacy-policy"
    },
    @{
        title = "写作技巧分享"
        content = "想写出更好的文章吗？查看我们的写作技巧指南，学习如何撰写吸引人的标题、组织文章结构、使用图片和代码等。"
        type = $AnnouncementTypes.INFO
        priority = 2
        link = "/writing-guide"
    }
)

<#
.SYNOPSIS
    从数据库获取管理员用户

.DESCRIPTION
    使用 psql 命令查询管理员用户的 ID

.EXAMPLE
    Get-AdminUsers
#>
function Get-AdminUsers {
    Write-ColorOutput "  查询管理员用户..." "Gray"
    
    try {
        $query = @"
SELECT DISTINCT u.id, u.username 
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE r.name = 'ADMIN' AND u.username LIKE 'test_%'
ORDER BY u.id
"@
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
            Write-ColorOutput "✓ 获取到 $($results.Count) 个管理员用户" "Green"
            return $results
        }
        else {
            throw "未找到管理员用户"
        }
    }
    catch {
        throw "获取管理员用户失败: $_"
    }
}

<#
.SYNOPSIS
    生成公告数据

.DESCRIPTION
    生成指定数量的全局公告

.PARAMETER Count
    要生成的公告数量

.PARAMETER AdminUsers
    管理员用户列表

.EXAMPLE
    New-Announcements -Count 5 -AdminUsers $adminUsers
#>
function New-Announcements {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Count,
        
        [Parameter(Mandatory = $true)]
        [array]$AdminUsers
    )
    
    $announcements = @()
    
    # 确保至少有足够的模板
    $templates = $AnnouncementTemplates
    if ($Count -gt $templates.Count) {
        # 如果需要的数量超过模板数量，重复使用模板
        $templates = $templates * [math]::Ceiling($Count / $templates.Count)
    }
    
    # 随机打乱模板顺序
    $templates = $templates | Get-Random -Count $templates.Count
    
    for ($i = 0; $i -lt $Count; $i++) {
        $template = $templates[$i]
        
        # 生成公告 ID
        $announcementId = Get-NextId -IdGeneratorUrl $IdGeneratorUrl
        
        # 随机选择创建者（管理员）
        $creator = $AdminUsers | Get-Random
        
        # 随机决定是否启用
        $isEnabled = (Get-Random -Minimum 0.0 -Maximum 1.0) -lt $EnabledRatio
        
        # 随机决定是否设置过期时间
        $expiresAt = $null
        if ($template.hasExpiry -and (Get-Random -Minimum 0.0 -Maximum 1.0) -lt 0.5) {
            # 设置 7-30 天后过期
            $daysToExpire = Get-Random -Minimum 7 -Maximum 31
            $expiresAt = (Get-Date).AddDays($daysToExpire)
        }
        
        $announcement = @{
            id = $announcementId
            title = $template.title
            content = $template.content
            is_markdown = $false
            type = $template.type
            link = $template.link
            created_by_id = $creator.id
            is_enabled = $isEnabled
            expires_at = $expiresAt
            priority = $template.priority
        }
        
        $announcements += $announcement
    }
    
    return $announcements
}

<#
.SYNOPSIS
    生成 SQL 插入语句

.DESCRIPTION
    将公告数据转换为 SQL INSERT 语句

.PARAMETER Announcements
    公告列表

.EXAMPLE
    New-InsertStatements -Announcements $announcements
#>
function New-InsertStatements {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Announcements
    )
    
    $statements = @()
    
    foreach ($announcement in $Announcements) {
        $link = if ($announcement.link) { "'$($announcement.link)'" } else { "NULL" }
        $expiresAt = if ($announcement.expires_at) { 
            "'$($announcement.expires_at.ToString("yyyy-MM-dd HH:mm:ss"))'" 
        } else { 
            "NULL" 
        }
        $isEnabled = if ($announcement.is_enabled) { "TRUE" } else { "FALSE" }
        $isMarkdown = if ($announcement.is_markdown) { "TRUE" } else { "FALSE" }
        
        $statement = @"
INSERT INTO global_announcements (id, title, content, is_markdown, type, link, created_by_id, is_enabled, expires_at, priority, created_at)
VALUES ($($announcement.id), '$($announcement.title)', '$($announcement.content)', $isMarkdown, $($announcement.type), $link, $($announcement.created_by_id), $isEnabled, $expiresAt, $($announcement.priority), CURRENT_TIMESTAMP);
"@
        
        $statements += $statement
    }
    
    return $statements
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          全局公告数据生成脚本                      ║" "Cyan"
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

# 步骤 2: 获取管理员用户
Write-ColorOutput "`n=== 步骤 2: 获取管理员用户 ===" "Cyan"

try {
    $adminUsers = Get-AdminUsers
}
catch {
    Write-ColorOutput "✗ 获取管理员用户失败: $_" "Red"
    exit 1
}

# 步骤 3: 生成公告数据
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "公告数据已生成但未插入数据库" "Yellow"
    
    $sampleAnnouncements = New-Announcements -Count $AnnouncementCount -AdminUsers $adminUsers
    
    Write-ColorOutput "`n示例公告（共 $($sampleAnnouncements.Count) 条）:" "Yellow"
    
    foreach ($announcement in $sampleAnnouncements) {
        $typeName = ($AnnouncementTypes.GetEnumerator() | Where-Object { $_.Value -eq $announcement.type }).Name
        $enabledText = if ($announcement.is_enabled) { "启用" } else { "禁用" }
        $expiryText = if ($announcement.expires_at) { "过期: $($announcement.expires_at.ToString('yyyy-MM-dd'))" } else { "无过期" }
        
        Write-ColorOutput "  - $($announcement.title)" "Gray"
        Write-ColorOutput "    类型: $typeName, 状态: $enabledText, $expiryText" "DarkGray"
    }
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 3: 生成公告数据 ===" "Cyan"

try {
    $announcements = New-Announcements -Count $AnnouncementCount -AdminUsers $adminUsers
    Write-ColorOutput "✓ 生成 $($announcements.Count) 条公告" "Green"
}
catch {
    Write-ColorOutput "✗ 生成公告数据失败: $_" "Red"
    exit 1
}

# 步骤 4: 生成 SQL 文件
Write-ColorOutput "`n=== 步骤 4: 生成 SQL 文件 ===" "Cyan"

$sqlOutputPath = Join-Path $PSScriptRoot "..\sql\generated-announcements.sql"

try {
    $sqlStatements = New-InsertStatements -Announcements $announcements
    
    $sqlContent = @"
-- =====================================================
-- Generated Announcements Data
-- 生成的全局公告数据
-- 
-- 生成时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
-- 总公告数: $($announcements.Count)
-- =====================================================

\c blog_notification;

BEGIN;

$($sqlStatements -join "`n`n")

COMMIT;

-- =====================================================
-- 全局公告数据生成完成
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

Write-ColorOutput "✓ 全局公告数据生成完成" "Green"
Write-ColorOutput "  总公告数: $($announcements.Count)" "Green"

# 统计启用/禁用
$enabledCount = ($announcements | Where-Object { $_.is_enabled }).Count
$disabledCount = $announcements.Count - $enabledCount
$enabledPercentage = [math]::Round(($enabledCount / $announcements.Count) * 100, 1)

Write-ColorOutput "`n=== 启用状态统计 ===" "Cyan"
Write-ColorOutput "  启用: $enabledCount ($enabledPercentage%)" "Gray"
Write-ColorOutput "  禁用: $disabledCount ($([math]::Round(100 - $enabledPercentage, 1))%)" "Gray"

# 统计过期时间
$withExpiryCount = ($announcements | Where-Object { $_.expires_at }).Count
$withoutExpiryCount = $announcements.Count - $withExpiryCount

Write-ColorOutput "`n=== 过期时间统计 ===" "Cyan"
Write-ColorOutput "  设置过期时间: $withExpiryCount" "Gray"
Write-ColorOutput "  无过期时间: $withoutExpiryCount" "Gray"

# 统计类型
$typeStats = @{}
foreach ($announcement in $announcements) {
    $typeName = ($AnnouncementTypes.GetEnumerator() | Where-Object { $_.Value -eq $announcement.type }).Name
    if (-not $typeStats.ContainsKey($typeName)) {
        $typeStats[$typeName] = 0
    }
    $typeStats[$typeName]++
}

Write-ColorOutput "`n=== 公告类型统计 ===" "Cyan"
foreach ($type in $typeStats.Keys | Sort-Object) {
    $count = $typeStats[$type]
    $percentage = [math]::Round(($count / $announcements.Count) * 100, 1)
    Write-ColorOutput "  $type : $count ($percentage%)" "Gray"
}

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
Write-ColorOutput "║          全局公告数据生成成功！                    ║" "Green"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"

Write-ColorOutput "`n下一步：执行 SQL 文件插入数据" "Yellow"
Write-ColorOutput "  psql -h localhost -p 5432 -U postgres -f `"$sqlOutputPath`"" "Gray"

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试全局公告数据

.DESCRIPTION
    此脚本生成全局公告数据，包括不同类型的公告、启用状态和过期时间。
    公告数据会生成 SQL 文件，需要手动执行 SQL 文件插入数据库。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER AnnouncementCount
    要生成的公告数量，默认为 5

.PARAMETER EnabledRatio
    启用公告比例，默认为 0.6（60%）

.PARAMETER DryRun
    仅生成数据但不创建 SQL 文件，用于预览

.EXAMPLE
    .\Generate-Announcements.ps1
    使用默认配置生成公告数据

.EXAMPLE
    .\Generate-Announcements.ps1 -DryRun
    预览模式，生成数据但不创建 SQL 文件

.EXAMPLE
    .\Generate-Announcements.ps1 -AnnouncementCount 10
    生成 10 条公告

.EXAMPLE
    .\Generate-Announcements.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. blog-id-generator 服务已启动（端口 8088）
    2. PostgreSQL 数据库已启动（端口 5432）
    3. 已执行用户数据生成脚本

    生成数据：
    - 默认生成 5 条公告
    - 60% 的公告处于启用状态（默认）
    - 部分公告设置过期时间
    - 包含 4 种类型：INFO、WARNING、IMPORTANT、MAINTENANCE
    - 生成 SQL 文件需要手动执行

    验证需求：
    - Requirements 9.1: 生成至少 5 条全局公告
    - Requirements 9.2: 确保至少 3 条公告处于启用状态
    - Requirements 9.3: 为部分公告设置过期时间
#>
