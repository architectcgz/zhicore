# 获取一个有效的文章 ID
$ApiUrl = "http://localhost:8100"

try {
    $response = Invoke-RestMethod -Uri "$ApiUrl/api/v1/posts?page=1&size=1" -Method Get
    
    if ($response.code -eq 200 -and $response.data.items.Count -gt 0) {
        $postId = $response.data.items[0].id
        $title = $response.data.items[0].title
        
        Write-Host "有效的文章 ID: $postId" -ForegroundColor Green
        Write-Host "文章标题: $title" -ForegroundColor Gray
        Write-Host "`n访问链接: http://localhost:8100/api/v1/posts/$postId" -ForegroundColor Cyan
    } else {
        Write-Host "没有找到文章" -ForegroundColor Yellow
    }
} catch {
    Write-Host "请求失败: $($_.Exception.Message)" -ForegroundColor Red
}
