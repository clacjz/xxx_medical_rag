package com.clacjz.medical.rag.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinIOConfig {

    private final RAGProperties ragProperties;

    @Bean
    public MinioClient minioClient() {
        RAGProperties.MinIOConfig minioConfig = ragProperties.getMinio();

        log.info("Initializing MinIO client with endpoint: {}", minioConfig.getEndpoint());

        return MinioClient.builder()
                .endpoint(minioConfig.getEndpoint())
                .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                .build();
    }
}
