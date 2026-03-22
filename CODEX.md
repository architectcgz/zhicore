# CODEX

## 缓存分层约定

- `application` 层负责缓存策略、缓存端口、回源时机、热点锁和降级逻辑。
- `infrastructure` 层负责 Redis 实现、序列化、Key 设计、TTL，以及在确有需要时承载兼容策略。
- `domain` 层对象可以作为应用层返回值，但不要直接作为 Redis 落盘对象。

## 缓存对象约定

- Redis 中落盘的对象必须是稳定的缓存快照对象，例如 `*CacheSnapshot`。
- 缓存快照只保留真正需要持久化的字段，不包含派生 getter、计算属性、运行时状态或框架字段。
- 如果应用层需要返回领域对象，由缓存实现负责在快照对象和领域对象之间转换。

## 本次事故记录

- 事故时间：2026-03-22
- 场景：`comment detail cache`
- 原因：直接把 `Comment` 领域对象写入 Redis，Jackson 把 `isTopLevel()`、`isDeleted()`、`isReply()` 这些派生 getter 也序列化进缓存。
- 后果：读取旧缓存值时，Redis 反序列化因为 `topLevel/deleted/reply` 未被构造函数接收而报错，缓存每次都回源数据库。
- 修复：新增 `CommentDetailCacheSnapshot` 作为 Redis 落盘对象，读取时转换回 `Comment`；开发阶段直接把 detail key 升级到 `v2`，整体失效旧缓存，不加兼容分支。

## 以后不要再犯

- 不要把 rich domain model 直接塞进 Redis、MQ、本地文件或跨服务 JSON。
- 新增缓存前，先明确缓存语义：缓存的是领域实体快照、查询视图还是聚合结果，不同语义使用不同对象。
- 开发阶段如果缓存 schema 已变且未对外承诺兼容，优先升级 key 版本或直接清理缓存，不要为了历史脏数据把兼容逻辑带进正式代码。
- 序列化对象必须有专门的回归测试，至少覆盖：
  - 新写入值不会带入派生字段
  - 缓存 schema 变更时，新 key 或清理策略已经生效
- 压测缓存优化时，不能只看 `2xx`、平均耗时和 P95。
- 必须同时检查：
  - Redis 实际 key 是否写入
  - TTL 是否符合预期
  - 服务日志里是否仍有 cache read failed / fallback to database
  - Grafana/Prometheus 上数据库或服务 CPU 是否异常抖动

## 变更检查清单

- 改缓存实现时，先写失败测试，再改代码。
- 如果缓存对象是给外部系统持久化的，优先新建 `snapshot` / `dto` / `view`，不要复用领域对象。
- 如果确实需要兼容历史脏数据，先证明这是业务要求，不要默认把开发期脏缓存兼容到正式代码里。
