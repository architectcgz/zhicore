# Check ZhiCore-admin service logs for Feign errors

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Checking ZhiCore-admin Service Logs" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Please check the ZhiCore-admin service console/logs for:" -ForegroundColor Yellow
Write-Host "1. Feign client errors" -ForegroundColor Gray
Write-Host "2. Service discovery errors" -ForegroundColor Gray
Write-Host "3. Nacos registration status" -ForegroundColor Gray
Write-Host "4. Circuit breaker status" -ForegroundColor Gray
Write-Host ""

Write-Host "Common issues to look for:" -ForegroundColor Yellow
Write-Host "- 'No instances available for ZhiCore-user'" -ForegroundColor Gray
Write-Host "- 'Connection refused'" -ForegroundColor Gray
Write-Host "- 'Sentinel blocked'" -ForegroundColor Gray
Write-Host "- 'Feign.RetryableException'" -ForegroundColor Gray
Write-Host ""

Write-Host "Checking Nacos service registration..." -ForegroundColor Yellow
Write-Host "Nacos Console: http://localhost:8848/nacos" -ForegroundColor Cyan
Write-Host "Username: nacos" -ForegroundColor Gray
Write-Host "Password: nacos" -ForegroundColor Gray
Write-Host ""

Write-Host "Expected services in ZhiCore_SERVICE group:" -ForegroundColor Yellow
Write-Host "- ZhiCore-user (port 8081)" -ForegroundColor Gray
Write-Host "- ZhiCore-admin (port 8090)" -ForegroundColor Gray
Write-Host "- ZhiCore-gateway (port 8000)" -ForegroundColor Gray
Write-Host ""

Write-Host "If ZhiCore-admin cannot find ZhiCore-user:" -ForegroundColor Yellow
Write-Host "1. Check if both services are in the same Nacos group (ZhiCore_SERVICE)" -ForegroundColor Gray
Write-Host "2. Check if ZhiCore-admin has spring-cloud-starter-alibaba-nacos-discovery dependency" -ForegroundColor Gray
Write-Host "3. Check if @EnableDiscoveryClient or @EnableFeignClients is present" -ForegroundColor Gray
Write-Host "4. Restart ZhiCore-admin service" -ForegroundColor Gray
Write-Host ""
