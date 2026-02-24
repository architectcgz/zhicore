# Changelog

本文档记录 zhicore-content 模块的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [Unreleased]

### 架构改进 - 2026-02-23

#### Fixed

**统一领域 ID 类型使用规范**（2026-02-23）
- 修复应用层中领域 ID 类型（PostId、UserId、TagId、TopicId）与基础类型（Long、String）混用的问题
- 为所有领域 ID 类添加详细的文档注释，说明 getValue() 和 toString() 的使用场景
- 更新 PostRedisKeys 工具类，添加接受领域类型参数的方法重载
- 更新所有命令处理器的缓存失效方法，使用领域类型作为参数
- 标记旧的 String 参数方法为 @Deprecated，保持向后兼容性

**修改的文件**：
- 领域 ID 类：PostId、UserId、TagId、TopicId - 添加详细的 getValue() 和 toString() 方法注释
- PostRedisKeys - 添加领域类型参数的方法重载，标记 String 参数方法为 @Deprecated
- DeletePostHandler - invalidateAllCache 方法使用 PostId 参数
- RestorePostHandler - invalidateAllCache 方法使用 PostId 参数
- PublishPostHandler - invalidateCache 方法使用 PostId 参数
- UnpublishPostHandler - invalidateCache 方法使用 PostId 和 Post 参数
- UpdatePostTagsHandler - invalidateCache 方法使用 PostId 和 TagId 参数
- UpdatePostMetaHandler - invalidateCache 方法使用 PostId 参数
- UpdatePostContentHandler - invalidateCache 方法使用 PostId 参数
- PurgePostHandler - clearAllCache 方法使用 PostId 参数

**技术细节**：
- **类型安全**：在应用层使用领域类型，编译期检查避免传递错误的 ID 类型
- **语义清晰**：明确表示这是文章 ID、用户 ID 还是标签 ID，而不是普通字符串或数字
- **一致性**：所有命令处理器遵循统一的类型规范
- **封装性**：PostRedisKeys 内部负责调用 getValue() 构建缓存键，应用层无需关心
- **向后兼容**：保留 @Deprecated 标记的旧方法，支持渐进式迁移
- **文档完善**：为 getValue() 添加数据库操作场景说明，为 toString() 添加缓存键和日志场景说明

**架构优势**：
- 应用层保持领域类型，仅在基础设施层转换为基础类型
- 符合 DDD 分层架构原则
- 提高代码可读性和可维护性
- 减少类型转换错误

### 代码修复 - 2026-02-23

#### Fixed

**统一命令处理器使用领域类型参数**（2026-02-23）
- 更新所有命令处理器的缓存失效方法，使用领域类型（PostId、UserId、TagId）作为参数
- 在 PostRedisKeys 中添加缺失的 `content(PostId)` 方法
- 消除不必要的 `.getValue()` 调用和类型转换
- 提高类型安全性和代码一致性

**修改的文件**：
- PublishPostHandler - invalidateCache 方法使用 PostId 参数
- UnpublishPostHandler - invalidateCache 方法使用 PostId 参数
- UpdatePostTagsHandler - invalidateCache 方法使用 PostId 和 TagId 参数
- UpdatePostMetaHandler - invalidateCache 方法使用 PostId 参数
- UpdatePostContentHandler - invalidateCache 方法使用 PostId 参数
- PurgePostHandler - clearAllCache 方法使用 PostId 参数
- PostRedisKeys - 添加 content(PostId) 和 content(String) 方法

**技术细节**：
- 所有缓存失效方法现在接受领域类型参数，保持领域模型的完整性
- 在应用层方法中直接传递领域对象，避免不必要的类型转换
- PostRedisKeys 内部负责调用 `.getValue()` 构建缓存键
- 添加详细的方法注释说明参数类型选择的原因
- 所有更新的文件编译通过，无类型错误

**创建领域特定的文章所有权异常**（2026-02-23）
- 创建 `PostOwnershipException` 领域异常类
- 替换所有 Handler 中错误使用的 `UnauthorizedException`
- 使用正确的 HTTP 状态码 403 Forbidden（而非 401 Unauthorized）
- 提供更清晰的错误信息（中文）
- 在 GlobalExceptionHandler 中添加专门的异常处理

**技术细节**：
- **认证 vs 授权**：401 用于身份验证失败，403 用于权限不足
- **领域特定异常**：`PostOwnershipException` 明确表示"不是文章所有者"这个业务规则
- **符合 DDD**：领域异常定义在领域层，提供更好的语义
- **统一处理**：所有文章所有权验证都使用同一个异常

**修改的文件**：
- DeletePostHandler
- PurgePostHandler
- UpdatePostMetaHandler
- UpdatePostTagsHandler
- UpdatePostContentHandler
- RestorePostHandler
- SchedulePublishHandler

**修复工作流中的充血模型方法调用**（2026-02-23）
- 修复 CreatePostWorkflow 中错误调用不存在的 `setIncompleteReason` 方法
- 修复 CreateDraftWorkflow 中错误调用不存在的 `setIncompleteReason` 方法
- 使用正确的充血模型方法 `markAsIncomplete(reason)` 替代
- 移除重复的 `setWriteState` 调用（`markAsIncomplete` 内部已处理）
- 保持领域模型的封装性和一致性

**技术细节**：
- Post 实体采用充血模型设计，提供 `markAsIncomplete(String reason)` 方法
- 该方法封装了完整的业务逻辑：设置 writeState、incompleteReason 和 updatedAt
- 避免使用多个 setter 方法，保证状态一致性

### 架构迁移 - 2026-02-22

#### 已完成 ✅

**Repository 目录完全迁移**（2026-02-23）
- 迁移 PostTagMapper → PostTagEntityMyBatisMapper
- 迁移 PostTagPO → PostTagEntity
- 迁移 TagStatsMapper → TagStatsEntityMyBatisMapper
- 更新 PostRepositoryPgImpl 使用新的 Mapper
- 更新 TagStatsEventHandler 使用新的 Mapper
- 更新测试文件引用
- 删除 infrastructure/repository 目录及所有旧代码
- 移动 XML 映射文件到 Java 源码目录（与 Mapper 接口同目录）
- 删除 resources/mapper 目录及所有旧 XML 文件
- 完成向新架构的完全迁移

**DualStorageManager 移除**（2026-02-22）
- 实现 UpdatePostContentHandler（更新文章内容）
- 实现 PurgePostHandler（物理删除文章）
- 重构 PostApplicationService 使用新架构
- 删除 DualStorageManager、DualStorageManagerImpl、CachedDualStorageManager
- 完成向新架构的迁移

**新架构实现**
- 实现 Tag 新架构（TagRepositoryPgImpl, CacheAsideTagQuery）
- 实现 PostTag 新架构（PostTagRepositoryPgImpl）
- 实现 Category 新架构（CategoryRepositoryPgImpl）
- 缓存装饰器从 infrastructure 层迁移到 application/decorator 层
- 查询服务从 infrastructure 层迁移到 application/query 层

**旧代码清理**
- 删除 infrastructure/repository 目录（所有 Mapper 和 PO 类）
- 删除 resources/mapper 目录（所有旧 XML 映射文件）
- 删除 CachedPostRepository（旧缓存装饰器）
- 删除 CachedTagRepository（旧缓存装饰器）
- 删除 CachedPostTagRepository（旧缓存装饰器）
- 删除 PostRepositoryImpl（旧 Repository 实现）
- 删除 TagRepositoryImpl（旧 Repository 实现）
- 删除 PostTagRepositoryImpl（旧 Repository 实现）
- 删除 CategoryRepositoryImpl（旧 Repository 实现）
- 删除 PostLikeRepositoryImpl（旧 Repository 实现）
- 删除 PostFavoriteRepositoryImpl（旧 Repository 实现）
- 删除 ArchiveManagerImpl（旧归档管理器）
- 删除 ConsistencyCheckerImpl（旧一致性检查器）
- 删除 QueryOptimizationServiceImpl（旧查询优化服务）
- 删除 VersionManagerImpl（旧版本管理器）
- 删除 ArchiveManager 接口
- 删除 ConsistencyChecker 接口
- 删除 QueryOptimizationService 接口
- 删除 VersionManager 接口
- 删除 PostArchiveRepository（MongoDB 归档仓库）
- 删除 PostVersionRepository（MongoDB 版本仓库）
- 删除 PostArchive 文档类
- 删除 PostVersion 文档类

#### 架构变更说明

**旧架构 → 新架构**

| 组件类型 | 旧位置 | 新位置 |
|---------|--------|--------|
| Repository 实现 | `infrastructure/repository/` | `infrastructure/persistence/pg/` |
| 缓存装饰器 | `infrastructure/repository/Cached*` | `application/decorator/CacheAside*` |
| 查询服务 | 无独立层 | `application/query/*QueryServiceImpl` |

**新架构优势**

1. **职责分离更清晰**
   - Repository 只负责数据持久化
   - 缓存逻辑在 Application 层统一管理
   - 查询和命令分离（CQRS）

2. **缓存策略统一**
   - 所有缓存装饰器使用 Cache-Aside 模式
   - 统一的热点检测、分布式锁、降级处理
   - 防止缓存穿透、击穿、雪崩

3. **更易维护和测试**
   - 层次结构清晰
   - 依赖关系简单
   - 测试更容易编写

#### 对开发者的影响

**如果你正在开发新功能**
- ✅ 参考 Post/Tag 的新架构实现
- ✅ Repository 实现放在 `infrastructure/persistence/pg/`
- ✅ 查询服务放在 `application/query/`
- ✅ 缓存装饰器放在 `application/decorator/`
- ❌ 不要再使用旧的 `infrastructure/repository/` 目录

**如果你的代码引用了旧组件**
- 旧的 Repository 实现已删除
- 请更新为使用新的 Repository 实现
- 查询操作请使用 Query Service + Cache Decorator

**迁移示例**

```java
// 旧代码
@Autowired
private TagRepository tagRepository;  // 注入的是 CachedTagRepository

Tag tag = tagRepository.findById(tagId);

// 新代码
@Autowired
private TagQuery tagQuery;  // 注入的是 CacheAsideTagQuery

Optional<TagDetailDTO> tag = tagQuery.getDetail(tagId);
```

**DualStorageManager 移除** ✅
- 实现 UpdatePostContentHandler（更新文章内容）
- 实现 PurgePostHandler（物理删除文章）
- 重构 PostApplicationService 使用新架构
- 删除 DualStorageManager、DualStorageManagerImpl、CachedDualStorageManager
- 完成向新架构的迁移

#### 相关文档

- [新架构文档](./docs/NEW-ARCHITECTURE.md)
- [架构深度分析](./docs/ARCHITECTURE-DEEP-DIVE.md)

---

## [1.0.0] - 2026-02-01

### Added
- 初始版本发布
- 实现文章管理核心功能
- 实现标签管理功能
- 实现分类管理功能
- 实现 PostgreSQL + MongoDB 双存储架构

### Changed
- N/A

### Deprecated
- N/A

### Removed
- N/A

### Fixed
- N/A

### Security
- N/A

---

## 版本说明

### 版本号格式

`主版本号.次版本号.修订号`

- **主版本号**：不兼容的 API 变更
- **次版本号**：向下兼容的功能新增
- **修订号**：向下兼容的问题修正

### 变更类型

- **Added**: 新增功能
- **Changed**: 功能变更
- **Deprecated**: 即将废弃的功能
- **Removed**: 已删除的功能
- **Fixed**: 问题修复
- **Security**: 安全相关变更

---

**维护者**: ZhiCore 开发团队  
**最后更新**: 2026-02-23
