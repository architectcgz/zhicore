# Requirements Document

## Introduction

本文档定义了将 ZhiCore-microservice 的 ID 生成功能从内置的 ZhiCore-leaf 服务迁移到独立的 id-generator 服务的需求。通过使用独立的 id-generator 服务，可以实现更好的服务解耦、统一的 ID 生成策略，并减少维护成本。

## Glossary

- **ZhiCore_System**: 博客微服务系统，包含多个微服务模块
- **ID_Generator_Service**: 独立的分布式 ID 生成服务，提供 Snowflake 和 Segment 两种 ID 生成模式
- **ZhiCore_Leaf_Service**: ZhiCore-microservice 内置的 ID 生成服务，基于美团 Leaf 雪花算法
- **ID_Generator_Client**: id-generator 的 Java 客户端库，用于调用 ID_Generator_Service
- **ID_Generator_Starter**: id-generator 的 Spring Boot Starter，简化集成配置
- **Feign_Client**: 当前 ZhiCore 服务使用的 OpenFeign 客户端，用于调用 ZhiCore_Leaf_Service
- **Service_Module**: ZhiCore-microservice 中的各个微服务模块（如 ZhiCore-user、ZhiCore-post、ZhiCore-comment 等）
- **Nacos**: 服务注册与发现中心

## Requirements

### Requirement 1: 集成 ID Generator Client

**User Story:** 作为开发者，我想要在 ZhiCore 微服务中集成 id-generator 客户端，以便使用独立的 ID 生成服务

#### Acceptance Criteria

1. WHEN 在 ZhiCore-microservice 父 pom.xml 中添加 id-generator-spring-boot-starter 依赖 THEN THE ZhiCore_System SHALL 能够使用 ID_Generator_Client
2. WHEN 在需要 ID 生成的服务模块中添加 starter 依赖 THEN THE Service_Module SHALL 自动配置 ID_Generator_Client
3. WHEN 配置 id-generator 服务地址 THEN THE ID_Generator_Client SHALL 能够连接到 ID_Generator_Service
4. WHEN ID_Generator_Service 不可用 THEN THE ID_Generator_Client SHALL 抛出明确的异常信息

### Requirement 2: 替换 Leaf Service 调用

**User Story:** 作为开发者，我想要将所有使用 Leaf Service 的代码替换为 ID Generator Client，以便统一 ID 生成方式

#### Acceptance Criteria

1. WHEN 在 ZhiCore-notification 服务中调用 ID 生成 THEN THE ZhiCore_System SHALL 使用 ID_Generator_Client 而不是 Feign_Client
2. WHEN 在 ZhiCore-message 服务中调用 ID 生成 THEN THE ZhiCore_System SHALL 使用 ID_Generator_Client 而不是 Feign_Client
3. WHEN 在其他服务中调用 ID 生成 THEN THE ZhiCore_System SHALL 使用 ID_Generator_Client 而不是 Feign_Client
4. WHEN 生成 ID THEN THE ZhiCore_System SHALL 返回 Long 类型而不是 String 类型
5. WHEN 需要批量生成 ID THEN THE ID_Generator_Client SHALL 支持批量生成功能

### Requirement 3: 移除 Leaf Service 依赖

**User Story:** 作为开发者，我想要移除 ZhiCore-leaf 服务及其相关依赖，以便简化系统架构

#### Acceptance Criteria

1. WHEN 移除 ZhiCore-leaf 模块 THEN THE ZhiCore_System SHALL 不再包含 ZhiCore-leaf 服务
2. WHEN 移除 Leaf Feign Client THEN THE Service_Module SHALL 不再依赖 ZhiCore-leaf 的 Feign 接口
3. WHEN 从父 pom.xml 中移除 ZhiCore-leaf 模块声明 THEN THE ZhiCore_System SHALL 能够正常编译
4. WHEN 移除 Nacos 中的 ZhiCore-leaf 配置 THEN THE ZhiCore_System SHALL 不再尝试连接 ZhiCore-leaf 服务

### Requirement 4: 配置管理

**User Story:** 作为运维人员，我想要通过配置文件管理 ID Generator 的连接信息，以便在不同环境中灵活部署

#### Acceptance Criteria

1. WHEN 在 application.yml 中配置 id-generator.server-url THEN THE ID_Generator_Client SHALL 使用指定的服务地址
2. WHEN 在 Nacos 配置中心配置 ID Generator 信息 THEN THE Service_Module SHALL 动态加载配置
3. WHEN 配置连接超时时间 THEN THE ID_Generator_Client SHALL 使用指定的超时设置
4. WHEN 配置重试策略 THEN THE ID_Generator_Client SHALL 按照配置进行重试

### Requirement 5: 向后兼容性

**User Story:** 作为开发者，我想要确保 ID 生成的向后兼容性，以便现有数据和逻辑不受影响

#### Acceptance Criteria

1. WHEN 生成新的 ID THEN THE ID_Generator_Service SHALL 生成与 Leaf Service 相同格式的 Snowflake ID
2. WHEN 解析已有的 ID THEN THE ZhiCore_System SHALL 能够正确解析 Leaf Service 生成的 ID
3. WHEN 比较新旧 ID THEN THE ZhiCore_System SHALL 保持 ID 的时间递增特性
4. WHEN 数据库中存在旧 ID THEN THE ZhiCore_System SHALL 能够正常处理新旧 ID 混合的场景

### Requirement 6: 错误处理

**User Story:** 作为开发者，我想要有完善的错误处理机制，以便在 ID 生成失败时能够及时发现和处理

#### Acceptance Criteria

1. WHEN ID_Generator_Service 返回错误 THEN THE ID_Generator_Client SHALL 抛出包含详细信息的异常
2. WHEN 网络连接失败 THEN THE ID_Generator_Client SHALL 抛出连接异常并记录日志
3. WHEN 超时发生 THEN THE ID_Generator_Client SHALL 抛出超时异常
4. WHEN 异常发生 THEN THE ZhiCore_System SHALL 记录错误日志并返回友好的错误信息给用户

### Requirement 7: 测试验证

**User Story:** 作为开发者，我想要有完整的测试覆盖，以便确保迁移后的功能正确性

#### Acceptance Criteria

1. WHEN 运行单元测试 THEN THE ZhiCore_System SHALL 验证 ID_Generator_Client 的基本功能
2. WHEN 运行集成测试 THEN THE ZhiCore_System SHALL 验证与 ID_Generator_Service 的集成
3. WHEN 运行端到端测试 THEN THE ZhiCore_System SHALL 验证各个服务模块的 ID 生成功能
4. WHEN 测试并发场景 THEN THE ID_Generator_Client SHALL 保证 ID 的唯一性

### Requirement 8: 部署和回滚

**User Story:** 作为运维人员，我想要有清晰的部署和回滚方案，以便安全地进行服务迁移

#### Acceptance Criteria

1. WHEN 部署新版本 THEN THE ZhiCore_System SHALL 先部署 ID_Generator_Service 再更新各个服务模块
2. WHEN 需要回滚 THEN THE ZhiCore_System SHALL 能够快速恢复到使用 Leaf Service 的版本
3. WHEN 灰度发布 THEN THE ZhiCore_System SHALL 支持部分服务先使用 ID_Generator_Service
4. WHEN 监控服务状态 THEN THE ZhiCore_System SHALL 提供 ID 生成的监控指标
