package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchRequest;
import com.clacjz.medical.rag.model.dto.qdrant.VectorSearchResult;
import io.qdrant.client.grpc.Common;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VectorCrudServiceTest {

    private static final String TEST_COLLECTION = "test_collection";

    private float[] createTestVector(float value) {
        float[] vector = new float[1024];
        Arrays.fill(vector, value);
        return vector;
    }

    @Test
    @DisplayName("测试VectorSearchRequest - 构建请求")
    void testVectorSearchRequest_BuildRequest() {
        float[] vector = createTestVector(0.5f);

        VectorSearchRequest request = VectorSearchRequest.builder()
                .vector(vector)
                .limit(10)
                .scoreThreshold(0.8)
                .ef(128)
                .build();

        assertNotNull(request);
        assertEquals(vector, request.getVector());
        assertEquals(10, request.getLimit());
        assertEquals(0.8, request.getScoreThreshold());
        assertEquals(128, request.getEf());
    }

    @Test
    @DisplayName("测试VectorSearchRequest - 默认值")
    void testVectorSearchRequest_DefaultValues() {
        float[] vector = createTestVector(0.5f);

        VectorSearchRequest request = VectorSearchRequest.builder()
                .vector(vector)
                .build();

        assertNotNull(request);
        assertEquals(10, request.getLimit());
        assertEquals(128, request.getEf());
        assertNull(request.getScoreThreshold());
    }

    @Test
    @DisplayName("测试VectorSearchRequest - 边界值")
    void testVectorSearchRequest_BoundaryValues() {
        float[] vector = createTestVector(0.5f);

        VectorSearchRequest request = VectorSearchRequest.builder()
                .vector(vector)
                .limit(1)
                .ef(64)
                .build();

        assertNotNull(request);
        assertEquals(1, request.getLimit());
        assertEquals(64, request.getEf());
    }

    @Test
    @DisplayName("测试VectorSearchResult - 构建结果")
    void testVectorSearchResult_BuildResult() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", "doc123");
        payload.put("chunkIndex", 0);

        VectorSearchResult result = VectorSearchResult.builder()
                .id("point-id-123")
                .score(0.95)
                .payload(payload)
                .build();

        assertNotNull(result);
        assertEquals("point-id-123", result.getId());
        assertEquals(0.95, result.getScore());
        assertEquals(payload, result.getPayload());
    }

    @Test
    @DisplayName("测试VectorSearchResult - 无Payload")
    void testVectorSearchResult_NoPayload() {
        VectorSearchResult result = VectorSearchResult.builder()
                .id("point-id-123")
                .score(0.95)
                .build();

        assertNotNull(result);
        assertEquals("point-id-123", result.getId());
        assertEquals(0.95, result.getScore());
        assertNull(result.getPayload());
    }

    @Test
    @DisplayName("测试VectorSearchResult - 分数边界")
    void testVectorSearchResult_ScoreBoundaries() {
        VectorSearchResult minScore = VectorSearchResult.builder()
                .id("id1")
                .score(0.0)
                .build();
        assertEquals(0.0, minScore.getScore());

        VectorSearchResult maxScore = VectorSearchResult.builder()
                .id("id2")
                .score(1.0)
                .build();
        assertEquals(1.0, maxScore.getScore());
    }

    @Test
    @DisplayName("测试向量维度一致性")
    void testVectorDimension() {
        int dimension = 1024;

        float[] vector1 = createTestVector(0.5f);
        assertEquals(dimension, vector1.length);

        float[] vector2 = createTestVector(0.1f);
        assertEquals(dimension, vector2.length);

        float[] vector3 = new float[dimension];
        assertEquals(dimension, vector3.length);
    }

    @Test
    @DisplayName("测试Payload构建")
    void testPayloadBuild() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", "doc123");
        payload.put("chunkIndex", 0);
        payload.put("text", "Sample text content");
        payload.put("pageNumber", 1);
        payload.put("isTitle", false);

        assertNotNull(payload);
        assertEquals("doc123", payload.get("documentId"));
        assertEquals(0, payload.get("chunkIndex"));
        assertEquals("Sample text content", payload.get("text"));
        assertEquals(1, payload.get("pageNumber"));
        assertEquals(false, payload.get("isTitle"));
    }

    @Test
    @DisplayName("测试UUID格式")
    void testUuidFormat() {
        String uuid = java.util.UUID.randomUUID().toString();
        assertNotNull(uuid);
        assertEquals(36, uuid.length());
        assertTrue(uuid.contains("-"));
    }

    // ---- Helper: invoke private buildSearchFilter via reflection ----

    private Common.Filter invokeBuildSearchFilter(Map<String, Object> filter) throws Exception {
        VectorCrudService service = new VectorCrudService(null, null, null);
        Method method = VectorCrudService.class.getDeclaredMethod("buildSearchFilter", Map.class);
        method.setAccessible(true);
        return (Common.Filter) method.invoke(service, filter);
    }

    @Nested
    @DisplayName("buildSearchFilter AND/OR 语义测试")
    class FilterSemanticsTests {

        @Test
        @DisplayName("单String值使用must(AND)逻辑")
        void singleStringValues_useMustAndLogic() throws Exception {
            Map<String, Object> filter = new HashMap<>();
            filter.put("key1", "val1");
            filter.put("key2", "val2");

            Common.Filter result = invokeBuildSearchFilter(filter);

            // Both entries should appear as must conditions (AND)
            assertEquals(2, result.getMustCount());
            assertEquals(0, result.getShouldCount());

            // Verify each must condition is a keyword match on the correct key
            List<String> actualKeys = result.getMustList().stream()
                    .map(c -> c.getField().getKey())
                    .toList();
            assertTrue(actualKeys.contains("key1"), "Should contain key1 condition");
            assertTrue(actualKeys.contains("key2"), "Should contain key2 condition");

            // Verify keyword values
            for (Common.Condition cond : result.getMustList()) {
                if (cond.getField().getKey().equals("key1")) {
                    assertEquals("val1", cond.getField().getMatch().getKeyword());
                } else if (cond.getField().getKey().equals("key2")) {
                    assertEquals("val2", cond.getField().getMatch().getKeyword());
                }
            }
        }

        @Test
        @DisplayName("List值使用should(OR)逻辑，外层包裹must")
        void listValues_useShouldOrLogic() throws Exception {
            Map<String, Object> filter = Map.of("drugs", List.of("阿司匹林", "布洛芬"));

            Common.Filter result = invokeBuildSearchFilter(filter);

            // Top-level: one must condition (the OR wrapper)
            assertEquals(1, result.getMustCount());
            assertEquals(0, result.getShouldCount());

            // The must condition contains a nested filter with should conditions
            Common.Condition mustCond = result.getMust(0);
            assertTrue(mustCond.hasFilter(), "List values should be wrapped in a nested filter");

            Common.Filter nestedOr = mustCond.getFilter();
            assertEquals(2, nestedOr.getShouldCount(), "Should have 2 OR conditions for 2 list items");

            // Verify each should condition targets the same key with correct keyword
            for (Common.Condition shouldCond : nestedOr.getShouldList()) {
                assertEquals("drugs", shouldCond.getField().getKey());
            }
            List<String> keywords = nestedOr.getShouldList().stream()
                    .map(c -> c.getField().getMatch().getKeyword())
                    .toList();
            assertTrue(keywords.contains("阿司匹林"), "Should contain 阿司匹林");
            assertTrue(keywords.contains("布洛芬"), "Should contain 布洛芬");
        }

        @Test
        @DisplayName("混合String和List值：String用must，List用should+must包裹")
        void mixedSingleAndListValues() throws Exception {
            Map<String, Object> filter = new HashMap<>();
            filter.put("type", "MANUAL");
            filter.put("drugs", List.of("a", "b"));

            Common.Filter result = invokeBuildSearchFilter(filter);

            // Two must conditions: one for "type" keyword, one for "drugs" OR-wrapper
            assertEquals(2, result.getMustCount());
            assertEquals(0, result.getShouldCount());

            // Find the keyword must condition and the nested-filter must condition
            Common.Condition typeCond = null;
            Common.Condition drugsOrWrapper = null;
            for (Common.Condition cond : result.getMustList()) {
                if (cond.hasField() && cond.getField().getKey().equals("type")) {
                    typeCond = cond;
                } else if (cond.hasFilter()) {
                    drugsOrWrapper = cond;
                }
            }

            assertNotNull(typeCond, "Should have a must condition for 'type'");
            assertNotNull(drugsOrWrapper, "Should have a must-wrapped OR condition for 'drugs'");

            // Verify type keyword match
            assertEquals("MANUAL", typeCond.getField().getMatch().getKeyword());

            // Verify drugs OR conditions
            Common.Filter drugsOr = drugsOrWrapper.getFilter();
            assertEquals(2, drugsOr.getShouldCount());
            List<String> drugKeywords = drugsOr.getShouldList().stream()
                    .map(c -> c.getField().getMatch().getKeyword())
                    .toList();
            assertTrue(drugKeywords.contains("a"));
            assertTrue(drugKeywords.contains("b"));
        }

        @Test
        @DisplayName("空List不产生任何条件")
        void emptyList_noConditionAdded() throws Exception {
            Map<String, Object> filter = new HashMap<>();
            filter.put("key", List.of());

            Common.Filter result = invokeBuildSearchFilter(filter);

            // Empty list produces no should items, so no must wrapper is added
            assertEquals(0, result.getMustCount(), "Empty list should not add any must condition");
            assertEquals(0, result.getShouldCount(), "Empty list should not add any should condition");
        }

        @Test
        @DisplayName("Number值使用must range条件(GTE=LTE)")
        void numberValue_usesMustRange() throws Exception {
            Map<String, Object> filter = Map.of("count", 5);

            Common.Filter result = invokeBuildSearchFilter(filter);

            assertEquals(1, result.getMustCount());
            assertEquals(0, result.getShouldCount());

            Common.Condition cond = result.getMust(0);
            assertTrue(cond.hasField(), "Number should produce a field condition");
            assertEquals("count", cond.getField().getKey());
            assertTrue(cond.getField().hasRange(), "Number should produce a range condition");

            Common.Range range = cond.getField().getRange();
            assertEquals(5.0, range.getGte(), 0.0001, "Range GTE should equal the number value");
            assertEquals(5.0, range.getLte(), 0.0001, "Range LTE should equal the number value");
        }
    }
}
