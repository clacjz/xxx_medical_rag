package com.clacjz.medical.rag.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;

/**
 * Jackson TypeHandler for MyBatis Plus JSON fields.
 * Deserializes JSON to LinkedHashMap to preserve insertion order,
 * then MyBatis Plus's autoResultMap mechanism handles type coercion to target fields.
 *
 * Uses Spring-configured ObjectMapper via constructor injection.
 */
public class JacksonTypeHandler extends BaseTypeHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(JacksonTypeHandler.class);

    private final ObjectMapper objectMapper;

    /**
     * Creates handler with a bare ObjectMapper (for non-Spring contexts).
     */
    public JacksonTypeHandler() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates handler with a custom ObjectMapper (e.g., Spring-configured).
     */
    public JacksonTypeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setObject(i, json, Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private Object parseJson(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON: {}", json, e);
            throw new SQLException("Failed to deserialize JSON to object", e);
        }
    }
}