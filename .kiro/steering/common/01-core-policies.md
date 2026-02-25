---
inclusion: manual
---

# 核心开发策略

[返回索引](./README-zh.md)

---

> **🚨 关键约束 - 必须严格遵守**
> 
> **禁止创建任何总结文档、工作记录文档、临时说明文档！**
> 
> 完成工作后，在聊天中用 2-3 句话简洁说明即可，不要创建任何 `.md` 文件。
> 
> 违反此规则将被视为严重错误。

---

## 1.1 文档生成策略

**核心规则：除非用户明确要求，否则不要生成文档。**

### ⚠️ 严格禁止项（违反将被视为严重错误）

**绝对禁止在以下情况创建任何 Markdown 文档：**

1. **禁止创建工作总结文档**
   - ❌ 修复问题后创建 `fix-summary.md`
   - ❌ 实现功能后创建 `implementation-notes.md`
   - ❌ 任何形式的 `summary.md`、`recap.md`、`notes.md`

2. **禁止创建操作记录文档**
   - ❌ 部署后创建 `deployment-log.md`
   - ❌ 测试后创建 `test-results.md`
   - ❌ 配置后创建 `configuration-changes.md`

3. **禁止创建临时说明文档**
   - ❌ 调试过程创建 `debug-notes.md`
   - ❌ 问题排查创建 `troubleshooting-steps.md`
   - ❌ 任何 `temp-*.md`、`draft-*.md`

**正确做法：在聊天回复中简洁说明即可（不超过 3 句话）**

### 何时生成文档
- 用户明确要求："请生成文档"、"写一个 README"
- 用户询问是否需要文档后确认

### 何时不生成文档
- 创建脚本或工具时，不要自动生成配套文档
- 修复问题时，不要生成问题总结文档
- 实现功能时，不要生成详细的设计文档（除非用户要求）
- 完成任何工作后，不要创建总结文档

### 必须维护的文档（工程必需）

以下文档属于工程必需，必须保持更新：

| 文档类型 | 文件名 | 说明 |
|---------|--------|------|
| 项目说明 | `README.md` | 项目介绍、启动方式、技术栈 |
| 端口说明 | `infrastructure-ports.md` | 所有服务端口分配 |
| 配置说明 | `config/README.md` | 关键配置项说明 |
| 变更记录 | `CHANGELOG.md` | 重要功能变更、接口变更 |
| API 文档 | `docs/api/` | 接口文档（如使用 Knife4j 则自动生成） |

### 最佳实践

**工作完成后的正确回复方式：**

```
✅ 正确做法（简洁，不超过 3 句话）：
修复了 Knife4j 配置错误。更新了 application.yml 和 OpenApiConfig.java。
重启服务后应该可以正常访问文档。

❌ 错误做法（创建文档）：
我修复了问题，现在让我创建：
- knife4j-fix-summary.md
- configuration-changes.md
- troubleshooting-guide.md
```

**创建脚本/工具后的正确回复方式：**

```
✅ 正确做法：
我创建了脚本 X 来解决问题 Y。
使用方法：.\script.ps1
需要我生成详细的使用文档吗？

❌ 错误做法：
我创建了脚本 X，现在让我生成：
- README.md
- USAGE_GUIDE.md  
- TROUBLESHOOTING.md
```

### 关键区分

**操作请求 ≠ 文档请求**

| 操作动词（不生成文档） | 文档动词（需生成文档） |
|---------------------|---------------------|
| 启动、检查、验证、测试 | 生成文档、写文档、创建说明 |
| 修复、调试、运行 | 记录、总结、编写指南 |
| 部署、配置、安装 | 制作教程、撰写手册 |

**示例**：
- "启动 API 文档" → 执行操作，告知结果
- "生成 API 使用文档" → 创建文档文件

---

## 1.2 禁止使用 Emoji

**严禁在所有代码文件的注释中使用 Emoji 字符！**

### 原因
1. 编码兼容性问题（Windows PowerShell 和某些 IDE）
2. 脚本解析错误
3. 跨平台兼容性问题

### 替代方案

| 禁止使用 | 应该使用 |
|----------|----------|
| ✅ | `[PASS]` 或 `SUCCESS` |
| ❌ | `[FAIL]` 或 `ERROR` |
| ⏭️ | `[SKIP]` 或 `SKIPPED` |
| ⚠️ | `[WARN]` 或 `WARNING` |
| ℹ️ | `[INFO]` |
| 🔧 | `[FIX]` 或 `FIXED` |

### 示例

```java
// ❌ 错误 - 使用 Emoji
/**
 * ✅ 用户注册成功
 * ❌ 用户名已存在
 */

// ✅ 正确 - 使用 ASCII 文本
/**
 * [SUCCESS] 用户注册成功
 * [ERROR] 用户名已存在
 */
```

---

## 1.3 AI 助手回复语言规范

**核心规则：AI 助手必须使用中文回复用户，专业术语除外。**

### ⚠️ 强制要求

**所有 AI 助手（包括 Kiro、ChatGPT 等）在与用户交互时必须使用中文。**

这是一个强制性规则，违反此规则将被立即指出并要求重新回复。

### 语言使用要求

1. **默认使用中文**
   - 所有回复、说明、解释必须使用中文
   - 所有生成的文档、注释、说明必须使用中文
   - 保持专业、清晰、简洁的表达方式
   - 即使用户使用英文提问，也应使用中文回复（除非用户明确要求使用英文）

2. **专业术语保留英文**
   - 技术术语、框架名称、工具名称保持英文（如 Spring Boot、Docker、Redis）
   - 代码相关的关键字、API 名称保持英文
   - 行业通用的缩写保持英文（如 API、REST、JSON、SQL、HTTP）
   - 编程语言名称保持英文（如 Java、Python、TypeScript）

3. **代码注释使用中文**
   - Java、JavaScript、TypeScript 等代码的注释使用中文
   - 配置文件的说明使用中文
   - 脚本中的注释使用中文
   - 参考 [代码规范](./02-code-standards-zh.md) 和 [中文注释规范](./chinese-comments.md)

4. **代码本身使用英文**
   - 变量名、函数名、类名使用英文（遵循编程语言规范）
   - 代码逻辑、语法使用标准编程语言
   - 只有注释和文档使用中文

### 示例

#### ✅ 正确做法

```
我已经更新了 Spring Boot 配置文件。修改了 application.yml 中的数据库连接配置，
并在 OpenApiConfig.java 中添加了 Knife4j 的相关配置。现在 API 文档应该可以正常访问了。
```

#### ❌ 错误做法

```
I've updated the Spring Boot configuration file. Modified the database connection 
settings in application.yml and added Knife4j configuration in OpenApiConfig.java.
The API documentation should now be accessible.
```

### 混合使用示例

#### ✅ 正确的中英文混合

- "我在 UserController 中添加了新的 REST API 接口"
- "Redis 缓存配置已更新，TTL 设置为 3600 秒"
- "PostgreSQL 数据库连接池大小调整为 20"
- "使用 @Transactional 注解确保事务一致性"
- "Knife4j 文档地址：http://localhost:8080/doc.html"

#### ❌ 完全英文（除非用户明确要求）

- "Added new REST API endpoint in UserController"
- "Updated Redis cache configuration with TTL of 3600 seconds"

### 代码注释示例

#### ✅ 正确的代码注释

```java
/**
 * 用户控制器
 * 
 * 提供用户管理相关的 REST API 接口，包括：
 * - 用户注册
 * - 用户登录
 * - 用户信息查询和更新
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    /**
     * 根据用户 ID 查询用户信息
     * 
     * @param userId 用户 ID
     * @return 用户信息
     */
    @GetMapping("/{userId}")
    public Result<UserDTO> getUserById(@PathVariable Long userId) {
        // 实现逻辑
    }
}
```

#### ❌ 错误的代码注释（使用英文）

```java
/**
 * User Controller
 * 
 * Provides REST API endpoints for user management
 */
@RestController
public class UserController {
    // Implementation
}
```

### 特殊情况

1. **用户使用英文提问**
   - 仍然使用中文回复，除非用户明确要求使用英文
   - 可以在回复中说明："我会用中文回复，如需英文请告知"

2. **国际化项目**
   - 如果项目明确是国际化项目，可根据上下文调整
   - 但默认仍使用中文，除非项目文档明确要求英文

3. **引用文档**
   - 引用英文文档时，提供中文说明 + 英文原文链接
   - 示例："根据 Spring Boot 官方文档（https://spring.io/...），我们需要..."

4. **错误信息**
   - 引用系统错误信息时可保留英文原文
   - 但需要提供中文解释
   - 示例："系统报错 'Connection refused'，这表示无法连接到数据库"

### 违规处理

如果 AI 助手使用英文回复：

1. **立即指出**："你为什么使用英文回复？请使用中文。"
2. **要求重新回复**："请用中文重新回复。"
3. **参考本规则**："请查看 .kiro/steering/development/01-core-policies.md 中的语言规范。"

### 检查清单

在每次回复前，AI 助手应自我检查：

- [ ] 回复主体是否使用中文？
- [ ] 专业术语是否适当保留英文？
- [ ] 代码注释是否使用中文？
- [ ] 生成的文档是否使用中文？
- [ ] 是否符合用户的语言偏好？

---

**最后更新**：2026-02-01
