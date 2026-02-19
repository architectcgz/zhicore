# Blog Microservices Docker 开发环境

## 概述

本目录包含博客微服务系统的 Docker Compose 开发环境配置。

## 基础设施验证

启动服务后，可以使用验证脚本检查所有组件是否正常运行：

```bash
# Windows PowerShell
.\docker\scripts\verify-infrastructure.ps1

# Linux/Mac
chmod +x docker/scripts/verify-infrastructure.sh
./docker/scripts/verify-infrastructure.sh
```

验证脚本会检查以下组件：
1. Docker Compose 服务状态
2. Nacos 服务注册与配置
3. Gateway 路由转发
4. Leaf ID 生成服务
5. RocketMQ 消息队列
6. Elasticsearch 搜索引擎
7. 监控栈 (Prometheus/Grafana)
8. SkyWalking 链路追踪

**注意**: RustFS 对象存储已移至独立的 file-service。详见 `file-service/docker/README.md`。

## 服务清单

| 服务 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 | 服务注册与配置中心 |
| RocketMQ NameServer | 9876 | 消息队列名称服务 |
| RocketMQ Broker | 10911 | 消息队列代理 |
| RocketMQ Dashboard | 8180 | 消息队列管理界面 |
| Elasticsearch | 9200 | 全文搜索引擎 |
| Kibana | 5601 | ES 可视化界面 |
| Prometheus | 9090 | 监控数据采集 |
| Grafana | 3000 | 监控可视化 |
| SkyWalking OAP | 11800/12800 | 链路追踪后端 |
| SkyWalking UI | 8080 | 链路追踪界面 |

**注意**: 文件存储服务 (RustFS) 已移至独立的 `file-service`。如需使用文件上传功能，请参考 `file-service/docker/README.md` 启动独立的文件服务环境。

## 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- 已运行的 PostgreSQL (端口 5432)
- 已运行的 Redis (端口 6500)

## 快速启动

```bash
# 进入 docker 目录
cd docker

# 启动所有服务（后台运行）
docker-compose up -d

# 或使用启动脚本
# Windows PowerShell
.\scripts\start-infrastructure.ps1 -Detached

# Linux/Mac
chmod +x scripts/start-infrastructure.sh
./scripts/start-infrastructure.sh -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f [service_name]

# 验证所有服务
# Windows PowerShell
.\scripts\verify-infrastructure.ps1

# Linux/Mac
./scripts/verify-infrastructure.sh
```

## 服务访问

- Nacos Console: http://localhost:8848/nacos (nacos/nacos)
- RocketMQ Dashboard: http://localhost:8180
- Kibana: http://localhost:5601
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin123456)
- SkyWalking UI: http://localhost:8080

**文件服务**: 文件存储 (RustFS) 已移至独立的 file-service，请参考 `file-service/docker/README.md`。

## 网络配置

所有服务连接到 `blog-network` 网络。如需连接现有的 PostgreSQL 和 Redis 容器，请确保它们也加入此网络：

```bash
# 将现有容器加入网络
docker network connect blog-network your-postgres-container
docker network connect blog-network your-redis-container
```

## 数据持久化

所有数据卷以 `blog_` 前缀命名：

- `blog_mysql_nacos_data` - Nacos MySQL 数据
- `blog_rocketmq_*` - RocketMQ 日志和存储
- `blog_elasticsearch_data` - ES 索引数据
- `blog_prometheus_data` - 监控数据
- `blog_grafana_data` - Grafana 配置

**注意**: 文件存储数据卷已移至 file-service 独立管理。

## 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 故障排查

### Nacos 启动失败
确保 PostgreSQL 中已创建 `nacos_config` 数据库。

### Elasticsearch 启动失败
检查系统 vm.max_map_count 设置：
```bash
# Linux
sudo sysctl -w vm.max_map_count=262144

# Windows (WSL2)
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

### RocketMQ Broker 连接失败
检查 broker.conf 中的 namesrvAddr 配置是否正确。

## 手动验证指南

### 1. 验证 Docker Compose 服务状态

```bash
cd docker
docker-compose ps
```

所有服务应显示 "Up" 或 "healthy" 状态。

### 2. 验证 Nacos 服务注册与配置

1. 访问 Nacos Console: http://localhost:8848/nacos
2. 使用默认账号登录: nacos/nacos
3. 检查服务列表中是否有 `blog-leaf` 和 `blog-gateway` 服务
4. 检查配置管理中是否有 `common.yml` 和 `blog-leaf.yml` 配置

```bash
# API 验证
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10
```

### 3. 验证 Gateway 路由转发

```bash
# 检查 Gateway 健康状态
curl http://localhost:8000/actuator/health

# 检查路由配置
curl http://localhost:8000/actuator/gateway/routes

# 通过 Gateway 访问 Leaf 服务
curl http://localhost:8000/api/v1/leaf/id
```

### 4. 验证 Leaf ID 生成

```bash
# 直接访问 Leaf 服务
curl http://localhost:8010/api/v1/leaf/id

# 批量生成 ID
curl http://localhost:8010/api/v1/leaf/ids?count=5

# 解析 ID（替换 {id} 为实际 ID）
curl http://localhost:8010/api/v1/leaf/parse/{id}
```

### 5. 验证 RocketMQ 消息队列

1. 访问 RocketMQ Dashboard: http://localhost:8180
2. 检查 Broker 状态是否正常
3. 检查 Topic 列表

```bash
# 检查 NameServer 端口
nc -zv localhost 9876

# 检查 Broker 端口
nc -zv localhost 10911
```

### 6. 验证 Elasticsearch

```bash
# 检查集群健康状态
curl http://localhost:9200/_cluster/health

# 检查节点信息
curl http://localhost:9200/_nodes
```

### 7. 验证监控栈

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin123456)

### 8. 验证 SkyWalking

- SkyWalking UI: http://localhost:8080

```bash
# 检查 OAP 端口
nc -zv localhost 11800
nc -zv localhost 12800
```
