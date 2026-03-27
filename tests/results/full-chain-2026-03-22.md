# ZhiCore 全链路测试记录（2026-03-22）

## 1. 执行摘要

- 执行日期：2026-03-22
- 运行方式：Docker 中的 zhicore 微服务 + 本地前端 dev server `http://localhost:3000`
- 基线批量结果：`/tmp/zhicore_full_chain_result.json`
- 基线统计：68 个 API case，`PASS 49 / FAIL 15 / WARN 4`
- 补充复测：
  - 消息发送链路在恢复 `allowStrangerMessage=true` 后重新打通
  - 搜索链路在修正 `PostSearchClient` 路径并为无 ES 分词插件环境补上 builtin schema fallback 后重新打通
  - gateway 公开读链路已重新打通：用户公开资料、评论分页、文件详情均返回 `200`
  - 图片上传经 gateway 已恢复到 `200`，上传后可回查文件详情
  - 管理端 UI 即使注入 localStorage token，仍被前端守卫重定向到登录页

## 2. 环境与结构

### 2.1 核心服务端口

| 服务 | 容器 | 端口 |
|---|---|---|
| gateway | `zhicore-gateway` | `8000` |
| user | `zhicore-user` | `8081` |
| content | `zhicore-content` | `8082` |
| comment | `zhicore-comment` | `8083` |
| message | `zhicore-message` | `8084` |
| notification | `zhicore-notification` | `8085` |
| search | `zhicore-search` | `8086` |
| ranking | `zhicore-ranking` | `8087` |
| upload | `zhicore-upload` | `8092 -> 8089` |
| admin | `zhicore-admin` | `8090` |
| file-service | `file-service-app` | `8089` |
| postgres | `shared-postgres` | `5432` |
| redis | `shared-redis` | `6379` |
| elasticsearch | `zhicore-elasticsearch` | `9200` |
| rocketmq | `shared-rocketmq-broker` | `10909/10911/10912` |

### 2.2 本次测试数据

- 数据前缀：`fullchain_1774155584`
- admin：`161313324569460737` / `fullchain_1774155584_admin@example.com`
- author：`161313324569460738` / `fullchain_1774155584_author@example.com`
- reader：`161313324569460739` / `fullchain_1774155584_reader@example.com`
- post：`161313324569460740`
- comment：`161313324573655045`
- reply comment：`161313324573655046`

### 2.3 去重规则

- 下表中标记为“已打通”的链路，如果对应服务、网关白名单、数据库表结构、MQ 消费逻辑没有变化，本轮之后不重复跑。
- 标记为“阻塞”的链路，必须在对应 blocker 修复后再回归，不要重复消耗时间。
- 精确去重以“case key” 为准，见文末“已执行 case key 清单”。

## 3. 已打通链路

| 链路 ID | 链路名称 | 已覆盖 case key | 服务结构 | 结果 | 下次何时重跑 |
|---|---|---|---|---|---|
| CHAIN-01 | 认证注册登录刷新 | `auth.register.*` `auth.login.*` `auth.refresh.author` | frontend/api client -> gateway -> user -> postgres/redis | 已打通 | 仅在 auth DTO、JWT、user 服务变更后重跑 |
| CHAIN-02 | 用户资料/关注/签到/陌生人消息开关 | `user.update_profile` `user.update_stranger_setting` `user.get_stranger_setting` `user.follow.*` `user.check_in*` | gateway -> user -> postgres/redis | 已打通 | 仅在 user 领域模型、关系表、签到逻辑变更后重跑 |
| CHAIN-03 | 作者发文草稿标签发布 | `content.create_post` `content.save_draft` `content.get_draft` `content.update_post` `content.attach_tags` `content.get_tags` `content.publish_post` `content.get_post_detail.public` `content.list_posts.cursor` `content.get_post_content` | gateway -> content -> postgres/mongodb/outbox | 已打通 | 仅在 content API、草稿、标签、发布事件变更后重跑 |
| CHAIN-04 | 文章点赞收藏 | `interaction.post.like*` `interaction.post.favorite*` | gateway -> content/interaction store -> postgres/redis | 已打通 | 仅在互动计数或鉴权变更后重跑 |
| CHAIN-05 | 评论写入/回复/点赞 | `comment.create` `comment.reply` `comment.like` `comment.liked_status` | gateway -> comment -> postgres/redis | 已打通 | 仅在 comment 写链路或点赞逻辑变更后重跑 |
| CHAIN-06 | 私信发送与会话查询 | 基线 `message.send` 受业务开关影响失败；补测 `POST /api/v1/messages`、`GET /api/v1/conversations/user/{authorId}` 成功 | gateway -> message -> user 校验 -> postgres | 已打通 | 仅在消息服务、陌生人消息策略、会话聚合逻辑变更后重跑 |
| CHAIN-07 | 搜索历史/热词/清空历史 | `search.history.reader` `search.hot` `search.clear_history.reader` | gateway -> search -> redis/elasticsearch | 已打通 | 仅在搜索历史或热词统计逻辑变更后重跑 |
| CHAIN-08 | 排行榜热榜与创作者分 | `ranking.post_score.await` `ranking.hot_post_details` `ranking.creator_score` | content/comment/interaction -> MQ -> ranking -> redis | 已打通 | 仅在 ranking 计算、消费、缓存逻辑变更后重跑 |
| CHAIN-09 | 管理端查询用户/文章/评论 | `admin.list_users` `admin.list_posts` `admin.list_comments` | gateway -> admin -> user/content/comment | 已打通 | 仅在 admin query API 或相关 feign 变更后重跑 |
| CHAIN-10 | 前端公开页面可视化验证 | 文章详情主内容可见、排行榜页可见新文章 | browser -> frontend -> gateway -> content/ranking | 部分打通 | 公开页 UI 或 gateway 公开读配置变更后重跑 |

## 4. 明确阻塞的链路与根因

| 阻塞 ID | 受影响 case key / 现象 | 当前表现 | 已确认根因 | 修复后优先复测 |
|---|---|---|---|---|
| BLOCK-01 | `user.get_profile.public` `comment.get_detail` `comment.list_page` `comment.like_count` `comment.replies_page` `boundary.files_route` | 2026-03-23 复测已解除，gateway 用户公开资料、评论分页、文件详情均返回 `200` | 根因拆成两部分：1) gateway 白名单缺口，`/api/v1/users/*`、comment 公共读接口、`/api/v1/files/*` 未正确放行；2) `files` 与 `upload` 被混到同一路由，且 file-service 读取文件详情需要 `X-App-Id`，gateway 之前没有补这个头。现已将 `/api/v1/upload/**` 保持转发到 `zhicore-upload`，`/api/v1/files/**` 单独转发到 `${FILE_SERVICE_URL}`，并补 `X-App-Id=${FILE_SERVICE_TENANT_ID:zhicore}`。 | 已于 2026-03-23 完成复测 |
| BLOCK-02 | `search.posts.reader` `search.suggest` `search.index_after_publish.await` | 2026-03-23 复测已解除，gateway `search/posts` 与 `search/suggest` 均返回 `200` | 根因拆成两部分：1) `PostSearchClient` 错把 content API 写成 `/posts/**`，实际可用路径是 `/api/v1/posts/**`；2) 当前 Docker 中的 Elasticsearch 未安装拼音/IK 插件，原始索引 schema 启动时报 `Unknown filter type [pinyin]`，导致索引初始化失败。现已修正 client 路径，并在 `zhicore-search` 启动时为无插件环境回退到 builtin schema，同时保留 `title.pinyin` 与 `tags` nested 映射以兼容现有查询。 | 已于 2026-03-23 完成复测 |
| BLOCK-03 | `notification.unread_count.await` `notification.list` `notification.read_all` | 2026-03-23 复测已解除，通知列表、未读数、全部已读均返回 `200` | 当前运行态 `zhicore_notification` 已存在 `notifications` 表；补充核对发现共享初始化脚本本来就包含 notification DDL，原记录属于过期环境状态。 | 已于 2026-03-23 完成复测 |
| BLOCK-04 | `admin.disable_reader` `admin.enable_reader` | 2026-03-23 复测已解除，disable/enable 返回 `200`，禁用/恢复登录副作用正常 | 当前运行态 `zhicore_admin` 已存在 `audit_logs` 表，管理写接口不再报 500。进一步核对 repo 后确认：共享初始化脚本此前缺少 admin DDL，导致“当前环境已恢复，但新环境不可复现”。本轮已将 `audit_logs` / `reports` 回填到 `database/init-all-databases.sql` 与 `docker/postgres-init/02-init-tables.sql`。 | 已于 2026-03-23 完成复测 |
| BLOCK-05 | `admin.list_pending_reports` | 2026-03-23 复测已解除，待处理举报列表返回 `200` | 当前运行态 `zhicore_admin` 已存在 `reports` 表；repo 层共享初始化脚本已同步补齐 `reports` DDL，避免新环境再次出现 schema 缺口。 | 已于 2026-03-23 完成复测 |
| BLOCK-06 | `upload.image` | 2026-03-23 复测已解除，gateway 图片上传返回 `200`，随后 `GET /api/v1/files/{fileId}` 也返回 `200` | 当前运行态证明 upload 代理链路已能把用户身份透传到 file-service；同时 gateway 已将 `/api/v1/upload/**` 与 `/api/v1/files/**` 分开处理，消除了此前的路由归属歧义。 | 已于 2026-03-23 完成主链路复测 |
| BLOCK-07 | 前端登录页提交正确账号后仍停在 `/auth/login` 并提示 `403` | 2026-03-23 复测已解除，浏览器 author/admin 登录均可进入首页 | 根因在 2026-03-23 收敛为一条认证链路缺口：1) 前端 `Login.vue`/`LoginRequest` 使用 `username`，后端要求 `email`；2) gateway `prod` 运行时 `CorsConfig` 用 `@Value` 读取 YAML list 失败，导致 `http://localhost:3000` 带 `Origin` 登录被 `403`；3) user-service 缺少 `GET /api/v1/auth/me`，登录后补拉当前用户报 `500`。现已改为前端提交 `email`、gateway 用 `Binder` 绑定 CORS 列表并在 `prod` 放行 localhost/127.0.0.1、user-service 新增 `/auth/me`。 | 已于 2026-03-23 完成浏览器复测 |
| BLOCK-08 | admin UI 注入 localStorage token 后访问 `/admin/users` 仍被重定向到登录页 | 2026-03-23 复测已解除，admin 登录后进入 `/admin/users`，刷新后仍停留在该页 | 根因从“只缺 `initAuth()`”扩展为两层：1) `src/main.ts` 未在路由启动前调用 `authStore.initAuth()`；2) `/auth/me` 返回 `userName`/`nickName`/`roles`，前端 `auth` store 未归一化成 `username`/`nickname`/`role`，导致管理员守卫把 admin 误判为非管理员。现已补 `initAuth()` 启动恢复，并在 `src/stores/auth.ts` 统一归一化当前用户和本地存储用户。 | 已于 2026-03-23 完成浏览器复测 |
| BLOCK-09 | `boundary.categories_route` | 2026-03-23 复测已解除，`GET /api/v1/categories` 经 gateway 与直连 content 均返回 `200`，浏览器 `/categories` 页面可打开 | 当前 Docker 运行态不再复现此前记录中的 `500`；`GET /api/v1/categories` 现返回 `{"code":200,"data":[]}`。本轮未对 content categories 代码做新增修复，因此将原记录标记为过期环境状态；后续若分类数据要在 UI 展示，只需补充数据而非修复接口异常。 | 已于 2026-03-23 完成 API 与浏览器复测 |

## 5. 前端 UI 证据

### 5.1 登录页

- 2026-03-23 author 登录复测成功：`/auth/login` -> `/`
- 快照：
  - `/home/azhi/workspace/projects/zhicore-frontend-vue/.playwright-cli/page-2026-03-23T10-56-41-531Z.yml`
- 关键网络：
  - `POST /api/v1/auth/login` => `200`
  - `GET /api/v1/auth/me` => `200`
- 代码定位：
  - `zhicore-frontend-vue/src/pages/auth/Login.vue`
  - `zhicore-frontend-vue/src/main.ts`
  - `zhicore-frontend-vue/src/stores/auth.ts`
  - `zhicore-microservice/zhicore-gateway/src/main/java/com/zhicore/gateway/config/CorsConfig.java`
  - `zhicore-microservice/zhicore-user/src/main/java/com/zhicore/user/interfaces/controller/AuthController.java`

### 5.2 文章详情页

- 现象：标题、正文、作者、点赞/收藏数据可展示；评论区报 `401`
- 快照：
  - `/home/azhi/workspace/projects/.playwright-cli/page-2026-03-22T05-06-43-928Z.yml`
- 控制台：
  - `/home/azhi/workspace/projects/.playwright-cli/console-2026-03-22T05-06-40-539Z.log`
- 根因：comment 公共读接口经 gateway 被错误要求鉴权，对应 BLOCK-01

### 5.3 排行榜页

- 现象：排行榜页面能展示本次新建文章 `full-chain-post-1774155584 updated`
- 快照：
  - `/home/azhi/workspace/projects/.playwright-cli/page-2026-03-22T05-06-46-225Z.yml`

### 5.4 管理页与分类页

- 2026-03-23 admin 登录后访问 `/admin/users` 成功，刷新后仍停留在 `/admin/users`
- 快照：
  - `/home/azhi/workspace/projects/zhicore-frontend-vue/.playwright-cli/page-2026-03-23T11-02-19-343Z.yml`
  - `/home/azhi/workspace/projects/zhicore-frontend-vue/.playwright-cli/page-2026-03-23T11-02-21-609Z.yml`
- 2026-03-23 `/categories` 页面可打开，标题为 `分类 - 知构`
- 快照：
  - `/home/azhi/workspace/projects/zhicore-frontend-vue/.playwright-cli/page-2026-03-23T11-04-03-788Z.yml`

### 5.4 管理端页面

- 现象：注入 admin token 后访问 `/admin/users`，仍跳转到 `/auth/login?redirect=/admin/users&reason=auth_required`
- 快照：
  - `/home/azhi/workspace/projects/.playwright-cli/page-2026-03-22T05-07-34-857Z.yml`
- 控制台：
  - `/home/azhi/workspace/projects/.playwright-cli/console-2026-03-22T05-07-32-260Z.log`
- 根因：前端未在应用启动阶段执行 `initAuth()`，对应 BLOCK-08

## 6. 补充复测证据（新鲜验证）

### 6.1 消息链路复测通过

- `POST /api/v1/messages`
  - 结果：`200`
  - 新消息 ID：`161313324582043651`
- `GET /api/v1/conversations/user/161313324569460738`
  - 结果：`200`
  - 会话 ID：`161313324582043649`

### 6.2 评论公开读经 gateway 仍失败

- gateway:
  - `GET /api/v1/comments/post/161313324569460740/page?page=1&size=10&sort=TIME`
  - 结果：`401 Missing authentication token`
- direct comment service:
  - `GET http://localhost:8083/api/v1/comments/post/161313324569460740/page?page=1&size=10&sort=TIME`
  - 结果：`200`

### 6.3 管理库表现状

- `docker exec shared-postgres psql -U postgres -d zhicore_admin -c "\dt"`
- 结果：`Did not find any relations.`

### 6.4 搜索链路复测通过（2026-03-23）

- content 契约验证：
  - `GET http://localhost:8082/posts/161313324569460740`
  - 结果：`500`
  - `GET http://localhost:8082/api/v1/posts/161313324569460740`
  - 结果：`200`
- pluginless ES 冷启动验证：
  - 删除索引：`DELETE http://localhost:9200/posts`
  - 重建搜索容器：`docker compose ... up -d --force-recreate zhicore-search`
  - 新鲜日志包含：
    - `Analysis plugins unavailable, falling back to builtin schema for index posts`
    - `Created index with builtin schema fallback: posts`
  - `GET http://localhost:9200/posts/_mapping`
  - 结果：fallback schema 已创建，保留 `title.pinyin` 文本子字段与 `tags` nested 映射
- 发布后索引与查询验证：
  - 更新文章：`PUT http://localhost:8082/api/v1/posts/161313324569460740`
  - 请求关键字：`searchfix-20260323-164157`
  - `GET http://localhost:8000/api/v1/search/posts?keyword=searchfix-20260323-164157&page=0&size=10`
  - 结果：`200`，`total=1`
- `GET http://localhost:8000/api/v1/search/suggest?prefix=searchfix-20260323-164157&limit=10`
- 结果：`200`，返回 `searchfix-20260323-164157`

### 6.5 gateway 公开读与文件链路复测通过（2026-03-23）

- gateway 公开读：
  - `GET http://localhost:8000/api/v1/users/161313324569460738`
  - 结果：`200`
  - `GET http://localhost:8000/api/v1/comments/post/161313324569460740/page?page=1&size=10&sort=TIME`
  - 结果：`200`
- 文件详情：
  - 先通过 gateway 上传图片：
    - `POST http://localhost:8000/api/v1/upload/image`
    - 结果：`200`
    - 新文件 ID：`019d1a0a-bb49-7595-b75d-268eceb10328`
  - 回查已有文件详情：
    - `GET http://localhost:8000/api/v1/files/019d1a03-aac8-70ad-b4cc-2ede709cce1b`
    - 结果：`200`
  - 直连 file-service 对照：
    - `GET http://localhost:8089/api/v1/files/019d1a03-aac8-70ad-b4cc-2ede709cce1b -H 'X-App-Id: zhicore'`
    - 结果：`200`
- 启动日志与配置行为：
  - gateway 启动日志显示 `zhicore-gateway.yml` 在 Nacos 中为空，当前容器实际使用镜像内 `application.yml`
  - 因此本轮修复由 gateway 镜像内的本地配置生效，而非远端 Nacos 覆盖

### 6.6 notification / admin 链路与 schema 复测通过（2026-03-23）

- notification 运行态：
  - `docker exec shared-postgres psql -U postgres -d zhicore_notification -c "\dt"`
  - 结果：存在 `notifications`
- admin 运行态：
  - `docker exec shared-postgres psql -U postgres -d zhicore_admin -c "\dt"`
  - 结果：存在 `audit_logs`、`reports`
- notification API：
  - `GET http://localhost:8000/api/v1/notifications?page=0&size=20`
  - 结果：`200`，author 账号返回 `total=3`
  - `GET http://localhost:8000/api/v1/notifications/unread-count`
  - 结果：`200`，`data=0`
  - `POST http://localhost:8000/api/v1/notifications/read-all`
  - 结果：`200`
- admin API：
  - `GET http://localhost:8000/api/v1/admin/reports/pending?page=1&size=20`
  - 结果：`200`
  - `POST http://localhost:8000/api/v1/admin/users/161313324569460739/disable`
  - 请求体：`{"reason":"full-chain-disable-check-20260323"}`
  - 结果：`200`
  - 随后 reader 再次登录：
    - HTTP：`200`
    - 业务码：`3006`
    - 消息：`用户已被禁用`
  - `POST http://localhost:8000/api/v1/admin/users/161313324569460739/enable`
  - 结果：`200`
  - 随后 reader 再次登录：
    - HTTP：`200`
    - 业务码：`200`
- 新环境可复现性补丁：
  - 之前 repo 中 `database/init-all-databases.sql` 与 `docker/postgres-init/02-init-tables.sql` 均缺少 admin 表 DDL
  - 本轮已补入 `audit_logs` / `reports`
  - 使用临时数据库 `zhicore_admin_verify_initall`、`zhicore_admin_verify_docker` 执行脚本片段后，均成功创建两张表

## 7. 已执行 case key 清单

### 7.1 admin

- PASS `admin.list_users`
- PASS `admin.list_posts`
- PASS `admin.list_comments`
- FAIL `admin.list_pending_reports`
- FAIL `admin.disable_reader`
- PASS `admin.disable_effect.login_block`
- FAIL `admin.enable_reader`
- PASS `admin.enable_effect.login_restore`
- PASS supplementary retest `admin.list_pending_reports.recheck`
- PASS supplementary retest `admin.disable_reader.recheck`
- PASS supplementary retest `admin.enable_reader.recheck`

### 7.2 auth

- PASS `auth.register.admin`
- PASS `auth.register.author`
- PASS `auth.register.reader`
- PASS `auth.login.admin`
- PASS `auth.login.author`
- PASS `auth.login.reader`
- PASS `auth.refresh.author`

### 7.3 boundary

- PASS supplementary retest `boundary.categories_route.recheck`
- WARN `boundary.files_route`
- PASS supplementary retest `boundary.files_route.recheck`

### 7.4 comment

- PASS `comment.create`
- FAIL `comment.get_detail`
- FAIL `comment.list_page`
- PASS supplementary retest `comment.list_page.recheck`
- PASS `comment.reply`
- PASS `comment.like`
- PASS `comment.liked_status`
- FAIL `comment.like_count`
- FAIL `comment.replies_page`

### 7.5 content

- PASS `content.create_post`
- PASS `content.save_draft`
- PASS `content.get_draft`
- PASS `content.update_post`
- PASS `content.attach_tags`
- PASS `content.get_tags`
- PASS `content.publish_post`
- PASS `content.get_post_detail.public`
- PASS `content.list_posts.cursor`
- PASS `content.get_post_content`

### 7.6 fixture

- PASS `fixture.admin_role`

### 7.7 interaction

- PASS `interaction.post.like`
- PASS `interaction.post.like_status`
- PASS `interaction.post.like_count`
- PASS `interaction.post.favorite`
- PASS `interaction.post.favorite_status`
- PASS `interaction.post.favorite_count`

### 7.8 message

- FAIL `message.send`
- FAIL `message.get_conversation_by_user`
- PASS `message.list_conversations.reader`
- PASS `message.unread_count.author.before_read`
- PASS supplementary retest `message.send.recheck`
- PASS supplementary retest `message.get_conversation_by_user.recheck`

### 7.9 notification

- WARN `notification.unread_count.await`
- FAIL `notification.list`
- FAIL `notification.read_all`
- PASS supplementary retest `notification.unread_count.await.recheck`
- PASS supplementary retest `notification.list.recheck`
- PASS supplementary retest `notification.read_all.recheck`

### 7.10 ranking

- PASS `ranking.post_score.await`
- PASS `ranking.hot_post_details`
- PASS `ranking.creator_score`

### 7.11 search

- FAIL `search.posts.reader`
- PASS supplementary retest `search.posts.reader.recheck`
- PASS `search.history.reader`
- FAIL `search.suggest`
- PASS supplementary retest `search.suggest.recheck`
- PASS `search.hot`
- PASS `search.clear_history.reader`
- WARN `search.index_after_publish.await`
- PASS supplementary retest `search.index_after_publish.await.recheck`

### 7.12 upload

- FAIL `upload.image`
- PASS supplementary retest `upload.image.recheck`

### 7.13 user

- FAIL `user.get_profile.public`
- PASS supplementary retest `user.get_profile.public.recheck`
- PASS `user.update_profile`
- PASS `user.update_stranger_setting`
- PASS `user.get_stranger_setting`
- PASS `user.follow.author`
- PASS `user.follow_check.author`
- PASS `user.follow_stats.author`
- PASS `user.check_in`
- PASS `user.check_in_stats`

## 8. 下轮回归建议顺序

1. 如将实时消息纳入本轮范围，单独排查前端 `/ws` 连接 `404`，它当前会持续刷 console error，但不影响本轮登录/管理页主链路
2. 以当前 Docker 环境再跑一次完整 browser sweep，确认文章详情评论区、发帖分类选择、消息中心等 UI 没有新回归
3. 若需要展示分类内容，补充 categories fixture 数据；当前接口已恢复为 `200`，但返回为空数组
