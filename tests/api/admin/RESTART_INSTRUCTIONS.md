# 重启服务指令

## 需要重启的服务

为了应用以下修复，需要重启相关服务：

### 1. ZhiCore-user 服务
**修复内容**: 修复了 `UserMapper.xml` 中的状态过滤逻辑
- 将 `status = #{status}` 改为根据 ACTIVE/DISABLED 转换为 `is_active` 布尔值
- 这将修复 ADMIN-003 (Disable User) 和 ADMIN-004 (Enable User) 的问题

**重启命令**:
```powershell
# 停止当前运行的 ZhiCore-user
# 然后重新启动
cd ZhiCore-user
mvn spring-boot:run
```

### 2. ZhiCore-post 服务
**修复内容**: 修复了 `PostMapper.xml` 中的状态类型转换
- 将 `status = #{status}` 改为 `status = CAST(#{status} AS SMALLINT)`
- 这将修复 ADMIN-013 (Filter Posts by Status) 的 PostgreSQL 类型不匹配问题

**重启命令**:
```powershell
# 停止当前运行的 ZhiCore-post
# 然后重新启动
cd ZhiCore-post
mvn spring-boot:run
```

## 验证修复

重启服务后，运行以下测试脚本验证修复：

```powershell
cd tests/api/admin
.\test-fixes.ps1
```

## 预期结果

- [ADMIN-003] Disable User: PASS
- [ADMIN-004] Enable User: PASS
- [ADMIN-013] Filter Posts by Status: PASS

## 注意事项

当前测试失败的原因是"需要管理员权限"，这可能是因为：
1. Gateway 的 JWT 验证逻辑需要检查
2. ADMIN 角色的权限配置需要确认

如果重启后仍然失败，需要进一步检查 Gateway 的权限验证逻辑。
