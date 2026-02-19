# 评论缓存架构设计

## 缓存分层结构

评论系统采用**混合存储方案**：静态内容用String，动态统计用Hash。

### 1. 静态内容缓存（String + JSON）

**键名规范**：`comments:{cid}:obj`

**存储内容**（不可变字段）：
```json
{
  "id": 123,
  "content": "评论内容",
  "authorId": "user456",
  "authorName": "张三",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "createTime": "2024-12-24T09:00:00Z",
  "parentId": 100,
  "rootId": 50,
  "postId": 10
}
```

**设计理由**：
- ✅ 静态内容几乎不变，适合整体读写
- ✅ JSON序列化性能可接受（有上层应用缓存）
- ✅ 代码简洁，不需要Lua脚本合并
- ✅ 语义清晰：不可变对象用不可变存储

**TTL策略**：24小时
```bash
SET comments:{cid}:obj '{json}' EX 86400
```

**为什么不用Hash？**
- ❌ 静态内容总是整体读取，Hash的部分字段读取优势用不上
- ❌ 不需要HSET单字段更新（内容不变）
- ❌ Hash+Hash需要Lua脚本合并，增加复杂度

---

### 2. 动态统计缓存（Hash）

**键名规范**：`comments:{cid}:stats`

**存储内容**（可变字段）：
```bash
HSET comments:{cid}:stats 
  like_count 100
  reply_count 50
```

**设计理由**：
- ✅ **HINCRBY原子更新**（点赞/回复高频操作的核心需求）
- ✅ **并发安全**，无需乐观锁
- ✅ **性能极高**，O(1)时间复杂度

**TTL策略**：2小时
```bash
EXPIRE comments:{cid}:stats 7200
```

**为什么不用String？**
- ❌ 更新点赞数需要：GET → 反序列化 → 修改 → 序列化 → SET（5步操作）
- ❌ 并发场景需要WATCH+MULTI乐观锁（复杂且性能差）
- ❌ 高并发下CPU和网络开销是Hash的**数倍**

---

### 3. 点赞关系缓存（Set）

**键名规范**：`comments:{cid}:likes`

**存储内容**：
```bash
SADD comments:{cid}:likes user123 user456 user789
```

**用途**：
- 去重检查（防止重复点赞）
- 点赞状态显示（红心/灰心）

**TTL策略**：与stats统一，2小时
```bash
EXPIRE comments:{cid}:likes 7200
```

**注意事项**：
- ⚠️ Set和Hash的TTL必须**严格一致**，避免不同步问题
- ⚠️ 批量设置TTL时使用Pipeline确保原子性

---

### 4. 排序索引缓存（ZSet）

#### 4.1 时间排序索引
**键名规范**：`posts:{postId}:comments:time`

```bash
# score = 创建时间戳
ZADD posts:{postId}:comments:time 
  1735023600 123  # 评论123的创建时间
  1735023660 124
  1735023720 125
```

**用途**：按时间倒序获取评论列表
```bash
# 获取最新20条评论ID
ZREVRANGE posts:{postId}:comments:time 0 19
```

#### 4.2 热度排序索引
**键名规范**：`posts:{postId}:comments:hot`

```bash
# score = 热度分数
ZADD posts:{postId}:comments:hot
  8532 123  # 评论123的热度分数
  7821 124
  6543 125
```

**用途**：按热度倒序获取评论列表
```bash
# 获取热度最高20条评论ID
ZREVRANGE posts:{postId}:comments:hot 0 19
```

**TTL策略**：1小时（索引可快速重建）
```bash
EXPIRE posts:{postId}:comments:time 3600
EXPIRE posts:{postId}:comments:hot 3600
```

---

### 5. 预览回复缓存（List）

**键名规范**：`comments:{rootId}:preview`

**存储内容**：
```bash
# 缓存每个根评论的前3条高赞回复ID
RPUSH comments:{rootId}:preview 201 202 203
```

**用途**：
- 评论列表页展示"查看更多回复"预览
- 减少数据库查询

**TTL策略**：30分钟
```bash
EXPIRE comments:{rootId}:preview 1800
```

---

## 差异化TTL策略

| 缓存类型 | TTL | 理由 |
|---------|-----|------|
| 静态内容 (String) | 24小时 | 内容不变，长期驻留节省查询 |
| 动态统计 (Hash) | 2小时 | 频繁变化，定期从数据库刷新 |
| 点赞关系 (Set) | 2小时 | 与统计同步，避免不一致 |
| 排序索引 (ZSet) | 1小时 | 可快速重建，不占用过多内存 |
| 预览回复 (List) | 30分钟 | 实时性要求高 |

**TTL设计原则**：
1. **不可变数据长TTL**（静态内容）
2. **可变数据短TTL**（统计、关系）
3. **可重建数据中TTL**（索引、预览）

---

## 批量操作优化

### Pipeline批量读取
```bash
# 读取100条评论
Pipeline {
  GET comments:1:obj
  HGETALL comments:1:stats
  GET comments:2:obj
  HGETALL comments:2:stats
  ...
  GET comments:100:obj
  HGETALL comments:100:stats
}
# 200个命令，1次网络往返
```

**性能优势**：
- 100条评论：1ms网络延迟 vs 100ms（单条查询）
- **100倍性能提升**

### 使用Hash Tag保证同节点（Redis Cluster）
```bash
# 确保同一评论的所有键在同一节点
comments:{123}:obj     → Node 1
comments:{123}:stats   → Node 1  # {123}是Hash Tag
comments:{123}:likes   → Node 1
```

**优势**：
- Pipeline操作不会跨节点
- 事务操作（MULTI/EXEC）可用

---

## 缓存一致性保证

### 1. 回填策略（Cache-Aside）
```
查询流程：
1. 尝试从Redis读取
   ↓ 未命中
2. 从数据库查询
   ↓
3. 异步回填Redis
   ↓
4. 返回数据
```

**代码示例**：
```csharp
// 缓存未命中时自动回填
if (cachedData == null)
{
    var dbData = await LoadFromDatabase(cid);
    _ = Task.Run(() => CacheToRedisAsync(dbData)); // 异步回填
    return dbData;
}
```

### 2. 失效策略

**何时失效缓存？**
- ✅ 用户修改昵称/头像 → 删除该用户所有评论的静态缓存
- ✅ 评论被删除 → 删除该评论的所有缓存
- ✅ 评论被编辑 → 删除静态内容缓存

**批量失效**：
```bash
# 用户改昵称，删除其所有评论缓存
DEL comments:1:obj comments:5:obj comments:10:obj ...
# 统计缓存不受影响，自然过期即可
```

### 3. 统计一致性（最终一致）

**流程**：
```
1. 用户点赞
   ↓
2. SADD检查去重
   ↓
3. 发送MQ消息
   ↓
4. 消费者：
   - 事务更新数据库
   - HINCRBY更新Redis
   ↓
5. 1-2秒后数据一致
```

**容错**：
- Redis故障 → 从数据库读取
- 消费者故障 → 重试机制保证最终一致

---

## 缓存键命名规范

| 数据类型 | 键名格式 | 示例 |
|---------|---------|------|
| 静态内容 | `comments:{cid}:obj` | `comments:123:obj` |
| 动态统计 | `comments:{cid}:stats` | `comments:123:stats` |
| 点赞关系 | `comments:{cid}:likes` | `comments:123:likes` |
| 预览回复 | `comments:{rootId}:preview` | `comments:50:preview` |
| 时间索引 | `posts:{postId}:comments:time` | `posts:10:comments:time` |
| 热度索引 | `posts:{postId}:comments:hot` | `posts:10:comments:hot` |

**规范原则**：
1. 使用复数 `comments`（统一风格）
2. 使用冒号分隔（Redis约定）
3. 使用 `{cid}` Hash Tag（Cluster支持）
4. 语义清晰（obj/stats/likes）

---

## 缓存预热策略

**策略选择**：**依赖懒加载（Cache-Aside），无需主动预热**

**理由**：
1. ✅ **评论数据访问模式分散**：不像热点商品，评论分布在大量文章中
2. ✅ **缓存未命中性能可接受**：单次数据库查询 + 异步回填 < 50ms
3. ✅ **避免资源浪费**：预热百万级评论会占用大量Redis内存
4. ✅ **自然淘汰冷数据**：TTL机制保证冷门评论自动过期

**懒加载流程**：
```
1. 查询Redis（未命中）
   ↓
2. 查询数据库（50ms）
   ↓
3. 异步回填Redis（不阻塞响应）
   ↓
4. 返回数据给用户
```

**特殊情况**：
- 热门文章发布时，评论访问量激增 → 依赖Pipeline批量查询优化
- Redis重启后，热门评论会在5-10分钟内自然回填完成

---

## 缓存三大问题防护

### 1. 缓存穿透（查询不存在的数据）

**场景**：恶意用户查询不存在的评论ID（如cid=999999999）

**防护措施**：
```python
# 方案A：缓存空值（推荐）
if comment == null:
    # 缓存空对象，TTL 5分钟
    SET comments:{cid}:obj "null" EX 300
    return null

# 方案B：布隆过滤器（可选，适用于海量数据）
if not BloomFilter.exists(cid):
    return null  # 直接拒绝，不查数据库
```

### 2. 缓存击穿（热点数据过期）

**场景**：热门评论缓存同时过期，大量请求瞬间打到数据库

**防护措施**：
```python
# 方案A：TTL加随机抖动
ttl = 7200 + random(0, 600)  # 2小时 ± 10分钟
SET comments:{cid}:obj {json} EX ttl

# 方案B：互斥锁（热点评论）
lock_key = f"lock:comments:{cid}"
if SETNX(lock_key, 1, 10):  # 10秒锁
    # 获取锁成功，重建缓存
    data = LoadFromDatabase(cid)
    CacheToRedis(data)
    DEL(lock_key)
else:
    # 等待100ms后重试
    sleep(0.1)
    return GetFromCache(cid)
```

### 3. 缓存雪崩（大量缓存同时失效）

**场景**：Redis重启或大批量数据同时过期

**防护措施**：
```python
# 方案A：差异化TTL（已采用）
静态内容：24小时 ± random(0, 3600)
动态统计：2小时 ± random(0, 600)
点赞关系：2小时 ± random(0, 600)

# 方案B：持久化保护
Redis配置：
- AOF持久化：always或everysec
- RDB快照：每小时一次

# 方案C：降级方案
if RedisDown:
    # 直接查询数据库，限流保护
    return QueryDBWithRateLimit(cid)
```

**实施优先级**：
- ✅ **缓存穿透**：缓存空值（已实施）
- ✅ **缓存雪崩**：TTL随机抖动（已采用差异化TTL）
- ⚠️ **缓存击穿**：可选，仅对极热门评论实施互斥锁
