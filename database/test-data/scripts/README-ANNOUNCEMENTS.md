# 全局公告数据生成脚本

## 概述

`Generate-Announcements.ps1` 脚本用于生成测试全局公告数据。该脚本生成不同类型的公告，包括信息、警告、重要通知和维护通知，并支持设置启用状态和过期时间。

## 功能特性

- ✅ 生成全局公告数据
- ✅ 支持多种公告类型（INFO、WARNING、IMPORTANT、MAINTENANCE）
- ✅ 设置部分公告为启用状态
- ✅ 为部分公告设置过期时间
- ✅ 自动分配管理员作为创建者
- ✅ 生成 SQL 文件用于数据插入
- ✅ 支持配置文件和命令行参数
- ✅ 提供 Dry Run 模式预览数据

## 前置条件

1. **服务运行**：
   - ZhiCore-id-generator 服务已启动（端口 8088）
   - PostgreSQL 数据库已启动（端口 5432）

2. **数据依赖**：
   - 已执行用户数据生成脚本（需要管理员用户）

3. **环境要求**：
   - PowerShell 5.1 或更高版本
   - psql 命令行工具（PostgreSQL 客户端）

## 使用方法

### 基本用法

```powershell
# 使用默认配置生成公告数据
.\Generate-Announcements.ps1

# 预览模式（不创建 SQL 文件）
.\Generate-Announcements.ps1 -DryRun

# 指定公告数量
.\Generate-Announcements.ps1 -AnnouncementCount 10

# 指定启用比例
.\Generate-Announcements.ps1 -EnabledRatio 0.8

# 使用自定义配置文件
.\Generate-Announcements.ps1 -ConfigPath ".\custom-config.json"
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ConfigPath` | string | "" | 配置文件路径（可选） |
| `IdGeneratorUrl` | string | "http://localhost:8088" | ID Generator 服务地址 |
| `AnnouncementCount` | int | 5 | 要生成的公告数量 |
| `EnabledRatio` | double | 0.6 | 启用公告比例（0.0-1.0） |
| `DryRun` | switch | false | 仅预览，不创建 SQL 文件 |

### 配置文件格式

在 `test-data-config.json` 中配置：

```json
{
  "idGeneratorUrl": "http://localhost:8088",
  "announcements": {
    "count": 5,
    "enabledRatio": 0.6
  }
}
```

## 生成数据说明

### 公告类型

脚本支持 4 种公告类型：

1. **INFO (0)**: 信息类公告
   - 欢迎消息
   - 新功能介绍
   - 写作技巧分享

2. **WARNING (1)**: 警告类公告
   - 社区规范提醒
   - 账号安全提醒

3. **IMPORTANT (2)**: 重要通知
   - 优秀作者评选活动
   - 隐私政策更新

4. **MAINTENANCE (3)**: 维护通知
   - 系统维护通知
   - 服务器升级通知

### 数据特征

- **数量**: 默认生成 5 条公告
- **启用状态**: 60% 的公告处于启用状态（默认）
- **过期时间**: 部分公告（如维护通知、活动通知）会设置 7-30 天后过期
- **优先级**: 每条公告都有优先级（1-5），用于排序显示
- **创建者**: 随机分配管理员用户作为创建者
- **链接**: 部分公告包含相关链接

### 公告内容模板

脚本内置了 10 个公告模板，包括：

1. 欢迎使用博客系统
2. 系统维护通知
3. 新功能上线
4. 社区规范提醒
5. 优秀作者评选活动
6. 账号安全提醒
7. 内容推荐算法优化
8. 服务器升级通知
9. 隐私政策更新
10. 写作技巧分享

如果需要生成的公告数量超过模板数量，脚本会重复使用模板。

## 输出文件

### SQL 文件

生成的 SQL 文件位于：`ZhiCore-microservice/database/test-data/sql/generated-announcements.sql`

文件内容包括：
- 生成时间和统计信息
- 数据库连接命令
- 事务控制（BEGIN/COMMIT）
- INSERT 语句

### 执行 SQL 文件

```powershell
# 执行生成的 SQL 文件
psql -h localhost -p 5432 -U postgres -f "ZhiCore-microservice/database/test-data/sql/generated-announcements.sql"
```

## 执行流程

1. **验证服务可用性**
   - 检查 ID Generator 服务是否运行
   - 生成测试 ID 验证服务正常

2. **获取管理员用户**
   - 从数据库查询管理员用户
   - 用于分配公告创建者

3. **生成公告数据**
   - 根据配置生成指定数量的公告
   - 随机选择公告模板
   - 设置启用状态和过期时间
   - 分配创建者和优先级

4. **生成 SQL 文件**
   - 将公告数据转换为 SQL INSERT 语句
   - 保存到文件

5. **显示结果**
   - 统计公告数量
   - 统计启用/禁用状态
   - 统计过期时间设置
   - 统计公告类型分布

## 示例输出

```
╔════════════════════════════════════════════════════╗
║          全局公告数据生成脚本                      ║
╚════════════════════════════════════════════════════╝

=== 步骤 1: 验证服务可用性 ===
  检查 ID Generator 服务...
✓ ID Generator 服务正常运行
  测试 ID: 1234567890123456789

=== 步骤 2: 获取管理员用户 ===
  查询管理员用户...
✓ 获取到 3 个管理员用户

=== 步骤 3: 生成公告数据 ===
✓ 生成 5 条公告

=== 步骤 4: 生成 SQL 文件 ===
✓ SQL 文件生成成功
  文件路径: ZhiCore-microservice/database/test-data/sql/generated-announcements.sql

=== 步骤 5: 生成结果 ===
✓ 全局公告数据生成完成
  总公告数: 5

=== 启用状态统计 ===
  启用: 3 (60.0%)
  禁用: 2 (40.0%)

=== 过期时间统计 ===
  设置过期时间: 2
  无过期时间: 3

=== 公告类型统计 ===
  IMPORTANT : 1 (20.0%)
  INFO : 2 (40.0%)
  MAINTENANCE : 1 (20.0%)
  WARNING : 1 (20.0%)

╔════════════════════════════════════════════════════╗
║          全局公告数据生成成功！                    ║
╚════════════════════════════════════════════════════╝

下一步：执行 SQL 文件插入数据
  psql -h localhost -p 5432 -U postgres -f "ZhiCore-microservice/database/test-data/sql/generated-announcements.sql"
```

## 数据验证

### 验证公告数量

```sql
-- 连接到数据库
\c ZhiCore_notification;

-- 查询公告总数
SELECT COUNT(*) FROM global_announcements;

-- 查询启用的公告
SELECT COUNT(*) FROM global_announcements WHERE is_enabled = TRUE;

-- 查询有过期时间的公告
SELECT COUNT(*) FROM global_announcements WHERE expires_at IS NOT NULL;
```

### 验证公告类型分布

```sql
-- 按类型统计公告数量
SELECT 
    CASE type
        WHEN 0 THEN 'INFO'
        WHEN 1 THEN 'WARNING'
        WHEN 2 THEN 'IMPORTANT'
        WHEN 3 THEN 'MAINTENANCE'
    END AS type_name,
    COUNT(*) as count
FROM global_announcements
GROUP BY type
ORDER BY type;
```

### 验证创建者

```sql
-- 验证所有公告都有有效的创建者
SELECT COUNT(*) 
FROM global_announcements a
LEFT JOIN users u ON a.created_by_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0
```

## 故障排查

### 问题 1: ID Generator 服务不可用

**错误信息**：
```
✗ ID Generator 服务不可用
  错误: 无法连接到服务
```

**解决方案**：
1. 检查服务是否启动：`http://localhost:8088/api/v1/id/snowflake`
2. 检查端口是否被占用
3. 查看服务日志

### 问题 2: 未找到管理员用户

**错误信息**：
```
✗ 获取管理员用户失败: 未找到管理员用户
```

**解决方案**：
1. 确认已执行用户数据生成脚本
2. 检查数据库中是否有管理员角色的用户：
   ```sql
   SELECT u.id, u.username, r.name
   FROM users u
   JOIN user_roles ur ON u.id = ur.user_id
   JOIN roles r ON ur.role_id = r.id
   WHERE r.name = 'ADMIN' AND u.username LIKE 'test_%';
   ```

### 问题 3: SQL 文件执行失败

**错误信息**：
```
ERROR: duplicate key value violates unique constraint
```

**解决方案**：
1. 清理旧数据：
   ```sql
   \c ZhiCore_notification;
   DELETE FROM global_announcements;
   ```
2. 重新执行 SQL 文件

## 需求验证

该脚本验证以下需求：

- ✅ **Requirements 9.1**: 生成至少 5 条全局公告
- ✅ **Requirements 9.2**: 确保至少 3 条公告处于启用状态（60% 启用率）
- ✅ **Requirements 9.3**: 为部分公告设置过期时间

## 相关文档

- [测试数据生成总览](../README.md)
- [小助手消息生成](./README-ASSISTANT-MESSAGES.md)
- [通知数据生成](./README-NOTIFICATIONS.md)
- [数据库表结构](../../docker/postgres-init/02-init-tables.sql)

## 维护说明

### 添加新的公告模板

在脚本中的 `$AnnouncementTemplates` 数组中添加新模板：

```powershell
$AnnouncementTemplates = @(
    # ... 现有模板 ...
    @{
        title = "新公告标题"
        content = "新公告内容"
        type = $AnnouncementTypes.INFO
        priority = 3
        link = "/new-link"  # 可选
        hasExpiry = $true   # 可选
    }
)
```

### 修改公告类型

在 `$AnnouncementTypes` 哈希表中定义新类型：

```powershell
$AnnouncementTypes = @{
    INFO = 0
    WARNING = 1
    IMPORTANT = 2
    MAINTENANCE = 3
    NEW_TYPE = 4  # 新类型
}
```

## 最后更新

- **日期**: 2026-02-13
- **版本**: 1.0.0
- **维护者**: 开发团队
