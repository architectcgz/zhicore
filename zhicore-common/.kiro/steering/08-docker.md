---
inclusion: fileMatch
fileMatchPattern: '{**/Dockerfile,**/docker-compose*.{yml,yaml},.dockerignore}'
---

# Docker 使用规范

[返回索引](./README-zh.md)

---

## 核心规则

**永远使用 docker-compose 配置文件来管理容器，而不是直接使用 docker 命令！**

---

## 原因

- 直接使用 `docker` 命令会影响所有同名容器
- 项目中可能有多个服务使用相同的容器名称
- 使用 docker-compose 可以确保只操作当前项目的容器

---

## 正确做法

```powershell
# ✅ 正确 - 使用 docker-compose
cd docker
docker-compose up -d redis

# ❌ 错误 - 直接使用 docker 命令
docker start redis
docker start ZhiCore-redis
```

---

## 常用命令

```powershell
# 启动基础设施
cd docker
docker-compose up -d

# 启动微服务
cd docker
docker-compose -f docker-compose.services.yml up -d

# 查看运行状态
docker-compose ps

# 停止所有服务
docker-compose down
docker-compose -f docker-compose.services.yml down
```

---

**最后更新**：2026-02-01
