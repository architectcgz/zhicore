# Start All ZhiCore Microservices
# This script starts all services in the correct order with proper environment variables
# Services will be started using controlPwshProcess for IDE terminal integration

param(
    [switch]$SkipBuild = $false,
    [switch]$SkipHealthCheck = $false
)

$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "启动 ZhiCore 微服务系统" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check infrastructure services
Write-Host "[步骤 1] 检查基础设施服务..." -ForegroundColor Yellow
Write-Host ""

$InfraServices = @(
    @{ Name = "PostgreSQL"; Port = 5432; Container = "zhicore-postgres" }
    @{ Name = "Redis"; Port = 6379; Container = "zhicore-redis" }
    @{ Name = "MongoDB"; Port = 27017; Container = "zhicore-mongodb" }
    @{ Name = "Nacos"; Port = 8848; Container = "zhicore-nacos" }
    @{ Name = "RocketMQ NameServer"; Port = 9876; Container = "zhicore-rocketmq-namesrv" }
    @{ Name = "RocketMQ Broker"; Port = 10911; Container = "zhicore-rocketmq-broker" }
)

$AllInfraUp = $true
foreach ($Service in $InfraServices) {
    $Container = docker ps --filter "name=$($Service.Container)" --format "{{.Names}}"
    if ($Container -eq $Service.Container) {
        Write-Host "  [通过] $($Service.Name) 正在运行" -ForegroundColor Green
    } else {
        Write-Host "  [失败] $($Service.Name) 未运行" -ForegroundColor Red
        $AllInfraUp = $false
    }
}

if (-not $AllInfraUp) {
    Write-Host ""
    Write-Host "错误: 基础设施服务未运行" -ForegroundColor Red
    Write-Host "请先启动基础设施服务:" -ForegroundColor Yellow
    Write-Host "  cd docker" -ForegroundColor Gray
    Write-Host "  .\start-infrastructure.ps1 -Build" -ForegroundColor Gray
    Write-Host "  或者:" -ForegroundColor Gray
    Write-Host "  docker-compose up -d" -ForegroundColor Gray
    exit 1
}

Write-Host ""

# Step 2: Build services (optional)
if (-not $SkipBuild) {
    Write-Host "[步骤 2] 编译服务..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  执行: mvn clean package -DskipTests" -ForegroundColor Gray
    Write-Host "  这可能需要几分钟..." -ForegroundColor Gray
    Write-Host ""
    
    # Save current directory and change to project root
    $OriginalLocation = Get-Location
    Set-Location -Path "$PSScriptRoot\.."
    
    mvn clean package -DskipTests
    
    # Return to original directory
    Set-Location -Path $OriginalLocation
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "错误: 编译失败" -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
    Write-Host "  [通过] 编译完成" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "[步骤 2] 跳过编译" -ForegroundColor Yellow
    Write-Host ""
}

# Step 3: Check and stop existing services
Write-Host "[步骤 3] 检查并停止已运行的服务..." -ForegroundColor Yellow
Write-Host ""

$ServicePorts = @(8100, 8101, 8102)
$StoppedCount = 0

foreach ($Port in $ServicePorts) {
    $Connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($Connection) {
        $ProcessId = $Connection.OwningProcess | Select-Object -First 1
        $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($Process -and $Process.ProcessName -eq "java") {
            Write-Host "  停止端口 $Port 上的服务 (PID: $ProcessId)..." -ForegroundColor Yellow
            Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
            $StoppedCount++
        }
    }
}

if ($StoppedCount -gt 0) {
    Write-Host "  已停止 $StoppedCount 个服务" -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "  没有需要停止的服务" -ForegroundColor Gray
}

Write-Host ""

# Step 4: Start services
Write-Host "[步骤 4] 启动微服务..." -ForegroundColor Yellow
Write-Host ""

# Get project root directory
$ProjectRoot = Split-Path -Parent $PSScriptRoot

# Service configurations (按依赖顺序启动)
$Services = @(
    @{ 
        Name = "网关服务"
        Module = "zhicore-gateway"
        Port = 8100
        Profile = "dev"
        Priority = 1
    }
    @{ 
        Name = "用户服务"
        Module = "zhicore-user"
        Port = 8101
        Profile = "dev"
        Priority = 2
    }
    @{ 
        Name = "内容服务"
        Module = "zhicore-content"
        Port = 8102
        Profile = "dev"
        Priority = 2
    }
)

Write-Host "服务将在独立的 PowerShell 窗口中启动（最小化）" -ForegroundColor Cyan
Write-Host "窗口会在任务栏中显示，需要查看日志时可以点击打开" -ForegroundColor Cyan
Write-Host ""

$StartedServices = @()

foreach ($Service in $Services | Sort-Object Priority) {
    Write-Host "  启动 $($Service.Name) (端口 $($Service.Port))..." -ForegroundColor Yellow
    
    # Find JAR file
    $JarPath = Join-Path $ProjectRoot "$($Service.Module)\target\$($Service.Module)-1.0.0-SNAPSHOT.jar"
    
    if (-not (Test-Path $JarPath)) {
        Write-Host "    [失败] JAR 文件不存在: $JarPath" -ForegroundColor Red
        Write-Host "    请先运行编译: mvn clean package -DskipTests" -ForegroundColor Yellow
        Write-Host ""
        continue
    }
    
    try {
        # Build PowerShell command to run in new window
        $WindowTitle = "$($Service.Name) - 端口 $($Service.Port)"
        
        # Build Java command with environment variables
        $JavaCommand = "java -jar `"$JarPath`" --spring.profiles.active=$($Service.Profile) --server.port=$($Service.Port)"
        
        # Create a PowerShell script block that will run in the new window
        $ScriptBlock = @"
`$Host.UI.RawUI.WindowTitle = '$WindowTitle'
Write-Host '========================================' -ForegroundColor Cyan
Write-Host '$($Service.Name)' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''
Write-Host '模块: $($Service.Module)' -ForegroundColor Gray
Write-Host '端口: $($Service.Port)' -ForegroundColor Gray
Write-Host '配置: $($Service.Profile)' -ForegroundColor Gray
Write-Host ''
Write-Host '正在启动服务...' -ForegroundColor Yellow
Write-Host ''
`$env:NACOS_GROUP = 'ZHICORE_SERVICE'
$JavaCommand
Write-Host ''
Write-Host '服务已停止' -ForegroundColor Red
Write-Host '按任意键关闭窗口...' -ForegroundColor Gray
`$null = `$Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
"@
        
        # Start service in new PowerShell window (minimized)
        $Process = Start-Process -FilePath "pwsh" `
            -ArgumentList "-NoExit", "-Command", $ScriptBlock `
            -WorkingDirectory $ProjectRoot `
            -WindowStyle Minimized `
            -PassThru
        
        Write-Host "    [已启动] $($Service.Name)" -ForegroundColor Green
        Write-Host "    模块: $($Service.Module)" -ForegroundColor Gray
        Write-Host "    端口: $($Service.Port)" -ForegroundColor Gray
        Write-Host "    PID: $($Process.Id)" -ForegroundColor Gray
        Write-Host "    窗口: 已最小化到任务栏" -ForegroundColor Gray
        Write-Host ""
        
        $StartedServices += @{
            Name = $Service.Name
            Port = $Service.Port
            Module = $Service.Module
            ProcessId = $Process.Id
        }
        
        # Small delay between services
        Start-Sleep -Seconds 2
    }
    catch {
        Write-Host "    [失败] 启动失败: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "所有服务已启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 5: Health check (optional)
if (-not $SkipHealthCheck) {
    Write-Host "[步骤 5] 等待服务健康检查..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "等待 15 秒让服务完成启动..." -ForegroundColor Cyan
    Start-Sleep -Seconds 15
    Write-Host ""
    
    $HealthyCount = 0
    $UnhealthyCount = 0
    
    foreach ($Service in $StartedServices) {
        $HealthUrl = "http://localhost:$($Service.Port)/actuator/health"
        Write-Host "  检查 $($Service.Name)..." -ForegroundColor Gray
        
        try {
            $Response = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 5 -ErrorAction Stop
            if ($Response.StatusCode -eq 200) {
                Write-Host "    [健康] $($Service.Name) 运行正常" -ForegroundColor Green
                $HealthyCount++
            }
        }
        catch {
            Write-Host "    [警告] $($Service.Name) 健康检查失败 (可能仍在启动中)" -ForegroundColor Yellow
            $UnhealthyCount++
        }
    }
    
    Write-Host ""
    Write-Host "健康检查结果: $HealthyCount 个服务健康, $UnhealthyCount 个服务未响应" -ForegroundColor Cyan
    Write-Host ""
}

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "启动完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "服务访问地址:" -ForegroundColor Cyan
Write-Host "  - 网关服务:   http://localhost:8100" -ForegroundColor White
Write-Host "  - 用户服务:   http://localhost:8101" -ForegroundColor White
Write-Host "  - 内容服务:   http://localhost:8102" -ForegroundColor White
Write-Host ""

Write-Host "API 文档地址:" -ForegroundColor Cyan
Write-Host "  - 网关聚合文档: http://localhost:8100/doc.html" -ForegroundColor White
Write-Host "  - 用户服务文档: http://localhost:8101/doc.html" -ForegroundColor White
Write-Host "  - 内容服务文档: http://localhost:8102/doc.html" -ForegroundColor White
Write-Host ""

Write-Host "健康检查命令:" -ForegroundColor Cyan
Write-Host "  Invoke-WebRequest http://localhost:8100/actuator/health" -ForegroundColor Gray
Write-Host "  Invoke-WebRequest http://localhost:8101/actuator/health" -ForegroundColor Gray
Write-Host "  Invoke-WebRequest http://localhost:8102/actuator/health" -ForegroundColor Gray
Write-Host ""

Write-Host "停止所有服务:" -ForegroundColor Yellow
Write-Host "  关闭所有服务窗口，或使用以下命令停止所有 Java 进程:" -ForegroundColor Gray
Write-Host "  Get-Process java | Stop-Process -Force" -ForegroundColor Gray
Write-Host ""
Write-Host "  或者停止特定服务 (使用 PID):" -ForegroundColor Gray
foreach ($Service in $StartedServices) {
    Write-Host "    Stop-Process -Id $($Service.ProcessId) -Force  # $($Service.Name)" -ForegroundColor Gray
}
Write-Host ""

Write-Host "查看服务端口占用:" -ForegroundColor Yellow
Write-Host "  Get-NetTCPConnection -LocalPort 8100,8101,8102 -ErrorAction SilentlyContinue | Select-Object LocalPort,State,OwningProcess" -ForegroundColor Gray
Write-Host ""

Write-Host "提示: 每个服务都在独立的 PowerShell 窗口中运行（已最小化）" -ForegroundColor Yellow
Write-Host "可以从任务栏点击对应窗口查看实时日志输出" -ForegroundColor Yellow
Write-Host "关闭窗口或按 Ctrl+C 可以停止对应的服务" -ForegroundColor Yellow
Write-Host ""

exit 0
