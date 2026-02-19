# SQL 脚本说明

本目录包含用于生成测试数据的 SQL 脚本。

## 文件列表

### generate-users.sql

生成测试用户数据的 SQL 脚本。

**生成数据**：
- 3 个管理员用户（test_admin_001 - test_admin_003）
- 5 个审核员用户（test_moderator_001 - test_moderator_005）
- 50 个普通用户（test_user_001 - test_user_050）

**特性**：
- 使用 BCrypt 加密密码（密码：Test@123456）
- 自动分配角色（ADMIN、MODERATOR、USER）
- 初始化用户关注统计和签到统计
- 包含数据验证逻辑
- 支持重复执行（自动清理旧数据）

**执行方式**：

方式 1：使用 PowerShell 脚本（推荐）
```powershell
cd blog-microservice/database/test-data/scripts
.\Execute-UserGeneration.ps1
```

方式 2：手动执行
```powershell
# 1. 获取 58 个用户 ID（使用 blog-id-generator 服务）
$response = Invoke-RestMethod -Uri "http://localhost:8088/api/v1/id/snowflake/batch?count=58" -Method Get
$ids = $response.data

# 2. 替换 SQL 脚本中的占位符 {ID_1}, {ID_2}, ... {ID_58}
# 使用文本编辑器或脚本进行替换

# 3. 执行 SQL 脚本
psql -h localhost -p 5432 -U postgres -d blog_user -f generate-users.sql
```

**验证结果**：
```sql
-- 查看所有测试用户
SELECT * FROM users WHERE username LIKE 'test_%' ORDER BY username;

-- 查看用户角色分配
SELECT u.username, r.name 
FROM users u 
JOIN user_roles ur ON u.id = ur.user_id 
JOIN roles r ON ur.role_id = r.id 
WHERE u.username LIKE 'test_%' 
ORDER BY u.username;

-- 查看用户统计
SELECT 
    COUNT(*) FILTER (WHERE username LIKE 'test_admin_%') AS admin_count,
    COUNT(*) FILTER (WHERE username LIKE 'test_moderator_%') AS moderator_count,
    COUNT(*) FILTER (WHERE username LIKE 'test_user_%') AS regular_count,
    COUNT(*) AS total_count,
    COUNT(*) FILTER (WHERE is_active = true) AS active_count
FROM users 
WHERE username LIKE 'test_%';
```

**清理测试数据**：
```sql
-- 删除所有测试用户及相关数据
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
DELETE FROM user_follow_stats WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
DELETE FROM user_check_in_stats WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
DELETE FROM users WHERE username LIKE 'test_%';
```

## 注意事项

1. **ID 生成**：用户 ID 必须从 blog-id-generator 服务获取（端口 8088），不能使用自增 ID
2. **密码安全**：所有测试用户使用相同的密码（Test@123456），仅用于测试环境
3. **数据隔离**：所有测试用户名以 `test_` 开头，便于识别和清理
4. **重复执行**：脚本支持重复执行，会自动清理旧的测试数据
5. **事务保护**：使用事务确保数据生成的原子性

## 前置条件

1. PostgreSQL 数据库已创建并初始化（blog_user）
2. blog-id-generator 服务正常运行（http://localhost:8088）
3. 数据库表结构已创建（users, roles, user_roles 等）
4. PostgreSQL 客户端工具已安装（psql）

## 故障排查

### 问题 1: 无法连接到 blog-id-generator 服务

**错误信息**：`无法连接到服务: http://localhost:8088`

**解决方案**：
1. 检查 blog-id-generator 服务是否启动
2. 验证端口配置是否正确（默认 8088）
3. 检查防火墙设置
4. 查看服务日志：`docker logs blog-id-generator`

### 问题 2: 数据库连接失败

**错误信息**：`psql: error: connection to server failed`

**解决方案**：
1. 检查 PostgreSQL 服务是否启动
2. 验证数据库名称、用户名、密码是否正确
3. 检查 PostgreSQL 配置文件（pg_hba.conf）

### 问题 3: 表不存在

**错误信息**：`ERROR: relation "users" does not exist`

**解决方案**：
1. 确保数据库已初始化
2. 执行数据库初始化脚本：
   ```powershell
   cd blog-microservice/database
   .\init-databases.ps1
   ```

### 问题 4: 角色不存在

**错误信息**：`ERROR: insert or update on table "user_roles" violates foreign key constraint`

**解决方案**：
1. 确保 roles 表已初始化
2. 检查是否存在 ADMIN(1)、MODERATOR(2)、USER(3) 角色

## 相关文档

- [测试数据生成总览](../README.md)
- [需求文档](../../../.kiro/specs/blog-test-data-generation/requirements.md)
- [设计文档](../../../.kiro/specs/blog-test-data-generation/design.md)
- [任务列表](../../../.kiro/specs/blog-test-data-generation/tasks.md)

