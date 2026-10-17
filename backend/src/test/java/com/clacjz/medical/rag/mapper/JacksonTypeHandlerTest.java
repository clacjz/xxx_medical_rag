package com.clacjz.medical.rag.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JacksonTypeHandler 单元测试")
class JacksonTypeHandlerTest {

    private JacksonTypeHandler handler;
    private JacksonTypeHandler handlerWithCustomMapper;

    @Mock
    private PreparedStatement ps;

    @Mock
    private ResultSet rs;

    @Mock
    private CallableStatement cs;

    @BeforeEach
    void setUp() {
        handler = new JacksonTypeHandler();
        handlerWithCustomMapper = new JacksonTypeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("构造函数")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造函数应创建handler")
        void shouldCreateHandlerWithDefaultConstructor() {
            assertNotNull(new JacksonTypeHandler());
        }

        @Test
        @DisplayName("带ObjectMapper构造函数应创建handler")
        void shouldCreateHandlerWithObjectMapper() {
            assertNotNull(new JacksonTypeHandler(new ObjectMapper()));
        }
    }

    @Nested
    @DisplayName("setNonNullParameter")
    class SetNonNullParameterTests {

        @Test
        @DisplayName("应将Map序列化为JSON并设置参数")
        void shouldSerializeMapToJson() throws SQLException {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("key", "value");
            data.put("num", 42);

            handler.setNonNullParameter(ps, 1, data, null);

            verify(ps).setObject(eq(1), argThat(json -> {
                String s = (String) json;
                return s.contains("\"key\"") && s.contains("\"value\"") && s.contains("\"num\"");
            }), eq(Types.OTHER));
        }

        @Test
        @DisplayName("应将字符串对象序列化并设置参数")
        void shouldSerializeStringObject() throws SQLException {
            handler.setNonNullParameter(ps, 2, "test-string", null);

            verify(ps).setObject(eq(2), anyString(), eq(Types.OTHER));
        }

        @Test
        @DisplayName("应使用Types.OTHER设置JDBC类型")
        void shouldUseTypesOther() throws SQLException {
            Map<String, String> data = Map.of("key", "val");
            handler.setNonNullParameter(ps, 1, data, null);

            verify(ps).setObject(eq(1), anyString(), eq(Types.OTHER));
        }

        @Test
        @DisplayName("自定义ObjectMapper应正常工作")
        void shouldWorkWithCustomMapper() throws SQLException {
            Map<String, String> data = Map.of("field", "val");
            handlerWithCustomMapper.setNonNullParameter(ps, 1, data, null);

            verify(ps).setObject(eq(1), anyString(), eq(Types.OTHER));
        }
    }

    @Nested
    @DisplayName("getNullableResult - 通过列名")
    class GetNullableResultByColumnNameTests {

        @Test
        @DisplayName("应将JSON字符串反序列化为LinkedHashMap")
        void shouldDeserializeJsonToLinkedHashMap() throws SQLException {
            String json = "{\"name\":\"test\",\"count\":5}";
            when(rs.getString("data")).thenReturn(json);

            Object result = handler.getNullableResult(rs, "data");

            assertNotNull(result);
            assertInstanceOf(LinkedHashMap.class, result);
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) result;
            assertEquals("test", map.get("name"));
            assertEquals(5, map.get("count"));
        }

        @Test
        @DisplayName("null值应返回null")
        void shouldReturnNullForNullValue() throws SQLException {
            when(rs.getString("data")).thenReturn(null);

            Object result = handler.getNullableResult(rs, "data");

            assertNull(result);
        }

        @Test
        @DisplayName("空字符串应返回null")
        void shouldReturnNullForBlankString() throws SQLException {
            when(rs.getString("data")).thenReturn("   ");

            Object result = handler.getNullableResult(rs, "data");

            assertNull(result);
        }

        @Test
        @DisplayName("空字符串应返回null")
        void shouldReturnNullForEmptyString() throws SQLException {
            when(rs.getString("data")).thenReturn("");

            Object result = handler.getNullableResult(rs, "data");

            assertNull(result);
        }

        @Test
        @DisplayName("嵌套JSON应正确解析")
        void shouldDeserializeNestedJson() throws SQLException {
            String json = "{\"outer\":{\"inner\":\"value\"}}";
            when(rs.getString("data")).thenReturn(json);

            Object result = handler.getNullableResult(rs, "data");

            assertNotNull(result);
            assertInstanceOf(LinkedHashMap.class, result);
        }

        @Test
        @DisplayName("无效JSON应抛出SQLException")
        void shouldThrowForInvalidJson() throws SQLException {
            when(rs.getString("data")).thenReturn("{invalid json}");

            assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "data"));
        }
    }

    @Nested
    @DisplayName("getNullableResult - 通过列索引")
    class GetNullableResultByColumnIndexTests {

        @Test
        @DisplayName("应将JSON字符串反序列化为LinkedHashMap")
        void shouldDeserializeJsonByColumnIndex() throws SQLException {
            String json = "{\"key\":\"value\"}";
            when(rs.getString(1)).thenReturn(json);

            Object result = handler.getNullableResult(rs, 1);

            assertNotNull(result);
            assertInstanceOf(LinkedHashMap.class, result);
        }

        @Test
        @DisplayName("null值应返回null")
        void shouldReturnNullForNullValue() throws SQLException {
            when(rs.getString(1)).thenReturn(null);

            Object result = handler.getNullableResult(rs, 1);

            assertNull(result);
        }

        @Test
        @DisplayName("无效JSON应抛出SQLException")
        void shouldThrowForInvalidJson() throws SQLException {
            when(rs.getString(1)).thenReturn("not json");

            assertThrows(SQLException.class, () -> handler.getNullableResult(rs, 1));
        }
    }

    @Nested
    @DisplayName("getNullableResult - 通过CallableStatement")
    class GetNullableResultFromCallableStatementTests {

        @Test
        @DisplayName("应将JSON字符串反序列化为LinkedHashMap")
        void shouldDeserializeJsonFromCallableStatement() throws SQLException {
            String json = "{\"key\":\"value\"}";
            when(cs.getString(1)).thenReturn(json);

            Object result = handler.getNullableResult(cs, 1);

            assertNotNull(result);
            assertInstanceOf(LinkedHashMap.class, result);
        }

        @Test
        @DisplayName("null值应返回null")
        void shouldReturnNullForNullValue() throws SQLException {
            when(cs.getString(1)).thenReturn(null);

            Object result = handler.getNullableResult(cs, 1);

            assertNull(result);
        }

        @Test
        @DisplayName("无效JSON应抛出SQLException")
        void shouldThrowForInvalidJson() throws SQLException {
            when(cs.getString(1)).thenReturn("broken json");

            assertThrows(SQLException.class, () -> handler.getNullableResult(cs, 1));
        }
    }

    @Nested
    @DisplayName("LinkedHashMap保留插入顺序")
    class LinkedHashMapOrderPreservationTests {

        @Test
        @DisplayName("应保留JSON字段的插入顺序")
        void shouldPreserveInsertionOrder() throws SQLException {
            String json = "{\"first\":1,\"second\":2,\"third\":3}";
            when(rs.getString("data")).thenReturn(json);

            Object result = handler.getNullableResult(rs, "data");

            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) result;
            var keys = map.keySet().toArray(new String[0]);
            assertArrayEquals(new String[]{"first", "second", "third"}, keys);
        }
    }
}
