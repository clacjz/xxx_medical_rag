package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.etcd")
public class EtcdProperties {

    private String[] uris = {"http://localhost:2379"};

    private String username;

    private String password;

    private Integer timeout = 5000;

    private Integer connectionTimeout = 5000;

    private String namespace = "";

    private LockConfig lock = new LockConfig();

    private WatchConfig watch = new WatchConfig();

    @Data
    public static class LockConfig {
        private Integer maxRetries = 3;
        private Integer retryDelayMs = 100;
        private Integer maxLockWaitTimeMs = 30000;
    }

    @Data
    public static class WatchConfig {
        private Integer maxRetries = 3;
        private Integer retryDelayMs = 1000;
    }
}
