# Let's Encrypt HTTPS 配置完整指南

## 目录
1. [前置准备](#前置准备)
2. [方案选择](#方案选择)
3. [方案A：直接在宿主机配置（推荐）](#方案a直接在宿主机配置推荐)
4. [方案B：使用 Docker Certbot](#方案b使用-docker-certbot)
5. [配置 Nginx](#配置-nginx)
6. [自动续期](#自动续期)
7. [验证和测试](#验证和测试)
8. [常见问题](#常见问题)

---

## 前置准备

### 1. 确认域名解析
确保你的域名已经正确解析到服务器 IP：

```bash
# 检查域名解析
nslookup www.archi0v0.top
nslookup archi0v0.top

# 或使用 dig
dig www.archi0v0.top +short
dig archi0v0.top +short
```

### 2. 确认端口开放
Let's Encrypt 需要通过 80 端口验证域名所有权：

```bash
# 检查 80 端口是否可访问
curl -I http://www.archi0v0.top/health

# 如果使用云服务器，确保安全组规则开放了：
# - 80 端口 (HTTP)
# - 443 端口 (HTTPS)
```

---

## 方案选择

| 特性 | 方案A (宿主机) | 方案B (Docker) |
|------|---------------|---------------|
| 难度 | ⭐⭐⭐ 简单 | ⭐⭐⭐⭐ 中等 |
| 维护 | ⭐⭐⭐⭐⭐ 方便 | ⭐⭐⭐ 稍复杂 |
| 推荐度 | ✅ **推荐** | ⚠️ 备选 |

---

## 方案A：直接在宿主机配置（推荐）

### 步骤1：安装 Certbot

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install certbot

# CentOS/RHEL
sudo yum install certbot

# 验证安装
certbot --version
```

### 步骤2：获取证书（Standalone 模式）

这种方式需要暂时停止 nginx 容器：

```bash
# 1. 停止 nginx 容器（释放 80 端口）
cd /path/to/your/deploy
docker-compose stop ZhiCore-frontend

# 2. 获取证书
sudo certbot certonly \
  --standalone \
  --preferred-challenges http \
  -d www.archi0v0.top \
  -d archi0v0.top \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email

# 3. 证书获取成功后会显示：
# Congratulations! Your certificate and chain have been saved at:
# /etc/letsencrypt/live/www.archi0v0.top/fullchain.pem
# Your key file has been saved at:
# /etc/letsencrypt/live/www.archi0v0.top/privkey.pem
```

### 步骤3：配置 Nginx 使用证书

创建 HTTPS 版本的 nginx 配置：

```bash
cd /path/to/your/deploy
```

编辑 `nginx.conf`，在现有配置基础上添加 HTTPS 支持：

```nginx
http {
    # ... 保持现有配置 ...
    
    # HTTP 服务器 - 重定向到 HTTPS
    server {
        listen 80;
        server_name www.archi0v0.top archi0v0.top;
        
        # Let's Encrypt 验证路径（续期时需要）
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }
        
        # 其他请求重定向到 HTTPS
        location / {
            return 301 https://$server_name$request_uri;
        }
    }
    
    # HTTPS 服务器
    server {
        listen 443 ssl http2;
        server_name www.archi0v0.top archi0v0.top;
        
        # SSL 证书配置
        ssl_certificate /etc/letsencrypt/live/www.archi0v0.top/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/www.archi0v0.top/privkey.pem;
        
        # SSL 优化配置
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
        ssl_prefer_server_ciphers off;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;
        ssl_session_tickets off;
        
        # OCSP Stapling
        ssl_stapling on;
        ssl_stapling_verify on;
        ssl_trusted_certificate /etc/letsencrypt/live/www.archi0v0.top/chain.pem;
        resolver 8.8.8.8 8.8.4.4 valid=300s;
        resolver_timeout 5s;
        
        # 安全头部
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
        
        # ... 保持原有的其他 location 配置 ...
        # 把原来 server 块中的所有 location 配置复制到这里
    }
}
```

### 步骤4：修改 docker-compose.yml

挂载证书目录到容器：

```yaml
services:
  ZhiCore-frontend:
    image: your-frontend-image
    container_name: ZhiCore-frontend
    ports:
      - "80:80"
      - "443:443"  # 添加 HTTPS 端口
    volumes:
      # 挂载 SSL 证书目录（只读）
      - /etc/letsencrypt:/etc/letsencrypt:ro
      # 挂载 certbot webroot（用于续期验证）
      - /var/www/certbot:/var/www/certbot:ro
    environment:
      - RUSTFS_ACCESS_KEY=${RUSTFS_ACCESS_KEY}
      - RUSTFS_SECRET_KEY=${RUSTFS_SECRET_KEY}
      - RUSTFS_REGION=${RUSTFS_REGION:-cn-north-1}
    restart: always
    networks:
      - ZhiCore-network
```

### 步骤5：重启服务

```bash
# 重新构建镜像（如果修改了 nginx.conf）
docker-compose build ZhiCore-frontend

# 启动服务
docker-compose up -d ZhiCore-frontend

# 查看日志
docker-compose logs -f ZhiCore-frontend
```

---

## 方案B：使用 Docker Certbot

如果不想在宿主机安装 certbot，可以使用 certbot 容器。

### 步骤1：修改 docker-compose.yml

添加 certbot 服务：

```yaml
services:
  # ... 现有服务 ...
  
  certbot:
    image: certbot/certbot
    container_name: certbot
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt
      - /var/lib/letsencrypt:/var/lib/letsencrypt
      - /var/www/certbot:/var/www/certbot
    command: certonly --webroot --webroot-path=/var/www/certbot --email your-email@example.com --agree-tos --no-eff-email -d www.archi0v0.top -d archi0v0.top
    depends_on:
      - ZhiCore-frontend
```

### 步骤2：配置 Nginx 支持 Webroot 验证

在 nginx.conf 的 HTTP server 块中添加：

```nginx
server {
    listen 80;
    server_name www.archi0v0.top archi0v0.top;
    
    # Certbot 验证路径
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    
    # 其他配置...
}
```

### 步骤3：创建证书目录并获取证书

```bash
# 创建必要的目录
sudo mkdir -p /var/www/certbot
sudo mkdir -p /etc/letsencrypt
sudo mkdir -p /var/lib/letsencrypt

# 启动 nginx（用于验证）
docker-compose up -d ZhiCore-frontend

# 运行 certbot 获取证书
docker-compose run --rm certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email \
  -d www.archi0v0.top \
  -d archi0v0.top

# 查看证书
sudo ls -la /etc/letsencrypt/live/www.archi0v0.top/
```

### 步骤4：更新 Nginx 配置并重启

按照方案A的步骤3配置 HTTPS，然后重启：

```bash
docker-compose restart ZhiCore-frontend
```

---

## 自动续期

Let's Encrypt 证书有效期为 90 天，需要定期续期。

### 方案A：使用宿主机 Crontab

```bash
# 编辑 crontab
sudo crontab -e

# 添加定时任务（每月1号凌晨3点尝试续期）
0 3 1 * * certbot renew --quiet --deploy-hook "docker-compose -f /path/to/deploy/docker-compose.yml restart ZhiCore-frontend"

# 或者每周尝试续期（推荐）
0 3 * * 0 certbot renew --quiet --deploy-hook "docker-compose -f /path/to/deploy/docker-compose.yml restart ZhiCore-frontend"
```

### 方案B：使用 Docker Certbot 续期

创建续期脚本 `deploy/renew-certs.sh`：

```bash
#!/bin/bash
set -e

echo "尝试续期 SSL 证书..."

cd /path/to/your/deploy

# 运行 certbot 续期
docker-compose run --rm certbot renew --quiet

# 重启 nginx 以加载新证书
docker-compose restart ZhiCore-frontend

echo "证书续期完成！"
```

赋予执行权限并添加到 crontab：

```bash
chmod +x deploy/renew-certs.sh

# 添加到 crontab
sudo crontab -e
0 3 1 * * /path/to/deploy/renew-certs.sh >> /var/log/certbot-renew.log 2>&1
```

### 测试续期

```bash
# 方案A：宿主机测试
sudo certbot renew --dry-run

# 方案B：Docker 测试
docker-compose run --rm certbot renew --dry-run
```

---

## 验证和测试

### 1. 测试 HTTPS 连接

```bash
# 测试 HTTPS 是否可访问
curl -I https://www.archi0v0.top

# 测试 HTTP 到 HTTPS 重定向
curl -I http://www.archi0v0.top

# 查看证书信息
openssl s_client -connect www.archi0v0.top:443 -servername www.archi0v0.top < /dev/null
```

### 2. 在线 SSL 测试

访问以下网站测试 SSL 配置质量：
- [SSL Labs](https://www.ssllabs.com/ssltest/analyze.html?d=www.archi0v0.top)
- 目标：达到 A 或 A+ 评级

### 3. 测试文件上传

1. 打开浏览器访问 `https://www.archi0v0.top`
2. 按 F12 打开开发者工具，切换到 Console 标签
3. 尝试上传文件
4. 应该看到：`✅ 使用 Web Crypto API 计算文件哈希`

### 4. 检查证书有效期

```bash
# 查看证书信息
sudo certbot certificates

# 输出示例：
# Certificate Name: www.archi0v0.top
#   Domains: www.archi0v0.top archi0v0.top
#   Expiry Date: 2025-04-04 12:00:00+00:00 (VALID: 89 days)
```

---

## 常见问题

### Q1: 获取证书时提示端口 80 被占用？

**A:** 确保停止了 nginx 容器：
```bash
docker-compose stop ZhiCore-frontend
# 或停止所有服务
docker-compose down
```

### Q2: 证书获取失败，提示域名验证失败？

**A:** 检查以下几点：
1. 域名 DNS 是否正确解析到服务器
2. 防火墙/安全组是否开放 80 端口
3. 是否有其他程序占用 80 端口

```bash
# 检查端口占用
sudo netstat -tlnp | grep :80
# 或
sudo lsof -i :80
```

### Q3: Docker 容器无法读取证书文件？

**A:** 检查文件权限：
```bash
# 查看证书目录权限
sudo ls -la /etc/letsencrypt/live/www.archi0v0.top/

# 如果权限有问题，调整权限
sudo chmod -R 755 /etc/letsencrypt/live/
sudo chmod -R 755 /etc/letsencrypt/archive/
```

### Q4: 证书续期失败？

**A:** 检查 nginx 配置是否包含 `.well-known/acme-challenge/` 路径：
```nginx
location /.well-known/acme-challenge/ {
    root /var/www/certbot;
}
```

确保目录存在且有写权限：
```bash
sudo mkdir -p /var/www/certbot
sudo chmod -R 755 /var/www/certbot
```

### Q5: 浏览器提示证书不受信任？

**A:** 可能原因：
1. 证书配置路径错误
2. 使用了错误的域名访问（如 IP 地址）
3. 时钟不同步

```bash
# 检查服务器时间
date
# 如果时间不对，同步时间
sudo ntpdate -u ntp.aliyun.com
```

### Q6: 如何查看 nginx 日志排查问题？

```bash
# 查看容器日志
docker-compose logs ZhiCore-frontend

# 进入容器查看 nginx 错误日志
docker-compose exec ZhiCore-frontend cat /var/log/nginx/error.log

# 实时查看日志
docker-compose logs -f ZhiCore-frontend
```

---

## 性能优化建议

### 1. 启用 HTTP/2

已在配置中包含：`listen 443 ssl http2;`

### 2. 启用 SSL Session 缓存

已在配置中包含：
```nginx
ssl_session_cache shared:SSL:10m;
ssl_session_timeout 10m;
```

### 3. 启用 OCSP Stapling

已在配置中包含，可提升 SSL 握手速度。

### 4. 使用 CDN

如果使用 CDN（如阿里云、腾讯云），可以在 CDN 层处理 HTTPS，减轻源站压力。

---

## 完整配置示例

创建 `deploy/nginx-with-https.conf` 完整配置：

```bash
# 我已经在你的 deploy/nginx.conf 基础上创建了一个包含 HTTPS 的版本
# 你可以备份当前配置，然后应用新配置

cd deploy
cp nginx.conf nginx.conf.http-only-backup
# 然后手动编辑 nginx.conf 添加 HTTPS 配置
```

---

## 快速启动脚本

创建一个一键配置脚本 `deploy/setup-https.sh`：

```bash
#!/bin/bash
# 将在下一步创建
```

需要我创建这个自动化脚本吗？

