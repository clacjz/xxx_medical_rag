package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class CollectionNotFoundException extends QdrantException {
    
    private final String collectionName;
    
    public CollectionNotFoundException(String collectionName) {
        super(String.format("Collection [%s] 不存在", collectionName));
        this.collectionName = collectionName;
    }
    
    public CollectionNotFoundException(String collectionName, Throwable cause) {
        super(String.format("Collection [%s] 不存在", collectionName), cause);
        this.collectionName = collectionName;
    }
}
