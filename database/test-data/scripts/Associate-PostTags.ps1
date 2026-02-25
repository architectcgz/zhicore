# =====================================================
# Associate Post Tags Script
# 文章标签关联脚本
# 
# 说明：此脚本用于为文章关联标签
# 功能：
# 1. 获取所有已创建的文章
# 2. 获取所有已创建的标签
# 3. 为每篇文章关联 1-5 个标签
# 4. 更新标签统计
# 
# Requirements: 4.4, 4.5, 4.6
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$AppId = "test-app",
    [int]$MinTagsPerPost = 1,
    [int]$MaxTagsPerPost = 5,
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
    $MinTagsPerPost = $config.tags.tagsPerPost.min
    $MaxTagsPerPost = $config.tags.tagsPerPost.max
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  每篇文章标签数: $MinTagsPerPost-$MaxTagsPerPost" "Gray"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $ApiBaseUrl = $config.apiBaseUrl
        $AppId = $config.appId
        $MinTagsPerPost = $config.tags.tagsPerPost.min
        $MaxTagsPerPost = $config.tags.tagsPerPost.max
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

<#
.SYNOPSIS
    获取所有标签

.DESCRIPTION
    从 API 获取所有标签列表

.EXAMPLE
    Get-AllTags
#>
function Get-AllTags {
    Write-ColorOutput "  获取标签列表..." "Gray"
    
    try {
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/tags" `
            -Method Get `
            -Headers @{
                "X-App-Id" = $AppId
            }
        
        if ($result -and $result.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($result.Count) 个标签" "Green"
            return $result
        }
        else {
            throw "未找到标签"
        }
    }
    catch {
        throw "获取标签失败: $_"
    }
}

<#
.SYNOPSIS
    获取所有文章

.DESCRIPTION
    从 API 获取所有文章列表

.EXAMPLE
    Get-AllPosts
#>
function Get-AllPosts {
    Write-ColorOutput "  获取文章列表..." "Gray"
    
    try {
        # 获取已发布文章
        $publishedPosts = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts?page=1&size=1000" `
            -Method Get `
            -Headers @{
                "X-App-Id" = $AppId
            }
        
        $allPosts = @()
        if ($publishedPosts -and $publishedPosts.items) {
            $allPosts += $publishedPosts.items
        }
        
        if ($allPosts.Count -gt 0) {
            Write-ColorOutput "✓ 获取到 $($allPosts.Count) 篇文章" "Green"
            return $allPosts
        }
        else {
            throw "未找到文章"
        }
    }
    catch {
        throw "获取文章失败: $_"
    }
}

<#
.SYNOPSIS
    为文章关联标签

.DESCRIPTION
    调用 API 为文章添加标签

.PARAMETER PostId
    文章 ID

.PARAMETER OwnerId
    文章作者 ID

.PARAMETER TagNames
    标签名称列表

.EXAMPLE
    Add-PostTags -PostId 123 -OwnerId 456 -TagNames @("Java", "Spring Boot")
#>
function Add-PostTags {
    param(
        [Parameter(Mandatory = $true)]
        [long]$PostId,
        
        [Parameter(Mandatory = $true)]
        [long]$OwnerId,
        
        [Parameter(Mandatory = $true)]
        [array]$TagNames
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
        "X-User-Id" = $OwnerId
    }
    
    $body = @{
        tags = $TagNames
    }
    
    try {
        Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/posts/$PostId/tags" `
            -Method Post `
            -Headers $headers `
            -Body $body | Out-Null
        
        return $true
    }
    catch {
        throw "为文章添加标签失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          文章标签关联脚本                          ║" "Cyan"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Cyan"

# 步骤 1: 验证服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证服务可用性 ===" "Cyan"

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

# 步骤 2: 获取标签和文章
Write-ColorOutput "`n=== 步骤 2: 获取标签和文章 ===" "Cyan"

try {
    $tags = Get-AllTags
}
catch {
    Write-ColorOutput "✗ 获取标签失败: $_" "Red"
    Write-ColorOutput "  请确保已执行标签生成脚本" "Yellow"
    exit 1
}

try {
    $posts = Get-AllPosts
}
catch {
    Write-ColorOutput "✗ 获取文章失败: $_" "Red"
    Write-ColorOutput "  请确保已执行文章生成脚本" "Yellow"
    exit 1
}

# 步骤 3: 为文章关联标签
Write-ColorOutput "`n=== 步骤 3: 为文章关联标签 ===" "Cyan"

if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "标签关联数据已生成但未执行" "Yellow"
    Write-ColorOutput "`n关联预览（前 10 篇文章）:" "Yellow"
    
    $previewCount = [math]::Min(10, $posts.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $post = $posts[$i]
        $tagCount = Get-Random -Minimum $MinTagsPerPost -Maximum ($MaxTagsPerPost + 1)
        $selectedTags = $tags | Get-Random -Count $tagCount
        $tagNames = $selectedTags | ForEach-Object { $_.name }
        
        Write-ColorOutput "  $($i + 1). 文章: $($post.title.Substring(0, [math]::Min(40, $post.title.Length)))..." "Gray"
        Write-ColorOutput "     标签: $($tagNames -join ', ')" "Gray"
    }
    
    if ($posts.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($posts.Count - 10) 篇文章" "Gray"
    }
    
    exit 0
}

$successCount = 0
$failedCount = 0
$totalTagAssociations = 0

for ($i = 0; $i -lt $posts.Count; $i++) {
    $post = $posts[$i]
    
    try {
        Show-Progress -Current ($i + 1) -Total $posts.Count -Message "关联标签: 文章 $($post.id)"
        
        # 随机选择 1-5 个标签
        $tagCount = Get-Random -Minimum $MinTagsPerPost -Maximum ($MaxTagsPerPost + 1)
        $selectedTags = $tags | Get-Random -Count $tagCount
        $tagNames = $selectedTags | ForEach-Object { $_.name }
        
        # 为文章添加标签
        Add-PostTags -PostId $post.id -OwnerId $post.ownerId -TagNames $tagNames
        
        $successCount++
        $totalTagAssociations += $tagNames.Count
    }
    catch {
        Write-ColorOutput "`n⚠ 为文章关联标签失败: $($post.title)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
        $failedCount++
    }
}

# 步骤 4: 显示结果
Write-ColorOutput "`n=== 步骤 4: 关联结果 ===" "Cyan"

Write-ColorOutput "✓ 标签关联完成" "Green"
Write-ColorOutput "  成功: $successCount" "Green"
Write-ColorOutput "  失败: $failedCount" "Red"
Write-ColorOutput "  总关联数: $totalTagAssociations" "Gray"
Write-ColorOutput "  平均每篇文章: $([math]::Round($totalTagAssociations / $successCount, 1)) 个标签" "Gray"

if ($successCount -eq $posts.Count) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          标签关联成功！                            ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          标签关联完成（部分失败）                  ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    为文章关联标签

.DESCRIPTION
    此脚本自动获取所有文章和标签，为每篇文章随机关联 1-5 个标签，
    并更新标签统计信息。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER MinTagsPerPost
    每篇文章最少标签数，默认为 1

.PARAMETER MaxTagsPerPost
    每篇文章最多标签数，默认为 5

.PARAMETER DryRun
    仅生成数据但不执行，用于预览

.EXAMPLE
    .\Associate-PostTags.ps1
    使用默认配置为文章关联标签

.EXAMPLE
    .\Associate-PostTags.ps1 -DryRun
    预览模式，生成数据但不执行

.EXAMPLE
    .\Associate-PostTags.ps1 -MinTagsPerPost 2 -MaxTagsPerPost 4
    每篇文章关联 2-4 个标签

.EXAMPLE
    .\Associate-PostTags.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-gateway 服务已启动（端口 8000）
    2. 已执行标签生成脚本
    3. 已执行文章生成脚本
    4. 网络连接正常

    功能说明：
    - 为每篇文章关联 1-5 个标签
    - 标签随机选择，确保多样性
    - 自动更新标签统计信息

    验证需求：
    - Requirements 4.4: 为每篇文章关联 1-5 个标签
    - Requirements 4.5: 确保 post_id 和 tag_id 都是有效的 ID
    - Requirements 4.6: 更新 tag_stats 表的文章计数
#>
