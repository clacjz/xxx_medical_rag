package com.clacjz.medical.rag.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JsonbTypeHandler 单元测试")
class JsonbTypeHandlerTest {

    private final JsonbTypeHandler handler = new JsonbTypeHandler();

    @Mock
    private PreparedStatement ps;

    @Mock
    private ResultSet rs;

    @Mock
    private CallableStatement cs;

    @Test
    @DisplayName("setNonNullParameter 应使用 Types.OTHER 设置参数")
    void setNonNullParameter_ShouldUseTypesOther() throws SQLException {
        String json = "[\"2057474339524153345\"]";

        handler.setNonNullParameter(ps, 1, json, null);

        verify(ps).setObject(1, json, Types.OTHER);
    }

    @Test
    @DisplayName("getNullableResult - 通过列名获取")
    void getNullableResult_ByColumnName() throws SQLException {
        String json = "[\"test-id\"]";
        when(rs.getString("collection_ids")).thenReturn(json);

        String result = handler.getNullableResult(rs, "collection_ids");

        assertEquals(json, result);
    }

    @Test
    @DisplayName("getNullableResult - 通过列名获取返回 null")
    void getNullableResult_ByColumnName_Null() throws SQLException {
        when(rs.getString("collection_ids")).thenReturn(null);

        String result = handler.getNullableResult(rs, "collection_ids");

        assertNull(result);
    }

    @Test
    @DisplayName("getNullableResult - 通过列索引获取")
    void getNullableResult_ByColumnIndex() throws SQLException {
        String json = "{\"key\": \"value\"}";
        when(rs.getString(1)).thenReturn(json);

        String result = handler.getNullableResult(rs, 1);

        assertEquals(json, result);
    }

    @Test
    @DisplayName("getNullableResult - 通过 CallableStatement 获取")
    void getNullableResult_FromCallableStatement() throws SQLException {
        String json = "\"test\"";
        when(cs.getString(1)).thenReturn(json);

        String result = handler.getNullableResult(cs, 1);

        assertEquals(json, result);
    }
}
