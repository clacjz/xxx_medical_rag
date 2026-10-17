package com.clacjz.medical.rag.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Custom Exceptions 单元测试")
class ExceptionsTest {

    @Nested
    @DisplayName("CollectionNotFoundException")
    class CollectionNotFoundExceptionTests {

        @Test
        @DisplayName("通过 collectionId 构造")
        void shouldConstructWithId() {
            CollectionNotFoundException ex = new CollectionNotFoundException("col-1");
            assertNotNull(ex);
            assertTrue(ex.getMessage().contains("col-1"));
        }
    }

    @Nested
    @DisplayName("CollectionAlreadyExistsException")
    class CollectionAlreadyExistsExceptionTests {

        @Test
        @DisplayName("通过 collectionId 构造")
        void shouldConstructWithId() {
            CollectionAlreadyExistsException ex = new CollectionAlreadyExistsException("col-1");
            assertNotNull(ex);
            assertTrue(ex.getMessage().contains("col-1"));
        }
    }

    @Nested
    @DisplayName("CollectionOperationException")
    class CollectionOperationExceptionTests {

        @Test
        @DisplayName("通过 message 构造")
        void shouldConstructWithMessage() {
            CollectionOperationException ex = new CollectionOperationException("操作失败");
            assertEquals("操作失败", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("VectorNotFoundException")
    class VectorNotFoundExceptionTests {

        @Test
        @DisplayName("通过 vectorId 构造")
        void shouldConstructWithId() {
            VectorNotFoundException ex = new VectorNotFoundException("v-1");
            assertNotNull(ex);
            assertTrue(ex.getMessage().contains("v-1"));
        }
    }

    @Nested
    @DisplayName("VectorOperationException")
    class VectorOperationExceptionTests {

        @Test
        @DisplayName("通过 message 构造")
        void shouldConstructWithMessage() {
            VectorOperationException ex = new VectorOperationException("向量操作失败");
            assertEquals("向量操作失败", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("BatchOperationException")
    class BatchOperationExceptionTests {

        @Test
        @DisplayName("构造并检查 message")
        void shouldConstructWithMessage() {
            BatchOperationException ex = new BatchOperationException("批量操作失败");
            assertNotNull(ex);
            assertEquals("批量操作失败", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("AiServiceException")
    class AiServiceExceptionTests {

        @Test
        @DisplayName("通过 ErrorCode 和 serviceName 构造")
        void shouldConstructWithErrorCode() {
            AiServiceException ex = new AiServiceException(
                    com.clacjz.medical.rag.constant.ErrorCode.AI_SERVICE_UNAVAILABLE, "Qwen3.6");
            assertNotNull(ex);
            assertNotNull(ex.getMessage());
            assertEquals("Qwen3.6", ex.getServiceName());
        }
    }
}
