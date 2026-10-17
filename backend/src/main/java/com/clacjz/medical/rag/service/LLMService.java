package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.exception.AiServiceException;
import com.clacjz.medical.rag.model.dto.StreamEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private static final String SERVICE_NAME = "LLM";
    private static final String OPENAI_CHAT_API = "/v1/chat/completions";
    private static final String OPENAI_STREAM_CHAT_API = "/v1/chat/completions";

    private final RAGProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    /**
     * 非流式生成
     */
    public String generate(String systemPrompt, String userMessage) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        return generate(systemPrompt, userMessage, config.getTemperature(), config.getMaxTokens(), null, false, null, false);
    }

    /**
     * 非流式生成（带会话历史）
     */
    public String generate(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        return generate(systemPrompt, userMessage, config.getTemperature(), config.getMaxTokens(), null, true, history, false);
    }

    /**
     * 带完整参数的非流式生成
     */
    public String generate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                          boolean enableHistory, List<Map<String, String>> history) {
        return generate(systemPrompt, userMessage, temperature, maxTokens, null, enableHistory, history, false);
    }

    /**
     * 带完整参数（含 topP）的非流式生成 - 使用 OpenAI 兼容接口
     */
    public String generate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                          Double topP, boolean enableHistory, List<Map<String, String>> history) {
        return generate(systemPrompt, userMessage, temperature, maxTokens, topP, enableHistory, history, false);
    }

    /**
     * 带完整参数（含 topP）的非流式生成 - 使用 OpenAI 兼容接口
     */
    public String generate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                          Double topP, boolean enableHistory, List<Map<String, String>> history,
                          boolean thinkingEnabled) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        String url = config.getBaseUrl() + OPENAI_CHAT_API;

        Map<String, Object> requestBody = buildOpenAIRequestBody(systemPrompt, userMessage, false,
                temperature != null ? temperature : config.getTemperature(),
                maxTokens != null ? maxTokens : config.getMaxTokens(),
                topP != null ? topP : config.getTopP(),
                config.getModel(),
                enableHistory, history,
                null,  // no response_format for regular generation
                thinkingEnabled);

        log.info("LLM generate request: url={}", url);

        try {
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(config.getTimeout()));

            if (response == null) {
                throw new AiServiceException(SERVICE_NAME, "Empty response from LLM");
            }

            log.debug("LLM generate response: {}", response);
            return parseOpenAIResponse(response);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM service error", e);
            throw new AiServiceException(SERVICE_NAME, "LLM service error: " + e.getMessage(), e);
        }
    }

    /**
     * 带完整参数（含 topP）的非流式生成 - 返回内容和思考过程
     */
    public GenerateResult generateWithReasoning(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                                               Double topP, boolean enableHistory, List<Map<String, String>> history,
                                               boolean thinkingEnabled) {
        return generateWithReasoning(systemPrompt, userMessage, temperature, maxTokens, topP, enableHistory, history,
                thinkingEnabled, null);
    }

    /**
     * 带完整参数（含 topP 和 thinkingMaxTokens）的非流式生成 - 返回内容和思考过程
     */
    public GenerateResult generateWithReasoning(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                                               Double topP, boolean enableHistory, List<Map<String, String>> history,
                                               boolean thinkingEnabled, Integer thinkingMaxTokens) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        String url = config.getBaseUrl() + OPENAI_CHAT_API;

        Map<String, Object> requestBody = buildOpenAIRequestBody(systemPrompt, userMessage, false,
                temperature != null ? temperature : config.getTemperature(),
                maxTokens != null ? maxTokens : config.getMaxTokens(),
                topP != null ? topP : config.getTopP(),
                config.getModel(),
                enableHistory, history,
                null,  // no response_format for reasoning generation
                thinkingEnabled,
                thinkingMaxTokens);

        log.info("LLM generateWithReasoning request: url={}", url);

        try {
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(config.getTimeout()));

            if (response == null) {
                throw new AiServiceException(SERVICE_NAME, "Empty response from LLM");
            }

            log.debug("LLM generateWithReasoning response: {}", response);
            return parseOpenAIResponseFull(response);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM service error", e);
            throw new AiServiceException(SERVICE_NAME, "LLM service error: " + e.getMessage(), e);
        }
    }

    /**
     * 带 response_format: json_object 的非流式生成
     * 用于需要结构化JSON输出的场景（如NER提取、文本分类等）
     * 低温度(0.1)确保输出稳定性
     */
    public String generateJson(String systemPrompt, String userMessage) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        String url = config.getBaseUrl() + OPENAI_CHAT_API;

        Map<String, Object> responseFormat = Map.of("type", "json_object");
        Map<String, Object> requestBody = buildOpenAIRequestBody(systemPrompt, userMessage, false,
                0.1,  // low temperature for stable extraction
                config.getMaxTokens(),
                0.9,  // topP
                config.getModel(),
                false, null,  // no history
                responseFormat,
                false);  // thinkingEnabled - JSON generation doesn't need thinking

        log.info("LLM generateJson request: url={}", url);

        try {
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(config.getTimeout()));

            if (response == null) {
                throw new AiServiceException(SERVICE_NAME, "Empty response from LLM");
            }

            log.debug("LLM generateJson response length: {}", response.length());
            return parseOpenAIResponse(response);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM generateJson error", e);
            throw new AiServiceException(SERVICE_NAME, "LLM generateJson error: " + e.getMessage(), e);
        }
    }

    /**
     * 流式生成 - 返回 StreamEventDTO 以区分 reasoning 和 token
     */
    public Flux<StreamEventDTO> streamGenerate(String systemPrompt, String userMessage) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        return streamGenerate(systemPrompt, userMessage, config.getTemperature(), config.getMaxTokens(), null, false, null, false);
    }

    /**
     * 带完整参数的流式生成 - 返回 StreamEventDTO
     */
    public Flux<StreamEventDTO> streamGenerate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                                      boolean enableHistory, List<Map<String, String>> history) {
        return streamGenerate(systemPrompt, userMessage, temperature, maxTokens, null, enableHistory, history, false);
    }

    /**
     * 带完整参数（含 topP 和 thinkingMaxTokens）的流式生成 - 使用 OpenAI 兼容接口
     * 返回 Flux<StreamEventDTO> 以区分 reasoning（思考过程）和 token（回答内容）
     */
    public Flux<StreamEventDTO> streamGenerate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                                      Double topP, boolean enableHistory, List<Map<String, String>> history,
                                      boolean thinkingEnabled) {
        return streamGenerate(systemPrompt, userMessage, temperature, maxTokens, topP, enableHistory, history,
                thinkingEnabled, null);
    }

    /**
     * 带完整参数（含 topP 和 thinkingMaxTokens）的流式生成 - 使用 OpenAI 兼容接口
     * 返回 Flux<StreamEventDTO> 以区分 reasoning（思考过程）和 token（回答内容）
     */
    public Flux<StreamEventDTO> streamGenerate(String systemPrompt, String userMessage, Double temperature, Integer maxTokens,
                                      Double topP, boolean enableHistory, List<Map<String, String>> history,
                                      boolean thinkingEnabled, Integer thinkingMaxTokens) {
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        String url = config.getBaseUrl() + OPENAI_STREAM_CHAT_API;

        Map<String, Object> requestBody = buildOpenAIRequestBody(systemPrompt, userMessage, true,
                temperature != null ? temperature : config.getTemperature(),
                maxTokens != null ? maxTokens : config.getMaxTokens(),
                topP != null ? topP : config.getTopP(),
                config.getModel(),
                enableHistory, history,
                null,  // no response_format for streaming
                thinkingEnabled,
                thinkingMaxTokens);

        log.info("LLM stream request: url={}, model={}", url, config.getModel());
        log.debug("LLM stream request body: {}", requestBody);

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> log.debug("LLM raw chunk: {}", chunk))
                // 处理标准 SSE 格式和控制消息
                .flatMap(chunk -> {
                    // 跳过空块
                    if (chunk == null || chunk.isBlank()) {
                        return Mono.empty();
                    }
                    // 处理标准 SSE 控制消息: data: [DONE]
                    String trimmed = chunk.trim();
                    if (trimmed.startsWith("data: ")) {
                        String data = trimmed.substring(6).trim();
                        if ("[DONE]".equals(data) || "[done]".equals(data)) {
                            return Mono.just(StreamEventDTO.builder().type("done").build());
                        }
                        // 标准 SSE 格式：data: {...} — 提取 JSON 并解析
                        if (data.startsWith("{")) {
                            return parseOpenAISseDataToEvent(data);
                        }
                        return Mono.empty();
                    }
                    // 处理 vLLM 返回的纯 JSON 行
                    if (trimmed.startsWith("{")) {
                        return parseOpenAISseDataToEvent(trimmed);
                    }
                    return Mono.empty();
                })
                .doOnNext(event -> log.debug("SSE event emitted: type={}, contentLength={}", event.getType(), event.getContent() != null ? event.getContent().length() : 0))
                .doOnError(e -> log.error("LLM stream service error", e))
                .onErrorResume(e -> {
                    log.error("LLM stream error, falling back to error message: {}", e.getMessage());
                    return Flux.just(StreamEventDTO.builder()
                            .type("error")
                            .message("LLM服务调用失败: " + e.getMessage())
                            .build());
                });
    }

    /**
     * 构建 OpenAI 兼容格式的请求体
     *
     * @param responseFormat response_format参数，如 {"type": "json_object"}，可为null
     */
    private Map<String, Object> buildOpenAIRequestBody(String systemPrompt, String userMessage, boolean stream,
                                                      Double temperature, Integer maxTokens, Double topP, String model,
                                                      boolean enableHistory, List<Map<String, String>> history,
                                                      Map<String, Object> responseFormat,
                                                      boolean thinkingEnabled) {
        return buildOpenAIRequestBody(systemPrompt, userMessage, stream, temperature, maxTokens, topP, model,
                enableHistory, history, responseFormat, thinkingEnabled, null);
    }

    private Map<String, Object> buildOpenAIRequestBody(String systemPrompt, String userMessage, boolean stream,
                                                      Double temperature, Integer maxTokens, Double topP, String model,
                                                      boolean enableHistory, List<Map<String, String>> history,
                                                      Map<String, Object> responseFormat,
                                                      boolean thinkingEnabled,
                                                      Integer thinkingMaxTokens) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统消息
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        // 历史消息（此前的多轮对话）
        if (enableHistory && history != null && !history.isEmpty()) {
            for (Map<String, String> histMsg : history) {
                String role = histMsg.get("role");
                String histContent = histMsg.get("content");
                if (role != null && histContent != null) {
                    messages.add(Map.of("role", role, "content", histContent));
                }
            }
        }

        // 当前用户消息
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature != null ? temperature : 0.7);
        requestBody.put("max_tokens", maxTokens != null ? maxTokens : 16384);
        requestBody.put("stream", stream);
        RAGProperties.LLMConfig config = ragProperties.getLlm();
        requestBody.put("repetition_penalty", config.getRepetitionPenalty());

        if (topP != null) {
            requestBody.put("top_p", topP);
        }

        if (responseFormat != null && !responseFormat.isEmpty()) {
            requestBody.put("response_format", responseFormat);
        }

        // 添加思考模式开关和思考token限制
        Map<String, Object> chatTemplate_kwargs = new HashMap<>();
        chatTemplate_kwargs.put("enable_thinking", thinkingEnabled);
        // 限制思考过程最大token：优先使用请求参数，否则取配置默认值
        int effectiveThinkingMaxTokens = thinkingMaxTokens != null
                ? thinkingMaxTokens
                : ragProperties.getLlm().getThinkingMaxTokens();
        chatTemplate_kwargs.put("thinking_max_tokens", effectiveThinkingMaxTokens);
        requestBody.put("chat_template_kwargs", chatTemplate_kwargs);

        log.info("LLM request - thinkingEnabled={}, thinking_max_tokens={}, model={}, max_tokens={}",
                thinkingEnabled, effectiveThinkingMaxTokens, requestBody.get("model"), requestBody.get("max_tokens"));

        return requestBody;
    }

    /**
     * 解析 OpenAI 非流式响应
     */
    private String parseOpenAIResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }
                }
            }
            throw new AiServiceException(SERVICE_NAME, "Invalid OpenAI response format");
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", response, e);
            throw new AiServiceException(SERVICE_NAME, "Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 OpenAI 非流式响应，同时提取 content 和 reasoning
     */
    private GenerateResult parseOpenAIResponseFull(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    JsonNode reasoning = message.get("reasoning");
                    String contentText = (content != null && !content.isNull()) ? content.asText() : "";
                    String reasoningText = (reasoning != null && !reasoning.isNull()) ? reasoning.asText() : null;
                    return new GenerateResult(contentText, reasoningText);
                }
            }
            throw new AiServiceException(SERVICE_NAME, "Invalid OpenAI response format");
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", response, e);
            throw new AiServiceException(SERVICE_NAME, "Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 OpenAI SSE 流式数据并返回带类型的 StreamEventDTO
     * 支持 Qwen3.6 的 reasoning 和 content 字段，区分思考过程和回答内容
     * 同时支持纯文本片段格式（非标准 SSE）
     */
    private Mono<StreamEventDTO> parseOpenAISseDataToEvent(String data) {
        if (data == null || data.isBlank()) {
            log.debug("SSE data is null or blank");
            return Mono.empty();
        }

        log.debug("SSE parse start: data={}", data);

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    // 检查是否有 reasoning（思考过程）
                    JsonNode reasoning = delta.get("reasoning");
                    JsonNode content = delta.get("content");

                    if (reasoning != null && !reasoning.isNull()) {
                        String reasoningText = reasoning.asText();
                        log.debug("SSE parsed reasoning: length={}", reasoningText.length());
                        return Mono.just(StreamEventDTO.builder()
                                .type("reasoning")
                                .content(reasoningText)
                                .reasoning(reasoningText)
                                .build());
                    }

                    if (content != null && !content.isNull()) {
                        String contentText = content.asText();
                        log.debug("SSE parsed content: length={}", contentText.length());
                        return Mono.just(StreamEventDTO.builder()
                                .type("token")
                                .content(contentText)
                                .build());
                    }
                }
            }
            log.debug("SSE data has no content or reasoning");
            return Mono.empty();
        } catch (Exception e) {
            // JSON 解析失败，可能是因为网络问题导致截断的 JSON 或非标准格式
            // 检查是否像截断的 JSON（缺少结尾）
            String trimmed = data != null ? data.trim() : "";
            if (trimmed.startsWith("{") && !trimmed.endsWith("}")) {
                log.warn("SSE received truncated JSON, discarding: length={}", trimmed.length());
            } else {
                log.warn("SSE data is not JSON, discarding: {}", trimmed.substring(0, Math.min(100, trimmed.length())));
            }
            // 不要将截断的数据作为 token 返回，避免答案包含乱码
            return Mono.empty();
        }
    }

    /**
     * LLM 生成结果，包含 content 和 reasoning
     */
    @lombok.Getter
    public static class GenerateResult {
        private final String content;
        private final String reasoning;

        public GenerateResult(String content, String reasoning) {
            this.content = content;
            this.reasoning = reasoning;
        }
    }
}