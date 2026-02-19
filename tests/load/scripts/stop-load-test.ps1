# Stop Load Test Script
# This script stops the running JMeter load test

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stopping Load Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Find JMeter processes
$JMeterProcesses = Get-Process | Where-Object { $_.ProcessName -eq "java" -and $_.MainWindowTitle -like "*JMeter*" }

if ($JMeterProcesses) {
    Write-Host "Found $($JMeterProcesses.Count) JMeter process(es)" -ForegroundColor Yellow
    foreach ($Process in $JMeterProcesses) {
        Write-Host "  Stopping process ID: $($Process.Id)" -ForegroundColor White
        Stop-Process -Id $Process.Id -Force
    }
    Write-Host ""
    Write-Host "JMeter processes stopped." -ForegroundColor Green
} else {
    Write-Host "No JMeter processes found." -ForegroundColor Yellow
}

Write-Host ""
exit 0
