# 标签（Tag）API 使用指南

## 概述

标签功能为博客系统提供了灵活的内容分类和检索能力。本文档详细介绍标签相关的所有 API 接口、使用示例和最佳实践。

## 核心概念

### 标签（Tag）

标签是对文章内容主题的轻量级、可复用标识，具有以下特征：

- **全局唯一**：通过 slug 保证唯一性
- **自动规范化**：标签名称会自动转换为 URL 友好的 slug
- **自动创建**：不存在的标签会在使用时自动创建
- **多对多关系**：一个文章可以有多个标签，一个标签可以关联多个文章

### Slug 规范化规则

标签名称会按以下规则自动转换为 slug：

1. 中文转拼音（如 "数据库" → "shu-ju-ku"）
2. 转换为小写
3. 空格替换为连字符（-）
4. 移除非法字符（只保留 a-z、0-9、-）
5. 合并连续的连字符

**示例**：
- "Spring Boot" → "spring-boot"
- "Java" → "java"
- "数据库" → "shu-ju-ku"
- "PostgreSQL" → "postgresql"

---

## API 端点列表

### 标签管理 API（TagController）

| 方法 | 路径 | 描述 | 认证 |
|------|------|------|------|
| GET | `/api/v1/tags/{slug}` | 获取标签详情 | 否 |
| GET | `/api/v1/tags` | 获取标签列表（分页） | 否 |
| GET | `/api/v1/tags/search` | 搜索标签 | 否 |
| GET | `/api/v1/tags/{slug}/posts` | 获取标签下的文章列表 | 否 |
| GET | `/api/v1/tags/hot` | 获取热门标签 | 否 |

### 文章标签 API（PostController）

| 方法 | 路径 | 描述 | 认证 |
|------|------|------|------|
| POST | `/api/v1/posts/{postId}/tags` | 为文章添加标签 | 是 |
| DELETE | `/api/v1/posts/{postId}/tags/{slug}` | 移除文章的标签 | 是 |
| GET | `/api/v1/posts/{postId}/tags` | 获取文章的标签列表 | 否 |

---

## API 详细说明

### 1. 获取标签详情

**请求**：
```http
GET /api/v1/tags/{slug}
```

**路径参数**：
- `slug`（必填）：标签的 URL 友好标识，如 "spring-boot"

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1234567890123456789,
    "name": "Spring Boot",
    "slug": "spring-boot",
    "description": "Spring Boot 是一个基于 Spring 框架的快速开发框架",
    "createdAt": "2026-01-29T10:00:00",
    "updatedAt": "2026-01-29T10:00:00"
  }
}
```

**错误响应**：
```json
{
  "code": 404,
  "message": "标签不存在: spring-boot",
  "data": null
}
```

**使用场景**：
- 标签详情页展示
- 获取标签的完整信息

---

### 2. 获取标签列表（分页）

**请求**：
```http
GET /api/v1/tags?page=0&size=20
```

**查询参数**：
- `page`（可选，默认 0）：页码，从 0 开始
- `size`（可选，默认 20）：每页大小，范围 1-100

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1234567890123456789,
        "name": "Spring Boot",
        "slug": "spring-boot",
        "description": "Spring Boot 快速开发框架",
        "createdAt": "2026-01-29T10:00:00",
        "updatedAt": "2026-01-29T10:00:00"
      },
      {
        "id": 1234567890123456790,
        "name": "Java",
        "slug": "java",
        "description": "Java 编程语言",
        "createdAt": "2026-01-28T10:00:00",
        "updatedAt": "2026-01-28T10:00:00"
      }
    ],
    "total": 150,
    "page": 0,
    "size": 20,
    "totalPages": 8
  }
}
```

**使用场景**：
- 标签管理页面
- 标签浏览页面

---

### 3. 搜索标签

**请求**：
```http
GET /api/v1/tags/search?keyword=spring&limit=10
```

**查询参数**：
- `keyword`（必填）：搜索关键词
- `limit`（可选，默认 10）：返回数量限制，范围 1-50

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1234567890123456789,
      "name": "Spring Boot",
      "slug": "spring-boot",
      "description": "Spring Boot 快速开发框架",
      "createdAt": "2026-01-29T10:00:00",
      "updatedAt": "2026-01-29T10:00:00"
    },
    {
      "id": 1234567890123456791,
      "name": "Spring Cloud",
      "slug": "spring-cloud",
      "description": "Spring Cloud 微服务框架",
      "createdAt": "2026-01-27T10:00:00",
      "updatedAt": "2026-01-27T10:00:00"
    }
  ]
}
```

**使用场景**：
- 标签输入框自动补全
- 标签搜索功能

---

### 4. 获取标签下的文章列表

**请求**：
```http
GET /api/v1/tags/{slug}/posts?page=0&size=20
```

**路径参数**：
- `slug`（必填）：标签的 URL 友好标识

**查询参数**：
- `page`（可选，默认 0）：页码，从 0 开始
- `size`（可选，默认 20）：每页大小，范围 1-100

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1234567890123456789,
        "title": "Spring Boot 入门教程",
        "excerpt": "本文介绍 Spring Boot 的基本概念和使用方法...",
        "authorId": 1001,
        "authorName": "张三",
        "tags": [
          {
            "id": 1234567890123456789,
            "name": "Spring Boot",
            "slug": "spring-boot"
          },
          {
            "id": 1234567890123456790,
            "name": "Java",
            "slug": "java"
          }
        ],
        "publishedAt": "2026-01-29T10:00:00",
        "viewCount": 1000,
        "likeCount": 50,
        "commentCount": 20
      }
    ],
    "total": 128,
    "page": 0,
    "size": 20,
    "totalPages": 7
  }
}
```

**使用场景**：
- 标签页面（如 `/tags/spring-boot`）
- 按标签筛选文章

---

### 5. 获取热门标签

**请求**：
```http
GET /api/v1/tags/hot?limit=10
```

**查询参数**：
- `limit`（可选，默认 10）：返回数量限制，范围 1-50

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1234567890123456789,
      "name": "Spring Boot",
      "slug": "spring-boot",
      "postCount": 128
    },
    {
      "id": 1234567890123456790,
      "name": "Java",
      "slug": "java",
      "postCount": 256
    },
    {
      "id": 1234567890123456791,
      "name": "数据库",
      "slug": "shu-ju-ku",
      "postCount": 89
    }
  ]
}
```

**使用场景**：
- 首页热门标签展示
- 标签云
- 侧边栏推荐标签

---

### 6. 为文章添加标签

**请求**：
```http
POST /api/v1/posts/{postId}/tags
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "tags": ["Spring Boot", "Java", "后端开发"]
}
```

**路径参数**：
- `postId`（必填）：文章 ID

**请求体**：
```json
{
  "tags": ["Spring Boot", "Java", "后端开发"]
}
```

**字段说明**：
- `tags`（必填）：标签名称列表，最多 10 个

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误响应**：
```json
{
  "code": 400,
  "message": "标签数量不能超过10个",
  "data": null
}
```

```json
{
  "code": 403,
  "message": "只有文章作者可以修改标签",
  "data": null
}
```

**功能说明**：
- 会替换文章的所有现有标签
- 不存在的标签会自动创建
- 标签名称会自动规范化为 slug
- 重复的标签会自动去重
- 只有文章作者可以修改标签

**使用场景**：
- 创建文章时添加标签
- 编辑文章时修改标签

---

### 7. 移除文章的标签

**请求**：
```http
DELETE /api/v1/posts/{postId}/tags/{slug}
Authorization: Bearer {access_token}
```

**路径参数**：
- `postId`（必填）：文章 ID
- `slug`（必填）：标签的 URL 友好标识

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**功能说明**：
- 根据 slug 移除指定标签
- 如果标签不存在，操作会被忽略（不报错）
- 只有文章作者可以移除标签

**使用场景**：
- 编辑文章时移除某个标签

---

### 8. 获取文章的标签列表

**请求**：
```http
GET /api/v1/posts/{postId}/tags
```

**路径参数**：
- `postId`（必填）：文章 ID

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1234567890123456789,
      "name": "Spring Boot",
      "slug": "spring-boot",
      "description": "Spring Boot 快速开发框架",
      "createdAt": "2026-01-29T10:00:00",
      "updatedAt": "2026-01-29T10:00:00"
    },
    {
      "id": 1234567890123456790,
      "name": "Java",
      "slug": "java",
      "description": "Java 编程语言",
      "createdAt": "2026-01-28T10:00:00",
      "updatedAt": "2026-01-28T10:00:00"
    }
  ]
}
```

**使用场景**：
- 文章详情页展示标签
- 编辑文章时回显标签

---

## 使用示例

### 场景 1：创建文章并添加标签

```javascript
// 1. 创建文章
const createPostResponse = await fetch('/api/v1/posts', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'Spring Boot 入门教程',
    content: '本文介绍 Spring Boot 的基本概念...',
    tags: ['Spring Boot', 'Java', '后端开发']
  })
});

const post = await createPostResponse.json();
console.log('文章创建成功，ID:', post.data.id);
```

### 场景 2：标签页面展示

```javascript
// 1. 获取标签详情
const tagResponse = await fetch('/api/v1/tags/spring-boot');
const tag = await tagResponse.json();

// 2. 获取标签下的文章列表
const postsResponse = await fetch('/api/v1/tags/spring-boot/posts?page=0&size=20');
const posts = await postsResponse.json();

// 3. 渲染页面
renderTagPage(tag.data, posts.data);
```

### 场景 3：标签输入框自动补全

```javascript
// 用户输入时触发搜索
async function handleTagInput(keyword) {
  if (keyword.length < 2) return;
  
  const response = await fetch(`/api/v1/tags/search?keyword=${encodeURIComponent(keyword)}&limit=10`);
  const result = await response.json();
  
  // 显示搜索结果
  showTagSuggestions(result.data);
}
```

### 场景 4：首页热门标签展示

```javascript
// 获取热门标签
const response = await fetch('/api/v1/tags/hot?limit=10');
const result = await response.json();

// 渲染标签云
renderTagCloud(result.data);
```

### 场景 5：编辑文章标签

```javascript
// 1. 获取文章当前标签
const currentTagsResponse = await fetch(`/api/v1/posts/${postId}/tags`);
const currentTags = await currentTagsResponse.json();

// 2. 用户修改标签后提交
const newTags = ['Spring Boot', 'Java', 'PostgreSQL'];
const updateResponse = await fetch(`/api/v1/posts/${postId}/tags`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ tags: newTags })
});

console.log('标签更新成功');
```

---

## 最佳实践

### 1. 标签命名规范

**推荐**：
- 使用简洁、明确的名称
- 使用常见的技术术语
- 保持一致的命名风格

**示例**：
- ✅ "Spring Boot"
- ✅ "Java"
- ✅ "数据库"
- ❌ "spring boot 框架教程"（过长）
- ❌ "sb"（缩写不明确）

### 2. 标签数量控制

- 每篇文章建议 3-5 个标签
- 最多不超过 10 个标签
- 避免过度标签化

### 3. 标签选择策略

- 优先使用已存在的标签
- 使用搜索功能查找相似标签
- 避免创建语义重复的标签

### 4. 性能优化

**批量查询**：
```javascript
// ❌ 错误：N+1 查询
for (const post of posts) {
  const tags = await fetch(`/api/v1/posts/${post.id}/tags`);
}

// ✅ 正确：在文章列表中已包含标签信息
const postsResponse = await fetch('/api/v1/posts');
const posts = postsResponse.data.items; // 每个 post 已包含 tags 字段
```

**缓存热门标签**：
```javascript
// 热门标签变化不频繁，可以缓存
const cachedHotTags = localStorage.getItem('hotTags');
if (cachedHotTags && Date.now() - cachedHotTags.timestamp < 3600000) {
  return JSON.parse(cachedHotTags.data);
}

const response = await fetch('/api/v1/tags/hot?limit=10');
const hotTags = await response.json();
localStorage.setItem('hotTags', JSON.stringify({
  data: hotTags.data,
  timestamp: Date.now()
}));
```

### 5. 错误处理

```javascript
async function addTagsToPost(postId, tags) {
  try {
    const response = await fetch(`/api/v1/posts/${postId}/tags`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ tags })
    });
    
    if (!response.ok) {
      const error = await response.json();
      
      if (error.code === 400) {
        alert('标签数量不能超过10个');
      } else if (error.code === 403) {
        alert('只有文章作者可以修改标签');
      } else if (error.code === 404) {
        alert('文章不存在');
      } else {
        alert('操作失败，请稍后重试');
      }
      
      return;
    }
    
    alert('标签添加成功');
  } catch (error) {
    console.error('网络错误:', error);
    alert('网络错误，请检查连接');
  }
}
```

---

## 常见问题

### Q1: 标签名称和 slug 有什么区别？

**A**: 
- **name**：标签的展示名称，可以包含大小写、空格、中文等，如 "Spring Boot"
- **slug**：URL 友好标识，自动规范化为小写、连字符分隔，如 "spring-boot"
- slug 用于 URL 路径和唯一标识，name 用于展示

### Q2: 如何避免创建重复标签？

**A**: 
- 系统会自动将标签名称规范化为 slug
- 相同 slug 的标签会被视为同一个标签
- 例如 "Spring Boot"、"spring boot"、"SPRING BOOT" 都会被规范化为 "spring-boot"

### Q3: 标签可以修改吗？

**A**: 
- 标签的 name 和 description 可以修改
- 标签的 slug 一旦创建不可修改（保证 URL 稳定性）
- 如需修改 slug，需要创建新标签并迁移文章

### Q4: 删除标签会影响文章吗？

**A**: 
- 删除标签会自动删除所有文章与该标签的关联
- 文章本身不会被删除
- 建议谨慎删除标签，或使用标签合并功能

### Q5: 标签搜索支持模糊匹配吗？

**A**: 
- 是的，搜索接口支持模糊匹配
- 会匹配标签名称中包含关键词的所有标签
- 例如搜索 "spring" 会匹配 "Spring Boot"、"Spring Cloud" 等

### Q6: 热门标签的排序规则是什么？

**A**: 
- 按文章数量降序排序
- 文章数量相同时按创建时间降序排序
- 统计数据每小时更新一次

---

## 相关文档

- [标签功能需求文档](../../.kiro/specs/post-tag-need/requirements.md)
- [标签功能设计文档](../../.kiro/specs/post-tag-need/design.md)
- [标签功能实现计划](../../.kiro/specs/post-tag-need/tasks.md)
- [API 测试脚本](../../tests/api/post/test-tag-api-full.ps1)

---

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-02-01 | 1.0.0 | 初始版本，完整的标签 API 文档 |
