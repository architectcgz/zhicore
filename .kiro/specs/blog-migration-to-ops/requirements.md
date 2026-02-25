# Requirements Document

## Introduction

将 `ZhiCore-migration` 模块重构为 `ZhiCore-ops` 运维服务模块。移除已废弃的 Flyway 数据库迁移功能和数据迁移服务，保留 CDC（Change Data Capture）和灰度发布功能，为未来的运维需求做准备。

## Glossary

- **ZhiCore-migration**: 当前的数据迁移服务模块
- **ZhiCore-ops**: 重构后的运维服务模块（Operations Service）
- **CDC**: Change Data Capture，数据变更捕获，使用 Debezium 实现
- **Flyway**: 数据库版本管理工具，已不再需要
- **Gray Release**: 灰度发布，用于新功能的渐进式发布
- **Debezium**: 开源的 CDC 平台，用于捕获数据库变更
- **Migration Service**: 数据迁移服务，用于从旧数据库迁移数据到新数据库（已废弃）

## Requirements

### Requirement 1: 模块重命名

**User Story:** 作为开发者，我想将 ZhiCore-migration 模块重命名为 ZhiCore-ops，以便更准确地反映其作为运维服务的定位。

#### Acceptance Criteria

1. WHEN 重命名模块目录 THEN THE System SHALL 将 `ZhiCore-migration` 目录重命名为 `ZhiCore-ops`
2. WHEN 更新 Maven 配置 THEN THE System SHALL 更新 `pom.xml` 中的 artifactId 为 `ZhiCore-ops`
3. WHEN 更新包名 THEN THE System SHALL 将 Java 包从 `com.ZhiCore.migration` 重命名为 `com.ZhiCore.ops`
4. WHEN 更新应用名称 THEN THE System SHALL 将 Spring 应用名从 `ZhiCore-migration` 改为 `ZhiCore-ops`
5. WHEN 更新根 pom.xml THEN THE System SHALL 更新父 pom.xml 中的模块引用

### Requirement 2: 移除 Flyway 相关功能

**User Story:** 作为开发者，我想移除 Flyway 相关的依赖和配置，因为数据库迁移脚本已迁移到 `/database` 目录，且项目为单人开发不需要版本管理。

#### Acceptance Criteria

1. WHEN 移除 Flyway 依赖 THEN THE System SHALL 从 `pom.xml` 中删除 `flyway-core` 和 `flyway-database-postgresql` 依赖
2. WHEN 移除 Flyway 插件 THEN THE System SHALL 从 `pom.xml` 的 build 配置中删除 `flyway-maven-plugin`
3. WHEN 移除 Flyway 配置类 THEN THE System SHALL 删除 `infrastructure/config/FlywayConfig.java`
4. WHEN 删除迁移脚本目录 THEN THE System SHALL 删除 `src/main/resources/db/migration` 整个目录
5. WHEN 清理配置文件 THEN THE System SHALL 从 `application.yml` 中移除 Flyway 相关配置（如果存在）

### Requirement 3: 移除数据迁移服务

**User Story:** 作为开发者，我想移除已废弃的数据迁移服务代码，因为数据迁移工作已完成，不再需要这些功能。

#### Acceptance Criteria

1. WHEN 删除迁移服务类 THEN THE System SHALL 删除 `application/service/UserMigrationService.java`
2. WHEN 删除迁移服务类 THEN THE System SHALL 删除 `application/service/PostMigrationService.java`
3. WHEN 删除迁移服务类 THEN THE System SHALL 删除 `application/service/CommentMigrationService.java`
4. WHEN 删除校验服务类 THEN THE System SHALL 删除 `application/service/MigrationValidator.java`
5. WHEN 删除领域模型 THEN THE System SHALL 删除 `domain/model/MigrationResult.java`
6. WHEN 删除领域模型 THEN THE System SHALL 删除 `domain/model/ValidationResult.java`
7. WHEN 删除迁移控制器 THEN THE System SHALL 删除 `interfaces/controller/MigrationController.java`
8. WHEN 删除空目录 THEN THE System SHALL 删除 `application/service` 目录（如果为空）
9. WHEN 删除空目录 THEN THE System SHALL 删除 `domain/model` 目录（如果为空）
10. WHEN 删除空目录 THEN THE System SHALL 删除 `domain` 目录（如果为空）

### Requirement 4: 清理不必要的依赖

**User Story:** 作为开发者，我想移除仅用于数据迁移的依赖，以减少模块的复杂度和构建大小。

#### Acceptance Criteria

1. WHEN 评估 MyBatis-Plus 依赖 THEN THE System SHALL 确认是否仅用于数据迁移
2. IF MyBatis-Plus 仅用于数据迁移 THEN THE System SHALL 从 `pom.xml` 中移除 `mybatis-plus-spring-boot3-starter` 依赖
3. WHEN 评估 JDBC 依赖 THEN THE System SHALL 确认 CDC 或灰度发布是否需要数据库连接
4. IF JDBC 不再需要 THEN THE System SHALL 从 `pom.xml` 中移除 `spring-boot-starter-jdbc` 依赖
5. WHEN 清理配置 THEN THE System SHALL 从 `application.yml` 中移除数据源配置（如果不再需要）
6. WHEN 清理配置 THEN THE System SHALL 从 `application.yml` 中移除 MyBatis-Plus 配置
7. WHEN 删除配置类 THEN THE System SHALL 删除 `infrastructure/config/DataSourceConfig.java`（如果存在）
8. WHEN 删除配置类 THEN THE System SHALL 删除 `infrastructure/config/MigrationProperties.java`

### Requirement 5: 保留 CDC 功能

**User Story:** 作为开发者，我想保留 CDC 相关的代码和配置，因为未来可能需要使用数据变更捕获功能。

#### Acceptance Criteria

1. WHEN 保留 Debezium 依赖 THEN THE System SHALL 确保 `pom.xml` 中保留 `debezium-api`、`debezium-embedded` 和 `debezium-connector-postgres` 依赖
2. WHEN 保留 CDC 代码 THEN THE System SHALL 保留 `infrastructure/cdc` 目录下的所有文件
3. WHEN 保留 CDC 配置 THEN THE System SHALL 保留 `infrastructure/config/CdcProperties.java`
4. WHEN 保留 CDC 配置 THEN THE System SHALL 保留 `infrastructure/config/DebeziumConfig.java`
5. WHEN 保留 CDC 配置 THEN THE System SHALL 在 `application.yml` 中保留 `cdc` 配置段
6. WHEN 更新包引用 THEN THE System SHALL 更新 CDC 相关类中的包导入语句（从 `com.ZhiCore.migration` 到 `com.ZhiCore.ops`）

### Requirement 6: 保留灰度发布功能

**User Story:** 作为开发者，我想保留灰度发布相关的代码和配置，因为未来可能需要使用灰度发布功能进行新功能的渐进式上线。

#### Acceptance Criteria

1. WHEN 保留灰度发布代码 THEN THE System SHALL 保留 `infrastructure/gray` 目录下的所有文件
2. WHEN 保留灰度发布配置 THEN THE System SHALL 保留 `infrastructure/config/GrayReleaseProperties.java`
3. WHEN 保留灰度发布控制器 THEN THE System SHALL 保留 `interfaces/controller/GrayReleaseController.java`
4. WHEN 保留灰度发布配置 THEN THE System SHALL 在 `application.yml` 中保留 `gray` 配置段
5. WHEN 更新包引用 THEN THE System SHALL 更新灰度发布相关类中的包导入语句（从 `com.ZhiCore.migration` 到 `com.ZhiCore.ops`）
6. WHEN 更新 API 路径 THEN THE System SHALL 保持 GrayReleaseController 的 API 路径为 `/api/gray`（不变）

### Requirement 7: 更新配置文件

**User Story:** 作为开发者，我想清理和更新配置文件，移除已废弃的配置项，保留必要的配置。

#### Acceptance Criteria

1. WHEN 更新应用名称 THEN THE System SHALL 在 `application.yml` 中将 `spring.application.name` 改为 `ZhiCore-ops`
2. WHEN 移除废弃配置 THEN THE System SHALL 删除 `application.yml` 中已注释的 `migration` 配置段
3. WHEN 清理数据源配置 THEN THE System SHALL 评估并移除不需要的数据源配置（如果 CDC 和灰度发布不需要）
4. WHEN 清理 MyBatis 配置 THEN THE System SHALL 删除 `application.yml` 中的 `mybatis-plus` 配置段
5. WHEN 更新模块描述 THEN THE System SHALL 更新 `pom.xml` 中的 `<description>` 为 "运维服务 - CDC 和灰度发布管理"
6. WHEN 更新日志配置 THEN THE System SHALL 在 `application.yml` 中将日志包名从 `com.ZhiCore.migration` 改为 `com.ZhiCore.ops`

### Requirement 8: 更新启动类和主类

**User Story:** 作为开发者，我想更新启动类的名称和注释，以反映新的模块定位。

#### Acceptance Criteria

1. WHEN 重命名启动类 THEN THE System SHALL 将 `MigrationApplication.java` 重命名为 `OpsApplication.java`
2. WHEN 更新类注释 THEN THE System SHALL 更新启动类的 JavaDoc 注释为 "运维服务启动类 - 负责 CDC 和灰度发布管理"
3. WHEN 更新包名 THEN THE System SHALL 将启动类的包从 `com.ZhiCore.migration` 改为 `com.ZhiCore.ops`
4. WHEN 更新主类配置 THEN THE System SHALL 确保 `pom.xml` 中的 `spring-boot-maven-plugin` 配置指向正确的主类（如果有显式配置）

### Requirement 9: 更新项目引用

**User Story:** 作为开发者，我想更新项目中所有对 ZhiCore-migration 模块的引用，确保重命名后项目能正常构建和运行。

#### Acceptance Criteria

1. WHEN 更新父 pom THEN THE System SHALL 在根 `pom.xml` 的 `<modules>` 中将 `ZhiCore-migration` 改为 `ZhiCore-ops`
2. WHEN 检查依赖引用 THEN THE System SHALL 检查其他模块是否依赖 `ZhiCore-migration`，如有则更新为 `ZhiCore-ops`
3. WHEN 检查文档引用 THEN THE System SHALL 搜索项目文档中对 `ZhiCore-migration` 的引用并更新
4. WHEN 检查脚本引用 THEN THE System SHALL 搜索脚本文件中对 `ZhiCore-migration` 的引用并更新
5. WHEN 检查配置引用 THEN THE System SHALL 检查 Docker Compose 等配置文件中是否有引用（当前应该没有）

### Requirement 10: 验证重构结果

**User Story:** 作为开发者，我想验证重构后的模块能够正常编译和启动，确保没有遗漏的引用或配置错误。

#### Acceptance Criteria

1. WHEN 执行 Maven 编译 THEN THE System SHALL 成功编译 `ZhiCore-ops` 模块
2. WHEN 执行 Maven 打包 THEN THE System SHALL 成功打包 `ZhiCore-ops` 模块
3. WHEN 启动应用 THEN THE System SHALL 成功启动 `ZhiCore-ops` 服务（CDC 和灰度发布功能默认关闭）
4. WHEN 检查健康端点 THEN THE System SHALL 访问 `/actuator/health` 返回 UP 状态
5. WHEN 检查灰度发布 API THEN THE System SHALL 确认灰度发布 API 在功能关闭时不可访问（符合 `@ConditionalOnProperty` 预期）
6. WHEN 执行全量构建 THEN THE System SHALL 成功构建整个项目（包括 ZhiCore-ops 模块）
