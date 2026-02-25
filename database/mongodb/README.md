# MongoDB 数据库脚本

本目录包含 MongoDB 数据库的初始化脚本和维护脚本。

## 目录结构

```
mongodb/
├── README.md                    # 本文档
└── init-ranking-indexes.js      # 排行榜归档索引初始化脚本
```

## 排行榜归档索引初始化

### 脚本说明

`init-ranking-indexes.js` 脚本用于为 `ranking_archive` 集合创建所有必需的索引，包括：

1. **复合索引 (idx_type_period_rank)**
   - 用途：按实体类型、排行榜类型和时间周期查询排行榜
   - 字段：`entityType`, `rankingType`, `period.year`, `period.month`, `period.week`, `rank`

2. **实体历史索引 (idx_entity_history)**
   - 用途：查询某个实体（文章/用户/话题）的历史排名
   - 字段：`entityId`, `entityType`, `rankingType`, `period.year`

3. **归档时间索引 (idx_archived_at)**
   - 用途：按归档时间查询，用于数据清理和统计分析
   - 字段：`archivedAt`

4. **唯一索引 (uk_entity_period)**
   - 用途：防止同一实体在同一时间段重复归档
   - 字段：`entityId`, `entityType`, `rankingType`, `period.year`, `period.month`, `period.week`, `period.date`
   - 特性：使用 `partialFilterExpression: { rank: { $type: "int" } }` 只对有效排名的文档应用唯一约束

### 使用方法

#### 方法 1：使用 mongosh 命令行执行

```bash
# Windows PowerShell
mongosh mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin init-ranking-indexes.js

# Linux/Mac
mongosh "mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin" init-ranking-indexes.js
```

#### 方法 2：在 mongosh 中加载执行

```bash
# 1. 连接到 MongoDB
mongosh mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin

# 2. 在 mongosh 中执行
use ZhiCore
load('init-ranking-indexes.js')
```

#### 方法 3：使用 Docker 容器执行

```bash
# 将脚本复制到容器中
docker cp init-ranking-indexes.js ZhiCore-mongodb:/tmp/

# 在容器中执行脚本
docker exec -it ZhiCore-mongodb mongosh mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin /tmp/init-ranking-indexes.js
```

### 验证索引

执行脚本后，可以使用以下命令验证索引是否创建成功：

```javascript
// 在 mongosh 中执行
use ZhiCore
db.ranking_archive.getIndexes()
```

预期输出应包含以下索引：
- `_id_` (默认索引)
- `idx_type_period_rank`
- `idx_entity_history`
- `idx_archived_at`
- `uk_entity_period`

### 删除索引

如果需要删除索引，可以使用以下命令：

```javascript
// 删除单个索引
db.ranking_archive.dropIndex("idx_type_period_rank")

// 删除所有索引（除了 _id_ 索引）
db.ranking_archive.dropIndexes()
```

### 重建索引

如果需要重建索引（例如修改索引定义后），可以：

```javascript
// 1. 删除旧索引
db.ranking_archive.dropIndex("idx_type_period_rank")

// 2. 重新执行初始化脚本
load('init-ranking-indexes.js')
```

## 索引设计说明

### 为什么使用 $type: "int" 而不是 $exists: true

唯一索引使用 `partialFilterExpression: { rank: { $type: "int" } }` 而不是 `{ rank: { $exists: true } }`，原因如下：

1. **更精确的过滤**：`$type: "int"` 确保只对 `rank` 字段为整数类型的文档应用唯一约束
2. **避免 null 值问题**：`$exists: true` 会匹配 `rank: null` 的文档，而 `$type: "int"` 不会
3. **数据质量保证**：只有有效的排名数据（整数）才会被唯一性约束保护

### 索引性能优化

所有索引都使用 `background: true` 选项：
- 索引创建在后台进行，不会阻塞数据库操作
- 适合在生产环境中执行
- 创建时间可能较长，但不影响应用运行

### 索引使用场景

1. **查询月榜数据**
   ```javascript
   db.ranking_archive.find({
     entityType: "post",
     rankingType: "monthly",
     "period.year": 2024,
     "period.month": 1
   }).sort({ rank: 1 })
   ```
   使用索引：`idx_type_period_rank`

2. **查询实体历史排名**
   ```javascript
   db.ranking_archive.find({
     entityId: "1234567890",
     entityType: "post",
     rankingType: "monthly"
   }).sort({ "period.year": -1 })
   ```
   使用索引：`idx_entity_history`

3. **按归档时间查询**
   ```javascript
   db.ranking_archive.find({
     archivedAt: { $gte: ISODate("2024-01-01") }
   })
   ```
   使用索引：`idx_archived_at`

## 故障排查

### 问题 1：连接失败

**错误信息**：
```
MongoServerError: Authentication failed
```

**解决方案**：
- 检查 MongoDB 服务是否正在运行
- 验证用户名和密码是否正确
- 确认 authSource 设置为 `admin`

### 问题 2：索引已存在

**错误信息**：
```
MongoServerError: Index with name 'idx_type_period_rank' already exists
```

**解决方案**：
- 索引已经存在，无需重复创建
- 如需重建，先删除旧索引：`db.ranking_archive.dropIndex("idx_type_period_rank")`

### 问题 3：权限不足

**错误信息**：
```
MongoServerError: not authorized on ZhiCore to execute command
```

**解决方案**：
- 确保使用的用户具有 `dbAdmin` 或 `readWrite` 角色
- 检查用户权限：`db.getUser("admin")`

## 相关文档

- [MongoDB 索引文档](https://www.mongodb.com/docs/manual/indexes/)
- [Partial Indexes](https://www.mongodb.com/docs/manual/core/index-partial/)
- [Background Index Builds](https://www.mongodb.com/docs/manual/core/index-creation/)
- [排行榜归档系统设计](.kiro/specs/ZhiCore-ranking-time-based-endpoints/design.md)

## 维护

- **创建时间**：2026-02-17
- **维护者**：ZhiCore Team
- **更新频率**：索引定义变更时更新
