package com.zhicore.content.infrastructure.persistence.pg.typehandler;

import com.zhicore.common.util.DateTimeUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * PostgreSQL timestamp 到 OffsetDateTime 的统一映射。
 *
 * content 模块当前大量时间列仍使用 timestamp（无时区），
 * 这里统一按应用系统时区做读写转换，避免 JDBC 默认映射把本地时间漂成 UTC 语义。
 */
@MappedJdbcTypes({JdbcType.TIMESTAMP, JdbcType.TIMESTAMP_WITH_TIMEZONE})
@MappedTypes(OffsetDateTime.class)
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, DateTimeUtils.toTimestamp(parameter));
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toOffsetDateTime(rs.getTimestamp(columnName));
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toOffsetDateTime(rs.getTimestamp(columnIndex));
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toOffsetDateTime(cs.getTimestamp(columnIndex));
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return DateTimeUtils.toOffsetDateTime(timestamp);
    }
}
