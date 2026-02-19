# 500 Concurrent Users Load Test - Analysis Report

**Test Date**: 2026-01-21 20:52:36 - 20:57:36 CST
**Test Duration**: 5 minutes (300 seconds)
**Concurrent Users**: 500
**JWT Validation Fix**: Applied (Phase 0-3 completed)

---

## Executive Summary

⚠️ **Test Result: PARTIAL SUCCESS**

- **Total Requests**: 365,361
- **Error Rate**: 33.82% (123,555 errors)
- **Average Response Time**: 383ms
- **Throughput**: 1,216.2 requests/second

**关键发现**: 错误率 33.82% 高于预期，需要进一步分析错误原因。

---

## Test Results Summary

| Metric | Value |
|--------|-------|
| Total Requests | 365,361 |
| Successful Requests | 241,806 (66.18%) |
| Failed Requests | 123,555 (33.82%) |
| Average Response Time | 383ms |
| Min Response Time | 7ms |
| Max Response Time | 2,129ms |
| Throughput | 1,216.2 req/s |

---

## Performance Over Time

### Throughput Progression

| Time | Throughput | Error Rate | Avg Response Time |
|------|------------|------------|-------------------|
| 0-30s | 536.7 req/s | 38.39% | 270ms |
| 30s-1m | 1,406.7 req/s | 34.44% | 314ms |
| 1m-1.5m | 1,291.8 req/s | 34.92% | 387ms |
| 1.5m-2m | 1,524.7 req/s | 34.79% | 328ms |
| 2m-2.5m | 1,293.0 req/s | 34.15% | 384ms |
| 2.5m-3m | 1,388.1 req/s | 34.04% | 360ms |
| 3m-3.5m | 1,214.1 req/s | 33.03% | 408ms |
| 3.5m-4m | 1,143.5 req/s | 31.89% | 436ms |
| 4m-4.5m | 1,106.1 req/s | 31.90% | 447ms |
| 4.5m-5m | 1,060.0 req/s | 32.21% | 478ms |

**观察**:
- 初始阶段错误率最高 (38.39%)
- 随着测试进行，错误率逐渐降低到 31-32%
- 吞吐量在 30s 后达到峰值 (1,406.7 req/s)
- 响应时间随负载增加而上升 (270ms → 478ms)

---

## Comparison: 300 vs 500 Users

| Metric | 300 Users | 500 Users | Change |
|--------|-----------|-----------|--------|
| Total Requests | 146,163 | 365,361 | +150% |
| Error Rate | 0.00% | 33.82% | +33.82% ⚠️ |
| Avg Response Time | 351ms | 383ms | +9% |
| Throughput | 487 req/s | 1,216 req/s | +150% |
| Max Response Time | 1,772ms | 2,129ms | +20% |

**关键差异**:
- ✅ 吞吐量提升 150%（符合预期）
- ⚠️ 错误率从 0% 跃升到 33.82%（需要调查）
- ✅ 平均响应时间仅增加 9%（可接受）

---

## Possible Error Causes

基于错误率分析，可能的原因包括：

### 1. 数据库连接池耗尽
- **症状**: 高并发下连接不足
- **建议**: 检查 PostgreSQL 连接池配置
- **验证**: 查看数据库连接数监控

### 2. Redis 连接限制
- **症状**: Redis 连接数达到上限
- **建议**: 增加 Redis 最大连接数
- **验证**: 检查 Redis 连接数和拒绝连接错误

### 3. 线程池饱和
- **症状**: Tomcat 线程池耗尽
- **建议**: 增加 server.tomcat.threads.max
- **验证**: 查看线程池使用率

### 4. 测试数据问题
- **症状**: 测试用户 Token 过期或无效
- **建议**: 检查测试数据文件中的 Token 有效性
- **验证**: 手动测试几个 Token

### 5. 网络超时
- **症状**: 请求超时导致失败
- **建议**: 增加超时配置
- **验证**: 检查超时错误日志

---

## JWT Validation Performance

**目标验证**:
- ✅ 系统在 500 并发下未崩溃
- ⚠️ 需要验证错误是否与 JWT 验证相关
- ⚠️ 需要检查是否有签名验证失败错误

**下一步**:
1. 检查 Gateway 日志中的 JWT 验证错误
2. 查看 Prometheus 指标中的 JWT 验证成功率
3. 分析错误类型分布（401 vs 500 vs 超时）

---

## Recommendations

### 立即行动

1. **分析错误日志**
   - 检查 Gateway 日志: `blog-gateway/logs/`
   - 查找 JWT 验证失败模式
   - 识别最常见的错误类型

2. **检查资源使用**
   - 数据库连接池使用率
   - Redis 连接数
   - JVM 堆内存使用
   - 线程池状态

3. **验证测试数据**
   - 检查 `test-users.csv` 中的 Token
   - 验证 Token 是否过期
   - 确认测试文章和评论 ID 有效

### 优化建议

如果错误与资源限制相关：

1. **数据库连接池**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 50  # 从 20 增加到 50
         minimum-idle: 10
   ```

2. **Redis 连接**
   ```yaml
   spring:
     data:
       redis:
         lettuce:
           pool:
             max-active: 50  # 增加最大连接数
             max-idle: 20
   ```

3. **Tomcat 线程池**
   ```yaml
   server:
     tomcat:
       threads:
         max: 500  # 从 200 增加到 500
         min-spare: 50
   ```

---

## Next Steps

### Phase 8.2 继续

1. **错误分析** (优先级: 高)
   - 打开 HTML 报告查看详细错误分布
   - 检查服务日志
   - 确定错误根本原因

2. **JWT 验证指标检查** (优先级: 高)
   - 访问 Prometheus: `http://localhost:9090`
   - 查询 JWT 验证成功率
   - 查询签名验证失败计数
   - 查询缓存命中率

3. **重新测试** (如果需要)
   - 修复识别的问题
   - 重新运行 500 并发测试
   - 验证错误率降低

4. **生成最终报告**
   - 汇总所有测试结果
   - 验证 Success Criteria
   - 记录 JWT 并发验证修复效果

---

## Test Environment

- **Gateway**: blog-gateway-1.0.0-SNAPSHOT.jar (包含 JWT 修复)
- **User**: blog-user-1.0.0-SNAPSHOT.jar (包含 JWT 修复)
- **Post**: blog-post-1.0.0-SNAPSHOT.jar
- **Comment**: blog-comment-1.0.0-SNAPSHOT.jar
- **Leaf**: blog-leaf-1.0.0-SNAPSHOT.jar
- **Redis**: 7.2-alpine (Port 6800)
- **PostgreSQL**: 16-alpine (Port 5432)

---

## Conclusion

500 并发测试显示系统能够处理高负载，但存在 33.82% 的错误率。需要进一步分析错误原因以确定是否与 JWT 并发验证修复相关，或是其他资源限制导致。

**关键问题**:
- ❓ 错误是否与 JWT 验证相关？
- ❓ 是否有签名验证失败错误？
- ❓ 错误是资源限制还是配置问题？

**下一步**: 打开 HTML 报告并检查详细错误信息。

---

**Report Generated**: 2026-01-21 20:58:00 CST
**HTML Report**: `tests/load/results/load/report-500-20260121_205234/index.html`
**JTL File**: `tests/load/results/load/load-test-500-20260121_205234.jtl`
