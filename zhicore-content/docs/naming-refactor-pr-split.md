# 命名迁移 PR 拆分清单（可执行）

## 背景
当前工作区是脏状态（跨模块已有大量改动），建议通过精确路径 `git add` 做最小提交，避免把无关改动带入命名迁移 PR。

## PR-1：消息目录统一（mq -> messaging）
目标：统一消息消费者目录与包名。

建议纳入文件：
- `src/main/java/com/zhicore/content/infrastructure/messaging/consumer/PostScheduleConsumer.java`
- `src/main/java/com/zhicore/content/infrastructure/messaging/consumer/UserProfileChangedConsumer.java`
- `src/main/java/com/zhicore/content/infrastructure/messaging/consumer/AuthorInfoCompensationConsumer.java`
- （如 Git 记录到）`src/main/java/com/zhicore/content/infrastructure/mq/*` 删除项

建议命令：
```powershell
git add src/main/java/com/zhicore/content/infrastructure/messaging/consumer
git add -u src/main/java/com/zhicore/content/infrastructure/mq
```

验收：
- `rg -n "infrastructure\\.mq" src/main/java src/test/java` 无结果（允许 `.skip` 非主路径例外）。

## PR-2：Mongo 目录统一（mongodb -> persistence/mongo）
目标：统一 Mongo document/repository 命名空间。

建议纳入文件：
- `src/main/java/com/zhicore/content/PostApplication.java`
- `src/main/java/com/zhicore/content/infrastructure/persistence/mongo/**`
- `src/main/java/com/zhicore/content/infrastructure/mongodb/**` 删除项
- `src/test/java/com/zhicore/content/infrastructure/persistence/mongo/repository/*.java.skip`（历史 skip 文件路径与 package 修正）

建议命令：
```powershell
git add src/main/java/com/zhicore/content/PostApplication.java
git add src/main/java/com/zhicore/content/infrastructure/persistence/mongo
git add -u src/main/java/com/zhicore/content/infrastructure/mongodb
git add src/test/java/com/zhicore/content/infrastructure/persistence/mongo/repository
git add -u src/test/java/com/zhicore/content/infrastructure/mongodb
```

验收：
- `rg -n "infrastructure\\.mongodb" src/main/java src/test/java -g "*.java"` 无主代码结果。

## PR-3：Assembler 去重 + 清理项
目标：消除重名、清理无效结构与工程命名。

建议纳入文件：
- `src/main/java/com/zhicore/content/application/assembler/PostViewAssembler.java`
- `src/main/java/com/zhicore/content/application/assembler/PostAssembler.java` 删除项
- `src/main/java/com/zhicore/content/interfaces/assembler/PostApiAssembler.java`
- `src/main/java/com/zhicore/content/application/service/PostApplicationService.java`
- `src/main/java/com/zhicore/content/application/service/TagApplicationService.java`
- `src/main/java/com/zhicore/content/domain/command/SaveDraftCommand.java` 删除项
- `zhicore-content.iml`（及旧 `.iml` 删除）
- `docs/naming-refactor-execution-plan.md`
- `docs/naming-refactor-pr-split.md`

建议命令：
```powershell
git add src/main/java/com/zhicore/content/application/assembler
git add src/main/java/com/zhicore/content/interfaces/assembler
git add src/main/java/com/zhicore/content/application/service/PostApplicationService.java
git add src/main/java/com/zhicore/content/application/service/TagApplicationService.java
git add -u src/main/java/com/zhicore/content/domain/command
git add zhicore-content.iml
git add -u zhicore-post.iml
git add docs/naming-refactor-execution-plan.md docs/naming-refactor-pr-split.md
```

验收：
- `rg -n "\\bPostAssembler\\b|SaveDraftCommand" src/main/java src/test/java` 无主代码残留。

## 每个 PR 提交前统一检查
```powershell
rg -n "infrastructure\\.mq|infrastructure\\.mongodb|application\\.assembler\\.PostAssembler|interfaces\\.assembler\\.PostAssembler|SaveDraftCommand" src/main/java src/test/java
mvn -q -DskipTests compile
```

说明：
- 当前 `mvn compile` 可能因外部依赖 `com.zhicore:zhicore-integration:1.0.0-SNAPSHOT` 缺失失败，需先解决依赖或在 CI 环境验证。
