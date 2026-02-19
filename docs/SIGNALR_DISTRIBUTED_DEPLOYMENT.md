# SignalR 分布式部署完整实现方案

## 概述

为了支持博客系统的分布式部署，我们对 ChatHub 进行了全面改造，使其能够在多服务器实例之间正确同步连接状态和消息。

## 核心问题

在分布式部署环境下，SignalR 面临以下问题：

1. **连接状态分散**：每个服务器实例只知道连接到自己的客户端
2. **跨实例通信困难**：用户A连接到服务器1，用户B连接到服务器2，无法直接通信
3. **在线状态不准确**：无法准确判断用户是否真的在线

## 解决方案

### 1. Redis 连接管理（Connection Management）

使用 Redis 统一存储和管理所有 SignalR 连接信息。

#### 数据结构设计

```
# 用户的连接ID集合（支持多设备）
blog:signalr:connections:{userId} -> Set<connectionId>

# 连接ID到用户ID的反向映射
blog:signalr:connection_user:{connectionId} -> userId

# 用户在线状态（TTL 30分钟）
blog:signalr:online:{userId} -> timestamp
```

#### 核心服务：`SignalRConnectionService`

```csharp
public interface ISignalRConnectionService
{
    // 添加连接
    Task AddConnectionAsync(string userId, string connectionId);
    
    // 移除连接
    Task RemoveConnectionAsync(string userId, string connectionId);
    
    // 获取用户的所有连接ID
    Task<List<string>> GetUserConnectionIdsAsync(string userId);
    
    // 检查用户是否在线
    Task<bool> IsUserOnlineAsync(string userId);
}
```

### 2. Redis Backplane（消息总线）

使用 SignalR 的 Redis Backplane 功能，实现跨服务器实例的消息广播。

#### 工作原理

```
服务器实例1              Redis Backplane              服务器实例2
    |                        |                            |
    | -- SendMessage -->     |                            |
    |                   [发布消息]                         |
    |                        |  -- 订阅通知 -->           |
    |                        |                   [接收并转发给客户端]
```

#### 配置

在 `Program.cs` 中添加：

```csharp
builder.Services.AddSignalR()
    .AddStackExchangeRedis(connectionString, options =>
    {
        options.Configuration.ChannelPrefix = RedisChannel.Literal("signalr:");
    });
```

## 收发消息处理流程

### 发送消息流程

```
1. 用户A（连接到服务器1）调用 SendMessage()
   └─> ChatHub.SendMessage(receiverId, content)

2. 保存消息到数据库
   └─> messageService.SendMessageAsync()

3. 获取接收者的所有连接ID（从Redis）
   └─> connectionService.GetUserConnectionIdsAsync(receiverId)
   返回：["conn1", "conn2", "conn3"]  // 可能分布在不同服务器

4. 通过 SignalR 客户端API发送消息
   └─> Clients.Clients(connectionIds).SendAsync("ReceiveMessage", message)
   
5. Redis Backplane 自动处理：
   - 如果连接在当前服务器：直接发送
   - 如果连接在其他服务器：通过Redis发布消息
   - 其他服务器订阅到消息后，转发给对应的客户端连接

6. 发送给发送者自己（多设备同步）
   └─> Clients.Caller.SendAsync("MessageSent", message)
```

### 关键代码

```csharp
public async Task SendMessage(string receiverId, string content, string messageType = "text")
{
    var senderId = GetUserId();
    
    // 1. 保存消息到数据库
    var message = await messageService.SendMessageAsync(senderId, req);

    // 2. 获取接收者的所有连接ID（从Redis，可能在不同服务器）
    var receiverConnectionIds = await GetUserConnectionIdsAsync(receiverId);
    
    // 3. 发送给接收者（Redis Backplane自动处理跨服务器通信）
    if (receiverConnectionIds.Any())
    {
        await Clients.Clients(receiverConnectionIds)
            .SendAsync(SignalREvents.ReceiveMessage, message);
    }

    // 4. 发送给发送者自己（多设备同步）
    await Clients.Caller.SendAsync(SignalREvents.MessageSent, message);
}
```

## 在线状态管理

### 连接时

```csharp
public override async Task OnConnectedAsync()
{
    var userId = GetUserId();
    
    // 1. 添加连接到Redis
    await connectionService.AddConnectionAsync(userId, Context.ConnectionId);
    
    // 2. 通知其他用户该用户上线
    await Clients.Others.SendAsync(SignalREvents.UserOnline, userId);
    
    // 3. 推送上线同步数据
    // ...
}
```

### 断开连接时

```csharp
public override async Task OnDisconnectedAsync(Exception? exception)
{
    var userId = GetUserId();
    
    // 1. 从Redis移除连接
    await connectionService.RemoveConnectionAsync(userId, Context.ConnectionId);
    
    // 2. 检查用户是否还有其他连接
    var isStillOnline = await connectionService.IsUserOnlineAsync(userId);
    
    // 3. 只有当用户所有连接都断开时才通知离线
    if (!isStillOnline)
    {
        await Clients.Others.SendAsync(SignalREvents.UserOffline, userId);
    }
}
```

## 消息送达确认

```csharp
public async Task ConfirmMessageDelivered(long messageId, string senderId)
{
    var receiverId = GetUserId();
    
    // 通知发送者：消息已送达对方设备（跨服务器）
    var senderConnectionIds = await GetUserConnectionIdsAsync(senderId);
    if (senderConnectionIds.Any())
    {
        await Clients.Clients(senderConnectionIds)
            .SendAsync(SignalREvents.MessageDelivered, messageId, receiverId);
    }
}
```

## 部署配置

### 环境变量

```bash
# Redis 配置
REDIS_ENABLED=true
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your_password

# 或者使用连接字符串
REDIS_CONNECTION_STRING=redis.example.com:6379,password=your_password
```

### Docker Compose 示例

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass your_password
    volumes:
      - redis-data:/data
    networks:
      - blog-network

  blog-backend-1:
    image: blog-backend:latest
    environment:
      - REDIS_ENABLED=true
      - REDIS_CONNECTION_STRING=redis:6379,password=your_password
    depends_on:
      - redis
    networks:
      - blog-network

  blog-backend-2:
    image: blog-backend:latest
    environment:
      - REDIS_ENABLED=true
      - REDIS_CONNECTION_STRING=redis:6379,password=your_password
    depends_on:
      - redis
    networks:
      - blog-network

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - blog-backend-1
      - blog-backend-2
    networks:
      - blog-network

networks:
  blog-network:

volumes:
  redis-data:
```

### Nginx 配置（负载均衡）

```nginx
upstream backend_servers {
    # 使用 IP hash 确保 WebSocket 连接的粘性
    ip_hash;
    
    server blog-backend-1:5000;
    server blog-backend-2:5000;
}

server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://backend_servers;
        proxy_http_version 1.1;
        
        # WebSocket 支持
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时设置
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
    }
}
```

## 关键优势

### 1. 真正的分布式支持

- ✅ 多服务器实例可以独立扩展
- ✅ 用户可以连接到任意服务器实例
- ✅ 消息可以跨服务器实例传递

### 2. 高可用性

- ✅ 单个服务器实例故障不影响其他实例
- ✅ 连接状态在 Redis 中持久化
- ✅ 支持零停机更新（滚动部署）

### 3. 多设备支持

- ✅ 同一用户可以在多个设备同时在线
- ✅ 消息自动同步到所有设备
- ✅ 准确的在线状态判断

### 4. 性能优化

- ✅ Redis 存储连接信息，查询速度快
- ✅ Backplane 使用发布订阅模式，效率高
- ✅ 只有实际需要时才跨服务器通信

## 测试验证

### 1. 单实例测试

```bash
# 启动单个实例
dotnet run

# 验证功能
- 用户登录
- 发送消息
- 接收消息
- 在线状态
```

### 2. 多实例测试

```bash
# 启动两个实例
dotnet run --urls "http://localhost:5000"
dotnet run --urls "http://localhost:5001"

# 验证场景
1. 用户A连接到实例1，用户B连接到实例2
2. 用户A发送消息给用户B
3. 用户B应该能收到消息（通过 Redis Backplane）
4. 断开用户A的连接，用户B应该看到用户A离线
```

### 3. Redis 验证

```bash
# 连接到 Redis
redis-cli

# 查看连接信息
SMEMBERS blog:signalr:connections:{userId}

# 查看在线状态
EXISTS blog:signalr:online:{userId}

# 监控 SignalR 消息
PSUBSCRIBE signalr:*
```

## 监控和调试

### 日志输出

系统会输出以下关键日志：

```
✅ SignalR Redis Backplane 已启用，支持分布式部署
用户 {UserId} 连接到聊天 Hub，ConnectionId: {ConnectionId}
成功添加SignalR连接: {UserId} -> {ConnectionId}
消息发送成功: {SenderId} -> {ReceiverId}
用户离线: {UserId} (最后连接: {ConnectionId})
```

### Redis 监控

```bash
# 查看 Redis 键数量
redis-cli DBSIZE

# 查看内存使用
redis-cli INFO memory

# 查看发布订阅频道
redis-cli PUBSUB CHANNELS signalr:*

# 查看订阅者数量
redis-cli PUBSUB NUMSUB signalr:all
```

## 故障排查

### 问题：消息无法跨服务器传递

**检查项：**
1. Redis 是否正常运行？
2. Redis Backplane 是否配置成功？
3. 网络是否畅通？

**解决方法：**
```bash
# 检查 Redis 连接
redis-cli ping

# 查看 SignalR 日志
# 应该看到 "SignalR Redis Backplane 已启用" 的消息
```

### 问题：用户在线状态不准确

**检查项：**
1. 连接是否正确添加到 Redis？
2. 断开连接时是否正确清理？
3. Redis TTL 是否合理？

**解决方法：**
```bash
# 查看用户连接
redis-cli SMEMBERS blog:signalr:connections:{userId}

# 查看连接映射
redis-cli GET blog:signalr:connection_user:{connectionId}

# 查看在线状态
redis-cli GET blog:signalr:online:{userId}
redis-cli TTL blog:signalr:online:{userId}
```

### 问题：Redis 内存占用过高

**检查项：**
1. 过期连接是否被清理？
2. TTL 是否设置正确？

**解决方法：**
```bash
# 手动清理过期数据
redis-cli KEYS "blog:signalr:connection_user:*" | xargs redis-cli DEL

# 调整 Redis 内存策略
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

## 性能建议

### 1. Redis 配置优化

```conf
# redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
save ""  # 禁用 RDB 持久化（连接状态可以丢失）
appendonly no  # 禁用 AOF 持久化
```

### 2. SignalR 配置优化

```csharp
builder.Services.AddSignalR(options =>
{
    options.ClientTimeoutInterval = TimeSpan.FromSeconds(60);
    options.KeepAliveInterval = TimeSpan.FromSeconds(30);
    options.EnableDetailedErrors = false;  // 生产环境关闭详细错误
});
```

### 3. 连接池配置

```csharp
// Redis 连接池配置
var connectionString = "redis:6379,password=xxx,connectTimeout=5000,syncTimeout=5000,connectRetry=3";
```

## 总结

通过引入 Redis 连接管理和 SignalR Redis Backplane，我们实现了：

1. **完整的分布式支持**：多服务器实例可以无缝协作
2. **统一的连接管理**：所有连接信息存储在 Redis 中
3. **跨服务器消息传递**：通过 Backplane 自动处理
4. **准确的在线状态**：基于 Redis 的集中式状态管理
5. **多设备支持**：同一用户可以在多个设备同时在线

这套方案已经过充分测试，可以支撑大规模分布式部署场景。

