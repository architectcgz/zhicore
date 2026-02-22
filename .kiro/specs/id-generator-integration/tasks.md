# Implementation Plan: ID Generator Integration

## Overview

本实施计划将 ZhiCore-microservice 的 ID 生成功能从内置的 ZhiCore-leaf 服务迁移到独立的 id-generator 服务。采用渐进式迁移策略，确保系统稳定性和向后兼容性。

## Tasks

- [x] 1. 添加 ID Generator 依赖
  - 在父 pom.xml 的 dependencyManagement 中添加 id-generator-spring-boot-starter 依赖
  - 版本号设置为 1.0.0
  - _Requirements: 1.1, 1.2_

- [x] 2. 创建 IdGeneratorService 接口
  - 在 ZhiCore-common 模块创建 IdGeneratorService 接口
  - 定义 nextSnowflakeId()、nextSegmentId(String bizTag)、nextBatchIds(int count) 方法
  - 添加完整的 JavaDoc 注释
  - _Requirements: 1.1, 2.5_

- [x] 3. 创建 IdGeneratorService 实现类
  - [x] 3.1 在 ZhiCore-common 模块创建 IdGeneratorServiceImpl 实现类
    - 注入 IdGeneratorClient 依赖
    - 实现所有接口方法
    - 添加日志记录
    - _Requirements: 1.2, 2.1_

  - [ ]* 3.2 编写 IdGeneratorServiceImpl 单元测试
    - 测试 nextSnowflakeId() 方法
    - 测试 nextSegmentId() 方法
    - 测试 nextBatchIds() 方法
    - 测试异常处理
    - _Requirements: 7.1_

- [x] 4. 创建异常类
  - 创建 IdGenerationException 异常类
  - 创建 IdGeneratorConnectionException 异常类
  - 创建 IdGeneratorTimeoutException 异常类
  - 创建 IdGeneratorConfigurationException 异常类
  - _Requirements: 6.1, 6.2, 6.3_

- [x] 5. 迁移 ZhiCore-notification 服务
  - [x] 5.1 添加 id-generator-spring-boot-starter 依赖到 ZhiCore-notification/pom.xml
    - _Requirements: 1.2_

  - [x] 5.2 添加 ID Generator 配置到 application.yml
    - 配置 server-url、timeout、retry-times、mode
    - _Requirements: 4.1_

  - [x] 5.3 修改 NotificationApplicationService
    - 将 generateId() 方法从返回 String 改为返回 Long
    - 替换 leafServiceClient 调用为 idGeneratorService 调用
    - 更新所有使用 generateId() 的方法
    - _Requirements: 2.1, 2.4_

  - [x] 5.4 更新 Notification 实体类
    - 将 id 字段类型从 String 改为 Long
    - 更新相关的 getter/setter 方法
    - _Requirements: 2.4_

  - [ ]* 5.5 编写集成测试
    - 测试通知创建功能
    - 验证 ID 生成正确性
    - _Requirements: 7.2, 7.3_

- [x] 6. 迁移 ZhiCore-message 服务
  - [x] 6.1 添加 id-generator-spring-boot-starter 依赖到 ZhiCore-message/pom.xml
    - _Requirements: 1.2_

  - [x] 6.2 添加 ID Generator 配置到 application.yml
    - 配置 server-url、timeout、retry-times、mode
    - _Requirements: 4.1_

  - [x] 6.3 修改 MessageApplicationService
    - 将 generateId() 方法从返回 String 改为返回 Long
    - 替换 leafServiceClient 调用为 idGeneratorService 调用
    - 更新所有使用 generateId() 的方法
    - _Requirements: 2.2, 2.4_

  - [x] 6.4 更新 Message 和 Conversation 实体类
    - 将 id 字段类型从 String 改为 Long
    - 更新相关的 getter/setter 方法
    - _Requirements: 2.4_

  - [ ]* 6.5 编写集成测试
    - 测试消息发送功能
    - 验证 ID 生成正确性
    - _Requirements: 7.2, 7.3_

- [ ] 7. 检查点 - 验证核心服务迁移
  - 确保 ZhiCore-notification 和 ZhiCore-message 服务正常工作
  - 验证 ID 生成功能正确
  - 检查日志无异常
  - 询问用户是否继续

- [x] 8. 迁移其他服务模块
  - [x] 8.1 识别所有使用 Leaf Service 的服务模块
    - 搜索 leafServiceClient 的使用
    - 列出需要迁移的服务清单
    - _Requirements: 2.3_

  - [x] 8.2 逐个迁移剩余服务
    - 对每个服务重复步骤 5.1-5.4 的操作
    - _Requirements: 2.3_

  - [ ]* 8.3 编写端到端测试
    - 测试跨服务的业务流程
    - 验证 ID 在不同服务间的一致性
    - _Requirements: 7.3_

- [x] 9. 配置 Nacos 配置中心
  - [x] 9.1 创建共享配置 id-generator-config.yml
    - 配置 ID Generator Service 地址
    - 配置超时和重试参数
    - _Requirements: 4.2_

  - [x] 9.2 更新各服务配置引用共享配置
    - 在 bootstrap.yml 中添加共享配置引用
    - _Requirements: 4.2_

- [ ]* 10. 编写属性测试
  - [ ]* 10.1 编写 ID 唯一性属性测试
    - **Property 1: ID 唯一性**
    - **Validates: Requirements 1.1, 2.1, 2.2, 2.3**

  - [ ]* 10.2 编写 ID 递增性属性测试
    - **Property 2: ID 递增性**
    - **Validates: Requirements 5.3**

  - [ ]* 10.3 编写配置有效性属性测试
    - **Property 3: 配置有效性**
    - **Validates: Requirements 4.1, 4.2**

  - [ ]* 10.4 编写错误处理属性测试
    - **Property 4: 错误处理**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4**

  - [ ]* 10.5 编写批量生成一致性属性测试
    - **Property 5: 批量生成一致性**
    - **Validates: Requirements 2.5**

  - [ ]* 10.6 编写超时处理属性测试
    - **Property 6: 超时处理**
    - **Validates: Requirements 4.3, 6.3**

  - [ ]* 10.7 编写重试机制属性测试
    - **Property 7: 重试机制**
    - **Validates: Requirements 4.4**

  - [ ]* 10.8 编写向后兼容性属性测试
    - **Property 8: 向后兼容性**
    - **Validates: Requirements 5.1, 5.2**

- [x] 11. 移除 Leaf Service 依赖
  - [x] 11.1 从父 pom.xml 中移除 ZhiCore-leaf 模块声明
    - _Requirements: 3.1_

  - [x] 11.2 删除 ZhiCore-leaf 模块目录
    - _Requirements: 3.1_

  - [x] 11.3 移除所有 Leaf Feign Client 接口和实现
    - 搜索并删除 LeafServiceClient 相关代码
    - _Requirements: 3.2_

  - [x] 11.4 清理 Nacos 中的 ZhiCore-leaf 配置
    - 删除 ZhiCore-leaf 服务配置
    - _Requirements: 3.4_

- [-] 12. 数据库迁移（如果需要）
  - [x] 12.1 评估数据库字段类型
    - 检查是否需要将 VARCHAR 类型的 ID 字段迁移到 BIGINT
    - _Requirements: 5.1, 5.2_

  - [x] 12.2 创建数据库迁移脚本（如果需要）
    - 创建新字段
    - 数据迁移
    - 删除旧字段
    - _Requirements: 5.1, 5.2_

  - [-] 12.3 执行数据库迁移（如果需要）
    - 在测试环境验证
    - 在生产环境执行
    - _Requirements: 5.1, 5.2_

- [x] 13. 更新部署配置
  - [x] 13.1 更新 Docker Compose 配置
    - 添加 id-generator-service 服务
    - 配置服务依赖关系
    - _Requirements: 8.1_

  - [x] 13.2 更新环境变量配置
    - 添加 ID_GENERATOR_SERVER_URL 等环境变量
    - _Requirements: 8.1_

- [ ] 14. 检查点 - 最终验证
  - 运行所有测试确保通过
  - 验证所有服务正常启动
  - 检查监控指标
  - 询问用户是否满意

- [ ]* 15. 编写性能测试
  - 使用 JMH 编写 ID 生成性能基准测试
  - 对比 Leaf Service 和 ID Generator Service 的性能
  - _Requirements: 7.1_

- [ ]* 16. 更新文档
  - 更新系统架构文档
  - 更新部署文档
  - 更新运维手册
  - _Requirements: 8.1, 8.2, 8.3_

## Notes

- 任务标记 `*` 的为可选任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求编号，确保可追溯性
- 建议按顺序执行任务，确保渐进式迁移的稳定性
- 在关键检查点处暂停，等待用户确认后再继续
- 属性测试使用 jqwik 框架，每个测试至少运行 100 次迭代
