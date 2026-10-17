package com.clacjz.medical.rag;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 集成测试基类
 *
 * 提供真实的 PostgreSQL、Redis、Qdrant 等中间件容器
 * 替代 H2 内存数据库，消除测试环境与生产环境的差异
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("testcontainers")
public abstract class BaseIntegrationTest {

    // PostgreSQL 容器
    @Container
    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
                    .withDatabaseName("rag_medical")
                    .withUsername("postgres")
                    .withPassword("postgres123")
                    .withReuse(true);

    // Redis 容器
    @Container
    protected static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:8.4.0-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    // Qdrant 容器 (简化版本，仅健康检查，不参与实际测试)
    @Container
    protected static final GenericContainer<?> qdrantContainer =
            new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.7.4"))
                    .withExposedPorts(6333, 6334)
                    .withReuse(true);

    // Etcd 容器 (用于配置中心测试)
    @Container
    protected static final GenericContainer<?> etcdContainer =
            new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.6.7"))
                    .withExposedPorts(2379, 2380)
                    .withEnv("ETCD_LISTEN_CLIENT_URLS", "http://0.0.0.0:2379")
                    .withEnv("ETCD_ADVERTISE_CLIENT_URLS", "http://0.0.0.0:2379")
                    .withReuse(true);

    // Elasticsearch 容器
    @Container
    protected static final GenericContainer<?> elasticsearchContainer =
            new GenericContainer<>(DockerImageName.parse("elasticsearch:8.12.0"))
                    .withExposedPorts(9200, 9300)
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // JPA/Hibernate - 使用 update 模式让 Liquibase 处理 schema
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Qdrant
        registry.add("rag.qdrant.host", qdrantContainer::getHost);
        registry.add("rag.qdrant.port", () -> qdrantContainer.getMappedPort(6333));
        registry.add("rag.qdrant.grpc-port", () -> qdrantContainer.getMappedPort(6334));

        // Etcd
        registry.add("rag.etcd.url", () -> "http://" + etcdContainer.getHost() + ":" + etcdContainer.getMappedPort(2379));

        // Elasticsearch
        registry.add("rag.elasticsearch.url", () -> "http://" + elasticsearchContainer.getHost() + ":" + elasticsearchContainer.getMappedPort(9200));

        // 禁用默认数据初始化（避免与容器数据冲突）
        registry.add("app.default-users.enabled", () -> "false");
        registry.add("app.default-department.enabled", () -> "false");

        // JWT Secret for tests
        registry.add("jwt.secret", () -> "test-secret-key-for-testing-purposes-only-must-be-256-bits");
    }
}
