# =====================================================
# Database Helper Module
# 数据库辅助模块
# 
# 说明：此模块提供数据库查询相关的工具函数
# 功能：
# 1. 通过 Docker 执行 PostgreSQL 查询
# 2. 获取测试用户
# 3. 获取文章数据
# 4. 获取其他基础数据
# =====================================================

<#
.SYNOPSIS
    执行 PostgreSQL 查询

.DESCRIPTION
    通过 Docker exec 执行 psql 查询

.PARAMETER Database
    数据库名称

.PARAMETER Query
    SQL 查询语句

.PARAMETER Container
    Docker 容器名称，默认为 ZhiCore-postgres

.EXAMPLE
    Invoke-PostgresQuery -Database "ZhiCore_user" -Query "SELECT id, username FROM users"
#>
function Invoke-PostgresQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Database,
        
        [Parameter(Mandatory = $true)]
        [string]$Query,
        
        [string]$Container = "ZhiCore-postgres"
    )
    
    try {
        $output = docker exec -i $Container psql -U postgres -d $Database -t -A -F "," -c $Query 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            throw "SQL 查询失败: $output"
        }
        
        return $output
    }
    catch {
        throw "执行数据库查询失败: $_"
    }
}

<#
.SYNOPSIS
    从数据库获取测试用户

.DESCRIPTION
    查询所有测试用户的 ID 和用户名

.EXAMPLE
    Get-TestUsersFromDB
#>
function Get-TestUsersFromDB {
    Write-Host "  从数据库查询测试用户..." -ForegroundColor Gray
    
    try {
        $query = "SELECT id, username FROM users WHERE username LIKE 'test_%' ORDER BY id"
        $output = Invoke-PostgresQuery -Database "ZhiCore_user" -Query $query
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    username = $values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-Host "✓ 获取到 $($results.Count) 个测试用户" -ForegroundColor Green
            return $results
        }
        else {
            throw "未找到测试用户"
        }
    }
    catch {
        throw "获取测试用户失败: $_"
    }
}

<#
.SYNOPSIS
    从数据库获取已发布文章

.DESCRIPTION
    查询所有已发布的文章

.EXAMPLE
    Get-PublishedPostsFromDB
#>
function Get-PublishedPostsFromDB {
    Write-Host "  从数据库查询已发布文章..." -ForegroundColor Gray
    
    try {
        $query = "SELECT id, owner_id FROM posts WHERE status = 1 ORDER BY id"
        $output = Invoke-PostgresQuery -Database "ZhiCore_post" -Query $query
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    owner_id = [long]$values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-Host "✓ 获取到 $($results.Count) 篇已发布文章" -ForegroundColor Green
            return $results
        }
        else {
            throw "未找到已发布文章"
        }
    }
    catch {
        throw "获取已发布文章失败: $_"
    }
}

<#
.SYNOPSIS
    从数据库获取管理员用户

.DESCRIPTION
    查询所有管理员用户

.EXAMPLE
    Get-AdminUsersFromDB
#>
function Get-AdminUsersFromDB {
    Write-Host "  从数据库查询管理员用户..." -ForegroundColor Gray
    
    try {
        $query = @"
SELECT DISTINCT u.id, u.username 
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE r.name = 'ADMIN' AND u.username LIKE 'test_%'
ORDER BY u.id
"@
        $output = Invoke-PostgresQuery -Database "ZhiCore_user" -Query $query
        
        $results = @()
        $lines = $output -split "`n" | Where-Object { $_ -ne "" }
        
        foreach ($line in $lines) {
            $values = $line -split ","
            if ($values.Count -ge 2) {
                $results += [PSCustomObject]@{
                    id = [long]$values[0]
                    username = $values[1]
                }
            }
        }
        
        if ($results.Count -gt 0) {
            Write-Host "✓ 获取到 $($results.Count) 个管理员用户" -ForegroundColor Green
            return $results
        }
        else {
            throw "未找到管理员用户"
        }
    }
    catch {
        throw "获取管理员用户失败: $_"
    }
}

# 导出函数
Export-ModuleMember -Function Invoke-PostgresQuery, Get-TestUsersFromDB, Get-PublishedPostsFromDB, Get-AdminUsersFromDB
