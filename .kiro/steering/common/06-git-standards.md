---
inclusion: manual
---

# Git 提交规范

[返回索引](./README-zh.md)

---

## Commit Message 格式

**使用 Conventional Commits 规范**

```
<type>(<scope>): <subject>

<body>

<footer>
```

---

## Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(user): 添加用户注册功能` |
| `fix` | Bug 修复 | `fix(post): 修复文章删除失败问题` |
| `refactor` | 重构（不改变功能） | `refactor(cache): 优化 Redis 缓存结构` |
| `perf` | 性能优化 | `perf(query): 优化文章列表查询性能` |
| `test` | 测试相关 | `test(user): 添加用户服务单元测试` |
| `docs` | 文档更新 | `docs(api): 更新 API 文档` |
| `style` | 代码格式（不影响逻辑） | `style: 统一代码缩进格式` |
| `chore` | 构建/工具链 | `chore: 升级 Spring Boot 到 3.2.0` |
| `revert` | 回滚 | `revert: 回滚 commit abc123` |

---

## Scope 范围（可选）

- 服务名：`user`, `post`, `comment`, `message`
- 模块名：`cache`, `mq`, `auth`, `api`
- 功能名：`login`, `register`, `upload`

---

## 提交示例

### 好的提交

```
feat(user): 添加用户注册功能

- 实现邮箱注册接口
- 添加邮箱验证码发送
- 实现用户信息持久化

Closes #123
```

```
fix(post): 修复文章删除时缓存未清除问题

问题：删除文章后，缓存中仍然存在旧数据
解决：在删除文章时同步清除 Redis 缓存

Fixes #456
```

### 禁止的提交

```
❌ update
❌ test
❌ fix bug
❌ 修改代码
❌ tmp commit
❌ wip
```

---

## 提交粒度

**一次提交只做一件事**

```
✅ 正确：
- feat(user): 添加用户注册功能
- test(user): 添加用户注册单元测试
- docs(user): 更新用户 API 文档

❌ 错误：
- update: 添加注册功能、修复登录 bug、更新文档
```

---

## 提交频率

- 完成一个小功能立即提交
- 修复一个 bug 立即提交
- 重构一个模块分多次提交
- 避免一次提交包含大量文件

---

**最后更新**：2026-02-01
