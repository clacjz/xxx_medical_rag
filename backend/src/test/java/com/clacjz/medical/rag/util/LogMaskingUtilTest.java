package com.clacjz.medical.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogMaskingUtil Unit Tests")
class LogMaskingUtilTest {

    @Test
    @DisplayName("Should mask sensitive info in log message")
    void testMaskSensitiveInfo() {
        String logMessage = "User password=secretPassword123";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertNotNull(masked);
        assertFalse(masked.contains("secretPassword123"));
    }

    @Test
    @DisplayName("Should mask email in log message")
    void testMaskEmail() {
        String logMessage = "User email=user@example.com logged in";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertNotNull(masked);
        assertFalse(masked.contains("user@example.com"));
        assertTrue(masked.contains("***@***.***"));
    }

    @Test
    @DisplayName("Should mask phone number in log message")
    void testMaskPhone() {
        String logMessage = "Contact phone: 13812345678";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertNotNull(masked);
        assertFalse(masked.contains("13812345678"));
        assertTrue(masked.contains("138****5678"));
    }

    @Test
    @DisplayName("Should mask ID card number in log message")
    void testMaskIdCard() {
        String logMessage = "ID card: 110101199001011234";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertNotNull(masked);
        assertFalse(masked.contains("110101199001011234"));
    }

    @Test
    @DisplayName("Should handle null input")
    void testMaskNullInput() {
        String masked = LogMaskingUtil.maskSensitiveInfo(null);
        assertNull(masked);
    }

    @Test
    @DisplayName("Should handle empty input")
    void testMaskEmptyInput() {
        String masked = LogMaskingUtil.maskSensitiveInfo("");
        assertEquals("", masked);
    }

    @Test
    @DisplayName("Should handle message without sensitive information")
    void testMaskNoSensitiveInfo() {
        String logMessage = "This is a normal log message without any sensitive data";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertEquals(logMessage, masked);
    }

    @Test
    @DisplayName("Should mask multiple sensitive information types")
    void testMaskMultipleTypes() {
        String logMessage = "email=user@test.com phone=13900001111";
        String masked = LogMaskingUtil.maskSensitiveInfo(logMessage);

        assertNotNull(masked);
        assertFalse(masked.contains("user@test.com"));
        assertFalse(masked.contains("13900001111"));
    }

    @Test
    @DisplayName("Should truncate long prompts")
    void testTruncateLongPrompt() {
        StringBuilder longPrompt = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longPrompt.append("x");
        }

        String masked = LogMaskingUtil.maskPrompt(longPrompt.toString());

        assertNotNull(masked);
        assertTrue(masked.length() < longPrompt.length());
        assertTrue(masked.contains("... [TRUNCATED for security]"));
    }

    @Test
    @DisplayName("Should handle short prompts without truncation")
    void testShortPrompts() {
        String shortPrompt = "This is a short prompt";

        String masked = LogMaskingUtil.maskPrompt(shortPrompt);

        assertEquals(shortPrompt, masked);
    }

    @Test
    @DisplayName("Should handle null prompt")
    void testMaskNullPrompt() {
        String masked = LogMaskingUtil.maskPrompt(null);
        assertNull(masked);
    }
}
