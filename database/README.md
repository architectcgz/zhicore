# 数据库初始化脚本

本目录包含博客微服务系统的数据库初始化脚本。

## 快速开始

### 方式一：使用合并脚本（推荐）

使用 `init-all-databases.sql` 快速初始化所有数据库表结构：

```bash
# 1. 连接到 ZhiCore_user 数据库
psql -h localhost -U postgres -d ZhiCore_user -f init-all-databases.sql

# 2. 连接到 ZhiCore_post 数据库
psql -h localhost -U postgres -d ZhiCore_post -f init-all-databases.sql

# 3. 连接到 ZhiCore_comment 数据库
psql -h localhost -U postgres -d ZhiCore_comment -f init-all-databases.sql

# 4. 连接到 ZhiCore_message 数据库
psql -h localhost -U postgres -d ZhiCore_message -f init-all-databases.sql

# 5. 连接到 ZhiCore_notification 数据库
psql -h localhost -U postgres -d ZhiCore_notification -f init-all-databases.sql
```

### 方式二：使用 PowerShell 脚本

```powershell
.\init-databases.ps1
```

## 数据库列表

| 数据库名称 | 服务 | 说明 |
|-----------|------|------|
| ZhiCore_user | User Service | 用户、角色、关注、签到 |
| ZhiCore_post | Post Service | 文章、点赞、收藏、统计 |
| ZhiCore_comment | Comment Service | 评论、回复、点赞 |
| ZhiCore_message | Message Service | 私信、会话 |
| ZhiCore_notification | Notification Service | 通知、公告、小助手消息 |

## 表结构说明

### User Service (ZhiCore_user)

- `users` - 用户基本信息
- `roles` - 角色定义
- `user_roles` - 用户角色关联
- `user_follows` - 用户关注关系
- `user_follow_stats` - 关注统计
- `user_blocks` - 用户拉黑
- `user_check_ins` - 签到记录
- `user_check_in_stats` - 签到统计

### Post Service (ZhiCore_post)

- `posts` - 文章内容
- `post_stats` - 文章统计（浏览、点赞、收藏、评论数）
- `post_likes` - 文章点赞
- `post_favorites` - 文章收藏

### Comment Service (ZhiCore_comment)

- `comments` - 评论内容
- `comment_stats` - 评论统计（点赞、回复数）
- `comment_likes` - 评论点赞

### Message Service (ZhiCore_message)

- `conversations` - 会话
- `messages` - 私信消息

### Notification Service (ZhiCore_notification)

- `notifications` - 用户通知
- `global_announcements` - 全局公告
- `assistant_messages` - 小助手消息

## ID 类型说明

所有主键 ID 均使用 `BIGINT` 类型，由分布式 ID 生成器（Leaf）生成。

## 索引策略

- 所有外键字段都有索引
- 时间字段使用降序索引以优化排序查询
- 组合索引遵循最左前缀原则
- 模糊查询字段使用 `text_pattern_ops` 操作符类

## 注意事项

1. **执行顺序**：脚本使用 `IF NOT EXISTS` 子句，可以安全地重复执行
2. **数据库连接**：确保 PostgreSQL 服务已启动且可访问
3. **权限要求**：需要有创建表和索引的权限
4. **字符编码**：数据库应使用 UTF-8 编码

## 故障排查

### 连接失败

```bash
# 检查 PostgreSQL 是否运行
docker ps | grep postgres

# 测试连接
psql -h localhost -U postgres -c "SELECT version();"
```

### 数据库不存在

```bash
# 创建数据库
psql -h localhost -U postgres -c "CREATE DATABASE ZhiCore_user;"
psql -h localhost -U postgres -c "CREATE DATABASE ZhiCore_post;"
psql -h localhost -U postgres -c "CREATE DATABASE ZhiCore_comment;"
psql -h localhost -U postgres -c "CREATE DATABASE ZhiCore_message;"
psql -h localhost -U postgres -c "CREATE DATABASE ZhiCore_notification;"
```

### 查看已创建的表

```bash
# 连接到数据库
psql -h localhost -U postgres -d ZhiCore_user

# 列出所有表
\dt

# 查看表结构
\d users
```

## 相关文档

- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [Docker Compose 配置](../docker/README.md)
- [服务端口配置](../docs/infrastructure-ports.md)
