# ZhiCore 后端压测记录

本报告以第三轮最终验证结果为准，覆盖 2026-03-21 17:48 至 17:56 +08:00 的最新 Docker 运行态。第二轮结果保留为历史对照，用于说明 Sentinel 阈值放开前后的差异。

相关资料：
- 凭据与当前 token：`docs/test/credentials.md`
- 压测环境变量：`docs/test/.env.pressure`
- 第一轮原始 `hey` 输出：`docs/test/results/20260321/`
- 第二轮原始 `hey` 输出：`docs/test/results/20260321/round2/`
- 第三轮首次尝试输出：`docs/test/results/20260321/round3/`
- 第三轮最终有效输出：`docs/test/results/20260321/round3-final/`
- `k6` 阶梯压测输出：`docs/test/results/20260321/k6/`
- `k6` 固定并发拐点输出：`docs/test/results/20260321/k6-knee/`
- `id-generator` 定向优化复测输出：`docs/test/results/20260321/retest-id-generator/`
- `comment` 扩容脚本：`docs/test/sql/comment-load-seed-20260321.sql`

## 测试方法

- 工具：`hey`
- 中并发轮次：`-n 200 -c 10`
- 高并发轮次：`-n 500 -c 50`
- 冒烟验证：`curl`
- 本次只对“基线请求已经返回正常业务结果”的服务执行正式压测。

## 测试前提

- `admin` 直连服务不能只带 JWT；它的接口契约依赖 `X-User-Id` 和 `X-User-Roles: ADMIN`。
- `message` 本轮已修复对 `zhicore-user` 的直连兜底，压测时使用 `X-User-Id` 直打服务。
- `notification` 已修复配置前缀导致的启动失败，但运行时仍被数据库表缺失阻断。
- `search`、`upload` 当前仍属于环境/数据基线问题，不纳入正式吞吐结论。

## 结论摘要

- `user/content/comment/ranking/message/admin/id-generator` 在第三轮 `500 x 50` 与 `2000 x 200` 下均已消除 `429`，说明此前高并发异常的主因确实是 Sentinel 阈值和 Nacos 配置问题，而不是业务服务立即失稳。
- 第三轮真实容量探测中，`ranking`、`user`、`content` 吞吐最高；`comment`、`id-generator`、`admin` 没有报错，但尾延迟最先明显上升，是当前环境下更早接近容量边界的服务。
- 按 `k6` 固定并发、`p95 < 500ms` 的保守口径，当前 3 个更弱服务的容量线可先记为：`id-generator ≈ 175 VUs / 650 req/s`、`comment ≈ 150 VUs / 869 req/s`、`admin` 在定向修复后已抬升到 `≈ 300 VUs / 1012 req/s`。
- 第三轮中途定位到 `ranking/admin/id-generator` 的 Nacos YAML 存在重复顶层键，导致 `parse data from Nacos error`，Spring 只能回退到代码默认阈值；修复 YAML 结构并补充 `RefreshScopeRefreshedEvent` 兜底监听后，动态配置已在运行态验证通过。
- `gateway` 本轮未继续作为主结论对象，当前重点是各后端直连服务的真实容量下界；第二轮 `gateway` 结果可作为历史参考。

## 第三轮有效结果

### 运行态修复

- 所有 7 个目标服务已支持在 Nacos 动态刷新后重新装载 Sentinel `FlowRuleManager`。
- `ranking/admin/id-generator` 的 `config/nacos/*.yml` 已修复为合法 YAML，运行日志已确认它们在 2026-03-21 17:51 +08:00 左右切换到 `5000 qps / warm-up=0`。
- 第三轮首次尝试 `round3/` 中仍出现 `429` 的结果不再作为最终结论，原因是当时这 3 个服务尚未真正绑定到修复后的 Nacos 配置。

### 对照压测：`500 x 50`

| 服务 | 结果 | 平均延迟 | p95 | 吞吐 |
| --- | --- | ---: | ---: | ---: |
| user | `500/500` HTTP `200` | `35.3ms` | `81.6ms` | `1202.51 req/s` |
| content | `500/500` HTTP `200` | `34.4ms` | `78.7ms` | `1266.88 req/s` |
| comment | `500/500` HTTP `200` | `268.6ms` | `452.5ms` | `170.24 req/s` |
| ranking | `500/500` HTTP `200` | `38.3ms` | `94.8ms` | `1148.32 req/s` |
| id-generator | `500/500` HTTP `200` | `175.6ms` | `320.2ms` | `264.92 req/s` |
| message | `500/500` HTTP `200` | `81.2ms` | `186.5ms` | `506.43 req/s` |
| admin | `500/500` HTTP `200` | `149.8ms` | `293.4ms` | `311.38 req/s` |

### 容量探测：`2000 x 200`

| 服务 | 结果 | 平均延迟 | p95 | 吞吐 | 判断 |
| --- | --- | ---: | ---: | ---: | --- |
| user | `2000/2000` HTTP `200` | `110.3ms` | `269.5ms` | `1607.82 req/s` | 仍较稳 |
| content | `2000/2000` HTTP `200` | `155.3ms` | `386.7ms` | `1142.67 req/s` | 仍较稳 |
| comment | `2000/2000` HTTP `200` | `589.0ms` | `1155.8ms` | `308.72 req/s` | 率先进入高尾延迟区 |
| ranking | `2000/2000` HTTP `200` | `105.0ms` | `239.1ms` | `1693.93 req/s` | 当前最强之一 |
| id-generator | `2000/2000` HTTP `200` | `636.1ms` | `1213.5ms` | `290.36 req/s` | 率先进入高尾延迟区 |
| message | `2000/2000` HTTP `200` | `206.4ms` | `504.5ms` | `853.17 req/s` | 可继续承压 |
| admin | `2000/2000` HTTP `200` | `345.0ms` | `788.6ms` | `504.79 req/s` | 中等压力下可用 |

### 当前可给出的真实容量判断

- 在当前单机 Docker 环境下，7 个后端直连服务的真实容量下界至少已达到 `2000` 请求、`200` 并发且 `0%` HTTP 错误。
- 如果以“`p95 < 500ms`”作为较保守的可用阈值，当前更稳的服务是 `ranking`、`user`、`content`、`message`；`comment` 与 `id-generator` 已在该负载下超过 `1s` 级 `p95`，更像是当前环境里的第一批性能瓶颈。
- 本轮尚未把服务压到明确报错或资源耗尽，因此结论应表述为“容量下界”和“最早出现延迟拐点的服务”，而不是绝对最大承载值。

## k6 阶梯压测

### 方法

- 工具：`grafana/k6` Docker 镜像
- 脚本：`docs/test/k6/service_capacity.js`
- 阶段：`30 -> 60 -> 90 -> 120 VUs`
- 每阶段时长：`5s`
- 总时长：约 `35s/服务`
- 原始输出：`docs/test/results/20260321/k6/`

### 摘要结果

| 服务 | k6 吞吐 | 平均延迟 | p95 | p99 | 错误率 |
| --- | ---: | ---: | ---: | ---: | ---: |
| ranking | `2853.53 req/s` | `20.98ms` | `59.77ms` | `79.88ms` | `0.00%` |
| user | `2066.55 req/s` | `29.36ms` | `83.02ms` | `107.82ms` | `0.00%` |
| content | `1761.16 req/s` | `35.74ms` | `99.87ms` | `153.78ms` | `0.00%` |
| message | `1592.39 req/s` | `38.49ms` | `110.22ms` | `150.24ms` | `0.00%` |
| admin | `976.35 req/s` | `65.17ms` | `134.90ms` | `228.68ms` | `0.00%` |
| comment | `713.88 req/s` | `87.41ms` | `176.53ms` | `313.51ms` | `0.00%` |
| id-generator | `591.68 req/s` | `125.18ms` | `281.55ms` | `444.43ms` | `0.00%` |

### k6 视角下的判断

- 在持续阶梯加压而非瞬时冲击下，7 个服务都保持 `0%` 错误，说明当前运行态已经具备稳定承压能力。
- `ranking`、`user`、`content`、`message` 组成第一梯队，其中 `ranking` 的持续吞吐最高，`p95` 也最低。
- `id-generator` 的持续压测结果已明显改善，虽然绝对吞吐仍低于第一梯队，但 `p99` 已收敛到 `500ms` 以内，不再表现为突出的高尾延迟单点。
- `comment` 与 `admin` 在持续压测下可用，但相比第一梯队已明显更早进入延迟抬升区。

## k6 固定并发拐点定位

### 方法

- 工具：`grafana/k6` Docker 镜像
- 脚本：`docs/test/k6/service_fixed_vus.js`
- 持续时间：`15s/点`
- 思考时间：`0ms`
- 判断口径：先以 `p95 < 500ms` 作为保守可用阈值，辅助观察 `p99` 和吞吐是否开始明显抖动
- 原始输出：`docs/test/results/20260321/k6-knee/`

### 结果摘要

| 服务 | 关键观测点 | 结论 |
| --- | --- | --- |
| id-generator | 历史结果：`100 VUs: 368.62 req/s, p95 469.93ms, p99 534.48ms`；`120 VUs: 311.57 req/s, p95 744.68ms, p99 1.50s`；`150 VUs: 344.86 req/s, p95 970.94ms, p99 1.57s`。优化后复测：`100 VUs: 534.87 req/s, p95 379.37ms, p99 492.66ms`；`120 VUs: 622.20 req/s, p95 329.84ms, p99 402.00ms`；`150 VUs: 669.64 req/s, p95 405.27ms, p99 496.38ms`；`175 VUs: 650.18 req/s, p95 483.31ms, p99 621.41ms`；`180 VUs: 577.12 req/s, p95 550.66ms, p99 1.72s`；`190 VUs: 613.05 req/s, p95 546.97ms, p99 1.59s`；`200 VUs: 681.86 req/s, p95 526.83ms, p99 593.30ms` | 旧结论中的 `100~120 VUs` 拐点已被抬高；`175 VUs` 仍满足 `p95 < 500ms`，而 `180 VUs` 起已稳定越线，当前保守容量线可收敛到 `175 VUs` 左右 |
| comment | `150 VUs: 868.97 req/s, p95 310.65ms, p99 418.34ms`；`175 VUs: 650.95 req/s, p95 531.39ms, p99 1.40s`；`300 VUs: 665.52 req/s, p95 1.03s, p99 1.96s` | 保守拐点在 `150~175 VUs`；`175 VUs` 已越过 `500ms` 线，`300 VUs` 起进入明显失稳区 |
| admin | 历史结果：`200 VUs: 836.81 req/s, p95 497.78ms, p99 1.33s`；`225 VUs: 824.37 req/s, p95 540.88ms, p99 1.37s`；`300 VUs: 901.35 req/s, p95 757.19ms, p99 1.39s` | 历史保守拐点在 `200~225 VUs`；后续定向修复后已重新上探，见下文 `admin` 复测 |

### 固定并发视角下的判断

- 这 3 个服务在本轮固定并发压测中仍然保持 `0.00%` HTTP 错误，当前失稳特征是“延迟先恶化”，而不是“先报错再失稳”。
- 如果以 `p95 < 500ms` 作为当前环境下的保守容量线，建议先按 `id-generator=175 VUs`、`comment=150 VUs` 估算；`admin` 在定向修复后的保守容量线可上调到 `300 VUs`。
- `id-generator` 的旧瓶颈已明显缓解，新拐点不再位于历史的 `100~120 VUs` 区间，而是上移到了 `175~180 VUs` 附近。

## id-generator 定向优化复测

### 本轮改动

- 在 `zhicore-id-generator` 代理层新增 Snowflake 预取缓存，单个发号请求优先消费本地队列，避免高并发下频繁同步打到下游 `IdGeneratorClient`。
- 新增可配置的缓存参数，当前默认值为：`capacity=512`、`refill-threshold=128`、`prefetch-batch-size=256`。
- 保留原有本地 Snowflake 兜底生成逻辑，避免下游临时失败时直接把抖动暴露给请求侧。

### 根因确认

- 历史瓶颈不在 HTTP 层，而在单次 `/api/v1/id/snowflake` 请求对下游批量取号能力利用不足。
- 下游 `BufferedIdGeneratorClient` 自带缓冲，但其 `batchFetchSize=50` 偏小；高并发时代理层会频繁同步等待下游补号，导致吞吐上不去、`p95/p99` 快速恶化。
- 本次优化的核心是把“每次请求都可能触发远程补号”改成“少量请求触发批量预取，其余请求直接命中本地缓存”。

### 复测结果

| 场景 | 吞吐 | p95 | p99 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| 阶梯 `30 -> 60 -> 90 -> 120 VUs` | `591.68 req/s` | `281.55ms` | `444.43ms` | `0.00%` |
| `100 VUs` | `534.87 req/s` | `379.37ms` | `492.66ms` | `0.00%` |
| `120 VUs` | `622.20 req/s` | `329.84ms` | `402.00ms` | `0.00%` |
| `150 VUs` | `669.64 req/s` | `405.27ms` | `496.38ms` | `0.00%` |
| `175 VUs` | `650.18 req/s` | `483.31ms` | `621.41ms` | `0.00%` |
| `180 VUs` | `577.12 req/s` | `550.66ms` | `1.72s` | `0.00%` |
| `190 VUs` | `613.05 req/s` | `546.97ms` | `1.59s` | `0.00%` |
| `200 VUs` | `681.86 req/s` | `526.83ms` | `593.30ms` | `0.00%` |

### 对比历史结果

- 阶梯压测吞吐从 `347.61 req/s` 提升到 `591.68 req/s`，增幅约 `70.2%`；`p95` 从 `346.92ms` 降到 `281.55ms`，`p99` 从 `602.48ms` 降到 `444.43ms`。
- `100 VUs` 吞吐从 `368.62 req/s` 提升到 `534.87 req/s`，增幅约 `45.1%`；`p95` 从 `469.93ms` 降到 `379.37ms`。
- `120 VUs` 吞吐从 `311.57 req/s` 提升到 `622.20 req/s`，增幅约 `99.7%`；`p95` 从 `744.68ms` 降到 `329.84ms`；`p99` 从 `1.50s` 降到 `402.00ms`。
- `150 VUs` 吞吐从 `344.86 req/s` 提升到 `669.64 req/s`，增幅约 `94.2%`；`p95` 从 `970.94ms` 降到 `405.27ms`；`p99` 从 `1.57s` 降到 `496.38ms`。
- 继续上探后，`175 VUs` 仍保持 `p95 483.31ms`；到 `180/190/200 VUs` 时，`p95` 分别上升到 `550.66ms / 546.97ms / 526.83ms`，说明新拐点已进入 `175~180 VUs` 区间。

### 复测判断

- 旧瓶颈已经被实质性消除，`120 VUs 744ms` 这组历史数据不再代表当前运行态。
- 以 `p95 < 500ms` 的保守口径看，`id-generator` 的容量线已从历史的 `100 VUs` 附近，抬升到当前的 `175 VUs` 左右。
- `180 VUs` 起 `p95` 和 `p99` 都开始明显恶化，因此新的实用拐点可先按 `175~180 VUs` 理解。

### `180 VUs` 重复验证

| 轮次 | 吞吐 | p95 | p99 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| `run-1` | `577.12 req/s` | `550.66ms` | `1.72s` | `0.00%` |
| `run-2` | `559.73 req/s` | `593.21ms` | `1.62s` | `0.00%` |
| `run-3` | `639.49 req/s` | `515.38ms` | `600.82ms` | `0.00%` |

- 3 轮 `180 VUs` 的 `p95` 分别为 `550.66ms / 593.21ms / 515.38ms`，均高于 `500ms`，说明这档负载已稳定越过保守容量线。
- 3 轮 `180 VUs` 的平均吞吐约 `592.11 req/s`，平均 `p95` 约 `553.08ms`，平均 `p99` 约 `1.31s`。
- 因此，`180 VUs` 不应视为“偶发抖动但可接受”，而应归类为“稳定进入高尾延迟区”。

## comment 定向优化复测

### 本轮改动

- 查询链已去掉顶级评论列表中的重复 `batchGetStats`，直接复用主查询已关联的统计值。
- 热门回复预加载已从“每个根评论 1 次查询”改为“单次批量查询 + 单次批量用户补全”，避免列表页 N+1。
- 已关闭 `comment` 服务默认 MyBatis `StdOutImpl`，运行态日志不再输出每条 SQL 文本。
- 已补充更贴合查询模式的复合索引，并提供可重复执行的扩容脚本：`docs/test/sql/comment-load-seed-20260321.sql`。

### 扩容后数据规模

- 目标文章 `189000000000000101`
- 顶级评论：`122`
- 回复：`361`

### 复测结果

| 场景 | 吞吐 | p95 | p99 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| `150 VUs` | `481.97 req/s` | `632.41ms` | `1.56s` | `0.00%` |
| `175 VUs` | `592.42 req/s` | `587.52ms` | `1.45s` | `0.00%` |
| `200 VUs` | `588.31 req/s` | `668.77ms` | `826.77ms` | `0.00%` |

### 复测判断

- 结构性热点已经修掉：运行态日志中不再出现 MyBatis SQL stdout，`comment` 读链路也不再对每个根评论单独查询热门回复。
- 在更接近真实规模的数据集下，`comment` 仍然表现为“延迟先失稳、错误率不升高”，说明当前瓶颈已从明显的应用层 N+1，收敛到更常规的列表拼装与数据量放大成本。
- 以当前扩容后的数据集看，`comment` 的保守容量线仍应按 `150 VUs` 以下理解；如果要继续上探，需要进一步优化列表缓存、用户信息聚合和热度排序链路。

## admin 定向优化复测

### 本轮改动

- `zhicore-user` 的管理员用户列表查询已移除角色装配 N+1。
- `zhicore-user` 的 `findByConditions()` 改为先批量取用户，再一次性调用 `roleRepository.findByUserIds(...)` 装配角色。
- `zhicore-admin` 与 `zhicore-user` 的默认日志级别已从 `DEBUG` 收回，避免压测期间输出 Feign 调用明细和 MyBatis SQL DEBUG。
- `zhicore-user` 的 `AdminUserQueryController` 已不再对每次管理员列表请求输出 `INFO` 日志。
- `zhicore-admin.yml` 的 Nacos 数据库名已修正为 `zhicore_admin`，避免重建后因 `ZhiCore_admin` 大小写错误导致健康检查持续失败。

### 根因确认

- `admin` 的压测接口 `/admin/users?page=1&size=5` 本身很薄，真实热点在下游 `zhicore-user` 的管理员查询。
- 单次运行态证据显示，修复前一次请求会触发：
  - `1` 次用户分页查询
  - `5` 次 `selectByUserId` 角色查询
  - `1` 次总数统计
- 这属于标准的 `1 + N + 1` 读放大；同时 `admin/user` 两侧的请求级日志和 SQL DEBUG 进一步抬高了高并发尾延迟。

### 复测结果

| 场景 | 吞吐 | p95 | p99 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| `200 VUs` | `940.50 req/s` | `404.01ms` | `1.08s` | `0.00%` |
| `225 VUs` | `985.10 req/s` | `401.72ms` | `550.61ms` | `0.00%` |
| `300 VUs` | `1012.26 req/s` | `478.37ms` | `614.94ms` | `0.00%` |
| `350 VUs` | `907.78 req/s` | `666.44ms` | `1.58s` | `0.00%` |
| `400 VUs` | `978.45 req/s` | `606.88ms` | `761.52ms` | `0.00%` |

原始输出补充：

- `docs/test/results/20260321/k6-knee/admin-vus350-after-fix.txt`

### 复测判断

- `admin` 的结构性热点已从“每页每用户一次角色查询”收敛为“分页查询 + 批量角色查询 + count 查询”。
- 这轮修复后，`admin` 在 `200/225/300 VUs` 下都保持 `0%` 错误，且 `p95` 已压回 `500ms` 以内。
- `350/400 VUs` 开始重新越过 `500ms` 线，但仍未出现 HTTP 错误，说明新失稳模式依旧是“延迟先恶化”。
- 以当前环境看，`admin` 的保守容量线可从历史的 `200~225 VUs` 上调到 `300 VUs` 左右。

## 第二轮历史对照（限流未放开前）

| 服务 | 压测接口 | 轮次 | 结果 | 平均延迟 | p95 | 吞吐 |
| --- | --- | --- | --- | ---: | ---: | ---: |
| gateway | `GET /api/v1/posts/cursor?limit=20` | `200 x 10` | `200/200` HTTP `200` | `35.6ms` | `63.4ms` | `272.32 req/s` |
| gateway | `GET /api/v1/posts/cursor?limit=20` | `500 x 50` | `500/500` HTTP `200` | `170.3ms` | `315.0ms` | `281.91 req/s` |
| user | `GET /api/v1/users/189000000000000002` | `200 x 10` | `83` 个 `200`，`117` 个 `429` | `11.9ms` | `42.8ms` | `796.09 req/s` |
| user | `GET /api/v1/users/189000000000000002` | `500 x 50` | `500` 个 `429` | `23.3ms` | `54.1ms` | `1829.87 req/s` |
| content | `GET /api/v1/posts/189000000000000101` | `200 x 10` | `100` 个 `200`，`100` 个 `429` | `7.7ms` | `31.0ms` | `1210.15 req/s` |
| content | `GET /api/v1/posts/189000000000000101` | `500 x 50` | `4` 个 `200`，`496` 个 `429` | `24.7ms` | `57.0ms` | `1751.43 req/s` |
| comment | `GET /api/v1/comments/post/189000000000000101/page?page=0&size=20&sort=TIME` | `200 x 10` | `50` 个 `200`，`150` 个 `429` | `27.7ms` | `111.6ms` | `355.71 req/s` |
| comment | `GET /api/v1/comments/post/189000000000000101/page?page=0&size=20&sort=TIME` | `500 x 50` | `500` 个 `429` | `25.8ms` | `58.0ms` | `1609.35 req/s` |
| ranking | `GET /api/v1/ranking/posts/hot?page=0&size=20` | `200 x 10` | `200/200` HTTP `200` | `5.4ms` | `9.8ms` | `1589.09 req/s` |
| ranking | `GET /api/v1/ranking/posts/hot?page=0&size=20` | `500 x 50` | `133` 个 `200`，`367` 个 `429` | `22.3ms` | `50.3ms` | `1920.51 req/s` |
| id-generator | `GET /api/v1/id/snowflake` | `200 x 10` | `166` 个 `200`，`34` 个 `429` | `29.0ms` | `57.8ms` | `339.03 req/s` |
| id-generator | `GET /api/v1/id/snowflake` | `500 x 50` | `56` 个 `200`，`444` 个 `429` | `18.1ms` | `152.5ms` | `2152.71 req/s` |
| admin | `GET /admin/users?page=1&size=5` | `200 x 10` | `33` 个 `200`，`167` 个 `429` | `11.7ms` | `35.8ms` | `822.00 req/s` |
| admin | `GET /admin/users?page=1&size=5` | `500 x 50` | `500` 个 `429` | `24.9ms` | `61.0ms` | `1718.64 req/s` |
| message | `GET /api/v1/conversations?limit=5` | `200 x 10` | `69` 个 `200`，`131` 个 `429` | `14.2ms` | `40.4ms` | `677.53 req/s` |
| message | `GET /api/v1/conversations?limit=5` | `500 x 50` | `500` 个 `429` | `30.8ms` | `76.0ms` | `1472.11 req/s` |

## 阻断项与失败服务

| 服务 | 验证接口 | 现象 | 根因判断 |
| --- | --- | --- | --- |
| search | `GET /api/v1/search/posts?keyword=ZhiCore&page=0&size=5` | HTTP `500` | Elasticsearch 日志报 `index_not_found_exception`，缺少 `posts` 索引 |
| notification | `GET /api/v1/notifications?page=0&size=5` | HTTP `500` | 服务已健康启动，但 PostgreSQL 日志报 `relation "notifications" does not exist`，通知表未初始化 |
| upload | `GET /api/v1/upload/file/test/url` | HTTP `500` | `file-service` token 已注入，但下游 `file-service-app` 仍不可解析/不可连接 |

## 本轮修复与验证

- 已修复 `message` 的 `zhicore-user` 直连兜底；容器内基线请求已从 `code=1004` 恢复为正常业务返回。
- 已修复 `notification` 的 `@ConfigurationProperties` 前缀非法问题；容器已从反复重启恢复为健康启动。
- `notification` 当前剩余阻断已收敛到数据库 schema，而不是应用启动配置。
- `id-generator` 基线请求恢复成功，`GET /api/v1/id/snowflake` 当前可返回正常雪花 ID。
- 已为 `user/content/comment/ranking/admin/message/id-generator` 增加 `RefreshScopeRefreshedEvent` 兜底监听，解决 `EnvironmentChangeEvent` 在部分服务里 `changed keys` 为空时不重载 Sentinel 规则的问题。
- 已修复 `ranking/admin/id-generator` 的 Nacos YAML 重复顶层键问题；修复后运行日志不再出现该 3 个 dataId 的解析失败。

## 建议修复顺序

1. 先补齐 `notification` 库表初始化，至少创建 `notifications` 相关 schema。
2. 初始化 `search` 依赖的 Elasticsearch `posts` 索引或补一次重建索引任务。
3. 补齐 `upload` 的下游 `file-service-app` 部署或网络连通性。
4. 如果要继续逼近绝对上限，下一轮应改用 `k6` 或 `wrk2` 做持续压测，并同时采集容器 CPU、内存、Redis、PostgreSQL、Elasticsearch 指标。
5. 如果要给出更严格的“生产可用容量”，建议按 SLA 先定义阈值，例如 `p95 < 300ms` 或 `p95 < 500ms`，再用阶梯加压法找各服务的拐点。
