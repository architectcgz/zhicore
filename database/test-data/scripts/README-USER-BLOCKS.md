# 用户拉黑关系生成脚本

## 概述

`Generate-UserBlocks.ps1` 脚本用于生成测试用户的拉黑关系数据。

## 功能

1. 为部分用户生成 0-5 个拉黑关系
2. 确保用户不拉黑自己
3. 通过 API 创建拉黑关系

## 前置条件

1. blog-gateway 服务已启动（端口 8000）
2. 已执行用户数据生成脚本（`Execute-UserGeneration.ps1`）
3. 网络连接正常

## 使用方法

### 基本用法

```powershell
# 使用默认配置
.\Generate-UserBlocks.ps1

# 预览模式（不实际创建）
.\Generate-UserBlocks.ps1 -DryRun

# 使用自定义配置文件
.\Generate-UserBlocks.ps1 -ConfigPath ".\custom-config.json"

# 自定义拉黑数范围
.\Generate-UserBlocks.ps1 -MinBlocksPerUser 1 -MaxBlocksPerUser 10

# 设置有拉黑行为的用户比例
.\Generate-UserBlocks.ps1 -BlockRatio 0.5
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| ConfigPath | string | "" | 配置文件路径 |
| ApiBaseUrl | string | http://localhost:8000 | API 基础地址 |
| AppId | string | test-app | 应用 ID |
| MinBlocksPerUser | int | 0 | 每用户最小拉黑数 |
| MaxBlocksPerUser | int | 5 | 每用户最大拉黑数 |
| BlockRatio | double | 0.3 | 有拉黑行为的用户比例 |
| DryRun | switch | false | 仅生成数据但不创建 |

## 配置文件

配置文件使用 JSON 格式，示例：

```json
{
  "apiBaseUrl": "http://localhost:8000",
  "appId": "test-app",
  "userRelations": {
    "blocksPerUser": {
      "min": 0,
      "max": 5
    }
  }
}
```

## 生成数据

### 拉黑关系

- 默认 30% 的用户有拉黑行为
- 每个拉黑用户随机生成 0-5 个拉黑关系
- 确保用户不拉黑自己
- 拉黑对象从所有其他用户中随机选择

### 数据特点

- 拉黑关系是单向的（A 拉黑 B 不意味着 B 拉黑 A）
- 拉黑关系数量相对较少，符合实际场景
- 可以通过 BlockRatio 参数调整有拉黑行为的用户比例

## 输出示例

```
╔════════════════════════════════════════════════════╗
║          用户拉黑关系生成脚本                      ║
╚════════════════════════════════════════════════════╝

=== 步骤 1: 验证服务可用性 ===
  检查 API 服务...
✓ API 服务正常运行
  状态: UP

=== 步骤 2: 获取测试用户 ===
  获取测试用户列表...
✓ 获取到 58 个测试用户

=== 生成拉黑关系数据 ===
  将为 17 个用户生成拉黑关系（30.0% 的用户）
✓ 拉黑关系数据生成完成
  总拉黑关系数: 42
  有拉黑行为的用户数: 17
  平均每个拉黑用户拉黑数: 2.5

=== 步骤 4: 创建拉黑关系 ===
  [==================================================] 100.0% - 创建拉黑关系 (42/42)

=== 步骤 5: 生成结果 ===
✓ 拉黑关系创建完成
  成功: 42

=== 统计信息 ===
  总数: 42
  成功: 42
  失败: 0
  成功率: 100%
  用户数: 58
  有拉黑行为的用户数: 17
  平均每个拉黑用户拉黑数: 2.5

╔════════════════════════════════════════════════════╗
║          用户拉黑关系生成成功！                    ║
╚════════════════════════════════════════════════════╝
```

## 数据验证

### 验证拉黑关系

```sql
-- 查看拉黑关系总数
SELECT COUNT(*) FROM user_blocks;

-- 查看每个用户的拉黑数
SELECT 
    blocker_id,
    COUNT(*) as blocked_count
FROM user_blocks
GROUP BY blocker_id
ORDER BY blocked_count DESC;

-- 验证用户不拉黑自己
SELECT COUNT(*) 
FROM user_blocks 
WHERE blocker_id = blocked_id;
-- 应该返回 0

-- 查看有拉黑行为的用户比例
SELECT 
    COUNT(DISTINCT blocker_id) as users_with_blocks,
    (SELECT COUNT(*) FROM users WHERE username LIKE 'test_%') as total_users,
    ROUND(COUNT(DISTINCT blocker_id)::numeric / 
          (SELECT COUNT(*) FROM users WHERE username LIKE 'test_%') * 100, 2) as percentage
FROM user_blocks;
```

### 验证拉黑关系有效性

```sql
-- 验证所有 blocker_id 都是有效用户
SELECT COUNT(*) 
FROM user_blocks ub
LEFT JOIN users u ON ub.blocker_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0

-- 验证所有 blocked_id 都是有效用户
SELECT COUNT(*) 
FROM user_blocks ub
LEFT JOIN users u ON ub.blocked_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0
```

## 故障排查

### 问题 1: API 服务不可用

**错误信息**：
```
✗ API 服务不可用
  错误: 无法连接到服务
```

**解决方案**：
1. 确认 blog-gateway 服务已启动
2. 检查端口 8000 是否被占用
3. 验证网络连接

### 问题 2: 未找到测试用户

**错误信息**：
```
✗ 获取测试用户失败: 未找到测试用户
```

**解决方案**：
1. 确认已执行用户数据生成脚本
2. 检查数据库中是否存在测试用户：
   ```sql
   SELECT COUNT(*) FROM users WHERE username LIKE 'test_%';
   ```

### 问题 3: 未生成任何拉黑关系

**警告信息**：
```
⚠ 未生成任何拉黑关系
  这是正常的，因为拉黑关系是随机生成的
```

**说明**：
这是正常现象，因为：
1. 拉黑关系是随机生成的
2. MinBlocksPerUser 默认为 0
3. 只有部分用户（默认 30%）有拉黑行为

**解决方案**：
如果需要确保生成拉黑关系，可以：
```powershell
# 设置最小拉黑数为 1
.\Generate-UserBlocks.ps1 -MinBlocksPerUser 1 -MaxBlocksPerUser 5

# 增加有拉黑行为的用户比例
.\Generate-UserBlocks.ps1 -BlockRatio 0.5
```

### 问题 4: 创建拉黑关系失败

**错误信息**：
```
创建拉黑关系失败: API 返回错误
```

**可能原因**：
1. 用户 ID 无效
2. 已存在相同的拉黑关系
3. 数据库约束冲突

**解决方案**：
1. 检查错误详情
2. 验证用户 ID 是否有效
3. 检查是否已存在拉黑关系

## 与关注关系的区别

| 特性 | 关注关系 | 拉黑关系 |
|------|---------|---------|
| 数量 | 每用户 5-20 个 | 每用户 0-5 个 |
| 覆盖率 | 100% 用户 | 30% 用户（默认） |
| 统计表 | user_follow_stats | 无 |
| 业务含义 | 社交关系 | 屏蔽关系 |

## 相关文件

- `Generate-UserFollows.ps1` - 用户关注关系生成脚本
- `modules/ApiHelper.psm1` - API 辅助模块
- `test-data-config.json` - 配置文件

## 需求验证

此脚本验证以下需求：

- **Requirements 2.4**: 为部分用户生成 1-5 个拉黑关系
- **Requirements 2.5**: 确保 blocker_id 和 blocked_id 都是有效的用户 ID
- **Requirements 2.6**: 确保用户不会拉黑自己
