# 管理后台 API 测试修复总结

## 已完成的修复

### 1. ADMIN-013: Filter Posts by Status (文章状态筛选)

**问题**: PostgreSQL 类型不匹配错误
```
ERROR: operator does not exist: smallint = character varying
```

**根本原因**: 
- 数据库 `posts.status` 字段类型是 `smallint`
- MyBatis XML 查询传入的是字符串 `"1"` (PUBLISHED 的代码)
- PostgreSQL 不会自动进行字符串到 smallint 的类型转换

**修复内容**: 修改 `ZhiCore-post/src/main/resources/mapper/PostMapper.xml`
```xml
<!-- 修复前 -->
<if test="status != null and status != ''">
    AND status = #{status}
</if>

<!-- 修复后 -->
<if test="status != null and status != ''">
    AND status = CAST(#{status} AS SMALLINT)
</if>
```

**需要重启**: ZhiCore-post 服务

---

### 2. ADMIN-003/004: Disable/Enable User (用户启用/禁用)

**问题**: "系统繁忙，请稍后重试"

**根本原因**: 
- 数据库 `users` 表使用 `is_active` (BOOLEAN) 字段存储用户状态
- `UserMapper.xml` 中错误地使用了不存在的 `status` 字段
- 查询条件需要将 ACTIVE/DISABLED 字符串转换为 TRUE/FALSE 布尔值

**修复内容**: 修改 `ZhiCore-user/src/main/resources/mapper/UserMapper.xml`
```xml
<!-- 修复前 -->
<if test="status != null and status != ''">
    AND status = #{status}
</if>

<!-- 修复后 -->
<if test="status != null and status != ''">
    <choose>
        <when test="status == 'ACTIVE'">
            AND is_active = TRUE
        </when>
        <when test="status == 'DISABLED'">
            AND is_active = FALSE
        </when>
    </choose>
</if>
```

**需要重启**: ZhiCore-user 服务 (已重启)

---

## 当前测试状态

运行 `.\test-fixes.ps1` 的结果：

```
[ADMIN-003] Disable User: FAIL - 系统繁忙，请稍后重试 (16ms)
[ADMIN-004] Enable User: FAIL - 系统繁忙，请稍后重试 (14ms)
[ADMIN-013] Filter Posts by Status: FAIL - 查询文章列表失败 (68ms)
```

---

## 下一步操作

### 1. 重启 ZhiCore-post 服务

ZhiCore-post 服务需要重启以应用 `PostMapper.xml` 的修复：

```powershell
# 停止当前运行的 ZhiCore-post
# 然后重新启动
cd ZhiCore-post
mvn spring-boot:run
```

### 2. 检查服务日志

如果重启后测试仍然失败，请检查服务日志：

**ZhiCore-user 服务日志** (ADMIN-003/004):
- 查找包含 `updateById`、`UserMapper`、`is_active` 的错误
- 可能的问题：MyBatis-Plus 的 `updateById` 方法可能需要额外配置

**ZhiCore-post 服务日志** (ADMIN-013):
- 查找包含 `selectByConditions`、`PostMapper`、`CAST` 的错误
- 确认 SQL 语句是否正确生成

### 3. 重新运行测试

重启 ZhiCore-post 服务后，运行：

```powershell
cd tests/api/admin
.\test-fixes.ps1
```

---

## 可能的额外问题

### ADMIN-003/004 仍然失败的可能原因

1. **MyBatis-Plus 更新问题**: `UserRepositoryImpl.update()` 使用 `userMapper.updateById(po)`，但 `UserPO` 可能没有正确映射 `is_active` 字段

2. **字段映射问题**: 检查 `UserPO` 类中是否有 `isActive` 字段，以及 MyBatis 是否正确映射到数据库的 `is_active` 字段

3. **事务问题**: 可能是事务配置导致更新失败

**建议**: 查看 ZhiCore-user 服务的完整错误堆栈，确定具体失败原因

---

## 测试验证

修复完成后，预期结果：

```
[ADMIN-003] Disable User: PASS - User disabled successfully
[ADMIN-004] Enable User: PASS - User enabled successfully
[ADMIN-013] Filter Posts by Status: PASS - Posts filtered by status successfully
```

---

## 文件修改清单

1. ✅ `ZhiCore-post/src/main/resources/mapper/PostMapper.xml` - 添加 CAST 类型转换
2. ✅ `ZhiCore-user/src/main/resources/mapper/UserMapper.xml` - 修复状态字段映射
3. ✅ `tests/api/admin/test-fixes.ps1` - 创建快速测试脚本
4. ✅ `tests/api/admin/RESTART_INSTRUCTIONS.md` - 重启指令文档

---

## 联系信息

如果问题持续存在，请提供：
1. ZhiCore-user 服务的完整错误日志
2. ZhiCore-post 服务的完整错误日志
3. 数据库表结构 (users 和 posts 表)
