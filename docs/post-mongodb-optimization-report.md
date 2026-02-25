# Post-MongoDB 混合存储架构优化方案

本文档记录了在设计 `post-mongodb-hybrid` 架构过程中识别出的关键风险点及其优化方案。

## 1. 数据一致性保障：三态提交机制

### 问题分析
采用 "Best Effort" 的双写模式（先写 PG，再写 Mongo）存在数据不一致风险。例如，MongoDB 写入成功但 PostgreSQL 事务提交超时或失败回滚，会导致 MongoDB 中产生无法关联的"孤儿数据"。

### 优化方案
引入 **"三态提交" (Three-State Commit)** 状态机，配合定时检查机制，确保最终一致性。

**流程：**
1.  **Pending (PG)**: 在 PostgreSQL 中插入元数据，状态设为 `PUBLISHING` (中间态)。
2.  **Write (Mongo)**: 将内容写入 MongoDB。
3.  **Commit (PG)**: 更新 PostgreSQL 中的状态为 `PUBLISHED` (终态)。

**异常处理：**
*   若流程在任意步骤中断，PostgreSQL 中的记录会保持 `PUBLISHING` 状态或被回滚消失。
*   **补偿机制**：`ConsistencyChecker` 定时扫描长时间处于 `PUBLISHING` 状态的记录，或扫描 MongoDB 中通过 `postId` 无法在 PG 找到对应元数据的孤儿文档，进行清理或重试。

## 2. API 架构优化：读写分离与按需加载

### 问题分析
原设计中 `getPostDetail` 接口强制聚合 PostgreSQL 的元数据和 MongoDB 的内容。在大流量场景下（如文章列表流、搜索结果页），这种全量聚合会带来不必要的 MongoDB I/O 开销和网络传输压力。

### 优化方案
采用 **CQRS (命令查询职责分离)** 思想，拆分读取接口：

1.  **`listPosts` (Metadata Only)**:
    *   仅查询 PostgreSQL。
    *   **场景**：首页列表、分类列表、管理后台列表。
    *   **优势**：极速响应，无 MongoDB 压力。

2.  **`getPostFullDetail` (Aggregated)**:
    *   聚合 PG 元数据 + Mongo 内容。
    *   **场景**：搜索引擎索引构建、编辑器回显。

3.  **`getPostContent` (Content Only)**:
    *   仅查询 MongoDB。
    *   **场景**：前端阅读页面的内容懒加载 (Lazy Load)。

## 3. 存储模型精简：彻底释放 PG 压力

### 问题分析
如果在迁移后仍然在 PostgreSQL 的 `posts` 表中保留 `raw` (Markdown) 和 `html` 字段作为备份，将无法达成"减轻关系型数据库存储压力"的核心目标，且造成双倍存储成本。

### 优化方案
*   **Schema 变更**：明确从 PostgreSQL 的 `posts` 表中移除 `raw` 和 `html` 字段。
*   **收益**：
    *   显著减小 PG 表的 Page Size，提升 Buffer Pool 的缓存命中率。
    *   减少 PG 的 WAL 日志量和 VACUUM 开销。

## 4. 草稿存储优化：Upsert 策略

### 问题分析
自动保存 (Auto-Save) 功能触发频率高（如每 30 秒）。如果每次保存都 Insert 一条新记录到 MongoDB，会导致文档数量呈指数级增长，浪费存储空间。

### 优化方案
*   **唯一索引 constraint**：在 MongoDB `post_drafts` 集合建立联合唯一索引 `{ postId: 1, userId: 1 }`。
*   **Upsert 操作**：使用 `save` 或 `update(upsert=true)` 操作。
*   **效果**：确保每个用户对每篇文章只保留一份最新的草稿副本，避免数据爆炸。历史版本管理交由正式的 `post_versions` 集合处理（只有用户显式发布或保存版本时才创建）。
