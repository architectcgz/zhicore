/**
 * MongoDB 排行榜归档索引初始化脚本
 * 
 * 功能：为 ranking_archive 集合创建所有必需的索引
 * 
 * 使用方法：
 * 1. 确保 MongoDB 服务正在运行
 * 2. 在命令行中执行：
 *    mongosh mongodb://admin:mongo123456@localhost:27017/ZhiCore?authSource=admin init-ranking-indexes.js
 * 
 * 或者在 mongosh 中执行：
 *    use ZhiCore
 *    load('init-ranking-indexes.js')
 * 
 * @author ZhiCore Team
 * @date 2026-02-17
 */

// 切换到 ZhiCore 数据库
db = db.getSiblingDB('ZhiCore');

print('========================================');
print('开始创建 ranking_archive 集合索引');
print('========================================\n');

// 1. 复合索引：按类型和时间查询排行榜
print('创建索引 1/4: idx_type_period_rank');
print('用途：按实体类型、排行榜类型和时间周期查询排行榜');
db.ranking_archive.createIndex(
  {
    entityType: 1,
    rankingType: 1,
    'period.year': -1,
    'period.month': -1,
    'period.week': -1,
    rank: 1
  },
  { 
    name: 'idx_type_period_rank',
    background: true
  }
);
print('✓ 索引创建成功\n');

// 2. 实体查询索引：查询某实体的历史排名
print('创建索引 2/4: idx_entity_history');
print('用途：查询某个实体（文章/用户/话题）的历史排名');
db.ranking_archive.createIndex(
  {
    entityId: 1,
    entityType: 1,
    rankingType: 1,
    'period.year': -1
  },
  { 
    name: 'idx_entity_history',
    background: true
  }
);
print('✓ 索引创建成功\n');

// 3. 归档时间索引：用于数据清理和统计
print('创建索引 3/4: idx_archived_at');
print('用途：按归档时间查询，用于数据清理和统计分析');
db.ranking_archive.createIndex(
  {
    archivedAt: 1
  },
  { 
    name: 'idx_archived_at',
    background: true
  }
);
print('✓ 索引创建成功\n');

// 4. 唯一索引：防止重复归档
print('创建索引 4/4: uk_entity_period');
print('用途：防止同一实体在同一时间段重复归档');
print('注意：使用 partialFilterExpression 只对有效排名的文档应用唯一约束');
db.ranking_archive.createIndex(
  {
    entityId: 1,
    entityType: 1,
    rankingType: 1,
    'period.year': 1,
    'period.month': 1,
    'period.week': 1,
    'period.date': 1
  },
  { 
    name: 'uk_entity_period',
    unique: true,
    partialFilterExpression: { rank: { $type: 'int' } },
    background: true
  }
);
print('✓ 索引创建成功\n');

// 显示所有索引
print('========================================');
print('索引创建完成，当前所有索引：');
print('========================================\n');
db.ranking_archive.getIndexes().forEach(function(index) {
  print('索引名称: ' + index.name);
  print('索引键: ' + JSON.stringify(index.key));
  if (index.unique) {
    print('类型: 唯一索引');
  }
  if (index.partialFilterExpression) {
    print('部分过滤: ' + JSON.stringify(index.partialFilterExpression));
  }
  print('---');
});

print('\n========================================');
print('索引初始化完成！');
print('========================================');
print('\n提示：');
print('- 所有索引都使用 background: true 选项，不会阻塞数据库操作');
print('- 唯一索引使用 $type: "int" 作为 partial filter，只对有效排名的文档应用约束');
print('- 可以使用 db.ranking_archive.getIndexes() 查看所有索引');
print('- 可以使用 db.ranking_archive.dropIndex("索引名称") 删除索引');
