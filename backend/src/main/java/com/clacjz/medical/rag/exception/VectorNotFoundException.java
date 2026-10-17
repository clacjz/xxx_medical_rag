package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class VectorNotFoundException extends QdrantException {
    
    private final String pointId;
    
    public VectorNotFoundException(String pointId) {
        super(String.format("向量 [%s] 不存在", pointId));
        this.pointId = pointId;
    }
    
    public VectorNotFoundException(String pointId, Throwable cause) {
        super(String.format("向量 [%s] 不存在", pointId), cause);
        this.pointId = pointId;
    }
}
