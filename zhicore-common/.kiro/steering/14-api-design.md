---
inclusion: fileMatch
fileMatchPattern: '**/*{Controller,controller,Api,api,Rest,rest}*.java'
---

# 接口设计规范

[返回索引](./README-zh.md)

---

## RESTful API 规范

### HTTP 方法语义

| 方法 | 语义 | 幂等性 | 示例 |
|------|------|--------|------|
| **GET** | 查询资源 | 是 | `GET /api/v1/users/{id}` |
| **POST** | 创建资源 | 否 | `POST /api/v1/users` |
| **PUT** | 完整更新资源 | 是 | `PUT /api/v1/users/{id}` |
| **PATCH** | 部分更新资源 | 否 | `PATCH /api/v1/users/{id}` |
| **DELETE** | 删除资源 | 是 | `DELETE /api/v1/users/{id}` |

### URL 命名规范

```
✅ 正确：
GET    /api/v1/users              # 获取用户列表
GET    /api/v1/users/{id}         # 获取单个用户
POST   /api/v1/users              # 创建用户
PUT    /api/v1/users/{id}         # 更新用户
DELETE /api/v1/users/{id}         # 删除用户
GET    /api/v1/users/{id}/posts   # 获取用户的文章列表

❌ 错误：
GET    /api/v1/getUser            # 不要在 URL 中使用动词
POST   /api/v1/user/create        # 不要使用动词
GET    /api/v1/Users              # 不要使用大写
```

---

## 版本控制

### URL 版本控制（推荐）

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 {
    // v1 版本接口
}

@RestController
@RequestMapping("/api/v2/users")
public class UserControllerV2 {
    // v2 版本接口
}
```

### 版本升级策略

- **v1 → v2**：保持 v1 接口可用，至少维护 6 个月
- **废弃通知**：提前 3 个月通知客户端
- **响应头标识**：`X-API-Version: v1`

---

## 幂等性设计

### 幂等性要求

| 场景 | 幂等性要求 | 实现方式 |
|------|-----------|---------|
| 支付 | 必须幂等 | 幂等 Key（订单号） |
| 创建订单 | 必须幂等 | 幂等 Key（业务流水号） |
| 发送消息 | 必须幂等 | 幂等 Key（消息 ID） |
| 查询 | 天然幂等 | 无需处理 |
| 删除 | 天然幂等 | 无需处理 |

### 幂等 Key 实现

```java
@PostMapping("/orders")
public Result createOrder(@RequestBody CreateOrderReq req,
                         @RequestHeader("X-Idempotent-Key") String idempotentKey) {
    // 检查幂等 Key
    if (redisTemplate.hasKey("idempotent:" + idempotentKey)) {
        return Result.success("订单已创建");
    }
    
    // 创建订单
    Order order = orderService.create(req);
    
    // 存储幂等 Key（24小时过期）
    redisTemplate.opsForValue().set(
        "idempotent:" + idempotentKey, 
        order.getId(), 
        24, 
        TimeUnit.HOURS
    );
    
    return Result.success(order);
}
```

---

## 错误码规范

### 错误码分段

| 错误码范围 | 类型 | 示例 |
|-----------|------|------|
| 10000-19999 | 系统错误 | 10001: 系统繁忙 |
| 20000-29999 | 业务错误 | 20001: 用户不存在 |
| 30000-39999 | 权限错误 | 30001: 未登录 |
| 40000-49999 | 第三方错误 | 40001: 支付失败 |

### 错误码定义

```java
@Getter
public enum ErrorCode {
    // 系统错误 10000-19999
    SYSTEM_ERROR(10000, "系统繁忙，请稍后重试"),
    PARAM_ERROR(10001, "参数错误"),
    
    // 业务错误 20000-29999
    USER_NOT_FOUND(20001, "用户不存在"),
    POST_NOT_FOUND(20002, "文章不存在"),
    
    // 权限错误 30000-39999
    UNAUTHORIZED(30001, "未登录"),
    FORBIDDEN(30002, "无权限"),
    
    // 第三方错误 40000-49999
    PAYMENT_FAILED(40001, "支付失败");
    
    private final int code;
    private final String message;
---

## 分页规范

### 分页策略选择

| 策略 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **偏移分页** | 小数据集、需要跳页 | 实现简单、支持跳页 | 深分页性能差 |
| **游标分页** | 大数据集、瀑布流 | 性能稳定、无重复/遗漏 | 不支持跳页 |
| **键集分页** | 有序数据、时间线 | 性能好 | 需要唯一排序键 |

### 分页请求参数

```java
// 偏移分页（小数据集）
@GetMapping("/api/v1/posts")
public Result<PageResult<Post>> getPosts(
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "20") Integer size) {
    
    // 限制最大页面大小
    size = Math.min(size, 100);
    
    // ...
}

// 游标分页（大数据集、推荐）
@GetMapping("/api/v1/posts")
public Result<CursorPageResult<Post>> getPosts(
    @RequestParam(required = false) String cursor,
    @RequestParam(defaultValue = "20") Integer limit) {
    
    // ...
}
```

### 分页响应格式

#### 偏移分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [...],
    "page": 1,
    "size": 20,
    "total": 156,
    "totalPages": 8,
    "hasNext": true,
    "hasPrev": false
  }
}
```

#### 游标分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [...],
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasNext": true
  }
}
```

### 分页 DTO 定义

```java
@Data
public class PageResult<T> {
    private List<T> items;
    private Integer page;
    private Integer size;
    private Long total;
    private Integer totalPages;
    private Boolean hasNext;
    private Boolean hasPrev;
    
    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setItems(page.getContent());
        result.setPage(page.getNumber() + 1);
        result.setSize(page.getSize());
        result.setTotal(page.getTotalElements());
        result.setTotalPages(page.getTotalPages());
        result.setHasNext(page.hasNext());
        result.setHasPrev(page.hasPrevious());
        return result;
    }
}

@Data
public class CursorPageResult<T> {
    private List<T> items;
    private String nextCursor;
    private Boolean hasNext;
}
```

### 页面大小限制

| 场景 | 默认值 | 最大值 |
|------|-------|-------|
| 列表查询 | 20 | 100 |
| 搜索结果 | 10 | 50 |
| 管理后台 | 20 | 200 |
| 导出数据 | - | 1000 |

```java
// 配置化页面大小限制
@Value("${api.pagination.max-size:100}")
private int maxPageSize;

public int normalizePageSize(Integer size) {
    if (size == null || size <= 0) {
        return 20;  // 默认值
    }
    return Math.min(size, maxPageSize);
}
```

### 排序规范

```java
// 请求参数
@GetMapping("/api/v1/posts")
public Result<PageResult<Post>> getPosts(
    @RequestParam(defaultValue = "createdAt") String sortBy,
    @RequestParam(defaultValue = "desc") String sortOrder) {
    
    // 白名单校验排序字段
    Set<String> allowedSortFields = Set.of("createdAt", "updatedAt", "title");
    if (!allowedSortFields.contains(sortBy)) {
        throw new BusinessException("不支持的排序字段: " + sortBy);
    }
    
    // ...
}
```

---

**最后更新**：2026-02-01
