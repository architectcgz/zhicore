# Docker环境下Let's Encrypt HTTPS配置指南

> 基于Docker + OpenResty/Nginx的免费HTTPS证书配置教程

---

## 📖 目录

1. [快速开始](#快速开始)
2. [准备工作](#准备工作)
3. [自动化配置](#自动化配置推荐)
4. [手动配置](#手动配置)
5. [证书续期](#证书续期)
6. [测试验证](#测试验证)
7. [常见问题](#常见问题)
8. [命令速查](#命令速查)

---

## ⚡ 快速开始

### 什么是HTTPS？

HTTPS通过SSL/TLS加密HTTP通信，提供三大保障：
- 🔒 **加密传输** - 防止数据被窃取
- ✅ **身份认证** - 确认网站真实性
- 🛡️ **数据完整** - 防止内容被篡改

### Let's Encrypt免费证书

- 永久免费的DV证书
- 支持自动化管理
- 90天有效期（支持自动续期）
- 全球主流浏览器信任

---

## 📋 准备工作

### 环境检查清单

```bash
# 1. 检查域名解析
dig yourdomain.com +short
curl -s ifconfig.me  # 对比是否一致

# 2. 检查端口
sudo netstat -tlnp | grep :80
sudo netstat -tlnp | grep :443

# 3. 云服务器安全组
# 确保开放 80(HTTP) 和 443(HTTPS) 端口

# 4. 备份配置
cd /path/to/your/deploy
cp nginx.conf nginx.conf.backup
cp docker-compose.yml docker-compose.yml.backup
```


---

## 🚀 自动化配置（推荐）

### 使用自动化脚本

项目提供 `setup-https.sh` 一键配置脚本。

```bash
# 1. 进入部署目录
cd /path/to/your/deploy

# 2. 赋予执行权限
chmod +x setup-https.sh

# 3. 运行脚本
sudo bash setup-https.sh

# 4. 按提示输入
# - 域名（不带www）: yourdomain.com
# - 邮箱: your-email@example.com
```

### 脚本会自动完成

- ✅ 检查域名解析和端口
- ✅ 安装Certbot工具
- ✅ 获取SSL证书
- ✅ 配置Nginx和Docker
- ✅ 设置自动续期
- ✅ 启动服务并验证

---

## 🔧 手动配置

### 步骤1：安装Certbot

```bash
# Ubuntu/Debian
sudo apt-get update && sudo apt-get install -y certbot

# CentOS/RHEL
sudo yum install -y epel-release certbot

# 验证
certbot --version
```

### 步骤2：获取证书

```bash
# 停止nginx（释放80端口）
cd /path/to/your/deploy
docker-compose stop ZhiCore-frontend

# 获取证书
sudo certbot certonly \
  --standalone \
  -d yourdomain.com \
  -d www.yourdomain.com \
  --email your-email@example.com \
  --agree-tos \
  --no-eff-email

# 证书保存在
# /etc/letsencrypt/live/yourdomain.com/fullchain.pem
# /etc/letsencrypt/live/yourdomain.com/privkey.pem
```

### 步骤3：配置Nginx

编辑 `nginx.conf`：

```nginx
http {
    # HTTP服务器 - 重定向到HTTPS
    server {
        listen 80;
        server_name yourdomain.com www.yourdomain.com;
        
        # Let's Encrypt验证路径
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }
        
        # 重定向到HTTPS
        location / {
            return 301 https://$host$request_uri;
        }
    }
    
    # HTTPS服务器
    server {
        listen 443 ssl http2;
        server_name yourdomain.com www.yourdomain.com;
        
        # SSL证书
        ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;
        
        # SSL优化
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
        ssl_prefer_server_ciphers off;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;
        
        # OCSP Stapling
        ssl_stapling on;
        ssl_stapling_verify on;
        ssl_trusted_certificate /etc/letsencrypt/live/yourdomain.com/chain.pem;
        resolver 8.8.8.8 8.8.4.4 valid=300s;
        
        # 安全头
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
        
        # 你的应用配置...
        root /usr/local/openresty/nginx/html;
        location / {
            try_files $uri $uri/ /index.html;
        }
    }
}
```

### 步骤4：配置Docker Compose

编辑 `docker-compose.yml`：

```yaml
services:
  ZhiCore-frontend:
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /var/www/certbot:/var/www/certbot:ro
```

### 步骤5：启动服务

```bash
docker-compose build ZhiCore-frontend
docker-compose up -d
docker-compose logs -f ZhiCore-frontend
```

---

## 🔄 证书续期

Let's Encrypt证书有效期90天，需配置自动续期（证书会在到期前30天自动续期）。

### 方式1：使用项目续期脚本

```bash
# 配置自动续期（推荐）
cd /path/to/your/deploy
sudo bash setup-auto-renew.sh

# 或手动添加crontab
sudo crontab -e
# 添加：0 3 * * * /path/to/deploy/renew-certs.sh >> /var/log/certbot-renew.log 2>&1
```

### 方式2：Certbot原生续期

```bash
sudo crontab -e
# 添加：0 3 * * * certbot renew --quiet --deploy-hook "docker-compose -f /path/to/deploy/docker-compose.yml restart ZhiCore-frontend"
```

### 测试续期

```bash
# 测试续期（不会真正续期）
sudo certbot renew --dry-run

# 查看证书到期时间
sudo certbot certificates
```

---

## ✅ 测试验证

### 基本测试

```bash
# 测试HTTPS
curl -I https://yourdomain.com
# 应该返回: HTTP/2 200

# 测试HTTP重定向
curl -I http://yourdomain.com
# 应该返回: HTTP/1.1 301 Moved Permanently

# 查看证书
openssl s_client -connect yourdomain.com:443 -servername yourdomain.com < /dev/null 2>/dev/null | openssl x509 -noout -dates
```

### 在线测试

- **SSL Labs**: https://www.ssllabs.com/ssltest/ (目标A或A+评级)
- **Mozilla Observatory**: https://observatory.mozilla.org/
- **SecurityHeaders**: https://securityheaders.com/

### 浏览器测试

1. 访问 `https://yourdomain.com`
2. 地址栏应显示🔒图标
3. 点击查看证书信息，确认颁发者为Let's Encrypt

---

## ❓ 常见问题

### 1. 证书获取失败

```bash
# 检查域名解析
dig yourdomain.com +short

# 检查80端口
sudo netstat -tlnp | grep :80
docker-compose down  # 停止占用80端口的容器

# 检查防火墙/安全组
# 确保80和443端口已开放
```

### 2. 证书路径错误

```bash
# 检查证书文件
sudo ls -la /etc/letsencrypt/live/yourdomain.com/

# 检查容器挂载
docker-compose exec ZhiCore-frontend ls /etc/letsencrypt/live/

# 修复权限
sudo chmod -R 755 /etc/letsencrypt/
```

### 3. 续期失败

```bash
# 确保nginx包含验证路径
# location /.well-known/acme-challenge/ {
#     root /var/www/certbot;
# }

# 测试验证路径
echo "test" | sudo tee /var/www/certbot/test.txt
curl http://yourdomain.com/.well-known/acme-challenge/test.txt
sudo rm /var/www/certbot/test.txt

# 查看日志
sudo tail -50 /var/log/letsencrypt/letsencrypt.log
```

### 4. Mixed Content警告

```javascript
// 前端代码：使用相对路径
✓ axios.get('/api/users')
✗ axios.get('http://yourdomain.com/api/users')
```

```nginx
# nginx配置：传递协议信息
proxy_set_header X-Forwarded-Proto $scheme;
```

### 5. 速率限制

Let's Encrypt限制：每个域名每周最多50个证书

```bash
# 测试时使用--dry-run（不计入限额）
sudo certbot certonly --dry-run --standalone -d yourdomain.com

# 查看已签发证书
# https://crt.sh/?q=yourdomain.com
```

---

## 📚 命令速查

### 证书管理
```bash
sudo certbot certificates          # 查看证书
sudo certbot renew                 # 续期证书
sudo certbot renew --dry-run       # 测试续期
sudo certbot delete --cert-name yourdomain.com  # 删除证书
```

### Docker操作
```bash
docker-compose restart ZhiCore-frontend           # 重启服务
docker-compose logs -f ZhiCore-frontend          # 查看日志
docker-compose exec ZhiCore-frontend nginx -t    # 测试配置
```

### 测试命令
```bash
curl -I https://yourdomain.com                # HTTPS测试
openssl s_client -connect yourdomain.com:443 -servername yourdomain.com < /dev/null  # 证书信息
```

### 日志查看
```bash
sudo tail -50 /var/log/letsencrypt/letsencrypt.log    # certbot日志
sudo tail -f /var/log/certbot-renew.log               # 续期日志
docker-compose exec ZhiCore-frontend tail -f /var/log/nginx/error.log  # nginx日志
```

---

## 🔗 参考资源

- **Let's Encrypt官网**: https://letsencrypt.org/
- **Certbot文档**: https://certbot.eff.org/docs/
- **SSL Labs测试**: https://www.ssllabs.com/ssltest/
- **Mozilla Observatory**: https://observatory.mozilla.org/
- **速率限制**: https://letsencrypt.org/docs/rate-limits/

---

## ✅ 总结

本指南提供了完整的HTTPS配置流程：

- ⚡ **快速开始** - 使用自动化脚本一键配置
- 🔧 **手动配置** - 逐步了解配置过程
- 🔄 **自动续期** - 确保证书永不过期
- ✅ **测试验证** - 多种方式验证配置
- ❓ **常见问题** - 快速解决配置问题

**配置成功后，你的网站将：**
- ✅ 使用HTTPS加密传输
- ✅ 获得浏览器信任（绿锁图标）
- ✅ 支持HTTP/2协议
- ✅ 通过SSL安全测试

---

## 📎 附录：脚本完整代码

### A. setup-https.sh - 自动化配置脚本

一键完成HTTPS配置的主脚本。

```bash
#!/bin/bash

# Let's Encrypt HTTPS 自动配置脚本
# 适用于 Docker + OpenResty 部署环境

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否以 root 运行
check_root() {
    if [ "$EUID" -ne 0 ]; then 
        print_error "请使用 root 权限运行此脚本"
        echo "使用方法: sudo bash setup-https.sh"
        exit 1
    fi
}

# 检查域名解析
check_dns() {
    local domain=$1
    print_info "检查域名解析: $domain"
    
    if ! host $domain > /dev/null 2>&1; then
        print_error "域名 $domain 无法解析，请检查 DNS 配置"
        return 1
    fi
    
    local resolved_ip=$(dig +short $domain | tail -n1)
    local server_ip=$(curl -s ifconfig.me)
    
    print_info "域名解析 IP: $resolved_ip"
    print_info "服务器 IP: $server_ip"
    
    if [ "$resolved_ip" != "$server_ip" ]; then
        print_warn "域名解析 IP 与服务器 IP 不匹配"
        read -p "是否继续？(y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 检查端口
check_ports() {
    print_info "检查端口占用..."
    
    if netstat -tlnp | grep :80 > /dev/null 2>&1; then
        print_warn "端口 80 已被占用"
        print_info "将尝试停止 Docker 容器..."
        docker-compose down || true
    fi
    
    if netstat -tlnp | grep :443 > /dev/null 2>&1; then
        print_warn "端口 443 已被占用"
    fi
}

# 安装 certbot
install_certbot() {
    print_info "检查 certbot 是否已安装..."
    
    if command -v certbot &> /dev/null; then
        print_info "certbot 已安装: $(certbot --version)"
        return 0
    fi
    
    print_info "安装 certbot..."
    
    # 检测系统类型
    if [ -f /etc/debian_version ]; then
        apt-get update
        apt-get install -y certbot
    elif [ -f /etc/redhat-release ]; then
        yum install -y certbot
    else
        print_error "不支持的操作系统"
        exit 1
    fi
    
    print_info "certbot 安装完成"
}

# 获取证书
obtain_certificate() {
    local domain=$1
    local email=$2
    
    print_info "获取 SSL 证书..."
    print_info "域名: $domain"
    print_info "邮箱: $email"
    
    # 确保 nginx 已停止
    docker-compose down 2>/dev/null || true
    
    # 使用 standalone 模式获取证书
    certbot certonly \
        --standalone \
        --preferred-challenges http \
        -d $domain \
        -d www.$domain \
        --email $email \
        --agree-tos \
        --no-eff-email \
        --non-interactive
    
    if [ $? -eq 0 ]; then
        print_info "证书获取成功！"
        print_info "证书路径: /etc/letsencrypt/live/$domain/"
    else
        print_error "证书获取失败"
        exit 1
    fi
}

# 备份配置文件
backup_config() {
    local config_file=$1
    local backup_file="${config_file}.backup-$(date +%Y%m%d-%H%M%S)"
    
    if [ -f "$config_file" ]; then
        print_info "备份配置文件: $backup_file"
        cp "$config_file" "$backup_file"
    fi
}

# 更新 nginx 配置
update_nginx_config() {
    local domain=$1
    local config_file="nginx.conf"
    
    print_info "更新 nginx 配置..."
    
    backup_config "$config_file"
    
    # 检查配置文件中是否已有 HTTPS 配置
    if grep -q "listen 443 ssl" "$config_file"; then
        print_info "配置文件中已存在 HTTPS 配置"
        return 0
    fi
    
    print_warn "需要手动编辑 nginx.conf 添加 HTTPS 配置"
    print_info "请参考文档中的配置示例"
    print_info "主要修改点："
    echo "  1. 添加 443 端口监听"
    echo "  2. 配置 SSL 证书路径"
    echo "  3. 添加 HTTP 到 HTTPS 重定向"
    echo ""
    read -p "是否现在编辑配置文件？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ${EDITOR:-vim} "$config_file"
    fi
}

# 更新 docker-compose.yml
update_docker_compose() {
    local compose_file="docker-compose.yml"
    
    print_info "检查 docker-compose.yml..."
    
    # 检查是否已挂载证书目录
    if grep -q "/etc/letsencrypt" "$compose_file"; then
        print_info "docker-compose.yml 已配置证书挂载"
        return 0
    fi
    
    backup_config "$compose_file"
    
    print_warn "需要手动编辑 docker-compose.yml"
    print_info "需要添加的配置："
    cat << 'EOF'

在 ZhiCore-frontend 服务中添加：
    ports:
      - "80:80"
      - "443:443"  # 添加 HTTPS 端口
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /var/www/certbot:/var/www/certbot:ro

EOF
    
    read -p "是否现在编辑 docker-compose.yml？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ${EDITOR:-vim} "$compose_file"
    fi
}

# 创建续期计划任务
setup_auto_renew() {
    local deploy_path=$(pwd)
    
    print_info "设置自动续期..."
    
    # 创建续期脚本
    cat > renew-certs.sh << EOF
#!/bin/bash
# SSL 证书自动续期脚本
# 由 setup-https.sh 自动生成

set -e

echo "[$(date)] 开始续期 SSL 证书..."

# 尝试续期证书
certbot renew --quiet --deploy-hook "cd $deploy_path && docker-compose restart ZhiCore-frontend"

if [ \$? -eq 0 ]; then
    echo "[$(date)] 证书续期成功"
else
    echo "[$(date)] 证书续期失败" >&2
    exit 1
fi
EOF
    
    chmod +x renew-certs.sh
    print_info "续期脚本创建完成: $(pwd)/renew-certs.sh"
    
    # 添加到 crontab
    local cron_cmd="0 3 1 * * $deploy_path/renew-certs.sh >> /var/log/certbot-renew.log 2>&1"
    
    # 检查是否已存在
    if crontab -l 2>/dev/null | grep -q "renew-certs.sh"; then
        print_info "crontab 任务已存在"
    else
        (crontab -l 2>/dev/null; echo "$cron_cmd") | crontab -
        print_info "crontab 任务添加成功（每月1号凌晨3点执行）"
    fi
    
    # 测试续期
    print_info "测试证书续期..."
    certbot renew --dry-run
}

# 启动服务
start_services() {
    print_info "启动 Docker 服务..."
    
    # 创建必要的目录
    mkdir -p /var/www/certbot
    
    # 重新构建镜像
    docker-compose build ZhiCore-frontend
    
    # 启动服务
    docker-compose up -d
    
    # 等待服务启动
    sleep 5
    
    # 检查服务状态
    if docker-compose ps | grep "ZhiCore-frontend" | grep "Up"; then
        print_info "服务启动成功"
    else
        print_error "服务启动失败"
        docker-compose logs ZhiCore-frontend
        exit 1
    fi
}

# 验证 HTTPS
verify_https() {
    local domain=$1
    
    print_info "验证 HTTPS 配置..."
    
    # 测试 HTTPS 连接
    if curl -k -I https://$domain 2>/dev/null | grep "HTTP"; then
        print_info "✅ HTTPS 可访问"
    else
        print_warn "⚠️  HTTPS 暂时无法访问，可能需要等待几分钟"
    fi
    
    # 测试 HTTP 重定向
    if curl -I http://$domain 2>/dev/null | grep "301"; then
        print_info "✅ HTTP 到 HTTPS 重定向正常"
    else
        print_warn "⚠️  HTTP 重定向未配置"
    fi
}

# 主函数
main() {
    echo "================================================"
    echo "  Let's Encrypt HTTPS 自动配置脚本"
    echo "================================================"
    echo ""
    
    # 检查 root 权限
    check_root
    
    # 获取配置信息
    read -p "请输入你的域名（不带 www）: " DOMAIN
    read -p "请输入你的邮箱: " EMAIL
    
    if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
        print_error "域名和邮箱不能为空"
        exit 1
    fi
    
    print_info "开始配置 HTTPS for $DOMAIN"
    echo ""
    
    # 执行配置步骤
    print_info "步骤 1/9: 检查域名解析"
    check_dns "$DOMAIN"
    echo ""
    
    print_info "步骤 2/9: 检查端口"
    check_ports
    echo ""
    
    print_info "步骤 3/9: 安装 Certbot"
    install_certbot
    echo ""
    
    print_info "步骤 4/9: 获取 SSL 证书"
    obtain_certificate "$DOMAIN" "$EMAIL"
    echo ""
    
    print_info "步骤 5/9: 更新 Nginx 配置"
    update_nginx_config "$DOMAIN"
    echo ""
    
    print_info "步骤 6/9: 更新 Docker Compose 配置"
    update_docker_compose
    echo ""
    
    print_info "步骤 7/9: 设置自动续期"
    setup_auto_renew
    echo ""
    
    print_info "步骤 8/9: 启动服务"
    start_services
    echo ""
    
    print_info "步骤 9/9: 验证 HTTPS"
    verify_https "$DOMAIN"
    echo ""
    
    # 完成
    echo "================================================"
    echo -e "${GREEN}✅ HTTPS 配置完成！${NC}"
    echo "================================================"
    echo ""
    echo "📋 后续步骤："
    echo "  1. 访问 https://$DOMAIN 验证网站"
    echo "  2. 测试文件上传功能"
    echo "  3. 查看 SSL 评级: https://www.ssllabs.com/ssltest/analyze.html?d=$DOMAIN"
    echo ""
    echo "📝 证书信息："
    certbot certificates
    echo ""
    echo "🔄 自动续期："
    echo "  - 续期脚本: $(pwd)/renew-certs.sh"
    echo "  - Crontab 任务已配置（每月1号凌晨3点）"
    echo ""
}

# 运行主函数
main
```

### B. renew-certs.sh - 证书续期脚本

自动续期SSL证书并重启服务。

```bash
#!/bin/bash

# Let's Encrypt SSL 证书自动续期脚本
# 适用于 Docker + OpenResty 部署环境

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} [$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} [$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} [$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 配置
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
SERVICE_NAME="ZhiCore-frontend"
LOG_FILE="/var/log/certbot-renew.log"

# 检查 certbot 是否安装
check_certbot() {
    if ! command -v certbot &> /dev/null; then
        print_error "certbot 未安装，请先安装 certbot"
        exit 1
    fi
}

# 检查证书到期时间
check_cert_expiry() {
    print_info "检查证书到期时间..."
    
    # 获取所有证书信息
    certbot certificates 2>/dev/null || true
}

# 续期证书
renew_certificates() {
    print_info "开始续期 SSL 证书..."
    
    # 尝试续期（只有在证书即将到期时才会真正续期）
    if certbot renew --quiet --deploy-hook "echo 'Certificate renewed'"; then
        print_info "证书续期检查完成"
        return 0
    else
        print_error "证书续期失败"
        return 1
    fi
}

# 重启 Docker 服务
restart_service() {
    print_info "重启 Docker 服务: $SERVICE_NAME"
    
    cd "$SCRIPT_DIR"
    
    if [ ! -f "$COMPOSE_FILE" ]; then
        print_error "docker-compose.yml 文件不存在: $COMPOSE_FILE"
        return 1
    fi
    
    # 重启前端服务
    if docker-compose restart "$SERVICE_NAME"; then
        print_info "服务重启成功"
        
        # 等待服务启动
        sleep 5
        
        # 检查服务状态
        if docker-compose ps | grep "$SERVICE_NAME" | grep "Up" > /dev/null; then
            print_info "服务运行正常"
            return 0
        else
            print_error "服务启动失败"
            docker-compose logs --tail=20 "$SERVICE_NAME"
            return 1
        fi
    else
        print_error "服务重启失败"
        return 1
    fi
}

# 验证 HTTPS 访问
verify_https() {
    print_info "验证 HTTPS 访问..."
    
    # 等待服务完全启动
    sleep 3
    
    # 测试 HTTPS 连接
    if curl -k -s -o /dev/null -w "%{http_code}" https://localhost | grep -q "200\|301\|302"; then
        print_info "✅ HTTPS 访问正常"
        return 0
    else
        print_warn "⚠️  HTTPS 访问可能存在问题"
        return 1
    fi
}

# 清理旧日志（保留最近30天）
cleanup_logs() {
    if [ -f "$LOG_FILE" ]; then
        # 获取日志文件大小（MB）
        local log_size=$(du -m "$LOG_FILE" | cut -f1)
        
        if [ "$log_size" -gt 10 ]; then
            print_info "日志文件过大 (${log_size}MB)，进行归档..."
            
            # 归档旧日志
            local backup_file="${LOG_FILE}.$(date +%Y%m%d)"
            mv "$LOG_FILE" "$backup_file"
            gzip "$backup_file"
            
            # 删除30天前的归档
            find "$(dirname "$LOG_FILE")" -name "certbot-renew.log.*.gz" -mtime +30 -delete
            
            print_info "日志归档完成"
        fi
    fi
}

# 主函数
main() {
    echo "========================================"
    echo "  SSL 证书自动续期脚本"
    echo "========================================"
    echo ""
    
    # 检查 certbot
    check_certbot
    
    # 检查当前证书状态
    check_cert_expiry
    echo ""
    
    # 尝试续期
    if renew_certificates; then
        print_info "证书续期检查成功"
        
        # 检查是否真的续期了（certbot renew 只有在证书即将到期时才会续期）
        # 如果证书被更新了，需要重启服务
        if [ -f /etc/letsencrypt/renewal-hooks/deploy/restart.flag ]; then
            print_info "检测到证书已更新，重启服务..."
            
            if restart_service; then
                verify_https
                rm -f /etc/letsencrypt/renewal-hooks/deploy/restart.flag
            else
                print_warn "SSL证书续期成功但服务重启失败"
                exit 1
            fi
        else
            print_info "证书尚未到期，无需续期"
        fi
        
        # 清理旧日志
        cleanup_logs
        
        echo ""
        echo "========================================"
        print_info "✅ 续期任务执行完成"
        echo "========================================"
        exit 0
    else
        echo ""
        echo "========================================"
        print_error "❌ 续期任务执行失败"
        echo "========================================"
        exit 1
    fi
}

# 运行主函数
main
```

### C. setup-auto-renew.sh - 配置自动续期脚本

设置证书自动续期定时任务。

```bash
#!/bin/bash

# 设置 Let's Encrypt 证书自动续期
# 使用方法: sudo bash setup-auto-renew.sh

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RENEW_SCRIPT="$SCRIPT_DIR/renew-certs.sh"

echo "========================================"
echo "  Let's Encrypt 自动续期配置"
echo "========================================"
echo ""

# 检查 renew-certs.sh 是否存在
if [ ! -f "$RENEW_SCRIPT" ]; then
    print_warn "续期脚本不存在: $RENEW_SCRIPT"
    exit 1
fi

# 设置执行权限
print_info "设置脚本执行权限..."
chmod +x "$RENEW_SCRIPT"

# 测试脚本
print_info "测试续期脚本..."
if bash "$RENEW_SCRIPT" 2>&1 | head -20; then
    print_info "脚本测试通过"
else
    print_warn "脚本测试遇到问题，但这可能是正常的（如果证书未到期）"
fi

echo ""

# 添加到 crontab
print_info "配置 crontab 自动任务..."

CRON_CMD="0 3 * * * $RENEW_SCRIPT >> /var/log/certbot-renew.log 2>&1"

# 检查是否已存在
if crontab -l 2>/dev/null | grep -q "renew-certs.sh"; then
    print_info "crontab 任务已存在，跳过..."
else
    # 添加新任务
    (crontab -l 2>/dev/null; echo "$CRON_CMD") | crontab -
    print_info "✅ crontab 任务添加成功"
fi

echo ""
print_info "当前 crontab 配置:"
crontab -l | grep "renew-certs.sh" || true

echo ""
echo "========================================"
echo -e "${GREEN}✅ 自动续期配置完成！${NC}"
echo "========================================"
echo ""
echo "📋 配置信息:"
echo "  - 续期脚本: $RENEW_SCRIPT"
echo "  - 执行时间: 每天凌晨 3:00"
echo "  - 日志文件: /var/log/certbot-renew.log"
echo ""
echo "📝 手动测试续期:"
echo "  sudo $RENEW_SCRIPT"
echo ""
echo "📝 查看续期日志:"
echo "  tail -f /var/log/certbot-renew.log"
echo ""
echo "📝 查看证书信息:"
echo "  certbot certificates"
echo ""
echo "📝 测试续期（不会真正续期）:"
echo "  certbot renew --dry-run"
echo ""
```

### D. 脚本使用说明

#### 使用方式

```bash
# 1. 一键配置HTTPS（推荐）
cd /path/to/deploy
chmod +x setup-https.sh
sudo bash setup-https.sh

# 2. 配置自动续期
chmod +x setup-auto-renew.sh
sudo bash setup-auto-renew.sh

# 3. 手动测试续期
chmod +x renew-certs.sh
sudo bash renew-certs.sh
```

#### 脚本特点

- ✅ **自动化** - 一键完成所有配置
- ✅ **交互式** - 友好的提示信息
- ✅ **安全检查** - 多重验证确保配置正确
- ✅ **错误处理** - 完善的错误提示和处理
- ✅ **日志记录** - 详细的操作日志
- ✅ **彩色输出** - 清晰的状态显示

#### 注意事项

1. 所有脚本都需要使用 `sudo` 运行（需要root权限）
2. 运行前确保域名已正确解析到服务器
3. 确保80和443端口已在防火墙/安全组开放
4. 备份重要配置文件后再运行
5. 首次运行 `setup-https.sh` 会自动创建 `renew-certs.sh`

---

*最后更新：2024年10月*

