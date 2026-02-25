# =====================================================
# API Helper Module
# API 辅助模块
# 
# 说明：此模块提供 API 调用相关的工具函数
# 功能：
# 1. 带重试机制的 API 调用
# 2. 错误处理
# 3. 进度显示
# =====================================================

<#
.SYNOPSIS
    带重试机制的 API 调用

.DESCRIPTION
    调用 REST API 并在失败时自动重试

.PARAMETER Uri
    API 地址

.PARAMETER Method
    HTTP 方法（GET, POST, PUT, DELETE 等）

.PARAMETER Headers
    请求头

.PARAMETER Body
    请求体（对象，将自动转换为 JSON）

.PARAMETER MaxRetries
    最大重试次数，默认为 3

.EXAMPLE
    Invoke-ApiWithRetry -Uri "http://localhost:8000/api/tags" -Method Post -Headers @{"Content-Type"="application/json"} -Body @{name="test"}
#>
function Invoke-ApiWithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        
        [Parameter(Mandatory = $true)]
        [string]$Method,
        
        [hashtable]$Headers = @{},
        
        [object]$Body = $null,
        
        [int]$MaxRetries = 3
    )
    
    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        try {
            $params = @{
                Uri = $Uri
                Method = $Method
                Headers = $Headers
                ErrorAction = "Stop"
                TimeoutSec = 30
            }
            
            if ($Body -ne $null) {
                $params.Body = ($Body | ConvertTo-Json -Depth 10)
            }
            
            $response = Invoke-RestMethod @params
            
            # 检查响应码
            if ($response.code -and $response.code -ne 200) {
                throw "API 返回错误: $($response.message)"
            }
            
            return $response.data
        }
        catch {
            $attempt++
            if ($attempt -ge $MaxRetries) {
                throw "API 调用失败（已重试 $MaxRetries 次）: $_"
            }
            Write-Warning "API 调用失败，正在重试（$attempt/$MaxRetries）..."
            Start-Sleep -Seconds 2
        }
    }
}

<#
.SYNOPSIS
    显示进度信息

.DESCRIPTION
    在控制台显示格式化的进度信息

.PARAMETER Current
    当前进度

.PARAMETER Total
    总数

.PARAMETER Message
    进度消息

.EXAMPLE
    Show-Progress -Current 10 -Total 30 -Message "生成标签"
#>
function Show-Progress {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Current,
        
        [Parameter(Mandatory = $true)]
        [int]$Total,
        
        [Parameter(Mandatory = $true)]
        [string]$Message
    )
    
    $percentage = [math]::Round(($Current / $Total) * 100, 1)
    $progressBar = "=" * [math]::Floor($percentage / 2)
    $emptyBar = " " * (50 - [math]::Floor($percentage / 2))
    
    Write-Host "`r  [$progressBar$emptyBar] $percentage% - $Message ($Current/$Total)" -NoNewline
    
    if ($Current -eq $Total) {
        Write-Host ""
    }
}

<#
.SYNOPSIS
    颜色输出函数

.DESCRIPTION
    在控制台输出带颜色的文本

.PARAMETER Message
    要输出的消息

.PARAMETER Color
    文本颜色，默认为 White

.EXAMPLE
    Write-ColorOutput "成功" "Green"
#>
function Write-ColorOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,
        
        [string]$Color = "White"
    )
    
    Write-Host $Message -ForegroundColor $Color
}

<#
.SYNOPSIS
    测试 API 服务可用性

.DESCRIPTION
    通过调用健康检查端点测试服务是否可用

.PARAMETER BaseUrl
    API 基础地址

.EXAMPLE
    Test-ApiService -BaseUrl "http://localhost:8000"
#>
function Test-ApiService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl
    )
    
    try {
        # 尝试调用健康检查端点
        $response = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" `
            -Method Get `
            -TimeoutSec 5 `
            -ErrorAction Stop
        
        return @{
            Available = $true
            Status = $response.status
            Message = "服务正常运行"
        }
    }
    catch {
        return @{
            Available = $false
            Status = $null
            Message = "无法连接到服务: $_"
        }
    }
}

# 导出函数
Export-ModuleMember -Function Invoke-ApiWithRetry, Show-Progress, Write-ColorOutput, Test-ApiService
