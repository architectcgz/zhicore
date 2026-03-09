# Sentinel 本地规则与 Nacos 规则治理边界

日期：2026-03-09

## 目标

- 避免出现“代码一套、本地一套、Nacos 一套”的三套配置漂移。
- 明确谁负责默认基线，谁负责运行期覆盖。
- 让故障兜底、URL 归一化、资源命名、QPS 阈值分别归属到固定治理层。

## 当前现状

- 全仓默认基线仍以各模块代码内 `application.yml` 和 `*SentinelConfig` 为主。
- `content` 已在配置中接入 Sentinel Nacos datasource，覆盖 `flow / degrade / system` 三类规则。
- `search / ranking / notification / content / upload / id-generator / message` 已存在 URL 级或代表性 URL 级资源。
- `ApiResponseBlockExceptionHandler`、`SentinelResourceAspect`、Feign fallback 行为仍由代码统一提供，不应交给 Nacos 动态拼装。

## 决策结论

| 决策项 | 结论 |
| --- | --- |
| 本地规则是否作为默认基线 | 是。代码中的资源命名、默认 QPS、默认 Warm Up、默认开关是唯一基线 |
| Nacos 是否允许覆盖全部规则 | 否。Nacos 只允许覆盖已在代码中声明过的规则项，不允许任意新增资源名 |
| 发生冲突时以谁为准 | 同一资源、同一规则类型下，运行期以合法的 Nacos 规则为准；缺失、非法或回滚时退回本地基线 |
| 开发 / 测试 / 生产是否同一策略 | 不同环境使用同一治理原则，但覆盖权限不同：开发环境默认本地优先，测试环境允许临时覆盖，生产环境允许审核后覆盖 |

## 规则分层

### 1. 代码层必须负责的内容

- 资源名定义：例如 `SearchRoutes`、`RankingRoutes`、`ContentSentinelResources`。
- Web 层 block 返回格式：统一由 `ApiResponseBlockExceptionHandler` 返回 `429 + ApiResponse`。
- 方法级 block 处理语义：统一抛显式失败，不返回伪数据。
- UrlCleaner、BlockExceptionHandler、Sentinel 切面注册。
- Feign fallback / fallbackFactory 的失败语义。

### 2. Nacos 可覆盖的内容

- 已存在 `FlowRule` 的 `count`、`grade`、`controlBehavior`、`warmUpPeriodSec`。
- 已存在 `DegradeRule`、`SystemRule` 的阈值参数。
- 已存在资源的临时限流收紧或放宽。

### 3. Nacos 不应覆盖的内容

- 新资源名或未发版的接口。
- fallback 返回结构和错误码。
- `ApiResponseBlockExceptionHandler` 的 HTTP 状态码或响应格式。
- `UrlCleaner` 逻辑、路由归一化规则、`@EnableFeignClients` 注册策略。
- 是否启用 Sentinel 注解切面、是否精确注册 Feign client 这类结构性配置。

## 优先级规则

### 运行期优先级

1. 合法且已发布的 Nacos 规则
2. 代码内默认规则
3. Sentinel 默认空规则

### 回退规则

- Nacos 拉取失败、反序列化失败、规则字段缺失时，必须继续保留代码内默认规则。
- 禁止因为 Nacos 不可用而把接口放回“完全不受控”的状态。
- 新增资源时，必须先发代码，再发 Nacos 覆盖；不能反过来做。

## 环境策略

| 环境 | 推荐策略 |
| --- | --- |
| 开发环境 | 默认只使用代码基线；如需验证 Nacos，使用独立 namespace / group，不共享生产规则 |
| 测试环境 | 允许临时覆盖已存在资源，用于压测、回归、故障演练；覆盖规则必须带 TTL 或回滚说明 |
| 生产环境 | 允许在代码基线之上做审核后覆盖；临时放大或收紧都必须记录变更单、负责人和回滚窗口 |

## 变更流程

1. 代码先定义资源名、默认 QPS、block 行为和测试。
2. 文档同步更新 QPS 基线表，说明该值是经验值、压测值还是线上观测值。
3. 只有在代码基线已发布后，才允许往 Nacos 下发覆盖规则。
4. 覆盖规则必须注明模块、资源、变更原因、预期持续时间、回滚条件。
5. 变更结束后，若覆盖值应长期保留，必须回写到代码基线并删除临时规则。

## 审计与验收清单

- 是否只覆盖了代码里已经声明的资源。
- 是否保留了本地默认规则作为兜底。
- 是否有接口级回归证明 block 时返回 `429 + ApiResponse`。
- 是否在文档中记录了该默认值的来源或假设。
- 是否明确了该规则属于公共读、登录态读、后台查询还是外部代理。

## 推荐实施口径

- 代码负责“默认值、资源名、异常语义、回退行为”。
- Nacos 负责“运行期同资源阈值覆盖”。
- 任何跨层行为变更，都必须走代码发布，不应在 Nacos 里偷偷完成。
