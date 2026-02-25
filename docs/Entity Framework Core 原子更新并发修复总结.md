# Entity Framework Core 原子更新并发修复总结

## 问题背景

在多用户并发环境下，传统的"读取-修改-写入"模式存在数据竞争问题：

```csharp
// ❌ 并发不安全的代码
var stats = await GetStatsAsync(postId);
stats.LikeCount += 1;  // 竞争条件：多个线程可能读取相同的值
await SaveChangesAsync();
```

## EF Core 7+ 原子更新解决方案

### 1. ExecuteUpdateAsync 的优势

✅ **原子性**：单条SQL语句执行，避免竞争条件  
✅ **类型安全**：编译时检查，避免SQL注入  
✅ **性能优化**：直接在数据库层面执行，减少网络往返  
✅ **简洁易读**：LINQ语法，易于维护  

### 2. 修复的服务

#### PostStatsService 修复

**修复前**：
```csharp
public async Task<int> IncrementLikeCountAsync(long postId, int increment = 1)
{
    var stats = await GetOrCreateStatsAsync(postId);  // 读取
    stats.LikeCount += increment;                     // 修改
    await dbContext.SaveChangesAsync();              // 写入 - 竞争条件！
    return stats.LikeCount;
}
```

**修复后**：
```csharp
public async Task<int> IncrementLikeCountAsync(long postId, int increment = 1)
{
    // 原子更新 - 并发安全
    var updateResult = await dbContext.PostStats
        .Where(ps => ps.PostId == postId)
        .ExecuteUpdateAsync(s => s
            .SetProperty(ps => ps.LikeCount, ps => ps.LikeCount + increment)
            .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
    
    // 处理记录不存在的情况
    if (updateResult == 0)
    {
        var stats = await GetOrCreateStatsAsync(postId);
        stats.LikeCount = increment;
        await dbContext.SaveChangesAsync();
        return stats.LikeCount;
    }
    
    // 获取更新后的值
    return await dbContext.PostStats
        .Where(ps => ps.PostId == postId)
        .Select(ps => ps.LikeCount)
        .FirstOrDefaultAsync();
}
```

#### UnifiedUnreadCountService 修复

**修复前**：使用原生SQL
```csharp
private async Task IncrementUnreadCountAtomicAsync(string userId, int notificationType, int count)
{
    var updateSql = @"UPDATE ""UserUnreadCounts"" SET ""SystemUnreadCount"" = ""SystemUnreadCount"" + {1}...";
    await _dbContext.Database.ExecuteSqlRawAsync(updateSql, userId, count);
}
```

**修复后**：使用EF Core原子更新
```csharp
private async Task<int> UpdateSystemCountAsync(string userId, int count, bool isIncrement)
{
    return await _dbContext.UserUnreadCounts
        .Where(u => u.UserId == userId)
        .ExecuteUpdateAsync(s => s
            .SetProperty(u => u.SystemUnreadCount, u => isIncrement 
                ? u.SystemUnreadCount + count 
                : (u.SystemUnreadCount >= count ? u.SystemUnreadCount - count : 0))
            .SetProperty(u => u.TotalUnreadCount, u => 
                u.PersonalUnreadCount + u.ChatUnreadCount + u.CommentUnreadCount + 
                u.MentionUnreadCount + u.InteractionUnreadCount + u.SystemUnreadCount + u.AssistantUnreadCount)
            .SetProperty(u => u.UpdateTime, DateTimeOffset.UtcNow));
}
```

### 3. 核心特性

#### 防止负数
```csharp
.SetProperty(ps => ps.LikeCount, ps => ps.LikeCount >= decrement ? ps.LikeCount - decrement : 0)
```

#### 同时更新多个字段
```csharp
.ExecuteUpdateAsync(s => s
    .SetProperty(ps => ps.LikeCount, ps => ps.LikeCount + increment)
    .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow))
```

#### 条件更新
```csharp
.Where(ps => ps.PostId == postId && ps.IsActive)
.ExecuteUpdateAsync(...)
```

### 4. 生成的SQL示例

EF Core会生成高效的SQL：
```sql
UPDATE "PostStats" 
SET "LikeCount" = "LikeCount" + @p0, 
    "UpdateTime" = @p1 
WHERE "PostId" = @p2
```

### 5. 性能对比

| 方法 | 数据库往返 | 并发安全 | 性能 |
|------|------------|----------|------|
| 传统方式 | 2次 (SELECT + UPDATE) | ❌ | 慢 |
| ExecuteUpdateAsync | 1次 (UPDATE) | ✅ | 快 |

### 6. 最佳实践

#### ✅ 推荐做法
```csharp
// 1. 使用原子更新
await context.Stats
    .Where(s => s.Id == id)
    .ExecuteUpdateAsync(s => s.SetProperty(x => x.Count, x => x.Count + 1));

// 2. 处理记录不存在的情况
if (updateResult == 0)
{
    // 创建新记录的逻辑
}

// 3. 防止负数
.SetProperty(x => x.Count, x => x.Count >= decrement ? x.Count - decrement : 0)
```

#### ❌ 避免的做法
```csharp
// 1. 读取-修改-写入模式
var entity = await context.FindAsync(id);
entity.Count += 1;
await context.SaveChangesAsync();

// 2. 不处理并发冲突
// 3. 不验证业务规则（如负数）
```

### 7. 适用场景

✅ **适合使用 ExecuteUpdateAsync**：
- 计数器更新（点赞、浏览、评论数）
- 状态字段更新
- 时间戳更新
- 简单的数值计算

❌ **不适合的场景**：
- 复杂的业务逻辑验证
- 需要触发EF Core事件的更新
- 需要更新导航属性的场景

### 8. 注意事项

1. **EF Core版本**：需要EF Core 7.0+
2. **事务支持**：ExecuteUpdateAsync支持事务
3. **变更跟踪**：不会更新内存中的实体
4. **触发器**：数据库触发器仍会执行
5. **并发令牌**：支持乐观并发控制

### 9. 错误处理

```csharp
try
{
    var updateResult = await context.Stats
        .Where(s => s.Id == id)
        .ExecuteUpdateAsync(s => s.SetProperty(x => x.Count, x => x.Count + 1));
    
    if (updateResult == 0)
    {
        // 记录不存在或条件不匹配
        throw new EntityNotFoundException();
    }
}
catch (DbUpdateConcurrencyException)
{
    // 处理并发冲突
}
```

## 总结

通过使用EF Core的`ExecuteUpdateAsync`，我们成功解决了以下并发问题：

1. **PostStatsService**：文章统计数据的并发更新
2. **UnifiedUnreadCountService**：用户未读计数的并发更新

这种方法比原生SQL更安全、更易维护，同时保持了高性能和并发安全性。
