package com.clacjz.medical.rag.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult 单元测试")
class SearchResultTest {

    @Nested
    @DisplayName("withScore")
    class WithScoreTests {

        @Test
        @DisplayName("创建副本并替换score值")
        void shouldCreateCopyWithNewScore() {
            SearchResult original = SearchResult.builder()
                    .id("chunk-1")
                    .documentId("doc-1")
                    .collectionId("col-1")
                    .text("some text")
                    .score(0.85)
                    .title("Title")
                    .snippet("snippet")
                    .startPage(1)
                    .endPage(3)
                    .metadata(Map.of("key", "value"))
                    .build();

            SearchResult copy = original.withScore(0.42);

            assertEquals(0.42, copy.getScore(), 1e-9);
        }

        @Test
        @DisplayName("所有其他字段保持不变")
        void shouldPreserveAllOtherFields() {
            Map<String, Object> metadata = Map.of("source", "pdf", "page", 5);
            SearchResult original = SearchResult.builder()
                    .id("chunk-1")
                    .documentId("doc-1")
                    .collectionId("col-1")
                    .text("some text")
                    .score(0.85)
                    .title("Title")
                    .snippet("snippet text")
                    .startPage(1)
                    .endPage(3)
                    .metadata(metadata)
                    .build();

            SearchResult copy = original.withScore(0.99);

            assertEquals("chunk-1", copy.getId());
            assertEquals("doc-1", copy.getDocumentId());
            assertEquals("col-1", copy.getCollectionId());
            assertEquals("some text", copy.getText());
            assertEquals("Title", copy.getTitle());
            assertEquals("snippet text", copy.getSnippet());
            assertEquals(1, copy.getStartPage());
            assertEquals(3, copy.getEndPage());
            assertEquals(metadata, copy.getMetadata());
        }

        @Test
        @DisplayName("原始对象不被修改")
        void shouldNotModifyOriginal() {
            SearchResult original = SearchResult.builder()
                    .id("chunk-1")
                    .documentId("doc-1")
                    .collectionId("col-1")
                    .text("some text")
                    .score(0.85)
                    .title("Title")
                    .snippet("snippet")
                    .startPage(1)
                    .endPage(3)
                    .metadata(Map.of("key", "value"))
                    .build();

            original.withScore(0.10);

            assertEquals(0.85, original.getScore(), 1e-9);
            assertEquals("chunk-1", original.getId());
            assertEquals("doc-1", original.getDocumentId());
            assertEquals("col-1", original.getCollectionId());
            assertEquals("some text", original.getText());
            assertEquals("Title", original.getTitle());
            assertEquals("snippet", original.getSnippet());
            assertEquals(1, original.getStartPage());
            assertEquals(3, original.getEndPage());
        }

        @Test
        @DisplayName("null字段在副本中保持为null")
        void shouldPreserveNullFields() {
            SearchResult original = SearchResult.builder()
                    .id(null)
                    .documentId(null)
                    .collectionId(null)
                    .text(null)
                    .score(null)
                    .title(null)
                    .snippet(null)
                    .startPage(null)
                    .endPage(null)
                    .metadata(null)
                    .build();

            SearchResult copy = original.withScore(0.5);

            assertEquals(0.5, copy.getScore(), 1e-9);
            assertNull(copy.getId());
            assertNull(copy.getDocumentId());
            assertNull(copy.getCollectionId());
            assertNull(copy.getText());
            assertNull(copy.getTitle());
            assertNull(copy.getSnippet());
            assertNull(copy.getStartPage());
            assertNull(copy.getEndPage());
            assertNull(copy.getMetadata());
        }
    }
}
