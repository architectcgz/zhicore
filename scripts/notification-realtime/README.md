# Notification Realtime 联调脚本

用于验证 `zhicore-notification` 在多实例场景下，是否都能收到同一条 RocketMQ realtime fanout 消息，并各自执行本机 WebSocket 分发。

## 前置条件

需要本机已有以下基础设施：

1. `shared-postgres`
2. `shared-redis`
3. `shared-nacos`
4. `shared-rocketmq-namesrv`
5. `shared-rocketmq-broker`
## 脚本

### `run-comment-stream-fanout-check.sh`

适用于双本地 JVM 场景。

一键完成以下动作：

1. 构建 `zhicore-notification` 可执行 jar
2. 在本地启动两个 `notification` 实例
3. 向 RocketMQ 发送一条 `realtime-comment-stream` fanout 消息
4. 校验两个实例都出现相同 `commentId` 的 WebSocket 广播日志
5. 结束两个本地实例

用法：

```bash
./scripts/notification-realtime/run-comment-stream-fanout-check.sh
```

可选环境变量：

```bash
A_PORT=18106 \
B_PORT=18107 \
./scripts/notification-realtime/run-comment-stream-fanout-check.sh
```

运行期间生成的 pid、日志和状态文件会放在 `scripts/notification-realtime/.runtime/`。

注意：

1. 如果 RocketMQ namesrv 返回的是容器内 broker 地址，例如 `shared-rocketmq-broker:10911`，宿主机 JVM 进程可能无法直连 broker。
2. 出现这种情况时，建议改用下面的 docker 联调脚本。

### `run-comment-stream-fanout-check-docker.sh`

适用于需要在 `shared-infra` 容器网络内验证双实例广播的一致性场景。

脚本会完成以下动作：

1. 构建 `zhicore-notification` 可执行 jar
2. 基于当前 worktree 构建临时 notification 镜像
3. 在 `shared-infra` 网络中启动两个临时 notification 容器
4. 向 RocketMQ 发送一条 `realtime-comment-stream` fanout 消息
5. 校验两个容器都出现相同 `commentId` 的 WebSocket 广播日志
6. 结束两个临时容器

用法：

```bash
./scripts/notification-realtime/run-comment-stream-fanout-check-docker.sh
```

可选环境变量：

```bash
BASE_IMAGE=zhicore-zhicore-notification:base-local \
A_PORT=18106 \
B_PORT=18107 \
./scripts/notification-realtime/run-comment-stream-fanout-check-docker.sh
```

说明：

1. 脚本会优先复用本地已有的 `zhicore-zhicore-notification:base-local` 或 `zhicore-zhicore-notification:latest` 作为基础镜像，避免离线环境下拉取 `eclipse-temurin:17-jre` 失败。
2. 如果需要显式指定基础镜像，可通过 `BASE_IMAGE` 覆盖。
