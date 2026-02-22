# =====================================================
# Generate Posts Script
# 生成文章数据脚本
# 
# 说明：此脚本用于生成测试文章数据
# 功能：
# 1. 从 ID Generator 服务获取文章 ID
# 2. 生成不同状态的文章（已发布、草稿、定时发布）
# 3. 为文章分配作者
# 4. 生成文章标题、内容、摘要
# 5. 设置时间戳
# 
# Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [string]$AppId = "test-app",
    [int]$PostCount = 200,
    [double]$PublishedRatio = 0.7,
    [double]$DraftRatio = 0.2,
    [double]$ScheduledRatio = 0.1,
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
    $PostCount = $config.posts.totalCount
    $PublishedRatio = $config.posts.publishedRatio
    $DraftRatio = $config.posts.draftRatio
    $ScheduledRatio = $config.posts.scheduledRatio
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  文章数量: $PostCount" "Gray"
    Write-ColorOutput "  已发布比例: $($PublishedRatio * 100)%" "Gray"
    Write-ColorOutput "  草稿比例: $($DraftRatio * 100)%" "Gray"
    Write-ColorOutput "  定时发布比例: $($ScheduledRatio * 100)%" "Gray"
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
        $PostCount = $config.posts.totalCount
        $PublishedRatio = $config.posts.publishedRatio
        $DraftRatio = $config.posts.draftRatio
        $ScheduledRatio = $config.posts.scheduledRatio
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 预定义的文章标题模板
$titleTemplates = @(
    "深入理解 {tech} 核心原理",
    "{tech} 最佳实践指南",
    "从零开始学习 {tech}",
    "{tech} 性能优化技巧",
    "{tech} 实战项目经验分享",
    "掌握 {tech} 的 10 个技巧",
    "{tech} 开发中的常见问题及解决方案",
    "{tech} 架构设计与实现",
    "{tech} 源码分析与解读",
    "使用 {tech} 构建现代化应用"
)

# 技术关键词
$techKeywords = @(
    "Spring Boot", "Vue.js", "React", "TypeScript", "Docker", "Kubernetes",
    "微服务", "分布式系统", "Redis", "PostgreSQL", "MongoDB", "Elasticsearch",
    "消息队列", "缓存策略", "API 设计", "前端工程化", "CI/CD", "DevOps",
    "Java", "Python", "Go", "Rust", "性能优化", "系统设计",
    "数据库优化", "高并发", "负载均衡", "容器化", "云原生", "Serverless"
)

# 文章内容模板
$contentTemplates = @(
    @"
# 简介

本文将深入探讨 {tech} 的核心概念和实践应用。

## 核心概念

{tech} 是现代软件开发中的重要技术，它提供了强大的功能和灵活的架构。

### 主要特性

1. **高性能**：优化的执行效率
2. **易用性**：简洁的 API 设计
3. **可扩展**：灵活的插件机制
4. **稳定性**：经过生产环境验证

## 实践应用

在实际项目中，我们可以这样使用 {tech}：

```
// 示例代码
function example() {
    console.log('Hello, {tech}!');
}
```

## 最佳实践

1. 遵循官方文档的建议
2. 注意性能优化
3. 做好错误处理
4. 编写单元测试

## 总结

通过本文的学习，相信你已经对 {tech} 有了更深入的理解。
"@,
    @"
# 前言

在现代软件开发中，{tech} 扮演着越来越重要的角色。

## 为什么选择 {tech}

{tech} 具有以下优势：

- 开发效率高
- 社区活跃
- 生态完善
- 文档齐全

## 快速开始

### 安装配置

首先需要安装相关依赖...

### 基础使用

创建第一个项目...

## 进阶技巧

### 性能优化

优化建议...

### 常见问题

问题解决方案...

## 结语

希望这篇文章能帮助你更好地使用 {tech}。
"@
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
    生成文章标题

.DESCRIPTION
    使用模板和关键词生成文章标题

.EXAMPLE
    New-PostTitle
#>
function New-PostTitle {
    $template = $titleTemplates | Get-Random
    $tech = $techKeywords | Get-Random
    return $template -replace '\{tech\}', $tech
}

<#
.SYNOPSIS
    生成文章内容

.DESCRIPTION
    使用模板生成文章内容

.PARAMETER Title
    文章标题

.EXAMPLE
    New-PostContent -Title "深入理解 Spring Boot 核心原理"
#>
function New-PostContent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Title
    )
    
    $template = $contentTemplates | Get-Random
    # 从标题中提取技术关键词
    $tech = $Title -replace '.*?([\w\s\.]+).*', '$1'
    if ([string]::IsNullOrWhiteSpace($tech)) {
        $tech = $techKeywords | Get-Random
    }
    
    return $template -replace '\{tech\}', $tech
}

<#
.SYNOPSIS
    生成文章摘要

.DESCRIPTION
    从文章内容中提取前 200 个字符作为摘要

.PARAMETER Content
    文章内容

.EXAMPLE
    New-PostExcerpt -Content "文章内容..."
#>
function New-PostExcerpt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content
    )
    
    # 移除 Markdown 标记
    $plainText = $Content -replace '#', '' -replace '\*', '' -replace '`', ''
    $plainText = $plainText -replace '\n+', ' ' -replace '\s+', ' '
    $plainText = $plainText.Trim()
    
    # 截取前 200 个字符
    if ($plainText.Length -gt 200) {
        return $plainText.Substring(0, 200) + "..."
    }
    else {
        return $plainText
    }
}

<#
.SYNOPSIS
    生成文章数据

.DESCRIPTION
    生成指定数量的文章数据，包括不同状态的文章

.PARAMETER Count
    要生成的文章数量

.PARAMETER Users
    用户列表

.EXAMPLE
    New-PostData -Count 200 -Users $users
#>
function New-PostData {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Count,
        
        [Parameter(Mandatory = $true)]
        [array]$Users
    )
    
    Write-ColorOutput "`n=== 生成文章数据 ===" "Cyan"
    
    # 计算各状态文章数量
    $publishedCount = [math]::Floor($Count * $PublishedRatio)
    $draftCount = [math]::Floor($Count * $DraftRatio)
    $scheduledCount = $Count - $publishedCount - $draftCount
    
    Write-ColorOutput "  文章分布:" "Gray"
    Write-ColorOutput "    已发布: $publishedCount" "Gray"
    Write-ColorOutput "    草稿: $draftCount" "Gray"
    Write-ColorOutput "    定时发布: $scheduledCount" "Gray"
    
    # 获取批量 ID
    Write-ColorOutput "  获取 $Count 个文章 ID..." "Gray"
    $ids = Get-BatchIds -IdGeneratorUrl $IdGeneratorUrl -Count $Count -BusinessType "post"
    Write-ColorOutput "✓ ID 获取成功" "Green"
    
    # 生成文章数据
    $posts = @()
    $currentIndex = 0
    
    # 生成已发布文章
    for ($i = 0; $i -lt $publishedCount; $i++) {
        $title = New-PostTitle
        $content = New-PostContent -Title $title
        $author = $Users | Get-Random
        
        $post = @{
            id = $ids[$currentIndex]
            title = $title
            content = $content
            ownerId = $author.id
            status = "PUBLISHED"
            publishedAt = (Get-Date).AddDays(-(Get-Random -Minimum 1 -Maximum 90))
            createdAt = (Get-Date).AddDays(-(Get-Random -Minimum 91 -Maximum 180))
        }
        
        $posts += $post
        $currentIndex++
    }
    
    # 生成草稿文章
    for ($i = 0; $i -lt $draftCount; $i++) {
        $title = New-PostTitle
        $content = New-PostContent -Title $title
        $author = $Users | Get-Random
        
        $post = @{
            id = $ids[$currentIndex]
            title = $title
            content = $content
            ownerId = $author.id
            status = "DRAFT"
            publishedAt = $null
            createdAt = (Get-Date).AddDays(-(Get-Random -Minimum 1 -Maximum 30))
        }
        
        $posts += $post
        $currentIndex++
    }
    
    # 生成定时发布文章
    for ($i = 0; $i -lt $scheduledCount; $i++) {
        $title = New-PostTitle
        $content = New-PostContent -Title $title
        $author = $Users | Get-Random
        
        $post = @{
            id = $ids[$currentIndex]
            title = $title
            content = $content
            ownerId = $author.id
            status = "SCHEDULED"
            publishedAt = $null
            scheduledAt = (Get-Date).AddDays((Get-Random -Minimum 1 -Maximum 30))
            createdAt = (Get-Date).AddDays(-(Get-Random -Minimum 1 -Maximum 30))
        }
        
        $posts += $post
        $currentIndex++
    }
    
    Write-ColorOutput "✓ 文章数据生成完成" "Green"
    Write-ColorOutput "  生成数量: $($posts.Count)" "Gray"
    
    return $posts
}

<#
.SYNOPSIS
    创建文章

.DESCRIPTION
    调用 API 创建文章

.PARAMETER Post
    文章数据对象

.EXAMPLE
    New-Post -Post @{title="标题"; content="内容"; ownerId=123}
#>
function New-Post {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Post
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $Post.ownerId
    }
    
    $body = @{
        title = $Post.title
        content = $Post.content
    }
    
    try {
        # 创建文章（草稿状态）
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        $postId = $result
        
        # 根据状态执行相应操作
        if ($Post.status -eq "PUBLISHED") {
            # 发布文章
            Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts/$postId/publish" `
                -Method Post `
                -Headers $headers `
                -Body @{} | Out-Null
        }
        elseif ($Post.status -eq "SCHEDULED") {
            # 定时发布
            $scheduleBody = @{
                scheduledAt = $Post.scheduledAt.ToString("yyyy-MM-ddTHH:mm:ss")
            }
            Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts/$postId/schedule" `
                -Method Post `
                -Headers $headers `
                -Body $scheduleBody | Out-Null
        }
        
        return @{
            postId = $postId
            status = $Post.status
        }
    }
    catch {
        throw "创建文章失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          文章数据生成脚本                          ║" "Cyan"
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

# 步骤 3: 生成文章数据
try {
    $posts = New-PostData -Count $PostCount -Users $users
}
catch {
    Write-ColorOutput "✗ 生成文章数据失败: $_" "Red"
    exit 1
}

# 步骤 4: 创建文章
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "文章数据已生成但未创建" "Yellow"
    Write-ColorOutput "`n生成的文章预览（前 10 个）:" "Yellow"
    
    $previewCount = [math]::Min(10, $posts.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $post = $posts[$i]
        Write-ColorOutput "  $($i + 1). [$($post.status)] $($post.title)" "Gray"
        Write-ColorOutput "     作者ID: $($post.ownerId)" "Gray"
    }
    
    if ($posts.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($posts.Count - 10) 篇文章" "Gray"
    }
    
    # 显示统计信息
    $publishedCount = ($posts | Where-Object { $_.status -eq "PUBLISHED" }).Count
    $draftCount = ($posts | Where-Object { $_.status -eq "DRAFT" }).Count
    $scheduledCount = ($posts | Where-Object { $_.status -eq "SCHEDULED" }).Count
    
    Write-ColorOutput "`n文章状态分布:" "Yellow"
    Write-ColorOutput "  已发布: $publishedCount ($([math]::Round($publishedCount / $posts.Count * 100, 1))%)" "Gray"
    Write-ColorOutput "  草稿: $draftCount ($([math]::Round($draftCount / $posts.Count * 100, 1))%)" "Gray"
    Write-ColorOutput "  定时发布: $scheduledCount ($([math]::Round($scheduledCount / $posts.Count * 100, 1))%)" "Gray"
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 4: 创建文章 ===" "Cyan"

$createdPosts = @()
$failedPosts = @()

for ($i = 0; $i -lt $posts.Count; $i++) {
    $post = $posts[$i]
    
    try {
        Show-Progress -Current ($i + 1) -Total $posts.Count -Message "创建文章: $($post.title.Substring(0, [math]::Min(30, $post.title.Length)))..."
        
        $result = New-Post -Post $post
        $createdPosts += @{
            post = $post
            result = $result
        }
    }
    catch {
        Write-ColorOutput "`n⚠ 创建文章失败: $($post.title)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
        $failedPosts += @{
            post = $post
            error = $_.Exception.Message
        }
    }
}

# 步骤 5: 显示结果
Write-ColorOutput "`n=== 步骤 5: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 文章创建完成" "Green"
Write-ColorOutput "  成功: $($createdPosts.Count)" "Green"

if ($failedPosts.Count -gt 0) {
    Write-ColorOutput "  失败: $($failedPosts.Count)" "Red"
    Write-ColorOutput "`n失败的文章:" "Red"
    foreach ($failed in $failedPosts) {
        Write-ColorOutput "  - $($failed.post.title): $($failed.error)" "Red"
    }
}

# 显示统计信息
$publishedCount = ($createdPosts | Where-Object { $_.result.status -eq "PUBLISHED" }).Count
$draftCount = ($createdPosts | Where-Object { $_.result.status -eq "DRAFT" }).Count
$scheduledCount = ($createdPosts | Where-Object { $_.result.status -eq "SCHEDULED" }).Count

Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  总数: $PostCount" "Gray"
Write-ColorOutput "  成功: $($createdPosts.Count)" "Green"
Write-ColorOutput "  失败: $($failedPosts.Count)" "Red"
Write-ColorOutput "  成功率: $([math]::Round(($createdPosts.Count / $PostCount) * 100, 2))%" "Gray"
Write-ColorOutput "`n  状态分布:" "Gray"
Write-ColorOutput "    已发布: $publishedCount" "Gray"
Write-ColorOutput "    草稿: $draftCount" "Gray"
Write-ColorOutput "    定时发布: $scheduledCount" "Gray"

if ($createdPosts.Count -eq $PostCount) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          文章数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          文章数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试文章数据

.DESCRIPTION
    此脚本自动从 ZhiCore-id-generator 服务获取文章 ID，生成不同状态的文章，
    并调用 API 创建文章。文章包括已发布、草稿和定时发布三种状态。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER PostCount
    要生成的文章数量，默认为 200

.PARAMETER PublishedRatio
    已发布文章比例，默认为 0.7 (70%)

.PARAMETER DraftRatio
    草稿文章比例，默认为 0.2 (20%)

.PARAMETER ScheduledRatio
    定时发布文章比例，默认为 0.1 (10%)

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-Posts.ps1
    使用默认配置生成 200 篇文章

.EXAMPLE
    .\Generate-Posts.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-Posts.ps1 -PostCount 100
    生成 100 篇文章

.EXAMPLE
    .\Generate-Posts.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-id-generator 服务已启动（端口 8088）
    2. ZhiCore-gateway 服务已启动（端口 8000）
    3. 已执行用户数据生成脚本
    4. 网络连接正常

    生成数据：
    - 200 篇文章（默认）
    - 70% 已发布文章
    - 20% 草稿文章
    - 10% 定时发布文章
    - 每篇文章包含：ID、标题、内容、作者、状态、时间戳

    验证需求：
    - Requirements 3.1: 生成至少 200 篇文章
    - Requirements 3.2: 至少 70% 的文章处于已发布状态
    - Requirements 3.3: 至少 20% 的文章处于草稿状态
    - Requirements 3.4: 至少 5% 的文章处于定时发布状态
    - Requirements 3.5: 为每篇文章分配有效的 owner_id
    - Requirements 3.6: 为每篇文章生成标题、内容、摘要
    - Requirements 3.8: 已发布文章设置 published_at 时间戳
    - Requirements 3.9: 定时发布文章设置 scheduled_at 时间戳为未来时间
#>
