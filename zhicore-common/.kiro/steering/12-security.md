---
inclusion: fileMatch
fileMatchPattern: '**/*.{java,yml,yaml,properties}'
---

# 安全规范

[返回索引](./README-zh.md)

---

## 敏感信息处理

### 禁止提交的内容

**严禁将以下内容提交到代码仓库：**

- 数据库密码
- API 密钥（阿里云、腾讯云等）
- JWT 私钥/公钥
- OAuth Client Secret
- 第三方服务 Token
- 加密盐值（Salt）
- 生产环境配置

### 配置文件分层

```
src/main/resources/
├── application.yml              # 默认配置（公共、非敏感）
├── application-dev.yml          # 开发环境（可提交）
├── application-test.yml         # 测试环境（可提交）
├── application-prod.yml         # 生产环境（禁止提交）
└── application-local.yml        # 本地配置（禁止提交，加入 .gitignore）
```

### .gitignore 配置

```gitignore
# 敏感配置文件
application-local.yml
application-prod.yml
*.key
*.pem
.env
.env.local

# IDE 配置
.idea/
*.iml
.vscode/
```

---

## 环境变量覆盖

**所有敏感配置必须支持环境变量覆盖**

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ZhiCore
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}

jwt:
  private-key: ${JWT_PRIVATE_KEY:}
  public-key: ${JWT_PUBLIC_KEY:}
```

### Docker 部署示例

```yaml
# docker-compose.yml
services:
  ZhiCore-user:
    environment:
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_PRIVATE_KEY=${JWT_PRIVATE_KEY}
```

---

## 日志脱敏

### 必须脱敏的字段

| 字段类型 | 脱敏规则 | 示例 |
|---------|---------|------|
| 手机号 | 保留前3后4位 | `138****5678` |
| 邮箱 | 保留前3位和域名 | `abc***@gmail.com` |
| 身份证 | 保留前6后4位 | `110101****1234` |
| 银行卡 | 保留后4位 | `**** **** **** 1234` |
| 密码 | 完全隐藏 | `******` |
| Token | 保留前8位 | `eyJhbGci...` |
| OpenID | 保留前8位 | `oB3_k5G...` |

### 脱敏工具类

```java
public class SensitiveDataUtil {
    
    /**
     * 手机号脱敏
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
    
    /**
     * 邮箱脱敏
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String prefix = parts[0];
        if (prefix.length() <= 3) {
            return "***@" + parts[1];
        }
        return prefix.substring(0, 3) + "***@" + parts[1];
    }
}
```

### 日志脱敏示例

```java
// ❌ 错误 - 直接打印敏感信息
log.info("用户登录: {}", user);

// ✅ 正确 - 脱敏后打印
log.info("用户登录: userId={}, phone={}, email={}", 
    user.getId(), 
    SensitiveDataUtil.maskPhone(user.getPhone()),
    SensitiveDataUtil.maskEmail(user.getEmail())
);
```

---

## 密钥管理

### 密钥存储位置

```
生产环境：
- 使用密钥管理服务（KMS）
- 使用 Vault 等密钥管理工具
- 使用 Kubernetes Secrets

开发环境：
- 使用环境变量
- 使用本地配置文件（不提交）
```

### 密钥轮换

- JWT 密钥：每 90 天轮换一次
- API 密钥：每 180 天轮换一次
- 数据库密码：每年轮换一次
- 支持密钥版本管理和平滑切换

---

**最后更新**：2026-02-01
