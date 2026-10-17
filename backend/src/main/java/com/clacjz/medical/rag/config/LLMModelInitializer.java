package com.clacjz.medical.rag.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * LLM模型自动初始化器
 * 启动时从vLLM的/v1/models接口获取实际模型名称并设置到配置中
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LLMModelInitializer implements CommandLineRunner {

    private final RAGProperties ragProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String MODELS_API = "/v1/models";

    @Override
    public void run(String... args) {
        try {
            String baseUrl = ragProperties.getLlm().getBaseUrl();
            String modelsUrl = baseUrl + MODELS_API;

            log.info("LLM model auto-initialization: fetching models from {}", modelsUrl);

            String response = webClient.get()
                    .uri(modelsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(5000));

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.get("data");

                if (data != null && data.isArray() && !data.isEmpty()) {
                    JsonNode firstModel = data.get(0);
                    JsonNode modelId = firstModel.get("id");

                    if (modelId != null && !modelId.isNull()) {
                        String actualModelName = modelId.asText();
                        String currentModel = ragProperties.getLlm().getModel();

                        log.info("LLM model detected: {}, current config: {}", actualModelName, currentModel);

                        if (!actualModelName.equals(currentModel)) {
                            ragProperties.getLlm().setModel(actualModelName);
                            log.info("LLM model updated to: {}", actualModelName);
                        }
                        return;
                    }
                }
                log.warn("LLM model auto-initialization: could not parse model id from response");
            } else {
                log.warn("LLM model auto-initialization: empty response from {}", modelsUrl);
            }
        } catch (Exception e) {
            log.warn("LLM model auto-initialization failed: {}, using default model: {}",
                    e.getMessage(), ragProperties.getLlm().getModel());
        }
    }
}