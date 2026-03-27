package com.zhicore.notification.infrastructure.repository.typehandler;

import com.zhicore.common.util.DateTimeUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * PostgreSQL TIMESTAMPTZ 到 LocalDateTime 的映射处理器。
 */
@MappedJdbcTypes(JdbcType.TIMESTAMP_WITH_TIMEZONE)
@MappedTypes(LocalDateTime.class)
public class OffsetDateTimeToLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, DateTimeUtils.toOffsetDateTime(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return mapDateTime(rs.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return mapDateTime(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return mapDateTime(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    private LocalDateTime mapDateTime(OffsetDateTime offsetDateTime) {
        return DateTimeUtils.toLocalDateTime(offsetDateTime);
    }
}
