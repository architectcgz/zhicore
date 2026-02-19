# 用户关注关系生成脚本

## 概述

`Generate-UserFollows.ps1` 脚本用于生成测试用户的关注关系数据。

## 功能

1. 为每个用户生成 5-20 个关注关系
2. 确保用户不关注自己
3. 通过 API 创建关注关系
4. 自动更新 user_follow_stats 统计

## 前置条件

1. blog-gateway 服务已启动（端口 8000）
2. 已执行用户数据生成脚本（`Execute-UserGeneration.ps1`）
3. 网络连接正常

## 使用方法

### 基本用法

```powershell
# 使用默认配置
.\Generate-UserFollows.ps1

# 预览模式（不实际创建）
.\Generate-UserFollows.ps1 -DryRun

# 使用自定义配置文件
.\Generate-UserFollows.ps1 -ConfigPath ".\custom-config.json"

# 自定义关注数范围
.\Generate-UserFollows.ps1 -MinFollowsPerUser 10 -MaxFollowsPerUser 30
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| ConfigPath | string | "" | 配置文件路径 |
| ApiBaseUrl | string | http://localhost:8000 | API 基础地址 |
| AppId | string | test-app | 应用 ID |
| MinFollowsPerUser | int | 5 | 每用户最小关注数 |
| MaxFollowsPerUser | int | 20 | 每用户最大关注数 |
| DryRun | switch | false | 仅生成数据但不创建 |

## 配置文件

配置文件使用 JSON 格式，示例：

```json
{
  "apiBaseUrl": "http://localhost:8000",
  "appId": "test-app",
  "userRelations": {
    "followsPerUser": {
      "min": 5,
      "max": 20
    }
  }
}
```

## 生成数据

### 关注关系

- 每个用户随机生成 5-20 个关注关系
- 确保用户不关注自己
- 关注对象从所有其他用户中随机选择

### 统计更新

脚本通过 API 创建关注关系，API 会自动更新：
- follower 的 following_count（关注数）
- following 的 followers_count（粉丝数）

## 输出示例

```
╔════════════════════════════════════════════════════╗
║          用户关注关系生成脚本                      ║
╚════════════════════════════════════════════════════╝

=== 步骤 1: 验证服务可用性 ===
  检查 API 服务...
✓ API 服务正常运行
  状态: UP

=== 步骤 2: 获取测试用户 ===
  获取测试用户列表...
✓ 获取到 58 个测试用户

=== 生成关注关系数据 ===
✓ 关注关系数据生成完成
  总关注关系数: 725
  平均每用户关注数: 12.5

=== 步骤 4: 创建关注关系 ===
  [==================================================] 100.0% - 创建关注关系 (725/725)

=== 步骤 5: 生成结果 ===
✓ 关注关系创建完成
  成功: 725

=== 统计信息 ===
  总数: 725
  成功: 725
  失败: 0
  成功率: 100%
  用户数: 58
  平均每用户关注数: 12.5

╔════════════════════════════════════════════════════╗
║          用户关注关系生成成功！                    ║
╚════════════════════════════════════════════════════╝
```

## 数据验证

### 验证关注关系

```sql
-- 查看关注关系总数
SELECT COUNT(*) FROM user_follows;

-- 查看每个用户的关注数
SELECT 
    follower_id,
    COUNT(*) as following_count
FROM user_follows
GROUP BY follower_id
ORDER BY following_count DESC;

-- 验证用户不关注自己
SELECT COUNT(*) 
FROM user_follows 
WHERE follower_id = following_id;
-- 应该返回 0
```

### 验证统计一致性

```sql
-- 验证 following_count 一致性
SELECT 
    u.id,
    u.username,
    ufs.following_count as stats_count,
    COUNT(uf.following_id) as actual_count
FROM users u
LEFT JOIN user_follow_stats ufs ON u.id = ufs.user_id
LEFT JOIN user_follows uf ON u.id = uf.follower_id
WHERE u.username LIKE 'test_%'
GROUP BY u.id, u.username, ufs.following_count
HAVING ufs.following_count != COUNT(uf.following_id);
-- 应该返回空结果

-- 验证 followers_count 一致性
SELECT 
    u.id,
    u.username,
    ufs.followers_count as stats_count,
    COUNT(uf.follower_id) as actual_count
FROM users u
LEFT JOIN user_follow_stats ufs ON u.id = ufs.user_id
LEFT JOIN user_follows uf ON u.id = uf.following_id
WHERE u.username LIKE 'test_%'
GROUP BY u.id, u.username, ufs.followers_count
HAVING ufs.followers_count != COUNT(uf.follower_id);
-- 应该返回空结果
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

### 问题 3: 创建关注关系失败

**错误信息**：
```
创建关注关系失败: API 返回错误
```

**可能原因**：
1. 用户 ID 无效
2. 已存在相同的关注关系
3. 数据库约束冲突

**解决方案**：
1. 检查错误详情
2. 验证用户 ID 是否有效
3. 检查是否已存在关注关系

## 相关文件

- `Generate-UserBlocks.ps1` - 用户拉黑关系生成脚本
- `modules/ApiHelper.psm1` - API 辅助模块
- `test-data-config.json` - 配置文件

## 需求验证

此脚本验证以下需求：

- **Requirements 2.1**: 为每个用户生成 5-20 个关注关系
- **Requirements 2.2**: 确保 follower_id 和 following_id 都是有效的用户 ID
- **Requirements 2.3**: 更新 user_follow_stats 表的统计数据
- **Requirements 2.6**: 确保用户不会关注自己
