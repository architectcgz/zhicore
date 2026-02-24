# Kiro 任务执行错误记录

本文件用于记录在执行 `.kiro/specs/zhicore-content-architecture-fixes` 相关任务过程中遇到的编译/测试错误及修复过程，便于后续追溯。

## 2026-02-25

### RedisCacheRepositoryImpl 编译失败

- 现象：执行 `mvn -pl zhicore-content -am -DskipTests compile` 时报错：`RedisCacheRepositoryImpl.java:[237,1] 需要 class、interface、enum 或 record`。
- 原因：`org.springframework.beans.factory.annotation.Value` 的 `import` 被错误地追加到了类文件末尾（在 `}` 之后），导致 Java 语法错误。
- 修复：将该 `import` 移动到文件顶部的 import 区域，并删除末尾的多余 `import`。

### TagDomainService 测试用例构造器不匹配

- 现象：执行指定测试用例时，`TagDomainServiceTest/TagDomainServicePropertyTest` 在 `testCompile` 阶段失败，提示 `TagDomainServiceImpl` 构造器参数不匹配。
- 原因：`TagDomainServiceImpl` 新增依赖 `IdGeneratorFeignClient`（R16），旧测试仅传入 `TagRepository`。
- 修复：为测试补充 `IdGeneratorFeignClient` Mock，并统一在构造器中注入。

### Testcontainers 容器生命周期导致连接拒绝

- 现象：集成测试中 PostgreSQL 容器日志显示已启动，但随后出现 `Connection refused`/Hikari 超时。
- 原因：
  - 原实现使用 `@AfterAll` 在每个测试类结束后停止容器；
  - 后续测试类复用同一 JVM/fork 时，仍使用已停止容器的连接信息，导致连接被拒绝。
- 修复：移除基类上的 `@AfterAll` 停止逻辑，改为在首次启动容器时注册 JVM ShutdownHook 统一清理，保证同一 fork 内多个测试类复用同一套容器。
