# Admin API 测试指南

## 前置条件

在运行管理员API测试之前,需要确保以下服务正在运行:

### 必需的基础设施服务
- PostgreSQL (端口 5432)
- Redis (端口 6379)
- Nacos (端口 8848)
- RocketMQ (端口 9876)

### 必需的微服务
- ZhiCore-leaf (端口 8010) - ID生成服务
- ZhiCore-user (端口 8081) - 用户服务
- ZhiCore-post (端口 8082) - 文章服务  
- ZhiCore-comment (端口 8083) - 评论服务
- ZhiCore-admin (端口 8090) - 管理服务

## 启动服务

### 方法1: 使用 Docker Compose (推荐)

```powershell
# 1. 启动基础设施
cd docker
docker-compose up -d

# 2. 构建并启动微服务
docker-compose -f docker-compose.services.yml build
docker-compose -f docker-compose.services.yml up -d ZhiCore-leaf ZhiCore-user ZhiCore-post ZhiCore-comment ZhiCore-admin

# 3. 等待服务启动 (约2-3分钟)
Start-Sleep -Seconds 120

# 4. 验证服务健康状态
docker-compose -f docker-compose.services.yml ps
```

### 方法2: 本地Maven启动 (开发环境)

```powershell
# 确保基础设施服务已启动
cd docker
docker-compose up -d

# 返回项目根目录
cd ..

# 启动各个服务 (每个服务在单独的终端窗口)
# 终端1:
cd ZhiCore-leaf
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 终端2:
cd ZhiCore-user  
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 终端3:
cd ZhiCore-post
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 终端4:
cd ZhiCore-comment
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 终端5:
cd ZhiCore-admin
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 验证服务状态

运行以下命令检查所有服务是否正常:

```powershell
# 检查服务健康状态
$ports = @(8010, 8081, 8082, 8083, 8090)
foreach ($port in $ports) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -TimeoutSec 3
        Write-Host "[PASS] Port $port is healthy" -ForegroundColor Green
    } catch {
        Write-Host "[FAIL] Port $port is not responding" -ForegroundColor Red
    }
}
```

## 运行测试

所有服务启动并健康后,运行管理员API测试:

```powershell
# 从项目根目录运行
.\tests\api\admin\test-admin-api-full.ps1
```

## 测试覆盖范围

该测试脚本包含 25 个测试用例,覆盖以下功能:

### Section 1: 用户管理 (7个测试)
- ADMIN-001: 获取用户列表
- ADMIN-002: 搜索用户
- ADMIN-003: 禁用用户
- ADMIN-004: 启用用户
- ADMIN-005: 禁用不存在的用户 (错误处理)
- ADMIN-006: 非管理员访问 (权限测试)
- ADMIN-007: 获取用户详情

### Section 2: 文章管理 (6个测试)
- ADMIN-008: 获取文章列表
- ADMIN-009: 搜索文章
- ADMIN-010: 删除文章
- ADMIN-011: 删除不存在的文章 (错误处理)
- ADMIN-012: 按作者筛选文章
- ADMIN-013: 按状态筛选文章

### Section 3: 评论管理 (6个测试)
- ADMIN-014: 获取评论列表
- ADMIN-015: 搜索评论
- ADMIN-016: 删除评论
- ADMIN-017: 删除不存在的评论 (错误处理)
- ADMIN-018: 按文章筛选评论
- ADMIN-019: 按用户筛选评论

### Section 4: 举报管理 (6个测试)
- ADMIN-020: 获取待处理举报
- ADMIN-021: 按状态筛选举报
- ADMIN-022: 处理举报 (批准)
- ADMIN-023: 处理不存在的举报 (错误处理)
- ADMIN-024: 处理举报 (拒绝)
- ADMIN-025: 无效的举报操作 (错误处理)

## 测试结果

测试结果将:
1. 在控制台显示详细的测试执行情况
2. 更新 `tests/results/test-status.md` 文件
3. 返回退出码 (0=全部通过, 1=存在失败)

## 故障排查

### 服务无法启动
- 检查端口是否被占用: `netstat -ano | findstr "8081"`
- 检查Docker容器状态: `docker ps`
- 查看服务日志: `docker logs ZhiCore-user`

### 测试失败
- 确认所有服务都已启动并健康
- 检查配置文件 `tests/config/test-env.json`
- 查看测试输出中的错误信息

### 数据库连接问题
- 确认PostgreSQL容器正在运行
- 检查数据库是否已初始化
- 验证数据库连接配置

## 清理

测试完成后,停止服务:

```powershell
# Docker方式
cd docker
docker-compose -f docker-compose.services.yml down

# Maven方式
Get-Process | Where-Object { $_.ProcessName -eq 'java' } | Stop-Process -Force
```
