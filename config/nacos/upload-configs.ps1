# 上传配置到 Nacos
# 此脚本确保使用正确的 UTF-8 编码上传配置文件
param(
    [string]$NacosUrl = "http://localhost:8848",
    [string]$Username = "nacos",
    [string]$Password = "nacos",
    [string]$Group = "ZHICORE-SERVICE"
)

$ErrorActionPreference = "Stop"

# 配置文件列表
$configs = @(
    "common.yml",
    "zhicore-gateway.yml",
    "zhicore-content.yml",
    "zhicore-content-dev.yml",
    "zhicore-comment.yml",
    "zhicore-user.yml",
    "zhicore-upload.yml",
    "zhicore-notification.yml",
    "zhicore-search.yml",
    "zhicore-ranking.yml",
    "zhicore-admin.yml",
    "zhicore-id-generator.yml"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "上传配置到 Nacos" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Nacos URL: $NacosUrl" -ForegroundColor Yellow
Write-Host "Group: $Group" -ForegroundColor Yellow
Write-Host ""

$successCount = 0
$failCount = 0

foreach ($configFile in $configs) {
    $configPath = Join-Path $PSScriptRoot $configFile
    
    if (-not (Test-Path $configPath)) {
        Write-Host "[SKIP] $configFile - 文件不存在" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "处理配置: $configFile" -ForegroundColor Cyan
    
    try {
        # 先删除 Nacos 中的旧配置
        Write-Host "  删除旧配置..." -ForegroundColor Gray
        $deleteParams = @{
            dataId = $configFile
            group = $Group
        }
        
        try {
            Invoke-RestMethod -Uri "$NacosUrl/nacos/v1/cs/configs" `
                -Method Delete `
                -Body $deleteParams `
                -ContentType "application/x-www-form-urlencoded" `
                -ErrorAction SilentlyContinue | Out-Null
            Start-Sleep -Milliseconds 500
        }
        catch {
            # 忽略删除错误
        }
        
        # 读取文件内容 - 使用 UTF-8 无 BOM
        Write-Host "  读取文件..." -ForegroundColor Gray
        $content = [System.IO.File]::ReadAllText($configPath, [System.Text.UTF8Encoding]::new($false))
        
        # 移除可能的 BOM
        if ($content.Length -gt 0 -and [int][char]$content[0] -eq 65279) {
            $content = $content.Substring(1)
            Write-Host "  移除了 BOM" -ForegroundColor Yellow
        }
        
        Write-Host "  文件大小: $($content.Length) 字符" -ForegroundColor Gray
        
        # 上传到 Nacos
        Write-Host "  上传到 Nacos..." -ForegroundColor Gray
        $body = @{
            dataId = $configFile
            group = $Group
            content = $content
            type = "yaml"
            username = $Username
            password = $Password
        }
        
        $response = Invoke-RestMethod -Uri "$NacosUrl/nacos/v1/cs/configs" `
            -Method Post `
            -Body $body `
            -ContentType "application/x-www-form-urlencoded; charset=UTF-8"
        
        if ($response -eq "true") {
            Write-Host "[SUCCESS] $configFile 上传成功" -ForegroundColor Green
            $successCount++
        } else {
            Write-Host "[FAILED] $configFile 上传失败: $response" -ForegroundColor Red
            $failCount++
        }
    }
    catch {
        Write-Host "[ERROR] $configFile 处理出错: $($_.Exception.Message)" -ForegroundColor Red
        $failCount++
    }
    
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "处理完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "成功: $successCount" -ForegroundColor Green
Write-Host "失败: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($successCount -gt 0) {
    Write-Host "配置已上传，请重启服务以加载新配置" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "验证配置:" -ForegroundColor Cyan
Write-Host "访问 Nacos 控制台: $NacosUrl/nacos" -ForegroundColor Yellow
Write-Host "配置管理 -> 配置列表 -> 筛选 Group: $Group" -ForegroundColor Yellow
