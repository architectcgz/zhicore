# Nacos 配置编码问题修复

## 问题描述

blog-user 服务启动时报错：
```
java.nio.charset.MalformedInputException: Input length = 1
parse data from Nacos error, dataId:common.yml
```

**根本原因**: `config/nacos/common.yml` 文件中的中文注释导致编码问题。

## 修复步骤

### 1. 更新 Nacos 配置

已将 `config/nacos/common.yml` 中的所有中文注释替换为英文。

**方式 A: 使用脚本自动更新**
```powershell
cd config/nacos
.\update-common-config.ps1
```

**方式 B: 手动更新 Nacos 控制台**
1. 打开 Nacos 控制台: http://localhost:8848/nacos
2. 登录 (用户名/密码: nacos/nacos)
3. 进入 "配置管理" -> "配置列表"
4. 找到 `common.yml` (Group: BLOG_SERVICE)
5. 点击 "编辑"
6. 复制 `config/nacos/common.yml` 的内容
7. 粘贴并保存

### 2. 重启 blog-user 服务

在 IDEA 中停止并重新启动 blog-user 服务。

### 3. 验证修复

运行测试脚本：
```powershell
cd tests/api/admin
.\test-fixes.ps1
```

## 预期结果

- blog-user 服务正常启动，无编码错误
- ADMIN-003 (Disable User) 测试通过
- ADMIN-004 (Enable User) 测试通过
- ADMIN-013 (Filter Posts by Status) 测试通过

## 技术细节

### 问题分析

1. **Nacos 配置加载**: Spring Cloud 在启动时从 Nacos 加载 `common.yml`
2. **YAML 解析**: SnakeYAML 解析器期望 UTF-8 编码
3. **编码不匹配**: 文件中的中文字符编码与解析器期望不符
4. **解析失败**: 导致服务启动失败

### 解决方案

- 移除所有中文注释，使用英文
- 确保文件使用 UTF-8 编码（无 BOM）
- 重新上传到 Nacos

### 最佳实践

**Nacos 配置文件规范**:
- 使用 UTF-8 编码（无 BOM）
- 避免使用非 ASCII 字符
- 注释使用英文
- 配置值可以使用中文（作为字符串值）

## 相关文件

- `config/nacos/common.yml` - 已修复的配置文件
- `config/nacos/update-common-config.ps1` - 自动更新脚本
- `tests/api/admin/test-fixes.ps1` - 测试脚本
