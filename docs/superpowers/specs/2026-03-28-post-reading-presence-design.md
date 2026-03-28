# 文章详情页“正在读”人数与头像设计

## 1. 背景

当前文章详情页已经具备正文、目录、评论、点赞收藏等阅读能力，但没有“当前有多少人正在读”这一类页面级在线态信息。

本次目标是在文章详情页增加：

- 显示当前“正在读”的总人数
- 展示最多 3 个正在阅读的登录用户头像
- 头像只做展示，不允许点击进入个人资料
- 匿名用户也计入人数，但不出现在头像列表中

## 2. 需求边界

### 2.1 页面范围

仅在文章详情页展示，不扩散到首页、列表页、推荐卡片或评论区。

### 2.2 在线口径

“正在读”定义为：用户当前仍停留在该文章详情页。

由于浏览器关闭、系统挂起、网络断开等场景下，前端无法绝对可靠地在离开瞬间通知后端，因此服务端口径采用：

- 页面进入时注册阅读会话
- 页面存活期间周期性续约
- 页面离开时尽力注销
- 异常退出依靠短 TTL 自动过期

这是一种对“页面还没关闭就算正在读”的工程近似实现。

### 2.3 用户口径

- 登录用户：计入人数，允许出现在头像列表
- 匿名用户：计入人数，不出现在头像列表

### 2.4 展示限制

- 最多展示 3 个头像
- 头像不可点击，不跳个人资料页
- 头像仅作为 presence 展示，不承载社交关系操作

## 3. 方案选择

### 3.1 方案 A：HTTP 心跳 + Redis presence + HTTP 查询

流程：

- 进入详情页时通过 HTTP 注册 presence 会话
- 前端按固定间隔发送心跳续期
- 离开页面时通过 `sendBeacon` 或普通请求尽力注销
- 前端定时拉取当前文章的 presence 聚合结果

优点：

- 改动范围最小，直接落在 `zhicore-content` 和详情页
- 匿名用户接入简单，不需要新增匿名 WebSocket 认证语义
- 不污染文章详情主查询缓存
- 与当前文章详情页的 HTTP 查询模型一致，易于灰度和回滚

缺点：

- 不是强实时，人数和头像变化存在秒级延迟

### 3.2 方案 B：复用现有 WebSocket / STOMP

流程：

- 进入详情页后订阅文章级 topic
- 服务端通过 WS 广播 presence 变化
- 前端实时更新人数和头像

优点：

- 对登录用户可以实现更实时的变化感知

缺点：

- 当前 WebSocket 只存在于 `message` 和 `notification` 服务，`content` 没有自己的 WS 入口
- 网关当前只转发 `/ws/message/**` 和 `/ws/notification/**`
- 现有 WS 语义是已登录用户通道，本需求还要求匿名用户计数
- 即使使用 WS，异常断网与页面崩溃场景仍需要 TTL / heartbeat 兜底，复杂度不会消失

### 3.3 本次选型

本次采用方案 A：`HTTP 心跳 + Redis presence + HTTP 查询`。

选择原因：

- 需求只覆盖文章详情页局部展示，不要求评论级强实时互动
- 匿名用户计数是刚性需求，而匿名 WS 接入会显著增加协议与安全复杂度
- 当前已有 WS 基础设施不在 `content` 服务内，强行复用会引入跨服务职责耦合
- 方案 A 可以在后续平滑升级到 WS 推送，不阻断长期演进

## 4. 总体设计

### 4.1 架构概览

后端在 `zhicore-content` 内新增一个独立的“文章阅读在线态”子能力：

- 注册 / 续约阅读会话
- 注销阅读会话
- 查询某篇文章当前的阅读 presence 聚合结果

presence 数据不写入文章详情缓存，也不写入文章主表，统一落在 Redis 中的短时数据结构。

前端在文章详情页新增一个 presence composable：

- 进入页面自动注册会话
- 定时续约
- 定时拉取 presence 聚合结果
- 离开页面注销
- 将人数和头像注入页面头部或阅读信息区域

## 5. 后端设计

### 5.1 API 设计

在 `zhicore-content` 新增 presence 读写接口，挂在文章资源下：

#### `POST /api/v1/posts/{postId}/readers/session`

作用：

- 创建或刷新当前页面的阅读会话

请求体建议：

```json
{
  "sessionId": "page-view-session-id"
}
```

说明：

- `sessionId` 由前端在进入当前文章详情页时生成，作为一次 page-view 级别会话标识
- 路由切换到另一篇文章或当前详情页组件重新挂载时，必须生成新的 `sessionId`
- 服务端根据鉴权上下文判断当前请求是登录用户还是匿名用户，不信任前端上传的用户身份字段
- presence 聚合时必须校验 `session.postId == 当前 postId`，避免历史 membership 串文
- 匿名用户仅标记匿名，不保存头像展示信息

#### `POST /api/v1/posts/{postId}/readers/session/leave`

作用：

- 页面离开时尽力注销当前阅读会话

请求体或参数：

```json
{
  "sessionId": "page-view-session-id"
}
```

选择 `POST` 而不是 `DELETE`，原因是前端页面离开时优先使用 `navigator.sendBeacon`，需要一个可以直接兼容的协议。

离开接口的服务端动作应包括：

- 删除 `post:presence:session:{sessionId}` 明细
- 从对应文章 `post:presence:{postId}:sessions` 的 `zset` 中移除该 `sessionId`

#### `GET /api/v1/posts/{postId}/readers/presence`

作用：

- 返回当前文章的阅读人数与可展示头像

响应体建议：

```json
{
  "readingCount": 12,
  "avatars": [
    {
      "userId": "1001",
      "nickname": "用户A",
      "avatarUrl": "https://..."
    }
  ]
}
```

约束：

- `avatars` 最多返回 3 个
- 返回头像数据时不附带任何 profile 链接字段

### 5.2 Redis 数据模型

presence 采用短 TTL 临时数据，不复用文章详情缓存。

建议 key：

- `post:presence:{postId}:sessions`
  - 使用 `zset` 记录当前文章的活跃 sessionId，`score = expireAtEpochMillis`
- `post:presence:session:{sessionId}`
  - 记录 session 明细，包括 `postId`、`userId`、`anonymous`、`nickname`、`avatarId/avatarUrl`、`expireAt`

建议策略：

- 每个 session TTL：40 秒
- 前端续约间隔：15 秒
- 每次心跳同时刷新 session 明细 TTL，并把 `sessionId` 以新的 `expireAt` 写入文章 `zset`
- 查询 presence 时先执行 `ZREMRANGEBYSCORE key -inf now` 清理过期 session，再读取剩余活跃 session
- 查询到 `sessionId` 后，再读取 `post:presence:session:{sessionId}` 明细；若明细不存在或 `postId` 不匹配，立刻从当前文章 `zset` 补清理
- 头像列表按最近心跳时间倒序选取登录用户，并按 `userId` 去重后截取前 3 个

这样可以保证：

- 异常断开最多残留一个短 TTL 周期
- 不依赖关闭事件绝对成功
- 文章维度 key 不会长期堆积失效 `sessionId`

### 5.3 会话去重规则

以 `sessionId` 作为页面级会话主键。

效果：

- 同一浏览器 tab 反复心跳不会重复计数
- 同一用户同时开多个 tab 阅读同一文章，可以计为多个“正在读页面”
- 同一用户的多个页面实例在头像列表中只展示一次，避免占满 3 个头像位

本次不做“同用户跨 tab 折叠为 1 人”的复杂合并。因为需求口径更接近页面在线态，而不是独立用户去重在线态。

### 5.4 登录用户头像来源

登录用户 presence 写入时保存以下快照字段：

- `userId`
- `nickname`
- `avatarId` 或解析后的 `avatarUrl`

优先原则：

- 以当前认证用户上下文为准
- 头像 URL 的解析方式沿用 content 现有文件 URL 解析器，避免前后端重新拼接

### 5.5 缓存与一致性

presence 结果不进入现有文章详情缓存，理由如下：

- 在线态高度瞬时，不适合混入分钟级详情缓存
- 详情缓存命中不应被 presence 抖动破坏
- presence 独立查询更容易做降级和限流

### 5.6 文章下架 / 删除 / 不可见处理

presence 的可见性必须与文章详情页保持同一套读权限和状态判断，不允许为不可读文章单独暴露“多少人正在读”。

规则如下：

- 对公开详情页不可读的文章（如已删除、已下架、未发布），`POST /readers/session` 不创建 session，直接返回与详情接口一致的不可访问结果
- 对公开详情页不可读的文章，`GET /readers/presence` 不返回人数与头像，直接返回与详情接口一致的不可访问结果
- `POST /readers/session/leave` 仍采用 best-effort 清理策略；即使文章已下架，也允许按 `sessionId` 清理残留 presence

运行中状态切换处理：

- 如果用户正在阅读时文章被下架，下一次心跳或下一次 presence 查询必须识别到文章已不可见
- 服务端在识别文章不可见后，应停止为该文章续约 session，并尽力清理当前 `sessionId`
- 前端在收到不可访问结果后，应停止 presence 轮询与心跳；页面主链路是否展示“文章不存在 / 已下线”，仍以详情接口结果为准

这样可以保证：

- 下架文章不会继续对外暴露 presence 信息
- 正在阅读中的旧 session 会在一次心跳周期内被收敛清理
- presence 子能力不会绕过现有文章可见性边界

### 5.7 异常与降级

当 Redis 不可用或 presence 查询失败时：

- 不影响文章详情主链路
- `GET presence` 返回空头像和 `0` 或业务定义的降级值
- 前端仅隐藏或弱化显示 presence 区块，不阻塞正文阅读

## 6. 前端设计

### 6.1 接入位置

接入边界包括：

- `PostDetail.vue`
- `usePostDetailContent.ts`
- `PostDetailHeader.vue`
- `PostDetailHeaderMeta.vue`
- 视情况新增独立展示组件，例如 `PostDetailReadingPresence.vue`

展示区域建议放在文章头部元信息区，靠近阅读时长、评论数等轻量阅读元数据。

原因：

- “多少人正在读”属于阅读上下文信息
- 位置应自然、低干扰，不进入操作栏或评论区

### 6.2 前端行为

详情页加载成功后：

1. 为当前 page-view 生成新的 `sessionId`
2. 调用 `POST /readers/session` 注册 presence
3. 启动两个定时任务
   - 每 15 秒续约一次 session
   - 每 10 到 15 秒查询一次 presence 聚合
4. 页面离开、路由切换、组件卸载时执行注销
5. 使用 `navigator.sendBeacon` 调用 `POST /readers/session/leave` 优先保证离开时请求发出；不支持时回退普通请求

补充规则：

- `sessionId` 为 page-view 级别，不持久化到跨页面长期存储
- 当前详情页组件因为 `postId` 变化而切换文章时，必须先停止旧心跳并尝试注销旧 `sessionId`，再为新文章生成新 `sessionId`

### 6.3 UI 展示

UI 结构：

- 文案：`12 人正在读`
- 头像组：最多 3 个圆形头像
- 当存在匿名用户但头像不足 3 时，不补匿名占位头像

交互规则：

- 头像容器使用普通 `div` / `span`
- 禁止使用 `router-link`、`a` 标签或点击事件跳个人主页
- 可保留 hover 提示昵称，但不触发导航

### 6.4 无鉴权场景

未登录用户进入详情页时：

- 仍创建匿名阅读 session
- 聚合人数正常增加
- 不会将匿名用户渲染到头像列表

### 6.5 刷新与重复进入

页面刷新视为旧 page-view 销毁、新 page-view 重建：

- 老 session 可能因刷新时未成功注销而短暂残留
- 新 session 会立即上线
- 最终依赖 TTL 收敛为正确值

## 7. 安全与风控

### 7.1 不暴露资料入口

presence 响应不返回 profile URL，不在前端绑定资料页路由。

### 7.2 限流与刷量防护

presence 写接口属于高频接口，需要最小限流保护：

- 单 session 心跳频率限制
- 对异常过高的 session 写频率直接丢弃或覆盖

本次不引入复杂风控系统，但应保证不会因频繁刷新导致 Redis 热点失控。

### 7.3 数据可信度

用户身份以服务端鉴权上下文为准，前端不允许指定他人的 `userId`、昵称或头像。

## 8. 测试设计

### 8.1 后端

应覆盖：

- 注册 session 后人数增加
- 同一 session 重复注册不重复计数
- 同一路由页面切换文章时，旧文章不会继续统计旧 session
- 注销 session 后人数减少
- 匿名用户计数但不进入头像列表
- 返回头像列表最多 3 个
- 同一登录用户多 tab 时头像列表按 `userId` 去重
- 过期 session 不计入 presence
- Redis 异常时 presence 查询降级，不影响文章详情主读接口

### 8.2 前端

应覆盖：

- 详情页挂载时注册 presence
- 定时续约与定时拉取 presence
- `postId` 变化时旧 session 停止续约并为新文章创建新 session
- 卸载时发送注销请求
- presence 数据渲染人数与 3 个以内头像
- 头像区域不可点击，不触发路由跳转
- presence 查询失败时页面主体仍正常显示

## 9. 风险与后续演进

### 9.1 当前风险

- 浏览器关闭事件不可靠，人数会存在最多一个 TTL 周期的滞后
- 多 tab 会按页面维度计数，而不是按用户维度去重
- 高频 presence 写入会增加 Redis 压力，需要合理 TTL 与心跳间隔

### 9.2 后续演进方向

若后续出现以下需求：

- 实时进出提示
- 评论区在线用户
- 页面协同状态
- 更低延迟的 presence 更新

可在保留 Redis presence 存储模型的前提下升级为：

- `content` 独立 WS endpoint
- 或统一实时 presence 基础服务

本次实现不预埋复杂 WS 协议，只保留可演进接口边界。

## 10. 分层与落点约束

presence 写路径不进入现有只读查询门面。

建议新增独立边界：

- controller：`PostReaderPresenceController`
- application service：`PostReaderPresenceAppService`
- repository / store：独立 Redis presence store

这样可以避免把高频写操作塞进现有 `PostQueryController` / `PostQueryFacade` 的读侧职责中。

## 11. 最终决策

本次功能按以下标准落地：

- 范围只在文章详情页
- presence 独立于文章详情缓存
- 使用 HTTP 注册 / 续约 / 查询 / 注销
- 使用 Redis 保存短 TTL 阅读会话
- 匿名用户计数，登录用户显示最多 3 个头像
- 头像不可点击
- presence 故障不影响文章详情正文阅读
