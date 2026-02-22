# =====================================================
# Generate Tags Script
# 生成标签数据脚本
# 
# 说明：此脚本用于生成测试标签数据
# 功能：
# 1. 从 ID Generator 服务获取标签 ID
# 2. 生成标签名称和 slug
# 3. 调用 API 创建标签
# 4. 验证 slug 格式
# 
# Requirements: 4.1, 4.2, 4.3
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$ApiBaseUrl = "http://localhost:8000",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [string]$AppId = "test-app",
    [int]$TagCount = 30,
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
    
    $ApiBaseUrl = $config.apiBaseUrl
    $IdGeneratorUrl = $config.idGeneratorUrl
    $AppId = $config.appId
    $TagCount = $config.tags.count
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
    Write-ColorOutput "  API 地址: $ApiBaseUrl" "Gray"
    Write-ColorOutput "  ID Generator 地址: $IdGeneratorUrl" "Gray"
    Write-ColorOutput "  标签数量: $TagCount" "Gray"
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
        $TagCount = $config.tags.count
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
        Write-ColorOutput "  配置文件: $defaultConfigPath" "Gray"
    }
}

# 预定义的标签名称列表（中英文混合）
$tagNames = @(
    "JavaScript", "TypeScript", "Python", "Java", "Go", "Rust", "C++", "C#",
    "前端开发", "后端开发", "全栈开发", "移动开发", "DevOps", "云计算",
    "数据库", "算法", "数据结构", "设计模式", "微服务", "容器化",
    "Vue.js", "React", "Angular", "Spring Boot", "Django", "Flask",
    "Docker", "Kubernetes", "CI/CD", "测试", "性能优化", "安全",
    "机器学习", "人工智能", "区块链", "Web3", "游戏开发", "嵌入式",
    "Linux", "Windows", "macOS", "Android", "iOS", "Flutter",
    "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Kafka"
)

<#
.SYNOPSIS
    生成标签 slug

.DESCRIPTION
    将标签名称转换为符合格式要求的 slug
    - 小写字母
    - 数字
    - 单个连字符
    - 不能以连字符开头或结尾

.PARAMETER Name
    标签名称

.EXAMPLE
    ConvertTo-TagSlug -Name "JavaScript"
    返回: "javascript"

.EXAMPLE
    ConvertTo-TagSlug -Name "前端开发"
    返回: "frontend-dev"
#>
function ConvertTo-TagSlug {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )
    
    # 转换为小写
    $slug = $Name.ToLower()
    
    # 替换空格为连字符
    $slug = $slug -replace '\s+', '-'
    
    # 移除特殊字符，只保留字母、数字和连字符
    $slug = $slug -replace '[^a-z0-9\-]', ''
    
    # 将多个连续的连字符替换为单个连字符
    $slug = $slug -replace '\-+', '-'
    
    # 移除开头和结尾的连字符
    $slug = $slug.Trim('-')
    
    # 如果 slug 为空或只包含非字母数字字符，使用随机字符串
    if ([string]::IsNullOrWhiteSpace($slug)) {
        $slug = "tag-" + (Get-Random -Minimum 1000 -Maximum 9999)
    }
    
    return $slug
}

<#
.SYNOPSIS
    验证 slug 格式

.DESCRIPTION
    验证 slug 是否符合格式要求：
    - 只包含小写字母、数字和单个连字符
    - 不能以连字符开头或结尾

.PARAMETER Slug
    要验证的 slug

.EXAMPLE
    Test-SlugFormat -Slug "javascript"
    返回: $true

.EXAMPLE
    Test-SlugFormat -Slug "-invalid-"
    返回: $false
#>
function Test-SlugFormat {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Slug
    )
    
    # 正则表达式：小写字母、数字、单个连字符，不能以连字符开头或结尾
    $pattern = '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    
    return $Slug -match $pattern
}

<#
.SYNOPSIS
    生成标签数据

.DESCRIPTION
    生成指定数量的标签数据，包括 ID、名称和 slug

.PARAMETER Count
    要生成的标签数量

.EXAMPLE
    New-TagData -Count 30
#>
function New-TagData {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Count
    )
    
    Write-ColorOutput "`n=== 生成标签数据 ===" "Cyan"
    
    # 获取批量 ID
    Write-ColorOutput "  获取 $Count 个标签 ID..." "Gray"
    $ids = Get-BatchIds -IdGeneratorUrl $IdGeneratorUrl -Count $Count -BusinessType "tag"
    Write-ColorOutput "✓ ID 获取成功" "Green"
    Write-ColorOutput "  ID 范围: $($ids[0]) - $($ids[-1])" "Gray"
    
    # 生成标签数据
    $tags = @()
    $usedSlugs = @{}
    
    for ($i = 0; $i -lt $Count; $i++) {
        # 选择标签名称（循环使用预定义列表）
        $name = $tagNames[$i % $tagNames.Count]
        
        # 如果名称重复，添加数字后缀
        if ($i -ge $tagNames.Count) {
            $suffix = [math]::Floor($i / $tagNames.Count) + 1
            $name = "$name $suffix"
        }
        
        # 生成 slug
        $slug = ConvertTo-TagSlug -Name $name
        
        # 确保 slug 唯一
        $originalSlug = $slug
        $counter = 1
        while ($usedSlugs.ContainsKey($slug)) {
            $slug = "$originalSlug-$counter"
            $counter++
        }
        $usedSlugs[$slug] = $true
        
        # 验证 slug 格式
        if (-not (Test-SlugFormat -Slug $slug)) {
            Write-ColorOutput "⚠ 警告: slug 格式不正确: $slug" "Yellow"
            # 使用备用 slug
            $slug = "tag-$($i + 1)"
        }
        
        $tag = @{
            id = $ids[$i]
            name = $name
            slug = $slug
            description = "测试标签: $name"
        }
        
        $tags += $tag
    }
    
    Write-ColorOutput "✓ 标签数据生成完成" "Green"
    Write-ColorOutput "  生成数量: $($tags.Count)" "Gray"
    
    return $tags
}

<#
.SYNOPSIS
    创建标签

.DESCRIPTION
    调用 API 创建标签

.PARAMETER Tag
    标签数据对象

.EXAMPLE
    New-Tag -Tag @{id=123; name="JavaScript"; slug="javascript"}
#>
function New-Tag {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Tag
    )
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-App-Id" = $AppId
    }
    
    $body = @{
        name = $Tag.name
        slug = $Tag.slug
        description = $Tag.description
    }
    
    try {
        $result = Invoke-ApiWithRetry -Uri "$ApiBaseUrl/api/v1/tags" `
            -Method Post `
            -Headers $headers `
            -Body $body
        
        return $result
    }
    catch {
        throw "创建标签失败: $_"
    }
}

# =====================================================
# 主执行流程
# =====================================================

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          标签数据生成脚本                          ║" "Cyan"
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

# 步骤 2: 生成标签数据
try {
    $tags = New-TagData -Count $TagCount
}
catch {
    Write-ColorOutput "✗ 生成标签数据失败: $_" "Red"
    exit 1
}

# 步骤 3: 创建标签
if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "标签数据已生成但未创建" "Yellow"
    Write-ColorOutput "`n生成的标签预览（前 10 个）:" "Yellow"
    
    $previewCount = [math]::Min(10, $tags.Count)
    for ($i = 0; $i -lt $previewCount; $i++) {
        $tag = $tags[$i]
        Write-ColorOutput "  $($i + 1). $($tag.name) -> $($tag.slug)" "Gray"
    }
    
    if ($tags.Count -gt 10) {
        Write-ColorOutput "  ... 还有 $($tags.Count - 10) 个标签" "Gray"
    }
    
    # 验证所有 slug 格式
    Write-ColorOutput "`n验证 slug 格式:" "Yellow"
    $invalidSlugs = @()
    foreach ($tag in $tags) {
        if (-not (Test-SlugFormat -Slug $tag.slug)) {
            $invalidSlugs += $tag.slug
        }
    }
    
    if ($invalidSlugs.Count -eq 0) {
        Write-ColorOutput "✓ 所有 slug 格式正确" "Green"
    }
    else {
        Write-ColorOutput "✗ 发现 $($invalidSlugs.Count) 个格式不正确的 slug:" "Red"
        $invalidSlugs | ForEach-Object { Write-ColorOutput "  - $_" "Red" }
    }
    
    exit 0
}

Write-ColorOutput "`n=== 步骤 3: 创建标签 ===" "Cyan"

$createdTags = @()
$failedTags = @()

for ($i = 0; $i -lt $tags.Count; $i++) {
    $tag = $tags[$i]
    
    try {
        Show-Progress -Current ($i + 1) -Total $tags.Count -Message "创建标签: $($tag.name)"
        
        $result = New-Tag -Tag $tag
        $createdTags += @{
            tag = $tag
            result = $result
        }
    }
    catch {
        Write-ColorOutput "`n⚠ 创建标签失败: $($tag.name)" "Yellow"
        Write-ColorOutput "  错误: $_" "Yellow"
        $failedTags += @{
            tag = $tag
            error = $_.Exception.Message
        }
    }
}

# 步骤 4: 显示结果
Write-ColorOutput "`n=== 步骤 4: 生成结果 ===" "Cyan"

Write-ColorOutput "✓ 标签创建完成" "Green"
Write-ColorOutput "  成功: $($createdTags.Count)" "Green"

if ($failedTags.Count -gt 0) {
    Write-ColorOutput "  失败: $($failedTags.Count)" "Red"
    Write-ColorOutput "`n失败的标签:" "Red"
    foreach ($failed in $failedTags) {
        Write-ColorOutput "  - $($failed.tag.name): $($failed.error)" "Red"
    }
}

# 步骤 5: 验证 slug 格式
Write-ColorOutput "`n=== 步骤 5: 验证 slug 格式 ===" "Cyan"

$invalidSlugs = @()
foreach ($created in $createdTags) {
    if (-not (Test-SlugFormat -Slug $created.tag.slug)) {
        $invalidSlugs += $created.tag.slug
    }
}

if ($invalidSlugs.Count -eq 0) {
    Write-ColorOutput "✓ 所有 slug 格式正确" "Green"
}
else {
    Write-ColorOutput "✗ 发现 $($invalidSlugs.Count) 个格式不正确的 slug:" "Red"
    $invalidSlugs | ForEach-Object { Write-ColorOutput "  - $_" "Red" }
}

# 显示统计信息
Write-ColorOutput "`n=== 统计信息 ===" "Cyan"
Write-ColorOutput "  总数: $TagCount" "Gray"
Write-ColorOutput "  成功: $($createdTags.Count)" "Green"
Write-ColorOutput "  失败: $($failedTags.Count)" "Red"
Write-ColorOutput "  成功率: $([math]::Round(($createdTags.Count / $TagCount) * 100, 2))%" "Gray"

if ($createdTags.Count -eq $TagCount) {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
    Write-ColorOutput "║          标签数据生成成功！                        ║" "Green"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
}
else {
    Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Yellow"
    Write-ColorOutput "║          标签数据生成完成（部分失败）              ║" "Yellow"
    Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Yellow"
}

# =====================================================
# 使用示例
# =====================================================

<#
.SYNOPSIS
    生成测试标签数据

.DESCRIPTION
    此脚本自动从 ZhiCore-id-generator 服务获取标签 ID，生成标签名称和 slug，
    并调用 API 创建标签。所有 slug 都符合格式要求（小写字母、数字、连字符）。

.PARAMETER ConfigPath
    配置文件路径（可选），如果不提供则使用默认配置或命令行参数

.PARAMETER ApiBaseUrl
    API 基础地址，默认为 http://localhost:8000

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址，默认为 http://localhost:8088

.PARAMETER AppId
    应用 ID，默认为 test-app

.PARAMETER TagCount
    要生成的标签数量，默认为 30

.PARAMETER DryRun
    仅生成数据但不创建，用于预览

.EXAMPLE
    .\Generate-Tags.ps1
    使用默认配置生成 30 个标签

.EXAMPLE
    .\Generate-Tags.ps1 -DryRun
    预览模式，生成数据但不创建

.EXAMPLE
    .\Generate-Tags.ps1 -TagCount 50
    生成 50 个标签

.EXAMPLE
    .\Generate-Tags.ps1 -ConfigPath ".\custom-config.json"
    使用自定义配置文件

.NOTES
    前置条件：
    1. ZhiCore-id-generator 服务已启动（端口 8088）
    2. ZhiCore-gateway 服务已启动（端口 8000）
    3. 网络连接正常

    生成数据：
    - 30 个标签（默认）
    - 每个标签包含：ID、名称、slug、描述
    - slug 格式：小写字母、数字、单个连字符

    验证需求：
    - Requirements 4.1: 生成至少 30 个标签
    - Requirements 4.2: 每个标签有唯一的 name 和 slug
    - Requirements 4.3: slug 符合格式要求
#>
