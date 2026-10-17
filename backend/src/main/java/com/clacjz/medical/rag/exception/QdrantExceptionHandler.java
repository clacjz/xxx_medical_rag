package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.model.vo.Result;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class QdrantExceptionHandler {

    @ExceptionHandler(CollectionOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleCollectionOperationException(CollectionOperationException e) {
        log.error("Collection操作异常: collection={}, operation={}",
                e.getCollectionName(), e.getOperation(), e);

        return Result.error(
                ErrorCode.COLLECTION_ERROR.getCode(),
                "Collection操作失败: " + e.getMessage()
        );
    }

    @ExceptionHandler(CollectionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleCollectionNotFoundException(CollectionNotFoundException e) {
        log.error("Collection不存在: collection={}", e.getCollectionName(), e);

        return Result.error(
                ErrorCode.COLLECTION_NOT_FOUND.getCode(),
                "Collection不存在: " + e.getCollectionName()
        );
    }

    @ExceptionHandler(CollectionAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleCollectionAlreadyExistsException(CollectionAlreadyExistsException e) {
        log.error("Collection已存在: collection={}", e.getCollectionName(), e);

        return Result.error(
                ErrorCode.COLLECTION_ALREADY_EXISTS.getCode(),
                "Collection已存在: " + e.getCollectionName()
        );
    }

    @ExceptionHandler(VectorOperationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleVectorOperationException(VectorOperationException e) {
        log.error("向量操作异常: pointId={}, operation={}",
                e.getPointId(), e.getOperation(), e);

        return Result.error(
                ErrorCode.VECTOR_ERROR.getCode(),
                "向量操作失败，请稍后重试"
        );
    }

    @ExceptionHandler(VectorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleVectorNotFoundException(VectorNotFoundException e) {
        log.error("向量不存在: pointId={}", e.getPointId(), e);

        return Result.error(
                ErrorCode.VECTOR_NOT_FOUND.getCode(),
                "向量不存在: " + e.getPointId()
        );
    }

    @ExceptionHandler(BatchOperationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleBatchOperationException(BatchOperationException e) {
        log.error("批量操作异常: batchIndex={}, itemCount={}",
                e.getBatchIndex(), e.getItemCount(), e);

        return Result.error(
                ErrorCode.BATCH_OPERATION_ERROR.getCode(),
                "批量操作失败: " + e.getMessage()
        );
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleCircuitBreakerOpenException(CallNotPermittedException e) {
        log.warn("Qdrant熔断器触发:", e);

        return Result.error(
                ErrorCode.SERVICE_UNAVAILABLE.getCode(),
                "向量服务暂时不可用，请稍后重试"
        );
    }

    @ExceptionHandler(QdrantException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleQdrantException(QdrantException e) {
        log.error("Qdrant服务异常: {}", e.getMessage(), e);

        return Result.error(
                ErrorCode.VECTOR_ERROR.getCode(),
                "向量服务异常，请稍后重试"
        );
    }
}
