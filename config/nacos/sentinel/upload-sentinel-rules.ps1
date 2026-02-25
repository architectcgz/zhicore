# Sentinel 规则上传脚本
# 将 Sentinel 规则配置上传到 Nacos

param(
    [string]$NacosServer = "http://localhost:8848",
    [string]$Username = "nacos",
    [string]$Password = "nacos",
    [string]$Namespace = "",
    [string]$Group = "SENTINEL_RULES"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Sentinel Rules Upload Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 获取脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 定义规则文件
$rules = @(
    @{
        DataId = "ZhiCore-post-flow-rules"
        File = "ZhiCore-post-flow-rules.json"
        Type = "json"
        Description = "ZhiCore Post Service Flow Control Rules"
    },
    @{
        DataId = "ZhiCore-post-degrade-rules"
        File = "ZhiCore-post-degrade-rules.json"
        Type = "json"
        Description = "ZhiCore Post Service Circuit Breaker Rules"
    },
    @{
        DataId = "ZhiCore-post-system-rules"
        File = "ZhiCore-post-system-rules.json"
        Type = "json"
        Description = "ZhiCore Post Service System Protection Rules"
    }
)

# 上传函数
function Upload-NacosConfig {
    param(
        [string]$DataId,
        [string]$FilePath,
        [string]$Type,
        [string]$Description
    )
    
    Write-Host "Uploading $DataId..." -ForegroundColor Yellow
    
    if (-not (Test-Path $FilePath)) {
        Write-Host "  [ERROR] File not found: $FilePath" -ForegroundColor Red
        return $false
    }
    
    $content = Get-Content -Path $FilePath -Raw -Encoding UTF8
    
    $url = "$NacosServer/nacos/v1/cs/configs"
    
    $body = @{
        dataId = $DataId
        group = $Group
        content = $content
        type = $Type
        desc = $Description
    }
    
    if ($Namespace) {
        $body.tenant = $Namespace
    }
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Body $body -ContentType "application/x-www-form-urlencoded; charset=UTF-8"
        
        if ($response -eq "true") {
            Write-Host "  [SUCCESS] $DataId uploaded successfully" -ForegroundColor Green
            return $true
        } else {
            Write-Host "  [ERROR] Failed to upload $DataId" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "  [ERROR] Exception occurred: $_" -ForegroundColor Red
        return $false
    }
}

# 上传所有规则
Write-Host "Nacos Server: $NacosServer" -ForegroundColor Cyan
Write-Host "Group: $Group" -ForegroundColor Cyan
Write-Host ""

$successCount = 0
$failCount = 0

foreach ($rule in $rules) {
    $filePath = Join-Path $ScriptDir $rule.File
    $result = Upload-NacosConfig -DataId $rule.DataId -FilePath $filePath -Type $rule.Type -Description $rule.Description
    
    if ($result) {
        $successCount++
    } else {
        $failCount++
    }
    
    Write-Host ""
}

# 总结
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Upload Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Success: $successCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
Write-Host ""

if ($failCount -eq 0) {
    Write-Host "All Sentinel rules uploaded successfully!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some rules failed to upload. Please check the errors above." -ForegroundColor Red
    exit 1
}
