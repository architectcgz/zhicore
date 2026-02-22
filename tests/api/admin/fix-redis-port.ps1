# Fix Redis Port Configuration
# This script fixes the Redis port from 6800 to 6379 in Nacos configuration

param(
    [string]$NacosUrl = "http://localhost:8848",
    [string]$Username = "nacos",
    [string]$Password = "nacos"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Fix Redis Port Configuration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Issue: Redis port is configured as 6800, but should be 6379" -ForegroundColor Yellow
Write-Host ""

# Read current common.yml
$ConfigPath = "../../config/nacos/common.yml"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath

if (-not (Test-Path $ConfigFullPath)) {
    Write-Host "ERROR - Config file not found: $ConfigFullPath" -ForegroundColor Red
    exit 1
}

Write-Host "[STEP 1] Reading current configuration..." -ForegroundColor Yellow
$ConfigContent = Get-Content $ConfigFullPath -Raw
Write-Host "  Configuration file loaded" -ForegroundColor Green
Write-Host ""

# Fix Redis port
Write-Host "[STEP 2] Fixing Redis port (6800 -> 6379)..." -ForegroundColor Yellow
$FixedContent = $ConfigContent -replace 'port: \$\{REDIS_PORT:6800\}', 'port: ${REDIS_PORT:6379}'

if ($FixedContent -eq $ConfigContent) {
    Write-Host "  WARNING - No changes needed (port already correct or pattern not found)" -ForegroundColor Yellow
} else {
    Write-Host "  Redis port fixed in configuration" -ForegroundColor Green
}
Write-Host ""

# Save fixed configuration
Write-Host "[STEP 3] Saving fixed configuration..." -ForegroundColor Yellow
Set-Content -Path $ConfigFullPath -Value $FixedContent -Encoding UTF8 -NoNewline
Write-Host "  Configuration saved to: $ConfigFullPath" -ForegroundColor Green
Write-Host ""

# Upload to Nacos
Write-Host "[STEP 4] Uploading to Nacos..." -ForegroundColor Yellow

try {
    $Body = @{
        dataId = "common.yml"
        group = "ZhiCore_SERVICE"
        content = $FixedContent
        type = "yaml"
    }
    
    $Response = Invoke-WebRequest -Uri "$NacosUrl/nacos/v1/cs/configs?username=$Username&password=$Password" `
        -Method POST `
        -Body $Body `
        -ContentType "application/x-www-form-urlencoded" `
        -ErrorAction Stop
    
    if ($Response.StatusCode -eq 200 -and $Response.Content -eq "true") {
        Write-Host "  SUCCESS - Configuration uploaded to Nacos" -ForegroundColor Green
    } else {
        Write-Host "  WARNING - Upload response: $($Response.Content)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR - Failed to upload to Nacos" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Please upload manually via Nacos console:" -ForegroundColor Yellow
    Write-Host "  1. Open http://localhost:8848/nacos" -ForegroundColor White
    Write-Host "  2. Go to Configuration Management -> Configurations" -ForegroundColor White
    Write-Host "  3. Edit 'common.yml' in group 'ZhiCore_SERVICE'" -ForegroundColor White
    Write-Host "  4. Change Redis port from 6800 to 6379" -ForegroundColor White
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Fix Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Restart ZhiCore-user service to pick up new configuration" -ForegroundColor White
Write-Host "2. Verify Redis connection: Test-NetConnection localhost -Port 6379" -ForegroundColor White
Write-Host "3. Re-run the admin API tests" -ForegroundColor White
