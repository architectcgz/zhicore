# ZhiCore Microservices Docker 配置

本目录包含 ZhiCore 微服务平台的 Docker 基础设施配置。

## 快速开始

### 1. 启动所有基础设施

```powershell
# 首次启动（构建自定义镜像）
.\start-infrastructure.ps1 -Build

# 后续启动
.\start-infrastructure.ps1
```

### 2. 启动单个服务

```powershell
# 启动 Redis
.\start-infrastructure.ps1 -Service zhicore-redis

# 启动 RocketMQ Broker
.\start-infrastructure.ps1 -Service zhicore-rocketmq-broker
```

### 3. 清理资源

```powershell
# 停止并删除所有容器和卷
.\start-infrastructure.ps1 -Clean
```

## 服务列表

### 数据库服务

| 服务 | 端口 | 用户名 | 密码 | 说明 |
|------|------|--------|------|------|
| PostgreSQL | 5432 | postgres | postgres123456 | 主数据库 |
| MongoDB | 27017 | admin | mongo123456 | 文档数据库 |
| MySQL (Nacos) | 3307 | nacos | nacos123456 | Nacos 配置数据库 |

### 缓存服务

| 服务 | 端口 | 密码 | 说明 |
|------|------|------|------|
| Redis | 6379 | redis123456 | 缓存和会话存储 |

### 服务注册与配置

| 服务 | 端口 | 访问地址 | 用户名/密码 |
|------|------|----------|------------|
| Nacos | 8848, 9848, 9849 | http://localhost:8848/nacos | nacos/nacos |

### 消息队列

| 服务 | 端口 | 访问地址 | 说明 |
|------|------|----------|------|
| RocketMQ NameServer | 9876 | - | 名称服务器 |
| RocketMQ Broker | 10909, 10911, 10912 | - | 消息代理 |
| RocketMQ Dashboard | 8180 | http://localhost:8180 | 管理控制台 |

### 搜索与分析

| 服务 | 端口 | 访问地址 | 说明 |
|------|------|----------|------|
| Elasticsearch | 9200, 9300 | http://localhost:9200 | 搜索引擎 |
| Kibana | 5601 | http://localhost:5601 | ES 可视化 |

### 监控服务

| 服务 | 端口 | 访问地址 | 用户名/密码 |
|------|------|----------|------------|
| Prometheus | 9090 | http://localhost:9090 | 指标收集 |
| Grafana | 3100 | http://localhost:3100 | admin/admin123456 |

### 管理界面

| 服务 | 端口 | 访问地址 | 用户名/密码 |
|------|------|----------|------------|
| Mongo Express | 8091 | http://localhost:8091 | admin/express123456 |

## 常用命令

### Docker Compose 命令

```powershell
# 启动所有服务
docker-compose up -d

# 启动特定服务
docker-compose up -d zhicore-redis zhicore-postgres

# 停止所有服务
docker-compose down

# 停止并删除卷
docker-compose down -v

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f zhicore-redis

# 重启服务
docker-compose restart zhicore-rocketmq-broker

# 构建自定义镜像
docker-compose build zhicore-rocketmq-broker
```

### 服务验证命令

```powershell
# 验证 Redis
docker exec -it zhicore-redis redis-cli -a redis123456 ping

# 验证 PostgreSQL
docker exec -it zhicore-postgres psql -U postgres -c "SELECT version();"

# 验证 MongoDB
docker exec -it zhicore-mongodb mongosh --eval "db.adminCommand('ping')"

# 验证 RocketMQ
docker exec -it zhicore-rocketmq-namesrv sh -c "mqadmin clusterList -n localhost:9876"

# 验证 Elasticsearch
curl http://localhost:9200/_cluster/health
```

## 配置文件说明

### docker-compose.yml
主配置文件，定义所有基础设施服务。

### 服务配置目录

```
docker/
├── redis/
│   ├── Dockerfile          # Redis 自定义镜像（可选）
│   └── redis.conf          # Redis 配置（参考）
├── rocketmq/
│   ├── Dockerfile          # RocketMQ Broker 自定义镜像
│   └── broker.conf         # Broker 配置
├── nacos/
│   ├── logs/               # Nacos 日志目录
│   └── mysql-init/         # MySQL 初始化脚本
├── prometheus/
│   ├── prometheus.yml      # Prometheus 配置
│   └── rules/              # 告警规则
└── grafana/
    ├── provisioning/       # 数据源配置
    └── dashboards/         # 仪表板定义
```

## 故障排查

### 问题 1: 端口被占用

```powershell
# 检查端口占用
netstat -ano | findstr :6379

# 停止占用端口的进程
Stop-Process -Id <PID> -Force
```

### 问题 2: 容器启动失败

```powershell
# 查看详细日志
docker-compose logs zhicore-redis

# 检查容器状态
docker-compose ps

# 重新创建容器
docker-compose up -d --force-recreate zhicore-redis
```

### 问题 3: 配置文件挂载失败

参考 [DOCKER_MOUNT_FIX.md](./DOCKER_MOUNT_FIX.md) 了解详细的解决方案。

**快速修复**：
- Redis: 已使用命令行参数，无需挂载配置文件
- RocketMQ: 使用自定义镜像，配置文件打包在镜像中

### 问题 4: 健康检查失败

```powershell
# 检查服务健康状态
docker inspect zhicore-redis | Select-String -Pattern "Health"

# 手动执行健康检查命令
docker exec -it zhicore-redis redis-cli -a redis123456 ping
```

### 问题 5: 网络连接问题

```powershell
# 检查 Docker 网络
docker network ls
docker network inspect zhicore-network

# 测试容器间连接
docker exec -it zhicore-rocketmq-broker ping zhicore-rocketmq-namesrv
```

## 性能优化

### 开发环境配置

当前配置已针对开发环境优化：
- RocketMQ: 512MB 堆内存
- Elasticsearch: 512MB 堆内存
- Redis: 512MB 最大内存
- PostgreSQL: 256MB 共享缓冲区

### 生产环境建议

生产环境应调整以下配置：
- 增加 JVM 堆内存（RocketMQ, Elasticsearch）
- 增加数据库连接池大小
- 启用持久化和备份
- 配置监控告警
- 使用外部配置管理

## 数据持久化

所有数据都存储在 Docker 卷中：

```powershell
# 查看所有卷
docker volume ls | Select-String "zhicore"

# 备份卷数据
docker run --rm -v zhicore_postgres_data:/data -v ${PWD}:/backup alpine tar czf /backup/postgres_backup.tar.gz /data

# 恢复卷数据
docker run --rm -v zhicore_postgres_data:/data -v ${PWD}:/backup alpine tar xzf /backup/postgres_backup.tar.gz -C /
```

## 安全注意事项

⚠️ **重要**：当前配置仅用于开发环境！

生产环境必须：
- 修改所有默认密码
- 启用 TLS/SSL 加密
- 配置防火墙规则
- 限制网络访问
- 定期更新镜像
- 实施访问控制

## 相关文档

- [Docker 挂载问题修复](./DOCKER_MOUNT_FIX.md)
- [端口分配文档](../../.kiro/steering/port-allocation.md)
- [Docker 使用规范](../../.kiro/steering/08-docker.md)
- [基础设施与端口](../../.kiro/steering/07-infrastructure.md)

## 更新日志

- **2026-02-20**: 
  - 修复 Redis 和 RocketMQ 配置文件挂载问题
  - 添加启动脚本和文档
  - 优化服务启动顺序

---

**维护者**：开发团队  
**最后更新**：2026-02-20
