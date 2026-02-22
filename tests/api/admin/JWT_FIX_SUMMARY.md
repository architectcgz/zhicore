# JWT 配置修复总结

## 问题描述

测试脚本直接调用 ZhiCore-post 服务（绕过网关）创建文章时失败，返回 HTTP 400 错误："参数值无效"。

## 根本原因

1. **JWT Secret 不一致**：
   - ZhiCore-user 服务使用自己的 JWT secret 签发 token
   - ZhiCore-post 服务使用不同的 JWT secret 验证 token
   - 签名验证失败，导致 `UserContext.getUserId()` 返回 null
   - `Post.createDraft()` 收到 null 的 userId，抛出 `IllegalArgumentException: 作者ID不能为空`

2. **配置未统一**：
   - 各服务没有导入 Nacos 的 `common.yml` 配置
   - JWT 配置分散在各服务的 `application.yml` 中
   - 配置属性名称不一致

## 已完成的修复

### 1. 创建统一的 JWT 配置

**文件**: `config/nacos/common.yml`

```yaml
# JWT 配置（所有服务统一使用）
jwt:
  secret: your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-chars
  access-token-expiration: 7200
  refresh-token-expiration: 604800
```

### 2. 更新 ZhiCore-user 配置

**文件**: `ZhiCore-user/src/main/resources/application.yml`

**修改前**:
```yaml
spring:
  application:
    name: ZhiCore-user
  config:
    import: optional:nacos:ZhiCore-user.yml?group=ZhiCore_SERVICE&refreshEnabled=true

# JWT 配置
jwt:
  secret: ${JWT_SECRET:ZhiCore-microservices-jwt-secret-key-must-be-at-least-256-bits}
  access-token-expiration: 3600
  refresh-token-expiration: 604800
```

**修改后**:
```yaml
spring:
  application:
    name: ZhiCore-user
  config:
    import:
      - optional:nacos:common.yml?group=ZhiCore_SERVICE&refreshEnabled=true
      - optional:nacos:ZhiCore-user.yml?group=ZhiCore_SERVICE&refreshEnabled=true

# JWT 配置已移至 common.yml
```

### 3. 更新 ZhiCore-post 配置

**文件**: `ZhiCore-post/src/main/resources/application.yml`

**修改前**:
```yaml
spring:
  application:
    name: ZhiCore-post
  config:
    import: optional:nacos:ZhiCore-post.yml?group=ZhiCore_SERVICE&refreshEnabled=true
```

**修改后**:
```yaml
spring:
  application:
    name: ZhiCore-post
  config:
    import:
      - optional:nacos:common.yml?group=ZhiCore_SERVICE&refreshEnabled=true
      - optional:nacos:ZhiCore-post.yml?group=ZhiCore_SERVICE&refreshEnabled=true
```

### 4. 推送配置到 Nacos

```powershell
.\config\nacos\update-common-config.ps1
```

### 5. 重启服务

```powershell
# 停止所有服务
jps -l | Select-String "ZhiCore-" | ForEach-Object { 
    $pid = $_.ToString().Split()[0] 
    Stop-Process -Id $pid -Force 
}

# 启动服务
.\start-admin-services-local.ps1
```

## 验证步骤

### 1. 检查 Nacos 配置

访问 Nacos 控制台：http://localhost:8848/nacos

- 命名空间：public
- Group：ZhiCore_SERVICE
- Data ID：common.yml

确认包含 JWT 配置。

### 2. 测试文章创建

```powershell
.\tests\api\admin\test-post-direct.ps1
```

**预期结果**：
- 用户注册成功
- 登录成功，获取 JWT token
- 文章创建成功，返回文章 ID
- 文章验证成功

### 3. 运行完整测试

```powershell
.\tests\api\admin\test-admin-api-full.ps1
```

## 技术细节

### JWT Token 流程

1. **用户登录** (ZhiCore-user):
   - 使用 `jwt.secret` 签发 JWT token
   - Token 包含 `sub` (userId), `userName`, `email`, `roles`

2. **直接调用服务** (ZhiCore-post):
   - `UserContextFilter` 从 `Authorization` header 提取 token
   - 使用 `jwt.secret` 验证签名
   - 解析 payload，设置 `UserContext`

3. **创建文章** (PostApplicationService):
   - 从 `UserContext.getUserId()` 获取用户 ID
   - 调用 `Post.createDraft(postId, userId, title, content)`
   - 保存文章到数据库

### 关键代码

**UserContextFilter.java**:
```java
@Value("${jwt.secret:your-256-bit-secret-key-for-jwt-token-generation}")
private String jwtSecret;

private Claims parseToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

**JwtTokenProvider.java** (ZhiCore-user):
```java
@Value("${jwt.secret:your-256-bit-secret-key-for-jwt-token-generation}")
private String jwtSecret;

@Value("${jwt.access-token-expiration:3600}")
private Long accessTokenExpiration;
```

## 注意事项

1. **配置优先级**：
   - Nacos 配置优先于本地 `application.yml`
   - `common.yml` 应该在服务特定配置之前导入

2. **属性名称**：
   - 使用 `access-token-expiration` 而不是 `access-token-expire`
   - 保持与代码中 `@Value` 注解一致

3. **服务重启**：
   - 修改配置后必须重启服务才能生效
   - Nacos 的 `refreshEnabled=true` 只对部分配置生效，JWT secret 需要重启

4. **安全性**：
   - 生产环境应使用更强的 JWT secret（至少 256 位）
   - 建议使用环境变量或密钥管理服务

## 后续优化建议

1. **统一配置管理**：
   - 所有服务都应导入 `common.yml`
   - 将通用配置（Redis、RocketMQ、Sentinel 等）统一管理

2. **配置验证**：
   - 添加启动时的配置验证
   - 确保关键配置（如 JWT secret）已正确加载

3. **测试改进**：
   - 测试脚本应该通过网关调用服务
   - 添加 JWT 配置验证的集成测试

4. **文档完善**：
   - 更新部署文档，说明配置依赖关系
   - 添加故障排查指南

## 相关文件

- `config/nacos/common.yml` - 统一配置
- `ZhiCore-user/src/main/resources/application.yml` - 用户服务配置
- `ZhiCore-post/src/main/resources/application.yml` - 文章服务配置
- `ZhiCore-common/src/main/java/com/ZhiCore/common/filter/UserContextFilter.java` - JWT 解析
- `ZhiCore-user/src/main/java/com/ZhiCore/user/infrastructure/security/JwtTokenProvider.java` - JWT 生成

## 测试脚本

- `tests/api/admin/test-post-direct.ps1` - 直接测试文章创建
- `tests/api/admin/test-jwt-parsing.ps1` - JWT 解析测试
- `tests/api/admin/diagnose-post-creation.ps1` - 诊断脚本
- `tests/api/admin/test-admin-api-full.ps1` - 完整管理 API 测试
