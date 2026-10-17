package com.clacjz.medical.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchProperties properties;

    private RestClient restClient;

    private ElasticsearchTransport transport;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        log.info("Initializing Elasticsearch client with URIs: {}", Arrays.toString(properties.getUris()));

        HttpHost[] hosts = Arrays.stream(properties.getUris())
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts);

        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(properties.getConnectionTimeout())
                        .setSocketTimeout(properties.getSocketTimeout())
                        .setConnectionRequestTimeout(properties.getMaxConnections())
        );

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder
                    .setMaxConnTotal(properties.getMaxConnections())
                    .setMaxConnPerRoute(properties.getMaxConnectionsPerRoute());

            if (StringUtils.hasText(properties.getUsername()) &&
                    StringUtils.hasText(properties.getPassword())) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(
                                properties.getUsername(),
                                properties.getPassword()
                        )
                );
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            return httpClientBuilder;
        });

        restClient = builder.build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(objectMapper)
        );

        log.info("Elasticsearch client initialized successfully");
        return new ElasticsearchClient(transport);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down Elasticsearch client");
        try {
            if (Objects.nonNull(transport)) {
                transport.close();
            }
            if (Objects.nonNull(restClient)) {
                restClient.close();
            }
            log.info("Elasticsearch client shut down successfully");
        } catch (Exception e) {
            log.error("Error shutting down Elasticsearch client", e);
        }
    }
}
