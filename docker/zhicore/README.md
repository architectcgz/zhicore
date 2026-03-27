# ZhiCore Services In Docker

此目录用于单独启动 ZhiCore 各业务服务。

网络规划：

- `zhicore-app`：ZhiCore 业务服务内部网络
- `shared-infra`：共享基础设施网络
- 业务服务同时接入 `zhicore-app` 和 `shared-infra`

使用方式：

```bash
cd docker/zhicore
docker compose build
docker compose up -d
docker compose ps
```

当前编排包含：

- `zhicore-gateway`
- `zhicore-user`
- `zhicore-content`
- `zhicore-comment`
- `zhicore-id-generator`
- `zhicore-message`
- `zhicore-notification`
- `zhicore-search`
- `zhicore-ranking`
- `zhicore-upload`
- `zhicore-admin`
- `zhicore-ops`

宿主机端口：

- `8000` gateway
- `8081` user
- `8082` content
- `8083` comment
- `8084` message
- `8085` notification
- `8086` search
- `8087` ranking
- `8088` id-generator
- `8092` upload
- `8093` admin
- `8094` ops

说明：

- `shared-postgres`、`shared-redis`、`shared-nacos`、`shared-rocketmq-*` 需要已在 `shared-infra` 网络中运行。
- `zhicore-mongodb`、`zhicore-elasticsearch`、`file-service-app`、`id-generator-nginx` 需要对业务容器可达；其中 ZhiCore 自带组件推荐接入 `zhicore-app`。
- 这里的 `zhicore-id-generator` 是站内代理服务，真实上游仍通过 `id-generator-nginx:8011` 访问外部 ID Generator 集群。
