package com.zhicore.notification.integration;

import com.zhicore.notification.infrastructure.repository.mapper.NotificationMapper;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationGroupStateMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Notification DB fallback aggregation query regression test")
class NotificationAggregationFallbackQueryRegressionTest {

    @Test
    @DisplayName("mapper SQL should use deterministic representative row selection for DB fallback")
    void mapperSqlShouldUseDeterministicRepresentativeRowSelection() {
        String sql = loadQuerySql();

        assertTrue(sql.contains("ROW_NUMBER() OVER ("),
                "expected SQL to use window function for deterministic representative row selection");
        assertTrue(sql.contains("PARTITION BY n.type, n.target_type, n.target_id"),
                "expected SQL to partition within the notification group");
        assertTrue(sql.contains("ORDER BY n.created_at DESC, n.id DESC"),
                "expected SQL to break same-timestamp ties with notification id");
        assertTrue(sql.contains("ORDER BY l.latest_time DESC, l.latest_notification_id DESC"),
                "expected outer ranking to keep pagination order stable");
    }

    @Test
    @DisplayName("group state SQL should use deterministic ordering for stable pagination")
    void groupStateSqlShouldUseDeterministicOrdering() {
        String sql = loadQuerySql(NotificationGroupStateMapper.class, "findPage",
                Long.class, int.class, int.class, int.class);

        assertTrue(sql.contains("ORDER BY latest.created_at DESC, state.latest_notification_id DESC"),
                "expected group-state query to break same-timestamp ties with latest notification id");
    }

    private String loadQuerySql() {
        return loadQuerySql(NotificationMapper.class, "findAggregatedNotifications",
                Long.class, int.class, int.class);
    }

    private String loadQuerySql(Class<?> mapperType, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = mapperType.getMethod(methodName, parameterTypes);
            Select select = method.getAnnotation(Select.class);
            return String.join("\n", select.value());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to load mapper SQL", e);
        }
    }
}
