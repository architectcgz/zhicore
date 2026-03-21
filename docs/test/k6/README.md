# k6 压测脚本

本目录存放可复用的 `k6` 服务容量压测脚本。当前使用 Docker 方式运行，无需在宿主机安装 `k6`。

## 脚本

- `service_capacity.js`
  - 单接口阶梯加压脚本
  - 通过环境变量注入目标 URL、鉴权头和压测阶段
- `service_fixed_vus.js`
  - 单接口固定并发脚本
  - 用于锁定某个服务的失稳拐点

## 运行示例

```bash
docker run --rm -i \
  -v /home/azhi/workspace/projects/zhicore-microservice:/work \
  -w /work/docs/test/k6 \
  -e TARGET_NAME=user \
  -e TARGET_URL=http://host.docker.internal:8081/api/v1/users/189000000000000002 \
  grafana/k6 run service_capacity.js
```

如需鉴权：

```bash
docker run --rm -i \
  -v /home/azhi/workspace/projects/zhicore-microservice:/work \
  -w /work/docs/test/k6 \
  -e TARGET_NAME=admin \
  -e TARGET_URL='http://host.docker.internal:8090/admin/users?page=1&size=5' \
  -e AUTH_TOKEN='<ACCESS_TOKEN>' \
  -e USER_ID='189000000000000001' \
  -e USER_ROLES='ADMIN' \
  grafana/k6 run service_capacity.js
```

## 常用环境变量

- `TARGET_NAME`: 服务名标签
- `TARGET_URL`: 压测 URL
- `AUTH_TOKEN`: Bearer token，可选
- `USER_ID`: `X-User-Id`，可选
- `USER_ROLES`: `X-User-Roles`，可选
- `EXTRA_HEADERS_JSON`: 额外请求头 JSON，可选
- `START_VUS`: 初始虚拟用户数，默认 `20`
- `STAGE_1_VUS` ~ `STAGE_4_VUS`: 阶梯目标并发，默认 `50/100/150/200`
- `STAGE_1_DURATION` ~ `STAGE_4_DURATION`: 每阶段时长，默认 `30s`
- `VUS`: 固定并发脚本的虚拟用户数，默认 `100`
- `DURATION`: 固定并发脚本的持续时长，默认 `20s`
- `SLEEP_MS`: 每次请求后的思考时间，默认 `0`
