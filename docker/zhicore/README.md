# ZhiCore Services In Docker

此目录用于单独启动 ZhiCore 各业务服务，复用已存在的 `shared-infra` 网络与共享基础设施容器。

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

- `shared-postgres`、`shared-redis`、`shared-nacos`、`shared-rocketmq-*`、`zhicore-mongodb`、`zhicore-elasticsearch`、`file-service-app`、`id-generator-nginx` 需要已在 `shared-infra` 网络中运行。
- 这里的 `zhicore-id-generator` 是站内代理服务，真实上游仍通过 `id-generator-nginx:8011` 访问外部 ID Generator 集群。
