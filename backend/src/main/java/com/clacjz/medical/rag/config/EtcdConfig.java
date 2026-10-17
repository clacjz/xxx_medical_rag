package com.clacjz.medical.rag.config;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EtcdConfig {

    private final EtcdProperties properties;

    private Client client;

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_KEY_LENGTH = 512;

    @PostConstruct
    public void validateProperties() {
        String namespace = properties.getNamespace();
        if (StringUtils.hasText(namespace)) {
            if (!VALID_KEY_PATTERN.matcher(namespace).matches()) {
                throw new IllegalStateException(
                        "etcd namespace contains invalid characters. Only alphanumeric, underscore and hyphen are allowed: " + namespace);
            }
            if (namespace.length() > 128) {
                throw new IllegalStateException(
                        "etcd namespace is too long. Maximum length is 128 characters: " + namespace.length());
            }
            if (namespace.contains("..")) {
                throw new IllegalStateException("etcd namespace contains path traversal pattern (..)");
            }
            log.info("Etcd namespace validated: {}", namespace);
        }
    }

    @Bean
    public Client etcdClient() {
        log.info("Initializing etcd client with URIs: {}", List.of(properties.getUris()));

        ClientBuilder builder = Client.builder()
                .endpoints(properties.getUris());

        if (StringUtils.hasText(properties.getUsername()) &&
                StringUtils.hasText(properties.getPassword())) {
            builder.user(io.etcd.jetcd.ByteSequence.from(properties.getUsername().getBytes(StandardCharsets.UTF_8)))
                    .password(io.etcd.jetcd.ByteSequence.from(properties.getPassword().getBytes(StandardCharsets.UTF_8)));
        }

        client = builder.build();

        log.info("Etcd client initialized successfully");
        return client;
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down etcd client");
        try {
            if (Objects.nonNull(client)) {
                client.close();
            }
            log.info("Etcd client shut down successfully");
        } catch (Exception e) {
            log.error("Error shutting down etcd client", e);
        }
    }

    public String getFullKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Key length exceeds maximum allowed: " + MAX_KEY_LENGTH);
        }

        String sanitizedKey = sanitizeKey(key);
        if (!VALID_KEY_PATTERN.matcher(sanitizedKey).matches()) {
            throw new IllegalArgumentException(
                    "Key contains invalid characters. Only alphanumeric, underscore and hyphen are allowed: " + key);
        }

        String namespace = properties.getNamespace();
        if (StringUtils.hasText(namespace)) {
            String sanitizedNamespace = sanitizeNamespace(namespace);
            return sanitizedNamespace + "/" + sanitizedKey;
        }
        return sanitizedKey;
    }

    private String sanitizeKey(String key) {
        return key.trim().replaceAll("[\\x00-\\x1F]", "");
    }

    private String sanitizeNamespace(String namespace) {
        String sanitized = namespace.trim()
                .replaceAll("[\\x00-\\x1F]", "")
                .replaceAll("\\.{2,}", "")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");

        if (sanitized.contains("..")) {
            throw new IllegalArgumentException("Namespace contains path traversal pattern");
        }

        return sanitized;
    }

    public String getNamespace() {
        return properties.getNamespace();
    }
}
