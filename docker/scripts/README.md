# Docker 脚本说明

本目录包含用于管理 Docker 环境的实用脚本。

## 数据库初始化脚本

### init-databases.ps1

**用途**: 在 Docker PostgreSQL 容器中初始化所有微服务数据库

**使用方法**:
```powershell
.\init-databases.ps1
```

**自定义参数**:
```powershell
.\init-databases.ps1 `
    -ContainerName "your-postgres-container" `
    -PostgresUser "your-username" `
    -PostgresPassword "your-password"
```

**功能**:
- 检查 PostgreSQL 容器状态
- 创建所有必需的数据库
- 复制并执行 SQL 迁移脚本
- 自动清理临时文件

### verify-databases.ps1

**用途**: 验证所有数据库和表是否已成功创建

**使用方法**:
```powershell
.\verify-databases.ps1
```

**功能**:
- 检查所有数据库是否存在
- 验证每个数据库中的表
- 生成详细的验证报告

## 基础设施管理脚本

### start-infrastructure.ps1

**用途**: 启动所有基础设施服务（Nacos, RocketMQ, Elasticsearch 等）

**使用方法**:
```powershell
.\start-infrastructure.ps1
```

### verify-infrastructure.ps1

**用途**: 验证所有基础设施服务是否正常运行

**使用方法**:
```powershell
.\verify-infrastructure.ps1
```

## 服务构建脚本

### build-services.ps1

**用途**: 构建所有微服务的 Docker 镜像

**使用方法**:
```powershell
.\build-services.ps1
```

## 测试脚本

### run-integration-tests.ps1

**用途**: 运行集成测试

**使用方法**:
```powershell
.\run-integration-tests.ps1
```

### run-acceptance-tests.ps1

**用途**: 运行验收测试

**使用方法**:
```powershell
.\run-acceptance-tests.ps1
```

## 快速开始

### 1. 启动基础设施

```powershell
cd docker/scripts
.\start-infrastructure.ps1
.\verify-infrastructure.ps1
```

### 2. 初始化数据库

```powershell
.\init-databases.ps1
.\verify-databases.ps1
```

### 3. 构建服务

```powershell
.\build-services.ps1
```

### 4. 启动服务

```powershell
cd ..
docker-compose -f docker-compose.yml -f docker-compose.services.yml up -d
```

## 注意事项

1. **执行策略**: 如果遇到执行策略错误，运行：
   ```powershell
   Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
   ```

2. **Docker 环境**: 确保 Docker Desktop 已启动并运行

3. **网络连接**: 某些脚本需要从 Docker Hub 拉取镜像

4. **权限**: 某些操作可能需要管理员权限

## 相关文档

- [Docker 数据库初始化指南](../DATABASE_INIT_GUIDE.md)
- [Docker 部署指南](../DEPLOYMENT.md)
- [基础设施 README](../README.md)
