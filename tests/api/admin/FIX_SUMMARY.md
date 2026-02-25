# Admin API 测试问题修复总结

## 问题诊断

通过快速测试脚本 `test-admin-quick.ps1` 发现：

1. ✅ **直接访问 admin 服务成功** (localhost:8090)
   - 说明 admin 服务本身工作正常
   - 权限验证正常

2. ❌ **通过网关访问失败** (localhost:8000)
   - 返回 401 未授权错误
   - 说明网关的 JWT 认证失败

## 根本原因

**JWT Secret 不一致**：

- **Gateway** (`ZhiCore-gateway/src/main/resources/application.yml`):
  ```yaml
  jwt:
    secret: ${JWT_SECRET:ZhiCore-microservices-jwt-secret-key-must-be-at-least-256-bits}
  ```

- **User Service** (`ZhiCore-user/src/main/resources/application.yml`):
  ```yaml
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-chars}
  ```

由于 secret 不一致，网关无法验证用户服务生成的 JWT token，导致所有需要认证的请求都返回 401。

## 修复方案

### 1. 统一 JWT Secret

已修改 `ZhiCore-user/src/main/resources/application.yml`，使用与网关相同的 secret：

```yaml
jwt:
  secret: ${JWT_SECRET:ZhiCore-microservices-jwt-secret-key-must-be-at-least-256-bits}
```

### 2. 修复测试脚本路由

已修改 `test-admin-api-full.ps1`，确保正确的路由：

```powershell
# 修改前（错误）
$AdminServiceUrl = "$GatewayUrl/api/v1/admin"  # 会导致 /api/v1/admin/admin/users

# 修改后（正确）
$AdminServiceUrl = "$GatewayUrl/api/v1"  # 配合 StripPrefix=2，正确路由到 /admin/users
```

## 需要执行的操作

### 重启服务

需要重启以下服务以应用配置更改：

1. **ZhiCore-user** (端口 8081)
   ```bash
   # 停止当前运行的 ZhiCore-user
   # 重新启动
   mvn spring-boot:run -pl ZhiCore-user
   ```

2. **ZhiCore-gateway** (端口 8000) - 如果之前已重启过，可以跳过
   ```bash
   # 停止当前运行的 ZhiCore-gateway
   # 重新启动
   mvn spring-boot:run -pl ZhiCore-gateway
   ```

### 验证修复

重启服务后，运行快速测试：

```powershell
cd tests/api/admin
.\test-admin-quick.ps1
```

预期结果：
- Step 5 (直接访问): ✅ SUCCESS
- Step 6 (通过网关): ✅ SUCCESS

### 运行完整测试

验证通过后，运行完整的管理后台测试：

```powershell
cd tests/api/admin
.\test-admin-api-full.ps1
```

## 技术细节

### JWT 认证流程

1. 用户登录 → 用户服务生成 JWT token (使用 user service 的 secret)
2. 客户端请求 → 网关验证 JWT token (使用 gateway 的 secret)
3. 如果 secret 不一致 → 验证失败 → 返回 401

### 网关路由配置

```yaml
# 管理服务路由
- id: ZhiCore-admin
  uri: lb://ZhiCore-admin
  predicates:
    - Path=/api/v1/admin/**
  filters:
    - StripPrefix=2  # 移除 /api/v1
```

路由转换：
- 请求: `GET /api/v1/admin/users`
- StripPrefix=2 移除 `/api/v1`
- 转发到 ZhiCore-admin: `GET /admin/users`

## 相关文件

- `ZhiCore-user/src/main/resources/application.yml` - 已修改
- `ZhiCore-gateway/src/main/resources/application.yml` - 无需修改
- `tests/api/admin/test-admin-api-full.ps1` - 已修改
- `tests/api/admin/test-admin-quick.ps1` - 新增（快速验证脚本）

## 后续建议

1. **环境变量配置**: 在生产环境中，应通过环境变量 `JWT_SECRET` 统一配置所有服务的 JWT secret
2. **配置中心**: 考虑使用 Nacos 配置中心统一管理 JWT 配置
3. **测试自动化**: 将快速测试脚本集成到 CI/CD 流程中
