package com.clacjz.medical.rag.config.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class SqlDebugInterceptor implements InnerInterceptor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler,
                           BoundSql boundSql) {
        if (log.isDebugEnabled()) {
            String sql = getSqlString(boundSql);
            String methodName = ms.getId();
            log.debug("[SQL-BEFORE] Method: {}, SQL: {}", methodName, sql);
        }
    }

    private String getSqlString(BoundSql boundSql) {
        if (boundSql == null) {
            return "N/A";
        }

        String sql = boundSql.getSql();
        if (sql == null || sql.isEmpty()) {
            return "Empty SQL";
        }

        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }

        try {
            Object parameterObject = boundSql.getParameterObject();
            StringBuilder resolvedSql = new StringBuilder();
            int paramIndex = 0;

            int lastIndex = 0;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    resolvedSql.append(sql, lastIndex, i);
                    if (paramIndex < parameterMappings.size()) {
                        Object value = getParameterValue(parameterObject, paramIndex);
                        resolvedSql.append(formatParameterValue(value));
                        paramIndex++;
                    } else {
                        resolvedSql.append("?");
                    }
                    lastIndex = i + 1;
                }
            }
            if (lastIndex < sql.length()) {
                resolvedSql.append(sql.substring(lastIndex));
            }

            return resolvedSql.toString();
        } catch (Exception e) {
            log.trace("Failed to resolve SQL parameters: {}", e.getMessage());
            return sql;
        }
    }

    private Object getParameterValue(Object parameterObject, int index) {
        if (parameterObject == null) {
            return null;
        }

        if (parameterObject instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) parameterObject;
            Object[] keys = map.keySet().toArray();
            if (index < keys.length) {
                return map.get(keys[index]);
            }
        }

        return null;
    }

    private String formatParameterValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }

        if (value instanceof LocalDateTime) {
            return "'" + ((LocalDateTime) value).format(FORMATTER) + "'";
        }

        if (value instanceof java.time.LocalDate) {
            return "'" + value + "'";
        }

        if (value instanceof java.util.Date) {
            return "'" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
        }

        if (value instanceof byte[]) {
            return "[BINARY DATA, " + ((byte[]) value).length + " bytes]";
        }

        return String.valueOf(value);
    }
}