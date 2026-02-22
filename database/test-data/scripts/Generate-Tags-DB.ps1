# =====================================================
# Generate Tags Script (Database Direct)
# 生成标签数据脚本（直接数据库插入）
# 
# 说明：此脚本直接在数据库中插入标签数据
# 功能：
# 1. 从 ID Generator 服务获取标签 ID
# 2. 生成标签名称和 slug
# 3. 直接在数据库中插入标签数据
# =====================================================

param(
    [string]$ConfigPath = "",
    [string]$IdGeneratorUrl = "http://localhost:8088",
    [int]$TagCount = 30,
    [string]$PostgresContainer = "ZhiCore-postgres",
    [string]$PostgresUser = "postgres",
    [string]$PostgresDatabase = "ZhiCore_post",
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

# 如果提供了配置文件路径，则加载配置
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    Write-ColorOutput "`n=== 加载配置文件 ===" "Cyan"
    $config = Get-Content $ConfigPath | ConvertFrom-Json
    
    $IdGeneratorUrl = $config.idGeneratorUrl
    $TagCount = $config.tags.count
    
    Write-ColorOutput "✓ 配置文件加载成功" "Green"
}
elseif (-not $ConfigPath) {
    # 尝试使用默认配置文件
    $defaultConfigPath = Join-Path $PSScriptRoot "..\test-data-config.json"
    if (Test-Path $defaultConfigPath) {
        Write-ColorOutput "`n=== 加载默认配置文件 ===" "Cyan"
        $config = Get-Content $defaultConfigPath | ConvertFrom-Json
        
        $IdGeneratorUrl = $config.idGeneratorUrl
        $TagCount = $config.tags.count
        
        Write-ColorOutput "✓ 默认配置文件加载成功" "Green"
    }
}

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Cyan"
Write-ColorOutput "║          标签数据生成脚本（数据库直插）            ║" "Cyan"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Cyan"

# 预定义的标签名称列表
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

# 步骤 1: 验证服务可用性
Write-ColorOutput "`n=== 步骤 1: 验证服务可用性 ===" "Cyan"

# 检查 Docker
try {
    $dockerVersion = docker --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 不可用"
    }
    Write-ColorOutput "✓ Docker 可用" "Green"
}
catch {
    Write-ColorOutput "✗ Docker 不可用: $_" "Red"
    exit 1
}

# 检查 PostgreSQL 容器
$containerStatus = docker ps --filter "name=$PostgresContainer" --format "{{.Names}}" 2>&1
if ($containerStatus -ne $PostgresContainer) {
    Write-ColorOutput "✗ PostgreSQL 容器未运行: $PostgresContainer" "Red"
    exit 1
}
Write-ColorOutput "✓ PostgreSQL 容器运行中" "Green"

# 检查 ID Generator
try {
    $response = Invoke-RestMethod -Uri "$IdGeneratorUrl/actuator/health" -TimeoutSec 5
    Write-ColorOutput "✓ ID Generator 服务正常" "Green"
}
catch {
    Write-ColorOutput "✗ ID Generator 服务不可用: $_" "Red"
    exit 1
}

# 步骤 2: 获取标签 ID
Write-ColorOutput "`n=== 步骤 2: 获取标签 ID ===" "Cyan"
try {
    $response = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake/batch?count=$TagCount" -Method Get
    if ($response.code -ne 200) {
        throw "ID 生成失败: $($response.message)"
    }
    $tagIds = $response.data
    Write-ColorOutput "✓ 成功获取 $($tagIds.Count) 个标签 ID" "Green"
}
catch {
    Write-ColorOutput "✗ 获取标签 ID 失败: $_" "Red"
    exit 1
}

# 步骤 3: 生成 SQL 插入语句
Write-ColorOutput "`n=== 步骤 3: 生成标签数据 ===" "Cyan"

$sqlStatements = @()
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

for ($i = 0; $i -lt $TagCount; $i++) {
    $tagId = $tagIds[$i]
    $tagName = $tagNames[$i % $tagNames.Count]
    
    # 生成 slug
    $slug = $tagName.ToLower() `
        -replace '[^a-z0-9\u4e00-\u9fa5]+', '-' `
        -replace '^-+|-+$', ''
    
    # 如果 slug 包含中文，使用拼音或英文替代
    if ($slug -match '[\u4e00-\u9fa5]') {
        $slugMap = @{
            "前端开发" = "frontend-dev"
            "后端开发" = "backend-dev"
            "全栈开发" = "fullstack-dev"
            "移动开发" = "mobile-dev"
            "云计算" = "cloud-computing"
            "数据库" = "database"
            "算法" = "algorithm"
            "数据结构" = "data-structure"
            "设计模式" = "design-pattern"
            "微服务" = "microservice"
            "容器化" = "containerization"
            "测试" = "testing"
            "性能优化" = "performance"
            "安全" = "security"
            "机器学习" = "machine-learning"
            "人工智能" = "ai"
            "区块链" = "blockchain"
            "游戏开发" = "game-dev"
            "嵌入式" = "embedded"
        }
        if ($slugMap.ContainsKey($tagName)) {
            $slug = $slugMap[$tagName]
        }
        else {
            $slug = "tag-$i"
        }
    }
    
    # 确保 slug 唯一
    $slug = "$slug-$i"
    
    $description = "关于 $tagName 的技术文章和讨论"
    
    $sqlStatements += "INSERT INTO tags (id, name, slug, description, created_at, updated_at) VALUES ($tagId, '$tagName', '$slug', '$description', '$now', '$now');"
}

Write-ColorOutput "✓ 生成 $($sqlStatements.Count) 条 SQL 语句" "Green"

if ($DryRun) {
    Write-ColorOutput "`n=== Dry Run 模式 ===" "Yellow"
    Write-ColorOutput "生成的 SQL 语句:" "Yellow"
    $sqlStatements | ForEach-Object { Write-ColorOutput "  $_" "Gray" }
    exit 0
}

# 步骤 4: 执行 SQL 插入
Write-ColorOutput "`n=== 步骤 4: 插入标签数据 ===" "Cyan"

$sql = $sqlStatements -join "`n"

try {
    $output = $sql | docker exec -i $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-ColorOutput "✗ SQL 执行失败" "Red"
        $output | ForEach-Object { Write-ColorOutput "  $_" "Red" }
        exit 1
    }
    
    Write-ColorOutput "✓ 标签数据插入成功" "Green"
}
catch {
    Write-ColorOutput "✗ 插入失败: $_" "Red"
    exit 1
}

# 步骤 5: 验证结果
Write-ColorOutput "`n=== 步骤 5: 验证结果 ===" "Cyan"

$verifyQuery = "SELECT COUNT(*) as count FROM tags;"

try {
    $result = $verifyQuery | docker exec -i $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -t 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ 数据验证完成" "Green"
        Write-ColorOutput "  标签总数: $($result.Trim())" "Gray"
    }
}
catch {
    Write-ColorOutput "⚠ 验证失败: $_" "Yellow"
}

Write-ColorOutput "`n╔════════════════════════════════════════════════════╗" "Green"
Write-ColorOutput "║          标签数据生成完成                          ║" "Green"
Write-ColorOutput "╚════════════════════════════════════════════════════╝" "Green"
Write-ColorOutput "  生成标签数: $TagCount" "Gray"
Write-ColorOutput ""

exit 0
