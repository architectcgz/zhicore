# 博客微服务端口映射

## 服务端口列表

| 服务名称 | 模块名 | 端口 | 说明 |
|---------|--------|------|------|
| 网关服务 | ZhiCore-gateway | 8000 | API 网关，统一入口 |
| 用户服务 | ZhiCore-user | 8101 | 用户注册、登录、个人信息管理 |
| 文章服务 | ZhiCore-post | 8102 | 文章 CRUD、点赞、收藏 |
| 评论服务 | ZhiCore-comment | 8103 | 评论管理、评论点赞 |
| 消息服务 | ZhiCore-message | 8084 | 私信、消息通知 |
| 通知服务 | ZhiCore-notification | 8085 | 系统通知、消息推送 |
| 搜索服务 | ZhiCore-search | 8086 | 全文搜索、搜索建议 |
| 排行服务 | ZhiCore-ranking | 8087 | 热门排行、推荐算法 |
| 管理服务 | ZhiCore-admin | 8090 | 后台管理功能 |
| 运维服务 | ZhiCore-ops | 8090 | 运维监控（与 admin 共用端口） |

## API 文档端口

所有服务的 Knife4j 文档都在各自端口的 `/doc.html` 路径下：

- **网关聚合文档**: http://localhost:8000/doc.html
- **用户服务文档**: http://localhost:8101/doc.html
- **文章服务文档**: http://localhost:8102/doc.html
- **评论服务文档**: http://localhost:8103/doc.html
- **消息服务文档**: http://localhost:8084/doc.html
- **通知服务文档**: http://localhost:8085/doc.html
- **搜索服务文档**: http://localhost:8086/doc.html
- **排行服务文档**: http://localhost:8087/doc.html
- **管理服务文档**: http://localhost:8090/doc.html

## 基础设施端口

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5432 | 主数据库 |
| Redis | 6379 | 缓存和会话存储 |
| MongoDB | 27017 | 文档存储 |
| Nacos | 8848 | 服务注册与配置中心 |
| Nacos MySQL | 3307 | Nacos 数据库 |
| RocketMQ NameServer | 9876 | 消息队列名称服务器 |
| RocketMQ Broker | 10909, 10911, 10912 | 消息队列代理 |
| RocketMQ Dashboard | 8180 | RocketMQ 控制台 |
| Prometheus | 9090 | 监控指标收集 |
| Sentinel Dashboard | 8858 | 流量控制台 |

## 端口规划说明

### 微服务端口规划 (810x, 808x, 809x)
- **810x 系列**: 核心业务服务（用户、文章、评论）
  - 8101: 用户服务
  - 8102: 文章服务
  - 8103: 评论服务

- **808x 系列**: 辅助业务服务（消息、通知、搜索、排行）
  - 8084: 消息服务
  - 8085: 通知服务
  - 8086: 搜索服务
  - 8087: 排行服务

- **809x 系列**: 管理和运维服务
  - 8090: 管理服务 / 运维服务

- **8000**: 网关服务（统一入口）

### 为什么不是连续的端口？
这是为了给未来的服务扩展留出空间，同时按功能分组便于管理。

## 快速检查所有服务

```powershell
# 检查所有微服务端口
$ports = @(8000, 8101, 8102, 8103, 8084, 8085, 8086, 8087, 8090)
foreach ($port in $ports) {
    $result = Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue
    $status = if ($result.TcpTestSucceeded) { "✓ RUNNING" } else { "✗ STOPPED" }
    $color = if ($result.TcpTestSucceeded) { "Green" } else { "Red" }
    Write-Host "$status - Port $port" -ForegroundColor $color
}
```

## 访问服务

### 通过网关访问（推荐）
```
http://localhost:8000/{service-path}/{endpoint}
```

例如：
- 用户注册: `POST http://localhost:8000/user/api/v1/users/register`
- 创建文章: `POST http://localhost:8000/post/api/v1/posts`
- 添加评论: `POST http://localhost:8000/comment/api/v1/comments`

### 直接访问服务
```
http://localhost:{service-port}/{endpoint}
```

例如：
- 用户注册: `POST http://localhost:8101/api/v1/users/register`
- 创建文章: `POST http://localhost:8102/api/v1/posts`
- 添加评论: `POST http://localhost:8103/api/v1/comments`

## 注意事项

1. **生产环境**: 所有服务端口应该只在内网开放，外部访问统一通过网关
2. **开发环境**: 可以直接访问各个服务端口进行调试
3. **文档访问**: 生产环境应禁用 `/doc.html` 端点
4. **健康检查**: 所有服务都提供 `/actuator/health` 端点

## 健康检查端点

所有微服务都提供 Spring Boot Actuator 健康检查端点：

```bash
# 网关服务
curl http://localhost:8000/actuator/health

# 用户服务
curl http://localhost:8101/actuator/health

# 文章服务
curl http://localhost:8102/actuator/health

# 评论服务
curl http://localhost:8103/actuator/health

# 消息服务
curl http://localhost:8084/actuator/health

# 通知服务
curl http://localhost:8085/actuator/health

# 搜索服务
curl http://localhost:8086/actuator/health

# 排行服务
curl http://localhost:8087/actuator/health

# 管理服务
curl http://localhost:8090/actuator/health
```

**健康检查响应示例：**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

## 相关文档

- [Knife4j 快速参考](./knife4j-quick-reference.md)
- [Knife4j 验证指南](./knife4j-verification-guide.md)
- [部署文档](../docker/DEPLOYMENT.md)
