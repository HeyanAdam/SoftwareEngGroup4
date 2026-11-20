package com.group4.kaoyan.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON 数组列与 {@code List<String>} 互转。
 *
 * <p>不做全局注册（{@code MappedTypes(List.class)} 会把所有 List 属性都抢走），
 * 只在需要的 XML 语句里用 {@code typeHandler} 显式指定。</p>
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonTypeHandler.toJson(parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<?> raw = JsonTypeHandler.fromJson(json, List.class);
        List<String> result = new ArrayList<>();
        if (raw != null) {
            for (Object item : raw) {
                result.add(item == null ? null : String.valueOf(item));
            }
        }
        return result;
    }
}
