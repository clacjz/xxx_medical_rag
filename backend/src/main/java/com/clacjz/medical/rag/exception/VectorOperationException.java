package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class VectorOperationException extends QdrantException {
    
    private final String pointId;
    private final String operation;
    
    public VectorOperationException(String message) {
        super(message);
        this.pointId = null;
        this.operation = null;
    }
    
    public VectorOperationException(String message, Throwable cause) {
        super(message, cause);
        this.pointId = null;
        this.operation = null;
    }
    
    public VectorOperationException(String pointId, String operation, Throwable cause) {
        super(String.format("向量 [%s] 操作 [%s] 失败", pointId, operation), cause);
        this.pointId = pointId;
        this.operation = operation;
    }
    
    public VectorOperationException(String pointId, String operation) {
        super(String.format("向量 [%s] 操作 [%s] 失败", pointId, operation));
        this.pointId = pointId;
        this.operation = operation;
    }
}
