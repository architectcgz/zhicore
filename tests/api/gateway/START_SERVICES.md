# 启动网关测试所需的服务

## 基础设施服务状态

✅ PostgreSQL (ZhiCore-postgres) - 运行中
✅ Redis (ZhiCore-redis) - 运行中  
✅ Nacos (ZhiCore-nacos) - 运行中

## 需要启动的微服务

请在Maven控制台或IDE中启动以下服务：

### 1. ZhiCore-user (端口 8081)
```bash
cd ZhiCore-user
mvn spring-boot:run
```

### 2. ZhiCore-post (端口 8082)
```bash
cd ZhiCore-post
mvn spring-boot:run
```

### 3. ZhiCore-gateway (端口 8000)
```bash
cd ZhiCore-gateway
mvn spring-boot:run
```

## 启动顺序建议

1. 先启动 ZhiCore-user (用户服务)
2. 再启动 ZhiCore-post (文章服务)
3. 最后启动 ZhiCore-gateway (网关服务)

## 验证服务启动

启动完成后，可以通过以下命令验证：

```powershell
# 检查服务端口
Test-NetConnection -ComputerName localhost -Port 8000  # Gateway
Test-NetConnection -ComputerName localhost -Port 8081  # User
Test-NetConnection -ComputerName localhost -Port 8082  # Post
```

或者访问健康检查端点：
- Gateway: http://localhost:8000/actuator/health
- User: http://localhost:8081/actuator/health
- Post: http://localhost:8082/actuator/health

## 运行测试

服务启动完成后，运行测试脚本：

```powershell
cd tests/api/gateway
.\test-gateway-api-full.ps1
```
