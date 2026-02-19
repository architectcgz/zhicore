# 检查文章列表和详情
# 用于诊断文章不存在的问题

$ApiUrl = "http://localhost:8100"

Write-Host "=== 检查文章列表 ===" -ForegroundColor Cyan

# 1. 获取文章列表
Write-Host "`n1. 获取文章列表..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$ApiUrl/api/v1/posts?page=1&size=10" -Method Get
    
    if ($response.code -eq 200) {
        Write-Host "✓ 成功获取文章列表" -ForegroundColor Green
        Write-Host "  总数: $($response.data.total)" -ForegroundColor Gray
        Write-Host "  当前页文章数: $($response.data.items.Count)" -ForegroundColor Gray
        
        if ($response.data.items.Count -gt 0) {
            Write-Host "`n  文章列表:" -ForegroundColor Gray
            foreach ($post in $response.data.items) {
                Write-Host "    - ID: $($post.id), 标题: $($post.title), 作者: $($post.ownerName)" -ForegroundColor Gray
            }
            
            # 尝试访问第一篇文章的详情
            $firstPostId = $response.data.items[0].id
            Write-Host "`n2. 尝试访问第一篇文章详情 (ID: $firstPostId)..." -ForegroundColor Yellow
            
            try {
                $detailResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/posts/$firstPostId" -Method Get
                
                if ($detailResponse.code -eq 200) {
                    Write-Host "✓ 成功获取文章详情" -ForegroundColor Green
                    Write-Host "  标题: $($detailResponse.data.title)" -ForegroundColor Gray
                    Write-Host "  作者ID: $($detailResponse.data.ownerId)" -ForegroundColor Gray
                    Write-Host "  状态: $($detailResponse.data.status)" -ForegroundColor Gray
                } else {
                    Write-Host "✗ 获取文章详情失败: $($detailResponse.message)" -ForegroundColor Red
                }
            } catch {
                Write-Host "✗ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
            }
        } else {
            Write-Host "  数据库中没有文章" -ForegroundColor Yellow
        }
    } else {
        Write-Host "✗ 获取文章列表失败: $($response.message)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. 尝试访问问题文章 ID
Write-Host "`n3. 尝试访问问题文章 (ID: 149887168121225200)..." -ForegroundColor Yellow
try {
    $problemResponse = Invoke-RestMethod -Uri "$ApiUrl/api/v1/posts/149887168121225200" -Method Get
    
    if ($problemResponse.code -eq 200) {
        Write-Host "✓ 文章存在" -ForegroundColor Green
    } else {
        Write-Host "✗ 文章不存在: $($problemResponse.message)" -ForegroundColor Red
    }
} catch {
    $errorMessage = $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        Write-Host "✗ 文章不存在" -ForegroundColor Red
        Write-Host "  错误详情: $errorBody" -ForegroundColor Gray
    } else {
        Write-Host "✗ 请求失败: $errorMessage" -ForegroundColor Red
    }
}

Write-Host "`n=== 检查完成 ===" -ForegroundColor Cyan
