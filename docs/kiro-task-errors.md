# Kiro 任务执行错误记录

本文件用于记录在执行 `.kiro/specs/zhicore-content-architecture-fixes` 相关任务过程中遇到的编译/测试错误及修复过程，便于后续追溯。

## 2026-02-24

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

### Maven 构建失败：`zhicore-integration` 依赖缺少版本

- 现象：执行 `mvn -pl zhicore-content -am test` 时报错：`'dependencies.dependency.version' for com.zhicore:zhicore-integration:jar is missing`。
- 原因：
  - `zhicore-content/pom.xml` 引入了 `zhicore-integration` 依赖，但父工程 `dependencyManagement` 未管理该依赖版本；
  - 同时父工程 `modules` 未纳入 `zhicore-integration`，导致 reactor 无法构建该模块。
- 修复：在父工程 `pom.xml` 中补充 `<module>zhicore-integration</module>`，并在 `dependencyManagement` 中管理 `zhicore-integration` 版本为 `${project.version}`；同时补齐 `zhicore-integration` 模块代码，使 `zhicore-content` 可正常构建与测试。

### zhicore-client 单测失败：ID 服务降级码不一致

- 现象：执行 `mvn -pl zhicore-content -am test` 时，`zhicore-client` 的 `IdGeneratorFeignClientFallbackFactoryTest` 断言失败：期望 `code=500`，实际为 `503`。
- 原因：在 R16（标签 ID 统一接入 ID 服务）实现中，ID 服务不可用按约定返回 `503 Service Unavailable`，但 `zhicore-client` 旧测试仍按 `500` 断言。
- 修复：将相关测试用例的断言从 `500` 更新为 `503`，与当前降级契约保持一致。

### zhicore-content 测试失败：测试环境缺少 ID 服务桩

- 现象：执行 `mvn -pl zhicore-content -am test` 时，多处测试在 `setUp()` 阶段抛出 `ServiceUnavailableException: ID 服务不可用，无法创建标签`。
- 原因：`TagDomainServiceImpl` 在创建标签时会调用 `IdGeneratorFeignClient` 获取 ID；但测试环境未启动 `zhicore-id-generator`，导致 Feign 调用失败并按契约返回 503，进而抛出异常。
- 修复：在 `test` profile 下为 `IdGeneratorFeignClient` 提供 `@Primary` 的本地桩实现（返回自增 ID），避免集成测试依赖外部 ID 服务。

### RedissonLockManagerImplIntegrationTest 失败：锁重入与缓存击穿场景建模不一致

- 现象：`shouldNotAcquireSameLockTwiceInSameThread` 断言失败（第二次获取锁实际返回 `true`），`shouldPreventCacheBreakthrough` 统计的“数据库查询次数”明显偏大。
- 原因：
  - Redisson 的 `RLock` 默认支持重入，同一线程重复 `tryLock` 会成功；
  - 该测试用例的“缓存击穿”场景仅模拟了加锁后的数据库访问，但缺少“首次查询后缓存已写入，后续请求应命中缓存”的逻辑建模，导致锁释放后仍会被后续线程继续获取并执行查询。
- 修复：
  - 在 `LockManager` 实现中增加“当前线程已持有同一把锁则直接返回失败”的保护（避免误用造成重入）；
  - 调整测试的击穿场景：在持锁区模拟“首次查询后写入缓存”，后续线程即使获取到锁也不再重复执行查询。
