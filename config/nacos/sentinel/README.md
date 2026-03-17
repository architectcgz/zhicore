# Sentinel 规则配置

本目录包含 `zhicore-content` 服务的 Sentinel 规则配置文件。

## 文件说明

### 规则配置文件

1. **zhicore-content-flow-rules.json** - 流控规则
   - 控制每个资源的 QPS（每秒查询率）
   - 防止系统过载

2. **zhicore-content-degrade-rules.json** - 降级规则
   - 配置熔断策略
   - 当服务不稳定时自动降级

3. **zhicore-content-system-rules.json** - 系统规则
   - 系统级别的保护规则
   - 基于系统负载、CPU 使用率等指标

### 上传脚本

**upload-sentinel-rules.ps1** - PowerShell 上传脚本
- 自动将规则上传到 Nacos
- 支持自定义 Nacos 服务器地址和认证信息

## 使用方法

### 1. 上传规则到 Nacos

使用默认配置（localhost:8848）：

```powershell
.\upload-sentinel-rules.ps1
```

使用自定义配置：

```powershell
.\upload-sentinel-rules.ps1 -NacosServer "http://your-nacos:8848" -Username "nacos" -Password "nacos"
```

### 2. 验证规则

上传成功后，可以通过以下方式验证：

1. **Nacos 控制台**
   - 访问 http://localhost:8848/nacos
   - 进入"配置管理" -> "配置列表"
   - 查看 SENTINEL_RULES 组下的配置

2. **Sentinel 控制台**
   - 访问 http://localhost:8858
   - 查看 `zhicore-content` 服务的规则配置

## 规则详解

### 流控规则 (Flow Rules)

```json
{
  "resource": "content:getPostDetail", // 资源名称（对应 @SentinelResource 的 value）
  "limitApp": "default",           // 来源应用
  "grade": 1,                      // 限流阈值类型（0=线程数，1=QPS）
  "count": 100,                    // 限流阈值
  "strategy": 0,                   // 流控模式（0=直接，1=关联，2=链路）
  "controlBehavior": 0,            // 流控效果（0=快速失败，1=Warm Up，2=排队等待）
  "clusterMode": false             // 是否集群模式
}
```

**当前配置：**
- `content:getPostDetail`: 300 QPS
- `content:getPostList`: 200 QPS
- `content:getPostContent`: 150 QPS
- `content:getTagDetail`: 200 QPS
- `content:getHotTags`: 120 QPS

### 降级规则 (Degrade Rules)

```json
{
  "resource": "content:getPostDetail", // 资源名称
  "grade": 0,                       // 降级策略（0=慢调用比例，1=异常比例，2=异常数）
  "count": 1000,                    // 慢调用阈值（毫秒），超过此时间视为慢调用（grade=0 时生效）
  "timeWindow": 10,                 // 熔断时长（秒）
  "minRequestAmount": 5,            // 最小请求数
  "statIntervalMs": 1000,           // 统计时长（毫秒）
  "slowRatioThreshold": 0.5         // 慢调用比例阈值（0-1，grade=0 时生效）
}
```

**当前配置：**

- 当前未下发降级规则，配置内容为 `[]`
- 如需启用，请按资源名 `content:*` 或 URL 资源 `/api/v1/tags/hot` 增量配置

### 系统规则 (System Rules)

```json
{
  "resource": "system",             // 系统级别规则
  "highestSystemLoad": -1,          // 最大系统负载（-1=不限制）
  "avgRt": 3000,                    // 平均响应时间（毫秒）
  "maxThread": -1,                  // 最大并发线程数（-1=不限制）
  "qps": 1000,                      // 系统总 QPS
  "highestCpuUsage": 0.8            // 最大 CPU 使用率（0.8 = 80%）
}
```

**当前配置：**

- 当前未下发系统规则，配置内容为 `[]`

## 降级策略说明

### 当前策略

- 流控规则已持久化到 Nacos，对应 `zhicore-content-flow-rules`
- 降级和系统规则当前保持空数组，避免控制台和客户端持续报“source is empty”
- 运行时默认值与代码中的 `content.sentinel.*` 保持一致
- **deletePost**: 快速失败，返回错误信息

## 调整建议

### 生产环境

建议根据实际负载调整以下参数：

1. **流控规则**
   - 根据压测结果调整 QPS 阈值
   - 考虑使用 Warm Up 模式应对突发流量

2. **降级规则**
   - 根据 P99 响应时间调整慢调用阈值
   - 根据错误率调整异常比例阈值

3. **系统规则**
   - 根据服务器配置调整 CPU 使用率阈值
   - 根据业务需求调整系统总 QPS

### 监控告警

建议配置以下监控指标：
- Sentinel 规则触发次数
- 降级事件发生频率
- 熔断恢复时间
- 系统负载趋势

## 相关文档

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)
- [Sentinel 规则配置](https://sentinelguard.io/zh-cn/docs/flow-control.html)
- [项目迁移指南](../../../zhicore-content/SENTINEL_MIGRATION_GUIDE.md)
