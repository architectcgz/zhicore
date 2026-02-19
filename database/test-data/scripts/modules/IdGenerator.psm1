# =====================================================
# ID Generator Module
# ID 生成器模块
# 
# 说明：此模块提供 ID 生成相关的工具函数
# 功能：
# 1. 从 ID Generator 服务获取单个或批量 ID
# 2. ID 缓存机制
# 3. 失败重试
# =====================================================

# ID 缓存
$script:IdCache = @{}

<#
.SYNOPSIS
    从 ID Generator 服务获取单个 ID

.DESCRIPTION
    调用 blog-id-generator 服务生成单个雪花算法 ID

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址

.PARAMETER BusinessType
    业务类型（可选）

.PARAMETER MaxRetries
    最大重试次数，默认为 3

.EXAMPLE
    Get-NextId -IdGeneratorUrl "http://localhost:8088"

.EXAMPLE
    Get-NextId -IdGeneratorUrl "http://localhost:8088" -BusinessType "tag"
#>
function Get-NextId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$IdGeneratorUrl,
        
        [string]$BusinessType = "",
        
        [int]$MaxRetries = 3
    )
    
    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        try {
            $response = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake" `
                -Method Get `
                -TimeoutSec 5 `
                -ErrorAction Stop
            
            if ($response.code -ne 200) {
                throw "ID 生成失败: $($response.message)"
            }
            
            return $response.data
        }
        catch {
            $attempt++
            if ($attempt -ge $MaxRetries) {
                throw "获取 ID 失败（已重试 $MaxRetries 次）: $_"
            }
            Write-Warning "获取 ID 失败，正在重试（$attempt/$MaxRetries）..."
            Start-Sleep -Seconds 1
        }
    }
}

<#
.SYNOPSIS
    从 ID Generator 服务批量获取 ID

.DESCRIPTION
    调用 blog-id-generator 服务批量生成雪花算法 ID

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址

.PARAMETER Count
    需要生成的 ID 数量

.PARAMETER BusinessType
    业务类型（可选）

.PARAMETER MaxRetries
    最大重试次数，默认为 3

.EXAMPLE
    Get-BatchIds -IdGeneratorUrl "http://localhost:8088" -Count 30

.EXAMPLE
    Get-BatchIds -IdGeneratorUrl "http://localhost:8088" -Count 30 -BusinessType "tag"
#>
function Get-BatchIds {
    param(
        [Parameter(Mandatory = $true)]
        [string]$IdGeneratorUrl,
        
        [Parameter(Mandatory = $true)]
        [int]$Count,
        
        [string]$BusinessType = "",
        
        [int]$MaxRetries = 3
    )
    
    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        try {
            $response = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake/batch?count=$Count" `
                -Method Get `
                -TimeoutSec 10 `
                -ErrorAction Stop
            
            if ($response.code -ne 200) {
                throw "批量 ID 生成失败: $($response.message)"
            }
            
            $ids = $response.data
            
            if ($ids.Count -ne $Count) {
                throw "ID 数量不正确: 期望 $Count，实际 $($ids.Count)"
            }
            
            return $ids
        }
        catch {
            $attempt++
            if ($attempt -ge $MaxRetries) {
                throw "批量获取 ID 失败（已重试 $MaxRetries 次）: $_"
            }
            Write-Warning "批量获取 ID 失败，正在重试（$attempt/$MaxRetries）..."
            Start-Sleep -Seconds 2
        }
    }
}

<#
.SYNOPSIS
    测试 ID Generator 服务可用性

.DESCRIPTION
    通过生成一个测试 ID 来验证服务是否正常运行

.PARAMETER IdGeneratorUrl
    ID Generator 服务地址

.EXAMPLE
    Test-IdGeneratorService -IdGeneratorUrl "http://localhost:8088"
#>
function Test-IdGeneratorService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$IdGeneratorUrl
    )
    
    try {
        $testResponse = Invoke-RestMethod -Uri "$IdGeneratorUrl/api/v1/id/snowflake" `
            -Method Get `
            -TimeoutSec 5 `
            -ErrorAction Stop
        
        if ($testResponse.code -eq 200) {
            return @{
                Available = $true
                TestId = $testResponse.data
                Message = "服务正常运行"
            }
        }
        else {
            return @{
                Available = $false
                TestId = $null
                Message = "服务返回错误: $($testResponse.message)"
            }
        }
    }
    catch {
        return @{
            Available = $false
            TestId = $null
            Message = "无法连接到服务: $_"
        }
    }
}

# 导出函数
Export-ModuleMember -Function Get-NextId, Get-BatchIds, Test-IdGeneratorService
