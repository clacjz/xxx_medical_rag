package com.clacjz.medical.rag.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON serialization utility using Spring-configured ObjectMapper.
 * Provides consistent JSON handling across the application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonUtil {

    private final ObjectMapper objectMapper;

    /**
     * Serialize an object to JSON string.
     */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }

    /**
     * Deserialize JSON string to object of specified type.
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), json, e);
            return null;
        }
    }

    /**
     * Deserialize JSON string to List of specified type.
     */
    public <T> List<T> fromJsonList(String json, Class<T> elementClass) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to List<{}>: {}", elementClass.getSimpleName(), json, e);
            return Collections.emptyList();
        }
    }

    /**
     * Deserialize JSON string to Map.
     */
    public Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to Map: {}", json, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Parse JSON string, returning empty map on failure.
     * Useful for logging/debugging where you want to continue even if parse fails.
     */
    public Map<String, Object> parseJsonMap(String json) {
        Map<String, Object> result = fromJsonMap(json);
        return result != null ? result : Collections.emptyMap();
    }

    /**
     * Escape a string for safe inclusion in a JSON string literal.
     * Handles backslash, quotes, newlines, tabs, and Unicode-sensitive characters.
     */
    public String escapeForJsonString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }
}