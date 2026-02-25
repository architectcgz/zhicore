# ZhiCore-Admin Feign Client 问题修复

## 问题描述

管理后台 API 测试失败，所有通过 ZhiCore-admin 调用后端服务的接口都返回"系统繁忙，请稍后重试"错误。

## 诊断结果

通过 `diagnose-feign.ps1` 脚本诊断发现：

1. **直接调用 ZhiCore-user 的 admin 端点成功** ✅
   - URL: `http://localhost:8081/admin/users`
   - 说明 ZhiCore-user 服务本身工作正常

2. **通过 gateway 调用 ZhiCore-admin 失败** ❌
   - URL: `http://localhost:8000/api/v1/admin/users`
   - 错误: "系统繁忙，请稍后重试"
   - 说明 ZhiCore-admin 的 Feign 客户端无法调用 ZhiCore-user

## 根本原因

**ZhiCore-admin 服务缺少 `bootstrap.yml` 配置文件**

- ZhiCore-admin 无法正确连接到 Nacos 服务注册中心
- Feign 客户端无法发现 ZhiCore-user 服务实例
- 所有 Feign 调用都进入 fallback 逻辑，返回"系统繁忙"错误

## 解决方案

### 1. 创建 bootstrap.yml

已创建 `ZhiCore-admin/src/main/resources/bootstrap.yml`:

```yaml
spring:
  application:
    name: ZhiCore-admin
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER:localhost:8848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        namespace: ${NACOS_NAMESPACE:}
        group: ZhiCore_SERVICE
        enabled: true
      config:
        namespace: ${NACOS_NAMESPACE:}
        group: ZhiCore_SERVICE
        file-extension: yml
        enabled: true
        refresh-enabled: true
```

### 2. 重启 ZhiCore-admin 服务

```powershell
# 停止当前运行的 ZhiCore-admin
# 然后重新启动
cd ZhiCore-admin
mvn spring-boot:run
```

### 3. 验证服务注册

访问 Nacos 控制台: http://localhost:8848/nacos
- 用户名: nacos
- 密码: nacos

确认以下服务已注册到 `ZhiCore_SERVICE` 组：
- ZhiCore-user (8081)
- ZhiCore-admin (8090)
- ZhiCore-gateway (8000)

### 4. 重新运行测试

```powershell
cd tests/api/admin
powershell -ExecutionPolicy Bypass -File .\test-admin-api-full.ps1
```

## 配置说明

### bootstrap.yml vs application.yml

- **bootstrap.yml**: 在应用启动的引导阶段加载，用于配置：
  - 服务名称
  - Nacos 连接信息
  - 配置中心设置
  
- **application.yml**: 在应用上下文创建后加载，用于配置：
  - 服务端口
  - 数据库连接
  - Feign 超时设置
  - 其他业务配置

### 为什么需要 bootstrap.yml？

Spring Cloud 应用需要在启动早期连接到配置中心（Nacos Config）和服务注册中心（Nacos Discovery）。如果没有 bootstrap.yml，应用无法：

1. 从 Nacos Config 拉取远程配置
2. 向 Nacos Discovery 注册服务
3. 通过服务发现调用其他微服务

## 其他微服务的 bootstrap.yml

检查发现以下服务都有 bootstrap.yml：
- ✅ ZhiCore-user
- ✅ ZhiCore-post
- ✅ ZhiCore-comment
- ✅ ZhiCore-message
- ✅ ZhiCore-notification
- ✅ ZhiCore-gateway
- ❌ ZhiCore-admin (已修复)

## 测试脚本

### diagnose-feign.ps1

快速诊断脚本，用于测试：
1. 用户注册和登录
2. ADMIN 角色分配
3. 直接调用 ZhiCore-user admin 端点
4. 通过 gateway 调用 ZhiCore-admin 端点

运行方式：
```powershell
cd tests/api/admin
powershell -ExecutionPolicy Bypass -File .\diagnose-feign.ps1 -ConfigPath "../../config/test-env.json"
```

## 预期结果

修复后，所有测试应该通过：

```
[STEP 1] Creating test user...
  [PASS] User registered
  [PASS] User logged in

[STEP 2] Assigning ADMIN role...
  [PASS] ADMIN role assigned
  [PASS] Re-logged in with ADMIN role

[STEP 3] Testing direct call to ZhiCore-user...
  [PASS] Direct call works

[STEP 4] Testing call through gateway...
  [PASS] Gateway call works
```

## 下一步

1. 重启 ZhiCore-admin 服务
2. 验证 Nacos 服务注册
3. 运行完整的管理后台 API 测试
4. 检查其他可能缺少 bootstrap.yml 的服务

---

**修复时间**: 2026-01-20
**修复人**: Kiro AI Assistant
