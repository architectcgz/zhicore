# 压力测试指南

本目录包含博客微服务系统的压力测试配置和脚本。

## 目录结构

```
load/
├── jmeter/
│   └── ZhiCore-load-test.jmx      # JMeter 测试计划
├── scripts/
│   └── run-load-test.ps1       # PowerShell 运行脚本
├── results/                     # 测试结果目录（自动创建）
└── README.md                    # 本文档
```

## 前置条件

### 1. 安装 Apache JMeter

下载并安装 Apache JMeter 5.6 或更高版本：

- 官方下载: https://jmeter.apache.org/download_jmeter.cgi
- 解压到本地目录，例如: `C:\apache-jmeter-5.6.3`

### 2. 准备测试环境

确保以下服务已启动并可访问：

- API 网关 (localhost:8000)
- 文章服务 (localhost:8082)
- 评论服务 (localhost:8083)
- 搜索服务 (localhost:8086)
- 通知服务 (localhost:8086)

### 3. 准备测试数据

在运行压力测试前，需要准备以下测试数据：

1. **测试用户**: 注册一个测试用户并获取 Access Token
2. **测试文章**: 创建至少一篇文章，记录文章 ID
3. **测试评论**: 创建至少一条评论，记录评论 ID

## 测试场景

本测试计划包含 8 个压力测试场景：

| 场景ID | 接口 | 并发用户 | 持续时间 | P99目标 | QPS目标 |
|--------|------|---------|---------|---------|---------|
| LOAD-001 | GET /api/v1/posts/{id} | 500 | 5分钟 | <100ms | >1000 |
| LOAD-002 | GET /api/v1/posts | 300 | 5分钟 | <200ms | >500 |
| LOAD-003 | POST /api/v1/posts/{id}/like | 500 | 5分钟 | <100ms | >2000 |
| LOAD-004 | GET /api/v1/search | 200 | 5分钟 | <300ms | >200 |
| LOAD-005 | GET /api/notifications | 300 | 5分钟 | <200ms | >500 |
| LOAD-006 | GET /api/comments/post/{postId} | 400 | 5分钟 | <150ms | >800 |
| LOAD-007 | POST /api/comments | 300 | 5分钟 | <200ms | >500 |
| LOAD-008 | POST /api/comments/{id}/like | 400 | 5分钟 | <100ms | >1500 |

## 使用方法

### 方式一：使用 PowerShell 脚本（推荐）

#### 1. 基本用法

```powershell
cd tests/load/scripts

# 运行所有测试场景
.\run-load-test.ps1 `
    -JMeterPath "C:\apache-jmeter-5.6.3\bin\jmeter.bat" `
    -BaseUrl "http://localhost:8000" `
    -TestPostId "1" `
    -TestCommentId "1" `
    -AccessToken "your_access_token_here"
```

#### 2. GUI 模式（用于测试开发）

```powershell
.\run-load-test.ps1 `
    -JMeterPath "C:\apache-jmeter-5.6.3\bin\jmeter.bat" `
    -GuiMode
```

#### 3. 参数说明

| 参数 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `-JMeterPath` | JMeter 可执行文件路径 | `C:\apache-jmeter\bin\jmeter.bat` | 是 |
| `-TestPlan` | 测试计划文件路径 | `../../jmeter/ZhiCore-load-test.jmx` | 否 |
| `-ResultsDir` | 结果输出目录 | `../../results/load` | 否 |
| `-BaseUrl` | API 网关地址 | `http://localhost:8000` | 否 |
| `-TestPostId` | 测试文章 ID | `1` | 否 |
| `-TestCommentId` | 测试评论 ID | `1` | 否 |
| `-AccessToken` | 访问令牌 | 空 | 是* |
| `-GuiMode` | 是否使用 GUI 模式 | `false` | 否 |
| `-Scenario` | 测试场景（预留） | `all` | 否 |

*注：部分测试场景需要认证，建议提供 AccessToken

### 方式二：直接使用 JMeter

#### 1. GUI 模式

```bash
# Windows
C:\apache-jmeter-5.6.3\bin\jmeter.bat -t tests/load/jmeter/ZhiCore-load-test.jmx

# Linux/Mac
/path/to/apache-jmeter/bin/jmeter.sh -t tests/load/jmeter/ZhiCore-load-test.jmx
```

#### 2. 命令行模式

```bash
# Windows
C:\apache-jmeter-5.6.3\bin\jmeter.bat -n ^
    -t tests/load/jmeter/ZhiCore-load-test.jmx ^
    -l tests/load/results/results.jtl ^
    -e -o tests/load/results/report ^
    -JBASE_URL=http://localhost:8000 ^
    -JTEST_POST_ID=1 ^
    -JTEST_COMMENT_ID=1 ^
    -JACCESS_TOKEN=your_token_here

# Linux/Mac
/path/to/apache-jmeter/bin/jmeter.sh -n \
    -t tests/load/jmeter/ZhiCore-load-test.jmx \
    -l tests/load/results/results.jtl \
    -e -o tests/load/results/report \
    -JBASE_URL=http://localhost:8000 \
    -JTEST_POST_ID=1 \
    -JTEST_COMMENT_ID=1 \
    -JACCESS_TOKEN=your_token_here
```

## 测试结果

### 结果文件

测试完成后，会在 `tests/load/results/` 目录下生成：

1. **JTL 文件**: `load-test-results-YYYYMMDD_HHMMSS.jtl`
   - 原始测试结果数据
   - 可用于后续分析

2. **HTML 报告**: `report-YYYYMMDD_HHMMSS/index.html`
   - 可视化测试报告
   - 包含响应时间、吞吐量、错误率等指标

### 关键指标

在 HTML 报告中关注以下指标：

1. **响应时间**
   - Average: 平均响应时间
   - Median: 中位数响应时间
   - 90th pct: 90% 请求的响应时间
   - 95th pct: 95% 请求的响应时间
   - 99th pct: 99% 请求的响应时间（P99）
   - Min/Max: 最小/最大响应时间

2. **吞吐量**
   - Throughput: 每秒请求数（QPS）
   - Received KB/sec: 接收数据速率
   - Sent KB/sec: 发送数据速率

3. **错误率**
   - Error %: 错误百分比
   - 应保持在 1% 以下

4. **并发**
   - Active Threads: 活跃线程数
   - 应与配置的并发用户数一致

## 性能目标

根据设计文档，各场景的性能目标如下：

| 场景 | P99 响应时间 | QPS | 错误率 |
|------|-------------|-----|--------|
| 文章详情 | <100ms | >1000 | <1% |
| 文章列表 | <200ms | >500 | <1% |
| 文章点赞 | <100ms | >2000 | <1% |
| 搜索 | <300ms | >200 | <1% |
| 通知列表 | <200ms | >500 | <1% |
| 评论列表 | <150ms | >800 | <1% |
| 创建评论 | <200ms | >500 | <1% |
| 评论点赞 | <100ms | >1500 | <1% |

## 故障排查

### 1. JMeter 启动失败

**问题**: 找不到 JMeter 可执行文件

**解决**:
- 检查 JMeter 安装路径是否正确
- 确保使用正确的参数 `-JMeterPath`

### 2. 连接超时

**问题**: 测试过程中出现大量连接超时

**解决**:
- 检查服务是否正常运行
- 检查网络连接
- 降低并发用户数

### 3. 认证失败

**问题**: 401 Unauthorized 错误

**解决**:
- 检查 Access Token 是否有效
- 确认 Token 未过期
- 重新登录获取新 Token

### 4. 内存不足

**问题**: JMeter 运行时内存不足

**解决**:
- 增加 JMeter 堆内存: 编辑 `jmeter.bat` 或 `jmeter.sh`
- 修改 `HEAP` 参数，例如: `-Xms1g -Xmx4g`

### 5. 结果文件过大

**问题**: JTL 文件过大，难以处理

**解决**:
- 减少测试持续时间
- 减少并发用户数
- 只保存必要的数据字段

## 最佳实践

1. **逐步增加负载**
   - 先从小并发开始测试
   - 逐步增加到目标并发数
   - 观察系统响应

2. **监控系统资源**
   - 同时监控 CPU、内存、网络使用率
   - 使用 Prometheus + Grafana 监控
   - 关注数据库连接池状态

3. **多次测试取平均值**
   - 单次测试结果可能不稳定
   - 建议至少运行 3 次
   - 取平均值作为最终结果

4. **隔离测试环境**
   - 使用独立的测试环境
   - 避免与开发环境混用
   - 确保测试数据一致性

5. **定期清理测试数据**
   - 压力测试会产生大量数据
   - 定期清理测试数据库
   - 避免影响后续测试

## 参考资料

- [Apache JMeter 官方文档](https://jmeter.apache.org/usermanual/index.html)
- [JMeter 最佳实践](https://jmeter.apache.org/usermanual/best-practices.html)
- [性能测试指南](https://jmeter.apache.org/usermanual/component_reference.html)

## 联系方式

如有问题，请联系测试团队或查看项目文档。
