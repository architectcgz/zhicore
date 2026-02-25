# 开发规范索引

本目录包含所有项目的开发规范和最佳实践文档。

---

## 📚 规范文档列表

### 核心策略
- [核心开发策略](./01-core-policies.md) - 文档生成策略、Emoji 禁用规则、AI 助手语言规范

### 代码规范
- [代码规范](./02-code-standards.md) - 注释语言、Jackson 反序列化
- [常量与配置管理](./03-constants-config.md) - 常量管理、避免硬编码
- [Java 编码标准](./04-java-standards.md) - Jakarta EE、枚举、异常处理、Optional、Stream、Lombok
- [代码风格自动化](./05-code-style.md) - Spotless、Checkstyle、IDE 配置

### 版本控制
- [Git 提交规范](./06-git-standards.md) - Commit Message、提交粒度

### 基础设施
- [基础设施与端口](./07-infrastructure.md) - 端口映射、服务配置
- [Docker 使用规范](./08-docker.md) - docker-compose 使用规范

### 脚本与测试
- [PowerShell 脚本规范](./09-powershell.md) - 脚本编写标准
- [API 测试规范](./10-api-testing.md) - 测试脚本、测试分类
- [单元测试规范](./18-unit-testing.md) - 命名规范、Mock、断言、覆盖率

### Spec 开发流程
- [Spec 任务创建规范](./19-spec-task-creation.md) - 任务文件创建规范、执行规范要求

### 架构设计
（暂无通用架构设计规范，项目特定的设计决策请参考各项目文档）

### 安全与日志
- [安全规范](./12-security.md) - 敏感信息处理、日志脱敏、密钥管理
- [可观测性与日志](./13-observability.md) - 日志规范、链路追踪、监控指标

### 接口与数据库
- [接口设计规范](./14-api-design.md) - RESTful API、版本控制、幂等性、分页规范
- [数据库规范](./15-database.md) - 主键策略、表设计、索引、SQL 规范、事务管理

### 高级主题
- [缓存规范](./16-cache.md) - 缓存策略、穿透防护、击穿防护、一致性
- [并发控制规范](./17-concurrency.md) - 乐观锁、悲观锁、分布式锁、防重设计

### 检查清单
- [提交前检查清单](./checklist.md) - 代码提交前的完整检查项

---

## 🔍 快速查找

### 按场景查找

| 场景 | 相关文档 |
|------|---------|
| 开始新项目 | [核心开发策略](./01-core-policies.md), [代码规范](./02-code-standards.md) |
| 创建 Spec 任务 | [Spec 任务创建规范](./19-spec-task-creation.md) |
| 配置基础设施 | [基础设施与端口](./07-infrastructure.md), [Docker 使用规范](./08-docker.md) |
| 编写代码 | [Java 编码标准](./04-java-standards.md), [代码风格自动化](./05-code-style.md) |
| 提交代码 | [Git 提交规范](./06-git-standards.md), [检查清单](./checklist.md) |
| 设计 API | [接口设计规范](./14-api-design.md) |
| 数据库设计 | [数据库规范](./15-database.md) |
| 安全审查 | [安全规范](./12-security.md) |
| 编写测试 | [API 测试规范](./10-api-testing.md), [单元测试规范](./18-unit-testing.md) |
| 缓存设计 | [缓存规范](./16-cache.md) |
| 并发控制 | [并发控制规范](./17-concurrency.md) |
| 问题排查 | [可观测性与日志](./13-observability.md) |

### 按技术栈查找

| 技术 | 相关文档 |
|------|---------|
| Java/Spring Boot | [Java 编码标准](./04-java-standards.md), [代码风格自动化](./05-code-style.md) |
| Docker | [Docker 使用规范](./08-docker.md), [基础设施与端口](./07-infrastructure.md) |
| PostgreSQL | [数据库规范](./15-database.md) |
| Redis | [常量与配置管理](./03-constants-config.md), [缓存规范](./16-cache.md), [并发控制规范](./17-concurrency.md) |
| RocketMQ | [常量与配置管理](./03-constants-config.md) |
| PowerShell | [PowerShell 脚本规范](./09-powershell.md) |

---

## 📖 使用说明

1. **新成员入职**：按顺序阅读核心策略、代码规范、Git 提交规范
2. **日常开发**：参考具体技术栈的规范文档
3. **代码审查**：使用检查清单进行系统性检查
4. **问题排查**：查阅可观测性与日志规范

---

## 🔄 文档维护

- **最后更新**：2026-02-01
- **维护者**：开发团队
- **更新频率**：根据项目实践持续更新

---

## 相关文档

### 项目特定文档

- **ZhiCore Microservice**: [基础设施端口配置](../../ZhiCore-microservice/.kiro/steering/infrastructure-ports.md)
- **IM System**: [基础设施端口配置](../../im-system/.kiro/steering/infrastructure-ports.md)
- **File Service**: [基础设施端口配置](../../file-service/.kiro/steering/infrastructure-ports.md)
- **ID Generator**: [基础设施端口配置](../../id-generator/.kiro/steering/infrastructure-ports.md)
