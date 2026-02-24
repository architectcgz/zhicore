# zhicore-content 命名统一迁移执行清单

## 0. 目标
统一目录/包命名，降低新人理解成本，避免重复语义目录导致的维护风险。

本次聚焦：
- `infrastructure/mq` 与 `infrastructure/messaging/consumer` 合并
- `infrastructure/mongodb` 与 `infrastructure/persistence/mongo` 合并
- `PostAssembler` 重名拆分
- 清理空目录与无引用文件

## 0.1 当前执行进度（2026-02-24）
- [x] 主题 A：`infrastructure/mq` 消费者已迁移到 `infrastructure/messaging/consumer`，包名已统一。
- [x] 主题 B：`infrastructure/mongodb` 的 `document/repository` 已迁到 `infrastructure/persistence/mongo`，引用与 `@EnableMongoRepositories` 已更新。
- [x] 主题 C：`PostAssembler` 重名已拆分为 `PostViewAssembler`（应用层）与 `PostApiAssembler`（接口层）。
- [x] 主题 D：`SaveDraftCommand` 已清理；空目录（`mq`/`mongodb`/`feign`）已删除；`.iml` 已更名为 `zhicore-content.iml`。
- [x] 补充清理：`.skip` Mongo 测试路径和 package 已迁移到 `infrastructure/persistence/mongo/repository`。
- [x] 已补充 PR 拆分清单：`docs/naming-refactor-pr-split.md`。

## 1. 执行原则
- 一次只做一个主题迁移，主题间分 PR，避免大爆炸。
- 每个主题必须满足：编译通过 + 核心测试通过 + 启动 smoke 通过。
- 优先“包名与目录一致”，避免目录迁了但 package 未迁。

## 2. 预检查（必须）
1. 建分支：`refactor/naming-unification`
2. 记录现状：
   - `rg --files src/main/java/com/zhicore/content/infrastructure/mq`
   - `rg --files src/main/java/com/zhicore/content/infrastructure/messaging/consumer`
   - `rg --files src/main/java/com/zhicore/content/infrastructure/mongodb`
   - `rg --files src/main/java/com/zhicore/content/infrastructure/persistence/mongo`
3. 全量编译基线：`mvn -q -DskipTests compile`

验收标准：
- 当前分支可编译。
- 基线文件清单已保存到 PR 描述或迁移记录。

## 3. 迁移主题 A：消息目录统一（先做）
目标：统一到 `infrastructure/messaging`，去掉 `infrastructure/mq`。

### 步骤
1. 将 `infrastructure/mq` 下消费者类迁移到 `infrastructure/messaging/consumer`。
2. 修改 package 声明为 `com.zhicore.content.infrastructure.messaging.consumer`。
3. 修复所有 import 引用：
   - `rg -n "infrastructure\\.mq|PostScheduleConsumer|UserProfileChangedConsumer|AuthorInfoCompensationConsumer" src/main/java src/test/java`
4. 删除空目录 `infrastructure/mq`。

### 验收
- `rg -n "infrastructure\\.mq" src/main/java src/test/java` 结果为空。
- 编译通过：`mvn -q -DskipTests compile`。

## 4. 迁移主题 B：Mongo 目录统一
目标：统一到 `infrastructure/persistence/mongo`，逐步废弃 `infrastructure/mongodb`。

### 步骤
1. 对 `infrastructure/mongodb/document/*` 和 `.../repository/*` 做归并设计：
   - 若功能重复，保留一套（推荐保留 `persistence/mongo`）。
   - 若两套都被使用，先引入过渡适配层再淘汰旧目录。
2. 迁移文件后统一 package 为 `com.zhicore.content.infrastructure.persistence.mongo...`
3. 修复引用：
   - `rg -n "infrastructure\\.mongodb" src/main/java src/test/java`
4. 删除旧目录（确认无引用后）。

### 验收
- `rg -n "infrastructure\\.mongodb" src/main/java src/test/java` 结果为空。
- 编译通过并执行 Mongo 相关测试（若可用）。

## 5. 迁移主题 C：Assembler 命名去重
目标：移除 `PostAssembler` 重名带来的认知冲突。

### 建议命名
- `interfaces/assembler/PostApiAssembler`
- `application/assembler/PostViewAssembler`（或 `PostDtoAssembler`）

### 步骤
1. 重命名类文件与类名。
2. 修正 package 内引用与静态调用。
3. 检查重复类名残留：
   - `rg -n "class PostAssembler|PostAssembler\\." src/main/java src/test/java`

### 验收
- 代码中仅保留新命名。
- 编译通过，相关 controller/service 测试通过。

## 6. 迁移主题 D：清理无效结构
目标：去除误导性结构。

### 处理项
1. 删除空目录：`infrastructure/feign`
2. 处理无引用文件：
   - `domain/command/SaveDraftCommand.java`（确认后删除或迁到 `application/command` 并接入）
3. `.iml` 文件名统一为模块名（`zhicore-content.iml`）

### 验收
- `rg -n "domain\\.command\\.SaveDraftCommand|SaveDraftCommand" src/main/java src/test/java` 与预期一致。
- 工程导入/编译不受影响。

## 7. 文档统一
1. 固定使用 `docs/` 目录，不再新增 `doc/`。
2. 将命名迁移结果写入：
   - `docs/NEW-ARCHITECTURE.md`（更新目录结构）
   - `docs/DEVELOPMENT-GUIDE.md`（更新包名与约定）

验收：
- 文档中的目录树与实际代码一致。

## 8. 回归与发布前检查
1. 编译：`mvn -q -DskipTests compile`
2. 单测（至少核心模块）：`mvn test -Dtest=*Post*`
3. 启动检查：
   - 应用可启动
   - 关键接口 smoke：
     - 创建 post
     - 发布 post
     - 详情查询
     - 点赞/收藏
4. Outbox 流程验证：
   - 生成一条事件并确认进入 `outbox_event`
   - Dispatcher 可正常投递

## 9. 推荐 PR 切分
1. PR-1：消息目录统一（mq -> messaging）
2. PR-2：mongo 目录统一（mongodb -> persistence/mongo）
3. PR-3：assembler 重命名 + 无效结构清理
4. PR-4：文档同步 + 规范落地（lint/约束）

## 10. 可选增强（防回归）
- 增加 ArchUnit/自定义检查，禁止新增以下目录：
  - `infrastructure.mq`
  - `infrastructure.mongodb`
- 增加 CI grep 校验，发现旧包名直接失败。
