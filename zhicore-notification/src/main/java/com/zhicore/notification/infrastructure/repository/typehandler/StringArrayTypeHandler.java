package com.zhicore.notification.infrastructure.repository.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PostgreSQL String数组类型处理器
 *
 * @author ZhiCore Team
 */
@MappedJdbcTypes(JdbcType.ARRAY)
@MappedTypes(List.class)
public class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("varchar", parameter.toArray());
        ps.setArray(i, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return extractList(rs.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return extractList(rs.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return extractList(cs.getArray(columnIndex));
    }

    private List<String> extractList(Array array) throws SQLException {
        if (array == null) {
            return new ArrayList<>();
        }
        Object[] objects = (Object[]) array.getArray();
        if (objects == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(objects)
                .map(obj -> obj != null ? obj.toString() : null)
                .filter(s -> s != null)
                .toList();
    }
}
