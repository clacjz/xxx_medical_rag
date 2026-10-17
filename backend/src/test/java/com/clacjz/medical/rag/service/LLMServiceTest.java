package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMService 单元测试")
class LLMServiceTest {

    @Nested
    @DisplayName("GenerateResult - 内部类")
    class GenerateResultTests {

        @Test
        @DisplayName("可以正确构建")
        void shouldBuildGenerateResult() {
            LLMService.GenerateResult result = new LLMService.GenerateResult(
                    "这是生成的回复",
                    "这是思考过程"
            );

            assertEquals("这是生成的回复", result.getContent());
            assertEquals("这是思考过程", result.getReasoning());
        }

        @Test
        @DisplayName("reasoning 可以为 null")
        void shouldAllowNullReasoning() {
            LLMService.GenerateResult result = new LLMService.GenerateResult(
                    "回复内容",
                    null
            );

            assertEquals("回复内容", result.getContent());
            assertNull(result.getReasoning());
        }

        @Test
        @DisplayName("两个字段都可以访问")
        void shouldAccessAllFields() {
            LLMService.GenerateResult result = new LLMService.GenerateResult(
                    "测试回复",
                    "思考过程"
            );

            assertNotNull(result.getContent());
            assertNotNull(result.getReasoning());
        }

        @Test
        @DisplayName("content 可以为空字符串")
        void shouldAllowEmptyContent() {
            LLMService.GenerateResult result = new LLMService.GenerateResult("", "");

            assertEquals("", result.getContent());
            assertEquals("", result.getReasoning());
        }

        @Test
        @DisplayName("支持多行内容")
        void shouldSupportMultilineContent() {
            String content = "第一行\n第二行\n第三行";
            LLMService.GenerateResult result = new LLMService.GenerateResult(content, "思考");

            assertEquals(content, result.getContent());
        }

        @Test
        @DisplayName("支持长 reasoning 字段")
        void shouldSupportLongReasoning() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) sb.append("思考步骤 ").append(i).append("\n");

            LLMService.GenerateResult result = new LLMService.GenerateResult("内容", sb.toString());
            assertEquals(1000, result.getReasoning().split("\n").length);
        }

        @Test
        @DisplayName("两个独立实例之间互不影响")
        void shouldBeIndependentInstances() {
            LLMService.GenerateResult r1 = new LLMService.GenerateResult("a", "b");
            LLMService.GenerateResult r2 = new LLMService.GenerateResult("c", "d");

            assertEquals("a", r1.getContent());
            assertEquals("c", r2.getContent());
            assertEquals("b", r1.getReasoning());
            assertEquals("d", r2.getReasoning());
        }

        @Test
        @DisplayName("支持 Unicode 内容")
        void shouldSupportUnicodeContent() {
            LLMService.GenerateResult result = new LLMService.GenerateResult(
                    "中文内容：你好世界", "推理：这是中文思考");

            assertEquals("中文内容：你好世界", result.getContent());
            assertEquals("推理：这是中文思考", result.getReasoning());
        }
    }
}
