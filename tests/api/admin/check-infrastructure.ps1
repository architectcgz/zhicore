# Check Infrastructure Services
# This script checks if PostgreSQL and Redis are accessible

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Infrastructure Services Check" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check PostgreSQL
Write-Host "[CHECK 1] PostgreSQL Connection..." -ForegroundColor Yellow
try {
    $PgTest = Test-NetConnection -ComputerName localhost -Port 5432 -WarningAction SilentlyContinue
    if ($PgTest.TcpTestSucceeded) {
        Write-Host "  SUCCESS - PostgreSQL is accessible on port 5432" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - PostgreSQL is NOT accessible on port 5432" -ForegroundColor Red
        Write-Host "  Please start PostgreSQL: docker-compose up -d postgres" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Check Redis
Write-Host "[CHECK 2] Redis Connection..." -ForegroundColor Yellow
try {
    $RedisTest = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
    if ($RedisTest.TcpTestSucceeded) {
        Write-Host "  SUCCESS - Redis is accessible on port 6379" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - Redis is NOT accessible on port 6379" -ForegroundColor Red
        Write-Host "  Please start Redis: docker-compose up -d redis" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Check Nacos
Write-Host "[CHECK 3] Nacos Connection..." -ForegroundColor Yellow
try {
    $NacosTest = Test-NetConnection -ComputerName localhost -Port 8848 -WarningAction SilentlyContinue
    if ($NacosTest.TcpTestSucceeded) {
        Write-Host "  SUCCESS - Nacos is accessible on port 8848" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - Nacos is NOT accessible on port 8848" -ForegroundColor Red
        Write-Host "  Please start Nacos: docker-compose up -d nacos" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Check RocketMQ
Write-Host "[CHECK 4] RocketMQ Connection..." -ForegroundColor Yellow
try {
    $RocketMQTest = Test-NetConnection -ComputerName localhost -Port 9876 -WarningAction SilentlyContinue
    if ($RocketMQTest.TcpTestSucceeded) {
        Write-Host "  SUCCESS - RocketMQ is accessible on port 9876" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - RocketMQ is NOT accessible on port 9876" -ForegroundColor Red
        Write-Host "  Please start RocketMQ: docker-compose up -d rocketmq" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ERROR - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Check ZhiCore-user database exists
Write-Host "[CHECK 5] PostgreSQL Database 'ZhiCore_user'..." -ForegroundColor Yellow
Write-Host "  Attempting to connect to ZhiCore_user database..." -ForegroundColor White

$env:PGPASSWORD = "postgres123456"
try {
    $DbCheck = & psql -h localhost -U postgres -d ZhiCore_user -c "SELECT 1;" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  SUCCESS - Database 'ZhiCore_user' exists and is accessible" -ForegroundColor Green
    } else {
        Write-Host "  FAILED - Database 'ZhiCore_user' does not exist or is not accessible" -ForegroundColor Red
        Write-Host "  Error: $DbCheck" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  To create the database, run:" -ForegroundColor Yellow
        Write-Host "  psql -h localhost -U postgres -c 'CREATE DATABASE ZhiCore_user;'" -ForegroundColor Gray
    }
} catch {
    Write-Host "  WARNING - psql command not found" -ForegroundColor Yellow
    Write-Host "  Cannot verify database existence without psql client" -ForegroundColor Yellow
    Write-Host "  Please ensure PostgreSQL client tools are installed" -ForegroundColor Yellow
}
$env:PGPASSWORD = $null
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Infrastructure Check Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Ensure all infrastructure services are running" -ForegroundColor White
Write-Host "2. Verify database 'ZhiCore_user' exists" -ForegroundColor White
Write-Host "3. Check ZhiCore-user service logs for connection errors" -ForegroundColor White
Write-Host "4. Fix Redis port in application.yml (should be 6379, not 6800)" -ForegroundColor White
