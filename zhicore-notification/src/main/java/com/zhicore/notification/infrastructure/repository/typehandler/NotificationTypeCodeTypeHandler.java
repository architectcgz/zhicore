package com.zhicore.notification.infrastructure.repository.typehandler;

import com.zhicore.notification.domain.model.NotificationType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 通知类型编码处理器。
 *
 * <p>聚合查询直接返回数据库中的整数编码，需要显式转换为领域枚举。</p>
 */
@MappedJdbcTypes(JdbcType.INTEGER)
@MappedTypes(NotificationType.class)
public class NotificationTypeCodeTypeHandler extends BaseTypeHandler<NotificationType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, NotificationType parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public NotificationType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return mapCode(rs.getInt(columnName), rs.wasNull());
    }

    @Override
    public NotificationType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return mapCode(rs.getInt(columnIndex), rs.wasNull());
    }

    @Override
    public NotificationType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return mapCode(cs.getInt(columnIndex), cs.wasNull());
    }

    private NotificationType mapCode(int code, boolean wasNull) {
        if (wasNull) {
            return null;
        }
        return NotificationType.fromCode(code);
    }
}
