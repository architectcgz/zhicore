# Implementation Plan: Post MongoDB Hybrid Storage

## Overview

本实施计划将博客文章服务从单一 PostgreSQL 存储迁移到 PostgreSQL + MongoDB 混合存储架构。实施采用增量方式，确保每个步骤都可以独立验证和测试。核心策略是先建立基础设施，然后实现双写机制，最后添加高级功能（版本控制、草稿、归档）。

## Tasks

- [x] 1. MongoDB 基础设施部署和配置
  - 在 docker-compose.yml 中添加 MongoDB 8.0 和 Mongo Express 服务
  - 使用 MongoDB 8.0 官方镜像（mongo:8.0）
  - 配置 MongoDB 连接参数（主机、端口、数据库名、认证）
  - 创建 MongoDB 初始化脚本（数据库、集合、索引）
  - 在 blog-post 服务中添加 Spring Data MongoDB 依赖（兼容 MongoDB 8.0）
  - 创建 MongoDB 配置类和连接验证
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 12.1_
  - _版本要求: MongoDB 8.0.x（支持到 2029-10-31）_

- [ ]* 1.1 编写 MongoDB 连接集成测试
  - 测试 MongoDB 连接成功
  - 测试数据库和集合创建
  - 测试索引创建
  - _Requirements: 1.4_

- [x] 2. 数据模型和 Repository 层实现
  - [x] 2.1 创建 MongoDB 文档模型
    - 创建 PostContent 文档类（包含 postId、contentType、raw、html、text 等字段）
    - 创建 PostVersion 文档类（版本历史）
    - 创建 PostDraft 文档类（草稿）
    - 创建 PostArchive 文档类（归档）
    - _Requirements: 2.1, 3.1, 4.1, 6.2_

  - [x] 2.2 修改 PostgreSQL 实体类
    - 从 Post 实体中移除 raw 和 html 字段
    - 添加 isArchived 字段
    - 更新数据库迁移脚本
    - _Requirements: 2.1, 6.3_

  - [x] 2.3 实现 PostContentRepository
    - 实现 findByPostId 方法
    - 实现 save 方法
    - 实现 deleteByPostId 方法
    - 实现 findByPostIdIn 批量查询方法
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 2.4 编写 PostContentRepository 属性测试

    - **Property 15: 内容格式支持**
    - **Validates: Requirements 5.1, 5.3, 5.4**
    - _Requirements: 5.1, 5.3, 5.4_

  - [x] 2.5 编写 Repository 单元测试

    - 测试 CRUD 操作
    - 测试批量查询
    - 测试边界条件
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 3. 双写机制实现
  - [x] 3.1 实现 DualStorageManager 接口
    - 实现 createPost 方法（三阶段提交：1. PG写入PUBLISHING状态 2. Mongo写入 3. PG更新为PUBLISHED状态）
    - 实现 getPostFullDetail 方法（并行查询）
    - 实现 getPostContent 方法（仅查询内容）
    - 实现 updatePost 方法（双写更新）
    - 实现 deletePost 方法（双删除）
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.2 编写双写原子性属性测试

    - **Property 1: 双写原子性**
    - **Validates: Requirements 2.1, 2.3, 7.1**
    - _Requirements: 2.1, 2.3, 7.1_

  - [x] 3.3 编写数据一致性属性测试

    - **Property 2: 数据一致性**
    - **Validates: Requirements 2.2, 7.5**
    - _Requirements: 2.2, 7.5_

  - [x] 3.4 编写删除同步性属性测试

    - **Property 3: 删除同步性**
    - **Validates: Requirements 2.4**
    - _Requirements: 2.4_

  - [x] 3.5 编写回滚完整性属性测试

    - **Property 4: 回滚完整性**
    - **Validates: Requirements 2.5, 7.2, 7.3**
    - _Requirements: 2.5, 7.2, 7.3_

  - [x] 3.6 编写列表查询优化属性测试

    - **Property 5: 列表查询优化**
    - **Validates: Requirements 2.6**
    - _Requirements: 2.6_

  - [x] 3.7 编写双写机制单元测试

    - 测试 PostgreSQL 写入成功但 MongoDB 失败的回滚
    - 测试 MongoDB 写入成功但 PostgreSQL 失败的补偿删除
    - 测试网络超时场景
    - _Requirements: 2.5, 7.2, 7.3_

- [ ] 4. Checkpoint - 验证双写机制
  - 确保所有双写测试通过
  - 验证数据一致性
  - 如有问题请询问用户

- [x] 5. 版本历史管理实现
  - [x] 5.1 实现 VersionManager 接口
    - 实现 createVersion 方法（创建版本快照）
    - 实现 getVersions 方法（分页查询版本列表）
    - 实现 getVersion 方法（查询特定版本）
    - 实现 restoreVersion 方法（恢复到历史版本）
    - 实现 cleanOldVersions 方法（清理旧版本）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 5.2 编写版本创建单调性属性测试

    - **Property 6: 版本创建单调性**
    - **Validates: Requirements 3.1, 3.6**
    - _Requirements: 3.1, 3.6_

  - [x] 5.3 编写版本排序正确性属性测试

    - **Property 7: 版本排序正确性**
    - **Validates: Requirements 3.2**
    - _Requirements: 3.2_

  - [x] 5.4 编写版本内容完整性属性测试

    - **Property 8: 版本内容完整性**
    - **Validates: Requirements 3.3, 3.6**
    - _Requirements: 3.3, 3.6_

  - [x] 5.5 编写版本恢复幂等性属性测试

    - **Property 9: 版本恢复幂等性**
    - **Validates: Requirements 3.4**
    - _Requirements: 3.4_

  - [x] 5.6 编写版本清理策略属性测试

    - **Property 10: 版本清理策略**
    - **Validates: Requirements 3.5**
    - _Requirements: 3.5_

  - [x] 5.7 编写版本管理单元测试

    - 测试版本号生成
    - 测试版本列表分页
    - 测试版本恢复边界条件
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [-] 6. 草稿自动保存实现
  - [x] 6.1 实现 DraftManager 接口
    - 实现 saveDraft 方法（Upsert 模式）
    - 实现 getLatestDraft 方法（查询最新草稿）
    - 实现 getUserDrafts 方法（查询用户所有草稿）
    - 实现 deleteDraft 方法（删除草稿）
    - 实现 cleanExpiredDrafts 方法（清理过期草稿）
    - _Requirements: 4.1, 4.2, 4.4, 4.6_

  - [x] 6.2 编写草稿发布清理属性测试

    - **Property 13: 草稿发布清理**
    - **Validates: Requirements 4.4**
    - _Requirements: 4.4_

  - [x] 6.3 编写草稿保存容错性属性测试

    - **Property 14: 草稿保存容错性**
    - **Validates: Requirements 4.5**
    - _Requirements: 4.5_

  - [x] 6.4 编写草稿管理单元测试

    - 测试 Upsert 逻辑（同一用户同一文章只有一个草稿）
    - 测试草稿恢复提示逻辑
    - 测试草稿过期清理
    - _Requirements: 4.1, 4.2, 4.3, 4.6_

- [x] 7. 富文本内容扩展实现
  - [x] 7.1 扩展 PostContent 模型
    - 添加 blocks 字段（内容块列表）
    - 添加 media 字段（媒体资源列表）
    - 添加 wordCount 和 readingTime 字段
    - _Requirements: 5.2, 5.5_

  - [x] 7.2 实现富文本内容处理逻辑
    - 实现内容块序列化和反序列化
    - 实现媒体资源元数据提取
    - 实现字数统计和阅读时间计算
    - _Requirements: 5.3, 5.4, 5.6_

  - [x] 7.3 编写富文本块类型支持属性测试

    - **Property 16: 富文本块类型支持**
    - **Validates: Requirements 5.2**
    - _Requirements: 5.2_

  - [x] 7.4 编写媒体资源元数据完整性属性测试

    - **Property 17: 媒体资源元数据完整性**
    - **Validates: Requirements 5.5**
    - _Requirements: 5.5_

  - [x] 7.5 编写富文本内容单元测试

    - 测试各种内容块类型的序列化
    - 测试媒体资源元数据提取
    - 测试字数统计准确性
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6_

- [ ] 8. Checkpoint - 验证核心功能
  - 确保版本控制、草稿、富文本功能测试通过
  - 验证功能完整性
  - 如有问题请询问用户

- [-] 9. 内容归档和冷热分离实现
  - [x] 9.1 实现 ArchiveManager 接口
    - 实现 archivePost 方法（归档文章）
    - 实现 restorePost 方法（恢复归档）
    - 实现 getArchivedContent 方法（查询归档内容）
    - 实现 batchArchive 方法（批量归档）
    - 实现 isArchived 方法（检查归档状态）
    - _Requirements: 6.2, 6.3, 6.4, 6.5_

  - [x] 9.2 编写冷数据识别准确性属性测试

    - **Property 18: 冷数据识别准确性**
    - **Validates: Requirements 6.1**
    - _Requirements: 6.1_

  - [ ]* 9.3 编写归档数据完整性属性测试
    - **Property 19: 归档数据完整性**
    - **Validates: Requirements 6.2, 6.3**
    - _Requirements: 6.2, 6.3_

  - [ ]* 9.4 编写归档内容可访问性属性测试
    - **Property 20: 归档内容可访问性**
    - **Validates: Requirements 6.4**
    - _Requirements: 6.4_

  - [ ]* 9.5 编写归档自动恢复属性测试
    - **Property 21: 归档自动恢复**
    - **Validates: Requirements 6.5**
    - _Requirements: 6.5_

  - [ ]* 9.6 编写归档错误隔离属性测试
    - **Property 22: 归档错误隔离**
    - **Validates: Requirements 6.6**
    - _Requirements: 6.6_

  - [ ]* 9.7 编写归档管理单元测试
    - 测试归档条件判断
    - 测试归档后的数据状态
    - 测试归档失败回滚
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [-] 10. 一致性检查和修复实现
  - [x] 10.1 实现 ConsistencyChecker 接口
    - 实现 checkPost 方法（单篇文章一致性检查）
    - 实现 batchCheck 方法（批量一致性检查）
    - 实现 repair 方法（数据修复）
    - 实现 scheduledCheck 方法（定时检查）
    - _Requirements: 7.4, 7.5, 7.6_

  - [ ]* 10.2 编写一致性检查准确性属性测试
    - **Property 23: 一致性检查准确性**
    - **Validates: Requirements 7.5**
    - _Requirements: 7.5_

  - [ ]* 10.3 编写数据修复正确性属性测试
    - **Property 24: 数据修复正确性**
    - **Validates: Requirements 7.6**
    - _Requirements: 7.6_

  - [ ]* 10.4 编写一致性检查单元测试
    - 测试不一致数据检测
    - 测试修复策略
    - 测试定时任务调度
    - _Requirements: 7.4, 7.5, 7.6_

- [x] 11. 缓存层集成
  - [x] 11.1 实现 Redis 缓存策略
    - 实现文章内容缓存（热点数据）
    - 实现文章详情缓存
    - 实现草稿缓存
    - 实现缓存失效策略
    - _Requirements: 8.3_

  - [ ]* 11.2 编写缓存一致性属性测试
    - **Property 25: 缓存一致性**
    - **Validates: Requirements 8.3**
    - _Requirements: 8.3_

  - [ ]* 11.3 编写缓存单元测试
    - 测试缓存命中和未命中
    - 测试缓存更新和失效
    - 测试缓存穿透保护
    - _Requirements: 8.3_

- [x] 12. 性能优化实现
  - [x] 12.1 实现查询性能优化
    - 优化文章列表查询（仅查询 PostgreSQL）
    - 优化文章详情查询（并行查询 PG 和 Mongo）
    - 实现批量查询优化
    - _Requirements: 8.1, 8.2, 8.5_

  - [x] 12.2 实现降级策略（使用 Sentinel）
    - 集成 Spring Cloud Alibaba Sentinel
    - 配置 Sentinel 规则持久化到 Nacos
    - 在 DualStorageManagerImpl 中使用 @SentinelResource 注解
    - 实现流控、降级、系统保护规则
    - 提供规则配置文件和上传脚本
    - _Requirements: 8.6, 10.4_

  - [ ]* 12.3 编写索引使用验证属性测试
    - **Property 26: 索引使用验证**
    - **Validates: Requirements 8.4**
    - _Requirements: 8.4_

  - [ ]* 12.4 编写批量操作原子性属性测试
    - **Property 27: 批量操作原子性**
    - **Validates: Requirements 8.5**
    - _Requirements: 8.5_

  - [ ]* 12.5 编写降级功能可用性属性测试
    - **Property 28: 降级功能可用性**
    - **Validates: Requirements 8.6, 10.4**
    - _Requirements: 8.6, 10.4_

  - [ ]* 12.6 编写性能测试
    - 测试文章列表查询响应时间（< 100ms）
    - 测试文章详情查询响应时间（< 200ms）
    - 测试并发写入性能（> 1000 TPS）
    - _Requirements: 8.1, 8.2_

- [ ] 13. Checkpoint - 验证性能和稳定性
  - 确保所有性能测试通过
  - 验证降级策略有效性
  - 如有问题请询问用户

- [ ] 14. 数据迁移工具实现
  - [ ] 14.1 实现迁移工具核心逻辑
    - 实现从 PostgreSQL 读取文章内容
    - 实现写入 MongoDB 的逻辑
    - 实现数据验证逻辑
    - 实现迁移进度跟踪
    - _Requirements: 9.1, 9.2_

  - [ ] 14.2 实现迁移错误处理
    - 实现单个文章迁移失败的错误记录
    - 实现迁移失败重试机制
    - 实现迁移报告生成
    - _Requirements: 9.3, 9.4_

  - [ ] 14.3 实现迁移验证和清理
    - 实现迁移后的数据一致性验证
    - 实现 PostgreSQL 内容字段清理选项
    - _Requirements: 9.5, 9.6_

  - [ ]* 14.4 编写迁移数据完整性属性测试
    - **Property 29: 迁移数据完整性（Round-trip Property）**
    - **Validates: Requirements 9.1, 9.2, 9.5**
    - _Requirements: 9.1, 9.2, 9.5_

  - [ ]* 14.5 编写迁移错误隔离属性测试
    - **Property 30: 迁移错误隔离**
    - **Validates: Requirements 9.3**
    - _Requirements: 9.3_

  - [ ]* 14.6 编写迁移报告完整性属性测试
    - **Property 31: 迁移报告完整性**
    - **Validates: Requirements 9.4**
    - _Requirements: 9.4_

  - [ ]* 14.7 编写迁移工具单元测试
    - 测试迁移逻辑
    - 测试错误处理
    - 测试报告生成
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 15. 监控和告警实现（Spring Cloud Alibaba 架构）
  - [x] 15.1 集成 Micrometer 和 Prometheus 监控指标
    - 使用 Micrometer 收集数据库查询响应时间
    - 使用 Micrometer 统计双写成功率和失败率
    - 配置 Prometheus 端点暴露指标
    - 实现自定义监控指标（慢查询、数据一致性等）
    - _Requirements: 10.1, 10.2, 10.5_

  - [x] 15.2 集成 Sentinel 实现降级和告警
    - 配置 Sentinel 规则监控双写操作
    - 实现 MongoDB 连接失败降级策略
    - 配置 Sentinel 告警规则（失败率、响应时间）
    - 集成 Sentinel Dashboard 实时监控
    - _Requirements: 10.3, 10.4, 10.5, 8.6_

  - [x] 15.3 实现告警通知机制
    - 实现基于日志的告警通知
    - 实现数据不一致告警
    - 实现存储空间不足告警
    - 实现慢查询告警
    - 支持告警去重和聚合
    - _Requirements: 10.3, 10.4, 10.5, 10.6_

  - [x] 15.4 配置 Grafana 监控面板
    - 创建 PostgreSQL 性能监控面板
    - 创建 MongoDB 性能监控面板
    - 创建双写操作监控面板
    - 创建告警规则和通知渠道
    - _Requirements: 10.1, 10.2, 10.5_

  - [ ]* 15.3 编写监控指标记录属性测试
    - **Property 32: 监控指标记录**
    - **Validates: Requirements 10.1, 10.2**
    - _Requirements: 10.1, 10.2_

  - [ ]* 15.4 编写告警触发及时性属性测试
    - **Property 33: 告警触发及时性**
    - **Validates: Requirements 10.3, 10.4, 10.5, 10.6**
    - _Requirements: 10.3, 10.4, 10.5, 10.6_

  - [ ]* 15.5 编写监控和告警单元测试
    - 测试指标收集准确性
    - 测试告警触发条件
    - 测试告警通知发送
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [-] 16. API 层改造
  - [x] 16.1 更新文章 API 接口
    - 更新创建文章接口（使用双写）
    - 更新查询文章详情接口（聚合数据）
    - 更新文章列表接口（仅返回元数据）
    - 更新更新文章接口（双写更新）
    - 更新删除文章接口（双删除）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 11.1, 11.2_

  - [-] 16.2 添加版本历史 API
    - 添加查询版本列表接口
    - 添加查询特定版本接口
    - 添加恢复版本接口
    - _Requirements: 3.2, 3.3, 3.4_

  - [x] 16.3 添加草稿管理 API
    - 添加保存草稿接口
    - 添加查询草稿接口
    - 添加删除草稿接口
    - _Requirements: 4.1, 4.2, 4.4, 4.6_

  - [ ]* 16.4 编写 API 响应结构兼容性属性测试
    - **Property 34: API 响应结构兼容性**
    - **Validates: Requirements 11.1, 11.2**
    - _Requirements: 11.1, 11.2_

  - [ ]* 16.5 编写 API 请求参数兼容性属性测试
    - **Property 35: API 请求参数兼容性**
    - **Validates: Requirements 11.3**
    - _Requirements: 11.3_

  - [ ]* 16.6 编写 API 错误响应兼容性属性测试
    - **Property 36: API 错误响应兼容性**
    - **Validates: Requirements 11.6**
    - _Requirements: 11.6_

  - [ ]* 16.7 编写新旧功能隔离性属性测试
    - **Property 37: 新旧功能隔离性**
    - **Validates: Requirements 11.4**
    - _Requirements: 11.4_

  - [ ]* 16.8 编写 API 集成测试
    - 测试完整的文章创建流程
    - 测试完整的文章编辑流程
    - 测试版本历史管理流程
    - 测试草稿自动保存流程
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.2, 3.3, 3.4, 4.1, 4.2, 4.4_

- [x] 17. 配置管理实现
  - [x] 17.1 实现配置类
    - 创建 MongoDBProperties 配置类
    - 创建 ArchiveProperties 配置类
    - 创建 VersionProperties 配置类
    - 创建 DraftProperties 配置类
    - 创建 ConsistencyProperties 配置类
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [x] 17.2 添加配置文件
    - 在 application.yml 中添加 MongoDB 配置
    - 添加归档策略配置
    - 添加版本控制配置
    - 添加草稿配置
    - 添加一致性检查配置
    - 添加降级策略配置
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [ ]* 17.3 编写配置加载单元测试
    - 测试配置加载正确性
    - 测试配置默认值
    - 测试配置验证
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [ ] 18. Final Checkpoint - 完整性验证
  - 运行所有单元测试
  - 运行所有属性测试（每个测试 100 次迭代）
  - 运行所有集成测试
  - 验证测试覆盖率（> 80%）
  - 确保所有功能正常工作
  - 如有问题请询问用户

- [ ] 19. 文档和部署准备
  - [ ] 19.1 编写部署文档
    - 编写 MongoDB 部署指南
    - 编写数据迁移指南
    - 编写配置说明文档
    - 编写故障排查指南
    - _Requirements: 1.1, 9.1, 12.1_

  - [ ] 19.2 编写 API 文档
    - 更新 API 文档（包含新接口）
    - 编写版本历史 API 文档
    - 编写草稿管理 API 文档
    - _Requirements: 3.2, 3.3, 3.4, 4.1, 4.2, 4.4_

  - [ ] 19.3 准备迁移脚本
    - 创建数据库迁移脚本
    - 创建数据迁移脚本
    - 创建回滚脚本
    - _Requirements: 9.1, 9.2, 9.5_

## Notes

- 任务标记 `*` 的为可选任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求以便追溯
- Checkpoint 任务确保增量验证
- 属性测试验证通用正确性属性
- 单元测试验证具体示例和边界条件
- 所有属性测试应运行至少 100 次迭代
- 使用 jqwik 作为属性测试框架

## 版本要求

- **MongoDB**: 8.0.x（官方镜像：mongo:8.0）
  - 发布日期：2024年10月
  - 支持到：2029年10月31日
  - 性能提升：相比 7.0 有 32% 更快的读取，59% 更快的更新，200%+ 更快的时序查询
- **Mongo Express**: latest（Web 管理界面）
- **Spring Data MongoDB**: 兼容 MongoDB 8.0 的最新稳定版本
- **MongoDB Java Driver**: 5.x（支持 MongoDB 8.0）
