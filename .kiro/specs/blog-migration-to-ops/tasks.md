# Implementation Plan: blog-migration 重构为 blog-ops

## Overview

将 blog-migration 模块重构为 blog-ops 运维服务模块，移除已废弃的 Flyway 和数据迁移功能，保留 CDC 和灰度发布功能。

## Tasks

- [x] 1. 重命名模块目录和基础配置
  - [x] 1.1 使用 Git 重命名模块目录
    - 执行 `git mv blog-migration blog-ops`
    - _Requirements: 1.1_
  
  - [x] 1.2 更新根 pom.xml 中的模块引用
    - 将 `<module>blog-migration</module>` 改为 `<module>blog-ops</module>`
    - _Requirements: 1.5, 9.1_
  
  - [x] 1.3 更新模块 pom.xml
    - 将 `<artifactId>blog-migration</artifactId>` 改为 `<artifactId>blog-ops</artifactId>`
    - 将 `<name>Blog Migration Service</name>` 改为 `<name>Blog Ops Service</name>`
    - 将 `<description>` 改为 "运维服务 - CDC 和灰度发布管理"
    - _Requirements: 1.2, 7.5_

- [ ] 2. 移除 Flyway 相关功能
  - [x] 2.1 移除 Flyway 依赖
    - 从 pom.xml 中删除 `flyway-core` 依赖
    - 从 pom.xml 中删除 `flyway-database-postgresql` 依赖
    - _Requirements: 2.1_
  
  - [x] 2.2 移除 Flyway Maven 插件
    - 从 pom.xml 的 `<build><plugins>` 中删除 `flyway-maven-plugin` 配置
    - _Requirements: 2.2_
  
  - [x] 2.3 删除 Flyway 配置类
    - 删除 `infrastructure/config/FlywayConfig.java`（如果存在）
    - _Requirements: 2.3_
  
  - [x] 2.4 删除数据库迁移脚本目录
    - 删除 `src/main/resources/db/migration` 整个目录
    - _Requirements: 2.4_

- [x] 3. 移除数据迁移服务代码
  - [x] 3.1 删除迁移服务类
    - 删除 `application/service/UserMigrationService.java`
    - 删除 `application/service/PostMigrationService.java`
    - 删除 `application/service/CommentMigrationService.java`
    - 删除 `application/service/MigrationValidator.java`
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 3.2 删除领域模型类
    - 删除 `domain/model/MigrationResult.java`
    - 删除 `domain/model/ValidationResult.java`
    - _Requirements: 3.5, 3.6_
  
  - [x] 3.3 删除迁移控制器
    - 删除 `interfaces/controller/MigrationController.java`
    - _Requirements: 3.7_
  
  - [x] 3.4 删除空目录
    - 删除 `application/service` 目录（如果为空）
    - 删除 `domain/model` 目录（如果为空）
    - 删除 `domain` 目录（如果为空）
    - _Requirements: 3.8, 3.9, 3.10_

- [ ] 4. 清理不必要的依赖和配置
  - [ ] 4.1 评估并移除 MyBatis-Plus 依赖
    - 检查 CDC 和灰度发布是否使用 MyBatis-Plus
    - 如果不使用，从 pom.xml 中删除 `mybatis-plus-spring-boot3-starter` 依赖
    - _Requirements: 4.1, 4.2_
  
  - [ ] 4.2 评估并移除 JDBC 依赖
    - 检查 CDC 是否需要 JDBC 连接（Debezium 可能需要）
    - 如果不需要，从 pom.xml 中删除 `spring-boot-starter-jdbc` 依赖
    - _Requirements: 4.3, 4.4_
  
  - [ ] 4.3 清理配置文件
    - 从 application.yml 中删除数据源配置（如果不需要）
    - 从 application.yml 中删除 mybatis-plus 配置段
    - _Requirements: 4.5, 4.6_
  
  - [ ] 4.4 删除配置类
    - 删除 `infrastructure/config/DataSourceConfig.java`（如果存在）
    - 删除 `infrastructure/config/MigrationProperties.java`
    - _Requirements: 4.7, 4.8_

- [ ] 5. 更新 Java 包名
  - [ ] 5.1 重命名启动类
    - 将 `MigrationApplication.java` 重命名为 `OpsApplication.java`
    - 更新类的 JavaDoc 注释为 "运维服务启动类 - 负责 CDC 和灰度发布管理"
    - _Requirements: 8.1, 8.2_
  
  - [ ] 5.2 批量更新包名
    - 使用 IDE 重构功能将 `com.blog.migration` 重命名为 `com.blog.ops`
    - 或使用命令行工具批量替换包声明和导入语句
    - _Requirements: 1.3, 8.3_
  
  - [ ] 5.3 验证包引用
    - 检查所有 Java 文件的 package 声明
    - 检查所有 import 语句
    - 确保没有遗漏的 `com.blog.migration` 引用
    - _Requirements: 5.6, 6.5_

- [ ] 6. 更新配置文件
  - [ ] 6.1 更新 application.yml
    - 将 `spring.application.name` 改为 `blog-ops`
    - 删除已注释的 `migration` 配置段
    - 删除 `mybatis-plus` 配置段（如果存在）
    - 将 `logging.level.com.blog.migration` 改为 `logging.level.com.blog.ops`
    - 确保保留 `cdc` 配置段
    - 确保保留 `gray` 配置段
    - _Requirements: 7.1, 7.2, 7.4, 7.6, 5.5, 6.4_
  
  - [ ] 6.2 更新 bootstrap.yml（如果需要）
    - 检查是否有对 blog-migration 的引用
    - 如有，更新为 blog-ops
    - _Requirements: 1.4_

- [ ] 7. 检查和更新项目引用
  - [ ] 7.1 检查其他模块的依赖
    - 搜索其他模块的 pom.xml 中是否依赖 blog-migration
    - 如有，更新 artifactId 为 blog-ops
    - _Requirements: 9.2_
  
  - [ ] 7.2 检查文档引用
    - 搜索 docs 目录中对 blog-migration 的引用
    - 更新为 blog-ops
    - _Requirements: 9.3_
  
  - [ ] 7.3 检查脚本引用
    - 搜索 scripts 和 tests 目录中对 blog-migration 的引用
    - 更新为 blog-ops
    - _Requirements: 9.4_
  
  - [ ] 7.4 检查 Docker 配置
    - 检查 docker-compose 文件中是否有 blog-migration 引用
    - 如有，更新为 blog-ops
    - _Requirements: 9.5_

- [ ] 8. 第一次验证 - 编译测试
  - [ ] 8.1 清理并编译模块
    - 执行 `mvn clean compile -pl blog-ops`
    - 确保编译成功，无错误
    - _Requirements: 10.1_
  
  - [ ] 8.2 检查编译警告
    - 查看编译输出中的警告信息
    - 修复任何与包名或导入相关的警告
    - _Requirements: 10.1_

- [ ] 9. 第二次验证 - 打包测试
  - [ ] 9.1 打包模块
    - 执行 `mvn clean package -pl blog-ops`
    - 确保打包成功，生成 jar 文件
    - _Requirements: 10.2_
  
  - [ ] 9.2 检查 jar 文件
    - 验证 jar 文件名为 `blog-ops-1.0.0-SNAPSHOT.jar`
    - 检查 jar 文件大小是否合理
    - _Requirements: 10.2_

- [ ] 10. 第三次验证 - 启动测试
  - [ ] 10.1 启动应用
    - 确保 Redis 服务已启动
    - 执行 `java -jar blog-ops/target/blog-ops-1.0.0-SNAPSHOT.jar`
    - 或使用 IDE 运行 OpsApplication
    - _Requirements: 10.3_
  
  - [ ] 10.2 检查启动日志
    - 确认应用名显示为 blog-ops
    - 确认没有启动错误
    - 确认 CDC 和灰度发布功能未启动（默认关闭）
    - _Requirements: 10.3_
  
  - [ ] 10.3 测试健康检查端点
    - 访问 `http://localhost:8090/actuator/health`
    - 确认返回 `{"status":"UP"}`
    - _Requirements: 10.4_
  
  - [ ] 10.4 测试灰度发布 API（应该不可访问）
    - 尝试访问 `http://localhost:8090/api/gray/config`
    - 确认返回 404（因为 gray.enabled=false）
    - _Requirements: 10.5_

- [ ] 11. 第四次验证 - 全量构建测试
  - [ ] 11.1 执行全量构建
    - 执行 `mvn clean install`
    - 确保所有模块都能成功构建
    - _Requirements: 10.6_
  
  - [ ] 11.2 检查构建输出
    - 确认 blog-ops 模块构建成功
    - 确认没有其他模块因为依赖问题而失败
    - _Requirements: 10.6_

- [ ] 12. 最终检查和清理
  - [ ] 12.1 执行手动验证清单
    - 检查设计文档中的"手动验证清单"
    - 确认所有项目都已完成
    - _Requirements: 所有需求_
  
  - [ ] 12.2 更新相关文档
    - 更新项目 README（如果提到 blog-migration）
    - 更新架构文档
    - 添加 blog-ops 模块说明
    - _Requirements: 9.3_
  
  - [ ] 12.3 提交代码
    - 提交所有更改到 Git
    - 编写清晰的 commit message
    - 推送到远程仓库
    - _Requirements: 所有需求_

- [ ] 13. Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户

## Notes

- 本次重构主要是代码结构调整，不涉及业务逻辑变更
- CDC 和灰度发布功能默认关闭，不影响正常启动
- 重命名操作建议使用 Git 命令以保留文件历史
- 包名重命名建议使用 IDE 的重构功能以确保完整性
- 每个大步骤完成后建议执行编译验证
- 最终需要执行全量构建确保不影响其他模块
