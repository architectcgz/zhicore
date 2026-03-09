# Sentinel QPS 基线表

日期：2026-03-09

## 适用范围

- `search`
- `ranking`
- `content`
- `comment`
- `user`
- `message`
- `notification`
- `admin`
- `upload`
- `id-generator`

## 口径说明

- 本文以各模块 `application.yml` 当前提交值为准，作为代码内默认基线。
- 当前仓库未附压测报告或线上观测截图，因此除特别说明外，默认值统一标记为“经验基线”。
- 若同一能力同时存在 URL 级资源和方法级资源，则两者默认共用同一 QPS，避免 Web 层与业务层配置漂移。
- 后续如有压测数据或线上观测数据，允许把“经验基线”升级为“压测基线”或“线上观测基线”，但必须同步更新本文。

## 分层建议

| 分层 | 当前建议区间 | 说明 |
| --- | --- | --- |
| 公共读接口 | 120-200 | 优先防刷与保护数据库/ES/缓存回源 |
| 登录态读接口 | 150-400 | 允许更高读频，但仍以用户级热点查询为主 |
| 后台查询接口 | 80-120 | 以稳定性优先，避免管理端大查询拖垮主链路 |
| 写接口 / 外部代理接口 | 20-60 | 对下游依赖和 IO 成本更敏感，应保守设置 |
| 高缓存聚合 / 轻量生成接口 | 400-1000 | 仅限 Redis/ZSet/计数器/ID 发号这类轻量路径 |

## 模块基线

### search

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `posts-qps` | `/api/v1/search/posts` + `SEARCH_POSTS` | 公共读接口 | 200 | 经验基线；搜索结果页有缓存与索引加持，但需防止持续爬取 |
| `suggest-qps` | `/api/v1/search/suggest` + `GET_SUGGESTIONS` | 公共读接口 | 300 | 经验基线；联想词更轻量，允许略高于主搜索 |
| `hot-keywords-qps` | `/api/v1/search/hot` + `GET_HOT_KEYWORDS` | 公共读接口 | 200 | 经验基线；热点词聚合可缓存，但仍需限制外部刷量 |
| `history-qps` | `/api/v1/search/history` + `GET_USER_HISTORY` | 登录态读接口 | 100 | 经验基线；个性化历史记录缺少公共缓存，保守控制 |

### ranking

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `hot-posts-qps` | 热榜路由 + `HOT_POST_DETAILS` / `RESOLVE_POST_METADATA` | 高缓存聚合接口 | 1000 | 经验基线；默认假设 Redis ZSet / 榜单缓存命中率高 |
| `creator-qps` | 创作者排行路由 | 高缓存聚合接口 | 500 | 经验基线；聚合成本低于文章热榜，但仍有聚合读放大 |
| `topic-qps` | 话题排行路由 | 高缓存聚合接口 | 500 | 经验基线；与创作者排行同层管理 |

### content

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `post-detail-qps` | `/api/v1/posts/{postId}` + `GET_POST_DETAIL` | 公共读接口 | 300 | 经验基线；文章详情有缓存前置，但存在热点放大 |
| `post-list-qps` | `/api/v1/posts` + `GET_POST_LIST` | 公共读接口 | 200 | 经验基线；列表查询易被翻页爬取 |
| `post-content-qps` | `/api/v1/posts/{postId}/content` + `GET_POST_CONTENT` | 公共读接口 | 150 | 经验基线；正文读取更依赖存储层回源 |
| `tag-detail-qps` | `/api/v1/tags/{slug}` + `GET_TAG_DETAIL` | 公共读接口 | 200 | 经验基线；标签详情偏轻量 |
| `tag-list-qps` | `/api/v1/tags` + `LIST_TAGS` | 公共读接口 | 150 | 经验基线；列表查询存在刷库风险 |
| `tag-search-qps` | `/api/v1/tags/search` + `SEARCH_TAGS` | 公共读接口 | 150 | 经验基线；搜索类接口需限制关键词枚举 |
| `tag-posts-qps` | `/api/v1/tags/{slug}/posts` + `GET_POSTS_BY_TAG` | 公共读接口 | 120 | 经验基线；标签维度翻页更易造成扫描压力 |
| `hot-tags-qps` | `/api/v1/tags/hot` + `GET_HOT_TAGS` | 公共读接口 | 120 | 经验基线；热门标签可缓存，但要与详情/列表区分 |
| `post-liked-qps` | `/api/v1/posts/{postId}/like/status` + `IS_POST_LIKED` | 登录态读接口 | 250 | 经验基线；依赖用户态缓存与关系判定 |
| `batch-post-liked-qps` | `/api/v1/posts/like/batch-status` + `BATCH_CHECK_POST_LIKED` | 登录态读接口 | 150 | 经验基线；批量接口单次放大更明显，低于单查 |
| `post-like-count-qps` | `/api/v1/posts/{postId}/like/count` + `GET_POST_LIKE_COUNT` | 公共读接口 | 250 | 经验基线；计数查询轻量，可略高于普通列表 |
| `post-favorited-qps` | `/api/v1/posts/{postId}/favorite/status` + `IS_POST_FAVORITED` | 登录态读接口 | 250 | 经验基线；与点赞状态同层 |
| `batch-post-favorited-qps` | `/api/v1/posts/favorite/batch-status` + `BATCH_CHECK_POST_FAVORITED` | 登录态读接口 | 150 | 经验基线；与批量点赞状态同层 |
| `post-favorite-count-qps` | `/api/v1/posts/{postId}/favorite/count` + `GET_POST_FAVORITE_COUNT` | 公共读接口 | 250 | 经验基线；计数查询轻量 |
| `admin-query-posts-qps` | `/api/v1/admin/posts` + `ADMIN_QUERY_POSTS` | 后台查询接口 | 100 | 经验基线；后台检索以保护主库为先 |
| `outbox-failed-qps` | `/api/v1/admin/outbox/failed` + `LIST_FAILED_OUTBOX` | 后台查询接口 | 80 | 经验基线；运维查询频率应低于普通后台检索 |

### comment

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `comment-detail-qps` | 评论详情 | 公共读接口 | 200 | 经验基线；详情查询中等成本 |
| `top-level-page-qps` | 顶级评论传统分页 | 公共读接口 | 150 | 经验基线；传统分页更易触发深翻页扫描 |
| `top-level-cursor-qps` | 顶级评论游标分页 | 公共读接口 | 250 | 经验基线；游标分页更友好，可高于传统分页 |
| `replies-page-qps` | 回复传统分页 | 公共读接口 | 150 | 经验基线；与顶级评论传统分页同层 |
| `replies-cursor-qps` | 回复游标分页 | 公共读接口 | 250 | 经验基线；与顶级评论游标分页同层 |
| `comment-liked-qps` | 点赞状态查询 | 登录态读接口 | 300 | 经验基线；用户态关系查询偏轻量 |
| `batch-comment-liked-qps` | 批量点赞状态查询 | 登录态读接口 | 200 | 经验基线；批量接口需保守于单查 |
| `comment-like-count-qps` | 点赞计数查询 | 公共读接口 | 300 | 经验基线；计数读取可高于分页类接口 |
| `admin-query-comments-qps` | 后台评论查询 | 后台查询接口 | 120 | 经验基线；后台查询保守控速 |

### user

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `user-detail-qps` | 用户详情 | 登录态 / 公共读接口 | 250 | 经验基线；资料页查询中等成本 |
| `user-simple-qps` | 用户简要信息 | 登录态读接口 | 400 | 经验基线；下游大量依赖，单次查询较轻 |
| `batch-user-simple-qps` | 批量用户简要信息 | 登录态读接口 | 200 | 经验基线；批量接口需要限制 fan-out |
| `stranger-message-setting-qps` | 陌生人消息设置 | 登录态读接口 | 300 | 经验基线；布尔/枚举型轻量配置查询 |
| `followers-qps` | 粉丝列表 | 登录态读接口 | 150 | 经验基线；关系链列表查询成本较高 |
| `followings-qps` | 关注列表 | 登录态读接口 | 150 | 经验基线；与粉丝列表同层 |
| `follow-stats-qps` | 关注统计 | 登录态读接口 | 250 | 经验基线；计数类查询可高于列表 |
| `is-following-qps` | 关注关系判断 | 登录态读接口 | 400 | 经验基线；布尔关系判断轻量 |
| `check-in-stats-qps` | 签到统计 | 登录态读接口 | 200 | 经验基线；聚合读中等成本 |
| `monthly-check-in-bitmap-qps` | 月度签到位图 | 登录态读接口 | 150 | 经验基线；位图读取虽轻量，但读取窗口较大 |
| `blocked-users-qps` | 拉黑列表 | 登录态读接口 | 150 | 经验基线；安全关系链列表保守控制 |
| `is-blocked-qps` | 拉黑关系判断 | 登录态读接口 | 300 | 经验基线；布尔判断轻量 |
| `query-users-qps` | 后台用户查询 | 后台查询接口 | 100 | 经验基线；后台检索以主库保护为先 |

### message

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `conversation-list-qps` | `/api/v1/conversations` + `GET_CONVERSATION_LIST` | 登录态读接口 | 200 | 经验基线；会话列表读取频繁，但涉及关系与用户信息装配 |
| `conversation-detail-qps` | `/api/v1/conversations/{conversationId}` + `GET_CONVERSATION` / `GET_CONVERSATION_BY_USER` | 登录态读接口 | 300 | 经验基线；详情单查成本低于列表 |
| `message-history-qps` | `/api/v1/messages/conversation/{conversationId}` + `GET_MESSAGE_HISTORY` | 登录态读接口 | 250 | 经验基线；消息历史存在分页回源成本 |
| `unread-count-qps` | `/api/v1/messages/unread-count` + `GET_UNREAD_COUNT` | 登录态读接口 | 400 | 经验基线；未读数为轻量计数型接口 |

### notification

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `aggregated-qps` | `/api/v1/notifications` + `GET_AGGREGATED_NOTIFICATIONS` | 登录态读接口 | 200 | 经验基线；聚合通知需做多源合并 |
| `unread-count-qps` | `/api/v1/notifications/unread-count` + `GET_UNREAD_COUNT` | 登录态读接口 | 400 | 经验基线；未读数是轻量计数型查询 |

### admin

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `user-list-qps` | 后台用户列表 | 后台查询接口 | 100 | 经验基线；后台大查询以稳定为先 |
| `post-list-qps` | 后台文章列表 | 后台查询接口 | 100 | 经验基线；与后台用户列表同层 |
| `comment-list-qps` | 后台评论列表 | 后台查询接口 | 100 | 经验基线；与后台用户列表同层 |
| `report-list-qps` | 后台举报列表 | 后台查询接口 | 80 | 经验基线；运维/审计查询更保守 |

### upload

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `upload-image-qps` | `/api/v1/upload/image` + `UPLOAD_IMAGE` | 外部代理接口 | 60 | 经验基线；写路径依赖 file-service 与 IO，需保守 |
| `upload-audio-qps` | `/api/v1/upload/audio` + `UPLOAD_AUDIO` | 外部代理接口 | 40 | 经验基线；音频体积更大，低于图片上传 |
| `upload-images-batch-qps` | `/api/v1/upload/images/batch` + `UPLOAD_IMAGES_BATCH` | 外部代理接口 | 20 | 经验基线；批量写放大最明显，应最低 |
| `get-file-url-qps` | `/api/v1/upload/file/{fileId}/url` + `GET_FILE_URL` | 外部代理接口 | 200 | 经验基线；读路径轻于上传，可明显高于写接口 |
| `delete-file-qps` | `/api/v1/upload/file/{fileId}` + `DELETE_FILE` | 外部代理接口 | 60 | 经验基线；删除同属写路径，沿用图片上传级别 |

### id-generator

| 配置键 | 保护对象 | 接口类型 | 默认 QPS | 来源 / 假设 |
| --- | --- | --- | --- | --- |
| `snowflake-qps` | `/api/v1/id/snowflake` + `GENERATE_SNOWFLAKE_ID` | 高缓存 / 轻量生成接口 | 500 | 经验基线；Snowflake 发号为轻量单点代理 |
| `batch-snowflake-qps` | `/api/v1/id/snowflake/batch` + `GENERATE_BATCH_SNOWFLAKE_IDS` | 高缓存 / 轻量生成接口 | 200 | 经验基线；批量发号会放大下游压力，低于单次发号 |
| `segment-qps` | `/api/v1/id/segment/{bizTag}` + `GENERATE_SEGMENT_ID` | 高缓存 / 轻量生成接口 | 300 | 经验基线；Segment 模式依赖业务标签和号段管理，介于单次与批量之间 |

## 后续收敛动作

- 为每个模块补 1 份压测记录，至少包含 QPS、P95/P99、错误率、核心依赖饱和度。
- 为线上接口补采样看板，最少记录峰值 QPS、拒绝次数、缓存命中率、数据库慢查询数量。
- 只有当压测或线上观测能解释默认值时，才允许把“经验基线”迁移为“可上线基线”。
