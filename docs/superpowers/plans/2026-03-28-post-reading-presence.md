# Post Reading Presence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为文章详情页增加“多少人正在读 + 最多 3 个登录用户头像”的在线态能力，匿名用户计数但不展示头像，头像不可点击，页面隐藏后不再计入正在读。

**Architecture:** 后端在 `zhicore-content` 内新增独立 presence 读写能力，使用 Redis `zset + session detail TTL` 保存短时阅读会话；前端在 `PostDetail` 页面新增 presence composable，通过 HTTP 注册、`25s` 续约、`3s` 查询和离开清理来驱动 UI。文章可见性沿用详情页公开访问规则，presence 不进入文章详情缓存，也不复用现有 `message/notification` WebSocket。

**Tech Stack:** Java, Spring Boot, Redis, JUnit 5, Mockito, MockMvc, Vue 3, TypeScript, Vitest, Vue Test Utils

---

### Task 1: 准备跨仓 worktree 和执行上下文

**Files:**
- Modify: `docs/superpowers/plans/2026-03-28-post-reading-presence.md`

- [ ] **Step 1: 为 `/home/azhi/workspace/projects/zhicore-microservice` 创建 backend worktree**
- [ ] **Step 2: 为 `/home/azhi/workspace/projects/zhicore-frontend-vue` 创建 frontend worktree**
- [ ] **Step 3: 记录两个 worktree 路径、对应分支名和执行顺序**
- [ ] **Step 4: 确认两个 worktree 都是干净状态后再进入实现**

### Task 2: 先写 backend presence 失败测试

**Files:**
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/test/java/com/zhicore/content/application/service/PostReaderPresenceAppServiceTest.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/test/java/com/zhicore/content/interfaces/controller/PostReaderPresenceControllerTest.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/test/java/com/zhicore/content/infrastructure/cache/DefaultPostReaderPresenceStoreIntegrationTest.java`

- [ ] **Step 1: 写 failing test，断言注册后返回可见文章的 presence 会话**
- [ ] **Step 2: 写 failing test，断言不可见文章不能注册或查询 presence**
- [ ] **Step 3: 写 failing test，断言匿名用户计数但不进入头像列表**
- [ ] **Step 4: 写 failing test，断言同一登录用户多页面实例只占一个头像位**
- [ ] **Step 5: 写 failing test，断言同一 session 重复注册不重复计数，过期 session 不计入 presence**
- [ ] **Step 6: 写 failing test，断言 `postId` 切换后旧文章不会继续统计旧 session**
- [ ] **Step 7: 写 failing test，断言 leave 会清理 session 明细和文章 zset membership**
- [ ] **Step 8: 写 failing test，断言 Redis/presence 故障不会破坏正文主链路**
- [ ] **Step 9: 运行 `mvn -pl zhicore-content -Dtest=PostReaderPresenceAppServiceTest,PostReaderPresenceControllerTest,DefaultPostReaderPresenceStoreIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败原因正确**

### Task 3: 实现 backend presence store、key 和 DTO 契约

**Files:**
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/application/port/store/PostReaderPresenceStore.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/application/dto/PostReaderPresenceSnapshot.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/application/dto/PostReaderPresenceView.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/interfaces/dto/request/PostReaderPresenceSessionRequest.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/interfaces/dto/request/PostReaderPresenceLeaveRequest.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/interfaces/dto/response/PostReaderPresenceResponse.java`
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/infrastructure/cache/PostRedisKeys.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/infrastructure/cache/DefaultPostReaderPresenceStore.java`

- [ ] **Step 1: 在 `PostRedisKeys` 中增加 presence session detail 与 article sessions zset key**
- [ ] **Step 2: 定义 application 层快照 / 视图对象，区分 Redis 落盘对象与查询返回对象**
- [ ] **Step 3: 实现 `PostReaderPresenceStore` 的 Redis 读写与过期清理**
- [ ] **Step 4: 在 store 内落实 `ZREMRANGEBYSCORE` 清理和错文 membership 补清理**
- [ ] **Step 5: 运行 Task 2 中的 store 相关测试，确认通过**

### Task 4: 实现 backend app service、文章可见性校验和接口

**Files:**
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/application/service/PostReaderPresenceAppService.java`
- Create: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/interfaces/controller/PostReaderPresenceController.java`
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/java/com/zhicore/content/application/service/PostQueryFacade.java`
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/zhicore-content/src/main/resources/application.yml`

- [ ] **Step 1: 在 `PostQueryFacade` 提炼或新增公开详情可见性判断辅助能力，供 presence 复用**
- [ ] **Step 2: 在 `PostReaderPresenceAppService` 中实现 register / leave / query**
- [ ] **Step 3: 落实匿名防刷最小策略与登录后 session 重建所需的身份处理**
- [ ] **Step 4: 在 controller 暴露 `POST /api/v1/posts/{postId}/readers/session`、`POST /api/v1/posts/{postId}/readers/session/leave`、`GET /api/v1/posts/{postId}/readers/presence`**
- [ ] **Step 5: 为 Redis 故障降级、隐藏页 leave、文章下架场景补齐返回与日志**
- [ ] **Step 6: 运行 Task 2 backend 测试，确认通过**

### Task 5: 先写 frontend presence 失败测试

**Files:**
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/test/composables/usePostReadingPresence.test.ts`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/test/components/post/detail/PostDetailReadingPresence.test.ts`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/test/pages/post/PostDetail.test.ts`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/test/api/post.test.ts`

- [ ] **Step 1: 写 failing test，断言 post API 能请求 register / leave / presence 接口**
- [ ] **Step 2: 写 failing test，断言 composable 在挂载时注册、按间隔续约和查询**
- [ ] **Step 3: 写 failing test，断言 `document.hidden = true` 时发送 leave 并停止计时器**
- [ ] **Step 4: 写 failing test，断言登录态变化时销毁匿名 session 并重建登录 session**
- [ ] **Step 5: 写 failing test，断言 `postId` 变化时旧 session 被 leave，新文章创建新 session**
- [ ] **Step 6: 写 failing test，断言 presence 查询失败时正文仍正常渲染**
- [ ] **Step 7: 写 failing test，断言 presence UI 只展示最多 3 个头像且头像不可点击**
- [ ] **Step 8: 运行 `pnpm vitest run test/api/post.test.ts test/composables/usePostReadingPresence.test.ts test/components/post/detail/PostDetailReadingPresence.test.ts test/pages/post/PostDetail.test.ts`，确认失败**

### Task 6: 实现 frontend API、presence composable 和详情页 UI

**Files:**
- Modify: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/api/post.ts`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/types/post/presence.ts`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/composables/usePostReadingPresence.ts`
- Modify: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/composables/usePostDetailContent.ts`
- Modify: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/pages/post/PostDetail.vue`
- Modify: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/components/post/detail/PostDetailHeader.vue`
- Modify: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/components/post/detail/PostDetailHeaderMeta.vue`
- Create: `/home/azhi/workspace/projects/zhicore-frontend-vue/src/components/post/detail/PostDetailReadingPresence.vue`

- [ ] **Step 1: 在 `postApi` 中增加 register / leave / query presence 方法和响应归一化**
- [ ] **Step 2: 创建 presence 类型定义，明确人数、头像数组和单头像字段**
- [ ] **Step 3: 在 `usePostReadingPresence` 中实现 page-view session、visibilitychange、sendBeacon 和登录重建**
- [ ] **Step 4: 将 presence 状态接入 `usePostDetailContent`，保持正文主链路与 presence 故障解耦**
- [ ] **Step 5: 新增 `PostDetailReadingPresence.vue` 并接到 header meta 区域**
- [ ] **Step 6: 确保头像使用普通元素渲染，不绑定路由或点击导航**
- [ ] **Step 7: 运行 Task 5 前端测试，确认通过**

### Task 7: 做最小充分联调验证

**Files:**
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/docs/superpowers/plans/2026-03-28-post-reading-presence.md`

- [ ] **Step 1: 运行 backend 定向测试**
- [ ] **Step 2: 运行 frontend 定向测试**
- [ ] **Step 3: 若存在一侧失败，先修复后重跑，不并发叠加重任务**
- [ ] **Step 4: 人工检查 `postId` 切换、隐藏页 leave 和文章不可见场景的收敛行为**
- [ ] **Step 5: 记录通过项、失败项、未覆盖风险和降级行为**

**Run:**

```bash
cd /home/azhi/workspace/projects/zhicore-microservice
mvn -pl zhicore-content -Dtest=PostReaderPresenceAppServiceTest,PostReaderPresenceControllerTest,DefaultPostReaderPresenceStoreIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test

cd /home/azhi/workspace/projects/zhicore-frontend-vue
pnpm vitest run test/api/post.test.ts test/composables/usePostReadingPresence.test.ts test/components/post/detail/PostDetailReadingPresence.test.ts test/pages/post/PostDetail.test.ts
```

Expected:
- backend presence 测试通过，文章不可见、匿名、防刷、leave 与清理逻辑满足 spec
- frontend presence 测试通过，header 区域正确展示，隐藏页、`postId` 切换、登录切换与降级行为正确

### Task 8: 收尾 review 和文档对齐

**Files:**
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/docs/superpowers/plans/2026-03-28-post-reading-presence.md`
- Modify: `/home/azhi/workspace/projects/zhicore-microservice/docs/superpowers/specs/2026-03-28-post-reading-presence-design.md`（仅当实现与 spec 存在必要差异）

- [ ] **Step 1: 运行 code review，检查接口兼容、Redis 一致性、匿名防刷和前端交互回归**
- [ ] **Step 2: 若实现细节与 spec 出现偏差，先更新 spec 再收尾**
- [ ] **Step 3: 记录最终验证证据和残留风险**
