# Requirements Document

## Introduction

本规格文档定义了博客文章服务（ZhiCore-post）的混合存储架构改造需求。通过引入 MongoDB 作为内容存储层，配合现有的 PostgreSQL 元数据存储，实现更灵活、高效的文章内容管理，并支持版本历史、草稿自动保存、富文本扩展等高级功能。

## Glossary

- **Post_Service**: 博客文章服务，负责文章的创建、编辑、发布、查询等功能
- **PostgreSQL**: 关系型数据库，存储文章元数据、关系数据、统计数据
- **MongoDB**: 文档型数据库，存储文章内容、版本历史、草稿数据
- **Post_Metadata**: 文章元数据，包括 ID、标题、作者、状态、发布时间等结构化信息
- **Post_Content**: 文章内容，包括 Markdown 原文、HTML 渲染结果、纯文本等
- **Post_Version**: 文章版本，记录文章的每次编辑历史
- **Post_Draft**: 文章草稿，用户编辑过程中的临时保存数据
- **Dual_Write**: 双写机制，同时写入 PostgreSQL 和 MongoDB 以保证数据一致性
- **Content_Archive**: 内容归档，将不活跃的文章内容迁移到 MongoDB 以减轻 PostgreSQL 压力
- **Hot_Data**: 热数据，最近活跃的文章数据，存储在 PostgreSQL
- **Cold_Data**: 冷数据，不活跃的历史文章数据，存储在 MongoDB
- **Auto_Save**: 自动保存，编辑器定期自动保存草稿的机制
- **Version_Control**: 版本控制，追踪和管理文章的编辑历史
- **Rich_Content**: 富文本内容，支持文本、图片、视频、代码块等多种内容类型
- **Content_Block**: 内容块，富文本内容的基本组成单元
- **Consistency_Check**: 一致性检查，验证 PostgreSQL 和 MongoDB 数据一致性的机制

## Requirements

### Requirement 1: MongoDB 基础设施部署

**User Story:** 作为系统管理员，我想要部署 MongoDB 服务，以便为文章服务提供文档存储能力。

#### Acceptance Criteria

1. WHEN 启动 Docker Compose THEN THE System SHALL 启动 MongoDB 容器并监听 27017 端口
2. WHEN MongoDB 启动完成 THEN THE System SHALL 创建 ZhiCore 数据库和必要的集合
3. WHEN MongoDB 初始化 THEN THE System SHALL 创建必要的索引以优化查询性能
4. WHEN 应用启动 THEN THE Post_Service SHALL 成功连接到 MongoDB 并验证连接状态
5. WHERE 部署了 Mongo Express THEN THE System SHALL 提供 Web 管理界面访问 MongoDB

### Requirement 2: 文章内容存储迁移

**User Story:** 作为开发者，我想要将文章内容从 PostgreSQL 迁移到 MongoDB，以便提升大文本存储和查询性能。

#### Acceptance Criteria

1. WHEN 创建新文章 THEN THE Post_Service SHALL 将元数据存储到 PostgreSQL 并将内容存储到 MongoDB
2. WHEN 查询文章详情 THEN THE Post_Service SHALL 从 PostgreSQL 获取元数据并从 MongoDB 获取内容
3. WHEN 更新文章内容 THEN THE Post_Service SHALL 同时更新 PostgreSQL 的更新时间和 MongoDB 的内容
4. WHEN 删除文章 THEN THE Post_Service SHALL 同时删除 PostgreSQL 和 MongoDB 中的数据
5. WHEN 数据写入失败 THEN THE Post_Service SHALL 回滚已写入的数据以保证一致性
6. WHEN 查询文章列表 THEN THE Post_Service SHALL 仅从 PostgreSQL 查询元数据而不加载内容

### Requirement 3: 文章版本历史管理

**User Story:** 作为内容创作者，我想要查看文章的编辑历史，以便追踪内容变化和恢复到历史版本。

#### Acceptance Criteria

1. WHEN 用户更新文章内容 THEN THE Post_Service SHALL 在 MongoDB 中创建新的版本记录
2. WHEN 用户查询版本历史 THEN THE Post_Service SHALL 返回按时间倒序排列的版本列表
3. WHEN 用户查看特定版本 THEN THE Post_Service SHALL 返回该版本的完整内容快照
4. WHEN 用户恢复到历史版本 THEN THE Post_Service SHALL 将该版本内容设置为当前内容并创建新版本
5. WHEN 版本数量超过限制 THEN THE Post_Service SHALL 自动清理最旧的版本记录
6. WHEN 查询版本详情 THEN THE Post_Service SHALL 返回版本号、编辑者、编辑时间、变更说明等信息

### Requirement 4: 草稿自动保存

**User Story:** 作为内容创作者，我想要编辑器自动保存草稿，以便在意外情况下不丢失编辑内容。

#### Acceptance Criteria

1. WHEN 用户编辑文章内容 THEN THE Post_Service SHALL 每 30 秒自动保存草稿到 MongoDB
2. WHEN 用户打开文章编辑器 THEN THE Post_Service SHALL 检查是否存在未发布的草稿
3. IF 存在草稿且草稿时间晚于文章更新时间 THEN THE Post_Service SHALL 提示用户恢复草稿
4. WHEN 用户发布文章 THEN THE Post_Service SHALL 删除对应的草稿记录
5. WHEN 草稿保存失败 THEN THE Post_Service SHALL 在前端显示警告但不中断编辑
6. WHEN 查询用户草稿列表 THEN THE Post_Service SHALL 返回所有未发布的草稿及其保存时间

### Requirement 5: 富文本内容扩展

**User Story:** 作为内容创作者，我想要在文章中嵌入多种类型的内容，以便创作更丰富的文章。

#### Acceptance Criteria

1. WHEN 用户创建文章 THEN THE Post_Service SHALL 支持存储 Markdown、HTML、富文本三种内容格式
2. WHEN 文章包含内容块 THEN THE Post_Service SHALL 支持文本、图片、视频、代码、图表等块类型
3. WHEN 保存富文本内容 THEN THE Post_Service SHALL 将内容块列表存储为 JSON 格式到 MongoDB
4. WHEN 查询富文本内容 THEN THE Post_Service SHALL 返回完整的内容块结构和元数据
5. WHEN 内容包含媒体资源 THEN THE Post_Service SHALL 存储媒体 URL、缩略图、大小等信息
6. WHEN 渲染文章 THEN THE Post_Service SHALL 根据内容类型返回相应的渲染数据

### Requirement 6: 内容归档和冷热分离

**User Story:** 作为系统管理员，我想要自动归档不活跃的文章内容，以便降低 PostgreSQL 的存储压力。

#### Acceptance Criteria

1. WHEN 文章超过 6 个月未更新 THEN THE Post_Service SHALL 将其标记为冷数据候选
2. WHEN 执行归档任务 THEN THE Post_Service SHALL 将冷数据的完整快照存储到 MongoDB
3. WHEN 归档完成 THEN THE Post_Service SHALL 从 PostgreSQL 中删除内容字段但保留元数据
4. WHEN 查询已归档文章 THEN THE Post_Service SHALL 从 MongoDB 加载内容并返回完整数据
5. WHEN 更新已归档文章 THEN THE Post_Service SHALL 将其恢复为热数据并更新内容
6. WHEN 归档失败 THEN THE Post_Service SHALL 记录错误日志并保持原数据不变

### Requirement 7: 数据一致性保证

**User Story:** 作为开发者，我想要确保 PostgreSQL 和 MongoDB 的数据一致性，以便系统数据的准确性和可靠性。

#### Acceptance Criteria

1. WHEN 执行写操作 THEN THE Post_Service SHALL 使用事务机制保证 PostgreSQL 和 MongoDB 的原子性
2. IF PostgreSQL 写入成功但 MongoDB 写入失败 THEN THE Post_Service SHALL 回滚 PostgreSQL 的更改
3. IF MongoDB 写入成功但 PostgreSQL 写入失败 THEN THE Post_Service SHALL 删除 MongoDB 的数据
4. WHEN 检测到数据不一致 THEN THE Post_Service SHALL 记录错误日志并触发告警
5. WHEN 执行一致性检查 THEN THE Post_Service SHALL 比对 PostgreSQL 和 MongoDB 的数据并生成报告
6. WHEN 发现数据不一致 THEN THE Post_Service SHALL 提供修复工具以 PostgreSQL 为准同步数据

### Requirement 8: 性能优化

**User Story:** 作为系统管理员，我想要优化查询性能，以便提升用户体验和降低服务器负载。

#### Acceptance Criteria

1. WHEN 查询文章列表 THEN THE Post_Service SHALL 仅从 PostgreSQL 查询元数据响应时间小于 100ms
2. WHEN 查询文章详情 THEN THE Post_Service SHALL 并行查询 PostgreSQL 和 MongoDB 响应时间小于 200ms
3. WHEN 频繁访问的内容 THEN THE Post_Service SHALL 使用 Redis 缓存减少数据库查询
4. WHEN MongoDB 查询 THEN THE Post_Service SHALL 使用索引优化查询性能
5. WHEN 批量操作 THEN THE Post_Service SHALL 使用批量写入减少网络往返次数
6. WHEN 系统负载高 THEN THE Post_Service SHALL 降级为仅查询 PostgreSQL 元数据

### Requirement 9: 数据迁移工具

**User Story:** 作为系统管理员，我想要使用迁移工具将现有文章数据迁移到新架构，以便平滑过渡到混合存储架构。

#### Acceptance Criteria

1. WHEN 执行迁移命令 THEN THE Migration_Tool SHALL 读取 PostgreSQL 中的文章内容并写入 MongoDB
2. WHEN 迁移单篇文章 THEN THE Migration_Tool SHALL 保持文章 ID 不变并验证数据完整性
3. WHEN 迁移过程中出错 THEN THE Migration_Tool SHALL 记录失败的文章 ID 并继续处理其他文章
4. WHEN 迁移完成 THEN THE Migration_Tool SHALL 生成迁移报告包括成功数、失败数、耗时等
5. WHEN 验证迁移结果 THEN THE Migration_Tool SHALL 比对 PostgreSQL 和 MongoDB 的数据一致性
6. WHEN 迁移验证通过 THEN THE Migration_Tool SHALL 提供清理 PostgreSQL 内容字段的选项

### Requirement 10: 监控和告警

**User Story:** 作为系统管理员，我想要监控混合存储架构的运行状态，以便及时发现和处理问题。

#### Acceptance Criteria

1. WHEN 系统运行 THEN THE Post_Service SHALL 记录 PostgreSQL 和 MongoDB 的查询响应时间
2. WHEN 数据写入 THEN THE Post_Service SHALL 记录双写成功率和失败率
3. WHEN 数据不一致 THEN THE Post_Service SHALL 触发告警通知管理员
4. WHEN MongoDB 连接失败 THEN THE Post_Service SHALL 降级为仅使用 PostgreSQL 并记录告警
5. WHEN 查询性能下降 THEN THE Post_Service SHALL 记录慢查询日志并分析原因
6. WHEN 存储空间不足 THEN THE Post_Service SHALL 触发告警并建议清理或扩容

### Requirement 11: API 兼容性

**User Story:** 作为前端开发者，我想要 API 接口保持兼容，以便无需修改前端代码即可使用新架构。

#### Acceptance Criteria

1. WHEN 调用列表 API THEN THE Post_Service SHALL 仅返回元数据（不含内容）
2. WHEN 需要完整内容 THEN THE Post_Service SHALL 提供专门的详情接口聚合数据
3. WHEN 前端展示 THEN THE Post_Service SHALL 支持按需延迟加载文章内容
4. WHEN 使用新架构 THEN THE Post_Service SHALL 不强制保持旧 API 的完全兼容性
5. WHEN API 响应 THEN THE Post_Service SHALL 保持响应时间在可接受范围内
6. WHEN 错误发生 THEN THE Post_Service SHALL 返回统一的错误码

### Requirement 12: 配置管理

**User Story:** 作为系统管理员，我想要灵活配置混合存储架构的参数，以便根据实际情况调整系统行为。

#### Acceptance Criteria

1. WHEN 配置 MongoDB 连接 THEN THE Post_Service SHALL 支持配置主机、端口、数据库、认证信息
2. WHEN 配置归档策略 THEN THE Post_Service SHALL 支持配置归档时间阈值和归档任务执行频率
3. WHEN 配置版本控制 THEN THE Post_Service SHALL 支持配置最大版本数和版本清理策略
4. WHEN 配置自动保存 THEN THE Post_Service SHALL 支持配置保存间隔和草稿过期时间
5. WHEN 配置一致性检查 THEN THE Post_Service SHALL 支持配置检查频率和修复策略
6. WHEN 配置降级策略 THEN THE Post_Service SHALL 支持配置降级条件和降级行为
