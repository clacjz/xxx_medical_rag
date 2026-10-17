package com.clacjz.medical.rag.model.vo;

import com.clacjz.medical.rag.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Result Response Format Unit Tests")
class ResultTest {

    @Test
    @DisplayName("Should create success result without data")
    void testSuccessWithoutData() {
        Result<Void> result = Result.success();

        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTimestamp());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should create success result with data")
    void testSuccessWithData() {
        Map<String, String> data = new HashMap<>();
        data.put("key", "value");

        Result<Map<String, String>> result = Result.success(data);

        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals("value", result.getData().get("key"));
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should create success result with custom message and data")
    void testSuccessWithCustomMessage() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);

        Result<Map<String, Object>> result = Result.success("Operation completed", data);

        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Operation completed", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().get("id"));
    }

    @Test
    @DisplayName("Should create error result with ErrorCode")
    void testErrorWithErrorCode() {
        Result<Void> result = Result.error(ErrorCode.USER_NOT_FOUND);

        assertNotNull(result);
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
        assertEquals(ErrorCode.USER_NOT_FOUND.getMessage(), result.getMessage());
        assertNull(result.getData());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Should create error result with ErrorCode and custom message")
    void testErrorWithErrorCodeAndCustomMessage() {
        String customMessage = "Custom error message";
        Result<Void> result = Result.error(ErrorCode.INVALID_PARAMETER, customMessage);

        assertNotNull(result);
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), result.getCode());
        assertEquals(customMessage, result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Should create error result with custom code and message")
    void testErrorWithCustomCodeAndMessage() {
        int customCode = 5000;
        String customMessage = "Custom error";

        Result<Void> result = Result.error(customCode, customMessage);

        assertNotNull(result);
        assertEquals(customCode, result.getCode());
        assertEquals(customMessage, result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Should create result with full parameters")
    void testOfWithAllParameters() {
        Map<String, Integer> data = new HashMap<>();
        data.put("count", 42);

        Result<Map<String, Integer>> result = Result.of(200, "Success", data);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("Success", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(42, result.getData().get("count"));
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should handle null data correctly")
    void testSuccessWithNullData() {
        Result<String> result = Result.success((String) null);

        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNull(result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should generate timestamp in correct format")
    void testTimestampFormat() {
        Result<Void> result = Result.success();

        assertNotNull(result.getTimestamp());
        assertNotNull(result.getTimestamp().getYear());
        assertNotNull(result.getTimestamp().getMonth());
        assertNotNull(result.getTimestamp().getDayOfMonth());
    }

    @Test
    @DisplayName("Should correctly identify success status")
    void testIsSuccess() {
        Result<Void> successResult = Result.success();
        Result<Void> errorResult = Result.error(ErrorCode.SYSTEM_ERROR);

        assertTrue(successResult.isSuccess());
        assertFalse(errorResult.isSuccess());
    }

    @Test
    @DisplayName("Should handle nested data structures")
    void testNestedDataStructures() {
        Map<String, Object> nestedData = new HashMap<>();
        Map<String, String> innerMap = new HashMap<>();
        innerMap.put("innerKey", "innerValue");
        nestedData.put("outerKey", innerMap);

        Result<Map<String, Object>> result = Result.success(nestedData);

        assertNotNull(result);
        assertNotNull(result.getData().get("outerKey"));
        @SuppressWarnings("unchecked")
        Map<String, String> retrievedInnerMap = (Map<String, String>) result.getData().get("outerKey");
        assertEquals("innerValue", retrievedInnerMap.get("innerKey"));
    }

    @Test
    @DisplayName("Should support different data types")
    void testDifferentDataTypes() {
        Result<Integer> intResult = Result.success(42);
        assertEquals(42, intResult.getData());

        Result<String> stringResult = Result.success("test");
        assertEquals("test", stringResult.getData());

        Result<Boolean> boolResult = Result.success(true);
        assertTrue(boolResult.getData());
    }
}
