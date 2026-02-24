# 模块设计：更新 Post（接手版）

## 1. 模块目标
支持作者对已有文章进行内容与元数据更新，并保障：
- 权限正确（仅作者可改）。
- 删除态不可编辑。
- 内容与元数据更新后缓存失效。
- 已发布文章变更可对外传播（Outbox）。

## 2. 入口与链路
- 接口：`PUT /api/v1/posts/{postId}`
- 入口：`PostController#updatePost`
- 编排：`PostFacadeService#updatePost`
- 核心：`PostApplicationService#updatePost`
- 命令处理器：
  - `UpdatePostMetaHandler`
  - `UpdatePostContentHandler`

调用链：
`Controller -> Facade -> PostApplicationService -> Handler(meta/content) -> PG + Mongo + Cache + Event`

## 3. 更新内容
- 元数据：标题、摘要、封面、话题、标签关系。
- 正文：写入 Mongo 内容存储（`PostContentStore`）。
- 事件：已发布文章会写 `PostUpdatedIntegrationEvent` 到 Outbox。

## 4. 一致性与事务
- `PostApplicationService#updatePost` 使用事务。
- 元数据在 PG，内容在 Mongo，属于跨存储最终一致。
- Mongo 更新异常会中断更新流程并回滚 PG 事务（Mongo 非同库事务，存在极小窗口不一致风险）。

## 5. 当前实现风险点
1. 更新流程存在双路径写入（应用服务直接写 + Handler 再写），职责边界偏模糊。
2. 标签更新采用“全量删除再重建”，并发写下可能覆盖他人更新。
3. 已发布更新事件中 `aggregateVersion` 来源不稳定，版本语义不清晰。

## 6. 优化建议
1. 收敛为单一更新编排模式（建议统一通过命令处理器）。
2. 标签更新改为差量更新（attach/detach）+ 版本校验。
3. 明确更新事件版本字段，统一使用文章版本或事件版本。

## 7. 代码锚点
- `src/main/java/com/zhicore/content/interfaces/controller/PostController.java`
- `src/main/java/com/zhicore/content/application/service/PostApplicationService.java`
- `src/main/java/com/zhicore/content/application/command/handlers/UpdatePostMetaHandler.java`
- `src/main/java/com/zhicore/content/application/command/handlers/UpdatePostContentHandler.java`

