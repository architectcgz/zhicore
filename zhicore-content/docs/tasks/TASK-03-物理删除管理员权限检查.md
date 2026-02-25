# TASK-03: 物理删除补齐管理员权限检查

## 背景

`PurgePostHandler.java:95` 中，物理删除未软删除文章时缺少管理员权限检查。
当前简化实现直接抛异常拒绝，需要补齐：管理员可以物理删除任意文章。

## 涉及文件

- `com.zhicore.content.application.command.handlers.PurgePostHandler` — 第 95 行 TODO
- `com.zhicore.content.interfaces.controller.AdminPostController` — 管理员端点参考
- 可能需要新增：管理员角色判断接口或工具

## 现有权限模型

项目当前通过 `UserContext.getUserId()` 获取用户身份，管理员端点通过路由前缀 `/api/v1/admin/*` 区分。
未发现 `@PreAuthorize` 或 Spring Security 角色体系。

## 实现要求

### 方案选择

**推荐方案**：通过 `UserContext` 扩展管理员标识

1. 在 `UserContext`（或等价的上下文对象）中增加 `isAdmin()` 方法
2. 管理员标识由网关层/认证层注入（如 Header `X-User-Role`）
3. `PurgePostHandler.validatePermission` 中检查：

```java
private void validatePermission(Post post, UserId userId) {
    if (!post.isDeleted()) {
        // 未软删除的文章，仅管理员可物理删除
        if (!UserContext.isAdmin()) {
            throw new IllegalStateException("只有管理员可以物理删除未软删除的文章");
        }
        return;
    }
    // 已软删除的文章，所有者或管理员可物理删除
    if (!post.isOwnedBy(userId) && !UserContext.isAdmin()) {
        throw new PostOwnershipException("无权删除此文章：用户不是文章所有者");
    }
}
```

**备选方案**：通过 Command 对象传入角色信息（解耦 UserContext）

### 边界情况

- 管理员标识缺失时，默认视为非管理员
- 管理员物理删除需记录审计日志（操作者、目标文章、操作时间）

## 验收标准

- [ ] 管理员可物理删除未软删除的文章
- [ ] 非管理员物理删除未软删除文章时抛出明确异常
- [ ] 已软删除文章：所有者或管理员均可物理删除
- [ ] 物理删除操作记录审计日志
- [ ] 单元测试覆盖：管理员/非管理员 × 已删除/未删除 四种组合

## 回滚策略

- 如果 `UserContext` 扩展涉及网关配合，需同步回滚网关配置
- 纯代码变更，revert commit 即可
