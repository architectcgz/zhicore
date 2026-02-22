# ZhiCore 测试数据生成工具

本目录包含用于生成 ZhiCore-microservice 测试数据的脚本和配置文件。

## 目录结构

```
test-data/
├── README.md                    # 本文档
├── test-data-config.json        # 配置文件
├── sql/                         # SQL 脚本目录
│   └── generate-users.sql       # 用户数据生成脚本
├── scripts/                     # PowerShell 脚本目录
│   ├── Generate-TestData.ps1    # 主执行脚本
│   ├── modules/                 # 功能模块
│   │   ├── IdGenerator.ps1      # ID 生成客户端
│   │   ├── ApiClient.ps1        # API 调用工具
│   │   ├── TagGenerator.ps1     # 标签生成
│   │   ├── PostGenerator.ps1    # 文章生成
│   │   ├── CommentGenerator.ps1 # 评论生成
│   │   ├── UserRelationGenerator.ps1  # 用户关系生成
│   │   ├── MessageGenerator.ps1 # 私信生成
│   │   ├── NotificationGenerator.ps1  # 通知生成
│   │   └── DataValidator.ps1    # 数据验证
│   └── logs/                    # 执行日志目录
└── tests/                       # 测试脚本目录
    └── Test-DataGeneration.ps1  # 数据生成测试
```

## 前置条件

### 1. 服务启动

确保以下服务已启动并正常运行：

- **PostgreSQL**: `localhost:5432`
- **MongoDB**: `localhost:27017`
- **Redis**: `localhost:6379`
- **Nacos**: `localhost:8848`
- **ID Generator**: `localhost:8088`
- **ZhiCore Gateway**: `localhost:8100`
- **所有 ZhiCore 微服务**: User(8101), Post(8102), Comment(8103), Message(8105), Notification(8106)

可以使用以下命令启动所有服务：

```powershell
cd ZhiCore-microservice/docker
docker-compose up -d
```

### 2. 数据库初始化

确保数据库已初始化并创建了所有必需的表：

```powershell
cd ZhiCore-microservice/database
.\init-databases.ps1
```

### 3. PowerShell 版本

需要 PowerShell 5.1 或更高版本。

## 配置说明

编辑 `test-data-config.json` 文件以自定义数据生成参数：

### API 配置

```json
{
  "apiBaseUrl": "http://localhost:8100",      // ZhiCore Gateway 地址
  "idGeneratorUrl": "http://localhost:8088",  // ID Generator 服务地址
  "appId": "test-app"                         // 应用 ID
}
```

### 用户配置

```json
{
  "users": {
    "adminCount": 3,        // 管理员用户数量
    "moderatorCount": 5,    // 审核员用户数量
    "regularCount": 50      // 普通用户数量
  }
}
```

### 文章配置

```json
{
  "posts": {
    "totalCount": 200,      // 文章总数
    "publishedRatio": 0.7,  // 已发布文章比例
    "draftRatio": 0.2,      // 草稿文章比例
    "scheduledRatio": 0.1   // 定时发布文章比例
  }
}
```

### 其他配置

参考配置文件中的注释说明。

## 使用方法

### 快速开始

1. 确保所有前置条件已满足
2. 运行主脚本生成所有测试数据：

```powershell
cd ZhiCore-microservice/database/test-data/scripts
.\Generate-TestData.ps1
```

### 自定义生成

可以使用参数自定义生成行为：

```powershell
# 只生成用户数据
.\Generate-TestData.ps1 -OnlyUsers

# 只生成文章数据
.\Generate-TestData.ps1 -OnlyPosts

# 清理旧数据后重新生成
.\Generate-TestData.ps1 -CleanOldData

# 跳过数据验证
.\Generate-TestData.ps1 -SkipValidation

# 使用自定义配置文件
.\Generate-TestData.ps1 -ConfigFile "custom-config.json"
```

### 数据验证

生成数据后，可以单独运行验证脚本：

```powershell
cd ZhiCore-microservice/database/test-data/scripts/modules
.\DataValidator.ps1
```

验证项包括：
- 外键引用完整性
- 统计数据一致性
- 时间戳合理性
- 唯一性约束
- 非空约束

## 生成流程

### 1. 准备阶段

- 验证服务可用性
- 加载配置文件
- 初始化日志记录

### 2. 基础数据生成（SQL）

- 清理旧测试数据
- 生成用户数据（管理员、审核员、普通用户）
- 分配用户角色

### 3. 业务数据生成（PowerShell + API）

按以下顺序生成数据：

1. **标签数据**（30个）
2. **文章数据**（200篇）
   - 已发布文章（140篇）
   - 草稿文章（40篇）
   - 定时发布文章（20篇）
3. **文章标签关联**
4. **文章互动数据**
   - 浏览记录
   - 点赞记录
   - 收藏记录
5. **评论数据**
   - 一级评论
   - 嵌套回复
6. **用户关系**
   - 关注关系
   - 拉黑关系
7. **私信数据**
   - 会话
   - 消息
8. **通知数据**
9. **全局公告**
10. **小助手消息**

### 4. 验证阶段

- 验证数据完整性
- 验证外键关系
- 生成统计报告

## 数据量统计

默认配置下生成的数据量：

| 数据类型 | 数量 |
|---------|------|
| 用户 | 58 |
| 标签 | 30 |
| 文章 | 200 |
| 评论 | 约 3,000 |
| 点赞 | 约 5,000 |
| 收藏 | 约 3,000 |
| 关注关系 | 约 700 |
| 私信会话 | 50 |
| 私信消息 | 约 600 |
| 通知 | 约 1,500 |
| 公告 | 5 |

## 执行时间

根据硬件配置和网络状况，完整生成所有数据大约需要：

- **最小配置**: 10-15 分钟
- **推荐配置**: 5-10 分钟
- **高性能配置**: 3-5 分钟

## 日志文件

执行日志保存在 `scripts/logs/` 目录下：

- `generate-{timestamp}.log`: 主执行日志
- `validation-{timestamp}.log`: 验证日志
- `error-{timestamp}.log`: 错误日志

## 故障排查

### 问题 1: 服务连接失败

**错误信息**: `无法连接到服务: http://localhost:8100`

**解决方案**:
1. 检查服务是否启动: `docker ps`
2. 检查端口是否被占用: `netstat -ano | findstr :8100`
3. 检查防火墙设置
4. 查看服务日志: `docker logs ZhiCore-gateway`

### 问题 2: ID 生成失败

**错误信息**: `ID Generator 服务不可用`

**解决方案**:
1. 确认 ID Generator 服务已启动
2. 检查端口配置是否正确（默认 8088）
3. 查看 ID Generator 日志

### 问题 3: 数据验证失败

**错误信息**: `发现 X 条外键引用无效`

**解决方案**:
1. 查看详细的验证日志
2. 检查数据库约束
3. 重新运行生成脚本并启用详细日志: `.\Generate-TestData.ps1 -Verbose`

### 问题 4: 权限不足

**错误信息**: `Access denied for user`

**解决方案**:
1. 检查数据库用户权限
2. 确认配置文件中的数据库凭据正确
3. 查看数据库连接配置

## 清理测试数据

如果需要清理生成的测试数据：

```powershell
# 清理所有测试数据
cd ZhiCore-microservice/database/test-data/scripts
.\Clean-TestData.ps1

# 只清理特定类型的数据
.\Clean-TestData.ps1 -OnlyPosts
.\Clean-TestData.ps1 -OnlyComments
```

**警告**: 清理操作不可逆，请确保在测试环境中执行。

## 性能优化建议

1. **批量操作**: 使用批量 API 调用减少网络开销
2. **并行处理**: 对于独立的数据生成任务，可以并行执行
3. **缓存 ID**: 批量获取 ID 并缓存，减少对 ID Generator 的调用
4. **数据库连接池**: 确保数据库连接池配置合理

## 安全注意事项

1. **仅用于测试环境**: 不要在生产环境运行此脚本
2. **敏感信息**: 配置文件中不要包含真实的敏感信息
3. **数据隔离**: 确保测试数据与生产数据完全隔离
4. **定期清理**: 定期清理不需要的测试数据

## 相关文档

- [需求文档](../../.kiro/specs/ZhiCore-test-data-generation/requirements.md)
- [设计文档](../../.kiro/specs/ZhiCore-test-data-generation/design.md)
- [任务列表](../../.kiro/specs/ZhiCore-test-data-generation/tasks.md)
- [数据库初始化文档](../README.md)
- [端口分配文档](../../../.kiro/steering/port-allocation.md)

## 维护

**最后更新**: 2026-02-13  
**维护者**: 开发团队  
**版本**: 1.0.0

## 许可证

本项目仅供内部测试使用。
