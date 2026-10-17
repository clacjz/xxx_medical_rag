package com.clacjz.medical.rag.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QdrantExceptionTest {

    @Test
    @DisplayName("测试QdrantException - 基本构造")
    void testQdrantException_BasicConstructor() {
        QdrantException exception = new QdrantException("Test message");
        
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("测试QdrantException - 带cause构造")
    void testQdrantException_WithCause() {
        RuntimeException cause = new RuntimeException("Original error");
        QdrantException exception = new QdrantException("Test message", cause);
        
        assertEquals("Test message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("测试CollectionOperationException - 基本构造")
    void testCollectionOperationException_BasicConstructor() {
        CollectionOperationException exception = new CollectionOperationException("Test message");
        
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCollectionName());
        assertNull(exception.getOperation());
    }

    @Test
    @DisplayName("测试CollectionOperationException - 带collectionName和operation")
    void testCollectionOperationException_WithDetails() {
        CollectionOperationException exception = new CollectionOperationException(
                "test_collection",
                CollectionOperationException.OperationType.CREATE);
        
        assertTrue(exception.getMessage().contains("test_collection"));
        assertTrue(exception.getMessage().contains("CREATE"));
        assertEquals("test_collection", exception.getCollectionName());
        assertEquals(CollectionOperationException.OperationType.CREATE, exception.getOperation());
    }

    @Test
    @DisplayName("测试VectorOperationException - 带pointId和operation")
    void testVectorOperationException_WithDetails() {
        VectorOperationException exception = new VectorOperationException(
                "point-123",
                "INSERT");
        
        assertTrue(exception.getMessage().contains("point-123"));
        assertTrue(exception.getMessage().contains("INSERT"));
        assertEquals("point-123", exception.getPointId());
        assertEquals("INSERT", exception.getOperation());
    }

    @Test
    @DisplayName("测试BatchOperationException - 带batchIndex和itemCount")
    void testBatchOperationException_WithDetails() {
        BatchOperationException exception = new BatchOperationException(5, 1000);
        
        assertTrue(exception.getMessage().contains("5"));
        assertTrue(exception.getMessage().contains("1000"));
        assertEquals(5, exception.getBatchIndex());
        assertEquals(1000, exception.getItemCount());
    }

    @Test
    @DisplayName("测试CollectionNotFoundException")
    void testCollectionNotFoundException() {
        CollectionNotFoundException exception = new CollectionNotFoundException("missing_collection");
        
        assertTrue(exception.getMessage().contains("missing_collection"));
        assertEquals("missing_collection", exception.getCollectionName());
    }

    @Test
    @DisplayName("测试VectorNotFoundException")
    void testVectorNotFoundException() {
        VectorNotFoundException exception = new VectorNotFoundException("missing_vector");
        
        assertTrue(exception.getMessage().contains("missing_vector"));
        assertEquals("missing_vector", exception.getPointId());
    }

    @Test
    @DisplayName("测试CollectionAlreadyExistsException")
    void testCollectionAlreadyExistsException() {
        CollectionAlreadyExistsException exception = new CollectionAlreadyExistsException("existing_collection");
        
        assertTrue(exception.getMessage().contains("existing_collection"));
        assertEquals("existing_collection", exception.getCollectionName());
    }

    @Test
    @DisplayName("测试异常继承关系")
    void testExceptionInheritance() {
        assertTrue(new QdrantException("test") instanceof RuntimeException);
        assertTrue(new CollectionOperationException("test") instanceof QdrantException);
        assertTrue(new VectorOperationException("test") instanceof QdrantException);
        assertTrue(new BatchOperationException("test") instanceof QdrantException);
        assertTrue(new CollectionNotFoundException("test") instanceof QdrantException);
        assertTrue(new VectorNotFoundException("test") instanceof QdrantException);
        assertTrue(new CollectionAlreadyExistsException("test") instanceof QdrantException);
    }
}
