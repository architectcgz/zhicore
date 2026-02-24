# 模块设计：创建 Post（草稿）（接手版）

## 1. 模块目标
创建 Post 模块负责把“前端输入的文章草稿”安全落地到系统，保证：
- 文章 ID 全局唯一
- 元数据（PG）和正文（Mongo）完成首次写入
- 初始标签、作者快照、话题等信息正确挂载
- 后续系统可通过事件感知“新文章已创建”

## 2. 请求入口与核心链路
- 接口：`POST /api/v1/posts`
- 入口：`PostController#createPost`
- 编排：`PostFacadeService#createPost`
- 业务主类：`PostApplicationService#createPost`
- 工作流：`CreateDraftWorkflow#execute`

调用链：
`Controller -> Facade -> ApplicationService -> Workflow -> Repository/Store -> EventPublisher`

## 3. 输入/输出契约
### 3.1 输入（CreatePostRequest）
关键字段：
- `title`
- `content`
- `coverImageId`（可选）
- `tags`（可选，最大 10）
- `topicId`（可选）

### 3.2 输出
- 成功返回 `postId`（Long）

## 4. 详细流程（逐步）
1. 生成 `postId`
   - 调用 `idGeneratorFeignClient.generateSnowflakeId()`。
   - 失败直接抛业务异常。
2. 参数与业务校验
   - 封面 `fileId` 格式校验。
   - 标签数量限制（<=10）。
3. 标签处理
   - `tagDomainService.findOrCreateBatch()`：不存在标签自动创建。
4. 作者快照处理
   - 调用 `user-service` 获取用户昵称/头像/profileVersion。
   - 远程失败时降级 `OwnerSnapshot.createDefault`，不中断创建。
5. 构造命令对象
   - `CreatePostCommand` 聚合所有入参与上下文。
6. 工作流执行（`CreateDraftWorkflow`）
   - `Post.createDraft` 创建聚合根
   - 持久化 `posts`（PG）
   - 持久化正文 `contentStore.saveContent`（Mongo）
   - 更新写入状态 `WriteState`
   - 聚合产出 `PostCreatedDomainEvent`
7. 事件发布
   - 批量发布领域事件（进程内）
   - 将 `PostCreatedDomainEvent` 转换为 `PostCreatedIntegrationEvent` 并写 Outbox

## 5. 数据落地细节
### 5.1 PostgreSQL
- `posts`：
  - `id`, `owner_id`, `owner_name`, `owner_avatar_id`, `owner_profile_version`
  - `title`, `cover_image_id`, `status`, `write_state`, `topic_id`

### 5.2 MongoDB
- 正文字段：`postId`, `contentType`, `raw`, `createdAt`, `updatedAt`

### 5.3 事件
- 领域事件：聚合内部状态变更通知
- 集成事件：Outbox 供下游服务消费

## 6. 一致性与事务
- `PostApplicationService#createPost` 使用事务。
- `CreateDraftWorkflow#execute` 使用 `Propagation.MANDATORY`，确保在外层事务中执行。
- PG 与 Mongo 不在同一数据库事务中：
  - 出现异常时执行 `contentStore.deleteContent(postId)` best-effort 清理。

## 7. 异常分支
### 7.1 ID 服务不可用
- 直接失败，返回“生成文章ID失败”。

### 7.2 用户信息获取失败
- 降级默认作者快照，不阻断创建。

### 7.3 Mongo 写入失败
- 抛 `CreatePostFailedException`
- 触发事务回滚并尝试删除可能的残留内容。

### 7.4 标签创建失败
- 创建整体失败回滚，防止半成功。

## 8. 监控与排障
### 8.1 关键日志
- `Draft created: postId=...`
- `Published integration event to Outbox`

### 8.2 快速排查顺序
1. 查 `posts` 是否存在该 `postId`
2. 查 Mongo 是否有对应正文
3. 查 `outbox_event` 是否有 `PostCreated` 记录
4. 查远程调用（ID/User）失败日志

## 9. 易踩坑与改进建议
- 外部依赖较多（ID/User/Tag），建议设置统一超时和熔断策略。
- 建议增加“创建事务耗时”分位统计，识别远程依赖拖慢路径。
- 建议补“脏数据巡检”：PG 有 post 但 Mongo 无 content 的自动修复任务。

## 10. 代码锚点
- `src/main/java/com/zhicore/content/interfaces/controller/PostController.java`
- `src/main/java/com/zhicore/content/application/service/PostApplicationService.java`
- `src/main/java/com/zhicore/content/application/workflow/CreateDraftWorkflow.java`
