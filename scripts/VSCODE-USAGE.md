# VS Code 中启动微服务指南

本文档说明如何在 VS Code 的集成终端中启动和管理博客微服务。

---

## 方式 1: 使用 VS Code Tasks（最推荐）

### 步骤 1: 生成 Tasks 配置

在 VS Code 终端中运行：

```powershell
cd blog-microservice/scripts
.\generate-vscode-tasks.ps1
```

这会在项目根目录创建 `.vscode/tasks.json` 文件。

### 步骤 2: 启动服务

有三种方式启动服务：

#### 方式 A: 使用命令面板（推荐）

1. 按 `Ctrl+Shift+P` 打开命令面板
2. 输入 `Tasks: Run Task`
3. 选择要启动的服务，例如：
   - `启动: 网关服务`
   - `启动: 用户服务`
   - `启动: 文章服务`
   - 等等...

#### 方式 B: 使用快捷键

1. 按 `Ctrl+Shift+B`（运行构建任务）
2. 选择要启动的服务

#### 方式 C: 使用菜单

1. 点击菜单: `Terminal > Run Task...`
2. 选择要启动的服务

### 步骤 3: 查看服务日志

- 每个服务会在独立的终端面板中运行
- 点击底部的 `TERMINAL` 标签查看所有终端
- 使用下拉菜单切换不同服务的终端

### 步骤 4: 停止服务

- 在对应的终端面板中按 `Ctrl+C`
- 或点击终端右上角的垃圾桶图标关闭终端

---

## 方式 2: 手动在终端中启动

### 步骤 1: 获取启动命令

```powershell
cd blog-microservice/scripts
.\generate-vscode-commands.ps1
```

这会显示所有服务的启动命令，并保存到 `vscode-start-commands.txt` 文件。

### 步骤 2: 创建多个终端

1. 按 `Ctrl+` ` 打开集成终端
2. 点击终端右上角的 `+` 按钮创建新终端（或按 `Ctrl+Shift+` `）
3. 重复创建，直到有足够的终端（建议至少 3-4 个）

### 步骤 3: 在每个终端中运行服务

在不同的终端中运行以下命令：

```powershell
# 终端 1: 网关服务
.\scripts\start-service.ps1 -Service gateway

# 终端 2: 用户服务
.\scripts\start-service.ps1 -Service user

# 终端 3: 文章服务
.\scripts\start-service.ps1 -Service post

# 终端 4: 评论服务
.\scripts\start-service.ps1 -Service comment

# ... 以此类推
```

### 推荐启动顺序

1. **优先级 1**: 网关服务 (gateway)
2. **优先级 2**: 核心服务
   - 用户服务 (user)
   - 文章服务 (post)
   - 评论服务 (comment)
   - 上传服务 (upload)
3. **优先级 3**: 辅助服务
   - 消息服务 (message)
   - 通知服务 (notification)
   - 搜索服务 (search)
   - 排行服务 (ranking)
4. **优先级 4**: 管理服务 (admin)

---

## 终端管理技巧

### 重命名终端

1. 右键点击终端标签
2. 选择 `Rename`
3. 输入服务名称（例如: "网关服务"）

### 终端布局

VS Code 支持分割终端面板：

1. 右键点击终端标签
2. 选择 `Split Terminal` 或按 `Ctrl+Shift+5`
3. 可以并排查看多个服务的日志

### 终端颜色标记

可以为不同的终端设置不同的颜色：

1. 右键点击终端标签
2. 选择 `Change Icon Color`
3. 选择颜色（例如: 网关用蓝色，用户服务用绿色）

---

## 常见问题

### Q1: 如何同时查看多个服务的日志？

**方案 A**: 使用分割终端
1. 启动第一个服务
2. 右键终端标签 > `Split Terminal`
3. 在新的分割面板中启动另一个服务

**方案 B**: 使用多个终端标签
1. 启动多个服务，每个在独立的终端中
2. 使用终端下拉菜单快速切换

### Q2: 终端太多，如何管理？

**建议**:
1. 只启动需要调试的服务
2. 使用终端重命名功能
3. 使用终端颜色标记
4. 关闭不需要的服务终端

### Q3: 如何快速重启某个服务？

1. 在对应终端中按 `Ctrl+C` 停止服务
2. 按 `↑` 键调出上一条命令
3. 按 `Enter` 重新启动

### Q4: 服务启动失败怎么办？

**检查清单**:
1. 确认基础设施服务已启动（PostgreSQL, Redis, Nacos 等）
2. 确认端口未被占用
3. 确认 JAR 文件已编译
4. 查看终端中的错误信息

**检查基础设施**:
```powershell
cd blog-microservice/docker
docker-compose ps
```

**检查端口占用**:
```powershell
Get-NetTCPConnection -LocalPort 8100,8101,8102,8103,8104,8105,8106,8107,8108,8109 -ErrorAction SilentlyContinue
```

**重新编译**:
```powershell
cd blog-microservice
mvn clean package -DskipTests
```

---

## VS Code 设置优化

### 推荐的 settings.json 配置

在 `.vscode/settings.json` 中添加：

```json
{
  "terminal.integrated.defaultProfile.windows": "PowerShell",
  "terminal.integrated.profiles.windows": {
    "PowerShell": {
      "source": "PowerShell",
      "icon": "terminal-powershell"
    }
  },
  "terminal.integrated.scrollback": 10000,
  "terminal.integrated.fontSize": 14,
  "terminal.integrated.lineHeight": 1.2
}
```

### 推荐的快捷键

在 `keybindings.json` 中添加：

```json
[
  {
    "key": "ctrl+shift+t",
    "command": "workbench.action.tasks.runTask"
  },
  {
    "key": "ctrl+shift+`",
    "command": "workbench.action.terminal.new"
  }
]
```

---

## 服务访问地址

启动服务后，可以通过以下地址访问：

| 服务 | 地址 | API 文档 |
|------|------|---------|
| 网关服务 | http://localhost:8100 | http://localhost:8100/doc.html |
| 用户服务 | http://localhost:8101 | http://localhost:8101/doc.html |
| 文章服务 | http://localhost:8102 | http://localhost:8102/doc.html |
| 评论服务 | http://localhost:8103 | http://localhost:8103/doc.html |
| 上传服务 | http://localhost:8104 | http://localhost:8104/doc.html |
| 消息服务 | http://localhost:8105 | http://localhost:8105/doc.html |
| 通知服务 | http://localhost:8106 | http://localhost:8106/doc.html |
| 搜索服务 | http://localhost:8107 | http://localhost:8107/doc.html |
| 排行服务 | http://localhost:8108 | http://localhost:8108/doc.html |
| 管理服务 | http://localhost:8109 | http://localhost:8109/doc.html |

### 健康检查

```powershell
# 检查单个服务
Invoke-WebRequest http://localhost:8100/actuator/health

# 检查所有服务
8100..8109 | ForEach-Object {
    try {
        $response = Invoke-WebRequest "http://localhost:$_/actuator/health" -TimeoutSec 2
        Write-Host "端口 $_ : 健康" -ForegroundColor Green
    }
    catch {
        Write-Host "端口 $_ : 不可用" -ForegroundColor Red
    }
}
```

---

## 完整工作流示例

### 场景: 开发文章功能

1. **启动基础设施**
   ```powershell
   cd blog-microservice/docker
   docker-compose up -d
   ```

2. **生成 VS Code Tasks**（首次使用）
   ```powershell
   cd blog-microservice/scripts
   .\generate-vscode-tasks.ps1
   ```

3. **启动必要的服务**
   - 按 `Ctrl+Shift+P`
   - 输入 `Tasks: Run Task`
   - 依次启动:
     - `启动: 网关服务`
     - `启动: 用户服务`
     - `启动: 文章服务`

4. **开发和测试**
   - 修改代码
   - 在文章服务终端中按 `Ctrl+C` 停止
   - 重新编译: `mvn clean package -DskipTests`
   - 按 `↑` 和 `Enter` 重启服务

5. **查看日志**
   - 切换到对应的终端面板
   - 查看实时日志输出

6. **停止服务**
   - 在每个终端中按 `Ctrl+C`
   - 或关闭终端标签

---

## 相关文档

- [脚本使用说明](./README.md)
- [服务端口配置](../docs/service-ports.md)
- [Docker 部署文档](../docker/README.md)

---

**最后更新**: 2026-02-17
