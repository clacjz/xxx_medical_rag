package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.qdrant.*;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.BatchVectorService;
import com.clacjz.medical.rag.service.CollectionManagementService;
import com.clacjz.medical.rag.service.ResumableBatchService;
import com.clacjz.medical.rag.service.VectorCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/qdrant")
@RequiredArgsConstructor
@Tag(name = "Qdrant API", description = "Qdrant向量数据库底层操作接口")
public class QdrantController {

    private final CollectionManagementService collectionManagementService;
    private final VectorCrudService vectorCrudService;
    private final BatchVectorService batchVectorService;
    private final ResumableBatchService resumableBatchService;

    @GetMapping("/collections")
    @Operation(summary = "列举所有Collection", description = "获取Qdrant中所有Collection的列表")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<String>>> listCollections(
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "listCollections");
        log.info("List Qdrant collections: user={}", username);

        List<String> collections = collectionManagementService.listCollections();
        return ResponseEntity.ok(Result.success(collections));
    }

    @PostMapping("/collections")
    @Operation(summary = "创建Collection", description = "在Qdrant中创建新的Collection")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> createCollection(
            @Valid @RequestBody CreateCollectionInternalRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "createCollection");
        log.info("Create Qdrant collection: name={}, vectorSize={}, user={}",
                request.getCollectionName(), request.getVectorSize(), username);

        collectionManagementService.createCollection(
                request.getCollectionName(),
                request.getVectorSize(),
                collectionManagementService.parseDistance(request.getDistance())
        );

        return ResponseEntity.ok(Result.success("Collection创建成功", null));
    }

    @GetMapping("/collections/{collectionName}")
    @Operation(summary = "获取Collection信息", description = "获取指定Collection的详细信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionStats>> getCollectionInfo(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getCollectionInfo");
        log.info("Get collection info: name={}, user={}", collectionName, username);

        CollectionStats stats = collectionManagementService.getCollectionStats(collectionName);
        return ResponseEntity.ok(Result.success(stats));
    }

    @DeleteMapping("/collections/{collectionName}")
    @Operation(summary = "删除Collection", description = "删除指定的Collection及其所有向量数据")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> deleteCollection(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "deleteCollection");
        log.info("Delete Qdrant collection: name={}, user={}", collectionName, username);

        collectionManagementService.deleteCollection(collectionName);
        return ResponseEntity.ok(Result.success("Collection删除成功", null));
    }

    @PostMapping("/collections/{collectionName}/points")
    @Operation(summary = "插入向量", description = "向指定Collection插入单个向量")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<VectorInsertResponse>> insertVector(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Valid @RequestBody VectorInsertRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "insertVector");
        log.info("Insert vector: collection={}, user={}", collectionName, username);

        String pointId = vectorCrudService.insertVector(
                collectionName,
                request.getVector(),
                request.getPayload()
        );

        return ResponseEntity.ok(Result.success("向量插入成功", new VectorInsertResponse(pointId)));
    }

    @GetMapping("/collections/{collectionName}/points/{pointId}")
    @Operation(summary = "获取向量", description = "根据ID获取指定向量及其Payload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Map<String, Object>>> getVector(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Parameter(description = "向量ID") @PathVariable String pointId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getVector");
        log.info("Get vector: collection={}, pointId={}, user={}", collectionName, pointId, username);

        Map<String, Object> vector = vectorCrudService.getVector(collectionName, pointId);
        return ResponseEntity.ok(Result.success(vector));
    }

    @PutMapping("/collections/{collectionName}/points/{pointId}/payload")
    @Operation(summary = "更新向量Payload", description = "更新指定向量的Payload数据")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> updateVectorPayload(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Parameter(description = "向量ID") @PathVariable String pointId,
            @Valid @RequestBody UpdatePayloadRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "updateVectorPayload");
        log.info("Update vector payload: collection={}, pointId={}, user={}", collectionName, pointId, username);

        vectorCrudService.updateVectorPayload(collectionName, pointId, request.getPayload());
        return ResponseEntity.ok(Result.success("Payload更新成功", null));
    }

    @DeleteMapping("/collections/{collectionName}/points/{pointId}")
    @Operation(summary = "删除向量", description = "根据ID删除指定向量")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> deleteVector(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Parameter(description = "向量ID") @PathVariable String pointId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "deleteVector");
        log.info("Delete vector: collection={}, pointId={}, user={}", collectionName, pointId, username);

        vectorCrudService.deleteVector(collectionName, pointId);
        return ResponseEntity.ok(Result.success("向量删除成功", null));
    }

    @PostMapping("/collections/{collectionName}/points/search")
    @Operation(summary = "向量检索", description = "在指定Collection中进行向量相似度检索")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<List<VectorSearchResult>>> searchVectors(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Valid @RequestBody VectorSearchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "searchVectors");
        log.info("Search vectors: collection={}, limit={}, user={}",
                collectionName, request.getLimit(), username);

        List<VectorSearchResult> results = vectorCrudService.searchVectors(collectionName, request);
        return ResponseEntity.ok(Result.success(results));
    }

    @PostMapping("/collections/{collectionName}/points/batch")
    @Operation(summary = "批量插入向量", description = "向指定Collection批量插入多个向量")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<BatchInsertResult>> batchInsertVectors(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Valid @RequestBody BatchInsertRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "batchInsertVectors");
        log.info("Batch insert vectors: collection={}, count={}, user={}",
                collectionName, request.getItems().size(), username);

        request.setCollectionName(collectionName);
        BatchInsertResult result = batchVectorService.batchInsert(request);

        return ResponseEntity.ok(Result.success("批量插入完成", result));
    }

    @DeleteMapping("/collections/{collectionName}/points")
    @Operation(summary = "批量删除向量", description = "根据ID列表批量删除向量")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> batchDeleteVectors(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Valid @RequestBody BatchDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "batchDeleteVectors");
        log.info("Batch delete vectors: collection={}, count={}, user={}",
                collectionName, request.getPointIds().size(), username);

        batchVectorService.batchDelete(collectionName, request.getPointIds());
        return ResponseEntity.ok(Result.success("批量删除完成", null));
    }

    @PostMapping("/collections/{collectionName}/points/resumable")
    @Operation(summary = "启动可恢复批量操作", description = "启动支持断点续传的批量向量插入")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<ResumableBatchResponse>> startResumableBatch(
            @Parameter(description = "Collection名称") @PathVariable String collectionName,
            @Valid @RequestBody ResumableBatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "startResumableBatch");
        log.info("Start resumable batch: collection={}, count={}, user={}",
                collectionName, request.getItems().size(), username);

        request.setCollectionName(collectionName);
        String batchId = resumableBatchService.startBatch(request, null);

        return ResponseEntity.ok(Result.success("批量操作已启动",
                new ResumableBatchResponse(batchId, "RUNNING")));
    }

    @GetMapping("/batches/{batchId}")
    @Operation(summary = "获取批量操作状态", description = "获取指定批量操作的状态和进度")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ResumeState>> getBatchStatus(
            @Parameter(description = "Batch ID") @PathVariable String batchId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getBatchStatus");
        log.info("Get batch status: batchId={}, user={}", batchId, username);

        ResumeState state = resumableBatchService.getBatchState(batchId);
        return ResponseEntity.ok(Result.success(state));
    }

    @PostMapping("/batches/{batchId}/pause")
    @Operation(summary = "暂停批量操作", description = "暂停正在进行的批量操作")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<ResumeState>> pauseBatch(
            @Parameter(description = "Batch ID") @PathVariable String batchId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "pauseBatch");
        log.info("Pause batch: batchId={}, user={}", batchId, username);

        ResumeState state = resumableBatchService.pauseBatch(batchId);
        return ResponseEntity.ok(Result.success("批量操作已暂停", state));
    }

    @PostMapping("/batches/{batchId}/resume")
    @Operation(summary = "恢复批量操作", description = "恢复已暂停的批量操作")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<ResumeState>> resumeBatch(
            @Parameter(description = "Batch ID") @PathVariable String batchId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "resumeBatch");
        log.info("Resume batch: batchId={}, user={}", batchId, username);

        ResumeState state = resumableBatchService.resumeBatch(batchId);
        return ResponseEntity.ok(Result.success("批量操作已恢复", state));
    }

    @DeleteMapping("/batches/{batchId}")
    @Operation(summary = "取消批量操作", description = "取消正在进行的批量操作")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<ResumeState>> cancelBatch(
            @Parameter(description = "Batch ID") @PathVariable String batchId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "cancelBatch");
        log.info("Cancel batch: batchId={}, user={}", batchId, username);

        ResumeState state = resumableBatchService.cancelBatch(batchId);
        return ResponseEntity.ok(Result.success("批量操作已取消", state));
    }

    private String getUsernameSafely(UserDetails userDetails, String location) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
    }

    public static class CreateCollectionInternalRequest {
        private String collectionName;
        private int vectorSize;
        private String distance;

        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
        public int getVectorSize() { return vectorSize; }
        public void setVectorSize(int vectorSize) { this.vectorSize = vectorSize; }
        public String getDistance() { return distance; }
        public void setDistance(String distance) { this.distance = distance; }
    }

    public static class VectorInsertRequest {
        private float[] vector;
        private Map<String, Object> payload;

        public float[] getVector() { return vector; }
        public void setVector(float[] vector) { this.vector = vector; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    public static class VectorInsertResponse {
        private String pointId;

        public VectorInsertResponse(String pointId) {
            this.pointId = pointId;
        }

        public String getPointId() { return pointId; }
        public void setPointId(String pointId) { this.pointId = pointId; }
    }

    public static class UpdatePayloadRequest {
        private Map<String, Object> payload;

        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    public static class BatchDeleteRequest {
        private List<String> pointIds;

        public List<String> getPointIds() { return pointIds; }
        public void setPointIds(List<String> pointIds) { this.pointIds = pointIds; }
    }

    public static class ResumableBatchResponse {
        private String batchId;
        private String status;

        public ResumableBatchResponse(String batchId, String status) {
            this.batchId = batchId;
            this.status = status;
        }

        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
