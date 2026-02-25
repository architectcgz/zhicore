# TODO：架构与实现缺陷修复清单（接手版）

本清单按优先级给出“问题 -> 影响 -> 修复动作 -> 验收标准”，便于排期执行。

## P0（本周必须处理）
### 1. 列表接口参数未生效
- 问题：`GET /api/v1/posts` 的 `sort/status` 在 Controller 中接收但未下传使用。
- 位置：`PostController#getPublishedPosts`
- 影响：接口契约与实际行为不一致，前端筛选/排序无效。
- 修复动作：
  1. 定义统一查询请求 DTO（page/size/sort/status）
  2. 下传到 `PostFacadeService`/`PostApplicationService`
  3. Repository 增加对应查询实现
- 验收标准：
  - 不同 `status`、`sort` 参数返回可观测差异
  - 补集成测试覆盖

### 2. `PostStatus.valueOf(status)` 风险
- 问题：非法状态直接触发 `IllegalArgumentException`。
- 位置：`PostFacadeService#getMyPosts`
- 影响：返回码不可控，客户端体验差。
- 修复动作：
  1. 增加安全解析方法（默认值或显式校验报错）
  2. 输出统一业务错误码
- 验收标准：非法状态返回预期 `PARAM_ERROR`

### 3. 点赞/收藏计数可能出现负数
- 问题：取消动作直接 `decrement`，未限制下界。
- 位置：`PostLikeApplicationService`、`PostFavoriteApplicationService`
- 影响：统计错误，影响排序/推荐。
- 修复动作：
  1. Redis 侧执行带下界保护脚本或逻辑判断
  2. 增加 DB/Redis 计数校准任务
- 验收标准：任何路径下 count >= 0

## P1（下周处理）
### 4. Redis `KEYS` 风险
- 问题：多处使用 `keys(pattern)`。
- 影响：大 key 空间可能阻塞 Redis。
- 修复动作：改为 `SCAN` 批量游标删除/遍历。
- 验收标准：代码中无 `keys(pattern)` 调用。

### 5. Outbox `FAILED` 告警未实现
- 问题：`OutboxEventDispatcher` 中仍是 TODO。
- 影响：事件长期失败不可见。
- 修复动作：接入告警渠道（钉钉/飞书/PagerDuty 等）。
- 验收标准：制造失败事件可触发告警通知。

### 6. 标签并发覆盖风险
- 问题：`detachTag` 使用“读后全量替换”。
- 影响：并发编辑标签时可能丢更新。
- 修复动作：改差量更新 + 版本冲突控制。
- 验收标准：并发测试下无丢标签场景。

## P2（持续改进）
### 7. `publishedAt` 映射语义错误
- 问题：部分 VO 使用 `createdAt` 填充 `publishedAt`。
- 影响：时间展示不准确。
- 修复动作：统一改为真实 `publishedAt` 字段。

### 8. 删除文章未清理正文内资源
- 问题：内容图片清理仅有 TODO。
- 影响：对象存储垃圾增长。
- 修复动作：解析正文资源并异步清理，失败可重试。

### 9. `.skip` 测试过多
- 问题：当前约 21 个测试被跳过。
- 影响：回归保护不足。
- 修复动作：按核心链路优先恢复：创建、发布、Outbox、互动计数。

## 建议排期
1. Sprint 1：完成全部 P0 + 添加回归测试。
2. Sprint 2：完成 P1 并上线监控告警闭环。
3. Sprint 3：处理 P2 + 技术债专项。
