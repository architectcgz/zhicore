# ZhiCore Microservices 部署指南

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [详细部署步骤](#详细部署步骤)
- [服务端口映射](#服务端口映射)
- [健康检查](#健康检查)
- [监控与告警](#监控与告警)
- [故障排查](#故障排查)

## 环境要求

### 硬件要求

| 环境 | CPU | 内存 | 磁盘 |
|------|-----|------|------|
| 开发环境 | 4 核 | 16 GB | 50 GB |
| 生产环境 | 8 核+ | 32 GB+ | 200 GB+ |

### 软件要求

- Docker 24.0+
- Docker Compose 2.20+
- JDK 17 (构建时)
- Maven 3.9+ (构建时)

## 快速开始

### 0. 启动 ID Generator Service (外部依赖)

**重要**: ZhiCore 微服务依赖独立部署的 ID Generator Service。

```bash
# 确保 ID Generator Service 已启动并运行在端口 8011
# 详细部署说明请参考 id-generator 项目文档
```

验证 ID Generator Service:
```bash
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8011/actuator/health"

# Linux/macOS
curl http://localhost:8011/actuator/health
```

### 1. 启动基础设施

```bash
cd docker
docker-compose up -d
```

等待所有基础设施服务启动完成（约 2-3 分钟）。

### 2. 验证基础设施

```bash
# Windows PowerShell
.\scripts\verify-infrastructure.ps1

# Linux/macOS
./scripts/verify-infrastructure.sh
```

### 3. 构建服务镜像

```bash
# Windows PowerShell
.\scripts\build-services.ps1

# Linux/macOS
./scripts/build-services.sh
```

### 4. 启动应用服务

```bash
docker-compose -f docker-compose.services.yml up -d
```

## 详细部署步骤

### 步骤 1: 配置环境变量

复制并编辑环境变量文件：

```bash
cp .env.example .env
```

主要配置项：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `POSTGRES_HOST` | PostgreSQL 主机 | postgres |
| `POSTGRES_PORT` | PostgreSQL 端口 | 5432 |
| `POSTGRES_USER` | 数据库用户 | ZhiCore |
| `POSTGRES_PASSWORD` | 数据库密码 | ZhiCore123456 |
| `REDIS_HOST` | Redis 主机 | redis |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `NACOS_SERVER_ADDR` | Nacos 地址 | nacos:8848 |
| `ID_GENERATOR_SERVER_URL` | ID Generator 服务地址 | http://host.docker.internal:8011 |
| `ID_GENERATOR_TIMEOUT` | ID Generator 超时时间(ms) | 3000 |
| `ID_GENERATOR_RETRY_TIMES` | ID Generator 重试次数 | 3 |
| `ID_GENERATOR_MODE` | ID 生成模式 | snowflake |

**注意**: 
- 推荐在服务配置中使用规范键 `zhicore.id-generator.server-url`
- 运行时兼容环境变量 `ID_GENERATOR_SERVER_URL`
- Feign 客户端当前解析优先级为 `zhicore.id-generator.server-url` > `ID_GENERATOR_SERVER_URL` > `http://localhost:8011`
- `ID_GENERATOR_SERVER_URL` 使用 `host.docker.internal` 从 Docker 容器访问宿主机服务
- 本地直接 `mvn spring-boot:run` 启动服务时，默认应使用 `http://localhost:8011`
- 如果 ID Generator Service 部署在其他机器，请修改为实际地址

### 步骤 2: 初始化数据库

确保 PostgreSQL 中已创建以下数据库：

- `ZhiCore_user` - 用户服务数据库
- `ZhiCore_post` - 文章服务数据库
- `ZhiCore_comment` - 评论服务数据库
- `ZhiCore_message` - 消息服务数据库
- `ZhiCore_notification` - 通知服务数据库
- `ZhiCore_admin` - 管理服务数据库
- `nacos_config` - Nacos 配置数据库

### 步骤 3: 配置 Nacos

1. 访问 Nacos 控制台: http://localhost:8848/nacos
2. 默认账号: nacos / nacos
3. 导入配置文件（位于 `config/nacos/` 目录）

ID Generator 相关配置要求:

- `zhicore-content.yml` 和 `zhicore-comment.yml` 都必须存在于 Nacos，不能只上传 `content`
- 相关服务的 DataId 内应显式配置:

```yaml
zhicore:
  id-generator:
    server-url: ${ID_GENERATOR_SERVER_URL:http://localhost:8011}
```

- Docker 容器内通常通过环境变量把该值覆盖为 `http://host.docker.internal:8011`
- 如果缺少上述配置，Feign 会退回到基于服务名 `zhicore-id-generator` 的服务发现；本地联调未注册该服务时会直接调用失败

### 步骤 3.5: 配置文件存储 (可选)

**注意**: 文件存储功能已移至独立的 `file-service`。

如需使用文件上传功能，请参考 `file-service/docker/README.md` 启动独立的文件服务环境。file-service 提供：
- 独立的 RustFS 对象存储
- 多租户文件隔离 (通过 App ID)
- 独立的数据库和配置

### 步骤 4: 启动服务

按以下顺序启动服务：

1. **基础设施层**
   ```bash
   docker-compose up -d nacos rocketmq-namesrv rocketmq-broker elasticsearch
   ```

2. **网关服务**
   ```bash
   docker-compose -f docker-compose.services.yml up -d ZhiCore-gateway
   ```

3. **业务服务**
   ```bash
   docker-compose -f docker-compose.services.yml up -d ZhiCore-user ZhiCore-post ZhiCore-comment
   docker-compose -f docker-compose.services.yml up -d ZhiCore-message ZhiCore-notification
   docker-compose -f docker-compose.services.yml up -d ZhiCore-search ZhiCore-ranking ZhiCore-upload ZhiCore-admin
   ```

**注意**: 
- ZhiCore-leaf 服务已被移除，现在使用独立的 ID Generator Service
- 确保 ID Generator Service 在启动业务服务前已经运行

## 服务端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| ZhiCore-gateway | 8000 | API 网关 |
| ZhiCore-user | 8081 | 用户服务 |
| ZhiCore-post | 8082 | 文章服务 |
| ZhiCore-comment | 8083 | 评论服务 |
| ZhiCore-message | 8084 | 消息服务 |
| ZhiCore-notification | 8085 | 通知服务 |
| ZhiCore-search | 8086 | 搜索服务 |
| ZhiCore-ranking | 8087 | 排行榜服务 |
| ZhiCore-upload | 8089 | 上传服务 |
| ZhiCore-admin | 8090 | 管理服务 |

### 外部依赖服务

| 服务 | 端口 | 说明 |
|------|------|------|
| ID Generator Service | 8011 | 分布式 ID 生成服务 (外部独立部署) |

### 基础设施端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 | 服务注册与配置中心 |
| RocketMQ NameServer | 9876 | 消息队列 NameServer |
| RocketMQ Broker | 10911 | 消息队列 Broker |
| RocketMQ Dashboard | 8180 | RocketMQ 控制台 |
| Elasticsearch | 9200 | 搜索引擎 |
| Kibana | 5601 | ES 可视化 |
| Prometheus | 9090 | 监控数据采集 |
| Grafana | 3000 | 监控可视化 |
| SkyWalking UI | 8080 | 链路追踪 |

**注意**: 文件存储 (RustFS) 已移至独立的 file-service。详见 `file-service/docker/README.md`。

## 健康检查

### 检查服务状态

```bash
# 检查所有服务健康状态
curl http://localhost:8000/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # User Service
curl http://localhost:8082/actuator/health  # Post Service
curl http://localhost:8083/actuator/health  # Comment Service
curl http://localhost:8084/actuator/health  # Message Service
curl http://localhost:8085/actuator/health  # Notification Service
# ... 其他服务

# 检查 ID Generator Service (外部服务)
curl http://localhost:8011/actuator/health
```

### 检查 Nacos 注册

访问 Nacos 控制台查看服务注册情况：
http://localhost:8848/nacos/#/serviceManagement

### 检查 Prometheus 指标

访问 Prometheus 查看指标采集：
http://localhost:9090/targets

## 监控与告警

### Grafana 仪表板

访问 Grafana: http://localhost:3000
- 默认账号: admin / admin123456

预置仪表板：
- **ZhiCore Microservices Overview** - 服务概览
- **ZhiCore Services - SLA Dashboard** - SLA 指标监控

### 告警规则

告警规则位于 `prometheus/rules/` 目录：

| 文件 | 说明 |
|------|------|
| `sla-alerts.yml` | SLA 指标告警（响应时间、错误率等） |
| `leaf-alerts.yml` | ID 生成服务告警 |
| `gray-release-alerts.yml` | 灰度发布告警 |

### SLA 指标

| 指标 | 目标 | 告警阈值 |
|------|------|---------|
| 可用性 | 99.9% | 服务下线 > 1 分钟 |
| P95 响应时间 | < 500ms | > 500ms 持续 5 分钟 |
| 错误率 | < 1% | > 1% 持续 5 分钟 |

## 故障排查

### 服务无法启动

1. 检查日志：
   ```bash
   docker logs ZhiCore-user
   ```

2. 检查依赖服务：
   ```bash
   docker-compose ps
   ```

3. 检查网络连接：
   ```bash
   docker network inspect ZhiCore-network
   ```

### 服务注册失败

1. 检查 Nacos 是否正常运行
2. 检查服务配置中的 Nacos 地址
3. 检查网络连通性

### 数据库连接失败

1. 检查 PostgreSQL 是否正常运行
2. 检查数据库用户权限
3. 检查连接池配置

### 消息队列问题

1. 检查 RocketMQ NameServer 和 Broker 状态
2. 访问 RocketMQ Dashboard 查看消息积压
3. 检查消费者组配置

### ID Generator Service 连接问题

1. **检查 ID Generator Service 是否运行**
   ```bash
   curl http://localhost:8011/actuator/health
   ```

2. **检查网络连通性**
   - 从 Docker 容器内访问: `http://host.docker.internal:8011`
   - 从宿主机访问: `http://localhost:8011`

3. **检查环境变量配置**
   ```bash
   # 查看服务配置
   docker exec ZhiCore-notification env | grep ID_GENERATOR
   ```

4. **检查 Nacos 配置是否完整**
   - 确认 `zhicore-content.yml`、`zhicore-comment.yml` 已上传到目标 Group
   - 确认配置键为 `zhicore.id-generator.server-url`，而不是只保留自定义根级键
   - 本地直启时如果未额外传环境变量，最终应解析为 `http://localhost:8011`

5. **常见错误**
   - `Connection refused`: ID Generator Service 未启动
   - `Timeout`: 网络配置问题或服务响应慢
   - `404 Not Found`: ID Generator Service 版本不匹配
   - `URL not provided. Will try picking an instance via load-balancing.`: 未解析到直连 URL，Feign 已退回服务发现
   - `No servers available for service: zhicore-id-generator`: 服务发现回退后未在 Nacos 中找到 `zhicore-id-generator`

6. **解决方案**
   - 确保 ID Generator Service 已启动: `docker ps | grep id-generator`
   - 检查端口映射: `netstat -an | grep 8011`
   - 查看服务日志: `docker logs ZhiCore-notification | grep "ID Generator"`
   - 检查 Nacos DataId 是否上传完整，尤其不要漏掉 `zhicore-comment.yml`
   - 优先修正 `zhicore.id-generator.server-url`，不要依赖 Feign 自动回退到服务发现

## 生产环境建议

### 安全配置

1. 修改所有默认密码
2. 启用 HTTPS
3. 配置防火墙规则
4. 启用 JWT Token 黑名单

### 性能优化

1. 根据负载调整 JVM 参数
2. 配置合适的数据库连接池大小
3. 启用 Redis 集群模式
4. 配置 CDN 加速静态资源

### 高可用部署

1. 部署多个服务实例
2. 使用负载均衡器
3. 配置数据库主从复制
4. 配置 Redis 哨兵或集群模式
