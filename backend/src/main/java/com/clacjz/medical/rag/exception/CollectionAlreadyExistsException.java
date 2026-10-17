package com.clacjz.medical.rag.exception;

import lombok.Getter;

@Getter
public class CollectionAlreadyExistsException extends QdrantException {
    
    private final String collectionName;
    
    public CollectionAlreadyExistsException(String collectionName) {
        super(String.format("Collection [%s] 已存在", collectionName));
        this.collectionName = collectionName;
    }
}
