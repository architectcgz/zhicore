# Design Document

## Overview

本设计文档描述了将 `blog-migration` 模块重构为 `blog-ops` 运维服务模块的详细方案。重构的核心目标是移除已废弃的 Flyway 数据库迁移功能和数据迁移服务，保留 CDC 和灰度发布功能，简化模块结构，为未来的运维需求做准备。

## Architecture

### 当前架构（blog-migration）

```
blog-migration/
├── src/main/java/com/blog/migration/
│   ├── MigrationApplication.java
│   ├── application/
│   │   └── service/
│   │       ├── UserMigrationService.java      [删除]
│   │       ├── PostMigrationService.java      [删除]
│   │       ├── CommentMigrationService.java   [删除]
│   │       └── MigrationValidator.java        [删除]
│   ├── domain/
│   │   └── model/
│   │       ├── MigrationResult.java           [删除]
│   │       └── ValidationResult.java          [删除]
│   ├── infrastructure/
│   │   ├── cdc/                               [保留]
│   │   ├── gray/                              [保留]
│   │   └── config/
│   │       ├── CdcProperties.java             [保留]
│   │       ├── DebeziumConfig.java            [保留]
│   │       ├── GrayReleaseProperties.java     [保留]
│   │       ├── FlywayConfig.java              [删除]
│   │       ├── DataSourceConfig.java          [删除]
│   │       └── MigrationProperties.java       [删除]
│   └── interfaces/
│       └── controller/
│           ├── MigrationController.java       [删除]
│           └── GrayReleaseController.java     [保留]
├── src/main/resources/
│   ├── db/migration/                          [删除整个目录]
│   ├── application.yml
│   └── bootstrap.yml
└── pom.xml
```

### 目标架构（blog-ops）

```
blog-ops/
├── src/main/java/com/blog/ops/
│   ├── OpsApplication.java
│   ├── infrastructure/
│   │   ├── cdc/
│   │   │   ├── CdcEvent.java
│   │   │   ├── CdcEventHandler.java
│   │   │   ├── DebeziumConfig.java
│   │   │   ├── PostStatsCdcConsumer.java
│   │   │   ├── PostLikesCdcConsumer.java
│   │   │   ├── CommentStatsCdcConsumer.java
│   │   │   ├── CommentLikesCdcConsumer.java
│   │   │   └── UserFollowStatsCdcConsumer.java
│   │   ├── gray/
│   │   │   ├── GrayConfig.java
│   │   │   ├── GrayPhase.java
│   │   │   ├── GrayStatus.java
│   │   │   ├── GrayRouter.java
│   │   │   ├── GrayReleaseConfig.java
│   │   │   ├── GrayRollbackService.java
│   │   │   └── GrayDataReconciliationTask.java
│   │   └── config/
│   │       ├── CdcProperties.java
│   │       └── GrayReleaseProperties.java
│   └── interfaces/
│       └── controller/
│           └── GrayReleaseController.java
├── src/main/resources/
│   ├── application.yml
│   └── bootstrap.yml
└── pom.xml
```

## Components and Interfaces

### 1. CDC 组件（保留）

#### 1.1 Debezium 配置
- **DebeziumConfig**: Debezium Embedded Engine 配置
- **CdcProperties**: CDC 配置属性类

#### 1.2 CDC 事件处理
- **CdcEvent**: CDC 事件模型
- **CdcEventHandler**: CDC 事件处理器接口

#### 1.3 CDC 消费者
- **PostStatsCdcConsumer**: 文章统计数据变更消费者
- **PostLikesCdcConsumer**: 文章点赞数据变更消费者
- **CommentStatsCdcConsumer**: 评论统计数据变更消费者
- **CommentLikesCdcConsumer**: 评论点赞数据变更消费者
- **UserFollowStatsCdcConsumer**: 用户关注统计数据变更消费者

### 2. 灰度发布组件（保留）

#### 2.1 灰度配置
- **GrayConfig**: 灰度配置模型
- **GrayPhase**: 灰度阶段枚举
- **GrayStatus**: 灰度状态枚举
- **GrayReleaseProperties**: 灰度发布配置属性类

#### 2.2 灰度路由
- **GrayRouter**: 灰度流量路由器
  - 根据配置的流量比例决定用户是否进入灰度
  - 支持白名单和黑名单
  - 使用 Redis 存储用户灰度标记

#### 2.3 灰度管理
- **GrayReleaseConfig**: 灰度发布配置管理
- **GrayRollbackService**: 灰度回滚服务
  - 监控灰度指标（错误率、延迟）
  - 自动回滚机制
  - 灰度阶段推进

#### 2.4 数据对账
- **GrayDataReconciliationTask**: 灰度数据对账任务
  - 定期对比灰度环境和生产环境的数据一致性
  - 检测数据差异并告警

#### 2.5 API 接口
- **GrayReleaseController**: 灰度发布管理 API
  - `GET /api/gray/config`: 获取灰度配置
  - `PUT /api/gray/config`: 更新灰度配置
  - `GET /api/gray/check/{userId}`: 检查用户是否在灰度中
  - `POST /api/gray/rollback`: 执行回滚
  - `POST /api/gray/advance`: 推进灰度阶段
  - `POST /api/gray/reconcile`: 手动触发数据对账
  - `GET /api/gray/reconciliation/result`: 获取最新对账结果
  - `GET /api/gray/check-rollback`: 检查是否需要自动回滚
  - `DELETE /api/gray/user-flags/{userId}`: 清除用户灰度标记
  - `DELETE /api/gray/user-flags`: 清除所有用户灰度标记

### 3. 移除的组件

#### 3.1 数据迁移服务（删除）
- UserMigrationService
- PostMigrationService
- CommentMigrationService
- MigrationValidator

#### 3.2 迁移领域模型（删除）
- MigrationResult
- ValidationResult

#### 3.3 迁移 API（删除）
- MigrationController

#### 3.4 Flyway 相关（删除）
- FlywayConfig
- db/migration 目录
- Flyway Maven 插件配置

#### 3.5 数据源配置（删除）
- DataSourceConfig
- MigrationProperties
- 数据源配置（如果 CDC 不需要）

## Data Models

### CDC 配置模型

```yaml
cdc:
  enabled: false  # 默认关闭
  connector:
    name: blog-postgres-connector
    database:
      hostname: localhost
      port: 5432
      user: postgres
      password: postgres123456
      dbname: blog_user
    slot:
      name: blog_cdc_slot
    publication:
      name: blog_publication
    tables:
      - post_stats
      - post_likes
      - comment_stats
      - comment_likes
      - user_follow_stats
```

### 灰度发布配置模型

```yaml
gray:
  enabled: false  # 默认关闭
  traffic-ratio: 5  # 灰度流量比例 (0-100)
  whitelist-users: ""  # 灰度用户白名单
  blacklist-users: ""  # 灰度用户黑名单
  reconciliation-interval: 300  # 数据对账间隔（秒）
  alert:
    error-rate-threshold: 0.01  # 错误率告警阈值
    latency-threshold-ms: 500  # 延迟告警阈值
```

## Correctness Properties

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的正式陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: 模块重命名完整性

*对于任何* 代码文件或配置文件，如果它包含对 `blog-migration` 的引用，那么在重构后应该被更新为 `blog-ops` 或被删除

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 9.1, 9.2, 9.3, 9.4**

### Property 2: Flyway 依赖完全移除

*对于任何* 配置文件或代码文件，重构后不应包含对 Flyway 的引用（依赖、配置、导入语句）

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

### Property 3: 数据迁移代码完全移除

*对于任何* 数据迁移相关的类（UserMigrationService, PostMigrationService, CommentMigrationService, MigrationValidator, MigrationResult, ValidationResult, MigrationController），重构后这些文件应该不存在

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

### Property 4: CDC 功能完整保留

*对于任何* CDC 相关的文件（infrastructure/cdc 目录下的所有文件、CdcProperties、DebeziumConfig），重构后这些文件应该存在且包引用正确更新

**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6**

### Property 5: 灰度发布功能完整保留

*对于任何* 灰度发布相关的文件（infrastructure/gray 目录下的所有文件、GrayReleaseProperties、GrayReleaseController），重构后这些文件应该存在且包引用正确更新

**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**

### Property 6: 配置文件正确性

*对于* application.yml 配置文件，重构后应该满足：应用名为 `blog-ops`，不包含 Flyway 配置，不包含已注释的 migration 配置，不包含 mybatis-plus 配置，日志包名为 `com.blog.ops`

**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.6**

### Property 7: Maven 配置正确性

*对于* pom.xml 文件，重构后应该满足：artifactId 为 `blog-ops`，description 包含 "运维服务"，不包含 Flyway 依赖，不包含 Flyway 插件

**Validates: Requirements 1.2, 2.1, 2.2, 7.5**

### Property 8: 项目可编译性

*对于* blog-ops 模块，执行 `mvn clean compile` 应该成功完成，不应有编译错误

**Validates: Requirements 10.1, 10.6**

### Property 9: 项目可打包性

*对于* blog-ops 模块，执行 `mvn clean package` 应该成功完成，生成可执行的 jar 文件

**Validates: Requirements 10.2, 10.6**

### Property 10: 应用可启动性

*对于* blog-ops 应用，启动后应该能够成功运行，健康检查端点 `/actuator/health` 应该返回 UP 状态

**Validates: Requirements 10.3, 10.4**

## Error Handling

### 1. 编译错误处理

**场景**: 重命名包名后可能出现导入语句错误

**处理方式**:
- 使用 IDE 的重构功能批量更新包引用
- 手动检查并修复遗漏的导入语句
- 执行编译验证所有引用已更新

### 2. 配置错误处理

**场景**: 配置文件中可能存在对已删除类的引用

**处理方式**:
- 仔细检查 application.yml 和 bootstrap.yml
- 移除对已删除配置类的引用
- 确保 CDC 和灰度发布配置完整

### 3. 依赖冲突处理

**场景**: 移除依赖后可能导致其他模块编译失败

**处理方式**:
- 检查其他模块是否依赖 blog-migration
- 如有依赖，更新为 blog-ops
- 执行全量构建验证

### 4. 运行时错误处理

**场景**: 启动时可能因为缺少配置或依赖而失败

**处理方式**:
- 确保 CDC 和灰度发布默认关闭（enabled: false）
- 检查必要的依赖（Redis, RocketMQ）是否配置正确
- 查看启动日志定位问题

## Testing Strategy

### 单元测试

由于本次重构主要是代码结构调整和删除，不涉及业务逻辑变更，因此不需要编写新的单元测试。但需要：

1. **删除相关测试**
   - 删除 UserMigrationService 的测试
   - 删除 PostMigrationService 的测试
   - 删除 CommentMigrationService 的测试
   - 删除 MigrationValidator 的测试

2. **保留相关测试**
   - 保留 CDC 相关的测试（如果存在）
   - 保留灰度发布相关的测试（如果存在）

### 集成测试

1. **编译测试**
   ```bash
   mvn clean compile -pl blog-ops
   ```

2. **打包测试**
   ```bash
   mvn clean package -pl blog-ops
   ```

3. **启动测试**
   ```bash
   java -jar blog-ops/target/blog-ops-1.0.0-SNAPSHOT.jar
   ```

4. **健康检查测试**
   ```bash
   curl http://localhost:8090/actuator/health
   ```

5. **全量构建测试**
   ```bash
   mvn clean install
   ```

### 手动验证清单

- [ ] 模块目录已重命名为 blog-ops
- [ ] 所有 Java 文件的包名已更新为 com.blog.ops
- [ ] pom.xml 中的 artifactId 已更新为 blog-ops
- [ ] 根 pom.xml 中的模块引用已更新
- [ ] Flyway 依赖和插件已移除
- [ ] db/migration 目录已删除
- [ ] 数据迁移服务类已删除
- [ ] MigrationController 已删除
- [ ] CDC 相关代码已保留且包引用正确
- [ ] 灰度发布相关代码已保留且包引用正确
- [ ] application.yml 配置已更新
- [ ] 启动类已重命名为 OpsApplication
- [ ] 项目可以成功编译
- [ ] 项目可以成功打包
- [ ] 应用可以成功启动
- [ ] 健康检查端点正常工作
- [ ] 全量构建成功

## Implementation Notes

### 重命名策略

1. **使用 Git 保留历史**
   ```bash
   git mv blog-migration blog-ops
   ```

2. **批量更新包名**
   - 使用 IDE 的重构功能（Refactor > Rename Package）
   - 或使用 sed 命令批量替换

3. **更新配置文件**
   - 手动编辑 pom.xml
   - 手动编辑 application.yml
   - 手动编辑 bootstrap.yml

### 删除策略

1. **先删除测试代码**
   - 避免编译错误影响判断

2. **再删除业务代码**
   - 按照依赖关系从上到下删除
   - Controller -> Service -> Domain Model

3. **最后删除配置**
   - 删除配置类
   - 删除配置文件中的配置段

### 验证策略

1. **增量验证**
   - 每完成一个大的步骤就编译一次
   - 及时发现和修复问题

2. **全量验证**
   - 最后执行全量构建
   - 确保所有模块都能正常工作

## Deployment Considerations

### 1. 服务注册

由于服务名从 `blog-migration` 改为 `blog-ops`，需要注意：

- Nacos 中会出现新的服务名 `blog-ops`
- 旧的 `blog-migration` 服务实例会自动下线（如果有的话）
- 其他服务不应该依赖 blog-migration/blog-ops 服务（它是独立的运维工具）

### 2. 配置管理

如果使用 Nacos 配置中心：

- 需要在 Nacos 中创建 `blog-ops` 的配置
- 或者将 `blog-migration` 的配置重命名为 `blog-ops`
- 确保 CDC 和灰度发布配置正确

### 3. 端口配置

- 保持端口 8090 不变
- 确保端口没有被其他服务占用

### 4. 依赖服务

blog-ops 依赖以下服务：

- **Redis**: 用于灰度发布的用户标记存储
- **RocketMQ**: 用于 CDC 事件发送（如果启用）
- **PostgreSQL**: 用于 CDC 数据捕获（如果启用）
- **Nacos**: 用于服务注册和配置管理

### 5. 启动顺序

由于 CDC 和灰度发布默认关闭，blog-ops 可以独立启动，不需要特定的启动顺序。

## Future Enhancements

### 1. CDC 功能增强

- 支持更多表的数据变更捕获
- 支持数据变更的实时告警
- 支持数据变更的审计日志

### 2. 灰度发布功能增强

- 支持基于地域的灰度发布
- 支持基于用户画像的灰度发布
- 支持 A/B 测试功能
- 支持灰度发布的可视化监控

### 3. 运维工具扩展

- 添加数据备份和恢复功能
- 添加数据库性能监控
- 添加慢查询分析
- 添加数据一致性检查工具

### 4. 自动化运维

- 支持定时任务管理
- 支持自动化巡检
- 支持故障自动恢复
- 支持容量自动扩缩容
