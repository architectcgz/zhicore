# ZhiCore 微服务架构文档

## 文档版本

| 版本 | 日期 | 作者 | 说明 |
|------|------|------|------|
| 1.0 | 2026-02-11 | System | 初始版本 - 创建架构文档索引 |

---

## 📚 文档概述

本目录包含 ZhiCore-microservice 系统的完整架构文档，涵盖系统概述、微服务架构、技术选型、服务间通信、DDD 分层架构、数据架构、基础设施和部署架构等核心内容。

### 文档目标

- 帮助新成员快速了解系统架构
- 记录架构设计决策和演进历史
- 提供开发和运维参考指南
- 保持架构文档与代码同步

---

## 📖 核心架构文档

### 系统概述

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [01-系统概述](./01-system-overview.md) | 📝 待编写 | P1 | 系统定位、核心功能、技术栈、系统架构图 |

**内容预览**:
- 系统定位和核心功能
- 技术栈列表（Spring Boot 3.2.4、Nacos、RocketMQ 等）
- 系统架构图（Mermaid）
- 系统统计信息（14 个微服务、代码行数等）
- 核心特性

---

### 微服务架构

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [02-微服务列表和职责](./02-microservices-list.md) | 📝 待编写 | P0 | 14 个微服务的职责、端口、依赖关系 |

**内容预览**:
- 14 个微服务列表和职责说明
- 服务端口分配（参考 [端口分配文档](../../../.kiro/steering/port-allocation.md)）
- 服务依赖关系图（Mermaid）
- 服务启动顺序

**微服务列表**:
- ZhiCore-gateway (8000) - API 网关
- ZhiCore-user (8081) - 用户服务
- ZhiCore-post (8082) - 文章服务
- ZhiCore-comment (8083) - 评论服务
- ZhiCore-message (8084) - 消息服务
- ZhiCore-notification (8085) - 通知服务
- ZhiCore-search (8086) - 搜索服务
- ZhiCore-ranking (8087) - 排行服务
- ZhiCore-admin (8090) - 管理服务
- ZhiCore-ops - 运维服务
- ZhiCore-upload - 文件上传服务
- ZhiCore-api - API 模块（共享接口）
- ZhiCore-common - 公共模块

---

### 文件上传架构

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [03-文件上传架构](./03-file-upload-architecture.md) | 📝 待编写 | P0 | 最新的 ZhiCore-upload 架构设计（重点） |

**内容预览**:
- 架构演进历史（为什么移除 FileUploadService 接口）
- ZhiCore-upload 服务职责
- 前端上传流程（直接调用 ZhiCore-upload）
- 后端删除流程（通过 ZhiCoreUploadClient）
- 支持的文件类型（图片、音频）
- 文件上传流程图（Mermaid）
- 文件删除流程图（Mermaid）
- 代码示例

**重要说明**:
- ⚠️ 已移除 FileUploadService 接口，统一使用 ZhiCore-upload 服务
- 前端直接调用 ZhiCore-upload 服务上传文件
- 后端通过 ZhiCoreUploadClient 删除文件

---

### 服务间通信

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [04-服务间通信](./04-service-communication.md) | 📝 待编写 | P1 | Feign Client、消息队列、事件驱动 |

**内容预览**:
- Feign Client 使用方式
- ZhiCore-api 模块的作用（参考 [ZhiCore-api 模块说明](./ZhiCore-api-module-purpose.md)）
- 降级策略（FallbackFactory）
- 消息队列使用（RocketMQ）
- 领域事件发布和订阅
- 服务调用链路图（Mermaid）
- 代码示例

---

### DDD 分层架构

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [05-DDD 分层架构](./05-ddd-layered-architecture.md) | 📝 待编写 | P2 | 四层架构、聚合根、实体、值对象 |

**内容预览**:
- 四层架构说明（interfaces、application、domain、infrastructure）
- 每层的职责和依赖关系
- 聚合根、实体、值对象设计
- 仓储模式实现
- 领域事件使用
- 分层架构图（Mermaid）
- 代码示例（以 ZhiCore-post 为例）

---

### 数据架构

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [06-数据架构](./06-data-architecture.md) | ✅ 已完成 | P2 | 数据库设计、缓存策略、数据一致性 |

**内容概要**:
- 数据库选型（PostgreSQL、Redis、MongoDB）
- 数据库设计原则（主键策略、索引规范、字段命名）
- 缓存策略（Cache-Aside 模式、TTL 规范）
- 缓存穿透/击穿/雪崩防护
- 缓存 Key 命名规范（参考 [常量与配置管理](../../../.kiro/steering/development/03-constants-config.md)）
- 数据一致性保证（事务管理、延迟双删、事件驱动）
- 数据流图（Mermaid）
- 代码示例（仓储实现、缓存装饰器、Redis Key 管理）
- 最佳实践和性能优化建议

---

### 基础设施

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [07-基础设施](./07-infrastructure.md) | 📝 待编写 | P3 | Nacos、RocketMQ、Redis、Elasticsearch 等 |

**内容预览**:
- Nacos 配置（服务注册、配置中心）
- RocketMQ 配置（Topic、Tag、Consumer Group）
- Redis 配置（缓存、分布式锁）
- Elasticsearch 配置（搜索服务）
- PostgreSQL 配置（数据库）
- MongoDB 配置（如果使用）
- 监控和日志（Prometheus、Grafana、SkyWalking）
- 基础设施架构图（Mermaid）

**参考文档**:
- [基础设施与端口规范](../../../.kiro/steering/07-infrastructure.md)
- [端口分配文档](../../../.kiro/steering/port-allocation.md)

---

### 部署架构

| 文档 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| [08-部署架构](./08-deployment-architecture.md) | 📝 待编写 | P3 | Docker Compose 部署、端口分配、网络拓扑 |

**内容预览**:
- Docker Compose 部署方式
- 端口分配规则（参考 [端口分配文档](../../../.kiro/steering/port-allocation.md)）
- 网络拓扑
- 服务启动顺序
- 健康检查配置
- 部署架构图（Mermaid）
- 启动脚本说明（参考 [start-all-services.ps1](../../scripts/start-all-services.ps1)）

---

## 📋 现有架构文档

以下文档已经存在，提供了特定领域的架构说明：

| 文档 | 说明 | 最后更新 |
|------|------|---------|
| [ZhiCore-api 模块说明](./ZhiCore-api-module-purpose.md) | ZhiCore-api 模块的作用、为什么需要、如何使用 | 已完成 |
| [ZhiCore-message 与 im-system 集成](./ZhiCore-message-im-integration.md) | ZhiCore-message 模块与 im-system 的集成架构 | 已完成 |
| [File Service 集成架构](./file-service-integration.md) | File Service 在博客系统中的集成架构 | 已完成 |
| [File Service 数据流](./file-service-data-flow.md) | File Service 的数据流设计 | 已完成 |

---

## 🔍 快速查找指南

### 按角色查找

#### 新成员入职
1. [01-系统概述](./01-system-overview.md) - 了解系统整体架构
2. [02-微服务列表和职责](./02-microservices-list.md) - 了解各服务职责
3. [05-DDD 分层架构](./05-ddd-layered-architecture.md) - 了解代码组织方式

#### 开发人员
1. [03-文件上传架构](./03-file-upload-architecture.md) - 实现文件上传功能
2. [04-服务间通信](./04-service-communication.md) - 实现跨服务调用
3. [ZhiCore-api 模块说明](./ZhiCore-api-module-purpose.md) - 使用 ZhiCore-api 模块
4. [06-数据架构](./06-data-architecture.md) - 设计数据模型

#### 运维人员
1. [07-基础设施](./07-infrastructure.md) - 了解基础设施配置
2. [08-部署架构](./08-deployment-architecture.md) - 部署和维护系统
3. [端口分配文档](../../../.kiro/steering/port-allocation.md) - 查看端口分配

#### 架构师
1. [01-系统概述](./01-system-overview.md) - 系统整体架构
2. [05-DDD 分层架构](./05-ddd-layered-architecture.md) - DDD 设计
3. [File Service 集成架构](./file-service-integration.md) - 外部服务集成

---

### 按场景查找

#### 实现文件上传功能
1. [03-文件上传架构](./03-file-upload-architecture.md) - 了解文件上传架构
2. 参考代码：
   - `ZhiCore-upload/src/main/java/com/ZhiCore/upload/controller/FileUploadController.java`
   - `ZhiCore-post/src/main/java/com/ZhiCore/post/infrastructure/feign/ZhiCoreUploadClient.java`
   - `ZhiCore-user/src/main/java/com/ZhiCore/user/infrastructure/feign/ZhiCoreUploadClient.java`

#### 实现跨服务调用
1. [04-服务间通信](./04-service-communication.md) - 了解服务间通信模式
2. [ZhiCore-api 模块说明](./ZhiCore-api-module-purpose.md) - 使用 ZhiCore-api 模块
3. 参考代码：
   - `ZhiCore-api/client/` - Feign Client 接口定义
   - `ZhiCore-api/dto/` - 数据传输对象

#### 实现消息通知
1. [ZhiCore-message 与 im-system 集成](./ZhiCore-message-im-integration.md) - 了解消息架构
2. [04-服务间通信](./04-service-communication.md) - 了解事件驱动
3. 参考代码：
   - `ZhiCore-api/event/` - 领域事件定义
   - `ZhiCore-message/` - 消息服务实现

#### 部署系统
1. [08-部署架构](./08-deployment-architecture.md) - 了解部署架构
2. [端口分配文档](../../../.kiro/steering/port-allocation.md) - 查看端口分配
3. [Docker 使用规范](../../../.kiro/steering/08-docker.md) - Docker 使用规范
4. 参考文件：
   - `docker/docker-compose.yml` - Docker Compose 配置
   - `scripts/start-all-services.ps1` - 启动脚本

---

### 按技术栈查找

#### Spring Boot / Spring Cloud
- [01-系统概述](./01-system-overview.md) - 技术栈说明
- [04-服务间通信](./04-service-communication.md) - Feign Client 使用
- [05-DDD 分层架构](./05-ddd-layered-architecture.md) - Spring 分层架构

#### Nacos
- [07-基础设施](./07-infrastructure.md) - Nacos 配置
- [基础设施与端口规范](../../../.kiro/steering/07-infrastructure.md)

#### RocketMQ
- [04-服务间通信](./04-service-communication.md) - 消息队列使用
- [07-基础设施](./07-infrastructure.md) - RocketMQ 配置

#### PostgreSQL
- [06-数据架构](./06-data-architecture.md) - 数据库设计
- [数据库规范](../../../.kiro/steering/15-database.md)

#### Redis
- [06-数据架构](./06-data-architecture.md) - 缓存策略
- [缓存规范](../../../.kiro/steering/16-cache.md)

#### Docker
- [08-部署架构](./08-deployment-architecture.md) - Docker 部署
- [Docker 使用规范](../../../.kiro/steering/08-docker.md)

---

## 📊 系统统计信息

### 微服务数量
- **业务服务**: 9 个（gateway, user, post, comment, message, notification, search, ranking, admin）
- **支持服务**: 2 个（ops, upload）
- **共享模块**: 2 个（api, common）
- **总计**: 13 个模块

### 端口分配
- **应用服务**: 8000-8090
- **基础设施**: 参考 [端口分配文档](../../../.kiro/steering/port-allocation.md)

### 技术栈
- **框架**: Spring Boot 3.2.4, Spring Cloud
- **服务注册**: Nacos
- **消息队列**: RocketMQ
- **数据库**: PostgreSQL, MongoDB
- **缓存**: Redis
- **搜索**: Elasticsearch
- **监控**: Prometheus, Grafana, SkyWalking

---

## 🔄 文档更新记录

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|---------|--------|
| 2026-02-11 | 1.1 | 完成数据架构文档 | System |
| 2026-02-11 | 1.0 | 创建架构文档索引 | System |

---

## 📝 文档编写规范

### 文档结构

每个架构文档应包含以下部分：

1. **文档版本信息表格**
   ```markdown
   | 版本 | 日期 | 作者 | 说明 |
   |------|------|------|------|
   | 1.0 | YYYY-MM-DD | 作者 | 说明 |
   ```

2. **清晰的章节结构**
   - 概述
   - 背景
   - 架构设计
   - 技术实现
   - 代码示例
   - 最佳实践
   - 注意事项
   - 相关文档

3. **Mermaid 架构图**（如果适用）
   - 使用 `graph TB` 或 `graph LR` 布局
   - 使用清晰的节点标签
   - 使用箭头表示数据流或调用关系

4. **代码示例**（如果适用）
   - 使用真实的代码片段
   - 添加中文注释
   - 突出关键代码

5. **相关文档链接**
   - 链接到相关的规范文档
   - 链接到相关的架构文档
   - 使用相对路径

### 文档质量标准

- ✅ 使用中文编写（符合 [代码规范](../../../.kiro/steering/02-code-standards.md)）
- ✅ 使用 Mermaid 图表展示架构
- ✅ 使用表格整理信息
- ✅ 代码示例可编译运行
- ✅ 章节结构清晰
- ✅ 无拼写错误
- ✅ 内容准确，与代码一致

---

## 🔗 相关资源

### 开发规范
- [核心开发策略](../../../.kiro/steering/01-core-policies.md)
- [代码规范](../../../.kiro/steering/02-code-standards.md)
- [常量与配置管理](../../../.kiro/steering/03-constants-config.md)
- [Java 编码标准](../../../.kiro/steering/04-java-standards.md)
- [Git 提交规范](../../../.kiro/steering/06-git-standards.md)

### 基础设施
- [基础设施与端口规范](../../../.kiro/steering/07-infrastructure.md)
- [Docker 使用规范](../../../.kiro/steering/08-docker.md)
- [端口分配文档](../../../.kiro/steering/port-allocation.md)

### 设计规范
- [接口设计规范](../../../.kiro/steering/14-api-design.md)
- [数据库规范](../../../.kiro/steering/15-database.md)
- [缓存规范](../../../.kiro/steering/16-cache.md)
- [并发控制规范](../../../.kiro/steering/17-concurrency.md)

### 测试规范
- [API 测试规范](../../../.kiro/steering/10-api-testing.md)
- [单元测试规范](../../../.kiro/steering/18-unit-testing.md)

### 项目文档
- [项目根目录](../../)
- [数据库文档](../../database/)
- [Docker 文档](../../docker/)
- [API 文档](../api/)

---

## 💡 贡献指南

### 如何更新文档

1. **创建新文档**
   - 按照文档编写规范创建新文档
   - 在本 README.md 中添加文档链接
   - 更新文档更新记录

2. **更新现有文档**
   - 修改文档内容
   - 更新文档版本信息表格
   - 在本 README.md 的更新记录中添加条目

3. **审查文档**
   - 确保内容准确，与代码一致
   - 确保格式符合规范
   - 确保链接有效

### 文档维护者

- **主要维护者**: 架构团队
- **审查频率**: 每月或发生重大变更时
- **联系方式**: 通过项目 Issue 或 PR 提交反馈

---

## ❓ 常见问题

### Q1: 为什么移除了 FileUploadService 接口？

参考 [03-文件上传架构](./03-file-upload-architecture.md) 文档，了解架构演进历史和最新的文件上传方案。

### Q2: ZhiCore-api 模块的作用是什么？

参考 [ZhiCore-api 模块说明](./ZhiCore-api-module-purpose.md) 文档，了解 ZhiCore-api 模块的职责和使用方式。

### Q3: 如何实现跨服务调用？

参考 [04-服务间通信](./04-service-communication.md) 文档，了解 Feign Client 的使用方式和降级策略。

### Q4: 如何查看端口分配？

参考 [端口分配文档](../../../.kiro/steering/port-allocation.md)，查看所有服务的端口分配。

### Q5: 如何部署系统？

参考 [08-部署架构](./08-deployment-architecture.md) 文档，了解 Docker Compose 部署方式和启动脚本。

---

## 📞 反馈与支持

如果您在使用文档过程中遇到问题，或者有改进建议，请通过以下方式反馈：

1. **提交 Issue**: 在项目仓库中提交 Issue
2. **提交 PR**: 直接修改文档并提交 Pull Request
3. **联系团队**: 通过内部沟通渠道联系架构团队

---

**最后更新**: 2026-02-11  
**维护者**: 架构团队  
**文档状态**: 🚧 持续更新中
