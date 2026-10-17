package com.clacjz.medical.rag.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorCode Enum Unit Tests")
class ErrorCodeTest {

    @Test
    @DisplayName("Should return correct code")
    void testGetCode() {
        assertEquals(200, ErrorCode.SUCCESS.getCode());
        assertEquals(1000, ErrorCode.SYSTEM_ERROR.getCode());
        assertEquals(2001, ErrorCode.AUTHENTICATION_FAILED.getCode());
        assertEquals(4001, ErrorCode.DOCUMENT_NOT_FOUND.getCode());
        assertEquals(6001, ErrorCode.QUERY_FAILED.getCode());
        assertEquals(7001, ErrorCode.AI_SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("Should return correct message")
    void testGetMessage() {
        assertEquals("操作成功", ErrorCode.SUCCESS.getMessage());
        assertEquals("系统错误", ErrorCode.SYSTEM_ERROR.getMessage());
        assertEquals("用户不存在", ErrorCode.USER_NOT_FOUND.getMessage());
        assertEquals("文档不存在", ErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("Should format message with args")
    void testFormattedMessage() {
        String formatted = ErrorCode.RESOURCE_NOT_FOUND.getFormattedMessage("User", "123");
        assertEquals("资源不存在", formatted);
    }

    @Test
    @DisplayName("Should handle null args")
    void testFormattedMessageWithNullArgs() {
        String formatted = ErrorCode.INVALID_PARAMETER.getFormattedMessage(null);
        assertEquals(ErrorCode.INVALID_PARAMETER.getMessage(), formatted);
    }

    @Test
    @DisplayName("Should handle empty args")
    void testFormattedMessageWithEmptyArgs() {
        String formatted = ErrorCode.INVALID_PARAMETER.getFormattedMessage();
        assertEquals(ErrorCode.INVALID_PARAMETER.getMessage(), formatted);
    }

    @Test
    @DisplayName("Should have all required error codes")
    void testErrorCodeCoverage() {
        assertNotNull(ErrorCode.SUCCESS);
        assertNotNull(ErrorCode.SYSTEM_ERROR);
        assertNotNull(ErrorCode.AUTHENTICATION_FAILED);
        assertNotNull(ErrorCode.INVALID_TOKEN);
        assertNotNull(ErrorCode.USER_NOT_FOUND);
        assertNotNull(ErrorCode.DOCUMENT_NOT_FOUND);
        assertNotNull(ErrorCode.COLLECTION_NOT_FOUND);
        assertNotNull(ErrorCode.QUERY_FAILED);
        assertNotNull(ErrorCode.AI_SERVICE_UNAVAILABLE);
        assertNotNull(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    @DisplayName("Should have unique error codes")
    void testUniqueErrorCodes() {
        ErrorCode[] codes = ErrorCode.values();
        long uniqueCodes = java.util.Arrays.stream(codes)
                .map(ErrorCode::getCode)
                .distinct()
                .count();

        assertEquals(codes.length, uniqueCodes, "All error codes should be unique");
    }

    @Test
    @DisplayName("Should have valid error code ranges")
    void testErrorCodeRanges() {
        for (ErrorCode code : ErrorCode.values()) {
            int codeValue = code.getCode();
            assertTrue(codeValue >= 200 && codeValue < 20000,
                    "Error code " + code.name() + " should be in valid range");
        }
    }

    @Test
    @DisplayName("Should have non-empty messages")
    void testNonEmptyMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getMessage());
            assertFalse(code.getMessage().isEmpty(),
                    "Error code " + code.name() + " should have non-empty message");
        }
    }
}
