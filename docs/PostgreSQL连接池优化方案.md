# PostgreSQL 连接池耗尽问题解决方案

## 问题描述
```
fail: SelfZhiCore.Services.Impl.AntiSpamService[0]
      检查 Like 操作限制时发生错误: UserId=a2d8c2e8-33a5-4675-9f99-b284f5f0c67e
      System.InvalidOperationException: An exception has been raised that is likely due to a transient failure.
       ---> Npgsql.PostgresException (0x80004005): 53300: sorry, too many clients already
```

**原因分析：**
- 高并发点赞时，每个请求会执行3-4次数据库查询（AntiSpamService.CheckLikeActionAsync）
- 当前连接池配置：MaxPoolSize = 100
- 多用户同时点赞导致连接池快速耗尽

## 解决方案

### 方案1：使用Redis缓存优化（推荐）⭐

**优点：**
- 大幅减少数据库查询，提升性能
- 降低数据库连接消耗
- 响应速度更快

**实现：**
参考 `CheckMessageActionAsync` 的实现，为Like操作添加Redis缓存：

```csharp
// 1. 冷却时间检查用Redis缓存
// 2. 频率限制计数器用Redis存储
// 3. Redis不可用时回退到数据库查询
```

**需要修改的文件：**
1. `RedisKeys.cs` - 添加Like相关的Redis键定义
2. `AntiSpamService.cs` - 重写 `CheckLikeActionAsync` 方法
3. `RecordActionAsync` - 添加Like操作的Redis更新逻辑

---

### 方案2：增加连接池大小（快速缓解）

**配置调整（appsettings.json）：**
```json
"Database": {
  "MinPoolSize": 10,        // 增加最小连接数
  "MaxPoolSize": 300,       // 增加最大连接数（原100）
  "ConnectionTimeout": 30,
  "ConnectionIdleLifetime": 300,
  "Pooling": true,
  "Multiplexing": false     // 考虑启用为true以支持连接复用
}
```

**优点：** 快速实施，立即缓解问题
**缺点：** 治标不治本，高并发时仍可能耗尽

---

### 方案3：优化数据库查询

**当前问题：**
```csharp
// CheckLikeActionAsync 执行了多次独立查询：
// 1. 查询最后取消点赞时间
// 2. 查询1小时内对同一目标的操作次数
// 3. 查询1分钟内点赞次数
// 4. 查询1小时内点赞次数
```

**优化建议：**
1. 使用 `AsNoTracking()` 减少EF Core开销
2. 合并查询减少数据库往返
3. 添加适当的数据库索引

---

### 方案4：PostgreSQL服务器端优化

**调整PostgreSQL配置（postgresql.conf）：**
```ini
max_connections = 500           # 增加最大连接数（默认100）
shared_buffers = 256MB          # 增加共享缓冲区
effective_cache_size = 1GB      # 优化查询计划
work_mem = 8MB                  # 提高排序和哈希操作性能
```

**重启PostgreSQL后生效：**
```bash
# Linux
sudo systemctl restart postgresql

# Windows
# 通过服务管理器重启PostgreSQL服务
```

---

## 推荐实施顺序

1. **立即实施（方案2）：** 增加连接池到200-300，快速缓解问题
2. **短期（方案1）：** 实现Redis缓存，根本解决问题
3. **中期（方案3）：** 优化数据库查询，提升整体性能
4. **长期（方案4）：** 根据业务增长调整PostgreSQL配置

---

## 监控建议

系统已有 `DatabaseConnectionMonitorService` 监控服务，会：
- 每分钟检查连接池使用情况
- 超过80%使用率时发出WARNING
- 超过90%使用率时发出CRITICAL告警并发送邮件

**查看日志命令：**
```bash
# 查看连接池监控日志
grep "数据库连接统计" logs/application.log

# 查看告警信息
grep "数据库连接告警" logs/application.log
```

---

## 相关文件

- 配置文件：`ZhiCore-api/appsettings.json`
- 防刷服务：`ZhiCore-core/Services/Impl/AntiSpamService.cs`
- 数据库配置：`ZhiCore-shared/Config/DatabaseConfig.cs`
- Redis键定义：`ZhiCore-shared/Constants/RedisKeys.cs`
- 监控服务：`ZhiCore-core/Services/Background/DatabaseConnectionMonitorService.cs`
