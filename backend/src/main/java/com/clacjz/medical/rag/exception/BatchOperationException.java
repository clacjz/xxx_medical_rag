package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class BatchOperationException extends QdrantException {
    
    private final int batchIndex;
    private final int itemCount;
    
    public BatchOperationException(String message) {
        super(message);
        this.batchIndex = -1;
        this.itemCount = 0;
    }
    
    public BatchOperationException(String message, Throwable cause) {
        super(message, cause);
        this.batchIndex = -1;
        this.itemCount = 0;
    }
    
    public BatchOperationException(int batchIndex, int itemCount, Throwable cause) {
        super(String.format("批量操作失败: 批次=%d, 条目数=%d", batchIndex, itemCount), cause);
        this.batchIndex = batchIndex;
        this.itemCount = itemCount;
    }
    
    public BatchOperationException(int batchIndex, int itemCount) {
        super(String.format("批量操作失败: 批次=%d, 条目数=%d", batchIndex, itemCount));
        this.batchIndex = batchIndex;
        this.itemCount = itemCount;
    }
}
