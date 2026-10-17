package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class CollectionOperationException extends QdrantException {
    
    private final String collectionName;
    private final OperationType operation;
    
    public enum OperationType {
        CREATE, GET, DELETE, LIST, EXISTS
    }
    
    public CollectionOperationException(String message) {
        super(message);
        this.collectionName = null;
        this.operation = null;
    }
    
    public CollectionOperationException(String message, Throwable cause) {
        super(message, cause);
        this.collectionName = null;
        this.operation = null;
    }
    
    public CollectionOperationException(String collectionName, OperationType operation, Throwable cause) {
        super(String.format("Collection [%s] 操作 [%s] 失败", collectionName, operation), cause);
        this.collectionName = collectionName;
        this.operation = operation;
    }
    
    public CollectionOperationException(String collectionName, OperationType operation) {
        super(String.format("Collection [%s] 操作 [%s] 失败", collectionName, operation));
        this.collectionName = collectionName;
        this.operation = operation;
    }
}
