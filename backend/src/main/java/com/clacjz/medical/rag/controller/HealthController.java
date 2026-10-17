package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.model.vo.HealthCheckResult;
import com.clacjz.medical.rag.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "Health Check", description = "System Health Check APIs")
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RAGProperties ragProperties;

    @Value("${rag.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${spring.etcd.uris:http://localhost:12379}")
    private String etcdUris;

    @Value("${spring.elasticsearch.uris:http://localhost:19200}")
    private String elasticsearchUris;

    private final ExecutorService healthCheckExecutor = Executors.newFixedThreadPool(10);

    @GetMapping
    @Operation(summary = "Comprehensive health check", description = "Check health status of all services with concurrent execution")
    public ResponseEntity<Result<HealthCheckResult>> health() {
        long startTime = System.currentTimeMillis();
        Map<String, HealthCheckResult.ServiceHealth> services = new ConcurrentHashMap<>();

        try {
            CompletableFuture<Void> postgresFuture = CompletableFuture.runAsync(
                    () -> checkPostgreSQLAsync(services), healthCheckExecutor);
            CompletableFuture<Void> redisFuture = CompletableFuture.runAsync(
                    () -> checkRedisAsync(services), healthCheckExecutor);
            CompletableFuture<Void> qdrantFuture = CompletableFuture.runAsync(
                    () -> checkQdrantAsync(services), healthCheckExecutor);
            CompletableFuture<Void> minioFuture = CompletableFuture.runAsync(
                    () -> checkMinioAsync(services), healthCheckExecutor);
            CompletableFuture<Void> llmFuture = CompletableFuture.runAsync(
                    () -> checkLlmAsync(services), healthCheckExecutor);
            CompletableFuture<Void> etcdFuture = CompletableFuture.runAsync(
                    () -> checkEtcdAsync(services), healthCheckExecutor);
            CompletableFuture<Void> elasticsearchFuture = CompletableFuture.runAsync(
                    () -> checkElasticsearchAsync(services), healthCheckExecutor);

            CompletableFuture.allOf(
                    postgresFuture, redisFuture, qdrantFuture,
                    minioFuture, llmFuture, etcdFuture, elasticsearchFuture
            ).join();

        } catch (Exception e) {
            log.error("Error during concurrent health checks", e);
        }

        long totalResponseTime = System.currentTimeMillis() - startTime;

        boolean coreServicesHealthy = services.keySet().stream()
                .filter(this::isCoreService)
                .allMatch(key -> "UP".equals(services.get(key).getStatus()));

        boolean allHealthy = services.values().stream()
                .allMatch(s -> "UP".equals(s.getStatus()));

        String overallStatus = allHealthy ? "UP" : "DEGRADED";

        if (services.values().stream().anyMatch(s -> "DOWN".equals(s.getStatus()))) {
            overallStatus = "DEGRADED";
        }

        HealthCheckResult result = HealthCheckResult.builder()
                .status(overallStatus)
                .totalResponseTime(totalResponseTime)
                .timestamp(Instant.now())
                .services(services)
                .build();

        HttpStatus httpStatus = coreServicesHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(httpStatus).body(Result.success(result));
    }

    private boolean isCoreService(String serviceName) {
        return "postgresql".equalsIgnoreCase(serviceName) || "redis".equalsIgnoreCase(serviceName);
    }

    @GetMapping("/{service}")
    @Operation(summary = "Individual service health check", description = "Check health status of a specific service")
    public ResponseEntity<Result<HealthCheckResult.ServiceHealth>> checkServiceHealth(
            @PathVariable String service) {
        Map<String, HealthCheckResult.ServiceHealth> services = new ConcurrentHashMap<>();

        switch (service.toLowerCase()) {
            case "postgres", "postgresql" -> checkPostgreSQL(services);
            case "redis" -> checkRedis(services);
            case "qdrant" -> checkQdrant(services);
            case "minio" -> checkMinio(services);
            case "llm", "vllm" -> checkLlm(services);
            case "etcd" -> checkEtcd(services);
            case "elasticsearch", "es" -> checkElasticsearch(services);
            default -> {
                HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                        .status("UNKNOWN")
                        .message("Unknown service: " + service)
                        .responseTime(0L)
                        .build();
                services.put(service, health);
            }
        }

        // NPE防护：确保从services map中获取的对象不为null
        HealthCheckResult.ServiceHealth health = services.get(service.toLowerCase());
        if (health == null) {
            health = HealthCheckResult.ServiceHealth.builder()
                    .status("UNKNOWN")
                    .message("Service not found: " + service)
                    .responseTime(0L)
                    .build();
        }
        HttpStatus httpStatus = "UP".equals(health.getStatus()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(httpStatus).body(Result.success(health));
    }

    @GetMapping("/etcd")
    @Operation(summary = "etcd health check", description = "Check etcd configuration center connection")
    public ResponseEntity<Result<HealthCheckResult.ServiceHealth>> checkEtcdHealth() {
        Map<String, HealthCheckResult.ServiceHealth> services = new ConcurrentHashMap<>();
        checkEtcd(services);

        // NPE防护：确保从services map中获取的对象不为null
        HealthCheckResult.ServiceHealth health = services.get("etcd");
        if (health == null) {
            health = HealthCheckResult.ServiceHealth.builder()
                    .status("UNKNOWN")
                    .message("etcd service check not completed")
                    .responseTime(0L)
                    .build();
        }
        HttpStatus httpStatus = "UP".equals(health.getStatus()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(httpStatus).body(Result.success(health));
    }

    @GetMapping("/elasticsearch")
    @Operation(summary = "Elasticsearch health check", description = "Check Elasticsearch connection")
    public ResponseEntity<Result<HealthCheckResult.ServiceHealth>> checkElasticsearchHealth() {
        Map<String, HealthCheckResult.ServiceHealth> services = new ConcurrentHashMap<>();
        checkElasticsearch(services);

        // NPE防护：确保从services map中获取的对象不为null
        HealthCheckResult.ServiceHealth health = services.get("elasticsearch");
        if (health == null) {
            health = HealthCheckResult.ServiceHealth.builder()
                    .status("UNKNOWN")
                    .message("elasticsearch service check not completed")
                    .responseTime(0L)
                    .build();
        }
        HttpStatus httpStatus = "UP".equals(health.getStatus()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(httpStatus).body(Result.success(health));
    }

    private void checkPostgreSQLAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkPostgreSQL(services);
    }

    private void checkRedisAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkRedis(services);
    }

    private void checkQdrantAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkQdrant(services);
    }

    private void checkMinioAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkMinio(services);
    }

    private void checkLlmAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkLlm(services);
    }

    private void checkEtcdAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkEtcd(services);
    }

    private void checkElasticsearchAsync(Map<String, HealthCheckResult.ServiceHealth> services) {
        checkElasticsearch(services);
    }

    private void checkPostgreSQL(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(5);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(valid ? "UP" : "DOWN")
                    .message(valid ? "Connection successful" : "Connection failed")
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .connectionAvailable(valid)
                            .additionalInfo("Database: rag_medical")
                            .build())
                    .build();

            services.put("postgresql", health);
        } catch (Exception e) {
            log.error("PostgreSQL health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("Connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .connectionAvailable(false)
                            .additionalInfo("Database: rag_medical")
                            .build())
                    .build();

            services.put("postgresql", health);
        }
    }

    private void checkRedis(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isUp = "PONG".equals(pong);
            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isUp ? "UP" : "DOWN")
                    .message("PING successful")
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .connectionAvailable(isUp)
                            .additionalInfo("Database: 0")
                            .build())
                    .build();

            services.put("redis", health);
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("Connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .connectionAvailable(false)
                            .additionalInfo("Database: 0")
                            .build())
                    .build();

            services.put("redis", health);
        }
    }

    private void checkQdrant(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            URL url = new URL("http://" + ragProperties.getQdrant().getHost() + ":" + ragProperties.getQdrant().getPort() + "/healthz");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isHealthy = responseCode == 200;
            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isHealthy ? "UP" : "DOWN")
                    .message(isHealthy ? "Qdrant vector database is healthy" : "Qdrant health check failed with status: " + responseCode)
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host(ragProperties.getQdrant().getHost())
                            .port(ragProperties.getQdrant().getPort())
                            .endpoint("/healthz")
                            .connectionAvailable(isHealthy)
                            .build())
                    .build();

            services.put("qdrant", health);
        } catch (Exception e) {
            log.error("Qdrant health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("Qdrant connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host(ragProperties.getQdrant().getHost())
                            .port(ragProperties.getQdrant().getPort())
                            .endpoint("/healthz")
                            .connectionAvailable(false)
                            .build())
                    .build();

            services.put("qdrant", health);
        }
    }

    private void checkMinio(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            URL url = new URL(minioEndpoint + "/minio/health/live");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isHealthy = responseCode == 200;
            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isHealthy ? "UP" : "DOWN")
                    .message(isHealthy ? "MinIO object storage is healthy" : "MinIO health check failed with status: " + responseCode)
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .endpoint("/minio/health/live")
                            .connectionAvailable(isHealthy)
                            .build())
                    .build();

            services.put("minio", health);
        } catch (Exception e) {
            log.error("MinIO health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("MinIO connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .endpoint("/minio/health/live")
                            .connectionAvailable(false)
                            .build())
                    .build();

            services.put("minio", health);
        }
    }

    private void checkLlm(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            // VLLM 使用 OpenAI 兼容 API
            URL url = new URL(ragProperties.getLlm().getBaseUrl() + "/v1/models");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isHealthy = responseCode == 200;
            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isHealthy ? "UP" : "DOWN")
                    .message(isHealthy ? "VLLM LLM service is running" : "VLLM health check failed with status: " + responseCode)
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .endpoint("/v1/models")
                            .connectionAvailable(isHealthy)
                            .build())
                    .build();

            services.put("llm", health);
        } catch (Exception e) {
            log.error("VLLM health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("VLLM connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .endpoint("/v1/models")
                            .connectionAvailable(false)
                            .build())
                    .build();

            services.put("llm", health);
        }
    }

    private void checkEtcd(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            URL url = new URL(etcdUris + "/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isHealthy = responseCode == 200;
            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isHealthy ? "UP" : "DOWN")
                    .message(isHealthy ? "etcd configuration center is healthy" : "etcd health check failed with status: " + responseCode)
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host("localhost")
                            .port(12379)
                            .endpoint(etcdUris)
                            .connectionAvailable(isHealthy)
                            .additionalInfo("Cluster: rag-etcd-token")
                            .build())
                    .build();

            services.put("etcd", health);
        } catch (Exception e) {
            log.error("etcd health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("etcd connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host("localhost")
                            .port(12379)
                            .endpoint(etcdUris)
                            .connectionAvailable(false)
                            .additionalInfo("Cluster: rag-etcd-token")
                            .build())
                    .build();

            services.put("etcd", health);
        }
    }

    private void checkElasticsearch(Map<String, HealthCheckResult.ServiceHealth> services) {
        long startTime = System.currentTimeMillis();
        try {
            URL url = new URL(elasticsearchUris + "/_cluster/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            boolean isHealthy = responseCode == 200;
            String statusMessage = "Elasticsearch cluster is healthy";

            if (isHealthy) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()));
                String response = reader.readLine();
                reader.close();

                if (response != null && response.contains("\"status\":\"green\"")) {
                    statusMessage = "Elasticsearch cluster status: green";
                } else if (response != null && response.contains("\"status\":\"yellow\"")) {
                    statusMessage = "Elasticsearch cluster status: yellow (degraded)";
                } else if (response != null && response.contains("\"status\":\"red\"")) {
                    statusMessage = "Elasticsearch cluster status: red (unhealthy)";
                    isHealthy = false;
                }
            }

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status(isHealthy ? "UP" : "DOWN")
                    .message(statusMessage)
                    .responseTime(responseTime)
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host("localhost")
                            .port(19200)
                            .endpoint(elasticsearchUris)
                            .connectionAvailable(isHealthy)
                            .additionalInfo("Cluster: rag-elasticsearch-cluster")
                            .build())
                    .build();

            services.put("elasticsearch", health);
        } catch (Exception e) {
            log.error("Elasticsearch health check failed", e);
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheckResult.ServiceHealth health = HealthCheckResult.ServiceHealth.builder()
                    .status("DOWN")
                    .message("Elasticsearch connection failed: " + e.getMessage())
                    .responseTime(responseTime)
                    .errorType(e.getClass().getSimpleName())
                    .errorTimestamp(Instant.now())
                    .details(HealthCheckResult.ServiceHealth.ServiceDetails.builder()
                            .host("localhost")
                            .port(19200)
                            .endpoint(elasticsearchUris)
                            .connectionAvailable(false)
                            .additionalInfo("Cluster: rag-elasticsearch-cluster")
                            .build())
                    .build();

            services.put("elasticsearch", health);
        }
    }
}
