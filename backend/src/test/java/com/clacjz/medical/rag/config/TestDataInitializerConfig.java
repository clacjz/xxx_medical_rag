package com.clacjz.medical.rag.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestDataInitializerConfig {

    @Bean
    @Primary
    public DefaultUsersProperties testDefaultUsersProperties() {
        DefaultUsersProperties properties = new DefaultUsersProperties();
        properties.setEnabled(false);
        return properties;
    }
}
