# Check Nacos Service Registration

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Checking Nacos Service Registration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$NacosUrl = "http://localhost:8848"
$NacosUsername = "nacos"
$NacosPassword = "nacos"

Write-Host "Logging in to Nacos..." -ForegroundColor Yellow

try {
    # Login to Nacos to get access token
    $LoginBody = "username=$NacosUsername&password=$NacosPassword"
    $LoginResponse = Invoke-WebRequest -Uri "$NacosUrl/nacos/v1/auth/login" -Method POST -Body $LoginBody -ContentType "application/x-www-form-urlencoded"
    $LoginData = $LoginResponse.Content | ConvertFrom-Json
    $AccessToken = $LoginData.accessToken
    
    Write-Host "  [PASS] Logged in to Nacos" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "Checking Nacos API..." -ForegroundColor Yellow
    
    # Get service list from Nacos
    $Response = Invoke-WebRequest -Uri "$NacosUrl/nacos/v1/ns/service/list?pageNo=1&pageSize=100&groupName=ZhiCore_SERVICE&accessToken=$AccessToken" -Method GET
    $Data = $Response.Content | ConvertFrom-Json
    
    Write-Host "  [PASS] Nacos API accessible" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "Services in ZhiCore_SERVICE group:" -ForegroundColor Yellow
    
    $Services = $Data.doms
    
    $RequiredServices = @("ZhiCore-user", "ZhiCore-admin", "ZhiCore-gateway", "ZhiCore-post", "ZhiCore-comment")
    
    foreach ($ServiceName in $RequiredServices) {
        if ($Services -contains $ServiceName) {
            Write-Host "  [PASS] $ServiceName is registered" -ForegroundColor Green
            
            # Get service details
            try {
                $DetailResponse = Invoke-WebRequest -Uri "$NacosUrl/nacos/v1/ns/instance/list?serviceName=$ServiceName&groupName=ZhiCore_SERVICE&accessToken=$AccessToken" -Method GET
                $DetailData = $DetailResponse.Content | ConvertFrom-Json
                
                if ($DetailData.hosts -and $DetailData.hosts.Count -gt 0) {
                    foreach ($Instance in $DetailData.hosts) {
                        $HealthStatus = if ($Instance.healthy) { "HEALTHY" } else { "UNHEALTHY" }
                        Write-Host "    Instance: $($Instance.ip):$($Instance.port) - $HealthStatus" -ForegroundColor Gray
                    }
                }
            } catch {
                Write-Host "    [WARN] Could not get instance details" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  [FAIL] $ServiceName is NOT registered" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    Write-Host "All registered services:" -ForegroundColor Yellow
    foreach ($Service in $Services) {
        Write-Host "  - $Service" -ForegroundColor Gray
    }
    
} catch {
    Write-Host "  [FAIL] Cannot connect to Nacos" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please ensure:" -ForegroundColor Yellow
    Write-Host "  1. Nacos is running on port 8848" -ForegroundColor Gray
    Write-Host "  2. Services are configured to register with Nacos" -ForegroundColor Gray
    Write-Host "  3. bootstrap.yml files are present in all services" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
