# Infrastructure Port Configuration

## Overview

This document defines the port mappings and connection details for all infrastructure services in the ZhiCore Microservices system. Understanding these mappings is critical for proper service configuration.

---

## Port Mapping Concept

Docker port mapping format: `HOST_PORT:CONTAINER_PORT`

- **HOST_PORT**: Port accessible from your local machine (outside Docker)
- **CONTAINER_PORT**: Port used inside the Docker network

**Example**: `6800:6379` means:
- Connect to `localhost:6800` from your local machine
- Connect to `redis:6379` from inside Docker containers

---

## Infrastructure Services

### PostgreSQL (Primary Database)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-postgres` |
| Image | `postgres:16-alpine` |
| Host Port | `5432` |
| Container Port | `5432` |
| Default User | `postgres` |
| Default Password | `postgres123456` |
| Default Database | `ZhiCore` |

**Connection Strings**:
- From Host: `jdbc:postgresql://localhost:5432/ZhiCore_user`
- From Container: `jdbc:postgresql://postgres:5432/ZhiCore_user`

**Environment Variables**:
```yaml
DB_HOST: localhost (host) / postgres (container)
DB_PORT: 5432
DB_USERNAME: postgres
DB_PASSWORD: postgres123456
```

**Databases Created**:
- `ZhiCore_user` - User service
- `ZhiCore_post` - Post service
- `ZhiCore_comment` - Comment service
- `ZhiCore_message` - Message service
- `ZhiCore_notification` - Notification service
- `ZhiCore_upload` - Upload service
- `ZhiCore_admin` - Admin service

---

### Redis (Cache & Session Store)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-redis` |
| Image | `redis:7.2-alpine` |
| Host Port | `6379` |
| Container Port | `6379` |
| Default Password | `redis123456` |
| Max Memory | `512mb` |
| Eviction Policy | `allkeys-lru` |

**Port Assignment**: Redis instance #1 (6379)

**Connection Strings**:
- From Host: `redis://localhost:6379`
- From Container: `redis://redis:6379`

**Environment Variables**:
```yaml
# For local development (outside Docker)
REDIS_HOST: localhost
REDIS_PORT: 6379
REDIS_PASSWORD: redis123456

# For Docker deployment (inside containers)
REDIS_HOST: redis
REDIS_PORT: 6379
REDIS_PASSWORD: redis123456
```

---

### MySQL (Nacos Database)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-mysql-nacos` |
| Image | `mysql:8.0` |
| Host Port | `3307` |
| Container Port | `3306` |
| Root Password | `root123456` |
| Nacos User | `nacos` |
| Nacos Password | `nacos123456` |
| Database | `nacos` |

**Connection Strings**:
- From Host: `jdbc:mysql://localhost:3307/nacos`
- From Container: `jdbc:mysql://mysql-nacos:3306/nacos`

---

### Nacos (Service Registry & Config Center)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-nacos` |
| Image | `nacos/nacos-server:v2.3.0` |
| Console Port | `8848` |
| gRPC Port | `9848` |
| gRPC Port (Internal) | `9849` |
| Mode | `standalone` |
| Auth Enabled | `true` |

**Access URLs**:
- Console: `http://localhost:8848/nacos`
- API: `http://localhost:8848/nacos/v1/`

**Default Credentials**:
- Username: `nacos`
- Password: `nacos`

**Environment Variables**:
```yaml
NACOS_ADDR: localhost:8848 (host) / nacos:8848 (container)
NACOS_USERNAME: nacos
NACOS_PASSWORD: nacos
```

---

### RocketMQ (Message Queue)

#### NameServer

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-rocketmq-namesrv` |
| Image | `apache/rocketmq:4.9.6` |
| Port | `9876` |
| JVM Memory | `-Xms256m -Xmx256m` |

**Connection**:
- From Host: `localhost:9876`
- From Container: `rocketmq-namesrv:9876`

#### Broker

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-rocketmq-broker` |
| Image | `apache/rocketmq:4.9.6` |
| VIP Port | `10909` |
| Listen Port | `10911` |
| HA Port | `10912` |
| JVM Memory | `-Xms512m -Xmx512m -Xmn256m` |

**Memory Optimization**:
- Optimized for development environment
- Reduced from ~4.2GB to ~512MB
- See `docker/rocketmq/MEMORY_OPTIMIZATION.md` for details

#### Dashboard

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-rocketmq-dashboard` |
| Port | `8180` (host) -> `8080` (container) |
| URL | `http://localhost:8180` |
| JVM Memory | `-Xms128m -Xmx256m` |

**Environment Variables**:
```yaml
ROCKETMQ_NAME_SERVER: localhost:9876 (host) / rocketmq-namesrv:9876 (container)
```

**Memory Optimization Results** (as of 2026-01-20):
- NameServer: ~190MB (optimized from ~350MB)
- Broker: ~650MB (optimized from ~4.2GB)
- Dashboard: ~330MB (already optimized)
- **Total: ~1.17GB (reduced from ~5GB, 77% reduction)**

---

### Elasticsearch (Search Engine)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-elasticsearch` |
| Image | `elasticsearch:8.11.3` |
| HTTP Port | `9200` |
| Transport Port | `9300` |
| Cluster Name | `ZhiCore-es-cluster` |
| Security | Disabled |

**Connection**:
- From Host: `http://localhost:9200`
- From Container: `http://elasticsearch:9200`

**Environment Variables**:
```yaml
ELASTICSEARCH_HOST: localhost (host) / elasticsearch (container)
ELASTICSEARCH_PORT: 9200
```

---

### Kibana (ES Visualization)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-kibana` |
| Image | `kibana:8.11.3` |
| Port | `5601` |
| Locale | `zh-CN` |
| URL | `http://localhost:5601` |

---

### Prometheus (Metrics Collection)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-prometheus` |
| Image | `prom/prometheus:v2.48.0` |
| Port | `9090` |
| URL | `http://localhost:9090` |

---

### Grafana (Monitoring Dashboard)

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-grafana` |
| Image | `grafana/grafana:10.2.2` |
| Host Port | `3100` |
| Container Port | `3000` |
| Admin User | `admin` |
| Admin Password | `admin123456` |
| URL | `http://localhost:3100` |

---

### SkyWalking (Distributed Tracing)

#### OAP Server

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-skywalking-oap` |
| Image | `apache/skywalking-oap-server:9.6.0` |
| gRPC Port | `11800` |
| HTTP Port | `12800` |
| Storage | Elasticsearch |

**Agent Configuration**:
```yaml
SW_AGENT_COLLECTOR_BACKEND_SERVICES: localhost:11800 (host) / skywalking-oap:11800 (container)
```

#### UI

| Property | Value |
|----------|-------|
| Container Name | `ZhiCore-skywalking-ui` |
| Image | `apache/skywalking-ui:9.6.0` |
| Host Port | `8088` |
| Container Port | `8080` |
| URL | `http://localhost:8088` |

---

## Microservices Ports

| Service | Container Name | Port | Description |
|---------|---------------|------|-------------|
| Gateway | `ZhiCore-gateway` | `8000` | API Gateway |
| User | `ZhiCore-user` | `8101` | User Service |
| Post | `ZhiCore-post` | `8102` | Post Service |
| Comment | `ZhiCore-comment` | `8103` | Comment Service |
| Message | `ZhiCore-message` | `8084` | Message Service |
| Notification | `ZhiCore-notification` | `8085` | Notification Service |
| Search | `ZhiCore-search` | `8086` | Search Service |
| Ranking | `ZhiCore-ranking` | `8087` | Ranking Service |
| Admin | `ZhiCore-admin` | `8090` | Admin Service |

**API Documentation URLs** (Knife4j):
- Gateway (Aggregated): `http://localhost:8000/doc.html`
- User Service: `http://localhost:8101/doc.html`
- Post Service: `http://localhost:8102/doc.html`
- Comment Service: `http://localhost:8103/doc.html`
- Message Service: `http://localhost:8084/doc.html`
- Notification Service: `http://localhost:8085/doc.html`
- Search Service: `http://localhost:8086/doc.html`
- Ranking Service: `http://localhost:8087/doc.html`
- Admin Service: `http://localhost:8090/doc.html`

---

## External Service Ports (DO NOT USE)

These ports are used by other platform services. **DO NOT** assign these ports to ZhiCore-microservice services to avoid conflicts:

### ID Generator Service
- **PostgreSQL**: `5435` (id-generator-postgres)
- **ZooKeeper**: `2181`, `8888` (id-generator-zookeeper)
- **ID Generator Server**: `8010` (id-generator-server)

### File Service
- **PostgreSQL**: `5434` (file-service-postgres)
- **RustFS API**: `9001` (file-service-rustfs)
- **RustFS Console**: `9002` (file-service-rustfs)
- **File Service**: `8089` (file-service-app)

### IM System
- **PostgreSQL**: `5433` (im-postgres)
- **Redis**: `6380` (im-redis)
- **RocketMQ NameServer**: `9877` (im-rocketmq-nameserver)
- **RocketMQ Broker**: `10913`, `10915`, `10916`, `8080`, `8081` (im-rocketmq-broker)
- **RocketMQ Dashboard**: `8082` (im-rocketmq-dashboard)

### Reserved Port Ranges
- **5432-5435**: PostgreSQL instances (5432=ZhiCore, 5433=im, 5434=file, 5435=id-gen)
- **6379-6381**: Redis instances (6379=ZhiCore, 6380=im, 6381=reserved)
- **8010**: ID Generator
- **8080-8090**: Various services (check specific assignments)
- **9001-9002**: Object storage (RustFS)
- **9876-9877**: RocketMQ NameServer
- **10909-10912**: RocketMQ Broker

---

## Configuration Best Practices

### 1. Environment-Specific Configuration

Use environment variables with defaults:

```yaml
# Good - Works in both environments
redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
```

### 2. Docker Deployment

For services running in Docker, override with container names:

```yaml
# docker-compose.services.yml
environment:
  - REDIS_HOST=redis
  - REDIS_PORT=6379
  - POSTGRES_HOST=postgres
  - POSTGRES_PORT=5432
```

### 3. Local Development

For local development (services running outside Docker):

```yaml
# application-dev.yml or .env
REDIS_HOST=localhost
REDIS_PORT=6379
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
```

---

## Troubleshooting

### Redis Connection Issues

**Symptom**: `Connection refused` or `timeout` errors

**Diagnosis**:
1. Check if you're running inside or outside Docker
2. Verify port configuration matches your environment

**Solution**:
```bash
# Test from host
redis-cli -h localhost -p 6379 -a redis123456 ping

# Test from container
docker exec ZhiCore-redis redis-cli -a redis123456 ping
```

### PostgreSQL Connection Issues

**Symptom**: `Connection refused` or `database does not exist`

**Diagnosis**:
1. Verify database was created during initialization
2. Check connection parameters

**Solution**:
```bash
# List databases
docker exec ZhiCore-postgres psql -U postgres -c "\l"

# Test connection
docker exec ZhiCore-postgres psql -U postgres -d ZhiCore_user -c "SELECT 1"
```

### Nacos Registration Issues

**Symptom**: Services not appearing in Nacos console

**Diagnosis**:
1. Check Nacos address configuration
2. Verify network connectivity

**Solution**:
```bash
# Check Nacos health
curl http://localhost:8848/nacos/v1/console/health/readiness

# Check service registration
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=ZhiCore-user"
```

---

## Quick Reference

### Start Infrastructure
```bash
cd docker
docker-compose up -d
```

### Start Services
```bash
cd docker
docker-compose -f docker-compose.services.yml up -d
```

### Check Service Health
```bash
# Check all containers
docker ps

# Check specific service logs
docker logs ZhiCore-user

# Check service health endpoints
curl http://localhost:8000/actuator/health  # Gateway
curl http://localhost:8101/actuator/health  # User Service
curl http://localhost:8102/actuator/health  # Post Service
curl http://localhost:8103/actuator/health  # Comment Service
curl http://localhost:8084/actuator/health  # Message Service
curl http://localhost:8085/actuator/health  # Notification Service
curl http://localhost:8086/actuator/health  # Search Service
curl http://localhost:8087/actuator/health  # Ranking Service
curl http://localhost:8090/actuator/health  # Admin Service
```

### Stop All Services
```bash
cd docker
docker-compose -f docker-compose.services.yml down
docker-compose down
```

---

## Important Notes

1. **Port Allocation Strategy**: Services use sequential port numbers for the same type of infrastructure:
   - PostgreSQL: 5432 (ZhiCore), 5433 (im), 5434 (file), 5435 (id-gen)
   - Redis: 6379 (ZhiCore), 6380 (im), 6381 (reserved)

2. **Database Initialization**: All databases are created automatically on first startup via `docker/postgres-init/init-all-databases.sql`.

3. **Nacos Configuration**: Services load configuration from Nacos on startup. Ensure Nacos is healthy before starting services.

4. **Network**: All services must be on the `ZhiCore-network` Docker network to communicate.

5. **Health Checks**: All services have health check endpoints at `/actuator/health`.

---

## Related Documentation

- [Docker Compose Configuration](../docker/README.md)
- [Nacos Configuration Guide](../config/nacos/README.md)
- [Troubleshooting Guide](../docs/troubleshooting/)
