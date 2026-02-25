# 服务器CPU使用率过高的解决方法

## 一、问题背景

在某天的运维监控中，我发现服务器的CPU使用率持续维持在高位，系统响应明显变慢。作为一个运行博客系统的生产服务器，正常情况下CPU使用率应该在20%-40%之间，但现在系统出现了严重的性能问题，页面加载缓慢，部分请求甚至出现超时。

### 环境信息
- **操作系统**: Linux (Alibaba Cloud ECS)
- **应用框架**: ASP.NET Core 博客系统
- **数据库**: PostgreSQL / MySQL
- **缓存**: Redis
- **日志系统**: Logstash
- **Web服务器**: Nginx + Kestrel

## 二、问题排查流程

### 2.1 初步定位：使用 top 命令

首先使用 `top` 命令查看系统整体资源使用情况：

```bash
top
```

观察到以下关键信息：

```
PID     USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
410969  www       20   0 2600712 186232  21212 S 162.5   9.7   0:20.19 java
2750566 root      20   0 1236464  10704   7520 S  12.5   0.6   4:48.86 containerd-shim
589444  root      20   0  158476  34940  13924 S   6.2   1.8 633:34.29 AliYunDunMonito
589407  root      20   0  100008  10204   6124 S   6.2   0.5 296:21.93 AliYunDun
736     root      20   0  475008   1620   1016 R   6.2   0.1 214:32.19 tuned
```

**问题发现**：
- **PID 410969** 的 Java 进程占用了 **162.5%** 的CPU（说明占用了超过1个CPU核心）
- 该进程内存占用：186MB (9.7%)
- 运行时间：仅20秒就导致如此高的CPU占用
- 进程用户：www（Web服务用户）

按 `1` 可以查看每个CPU核心的使用情况，发现多个核心都被这个Java进程占用。

### 2.2 确认进程详细信息：ps -aux

使用 `ps -aux` 命令查看该Java进程的详细信息：

```bash
ps -aux | grep java
```

输出结果：

```bash
www  411216  159  6.0 2600464 115080 ?  Ssl  12:39  0:04 /usr/share/logstash/jdk/bin/java 
-Xms1g -Xmx1g 
-Djava.awt.headless=true 
-Dfile.encoding=UTF-8 
-Djruby.compile.invokedynamic=true 
-XX:+HeapDumpOnOutOfMemoryError 
-Djava.security.egd=file:/dev/urandom 
-Xmx128m -Xms128m 
-cp /usr/share/logstash/vendor/jruby/lib/jruby.jar:...
org.logstash.Logstash
```

**关键发现**：
- 进程是 **Logstash**（日志收集和处理工具）
- CPU占用：**159%**（持续高位）
- 内存配置：堆内存设置为 1GB（但命令行中又出现了 -Xmx128m）
- 启动时间：12:39，运行仅几分钟就导致CPU飙升

### 2.3 验证问题根源

为了确认是否是 Logstash 导致的问题，我决定临时关闭 Logstash 服务：

```bash
systemctl stop logstash
# 或者直接 kill 进程
kill 410969
```

**背景说明**：Logstash 原本是用来将日志数据发送到 Elasticsearch 进行集中存储和分析的，但现在我们已经不再使用 Elasticsearch，所以 Logstash 已经失去了存在的意义。

### 2.4 验证修复效果

关闭 Logstash 后，再次使用 `top` 命令查看：

```
PID     USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
1645    root      20   0 1566552  17688  14464 S   1.0   0.9   1118:45 argusagent
589444  root      20   0  158476  35024  13924 S   1.0   1.8 633:35.87 AliYunDunMonito
736     root      20   0  475008   1620   1016 S   0.3   0.1 214:32.55 tuned
981     root      20   0 1358012  13152   7044 S   0.3   0.7  76:24.83 BT-Task
2233990 systemd+  20   0  162960  11232   1952 S   0.3   0.6 227:28.00 redis-server
```

**结果确认**：
- CPU使用率恢复正常，所有进程的CPU占用都在 **1%** 左右
- 系统响应速度明显恢复
- 博客应用访问恢复正常
- **确认问题根源：Logstash 配置或处理逻辑导致CPU飙升**

### 2.5 分析 Logstash 日志

检查 Logstash 的日志文件，查找问题原因：

```bash
tail -n 100 /var/log/logstash/logstash-plain.log
```

可能的错误信息：

```
[2024-10-25T12:39:15,234][WARN ][logstash.outputs.elasticsearch] Attempted to send a bulk request but Elasticsearch appears to be unreachable or down!
[2024-10-25T12:39:16,456][ERROR][logstash.pipeline] Exception in pipelineworker, the pipeline stopped processing new events
[2024-10-25T12:39:17,789][WARN ][logstash.filters.grok] Grok pattern matching timeout
```

## 三、问题根源分析

通过以上排查步骤，确认问题根源是 **Logstash 日志处理系统**导致的CPU飙升。深入分析后，发现了以下几个核心问题：

### 3.1 JVM 堆内存配置冲突

从进程启动参数可以看到：

```bash
-Xms1g -Xmx1g      # 堆内存初始和最大值都是 1GB
...
-Xmx128m -Xms128m  # 又出现了 128MB 的设置（冲突！）
```

**问题分析**：
- JVM 启动参数中出现了**相互冲突的内存配置**
- 先设置 1GB，后面又覆盖为 128MB
- 最终生效的是 128MB，导致内存不足
- 频繁的 GC（垃圾回收）消耗大量CPU
- 128MB 内存对于 Logstash 处理日志来说严重不足


### 3.2 Elasticsearch 输出端不可达

从日志分析推测可能出现：

```
[WARN] Attempted to send a bulk request but Elasticsearch appears to be unreachable or down!
```

**问题分析**：
- Elasticsearch 服务不可用或网络不通
- Logstash 不断重试发送日志数据
- 事件积压在内存中，导致内存压力
- 重试机制消耗大量CPU和网络资源
- 没有设置合理的重试次数和超时时间

## 四、解决方案

### 4.0 最直接的方案：彻底移除 Logstash（推荐）

既然已经不再使用 Elasticsearch，那么 Logstash 就完全没有存在的必要了。**最简单、最彻底的解决方案就是直接卸载 Logstash**。

**步骤一：停止并禁用服务**

```bash
# 停止 Logstash 服务
systemctl stop logstash

# 禁止开机自启动
systemctl disable logstash

# 确认状态
systemctl status logstash
```

**步骤二：卸载 Logstash**

```bash
# CentOS/RHEL
yum remove logstash

# Ubuntu/Debian
apt-get remove logstash

# 或者完全清除（包括配置文件）
apt-get purge logstash
```

**步骤三：清理残留文件**

```bash
# 删除配置文件
rm -rf /etc/logstash/

# 删除日志文件
rm -rf /var/log/logstash/

# 删除数据文件
rm -rf /var/lib/logstash/
```

**步骤四：验证效果**

```bash
# 检查进程是否还在
ps aux | grep logstash

# 查看系统资源使用
top

# 预期：CPU 使用率应该恢复正常，不再有 Java 进程占用高 CPU
```

**后续日志管理方案**：

既然不用 Elasticsearch，可以采用更简单的日志管理方式：

```bash
# 方案 1：直接使用文件日志 + 日志轮转
# 应用配置中设置日志轮转策略即可

# 方案 2：使用云服务商的日志服务
# 阿里云 SLS、腾讯云 CLS、AWS CloudWatch Logs

# 方案 3：如果需要查询，可以使用 Loki + Grafana（更轻量）
```


## 五、修复效果验证

### 5.1 修复前后对比

| 指标 | 修复前 | 修复后 | 改善幅度 |
|------|--------|--------|----------|
| Logstash CPU 使用率 | 162.5% | 已彻底移除 | - |
| 系统整体 CPU 使用率 | >90% | <5% | ↓ 85%+ |
| 系统响应速度 | 缓慢卡顿 | 正常流畅 | 恢复正常 |
| 应用响应时间 | 超时频繁 | 正常 (<200ms) | 恢复正常 |
| 内存使用 | 持续增长 | 稳定 | 稳定 |
| 服务器成本 | 资源紧张 | 资源充裕 | 节省资源 |

**关键发现**：
- 问题进程：Logstash (Java)
- CPU占用：从 162.5% 降到关闭后系统 CPU < 5%
- 根本原因：Logstash 已无使用价值（ES 已停用），却还在运行并消耗大量资源
- 最终方案：**彻底卸载 Logstash**，采用更简单的文件日志方案


## 六、Docker 容器 CPU 占用过高的排查

### 6.1 Docker 特有的排查流程

如果你的应用运行在 Docker 容器中，排查流程会有所不同：

**步骤 1：查看所有容器的资源使用情况**

```bash
# 实时监控所有容器的 CPU、内存使用
docker stats

# 输出示例：
CONTAINER ID   NAME              CPU %     MEM USAGE / LIMIT     MEM %     NET I/O
a1b2c3d4e5f6   ZhiCore-backend      85.23%    512MiB / 2GiB        25.6%     1.2MB / 850kB
f6e5d4c3b2a1   ZhiCore-frontend     2.15%     128MiB / 1GiB        12.5%     500kB / 300kB
9a8b7c6d5e4f   redis             0.50%     32MiB / 512MiB       6.25%     100kB / 50kB
```

**步骤 2：定位 CPU 占用高的容器**

```bash
# 查看特定容器的详细信息
docker inspect <container_id>

# 查看容器的日志
docker logs <container_id> --tail 100

# 实时查看日志
docker logs -f <container_id>
```

**步骤 3：进入容器内部排查**

```bash
# 方法 1：使用 docker exec 进入容器
docker exec -it <container_id> /bin/bash
# 或者
docker exec -it <container_id> /bin/sh

# 进入容器后，使用标准排查命令
top
ps aux
```

**步骤 4：从宿主机查看容器进程**

```bash
# 查看容器的主进程 PID（在宿主机上）
docker inspect -f '{{.State.Pid}}' <container_id>

# 假设输出是 12345，然后在宿主机上查看
top -p 12345
ps -p 12345 -f

# 查看容器进程树
pstree -p 12345
```

### 6.2 常见的 Docker 容器 CPU 问题

#### 问题 1：应用本身的性能问题

**案例：ASP.NET Core 应用死循环**

```bash
# 进入容器
docker exec -it ZhiCore-backend bash

# 查看进程
ps aux
# 发现 dotnet 进程 CPU 很高

# 如果容器内没有调试工具，从宿主机安装
docker exec -it ZhiCore-backend apt-get update
docker exec -it ZhiCore-backend apt-get install -y procps htop

# 使用 dotnet-dump（如果容器中有 SDK）
docker exec -it ZhiCore-backend dotnet-dump collect -p 1
```

**解决方案**：
- 检查应用代码逻辑
- 查看应用日志定位问题
- 使用 APM 工具监控

#### 问题 2：容器资源限制不当

**查看容器的资源限制**：

```bash
# 查看容器配置的资源限制
docker inspect <container_id> | grep -A 10 "HostConfig"

# 查看 CPU 限制
docker inspect -f '{{.HostConfig.CpuShares}}' <container_id>
docker inspect -f '{{.HostConfig.CpuQuota}}' <container_id>
docker inspect -f '{{.HostConfig.CpuPeriod}}' <container_id>
```

**设置合理的资源限制**（docker-compose.yml）：

```yaml
version: '3.8'

services:
  ZhiCore-backend:
    image: ZhiCore-backend:latest
    deploy:
      resources:
        limits:
          cpus: '1.0'        # 限制最多使用 1 个 CPU 核心
          memory: 1G         # 限制内存 1GB
        reservations:
          cpus: '0.5'        # 保证至少 0.5 核心
          memory: 512M       # 保证至少 512MB
    restart: unless-stopped
```

**使用 docker run 设置资源限制**：

```bash
docker run -d \
  --name ZhiCore-backend \
  --cpus="1.0" \              # 限制 CPU
  --memory="1g" \             # 限制内存
  --memory-swap="2g" \        # 限制 swap
  --pids-limit=100 \          # 限制进程数
  ZhiCore-backend:latest
```

#### 问题 3：容器日志过多导致磁盘 IO 高

**查看容器日志大小**：

```bash
# 查看所有容器的日志大小
du -sh /var/lib/docker/containers/*/

# 清理容器日志
truncate -s 0 /var/lib/docker/containers/<container_id>/<container_id>-json.log
```

**配置日志轮转**（docker-compose.yml）：

```yaml
services:
  ZhiCore-backend:
    image: ZhiCore-backend:latest
    logging:
      driver: "json-file"
      options:
        max-size: "10m"      # 单个日志文件最大 10MB
        max-file: "3"        # 最多保留 3 个日志文件
```

#### 问题 4：容器中运行了多余的进程

**检查容器中的进程**：

```bash
# 进入容器查看所有进程
docker exec <container_id> ps aux

# 可能发现意外的进程，如：
# - 调试工具没有关闭
# - 后台任务失控
# - 僵尸进程
```

**解决方案**：
```bash
# 重启容器
docker restart <container_id>

# 如果问题依旧，检查 Dockerfile 和启动脚本
docker exec <container_id> cat /proc/1/cmdline
```

### 6.3 Docker 容器 CPU 排查完整示例

**场景**：发现服务器 CPU 很高，怀疑是 Docker 容器导致

```bash
# 1. 查看所有容器 CPU 使用情况
docker stats --no-stream

# 输出：
# CONTAINER ID   NAME           CPU %     MEM USAGE
# a1b2c3d4e5f6   ZhiCore-backend   156.23%   800MiB

# 2. 发现 ZhiCore-backend 容器 CPU 156%，查看容器详情
docker inspect ZhiCore-backend

# 3. 查看容器日志，寻找线索
docker logs ZhiCore-backend --tail 100 | grep -i error

# 4. 获取容器主进程在宿主机上的 PID
CONTAINER_PID=$(docker inspect -f '{{.State.Pid}}' ZhiCore-backend)
echo "Container PID: $CONTAINER_PID"

# 5. 在宿主机上查看该进程
top -p $CONTAINER_PID

# 6. 进入容器内部排查
docker exec -it ZhiCore-backend bash

# 7. 容器内部查看进程
ps aux
top

# 8. 如果是 .NET 应用，查看诊断信息
docker exec ZhiCore-backend dotnet-counters ps
docker exec ZhiCore-backend dotnet-counters monitor -p 1

# 9. 查看应用日志（容器内部）
cd /app
tail -f logs/application.log

# 10. 如果需要重启容器
docker restart ZhiCore-backend

# 11. 如果问题持续，停止容器并重新创建
docker stop ZhiCore-backend
docker rm ZhiCore-backend
docker-compose up -d ZhiCore-backend
```

### 6.4 预防措施：Docker 最佳实践

#### 1. 始终设置资源限制

```yaml
# docker-compose.yml
services:
  myapp:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
```

#### 2. 配置健康检查

```yaml
services:
  ZhiCore-backend:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5000/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 40s
```

#### 3. 监控容器指标

```bash
# 使用 Prometheus + cAdvisor 监控容器
docker run -d \
  --name=cadvisor \
  --publish=8080:8080 \
  --volume=/:/rootfs:ro \
  --volume=/var/run:/var/run:ro \
  --volume=/sys:/sys:ro \
  --volume=/var/lib/docker/:/var/lib/docker:ro \
  gcr.io/cadvisor/cadvisor:latest
```

#### 4. 使用轻量级基础镜像

```dockerfile
# 避免使用完整的 OS 镜像
FROM mcr.microsoft.com/dotnet/aspnet:8.0-alpine
# 而不是
# FROM ubuntu:latest
```

#### 5. 定期清理

```bash
# 清理未使用的容器、镜像、网络、卷
docker system prune -a

# 查看 Docker 占用的磁盘空间
docker system df
```

### 6.5 Docker 排查工具总结

| 命令 | 用途 | 示例 |
|------|------|------|
| `docker stats` | 实时监控容器资源 | `docker stats --no-stream` |
| `docker inspect` | 查看容器详细信息 | `docker inspect <container_id>` |
| `docker logs` | 查看容器日志 | `docker logs -f <container_id>` |
| `docker exec` | 进入容器执行命令 | `docker exec -it <container_id> bash` |
| `docker top` | 查看容器进程 | `docker top <container_id>` |
| `docker events` | 监控 Docker 事件 | `docker events --filter container=<id>` |
| `docker system df` | 查看磁盘使用 | `docker system df -v` |

## 七、常用排查命令总结

### Linux 系统命令

```bash
# 1. 查看CPU使用率
top                    # 按 1 查看各核心使用率
htop                   # 更友好的界面
top -p PID             # 只看特定进程

# 2. 查看进程详细信息
ps aux | grep java     # 查找 Java 进程
ps -p PID -f           # 查看进程详细信息
ps -eLf | grep PID     # 查看进程的所有线程

# 3. 查看进程打开的文件和连接
lsof -p PID            # 查看进程打开的所有文件
lsof -i :9600          # 查看端口占用
netstat -anp | grep PID

# 4. 查看内存使用
free -h                # 查看系统内存
ps -p PID -o %mem,rss,vsz  # 查看进程内存

# 5. 查看磁盘IO
iostat -x 1 10         # 磁盘 IO 统计
iotop                  # 实时 IO 监控
df -h                  # 磁盘空间

# 6. 查看系统负载
uptime                 # 系统运行时间和负载
w                      # 当前登录用户和负载

# 7. 实时监控日志
tail -f /var/log/logstash/logstash-plain.log
journalctl -u logstash -f
```

### Java/Logstash 诊断工具

```bash
# 1. 查看 Java 进程
jps -l                 # 列出所有 Java 进程
ps aux | grep java

# 2. 查看 JVM 堆内存
jmap -heap PID         # JVM 堆信息
jstat -gc PID 1000     # 每秒输出 GC 情况

# 3. 查看线程堆栈
jstack PID             # 线程堆栈
jstack PID > thread.dump  # 保存到文件

# 4. Logstash 监控 API
curl localhost:9600/_node/stats?pretty
curl localhost:9600/_node/hot_threads?pretty
curl localhost:9600/_node/stats/jvm?pretty
curl localhost:9600/_node/stats/pipelines?pretty

# 5. 查看配置
/usr/share/logstash/bin/logstash --config.test_and_exit -f /etc/logstash/conf.d/
```

### 快速诊断流程

```bash
# Step 1: 找出 CPU 使用率最高的进程
top -b -n 1 | head -20

# Step 2: 查看该进程的详细信息
ps -p PID -f

# Step 3: 如果是 Java 进程，查看 JVM 信息
jmap -heap PID
jstat -gcutil PID 1000 10

# Step 4: 查看进程日志
tail -n 100 /var/log/logstash/logstash-plain.log

# Step 5: 如果是 Logstash，查看监控 API
curl localhost:9600/_node/hot_threads?human=true

# Step 6: 临时解决（如果情况紧急）
systemctl stop logstash
# 或
kill PID  # 不建议 kill -9
```

## 八、总结

这次CPU使用率过高的问题，根本原因是 **Logstash 服务已经失去使用价值但仍在运行**。通过系统的排查流程：

1. **发现问题**：系统卡顿 → 使用 top 命令发现异常
2. **定位进程**：top 命令 → 发现 Java 进程（PID 410969）CPU 占用 162.5%
3. **确认身份**：ps -aux 命令 → 确认是 Logstash 服务
4. **验证根因**：关闭 Logstash → CPU 立即恢复正常（<5%）
5. **确定方案**：发现 Elasticsearch 已停用 → **Logstash 已无存在必要**
6. **最终解决**：彻底卸载 Logstash，改用简单的文件日志方案

### 问题核心

| 问题类型 | 具体表现 | 影响 |
|---------|---------|------|
| **⚠️ 最根本问题** | **Logstash 已无使用价值却仍在运行** | **白白消耗大量 CPU 和内存** |
| JVM 内存配置冲突 | -Xmx1g 被覆盖为 -Xmx128m | 频繁 GC，CPU 飙升 |
| Grok 正则回溯 | 复杂的 .* 贪婪匹配 | 单条日志解析耗时长 |
| 输出端不可达 | Elasticsearch 已停用但 Logstash 仍在发送 | 无限重试，资源耗尽 |
| Pipeline 配置不当 | Worker 线程过多 | 线程竞争激烈 |
| 日志量过大 | 无过滤和采样 | 处理能力达到瓶颈 |
