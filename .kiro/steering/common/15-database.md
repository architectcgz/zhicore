---
inclusion: fileMatch
fileMatchPattern: '**/*{Repository,repository,Entity,entity,Mapper,mapper}*.java'
---

# 数据库规范

[返回索引](./README-zh.md)

---

## 表设计规范

### 主键策略选择

根据表的业务性质选择合适的主键策略：

| 表类型 | 主键策略 | 示例 |
|--------|---------|------|
| **核心业务实体** | 复合业务主键（推荐） | `PRIMARY KEY (tenant_id, user_id)` |
| **独立实体（无多租户）** | 分布式 ID | Snowflake、Leaf Segment |
| **日志/请求/流水** | 自增主键 | `id BIGSERIAL PRIMARY KEY` |
| **关联表** | 联合主键 | `PRIMARY KEY (user_id, role_id)` |

### 复合业务主键（推荐）

```sql
-- ✅ 推荐 - 多租户场景使用复合业务主键
CREATE TABLE user_data (
    tenant_id INTEGER NOT NULL,       -- 租户 ID
    user_id BIGINT NOT NULL,          -- 用户 ID（分布式 ID）
    nickname VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, user_id)  -- 复合主键
);

-- 分片友好：可按 tenant_id 分片
```

### 独立实体主键

```sql
-- ✅ 正确 - 无多租户的独立实体
CREATE TABLE category (
    id BIGINT PRIMARY KEY,            -- 分布式 ID
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
```

### 日志/流水表主键

```sql
-- ✅ 正确 - 日志表使用自增主键
CREATE TABLE operation_log (
    id BIGSERIAL PRIMARY KEY,         -- 自增主键
    user_id BIGINT NOT NULL,          -- 允许同一用户多条记录
    operation VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 必须字段

**所有业务表必须包含以下字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `created_at` | TIMESTAMP | 创建时间，不可变 |
| `updated_at` | TIMESTAMP | 更新时间，每次修改更新 |
| `deleted` | BOOLEAN | 软删除标记（可选，视业务需求） |

### 字段命名规范

```
✅ 正确：
- user_id（下划线分隔）
- created_at（时间字段用 _at 后缀）
- is_active（布尔字段用 is_ 前缀）

❌ 错误：
- userId（驼峰命名）
- createTime（不一致的命名）
- active（缺少 is_ 前缀）
```

---

## 索引规范

### 索引命名

```sql
-- 普通索引：idx_{table}_{column}
CREATE INDEX idx_user_email ON user(email);

-- 唯一索引：uk_{table}_{column}
CREATE UNIQUE INDEX uk_user_phone ON user(phone);

-- 联合索引：idx_{table}_{column1}_{column2}
CREATE INDEX idx_post_owner_status ON post(owner_id, status);
```

### 索引设计原则

#### 1. 必须解释每个索引的目的

```sql
-- ✅ 正确 - 有注释说明
CREATE INDEX idx_post_owner_status ON post(owner_id, status);
-- 用途：查询用户的已发布文章列表

-- ❌ 错误 - 无说明
CREATE INDEX idx_post_owner_status ON post(owner_id, status);
```

#### 2. 禁止无意义的联合索引

```sql
-- ❌ 错误 - 字段顺序不合理
CREATE INDEX idx_post_status_owner ON post(status, owner_id);
-- 问题：status 区分度低，应该放在后面

-- ✅ 正确 - 高区分度字段在前
CREATE INDEX idx_post_owner_status ON post(owner_id, status);
```

---

## SQL 禁止项

### 禁止 SELECT *

```sql
-- ❌ 错误
SELECT * FROM user WHERE id = 1;

-- ✅ 正确
SELECT id, username, email, created_at FROM user WHERE id = 1;
```

### 禁止无 WHERE 的 UPDATE/DELETE

```sql
-- ❌ 错误 - 危险操作
UPDATE user SET status = 1;
DELETE FROM user;

-- ✅ 正确 - 必须有 WHERE 条件
UPDATE user SET status = 1 WHERE id = 123;
DELETE FROM user WHERE id = 123 AND deleted = FALSE;
```

### 禁止大表全表扫描

```sql
-- ❌ 错误 - 全表扫描
SELECT * FROM post ORDER BY created_at DESC LIMIT 10;

-- ✅ 正确 - 使用索引
SELECT * FROM post 
WHERE owner_id = 123 
ORDER BY created_at DESC 
LIMIT 10;
```

---

## 分页查询

### 推荐方式

```sql
-- ✅ 正确 - 使用游标分页（性能好）
SELECT * FROM post 
WHERE id > 1000 
ORDER BY id 
LIMIT 20;

-- ⚠️ 可用 - 传统分页（小数据量）
SELECT * FROM post 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 0;

-- ❌ 错误 - 深分页（性能差）
SELECT * FROM post 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 10000;
```

---

## 事务管理

### 事务注解使用

```java
// ✅ 正确 - 明确指定只读事务
@Transactional(readOnly = true)
public User getUser(Long id) {
    return userRepository.findById(id);
}

// ✅ 正确 - 指定异常回滚
@Transactional(rollbackFor = Exception.class)
public void createUser(User user) {
    userRepository.save(user);
    // 发送欢迎邮件
    emailService.sendWelcome(user.getEmail());
}

// ❌ 错误 - 事务范围过大
@Transactional
public void processOrder(Order order) {
    // 数据库操作
    orderRepository.save(order);
    
    // 外部 API 调用（不应该在事务内）
    paymentService.pay(order);  // 可能很慢
    
    // 发送消息（不应该在事务内）
    mqProducer.send(order);
}
```

---

**最后更新**：2026-02-01
