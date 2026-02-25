package com.zhicore.content.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.Properties;

/**
 * 慢查询拦截器
 * 
 * 记录执行时间超过阈值的 SQL 查询，用于性能分析和优化
 * 使用 @ConfigurationProperties 支持配置动态刷新
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Intercepts({
    @Signature(
        type = StatementHandler.class,
        method = "query",
        args = {Statement.class, ResultHandler.class}
    ),
    @Signature(
        type = StatementHandler.class,
        method = "update",
        args = {Statement.class}
    )
})
public class SlowQueryInterceptor implements Interceptor {

    private final PerformanceProperties performanceProperties;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!performanceProperties.isSlowQueryLogEnabled()) {
            return invocation.proceed();
        }

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        String sql = boundSql.getSql();

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        try {
            // 执行查询
            return invocation.proceed();
        } finally {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;

            // 记录慢查询
            if (executionTime > performanceProperties.getSlowQueryThresholdMs()) {
                log.warn("[SLOW QUERY] Execution time: {}ms (threshold: {}ms), SQL: {}", 
                    executionTime, performanceProperties.getSlowQueryThresholdMs(), formatSql(sql));
            } else if (log.isDebugEnabled()) {
                log.debug("[QUERY] Execution time: {}ms, SQL: {}", executionTime, formatSql(sql));
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // MyBatis 插件属性设置（如果需要）
        // 注意：我们现在使用 @ConfigurationProperties，这个方法可以保留为空
    }

    /**
     * 格式化 SQL（移除多余空白）
     */
    private String formatSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim();
    }
}
