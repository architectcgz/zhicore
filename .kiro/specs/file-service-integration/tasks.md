# Implementation Plan: File Service Integration

## Overview

本实施计划将 file-service 集成到 ZhiCore-microservice 系统中，实现统一的文件管理、多种上传方式支持、CDN 加速和服务解耦。实施将按照以下顺序进行：架构文档更新、依赖配置、核心服务实现、功能集成、Docker 部署、测试验证和文档更新。

## Tasks

- [x] 1. 更新架构设计文档
  - 更新系统架构图，添加 File Service 和 MinIO 组件
  - 更新部署架构图，展示 File Service、MinIO 和 CDN 的部署关系
  - 在架构文档中说明文件服务的定位、职责和数据流
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. 配置 Maven 依赖
  - [x] 2.1 在父 pom.xml 中添加 file-service 版本属性和依赖管理
    - 在 `<properties>` 中添加 `<file-service.version>1.0.0-SNAPSHOT</file-service.version>`
    - 在 `<dependencyManagement>` 中添加 file-service-spring-boot-starter 依赖
    - _Requirements: 2.1_

  - [x] 2.2 在 ZhiCore-common 模块添加 file-service-spring-boot-starter 依赖
    - 在 ZhiCore-common/pom.xml 中添加 starter 依赖
    - _Requirements: 2.2_

- [x] 3. 实现核心文件上传服务
  - [x] 3.1 创建 FileUploadService 接口
    - 在 ZhiCore-common 中创建 `com.zhicore.common.service.FileUploadService` 接口
    - 定义 uploadImage、uploadImage(file, isPublic)、deleteFile、extractFileId 方法
    - _Requirements: 3.1, 4.1, 5.1_

  - [x] 3.2 实现 FileUploadServiceImpl
    - 在 ZhiCore-common 中创建 `com.zhicore.common.service.impl.FileUploadServiceImpl`
    - 注入 FileServiceClient 依赖
    - 实现文件类型验证（只允许图片格式）
    - 实现文件大小验证（最大 10MB）
    - 实现文件上传逻辑（转换为临时文件、上传、清理）
    - 实现文件删除逻辑
    - 实现从 URL 提取文件 ID 的逻辑
    - _Requirements: 3.1, 3.2, 3.3, 4.1, 5.1_

  - [ ]* 3.3 编写 FileUploadServiceImpl 单元测试
    - 测试成功上传图片
    - 测试拒绝非图片文件
    - 测试拒绝超大文件
    - 测试文件删除
    - 测试从 URL 提取文件 ID
    - _Requirements: 13.1_

- [x] 4. 创建文件服务异常类
  - [x] 4.1 创建自定义异常类
    - 在 ZhiCore-common 中创建 `com.zhicore.common.exception.FileUploadException`
    - 创建 `FileNotFoundException`
    - 创建 `FileAccessDeniedException`
    - _Requirements: 9.1, 9.2_

  - [x] 4.2 实现全局异常处理器
    - 在 ZhiCore-common 中创建 `com.zhicore.common.handler.FileServiceExceptionHandler`
    - 处理 FileUploadException、InvalidRequestException、AuthenticationException
    - 处理 FileAccessDeniedException、FileNotFoundException、QuotaExceededException
    - 处理 NetworkException、FileServiceException
    - 记录错误日志并返回友好的错误信息
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [ ]* 4.3 编写异常处理器单元测试
    - 测试各种异常的处理逻辑
    - 验证返回的错误码和错误信息
    - _Requirements: 13.1_

- [x] 5. 配置 File Service 连接
  - [x] 5.1 在 application.yml 中添加 file-service 配置
    - 在 ZhiCore-user 和 ZhiCore-post 的 application.yml 中添加 file-service.client 配置
    - 配置 server-url、tenant-id、超时设置、重试设置
    - _Requirements: 2.3, 7.1, 7.3_

  - [x] 5.2 在 Nacos 配置中心创建共享配置
    - 创建 file-service-config.yml 配置文件
    - 配置生产环境的 File Service 连接信息
    - 配置 CDN 域名
    - _Requirements: 7.2, 7.4_

- [x] 6. 实现用户头像上传功能
  - [x] 6.1 创建 UserAvatarController
    - 在 ZhiCore-user 中创建 `com.ZhiCore.user.controller.UserAvatarController`
    - 实现 POST /api/user/avatar/upload 接口（上传头像）
    - 实现 DELETE /api/user/avatar 接口（删除头像）
    - 注入 FileUploadService 和 UserService
    - _Requirements: 3.1, 3.4_

  - [x] 6.2 在 UserService 中添加头像管理方法
    - 添加 updateAvatar(String avatarUrl) 方法
    - 添加 deleteAvatar() 方法
    - 实现删除旧头像的逻辑（如果存在）
    - _Requirements: 3.4, 3.5_

  - [ ]* 6.3 编写用户头像上传集成测试
    - 测试上传头像成功
    - 测试更新头像时删除旧头像
    - 测试删除头像
    - _Requirements: 13.2_

  - [ ]* 6.4 编写属性测试：头像更新一致性
    - **Property 4: 头像更新一致性**
    - **Validates: Requirements 3.5**
    - 验证任何用户头像更新操作，旧头像文件必须被删除

- [x] 7. 实现文章图片上传功能
  - [x] 7.1 创建 PostImageController
    - 在 ZhiCore-post 中创建 `com.zhicore.post.controller.PostImageController`
    - 实现 POST /api/post/image/cover 接口（上传封面图）
    - 实现 POST /api/post/image/content 接口（上传内容图片）
    - 注入 FileUploadService
    - 内容图片接口返回 Markdown 格式的图片链接
    - _Requirements: 4.1, 4.2, 5.1, 5.2_

  - [x] 7.2 在 PostService 中添加文章删除时的文件清理逻辑
    - 在删除文章时，删除关联的封面图
    - 在删除文章时，解析内容中的图片 URL 并删除
    - 在更新封面图时，删除旧的封面图
    - _Requirements: 4.3, 4.4, 5.3_

  - [ ]* 7.3 编写文章图片上传集成测试
    - 测试上传封面图成功
    - 测试上传内容图片成功并返回 Markdown 格式
    - 测试文章删除时清理文件
    - _Requirements: 13.2_

  - [ ]* 7.4 编写属性测试：文章删除时清理文件
    - **Property 5: 文章删除时清理文件**
    - **Validates: Requirements 4.3, 5.3**
    - 验证任何文章删除操作，关联的封面图和内容图片必须被删除

- [ ] 8. 实现认证集成
  - [ ] 8.1 创建自定义 TokenProvider（可选）
    - 在 ZhiCore-common 中创建 `com.zhicore.common.config.FileServiceConfig`
    - 实现自定义 TokenProvider Bean，从 Spring Security Context 获取 JWT 令牌
    - _Requirements: 8.1, 8.2_

  - [ ]* 8.2 编写认证集成测试
    - 测试已登录用户自动获取令牌
    - 测试未登录用户使用静态令牌
    - 测试令牌过期时抛出异常
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 13.2_

  - [ ]* 8.3 编写属性测试：认证令牌自动获取
    - **Property 7: 认证令牌自动获取**
    - **Validates: Requirements 8.1**
    - 验证任何已登录用户的文件操作，File Service Client 必须自动从 Security Context 获取 JWT 令牌

- [ ] 9. 实现访问控制
  - [ ]* 9.1 编写属性测试：访问级别正确性
    - **Property 6: 访问级别正确性**
    - **Validates: Requirements 6.1**
    - 验证任何上传的用户头像和文章图片，访问级别必须设置为 PUBLIC

- [-] 10. 实现大文件上传支持
  - [x] 10.1 在 FileUploadService 中添加大文件上传逻辑
    - 检测文件大小，如果大于 10MB 则使用分片上传
    - 使用 FileServiceClient 的分片上传 API
    - 实现上传失败时的自动取消逻辑
    - _Requirements: 10.1, 10.2, 10.3_

  - [ ]* 10.2 编写大文件上传集成测试
    - 测试上传大于 10MB 的文件使用分片上传
    - 测试分片上传失败时自动取消
    - _Requirements: 10.1, 10.2, 13.2_

  - [ ]* 10.3 编写属性测试：大文件分片上传
    - **Property 9: 大文件分片上传**
    - **Validates: Requirements 10.1**
    - 验证任何大于 10MB 的文件，系统必须使用分片上传方式

- [x] 11. 实现秒传优化
  - [x] 11.1 在 FileUploadService 中添加秒传逻辑
    - 在上传前计算文件哈希值
    - 调用 FileServiceClient 的秒传检查 API
    - 如果文件已存在，直接返回文件信息
    - 记录秒传日志
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [ ]* 11.2 编写秒传功能集成测试
    - 测试上传相同文件时触发秒传
    - 测试秒传成功返回文件信息
    - _Requirements: 11.2, 11.3, 13.2_

  - [ ]* 11.3 编写属性测试：秒传哈希一致性
    - **Property 10: 秒传哈希一致性**
    - **Validates: Requirements 11.1**
    - 验证任何文件，计算的哈希值必须在多次计算中保持一致

- [ ] 12. 配置连接到独立部署的 File Service
  - [ ] 12.1 配置 File Service 连接信息
    - 在 Nacos 配置中心更新 file-service-config.yml
    - 配置 FILE_SERVICE_URL 指向独立部署的 File Service
    - 配置租户 ID 和其他连接参数
    - _Requirements: 12.1, 12.3_

  - [ ] 12.2 配置跨 Docker 网络访问
    - 在 ZhiCore 服务的 docker-compose.yml 中配置 extra_hosts
    - 或配置使用宿主机 IP 访问 File Service
    - 确保 ZhiCore 服务能够访问独立部署的 File Service
    - _Requirements: 12.1, 12.2_

  - [ ] 12.3 创建启动脚本
    - 创建 start-with-file-service.sh 脚本
    - 脚本中说明需要先启动 File Service
    - 然后启动 ZhiCore 服务
    - 添加健康检查等待逻辑
    - _Requirements: 12.1, 12.2, 12.4_

  - [ ]* 12.4 测试服务连接
    - 测试 ZhiCore 服务能否成功连接到 File Service
    - 验证文件上传功能正常工作
    - 测试服务间的网络连接
    - _Requirements: 12.1, 12.2, 12.3, 13.3_

- [ ] 13. 实现监控和日志
  - [ ] 13.1 配置日志
    - 在 logback-spring.xml 中添加 File Service 相关日志配置
    - 配置日志级别和输出格式
    - _Requirements: 14.1, 14.2_

  - [ ] 13.2 实现监控指标收集
    - 创建 FileServiceMetrics 组件
    - 使用 Micrometer 收集上传成功/失败计数
    - 收集上传耗时指标
    - _Requirements: 14.1, 14.2_

  - [ ] 13.3 实现健康检查
    - 创建 FileServiceHealthIndicator 组件
    - 实现 File Service 健康检查逻辑
    - _Requirements: 14.3_

- [ ] 14. 实现数据迁移（如果需要）
  - [ ] 14.1 创建 FileMigrationService
    - 实现 migrateUserAvatars 方法（迁移用户头像）
    - 实现 migratePostCoverImages 方法（迁移文章封面图）
    - 实现文件下载和上传逻辑
    - 记录迁移成功和失败的统计信息
    - _Requirements: 15.1, 15.2, 15.3, 15.4_

  - [ ] 14.2 创建迁移脚本
    - 创建 migrate-files.sh 脚本
    - 实现数据库备份逻辑
    - 调用迁移 API
    - 验证迁移结果
    - _Requirements: 15.1, 15.2, 15.3, 15.4_

  - [ ]* 14.3 测试数据迁移
    - 准备测试数据
    - 执行迁移脚本
    - 验证迁移结果
    - _Requirements: 15.3, 15.4, 13.3_

- [ ] 15. 编写端到端测试
  - [ ]* 15.1 编写用户头像上传 E2E 测试
    - 测试通过 API 上传头像的完整流程
    - 验证返回的 URL 可访问
    - _Requirements: 13.3_

  - [ ]* 15.2 编写文章图片上传 E2E 测试
    - 测试通过 API 上传封面图和内容图片的完整流程
    - 验证返回的 URL 和 Markdown 格式
    - _Requirements: 13.3_

  - [ ]* 15.3 编写并发上传测试
    - 测试多个用户同时上传文件
    - 验证文件上传的正确性
    - _Requirements: 13.4_

- [ ] 16. 编写属性测试
  - [ ]* 16.1 编写属性测试：文件上传成功后返回有效 URL
    - **Property 1: 文件上传成功后返回有效 URL**
    - **Validates: Requirements 3.1, 4.1, 5.1**
    - 验证任何成功上传的文件，返回的 URL 必须是有效的且可访问的

  - [ ]* 16.2 编写属性测试：文件类型验证
    - **Property 2: 文件类型验证**
    - **Validates: Requirements 3.2**
    - 验证任何上传的文件，如果不是允许的图片格式，系统必须拒绝上传并返回错误信息

  - [ ]* 16.3 编写属性测试：文件大小限制
    - **Property 3: 文件大小限制**
    - **Validates: Requirements 3.3**
    - 验证任何上传的文件，如果超过大小限制，系统必须拒绝上传并返回错误信息

  - [ ]* 16.4 编写属性测试：错误异常明确性
    - **Property 8: 错误异常明确性**
    - **Validates: Requirements 9.1, 9.2, 9.4**
    - 验证任何文件操作失败，系统必须抛出包含详细信息的异常并记录日志

- [ ] 17. 更新文档
  - [ ] 17.1 更新 API 文档
    - 创建 docs/api/file-upload-api.md
    - 说明文件上传相关接口的请求参数、响应格式和错误码
    - 提供示例请求和响应
    - _Requirements: 16.1_

  - [ ] 17.2 更新部署文档
    - 更新 docs/deployment/deployment-architecture.md
    - 说明 File Service 的部署步骤和配置
    - 添加 docker-compose 使用说明
    - _Requirements: 16.2_

  - [ ] 17.3 更新配置文档
    - 创建 docs/configuration/file-service-config.md
    - 列出所有 File Service 相关配置项
    - 说明各配置项的作用和默认值
    - 提供不同环境的配置示例
    - _Requirements: 16.3_

  - [ ] 17.4 创建快速开始指南
    - 创建 docs/quickstart/file-upload-guide.md
    - 说明如何使用文件上传功能
    - 提供代码示例和最佳实践
    - 说明常见问题和解决方案
    - _Requirements: 16.4_

- [ ] 18. 最终验证和部署
  - 确保所有测试通过
  - 验证所有功能正常工作
  - 检查日志和监控指标
  - 准备生产环境部署

## Notes

- 任务标记 `*` 的为可选任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求，便于追溯
- 在关键节点设置了检查点，确保增量验证
- 属性测试验证通用正确性属性
- 单元测试验证具体示例和边界情况
- 集成测试和端到端测试验证完整流程
