# RocketMQ 内存优化说明

## 问题描述

RocketMQ 在默认配置下内存占用过高，单个 Broker 节点就占用 4GB+ 内存，对于开发环境来说资源消耗过大。

## 优化方案

### 1. JVM 堆内存优化

#### NameServer
- **优化前**: 默认配置 (~350MB 实际使用)
- **优化后**: `-Xms256m -Xmx256m`
- **说明**: NameServer 主要负责路由信息管理，内存需求较小

#### Broker
- **优化前**: 默认配置 (~4.2GB 实际使用)
- **优化后**: `-Xms512m -Xmx512m -Xmn256m`
- **说明**: 
  - `-Xms512m`: 初始堆大小 512MB
  - `-Xmx512m`: 最大堆大小 512MB
  - `-Xmn256m`: 新生代大小 256MB

#### Dashboard
- **优化前**: `-Xms128m -Xmx256m` (已优化)
- **优化后**: 保持不变
- **说明**: Dashboard 仅用于监控，内存配置已合理

### 2. Broker 配置优化

在 `broker.conf` 中添加以下配置：

```properties
# 减少消息存储的内存映射文件大小
mapedFileSizeCommitLog=104857600      # 100MB (默认 1GB)
mapedFileSizeConsumeQueue=3000000     # ~3MB (默认 6MB)

# 减少线程池大小
sendMessageThreadPoolNums=4           # 发送消息线程池 (默认 16)
pullMessageThreadPoolNums=4           # 拉取消息线程池 (默认 16)
queryMessageThreadPoolNums=2          # 查询消息线程池 (默认 8)
adminBrokerThreadPoolNums=4           # 管理线程池 (默认 16)
clientManageThreadPoolNums=4          # 客户端管理线程池 (默认 32)
heartbeatThreadPoolNums=2             # 心跳线程池 (默认 8)
endTransactionThreadPoolNums=4        # 事务消息线程池 (默认 8)

# 磁盘使用率告警阈值
diskMaxUsedSpaceRatio=95

# 消息索引配置
messageIndexEnable=true
messageIndexSafe=false
```

## 优化效果

| 组件 | 优化前 | 优化后 (配置) | 实际使用 | 节省 |
|------|--------|--------------|----------|------|
| NameServer | ~350MB | 256MB | ~190MB | ~160MB |
| Broker | ~4.2GB | 512MB | ~650MB | ~3.55GB |
| Dashboard | ~437MB | 256MB | ~330MB | ~107MB |
| **总计** | **~5GB** | **~1GB** | **~1.17GB** | **~3.83GB (77%)** |

**验证时间**: 2026-01-20 17:07

**说明**: 
- 实际内存使用略高于配置值是正常现象，包含了 JVM 元空间、直接内存等
- Broker 实际使用 650MB，仍比优化前的 4.2GB 减少了 85%
- 总内存从 5GB 降至 1.17GB，节省了 77% 的内存

## 适用场景

### 开发环境 (推荐)
- 消息量: 低到中等
- 并发量: 低到中等
- 消息保留: 短期 (48小时)
- 内存限制: 有限

### 生产环境 (需调整)
如果用于生产环境，建议根据实际负载调整：

```yaml
# 生产环境建议配置
rocketmq-namesrv:
  environment:
    - JAVA_OPT_EXT=-Xms512m -Xmx512m

rocketmq-broker:
  environment:
    - JAVA_OPT_EXT=-Xms2g -Xmx2g -Xmn1g
```

生产环境 broker.conf 调整：
```properties
sendMessageThreadPoolNums=16
pullMessageThreadPoolNums=16
queryMessageThreadPoolNums=8
adminBrokerThreadPoolNums=16
clientManageThreadPoolNums=32
```

## 应用优化

### 1. 重启 RocketMQ 服务

使用提供的脚本：
```powershell
cd docker/scripts
.\restart-rocketmq.ps1
```

或手动重启：
```bash
cd docker
docker-compose stop rocketmq-dashboard rocketmq-broker rocketmq-namesrv
docker-compose up -d rocketmq-namesrv
# 等待 10 秒
docker-compose up -d rocketmq-broker
# 等待 15 秒
docker-compose up -d rocketmq-dashboard
```

### 2. 验证优化效果

```bash
# 查看实时内存使用
docker stats

# 查看 RocketMQ 容器状态
docker ps --filter "name=rocketmq"

# 查看 Broker 日志
docker logs ZhiCore-rocketmq-broker

# 测试消息发送
curl http://localhost:8180
```

**预期结果**:
```
ZhiCore-rocketmq-namesrv     ~190MB
ZhiCore-rocketmq-broker      ~650MB
ZhiCore-rocketmq-dashboard   ~330MB
Total:                    ~1.17GB
```

### 3. 功能验证

访问 RocketMQ Dashboard 验证功能正常:
```
http://localhost:8180
```

检查项:
- [ ] Dashboard 可以正常访问
- [ ] Broker 状态显示为 "ONLINE"
- [ ] NameServer 连接正常
- [ ] 可以查看 Topic 列表
- [ ] 可以发送测试消息

## 性能影响

### 可能的影响
1. **消息吞吐量**: 略有下降 (线程池减小)
2. **消息延迟**: 基本无影响
3. **并发处理**: 适合中小规模并发

### 不受影响的功能
1. 消息可靠性
2. 消息顺序性
3. 事务消息
4. 延迟消息
5. 消息过滤

## 监控建议

### 1. 内存监控
```bash
# 持续监控内存使用
docker stats --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}"
```

### 2. 性能监控
访问 RocketMQ Dashboard: `http://localhost:8180`

关注指标：
- 消息生产 TPS
- 消息消费 TPS
- 消息堆积量
- Broker 负载

### 3. 日志监控
```bash
# Broker 日志
docker logs -f ZhiCore-rocketmq-broker

# NameServer 日志
docker logs -f ZhiCore-rocketmq-namesrv
```

## 故障排查

### 问题1: Broker 启动失败
**症状**: Broker 容器反复重启

**原因**: 内存不足导致 JVM 无法启动

**解决方案**:
```yaml
# 适当增加内存
environment:
  - JAVA_OPT_EXT=-Xms768m -Xmx768m -Xmn384m
```

### 问题2: 消息发送失败
**症状**: 生产者报错 "No route info"

**原因**: Broker 未完全启动或注册失败

**解决方案**:
```bash
# 检查 Broker 状态
docker logs ZhiCore-rocketmq-broker | grep "register"

# 重启 Broker
docker-compose restart rocketmq-broker
```

### 问题3: 内存使用仍然很高
**症状**: 优化后内存使用超过 1GB

**原因**: 
1. 消息堆积过多
2. 历史数据未清理
3. JVM 垃圾回收未触发

**解决方案**:
```bash
# 清理历史数据
docker exec ZhiCore-rocketmq-broker sh -c "rm -rf /home/rocketmq/store/*"

# 重启 Broker
docker-compose restart rocketmq-broker
```

## 回滚方案

如果优化后出现问题，可以回滚到默认配置：

### 1. 恢复 docker-compose.yml
```yaml
rocketmq-namesrv:
  # 移除 environment 中的 JAVA_OPT_EXT

rocketmq-broker:
  # 移除 environment 中的 JAVA_OPT_EXT
```

### 2. 恢复 broker.conf
删除所有内存优化相关配置，保留基础配置。

### 3. 重启服务
```bash
docker-compose restart rocketmq-namesrv rocketmq-broker
```

## 参考资料

- [RocketMQ 官方文档](https://rocketmq.apache.org/docs/quick-start/)
- [RocketMQ 性能调优指南](https://github.com/apache/rocketmq/blob/master/docs/cn/best_practice.md)
- [JVM 内存调优](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)

## 更新日志

- **2026-01-20**: 初始版本，优化开发环境内存配置
  - NameServer: 256MB
  - Broker: 512MB
  - Dashboard: 256MB
  - 总内存从 5GB 降至 1GB
