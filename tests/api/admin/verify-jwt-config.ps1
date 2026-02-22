# Verify JWT Configuration
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JWT Configuration Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Nacos configuration
Write-Host "[1/2] Checking Nacos configuration..." -ForegroundColor Yellow
try {
    $nacosUrl = "http://localhost:8848/nacos/v1/cs/configs?dataId=common.yml&group=ZhiCore_SERVICE&tenant="
    $response = Invoke-WebRequest -Uri $nacosUrl -UseBasicParsing
    $config = $response.Content
    
    if ($config -match "jwt:") {
        Write-Host "  [SUCCESS] JWT configuration found in Nacos" -ForegroundColor Green
        $jwtSection = $config -split "`n" | Select-String -Pattern "jwt|secret|expiration" -Context 0,2
        $jwtSection | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
    } else {
        Write-Host "  [FAIL] JWT configuration not found in Nacos" -ForegroundColor Red
    }
} catch {
    Write-Host "  [WARN] Could not access Nacos: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Check service health
Write-Host ""
Write-Host "[2/2] Checking service health..." -ForegroundColor Yellow
$services = @(
    @{Name="ZhiCore-user"; Port=8081},
    @{Name="ZhiCore-post"; Port=8082}
)

foreach ($service in $services) {
    try {
        $healthUrl = "http://localhost:$($service.Port)/actuator/health"
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2
        $health = $response.Content | ConvertFrom-Json
        if ($health.status -eq "UP") {
            Write-Host "  [PASS] $($service.Name) is UP" -ForegroundColor Green
        } else {
            Write-Host "  [WARN] $($service.Name) status: $($health.status)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [FAIL] $($service.Name) is DOWN or not responding" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verification Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
