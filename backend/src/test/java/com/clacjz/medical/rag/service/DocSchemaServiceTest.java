package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocSchemaService 单元测试")
class DocSchemaServiceTest {

    private DocSchemaService docSchemaService;

    @BeforeEach
    void setUp() {
        docSchemaService = new DocSchemaService();
        // 手动设置字段值模拟 PostConstruct 后的状态
        try {
            var field = DocSchemaService.class.getDeclaredField("manualSchema");
            field.setAccessible(true);
            field.set(docSchemaService, "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        } catch (Exception e) {
            // 如果无法设置，仍然测试返回值
        }
    }

    @Nested
    @DisplayName("getManualSchema")
    class GetManualSchemaTests {

        @Test
        @DisplayName("getManualSchema返回非null字符串")
        void shouldReturnSchema() {
            String schema = docSchemaService.getManualSchema();
            assertNotNull(schema);
        }

        @Test
        @DisplayName("schema可以设置为有效JSON")
        void shouldAllowValidJsonSchema() {
            try {
                var field = DocSchemaService.class.getDeclaredField("manualSchema");
                field.setAccessible(true);
                String validSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";
                field.set(docSchemaService, validSchema);

                String result = docSchemaService.getManualSchema();
                assertEquals(validSchema, result);
            } catch (Exception e) {
                fail("Should be able to set and get schema: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("schema包含type字段")
        void shouldContainTypeField() {
            String schema = docSchemaService.getManualSchema();
            assertTrue(schema.contains("\"type\""), "Schema should contain 'type' field");
        }

        @Test
        @DisplayName("schema包含properties字段")
        void shouldContainPropertiesField() {
            String schema = docSchemaService.getManualSchema();
            assertTrue(schema.contains("\"properties\""), "Schema should contain 'properties' field");
        }

        @Test
        @DisplayName("多次调用返回相同值")
        void shouldReturnSameValueOnMultipleCalls() {
            String first = docSchemaService.getManualSchema();
            String second = docSchemaService.getManualSchema();
            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("init - PostConstruct初始化")
    class InitTests {

        @Test
        @DisplayName("init方法存在且可调用")
        void initMethodShouldExist() {
            assertDoesNotThrow(() -> {
                var method = DocSchemaService.class.getDeclaredMethod("init");
                assertNotNull(method);
            });
        }

        @Test
        @DisplayName("PostConstruct注解存在")
        void initShouldHavePostConstructAnnotation() throws NoSuchMethodException {
            var method = DocSchemaService.class.getDeclaredMethod("init");
            assertTrue(method.isAnnotationPresent(jakarta.annotation.PostConstruct.class),
                    "init method should have @PostConstruct annotation");
        }
    }
}
