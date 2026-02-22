# Check ZhiCore-user service logs for errors
Write-Host "Checking ZhiCore-user service logs..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Please check the ZhiCore-user service console output for errors related to:" -ForegroundColor Yellow
Write-Host "  1. User disable/enable operations" -ForegroundColor Yellow
Write-Host "  2. Database update operations" -ForegroundColor Yellow
Write-Host "  3. MyBatis mapper errors" -ForegroundColor Yellow
Write-Host ""
Write-Host "Look for error messages containing:" -ForegroundColor Yellow
Write-Host "  - 'updateById'" -ForegroundColor Yellow
Write-Host "  - 'UserMapper'" -ForegroundColor Yellow
Write-Host "  - 'SQLException'" -ForegroundColor Yellow
Write-Host "  - 'is_active'" -ForegroundColor Yellow
Write-Host ""
Write-Host "Also check ZhiCore-post service logs for:" -ForegroundColor Yellow
Write-Host "  - 'selectByConditions'" -ForegroundColor Yellow
Write-Host "  - 'PostMapper'" -ForegroundColor Yellow
Write-Host "  - 'status'" -ForegroundColor Yellow
Write-Host "  - 'CAST'" -ForegroundColor Yellow
