---
inclusion: fileMatch
fileMatchPattern: '**/docker-compose*.{yml,yaml}'
---

# 基础设施与端口

[返回索引](./README-zh.md)

---

## 端口映射概念

Docker 端口映射格式：`HOST_PORT:CONTAINER_PORT`
- **HOST_PORT**：本地机器访问端口
- **CONTAINER_PORT**：Docker 内部端口

示例：`6379:6379` 表示：
- 本地访问：`localhost:6379`
- 容器内访问：`redis:6379`

---

## 基础设施服务端口示例

### PostgreSQL
- 容器名：`{project}-postgres`
- 端口：`5432`（或项目特定端口）
- 用户：`postgres`
- 密码：根据项目配置

### Redis
- 容器名：`{project}-redis`
- 端口：`6379`（或项目特定端口）
- 密码：根据项目配置

### Nacos
- 容器名：`{project}-nacos`
- 控制台：`http://localhost:8848/nacos`
- 用户名/密码：`nacos/nacos`

### RocketMQ
- NameServer：`localhost:9876`
- Broker：`10911`
- Dashboard：`http://localhost:8180`

### Elasticsearch
- HTTP：`http://localhost:9200`
- Transport：`9300`

---

## 端口分配原则

### 1. 避免端口冲突
- 检查系统中已使用的端口
- 为不同项目分配不同的端口范围
- 记录端口分配情况

### 2. 端口命名规范
- 容器名使用项目前缀：`{project}-{service}`
- 避免使用通用名称如 `redis`、`postgres`

### 3. 端口配置管理
- 在项目的 `infrastructure-ports.md` 中记录所有端口
- 使用环境变量配置端口
- 支持本地开发和 Docker 部署两种模式

**注意**：具体的端口分配和限制请参考各项目的 `infrastructure-ports.md` 文档。

---

**最后更新**：2026-02-01
