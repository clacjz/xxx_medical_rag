package com.clacjz.medical.rag.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QdrantConfig {

    private final RAGProperties ragProperties;

    public static final String QDRANT_CIRCUIT_BREAKER = "qdrant";

    @Bean
    public QdrantClient qdrantClient() {
        RAGProperties.QdrantConfig qdrant = ragProperties.getQdrant();

        log.info("Initializing Qdrant client with host: {} and gRPC port: {}", qdrant.getHost(), qdrant.getGrpcPort());

        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                qdrant.getHost(),
                qdrant.getGrpcPort(),
                false
        )
                .withTimeout(Duration.ofSeconds(qdrant.getTimeout()));

        if (qdrant.getApiKey() != null && !qdrant.getApiKey().isBlank()) {
            builder.withApiKey(qdrant.getApiKey());
            log.info("Qdrant client configured with API key authentication");
        }

        QdrantGrpcClient grpcClient = builder.build();
        QdrantClient client = new QdrantClient(grpcClient);

        log.info("Qdrant client initialized: timeout={}s, apiKeyConfigured={}",
                qdrant.getTimeout(), qdrant.getApiKey() != null && !qdrant.getApiKey().isBlank());

        return client;
    }

    @Bean
    public CircuitBreaker qdrantCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of(QDRANT_CIRCUIT_BREAKER, config);

        log.info("Qdrant CircuitBreaker configured: failureRateThreshold=50%, slidingWindowSize=20, waitDurationInOpenState=30s");

        return circuitBreaker;
    }
}
