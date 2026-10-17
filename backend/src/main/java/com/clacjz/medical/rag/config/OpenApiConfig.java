package com.clacjz.medical.rag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Value("${app.host:localhost}")
    private String appHost;

    @Value("${app.port:8080}")
    private int appPort;

    @Bean
    public OpenAPI customOpenAPI() {
        String serverUrl = String.format("http://%s:%d%s", appHost, appPort, contextPath);

        return new OpenAPI()
                .info(new Info()
                        .title("RAG Medical Knowledge Base API")
                        .description("Enterprise RAG System Backend API Documentation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RAG Development Team")
                                .email("dev@example.com")
                                .url("https://example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(Collections.singletonList(
                        new Server()
                                .url(serverUrl)
                                .description("API Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer Token Authentication")));
    }
}
