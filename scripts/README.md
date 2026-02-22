# ZhiCore Microservice 启动脚本

本目录包含用于启动和管理博客微服务的 PowerShell 脚本。

## 脚本列表

### 1. start-all-services.ps1

在后台启动所有微服务（无窗口）。

**用法:**
```powershell
# 编译并启动所有服务
.\start-all-services.ps1

# 跳过编译
.\start-all-services.ps1 -SkipBuild

# 跳过健康检查
.\start-all-services.ps1 -SkipHealthCheck

# 跳过编译和健康检查
.\start-all-services.ps1 -SkipBuild -SkipHealthCheck
```

**特点:**
- 服务在后台运行（无窗口）
- 适合快速启动和测试
- 日志输出到进程中（不可见）

**停止服务:**
```powershell
# 停止所有 Java 进程
Get-Process java | Stop-Process -Force
```

---

### 2. start-all-in-terminals.ps1

在多个终端窗口中启动所有微服务，每个服务一个窗口（使用 Windows Terminal 或 PowerShell 窗口）。

**用法:**
```powershell
# 使用 Windows Terminal 启动
.\start-all-in-terminals.ps1

# 使用普通 PowerShell 窗口
.\start-all-in-terminals.ps1 -UseWindowsTerminal:$false

# 跳过编译
.\start-all-in-terminals.ps1 -SkipBuild
```

**特点:**
- 每个服务在独立的外部终端窗口中运行
- 支持 Windows Terminal（多标签）和普通 PowerShell 窗口
- 适合不使用 IDE 的场景

**停止服务:**
- 关闭对应的终端窗口
- 或在窗口中按 `Ctrl+C`
- 或使用命令: `Get-Process java | Stop-Process -Force`

---

### 3. generate-vscode-tasks.ps1 (VS Code 用户推荐)

生成 VS Code tasks.json 配置文件，让你可以通过 VS Code 的任务系统启动服务。

**用法:**
```powershell
# 生成 tasks.json
.\generate-vscode-tasks.ps1
```

**特点:**
- 生成 `.vscode/tasks.json` 配置文件
- 每个服务作为独立的 VS Code 任务
- 可以通过 VS Code UI 启动和管理服务
- 每个服务在独立的 VS Code 终端面板中运行

**使用生成的任务:**
1. 按 `Ctrl+Shift+P` 打开命令面板
2. 输入 "Tasks: Run Task"
3. 选择要启动的服务（例如: "启动: 网关服务"）

---

### 4. generate-vscode-commands.ps1

生成在 VS Code 终端中手动运行的命令列表。

**用法:**
```powershell
# 生成命令列表
.\generate-vscode-commands.ps1

# 跳过编译
.\generate-vscode-commands.ps1 -SkipBuild
```

**特点:**
- 显示所有服务的启动命令
- 保存命令到 `vscode-start-commands.txt` 文件
- 适合手动在 VS Code 终端中启动服务

---

### 5. start-service.ps1

启动单个微服务（在当前终端中运行）。

**用法:**
```powershell
# 启动网关服务
.\start-service.ps1 -Service gateway

# 启动用户服务
.\start-service.ps1 -Service user

# 使用指定配置文件
.\start-service.ps1 -Service post -Profile prod
```

**可用服务:**
- `gateway` - 网关服务 (端口 8100)
- `user` - 用户服务 (端口 8101)
- `post` - 文章服务 (端口 8102)
- `comment` - 评论服务 (端口 8103)
- `upload` - 上传服务 (端口 8104)
- `message` - 消息服务 (端口 8105)
- `notification` - 通知服务 (端口 8106)
- `search` - 搜索服务 (端口 8107)
- `ranking` - 排行服务 (端口 8108)
- `admin` - 管理服务 (端口 8109)

**特点:**
- 在当前终端中运行
- 适合单独调试某个服务
- 可以直接看到日志输出

**停止服务:**
- 按 `Ctrl+C`

---

## 使用场景

### 场景 1: VS Code 集成终端（最推荐）

使用 VS Code Tasks 在集成终端中启动服务：

```powershell
# 1. 生成 VS Code tasks.json 配置
cd ZhiCore-microservice/scripts
.\generate-vscode-tasks.ps1

# 2. 在 VS Code 中使用
# 按 Ctrl+Shift+P，输入 "Tasks: Run Task"，选择要启动的服务
```

优点:
- 完美集成到 VS Code
- 每个服务在独立的终端面板中运行
- 可以通过 VS Code UI 管理所有服务
- 支持快捷键操作

### 场景 2: 手动启动服务

使用 `generate-vscode-commands.ps1` 获取启动命令：

```powershell
cd ZhiCore-microservice/scripts
.\generate-vscode-commands.ps1
```

然后在 VS Code 终端中手动运行每个服务的命令。

优点:
- 灵活控制启动顺序
- 可以选择性启动部分服务
- 适合调试特定服务

### 场景 2: 外部终端窗口

使用 `start-all-in-terminals.ps1` 在外部终端窗口中启动：

```powershell
cd ZhiCore-microservice/scripts
.\start-all-in-terminals.ps1
```

优点:
- 每个服务有独立的窗口
- 不占用 IDE 资源
- 适合不使用 IDE 的场景

### 场景 3: 快速测试

使用 `start-all-services.ps1` 在后台启动所有服务：

```powershell
cd ZhiCore-microservice/scripts
.\start-all-services.ps1 -SkipBuild
```

优点:
- 启动速度快
- 不占用多个窗口
- 适合快速验证功能

### 场景 4: 调试单个服务

使用 `start-service.ps1` 启动需要调试的服务：

```powershell
cd ZhiCore-microservice/scripts

# 在终端 1 中启动网关
.\start-service.ps1 -Service gateway

# 在终端 2 中启动用户服务
.\start-service.ps1 -Service user

# 在终端 3 中启动文章服务
.\start-service.ps1 -Service post
```

优点:
- 只启动需要的服务
- 节省资源
- 便于集中调试

---

## 前置条件

### 1. 基础设施服务

所有脚本都要求基础设施服务已启动：

```powershell
cd ZhiCore-microservice/docker
docker-compose up -d
```

需要的服务:
- PostgreSQL (端口 5432)
- Redis (端口 6379)
- Nacos (端口 8848)
- RocketMQ NameServer (端口 9876)
- MongoDB (端口 27017)

### 2. 编译项目

首次使用或代码变更后需要编译：

```powershell
cd ZhiCore-microservice
mvn clean package -DskipTests
```

或使用脚本的自动编译功能（不加 `-SkipBuild` 参数）。

---

## Windows Terminal 配置（可选）

如果使用 Windows Terminal，可以配置自定义配置文件以获得更好的体验。

### 安装 Windows Terminal

```powershell
# 使用 winget 安装
winget install Microsoft.WindowsTerminal

# 或从 Microsoft Store 安装
```

### 配置文件示例

在 Windows Terminal 的 `settings.json` 中添加：

```json
{
  "profiles": {
    "list": [
      {
        "name": "ZhiCore Gateway",
        "commandline": "powershell.exe -NoExit -Command \"cd C:\\path\\to\\ZhiCore-microservice; .\\scripts\\start-service.ps1 -Service gateway\"",
        "icon": "🌐",
        "colorScheme": "One Half Dark"
      },
      {
        "name": "ZhiCore User Service",
        "commandline": "powershell.exe -NoExit -Command \"cd C:\\path\\to\\ZhiCore-microservice; .\\scripts\\start-service.ps1 -Service user\"",
        "icon": "👤",
        "colorScheme": "One Half Dark"
      }
      // 添加其他服务...
    ]
  }
}
```

---

## 常见问题

### Q1: 端口已被占用

**错误信息:**
```
警告: 端口 8100 已被占用 (PID: 12345)
```

**解决方案:**
```powershell
# 查看占用端口的进程
Get-NetTCPConnection -LocalPort 8100 | Select-Object LocalPort,State,OwningProcess

# 停止进程
Stop-Process -Id 12345 -Force
```

### Q2: JAR 文件不存在

**错误信息:**
```
错误: JAR 文件不存在: ZhiCore-gateway\target\ZhiCore-gateway-1.0.0-SNAPSHOT.jar
```

**解决方案:**
```powershell
# 编译项目
cd ZhiCore-microservice
mvn clean package -DskipTests
```

### Q3: 基础设施服务未运行

**错误信息:**
```
错误: 基础设施服务未运行
```

**解决方案:**
```powershell
# 启动基础设施服务
cd ZhiCore-microservice/docker
docker-compose up -d

# 检查服务状态
docker-compose ps
```

### Q4: Windows Terminal 不可用

如果 `start-all-in-terminals.ps1` 提示 Windows Terminal 不可用，脚本会自动降级使用普通 PowerShell 窗口。

**安装 Windows Terminal:**
```powershell
winget install Microsoft.WindowsTerminal
```

---

## 服务端口映射

| 服务 | 端口 | 模块名称 |
|------|------|---------|
| 网关服务 | 8100 | ZhiCore-gateway |
| 用户服务 | 8101 | ZhiCore-user |
| 文章服务 | 8102 | ZhiCore-post |
| 评论服务 | 8103 | ZhiCore-comment |
| 上传服务 | 8104 | ZhiCore-upload |
| 消息服务 | 8105 | ZhiCore-message |
| 通知服务 | 8106 | ZhiCore-notification |
| 搜索服务 | 8107 | ZhiCore-search |
| 排行服务 | 8108 | ZhiCore-ranking |
| 管理服务 | 8109 | ZhiCore-admin |

---

## 健康检查

检查所有服务是否正常运行：

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

## 相关文档

- [服务端口配置](../docs/service-ports.md)
- [Docker 部署文档](../docker/README.md)
- [开发规范](.kiro/steering/README.md)

---

**最后更新**: 2026-02-17
