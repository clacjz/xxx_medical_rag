package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.OcrResultDTO;
import com.clacjz.medical.rag.model.enums.OcrStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OcrService 单元测试 - 简洁版")
class OcrServiceTest {

    @Nested
    @DisplayName("OcrResultDTO - OCR结果结构")
    class OcrResultDtoTests {

        @Test
        @DisplayName("成功状态的结果包含文本内容")
        void shouldHaveTextOnSuccess() {
            OcrResultDTO result = OcrResultDTO.builder()
                    .documentId("doc-1")
                    .currentPage(1)
                    .totalPages(5)
                    .ocrText("提取的文本内容")
                    .status(OcrStatus.SUCCESS)
                    .build();
            assertEquals(OcrStatus.SUCCESS, result.getStatus());
            assertEquals("提取的文本内容", result.getOcrText());
            assertEquals(5, result.getTotalPages());
        }

        @Test
        @DisplayName("失败状态的结果包含错误信息")
        void shouldHaveErrorMessageOnFailure() {
            OcrResultDTO result = OcrResultDTO.builder()
                    .documentId("doc-1")
                    .currentPage(1)
                    .status(OcrStatus.FAILED)
                    .errorMessage("OCR识别失败")
                    .build();
            assertEquals(OcrStatus.FAILED, result.getStatus());
            assertEquals("OCR识别失败", result.getErrorMessage());
        }

        @Test
        @DisplayName("EXCEEDED状态表示超出页数限制")
        void shouldHaveExceededStatus() {
            OcrResultDTO result = OcrResultDTO.builder()
                    .documentId("doc-1")
                    .status(OcrStatus.EXCEEDED)
                    .errorMessage("超出最大页数限制，请分页预览")
                    .build();
            assertEquals(OcrStatus.EXCEEDED, result.getStatus());
        }

        @Test
        @DisplayName("可设置HTML表格内容")
        void shouldHaveHtmlTable() {
            OcrResultDTO result = OcrResultDTO.builder()
                    .documentId("doc-1")
                    .status(OcrStatus.SUCCESS)
                    .htmlTable("<table><tr><td>内容</td></tr></table>")
                    .build();
            assertEquals("<table><tr><td>内容</td></tr></table>", result.getHtmlTable());
        }
    }

    @Nested
    @DisplayName("OcrFullResult - 全文档OCR结果结构")
    class OcrFullResultTests {

        @Test
        @DisplayName("包含文档ID、文本和页数信息")
        void shouldHaveAllFields() {
            OcrService.OcrFullResult result = new OcrService.OcrFullResult(
                    "doc-1",
                    "完整文档文本",
                    10,
                    10,
                    null
            );
            assertEquals("doc-1", result.documentId());
            assertEquals("完整文档文本", result.fullText());
            assertEquals(10, result.successfulPages());
            assertEquals(10, result.totalPages());
            assertNull(result.errorMessage());
        }

        @Test
        @DisplayName("有错误时errorMessage不为空")
        void shouldHaveErrorMessageWhenFailed() {
            OcrService.OcrFullResult result = new OcrService.OcrFullResult(
                    "doc-1",
                    null,
                    0,
                    0,
                    "OCR识别失败"
            );
            assertEquals("OCR识别失败", result.errorMessage());
            assertNull(result.fullText());
        }
    }

    @Nested
    @DisplayName("OcrExtractResult - 结构化提取结果")
    class OcrExtractResultTests {

        @Test
        @DisplayName("包含结构化数据")
        void shouldHaveStructuredData() {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("name", "药品名");
            data.put("dosage", "10mg");

            OcrService.OcrExtractResult result = new OcrService.OcrExtractResult(
                    "doc-1",
                    "完整文本",
                    data,
                    5,
                    5,
                    null
            );
            assertEquals("doc-1", result.documentId());
            assertEquals("完整文本", result.fullText());
            assertEquals(data, result.structuredData());
            assertEquals(5, result.successfulPages());
        }

        @Test
        @DisplayName("失败时structuredData为null")
        void shouldHaveNullDataOnFailure() {
            OcrService.OcrExtractResult result = new OcrService.OcrExtractResult(
                    "doc-1",
                    null,
                    null,
                    0,
                    0,
                    "OCR服务不可用"
            );
            assertNull(result.structuredData());
            assertEquals("OCR服务不可用", result.errorMessage());
        }
    }

    @Nested
    @DisplayName("OcrStatus - OCR状态枚举")
    class OcrStatusTests {

        @Test
        @DisplayName("三种OCR状态都存在")
        void shouldHaveAllStatuses() {
            assertNotNull(OcrStatus.SUCCESS);
            assertNotNull(OcrStatus.FAILED);
            assertNotNull(OcrStatus.EXCEEDED);
        }

        @Test
        @DisplayName("状态枚举值正确")
        void shouldHaveCorrectValues() {
            assertEquals("SUCCESS", OcrStatus.SUCCESS.name());
            assertEquals("FAILED", OcrStatus.FAILED.name());
            assertEquals("EXCEEDED", OcrStatus.EXCEEDED.name());
        }
    }
}