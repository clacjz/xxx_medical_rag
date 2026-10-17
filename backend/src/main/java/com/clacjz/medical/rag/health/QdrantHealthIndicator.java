package com.clacjz.medical.rag.health;

import com.clacjz.medical.rag.config.RAGProperties;
import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantHealthIndicator implements HealthIndicator {

    private final QdrantClient qdrantClient;
    private final RAGProperties ragProperties;

    private static final int TIMEOUT_SECONDS = 5;

    @Override
    public Health health() {
        String host = ragProperties.getQdrant().getHost();
        int port = ragProperties.getQdrant().getPort();

        try {
            var collections = qdrantClient.listCollectionsAsync()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long collectionCount = collections.size();

            return Health.up()
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .withDetail("collectionCount", collectionCount)
                    .withDetail("protocol", "gRPC")
                    .build();

        } catch (Exception e) {
            log.error("Qdrant health check failed: host={}, port={}", host, port, e);
            return Health.down()
                    .withDetail("host", host)
                    .withDetail("port", port)
                    .withDetail("error", e.getMessage())
                    .withDetail("protocol", "gRPC")
                    .build();
        }
    }
}
