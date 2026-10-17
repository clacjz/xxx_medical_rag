package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.DocumentChunkMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.DocumentChunk;
import com.clacjz.medical.rag.service.chunking.TextChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentChunkService 单元测试")
class DocumentChunkServiceTest {

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private DocumentChunkService documentChunkService;

    private static final String TEST_DOC_ID = "doc-123";
    private static final String TEST_COLLECTION_ID = "collection-123";

    @Nested
    @DisplayName("getChunksByDocumentIds - 批量获取文档分块")
    class GetChunksByDocumentIdsTests {

        @Test
        @DisplayName("返回空map当无文档id")
        void shouldReturnEmptyMapWhenNoDocumentIds() {
            Map<String, List<DocumentChunk>> result =
                    documentChunkService.getChunksByDocumentIds(Collections.emptyList());

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(documentChunkMapper, never()).selectByDocumentIds(any());
        }

        @Test
        @DisplayName("返回空map当documentIds为null")
        void shouldReturnEmptyMapWhenNull() {
            Map<String, List<DocumentChunk>> result =
                    documentChunkService.getChunksByDocumentIds(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("返回文档分块映射")
        void shouldReturnChunksMap() {
            DocumentChunk chunk1 = new DocumentChunk();
            chunk1.setId("chunk-1");
            chunk1.setDocumentId(TEST_DOC_ID);
            chunk1.setContent("测试内容1");

            DocumentChunk chunk2 = new DocumentChunk();
            chunk2.setId("chunk-2");
            chunk2.setDocumentId(TEST_DOC_ID);
            chunk2.setContent("测试内容2");

            when(documentChunkMapper.selectByDocumentIds(anyList()))
                    .thenReturn(Arrays.asList(chunk1, chunk2));

            Map<String, List<DocumentChunk>> result =
                    documentChunkService.getChunksByDocumentIds(List.of(TEST_DOC_ID));

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey(TEST_DOC_ID));
            assertEquals(2, result.get(TEST_DOC_ID).size());
        }
    }

    @Nested
    @DisplayName("getChunksByDocumentId - 获取单个文档分块")
    class GetChunksByDocumentIdTests {

        @Test
        @DisplayName("返回文档的分块列表")
        void shouldReturnChunksList() {
            DocumentChunk chunk1 = new DocumentChunk();
            chunk1.setId("chunk-1");
            chunk1.setDocumentId(TEST_DOC_ID);

            DocumentChunk chunk2 = new DocumentChunk();
            chunk2.setId("chunk-2");
            chunk2.setDocumentId(TEST_DOC_ID);

            when(documentChunkMapper.selectByDocumentId(TEST_DOC_ID))
                    .thenReturn(Arrays.asList(chunk1, chunk2));

            List<DocumentChunk> result = documentChunkService.getChunksByDocumentId(TEST_DOC_ID);

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("返回空列表当无分块")
        void shouldReturnEmptyListWhenNoChunks() {
            when(documentChunkMapper.selectByDocumentId(TEST_DOC_ID))
                    .thenReturn(Collections.emptyList());

            List<DocumentChunk> result = documentChunkService.getChunksByDocumentId(TEST_DOC_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("deleteChunksByDocumentId - 删除文档分块 (Bug#1 回归测试)")
    class DeleteChunksByDocumentIdTests {

        @Test
        @DisplayName("应调用 mapper.deleteByDocumentId 一次")
        void shouldInvokeMapperDeleteByDocumentId() {
            when(documentChunkMapper.deleteByDocumentId(TEST_DOC_ID)).thenReturn(3);

            documentChunkService.deleteChunksByDocumentId(TEST_DOC_ID);

            // 验证 @Delete 注解修复后仍能正确调用 mapper
            verify(documentChunkMapper, times(1)).deleteByDocumentId(TEST_DOC_ID);
        }

        @Test
        @DisplayName("mapper 返回 0 也不应抛 NPE")
        void shouldNotThrowWhenMapperReturnsZero() {
            // 修复前: @Select 注解下 DELETE 返回 null → 装箱 int 抛 IllegalArgumentException
            // 修复后: @Delete 注解正确返回受影响行数 (int 0/1/N 都安全)
            when(documentChunkMapper.deleteByDocumentId(TEST_DOC_ID)).thenReturn(0);

            assertDoesNotThrow(() ->
                    documentChunkService.deleteChunksByDocumentId(TEST_DOC_ID));
        }
    }

    @Nested
    @DisplayName("saveChunks - 批量插入分块")
    class BatchInsertTests {

        private static final int BATCH_SIZE = 200;

        private List<TextChunker.Chunk> buildChunks(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> new TextChunker.Chunk(i, "content-" + i, i * 100, (i + 1) * 100, 1, 1))
                    .collect(Collectors.toList());
        }

        private List<String> buildPointIds(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> "point-" + i)
                    .collect(Collectors.toList());
        }

        @Test
        @DisplayName("单个分块 — batchInsertWithJsonb 调用一次，批次大小为1")
        void shouldCallBatchInsertOnceForSingleChunk() {
            List<TextChunker.Chunk> chunks = buildChunks(1);
            List<String> pointIds = buildPointIds(1);

            documentChunkService.saveChunks(TEST_DOC_ID, chunks, pointIds);

            verify(documentChunkMapper, times(1)).deleteByDocumentId(TEST_DOC_ID);
            verify(documentChunkMapper, times(1)).batchInsertWithJsonb(argThat(list -> list.size() == 1));
        }

        @Test
        @DisplayName("恰好200个分块 — batchInsertWithJsonb 调用一次，批次大小为200")
        void shouldCallBatchInsertOnceForExactBatchSize() {
            List<TextChunker.Chunk> chunks = buildChunks(BATCH_SIZE);
            List<String> pointIds = buildPointIds(BATCH_SIZE);

            documentChunkService.saveChunks(TEST_DOC_ID, chunks, pointIds);

            verify(documentChunkMapper, times(1)).deleteByDocumentId(TEST_DOC_ID);
            verify(documentChunkMapper, times(1)).batchInsertWithJsonb(argThat(list -> list.size() == BATCH_SIZE));
        }

        @Test
        @DisplayName("201个分块 — batchInsertWithJsonb 调用两次 (200 + 1)")
        void shouldCallBatchInsertTwiceFor201Chunks() {
            List<TextChunker.Chunk> chunks = buildChunks(201);
            List<String> pointIds = buildPointIds(201);

            documentChunkService.saveChunks(TEST_DOC_ID, chunks, pointIds);

            verify(documentChunkMapper, times(1)).deleteByDocumentId(TEST_DOC_ID);
            verify(documentChunkMapper, times(2)).batchInsertWithJsonb(anyList());
            // 验证第一次调用批次大小为200，第二次为1
            verify(documentChunkMapper).batchInsertWithJsonb(argThat(list -> list.size() == BATCH_SIZE));
            verify(documentChunkMapper).batchInsertWithJsonb(argThat(list -> list.size() == 1));
        }

        @Test
        @DisplayName("400个分块 — batchInsertWithJsonb 调用两次 (200 + 200)")
        void shouldCallBatchInsertTwiceFor400Chunks() {
            List<TextChunker.Chunk> chunks = buildChunks(400);
            List<String> pointIds = buildPointIds(400);

            documentChunkService.saveChunks(TEST_DOC_ID, chunks, pointIds);

            verify(documentChunkMapper, times(1)).deleteByDocumentId(TEST_DOC_ID);
            verify(documentChunkMapper, times(2)).batchInsertWithJsonb(anyList());
            // 两次调用批次大小均为200
            verify(documentChunkMapper, times(2)).batchInsertWithJsonb(argThat(list -> list.size() == BATCH_SIZE));
        }

        @Test
        @DisplayName("空分块列表 — 不调用 batchInsertWithJsonb")
        void shouldNotCallBatchInsertForEmptyChunks() {
            documentChunkService.saveChunks(TEST_DOC_ID, Collections.emptyList(), Collections.emptyList());

            verify(documentChunkMapper, never()).deleteByDocumentId(any());
            verify(documentChunkMapper, never()).batchInsertWithJsonb(anyList());
        }

        @Test
        @DisplayName("null pointIds — chunk.pointId 为 null")
        void shouldHandleNullPointIds() {
            List<TextChunker.Chunk> chunks = buildChunks(3);

            documentChunkService.saveChunks(TEST_DOC_ID, chunks, null);

            verify(documentChunkMapper, times(1)).batchInsertWithJsonb(argThat(list -> {
                // 所有 chunk 的 pointId 应为 null
                return list.stream().allMatch(chunk -> chunk.getPointId() == null);
            }));
        }

        @Test
        @DisplayName("countByDocumentId 返回正确数量")
        void shouldReturnCorrectChunkCount() {
            when(documentChunkMapper.selectCount(any())).thenReturn(42L);

            long count = documentChunkService.countByDocumentId(TEST_DOC_ID);

            assertEquals(42L, count);
            verify(documentChunkMapper, times(1)).selectCount(any());
        }
    }

    @Nested
    @DisplayName("countByDocumentId - 统计文档分块数量")
    class CountByDocumentIdTests {

        @Test
        @DisplayName("返回正确的分块数量")
        void returnsCorrectCount() {
            when(documentChunkMapper.selectCount(any())).thenReturn(5L);

            long count = documentChunkService.countByDocumentId(TEST_DOC_ID);

            assertEquals(5L, count);
            verify(documentChunkMapper, times(1)).selectCount(any());
        }

        @Test
        @DisplayName("文档不存在时返回0")
        void returnsZeroForNonExistent() {
            when(documentChunkMapper.selectCount(any())).thenReturn(0L);

            long count = documentChunkService.countByDocumentId("nonexistent-doc");

            assertEquals(0L, count);
            verify(documentChunkMapper, times(1)).selectCount(any());
        }
    }
}
