# Sentinel 部署指南

本文档介绍如何部署和配置 Sentinel 控制台，以及如何将 Blog Post Service 接入 Sentinel。

## 1. Sentinel 控制台部署

### 1.1 使用 Docker 部署（推荐）

```bash
docker run -d \
  --name sentinel-dashboard \
  -p 8858:8858 \
  bladex/sentinel-dashboard:latest
```

### 1.2 使用 JAR 包部署

下载 Sentinel 控制台 JAR 包：

```bash
wget https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar
```

启动控制台：

```bash
java -Dserver.port=8858 -jar sentinel-dashboard-1.8.6.jar
```

### 1.3 访问控制台

- 地址：http://localhost:8858
- 默认用户名：sentinel
- 默认密码：sentinel

## 2. 配置 Sentinel 规则到 Nacos

### 2.1 上传规则配置

进入规则配置目录：

```powershell
cd blog-microservice/config/nacos/sentinel
```

执行上传脚本：

```powershell
.\upload-sentinel-rules.ps1
```

使用自定义 Nacos 地址：

```powershell
.\upload-sentinel-rules.ps1 -NacosServer "http://your-nacos:8848" -Username "nacos" -Password "nacos"
```

### 2.2 验证规则上传

1. 访问 Nacos 控制台：http://localhost:8848/nacos
2. 进入"配置管理" -> "配置列表"
3. 查看 SENTINEL_RULES 组下的配置：
   - blog-post-flow-rules
   - blog-post-degrade-rules
   - blog-post-system-rules

## 3. 启动 Blog Post Service

### 3.1 配置环境变量

确保以下环境变量已配置：

```bash
# Sentinel 控制台地址
SENTINEL_DASHBOARD=localhost:8858

# Nacos 配置（用于规则持久化）
NACOS_SERVER_ADDR=localhost:8848
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
```

### 3.2 启动服务

```bash
cd blog-microservice/blog-post
mvn spring-boot:run
```

或使用 Docker：

```bash
docker-compose up blog-post
```

### 3.3 验证连接

1. 访问 Sentinel 控制台：http://localhost:8858
2. 等待服务启动并发送第一个请求
3. 在左侧菜单中应该能看到 "blog-post" 应用
4. 点击进入，查看实时监控数据

## 4. 规则配置说明

### 4.1 流控规则

流控规则限制每个资源的 QPS（每秒查询率）：

| 资源 | QPS 限制 | 说明 |
|------|---------|------|
| createPost | 100 | 创建文章 |
| getPostFullDetail | 500 | 获取文章详情 |
| getPostContent | 500 | 获取文章内容 |
| updatePost | 100 | 更新文章 |
| deletePost | 50 | 删除文章 |

### 4.2 降级规则

降级规则配置熔断策略：

| 资源 | 策略 | 阈值 | 熔断时长 |
|------|------|------|---------|
| getPostFullDetail | 慢调用比例 | 50% | 10秒 |
| getPostContent | 慢调用比例 | 50% | 10秒 |
| createPost | 异常比例 | 50% | 10秒 |
| updatePost | 异常比例 | 50% | 10秒 |

### 4.3 系统规则

系统规则配置系统级别的保护：

| 指标 | 阈值 | 说明 |
|------|------|------|
| 系统 QPS | 1000 | 系统总 QPS 限制 |
| 平均响应时间 | 3000ms | 平均响应时间阈值 |
| CPU 使用率 | 80% | 最大 CPU 使用率 |

## 5. 动态调整规则

### 5.1 通过 Sentinel 控制台

1. 访问 Sentinel 控制台
2. 选择 "blog-post" 应用
3. 进入"流控规则"、"降级规则"或"系统规则"
4. 点击"新增"或"编辑"按钮
5. 修改规则参数
6. 点击"保存"

**注意**：通过控制台修改的规则不会持久化到 Nacos，重启后会丢失。

### 5.2 通过 Nacos 配置中心

1. 访问 Nacos 控制台：http://localhost:8848/nacos
2. 进入"配置管理" -> "配置列表"
3. 找到对应的规则配置（SENTINEL_RULES 组）
4. 点击"编辑"
5. 修改 JSON 配置
6. 点击"发布"

**推荐**：通过 Nacos 修改规则，可以持久化并自动推送到所有服务实例。

## 6. 监控和告警

### 6.1 实时监控

在 Sentinel 控制台中可以查看：
- 实时 QPS
- 响应时间
- 异常数量
- 流控/降级事件

### 6.2 集成 Prometheus

Blog Post Service 已集成 Prometheus 指标：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

访问指标端点：http://localhost:8082/actuator/prometheus

### 6.3 配置 Grafana

1. 添加 Prometheus 数据源
2. 导入 Sentinel 监控面板
3. 配置告警规则

## 7. 降级策略说明

### 7.1 getPostFullDetail 降级

当 MongoDB 不可用或响应缓慢时：
- **触发条件**：50% 的请求响应时间超过阈值
- **降级行为**：仅返回 PostgreSQL 数据（不包含完整内容）
- **恢复时间**：10 秒后自动尝试恢复

### 7.2 其他操作降级

- **createPost**: 快速失败，返回错误信息
- **updatePost**: 快速失败，返回错误信息
- **deletePost**: 快速失败，返回错误信息

## 8. 故障排查

### 8.1 服务未出现在控制台

**原因**：
- Sentinel 控制台地址配置错误
- 服务未发送任何请求（Sentinel 懒加载）
- 网络连接问题

**解决方法**：
1. 检查 `application.yml` 中的 `spring.cloud.sentinel.transport.dashboard` 配置
2. 发送一个测试请求到服务
3. 检查防火墙和网络连接

### 8.2 规则不生效

**原因**：
- 规则未正确上传到 Nacos
- Nacos 数据源配置错误
- 规则格式错误

**解决方法**：
1. 检查 Nacos 中是否存在规则配置
2. 检查 `application.yml` 中的 Nacos 数据源配置
3. 验证规则 JSON 格式是否正确
4. 查看服务日志中的错误信息

### 8.3 降级不生效

**原因**：
- blockHandler 或 fallback 方法签名不正确
- 方法不在同一个类中
- 方法访问修饰符不是 public

**解决方法**：
1. 检查 blockHandler 和 fallback 方法签名
2. 确保方法在同一个类中
3. 确保方法是 public 的
4. 查看服务日志中的错误信息

## 9. 生产环境建议

### 9.1 高可用部署

- 部署多个 Sentinel 控制台实例
- 使用负载均衡器分发流量
- 配置健康检查

### 9.2 规则管理

- 所有规则通过 Nacos 管理，不要在控制台直接修改
- 规则变更需要经过审批流程
- 定期备份规则配置

### 9.3 监控告警

- 配置 Prometheus + Grafana 监控
- 设置关键指标告警（QPS、响应时间、错误率）
- 配置降级事件告警

### 9.4 性能调优

- 根据压测结果调整 QPS 阈值
- 根据 P99 响应时间调整慢调用阈值
- 根据业务需求调整熔断时长

## 10. 相关文档

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)
- [Sentinel 规则配置](../config/nacos/sentinel/README.md)
- [性能优化总结](../../blog-post/PERFORMANCE_OPTIMIZATION_SUMMARY.md)
- [Sentinel 迁移指南](../../blog-post/SENTINEL_MIGRATION_GUIDE.md)
