package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.qdrant.*;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.BatchVectorService;
import com.clacjz.medical.rag.service.CollectionManagementService;
import com.clacjz.medical.rag.service.ResumableBatchService;
import com.clacjz.medical.rag.service.VectorCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QdrantController 单元测试")
class QdrantControllerTest {

    @Mock
    private CollectionManagementService collectionManagementService;

    @Mock
    private VectorCrudService vectorCrudService;

    @Mock
    private BatchVectorService batchVectorService;

    @Mock
    private ResumableBatchService resumableBatchService;

    @InjectMocks
    private QdrantController qdrantController;

    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_SYSTEM_ADMIN")
                .build();
    }

    @Nested
    @DisplayName("Collection管理测试")
    class CollectionManagementTests {

        @Test
        @DisplayName("列举所有Collection成功")
        void listCollections_Success() {
            List<String> collections = List.of("collection1", "collection2");
            when(collectionManagementService.listCollections()).thenReturn(collections);

            ResponseEntity<Result<List<String>>> response = qdrantController.listCollections(mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().getData().size());
        }

        @Test
        @DisplayName("列举Collection返回空列表")
        void listCollections_Empty() {
            when(collectionManagementService.listCollections()).thenReturn(List.of());

            ResponseEntity<Result<List<String>>> response = qdrantController.listCollections(mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().getData().isEmpty());
        }

        @Test
        @DisplayName("创建Collection成功")
        void createCollection_Success() {
            QdrantController.CreateCollectionInternalRequest request =
                    new QdrantController.CreateCollectionInternalRequest();
            request.setCollectionName("test-collection");
            request.setVectorSize(1024);
            request.setDistance("Cosine");

            when(collectionManagementService.parseDistance("Cosine"))
                    .thenReturn(io.qdrant.client.grpc.Collections.Distance.Cosine);
            doNothing().when(collectionManagementService)
                    .createCollection(eq("test-collection"), eq(1024),
                            eq(io.qdrant.client.grpc.Collections.Distance.Cosine));

            ResponseEntity<Result<Void>> response =
                    qdrantController.createCollection(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Collection创建成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("获取Collection信息成功")
        void getCollectionInfo_Success() {
            CollectionStats stats = new CollectionStats();
            stats.setName("test-collection");
            stats.setVectorsCount(100L);
            when(collectionManagementService.getCollectionStats("test-collection")).thenReturn(stats);

            ResponseEntity<Result<CollectionStats>> response =
                    qdrantController.getCollectionInfo("test-collection", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("test-collection", response.getBody().getData().getName());
        }

        @Test
        @DisplayName("删除Collection成功")
        void deleteCollection_Success() {
            doNothing().when(collectionManagementService).deleteCollection("test-collection");

            ResponseEntity<Result<Void>> response =
                    qdrantController.deleteCollection("test-collection", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Collection删除成功", response.getBody().getMessage());
        }
    }

    @Nested
    @DisplayName("向量CRUD操作测试")
    class VectorCrudTests {

        @Test
        @DisplayName("插入向量成功")
        void insertVector_Success() {
            QdrantController.VectorInsertRequest request =
                    new QdrantController.VectorInsertRequest();
            request.setVector(new float[]{0.1f, 0.2f, 0.3f});
            request.setPayload(Map.of("content", "test content"));

            when(vectorCrudService.insertVector(eq("test-collection"), any(float[].class), any()))
                    .thenReturn("point-id-123");

            ResponseEntity<Result<QdrantController.VectorInsertResponse>> response =
                    qdrantController.insertVector("test-collection", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("point-id-123", response.getBody().getData().getPointId());
        }

        @Test
        @DisplayName("获取向量成功")
        void getVector_Success() {
            Map<String, Object> vectorData = Map.of(
                    "id", "point-id-123",
                    "vector", new float[]{0.1f, 0.2f},
                    "payload", Map.of("content", "test")
            );
            when(vectorCrudService.getVector("test-collection", "point-id-123"))
                    .thenReturn(vectorData);

            ResponseEntity<Result<Map<String, Object>>> response =
                    qdrantController.getVector("test-collection", "point-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("point-id-123", response.getBody().getData().get("id"));
        }

        @Test
        @DisplayName("更新向量Payload成功")
        void updateVectorPayload_Success() {
            QdrantController.UpdatePayloadRequest request =
                    new QdrantController.UpdatePayloadRequest();
            request.setPayload(Map.of("updated", "value"));

            doNothing().when(vectorCrudService)
                    .updateVectorPayload(eq("test-collection"), eq("point-id-123"), any());

            ResponseEntity<Result<Void>> response =
                    qdrantController.updateVectorPayload("test-collection", "point-id-123", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Payload更新成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("删除向量成功")
        void deleteVector_Success() {
            doNothing().when(vectorCrudService).deleteVector("test-collection", "point-id-123");

            ResponseEntity<Result<Void>> response =
                    qdrantController.deleteVector("test-collection", "point-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("向量删除成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("向量检索成功")
        void searchVectors_Success() {
            VectorSearchRequest request = new VectorSearchRequest();
            request.setVector(new float[]{0.1f, 0.2f});
            request.setLimit(10);

            List<VectorSearchResult> results = List.of(
                    VectorSearchResult.builder().id("id1").score(0.95).build(),
                    VectorSearchResult.builder().id("id2").score(0.90).build()
            );
            when(vectorCrudService.searchVectors(eq("test-collection"), any(VectorSearchRequest.class)))
                    .thenReturn(results);

            ResponseEntity<Result<List<VectorSearchResult>>> response =
                    qdrantController.searchVectors("test-collection", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(2, response.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {

        @Test
        @DisplayName("批量插入向量成功")
        void batchInsertVectors_Success() {
            BatchInsertRequest request = new BatchInsertRequest();
            request.setCollectionName("test-collection");
            VectorBatchItem item = new VectorBatchItem();
            item.setVector(new float[]{0.1f});
            item.setPayload(Map.of("content", "test"));
            request.setItems(List.of(item));

            BatchInsertResult result = new BatchInsertResult();
            result.setSuccessCount(1);
            result.setFailedCount(0);
            when(batchVectorService.batchInsert(any(BatchInsertRequest.class))).thenReturn(result);

            ResponseEntity<Result<BatchInsertResult>> response =
                    qdrantController.batchInsertVectors("test-collection", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("批量插入完成", response.getBody().getMessage());
        }

        @Test
        @DisplayName("批量删除向量成功")
        void batchDeleteVectors_Success() {
            QdrantController.BatchDeleteRequest request =
                    new QdrantController.BatchDeleteRequest();
            request.setPointIds(List.of("id1", "id2", "id3"));

            doNothing().when(batchVectorService).batchDelete(eq("test-collection"), anyList());

            ResponseEntity<Result<Void>> response =
                    qdrantController.batchDeleteVectors("test-collection", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("批量删除完成", response.getBody().getMessage());
        }

        @Test
        @DisplayName("启动可恢复批量操作成功")
        void startResumableBatch_Success() {
            ResumableBatchRequest request = new ResumableBatchRequest();
            request.setCollectionName("test-collection");
            VectorBatchItem item = new VectorBatchItem();
            item.setVector(new float[]{0.1f});
            item.setPayload(Map.of("content", "test"));
            request.setItems(List.of(item));

            when(resumableBatchService.startBatch(any(ResumableBatchRequest.class), isNull()))
                    .thenReturn("batch-id-123");

            ResponseEntity<Result<QdrantController.ResumableBatchResponse>> response =
                    qdrantController.startResumableBatch("test-collection", request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("batch-id-123", response.getBody().getData().getBatchId());
            assertEquals("RUNNING", response.getBody().getData().getStatus());
        }
    }

    @Nested
    @DisplayName("批量操作状态管理测试")
    class BatchStatusTests {

        @Test
        @DisplayName("获取批量操作状态成功")
        void getBatchStatus_Success() {
            ResumeState state = new ResumeState();
            state.setBatchId("batch-id-123");
            state.setStatus(ResumeState.Status.RUNNING);
            state.setProcessedItems(50);
            state.setTotalItems(100);
            when(resumableBatchService.getBatchState("batch-id-123")).thenReturn(state);

            ResponseEntity<Result<ResumeState>> response =
                    qdrantController.getBatchStatus("batch-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("batch-id-123", response.getBody().getData().getBatchId());
        }

        @Test
        @DisplayName("暂停批量操作成功")
        void pauseBatch_Success() {
            ResumeState state = new ResumeState();
            state.setBatchId("batch-id-123");
            state.setStatus(ResumeState.Status.PAUSED);
            when(resumableBatchService.pauseBatch("batch-id-123")).thenReturn(state);

            ResponseEntity<Result<ResumeState>> response =
                    qdrantController.pauseBatch("batch-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(ResumeState.Status.PAUSED, response.getBody().getData().getStatus());
        }

        @Test
        @DisplayName("恢复批量操作成功")
        void resumeBatch_Success() {
            ResumeState state = new ResumeState();
            state.setBatchId("batch-id-123");
            state.setStatus(ResumeState.Status.RUNNING);
            when(resumableBatchService.resumeBatch("batch-id-123")).thenReturn(state);

            ResponseEntity<Result<ResumeState>> response =
                    qdrantController.resumeBatch("batch-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(ResumeState.Status.RUNNING, response.getBody().getData().getStatus());
        }

        @Test
        @DisplayName("取消批量操作成功")
        void cancelBatch_Success() {
            ResumeState state = new ResumeState();
            state.setBatchId("batch-id-123");
            state.setStatus(ResumeState.Status.CANCELLED);
            when(resumableBatchService.cancelBatch("batch-id-123")).thenReturn(state);

            ResponseEntity<Result<ResumeState>> response =
                    qdrantController.cancelBatch("batch-id-123", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(ResumeState.Status.CANCELLED, response.getBody().getData().getStatus());
        }
    }

    @Nested
    @DisplayName("用户信息缺失测试")
    class UserInfoMissingTests {

        @Test
        @DisplayName("用户信息缺失时抛出异常")
        void userInfoMissing_ThrowsException() {
            when(collectionManagementService.listCollections())
                    .thenThrow(new BizException(com.clacjz.medical.rag.constant.ErrorCode.AUTHENTICATION_FAILED,
                            "[listCollections] 用户信息不存在，请重新登录"));

            assertThrows(BizException.class,
                    () -> qdrantController.listCollections(null));
        }
    }
}
