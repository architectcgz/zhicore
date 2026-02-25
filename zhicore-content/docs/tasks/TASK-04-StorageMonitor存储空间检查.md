# TASK-04: StorageMonitor 实现 PostgreSQL/MongoDB 存储空间检查

## 背景

`StorageMonitor.java` 中 4 处 TODO（第 34、38、53、67 行），PostgreSQL 和 MongoDB 的存储空间检查逻辑均为空实现，仅输出 debug 日志。

## 涉及文件

- `com.zhicore.content.infrastructure.monitoring.StorageMonitor` — 4 处 TODO
- `com.zhicore.content.infrastructure.alert.AlertService` — 已有告警能力
- `com.zhicore.content.infrastructure.alert.AlertType` — 已有 `STORAGE_SPACE_LOW`
- `com.zhicore.content.infrastructure.config.MonitoringProperties` — 阈值配置

## 实现要求

### 1. PostgreSQL 存储空间检查

在 `checkPostgresStorage()` 中：

```sql
SELECT pg_database_size(current_database()) AS db_size;
```

- 通过 `JdbcTemplate` 执行原生 SQL
- 将结果与配置阈值比较
- 超阈值时调用 `AlertService` 发送 `STORAGE_SPACE_LOW` 告警

### 2. MongoDB 存储空间检查

在 `checkMongoStorage()` 中：

```java
Document stats = mongoTemplate.getDb().runCommand(new Document("dbStats", 1));
long dataSize = stats.getLong("dataSize");
long storageSize = stats.getLong("storageSize");
```

- 获取 dataSize 和 storageSize
- 与配置阈值比较，超阈值时触发告警

### 3. 配置项

确认 `MonitoringProperties` 中是否已有以下配置，如无则新增：

```yaml
zhicore:
  monitoring:
    storage:
      postgres-threshold: 10737418240  # 默认 10GB
      mongo-threshold: 10737418240     # 默认 10GB
      enabled: true
```

### 4. 异常处理

- 数据库连接失败时 catch 异常，记录 warn 日志，不中断定时任务
- 避免因监控逻辑异常导致整个 `@Scheduled` 任务挂掉

## 验收标准

- [ ] `checkPostgresStorage()` 能查询实际数据库大小
- [ ] `checkMongoStorage()` 能查询实际 MongoDB 存储大小
- [ ] 超阈值时触发 `STORAGE_SPACE_LOW` 告警
- [ ] 数据库不可达时不抛异常，记录 warn 日志
- [ ] 配置项可通过 application.yml 调整阈值
- [ ] 单元测试：mock 数据库返回值，验证告警触发逻辑

## 回滚策略

- 纯新增逻辑，revert commit 即可
- 可通过配置 `enabled: false` 快速关闭
