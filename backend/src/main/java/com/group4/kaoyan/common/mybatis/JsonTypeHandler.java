package com.group4.kaoyan.common.mybatis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL JSON 列与 Java 对象互转。
 *
 * <p>MySQL 的 JSON 列对 JDBC 只暴露成字符串，原生 MyBatis 默认按 String 取值。
 * 这里统一走 Jackson：写库 {@code ps.setString}（MySQL 8 会校验 JSON 格式），
 * 读库 {@code fromJson} 还原成对象。全局注册由 {@code type-handlers-package} 完成，
 * 也可以在 resultMap 里用 {@code typeHandler="..."} 显式指定。</p>
 *
 * <p>注意：本类只用于 JSON 列，不要给普通 VARCHAR 列使用。</p>
 */
@MappedTypes(Object.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromJson(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromJson(cs.getString(columnIndex));
    }

    /** 序列化为 JSON 文本；null 返回 null，交给数据库写 NULL。 */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            // 已经是 JSON 文本时原样写入，避免二次转义成 "\"{...}\""
            return text;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 序列化失败：" + e.getMessage(), e);
        }
    }

    /** 反序列化为通用对象（Map / List）。空串与 null 都返回 null。 */
    public static Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败：" + e.getMessage(), e);
        }
    }

    /** 反序列化为指定类型，供 service 需要强类型时使用。 */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败：" + e.getMessage(), e);
        }
    }
}
