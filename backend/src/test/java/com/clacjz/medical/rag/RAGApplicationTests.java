package com.clacjz.medical.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("RAG Application Context Tests")
class RAGApplicationTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        assertDoesNotThrow(() -> {
        });
    }
}
