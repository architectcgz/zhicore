# 文章互动数据生成指南

本文档说明如何生成文章互动数据（浏览、点赞、收藏）。

## 概述

文章互动数据生成分为三个部分：

1. **文章统计初始化** - 为所有文章创建 post_stats 记录（SQL）
2. **浏览记录生成** - 为已发布文章生成浏览数（SQL）
3. **点赞记录生成** - 为已发布文章生成点赞记录（PowerShell + API）
4. **收藏记录生成** - 为已发布文章生成收藏记录（PowerShell + API）

## 前置条件

在执行互动数据生成之前，请确保：

1. ✅ PostgreSQL 服务已启动（端口 5432）
2. ✅ ZhiCore-gateway 服务已启动（端口 8000）
3. ✅ 已执行用户数据生成脚本
4. ✅ 已执行文章数据生成脚本
5. ✅ 网络连接正常

## 执行步骤

### 步骤 1: 初始化文章统计

为所有文章创建 post_stats 记录：

```powershell
# 设置数据库密码
$env:PGPASSWORD="postgres123456"

# 执行 SQL 脚本
psql -h localhost -p 5432 -U postgres -d ZhiCore -f ..\sql\init-post-stats.sql
```

**预期结果**：
- 为每篇文章创建一条 post_stats 记录
- 所有统计字段初始化为 0

### 步骤 2: 生成浏览记录

为已发布文章生成浏览数：

```powershell
# 执行 SQL 脚本
psql -h localhost -p 5432 -U postgres -d ZhiCore -f ..\sql\generate-post-views.sql
```

**预期结果**：
- 每篇已发布文章的 view_count 设置为 10-100 之间的随机数
- 草稿和定时发布文章的 view_count 保持为 0

### 步骤 3: 生成点赞记录

为已发布文章生成点赞记录：

```powershell
# 使用默认配置
.\Generate-PostLikes.ps1

# 或使用自定义配置
.\Generate-PostLikes.ps1 -MinLikes 10 -MaxLikes 100

# 预览模式（不实际创建）
.\Generate-PostLikes.ps1 -DryRun
```

**预期结果**：
- 每篇已发布文章获得 5-50 个点赞（默认配置）
- post_stats 表的 like_count 自动更新
- 同一用户不会重复点赞同一篇文章

### 步骤 4: 生成收藏记录

为已发布文章生成收藏记录：

```powershell
# 使用默认配置
.\Generate-PostFavorites.ps1

# 或使用自定义配置
.\Generate-PostFavorites.ps1 -MinFavorites 5 -MaxFavorites 50

# 预览模式（不实际创建）
.\Generate-PostFavorites.ps1 -DryRun
```

**预期结果**：
- 每篇已发布文章获得 2-30 个收藏（默认配置）
- post_stats 表的 favorite_count 自动更新
- 同一用户不会重复收藏同一篇文章

## 一键执行所有步骤

创建一个批处理脚本来执行所有步骤：

```powershell
# Generate-AllInteractions.ps1

# 设置数据库密码
$env:PGPASSWORD="postgres123456"

Write-Host "步骤 1: 初始化文章统计..." -ForegroundColor Cyan
psql -h localhost -p 5432 -U postgres -d ZhiCore -f ..\sql\init-post-stats.sql

Write-Host "`n步骤 2: 生成浏览记录..." -ForegroundColor Cyan
psql -h localhost -p 5432 -U postgres -d ZhiCore -f ..\sql\generate-post-views.sql

Write-Host "`n步骤 3: 生成点赞记录..." -ForegroundColor Cyan
.\Generate-PostLikes.ps1

Write-Host "`n步骤 4: 生成收藏记录..." -ForegroundColor Cyan
.\Generate-PostFavorites.ps1

Write-Host "`n✓ 所有互动数据生成完成！" -ForegroundColor Green
```

## 配置参数

### 默认配置（test-data-config.json）

```json
{
  "interactions": {
    "viewsPerPost": {
      "min": 10,
      "max": 100
    },
    "likesPerPost": {
      "min": 5,
      "max": 50
    },
    "favoritesPerPost": {
      "min": 2,
      "max": 30
    }
  }
}
```

### 自定义配置

可以通过命令行参数覆盖默认配置：

```powershell
# 自定义点赞数量范围
.\Generate-PostLikes.ps1 -MinLikes 10 -MaxLikes 100

# 自定义收藏数量范围
.\Generate-PostFavorites.ps1 -MinFavorites 5 -MaxFavorites 50

# 使用自定义配置文件
.\Generate-PostLikes.ps1 -ConfigPath ".\custom-config.json"
```

## 数据验证

### 验证文章统计

```sql
-- 检查 post_stats 记录数
SELECT COUNT(*) as stats_count FROM post_stats;

-- 检查文章总数
SELECT COUNT(*) as post_count FROM posts;

-- 两者应该相等
```

### 验证浏览数

```sql
-- 检查已发布文章的浏览数范围
SELECT 
    MIN(view_count) as min_views,
    MAX(view_count) as max_views,
    ROUND(AVG(view_count), 2) as avg_views
FROM post_stats ps
JOIN posts p ON ps.post_id = p.id
WHERE p.status = 1;  -- 1 = PUBLISHED

-- 预期结果：min_views >= 10, max_views <= 100
```

### 验证点赞数

```sql
-- 检查点赞记录总数
SELECT COUNT(*) as total_likes FROM post_likes;

-- 检查每篇文章的点赞数
SELECT 
    p.id,
    p.title,
    ps.like_count as stats_count,
    COUNT(pl.user_id) as actual_count
FROM posts p
JOIN post_stats ps ON ps.post_id = p.id
LEFT JOIN post_likes pl ON pl.post_id = p.id
WHERE p.status = 1
GROUP BY p.id, p.title, ps.like_count
HAVING ps.like_count != COUNT(pl.user_id);

-- 如果有结果，说明统计不一致
```

### 验证收藏数

```sql
-- 检查收藏记录总数
SELECT COUNT(*) as total_favorites FROM post_favorites;

-- 检查每篇文章的收藏数
SELECT 
    p.id,
    p.title,
    ps.favorite_count as stats_count,
    COUNT(pf.user_id) as actual_count
FROM posts p
JOIN post_stats ps ON ps.post_id = p.id
LEFT JOIN post_favorites pf ON pf.post_id = p.id
WHERE p.status = 1
GROUP BY p.id, p.title, ps.favorite_count
HAVING ps.favorite_count != COUNT(pf.user_id);

-- 如果有结果，说明统计不一致
```

### 验证唯一性约束

```sql
-- 检查是否有重复点赞
SELECT post_id, user_id, COUNT(*) as count
FROM post_likes
GROUP BY post_id, user_id
HAVING COUNT(*) > 1;

-- 检查是否有重复收藏
SELECT post_id, user_id, COUNT(*) as count
FROM post_favorites
GROUP BY post_id, user_id
HAVING COUNT(*) > 1;

-- 两个查询都应该返回空结果
```

## 常见问题

### 问题 1: psql 命令未找到

**错误信息**：
```
psql : 无法将"psql"项识别为 cmdlet、函数、脚本文件或可运行程序的名称。
```

**解决方案**：
将 PostgreSQL 的 bin 目录添加到系统 PATH 环境变量：
```
C:\Program Files\PostgreSQL\16\bin
```

### 问题 2: API 服务不可用

**错误信息**：
```
✗ API 服务不可用
```

**解决方案**：
1. 确认 ZhiCore-gateway 服务已启动
2. 检查端口 8000 是否被占用
3. 验证服务健康状态：
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8000/actuator/health"
   ```

### 问题 3: 未找到已发布文章

**错误信息**：
```
✗ 获取已发布文章失败: 未找到已发布文章
```

**解决方案**：
1. 确认已执行文章数据生成脚本
2. 检查数据库中是否有已发布文章：
   ```sql
   SELECT COUNT(*) FROM posts WHERE status = 1;
   ```
3. 如果没有，重新执行文章生成脚本

### 问题 4: 点赞/收藏失败

**错误信息**：
```
⚠ 为文章 XXX 添加点赞失败
```

**可能原因**：
1. 网络连接问题
2. API 服务异常
3. 数据库连接问题

**解决方案**：
1. 检查网络连接
2. 查看 API 服务日志
3. 重新执行脚本（脚本会自动跳过已存在的记录）

## 性能优化

### 批量处理

对于大量数据，可以考虑：

1. **增加 API 超时时间**：
   ```powershell
   # 在 ApiHelper.psm1 中修改
   TimeoutSec = 60  # 默认 30 秒
   ```

2. **减少重试次数**：
   ```powershell
   # 在脚本中修改
   MaxRetries = 2  # 默认 3 次
   ```

3. **分批执行**：
   ```powershell
   # 只处理前 50 篇文章
   $posts = $posts[0..49]
   ```

## 清理数据

如果需要重新生成互动数据：

```sql
-- 清理点赞记录
DELETE FROM post_likes;

-- 清理收藏记录
DELETE FROM post_favorites;

-- 重置统计数据
UPDATE post_stats SET 
    view_count = 0,
    like_count = 0,
    favorite_count = 0;
```

## 相关文档

- [测试数据生成总览](../README.md)
- [用户数据生成指南](../sql/README.md)
- [文章数据生成指南](./README-POSTS.md)
- [API 测试规范](../../../../.kiro/steering/10-api-testing.md)

## 需求验证

本模块实现以下需求：

- ✅ Requirements 5.1: 为每篇文章创建 post_stats 记录
- ✅ Requirements 5.2: 为每篇已发布文章生成 10-100 次浏览记录
- ✅ Requirements 5.3: 为每篇已发布文章生成 5-50 个点赞记录
- ✅ Requirements 5.4: 确保 post_id 和 user_id 都是有效的 ID
- ✅ Requirements 5.5: 更新 post_stats 表的 like_count
- ✅ Requirements 5.6: 为每篇已发布文章生成 2-30 个收藏记录
- ✅ Requirements 5.7: 确保 post_id 和 user_id 都是有效的 ID
- ✅ Requirements 5.8: 更新 post_stats 表的 favorite_count
- ✅ Requirements 5.9: 确保同一用户不会重复点赞或收藏同一篇文章

---

**最后更新**: 2026-02-13
