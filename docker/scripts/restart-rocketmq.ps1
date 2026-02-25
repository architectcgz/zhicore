# RocketMQ Restart Script with Memory Optimization
# This script restarts RocketMQ services with optimized memory settings

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RocketMQ Memory Optimization" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get script directory and navigate to docker directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerDir = Join-Path (Split-Path -Parent $ScriptDir) ""

# Check current memory usage
Write-Host "[STEP 1] Checking current RocketMQ memory usage..." -ForegroundColor Yellow
$BeforeStats = docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}" | Select-String -Pattern "rocketmq"
Write-Host "Current memory usage:" -ForegroundColor White
$BeforeStats | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
Write-Host ""

# Stop RocketMQ services
Write-Host "[STEP 2] Stopping RocketMQ services..." -ForegroundColor Yellow
docker-compose -f "$DockerDir\docker-compose.yml" stop rocketmq-dashboard rocketmq-broker rocketmq-namesrv
Write-Host "  Services stopped" -ForegroundColor Green
Write-Host ""

# Wait a moment
Write-Host "[STEP 3] Waiting for cleanup..." -ForegroundColor Yellow
Start-Sleep -Seconds 5
Write-Host "  Cleanup complete" -ForegroundColor Green
Write-Host ""

# Start RocketMQ services with new configuration
Write-Host "[STEP 4] Starting RocketMQ services with optimized settings..." -ForegroundColor Yellow
docker-compose -f "$DockerDir\docker-compose.yml" up -d rocketmq-namesrv
Write-Host "  NameServer starting..." -ForegroundColor Gray
Start-Sleep -Seconds 10

docker-compose -f "$DockerDir\docker-compose.yml" up -d rocketmq-broker
Write-Host "  Broker starting..." -ForegroundColor Gray
Start-Sleep -Seconds 15

docker-compose -f "$DockerDir\docker-compose.yml" up -d rocketmq-dashboard
Write-Host "  Dashboard starting..." -ForegroundColor Gray
Start-Sleep -Seconds 5
Write-Host "  All services started" -ForegroundColor Green
Write-Host ""

# Check health status
Write-Host "[STEP 5] Checking service health..." -ForegroundColor Yellow
$HealthCheck = docker ps --filter "name=rocketmq" --format "table {{.Names}}\t{{.Status}}"
Write-Host $HealthCheck
Write-Host ""

# Check new memory usage
Write-Host "[STEP 6] Checking new memory usage..." -ForegroundColor Yellow
Start-Sleep -Seconds 10
$AfterStats = docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}" | Select-String -Pattern "rocketmq"
Write-Host "New memory usage:" -ForegroundColor White
$AfterStats | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Optimization Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Memory Configuration:" -ForegroundColor White
Write-Host "  NameServer: 256MB (Xms256m -Xmx256m)" -ForegroundColor Gray
Write-Host "  Broker:     512MB (Xms512m -Xmx512m -Xmn256m)" -ForegroundColor Gray
Write-Host "  Dashboard:  256MB (Xms128m -Xmx256m)" -ForegroundColor Gray
Write-Host ""
Write-Host "Expected total memory: ~1.17GB (down from 5GB)" -ForegroundColor Green
Write-Host ""
Write-Host "Note: It may take a few minutes for memory usage to stabilize." -ForegroundColor Yellow
Write-Host "Run 'docker stats' to monitor real-time memory usage." -ForegroundColor Yellow
Write-Host ""
