# Q&A

## Q1: comment 传统分页的极大分页问题应该怎么处理？

这个问题的重点不是“用户会不会手动翻到很后面”，而是“机器会不会直接请求极大分页”。

主要风险有两类：

- 单次请求超大 `page`，导致数据库执行高成本 `OFFSET` 扫描
- 从 `page=0` 开始顺序爬取，持续消耗查询资源

推荐处理方案：

1. 对传统分页接口设置查询窗口上限  
   例如限制 `page * size <= MAX_OFFSET_WINDOW`，超过窗口直接拒绝请求，而不是继续执行深分页查询。

2. 将深翻页能力收敛到游标分页  
   Web 首屏和浅层翻页继续使用 `/page`，更深的翻页统一走 `/cursor`。

3. 增加限流和监控  
   仅限制极大分页不够，还需要对高频翻页、同一 `postId` 的连续扫描增加限流、告警和访问审计。

结论：

- 极大分页限制解决的是大 `OFFSET` 慢查询问题
- 限流和监控解决的是顺序爬取问题
- 长期方向是让深分页场景尽量使用游标分页

### 当前代码实现（2026-03-09）

- `comment` 传统分页入口已增加查询窗口限制，统一使用 `CommonConstants.MAX_OFFSET_WINDOW = 5000`
- 当 `page * size >= 5000` 时，接口会返回参数错误，并提示“传统分页仅支持前5000条数据，请改用游标分页”
- 顶级评论和回复列表的传统分页都已接入该限制，深翻页场景应改走 `/cursor`

## Q2: 服务熔断与降级在代码里应该怎么落地？

这个问题的重点不是“有没有配 Sentinel 或 fallback”，而是“规则和降级代码是否真的会生效，且不会伪造业务成功”。

推荐处理方案：

1. 默认让 Feign fallback 返回明确失败  
   默认返回 `SERVICE_DEGRADED`，不要把下游故障伪装成空数据、假用户或假成功。

2. 公共层只做观测，不做业务降级拼装  
   共享模块负责统一日志、计数和错误码；具体返回结构仍在各服务自己的 `FallbackFactory` 中定义。

3. Sentinel 规则按真实资源落到模块内  
   不依赖 `user-service`、`post-service` 这类猜测式资源名，而是用 URL 级规则或显式 `@SentinelResource`。

4. 规则加载按资源覆盖，不重复追加  
   同一个模块重复初始化时，应该按资源替换旧规则，避免全局 `FlowRuleManager` 出现重复规则。

结论：

- “显式失败”比“伪造成功”更安全
- “模块内真实资源规则”比“common 猜资源名”更可靠
- 公共层应沉淀观测和装载工具，不应承载具体业务兜底语义

### 当前代码实现（2026-03-09）

- 新增了 `DownstreamFallbackSupport`，统一收口 Feign fallback 的日志、计数和 `SERVICE_DEGRADED` 返回
- `ranking`、`search`、`content`、`comment`、`user`、`message`、`notification`、`admin` 的 fallback 已统一成显式失败口径
- `ranking` 不再把文章服务失败静默当空数据
- `common` 里的伪全局 Sentinel 默认规则已默认关闭，避免出现“配置了但实际不命中”的误导
- `search` 已补上真实 URL 级 Sentinel 规则，`ranking` 的 URL 级规则也改成按资源覆盖加载
- 新增 `ApiResponseBlockExceptionHandler`，统一把 Sentinel block 返回成 `429 + ApiResponse`
- 各服务启动类的 `@EnableFeignClients` 已改成 `clients = {...}` 精确注册，不再因为同包扫描把共享客户端一并注册进来
- `search` 已切到服务内 `PostServiceClient + PostServiceFallbackFactory`，不再直接注册无本地 fallback 的共享文章客户端
- `common` 已统一提供 `SentinelResourceAspect`，各服务不再各自重复注册切面
- `search` 的 `searchPosts/getSuggestions/getHotKeywords/getUserHistory` 和 `ranking` 的热点聚合方法，已经补上显式 `@SentinelResource`
- 方法级 block 统一抛 `TOO_MANY_REQUESTS`，不返回伪数据；对应规则也已落到 `FlowRuleManager`
- `notification` 已补聚合通知和未读数的 URL 级 + 方法级规则，`content` 已补文章详情、文章列表、正文内容、标签详情/列表/搜索、标签文章、热门标签、点赞/收藏状态与计数、后台文章列表、Outbox 失败事件分页的热点读规则
- `message` 已补会话列表、会话详情、消息历史、未读数的热点读规则，`admin` 已补用户/帖子/评论/举报列表查询的热点读规则
- `comment` 已补评论详情、顶级评论分页/游标、回复分页/游标、点赞状态/计数、后台评论列表的热点读规则，`user` 已补用户详情、用户简要信息、批量简要信息、陌生人消息设置、粉丝/关注列表、关注统计、关注关系查询、签到统计、月度签到记录、拉黑列表、拉黑关系查询、后台用户查询的热点读规则
- `upload` 已补上传图片、上传音频、批量上传、文件地址查询、文件删除的方法级规则，`id-generator` 已补单个 Snowflake、批量 Snowflake、Segment ID 生成的方法级规则
