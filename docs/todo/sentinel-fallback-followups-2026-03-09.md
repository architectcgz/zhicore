# Sentinel / Fallback 收尾 TODO

日期：2026-03-09

## 完成情况

- [x] TODO 5 已完成：`upload` 启动类已移除裸 `@EnableFeignClients`
- [x] TODO 2 已完成：为 `content / upload / id-generator / message` 各补 1 个真实接口级 `429 + ApiResponse` 回归
- [x] TODO 3 已完成：已产出 [`docs/sentinel/sentinel-qps-baseline-2026-03-09.md`](/home/azhi/workspace/projects/zhicore-microservice/docs/sentinel/sentinel-qps-baseline-2026-03-09.md)
- [x] TODO 4 已完成：已产出 [`docs/sentinel/sentinel-rule-governance-2026-03-09.md`](/home/azhi/workspace/projects/zhicore-microservice/docs/sentinel/sentinel-rule-governance-2026-03-09.md)
- [x] TODO 1 已完成：本轮提交拆分为 `005715b refactor(upload): 删除启动类多余 Feign 开关`、`63b1635 test(sentinel): 补代表接口 URL 限流回归`、`455b444 docs(sentinel): 补 QPS 基线与治理边界`

## 背景

当前仓库已经完成以下主线改造：

- Feign 降级统一为显式失败，避免伪造成功或静默空数据
- Sentinel 规则从伪全局默认规则，收敛为模块内真实资源规则
- `@EnableFeignClients` 已统一改为 `clients = {...}` 精确注册
- `search`、`ranking`、`content`、`comment`、`user`、`message`、`notification`、`admin`、`upload`、`id-generator` 已补方法级热点资源

当前剩余工作不再是“有没有熔断”，而是“如何把这套治理真正收口并可长期维护”。

## TODO 1: 提交当前熔断与降级改造

目标：

- 将当前未提交的 Sentinel / fallback 改造整理为一组或多组清晰提交

建议拆分：

1. `common + fallback 基座`
2. `search/ranking/content/comment/user/message/notification/admin` 方法级 Sentinel
3. `upload/id-generator` 补齐
4. 文档更新

验收标准：

- `git status` 中本轮改动清晰可解释
- 每个提交主题单一
- 提交信息能反映模块和改动意图

## TODO 2: 补一层真正命中 block 的接口级回归

目标：

- 不只验证 `ConfigTest` 和 `HandlersTest`
- 要验证“接口在命中 Sentinel 后确实返回 `429 + ApiResponse`”

优先模块：

1. `content`
2. `upload`
3. `id-generator`
4. `message`

建议做法：

- 在测试中临时下发极低阈值规则
- 通过短时间内连续请求触发 block
- 断言 HTTP 状态码、业务码、错误消息

验收标准：

- 至少覆盖每个代表性模块 1 个真实接口
- 能证明 block handler 和 `ApiResponseBlockExceptionHandler` 真实生效

## TODO 3: 收敛各模块 QPS 默认值

目标：

- 将当前代码中的 QPS 默认值，从“工程基线”进一步收敛为“可上线基线”

建议按接口类型分层：

- 公共读接口
- 登录态读接口
- 后台查询接口
- 写接口
- 外部代理接口（如 `upload`、`id-generator`）

建议动作：

1. 导出当前各模块 `application.yml` 中的 Sentinel QPS
2. 汇总成一份基线表
3. 标记哪些值来自经验值，哪些值来自压测或线上观测
4. 最终统一口径

验收标准：

- 有一份跨模块 QPS 基线表
- 每个默认值都能说明来源或假设

## TODO 4: 明确 Nacos 规则和本地规则的治理边界

目标：

- 避免以后出现“代码一套、本地一套、Nacos 一套”的双重甚至三重配置漂移

需要决策的问题：

1. 本地规则是否作为默认基线
2. Nacos 是否允许覆盖全部规则，还是只允许覆盖部分规则
3. 发生冲突时以谁为准
4. 开发、测试、生产环境是否使用同一策略

建议方案：

- 本地代码维护默认基线
- Nacos 只用于运行期覆盖，且必须有明确优先级说明
- 在文档里写清楚“哪些规则可动态覆盖，哪些不建议”

验收标准：

- 有明确治理说明
- 至少一份文档说明本地规则与 Nacos 规则的优先关系

## TODO 5: 清理 `upload` 启动类中的多余 Feign 开关

现状：

- [UploadApplication.java](/home/azhi/workspace/projects/zhicore-microservice/zhicore-upload/src/main/java/com/zhicore/upload/UploadApplication.java) 仍保留裸 `@EnableFeignClients`
- 当前 `upload` 模块并没有本地 Feign 客户端需要注册

建议：

- 删除该注解
- 保持启动类最小化，避免后续误注册共享客户端

验收标准：

- 删除后 `upload` 模块编译和测试通过
- 启动类职责更清晰

## 执行顺序建议

1. TODO 5
2. TODO 2
3. TODO 3
4. TODO 4
5. TODO 1

原因：

- 先做 `upload` 启动类清理，成本最低
- 再补接口级 block 回归，先把真实行为锁住
- 再做 QPS 基线和治理边界，避免参数和配置继续漂移
- 最后再整理提交，能减少返工
