package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class ElasticsearchProperties {

    private String[] uris = {"http://localhost:9200"};

    private String username;

    private String password;

    private Integer connectionTimeout = 5000;

    private Integer socketTimeout = 30000;

    private Integer maxConnections = 100;

    private Integer maxConnectionsPerRoute = 10;

    private String indexPrefix = "medical_rag_";
}
