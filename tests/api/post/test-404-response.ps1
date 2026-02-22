# 测试文章不存在时是否返回 404
# 验证修复：文章不存在应该返回 404 而不是 500

$ApiUrl = "http://localhost:8100"
$NonExistentPostId = "149887168121225200"

Write-Host "=== 测试文章不存在返回 404 ===" -ForegroundColor Cyan
Write-Host "测试文章 ID: $NonExistentPostId" -ForegroundColor Gray

try {
    $response = Invoke-WebRequest -Uri "$ApiUrl/api/v1/posts/$NonExistentPostId" -Method Get -ErrorAction Stop
    
    Write-Host "✗ 测试失败：应该返回 404，但请求成功了" -ForegroundColor Red
    Write-Host "  状态码: $($response.StatusCode)" -ForegroundColor Gray
    
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    
    if ($statusCode -eq 404) {
        Write-Host "✓ 测试通过：正确返回 404 Not Found" -ForegroundColor Green
        Write-Host "  状态码: $statusCode" -ForegroundColor Gray
    } elseif ($statusCode -eq 500) {
        Write-Host "✗ 测试失败：返回了 500 而不是 404" -ForegroundColor Red
        Write-Host "  状态码: $statusCode" -ForegroundColor Gray
        Write-Host "  这是修复前的行为，需要重启服务应用修复" -ForegroundColor Yellow
    } else {
        Write-Host "✗ 测试失败：返回了意外的状态码" -ForegroundColor Red
        Write-Host "  状态码: $statusCode" -ForegroundColor Gray
    }
    
    # 尝试读取响应体
    try {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        $reader.Close()
        
        $jsonResponse = $responseBody | ConvertFrom-Json
        Write-Host "`n响应内容:" -ForegroundColor Gray
        Write-Host "  Code: $($jsonResponse.code)" -ForegroundColor Gray
        Write-Host "  Message: $($jsonResponse.message)" -ForegroundColor Gray
    } catch {
        # 无法读取响应体
    }
}

Write-Host "`n=== 测试完成 ===" -ForegroundColor Cyan
Write-Host "注意：如果测试失败，需要重新编译并重启 ZhiCore-post 服务" -ForegroundColor Yellow
