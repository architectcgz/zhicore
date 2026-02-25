---
inclusion: always
---

# Docker Compose 使用规范

## 核心规则

**永远使用 docker-compose 配置文件来管理容器，而不是直接使用 docker 命令！**

## 原因

- 直接使用 `docker` 命令（如 `docker start`, `docker stop`）会影响所有同名容器
- 项目中可能有多个服务使用相同的容器名称（如 `redis`, `postgres`）
- 使用 docker-compose 可以确保只操作当前项目的容器

## 正确做法

### 启动服务

```powershell
# ✅ 正确 - 使用 docker-compose 在指定目录
cd docker
docker-compose up -d redis

# ✅ 正确 - 使用 path 参数
docker-compose up -d redis
# 使用 path 参数: docker/

# ❌ 错误 - 直接使用 docker 命令
docker start redis
docker start ZhiCore-redis
```

### 停止服务

```powershell
# ✅ 正确
cd docker
docker-compose stop redis

# ❌ 错误
docker stop redis
docker stop ZhiCore-redis
```

### 重启服务

```powershell
# ✅ 正确
cd docker
docker-compose restart redis

# ❌ 错误
docker restart redis
```

### 查看日志

```powershell
# ✅ 正确
cd docker
docker-compose logs -f redis

# ✅ 也可以 - 查看日志可以直接用 docker
docker logs -f ZhiCore-redis
```

## 项目结构

```
ZhiCore-microservice/
├── docker/
│   ├── docker-compose.yml          # 基础设施服务
│   ├── docker-compose.services.yml # 微服务
│   ├── postgres-init/
│   ├── redis/
│   └── ...
```

## 常用命令

### 启动基础设施

```powershell
cd docker
docker-compose up -d postgres redis
```

### 启动所有基础设施

```powershell
cd docker
docker-compose up -d
```

### 启动微服务

```powershell
cd docker
docker-compose -f docker-compose.services.yml up -d
```

### 查看运行状态

```powershell
cd docker
docker-compose ps
```

### 停止所有服务

```powershell
cd docker
docker-compose down
docker-compose -f docker-compose.services.yml down
```

## 为什么这很重要

### 场景示例

假设系统中有两个项目：
- `ZhiCore-microservice` 使用 `ZhiCore-redis` (端口 6800)
- `im-service` 使用 `im-redis` (端口 16379)

如果使用 `docker start redis`，可能会：
1. 启动错误的 Redis 容器
2. 影响其他项目的服务
3. 导致端口冲突

使用 docker-compose 可以确保：
1. 只操作当前项目的容器
2. 使用正确的配置和端口
3. 不影响其他项目

## 检查清单

在操作 Docker 容器前，确认：

- [ ] 是否在正确的目录（`docker/`）
- [ ] 是否使用 `docker-compose` 命令
- [ ] 是否指定了正确的配置文件（如果需要）
- [ ] 是否使用了服务名称而不是容器名称

## 错误示例和修正

### 错误 1: 直接启动容器

```powershell
# ❌ 错误
docker start ZhiCore-redis

# ✅ 修正
cd docker
docker-compose up -d redis
```

### 错误 2: 在错误的目录

```powershell
# ❌ 错误 - 在项目根目录
docker-compose up -d redis

# ✅ 修正
cd docker
docker-compose up -d redis
```

### 错误 3: 使用容器名而不是服务名

```powershell
# ❌ 错误
cd docker
docker-compose up -d ZhiCore-redis

# ✅ 修正
cd docker
docker-compose up -d redis
```

## 服务名称映射

| 服务名称 (docker-compose) | 容器名称 | 端口 |
|---------------------------|---------|------|
| postgres | ZhiCore-postgres | 5432 |
| redis | ZhiCore-redis | 6800 |
| mysql-nacos | ZhiCore-mysql-nacos | 3307 |
| nacos | ZhiCore-nacos | 8848 |
| rocketmq-namesrv | ZhiCore-rocketmq-namesrv | 9876 |
| rocketmq-broker | ZhiCore-rocketmq-broker | 10911 |
| elasticsearch | ZhiCore-elasticsearch | 9200 |

## 总结

**记住：永远使用 docker-compose 配置文件来管理容器！**

这样可以：
- ✅ 避免影响其他项目
- ✅ 使用正确的配置
- ✅ 保持环境一致性
- ✅ 便于团队协作
