package com.zhicore.content.infrastructure.persistence.pg.typehandler;

import com.zhicore.content.domain.model.TagId;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * TagId 类型处理器
 * 
 * 用于 MyBatis 自动转换 TagId 值对象和数据库 BIGINT 类型。
 * 
 * @author ZhiCore Team
 */
@MappedTypes(TagId.class)
@MappedJdbcTypes(JdbcType.BIGINT)
public class TagIdTypeHandler extends BaseTypeHandler<TagId> {
    
    /**
     * 设置非空参数（值对象转 Long）
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TagId parameter, JdbcType jdbcType) 
            throws SQLException {
        ps.setLong(i, parameter.getValue());
    }
    
    /**
     * 根据列名获取结果（Long 转值对象）
     */
    @Override
    public TagId getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : TagId.of(value);
    }
    
    /**
     * 根据列索引获取结果（Long 转值对象）
     */
    @Override
    public TagId getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long value = rs.getLong(columnIndex);
        return rs.wasNull() ? null : TagId.of(value);
    }
    
    /**
     * 从存储过程获取结果（Long 转值对象）
     */
    @Override
    public TagId getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long value = cs.getLong(columnIndex);
        return cs.wasNull() ? null : TagId.of(value);
    }
}
