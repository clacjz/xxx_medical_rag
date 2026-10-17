package com.clacjz.medical.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Webhook 回调服务 — 文档处理完成后异步通知调用方，避免轮询。
 * 内置SSRF安全RestTemplate，在建立HTTP连接时检查目标IP是否为内网地址，
 * 防止DNS rebinding攻击。
 */
@Slf4j
@Service
public class WebhookService {

    private final RestTemplate restTemplate;

    public WebhookService() {
        this.restTemplate = createSsrfSafeRestTemplate();
    }

    /**
     * 测试用构造函数，允许注入mock RestTemplate
     */
    WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 测试用访问器，获取内部RestTemplate
     */
    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    private static RestTemplate createSsrfSafeRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                // 在实际建立连接前，检查目标IP是否为内网地址（防止DNS rebinding）
                URI uri = URI.create(connection.getURL().toString());
                String host = uri.getHost();
                if (host != null) {
                    InetAddress address = InetAddress.getByName(host);
                    if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                            || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                        throw new java.io.IOException("SSRF防护: 回调URL指向内网地址 " + address.getHostAddress());
                    }
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    /**
     * 发送处理完成回调
     *
     * @param callbackUrl 回调 URL（由调用方传入）
     * @param documentId  文档 ID
     * @param success     是否成功
     * @param storedCount 存储的向量数（成功时）
     * @param errorMessage 错误消息（失败时）
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> sendCallback(
            String callbackUrl,
            String documentId,
            boolean success,
            Integer storedCount,
            String errorMessage) {

        if (callbackUrl == null || callbackUrl.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("documentId", documentId);
        body.put("status", success ? "completed" : "failed");
        if (success) {
            body.put("storedCount", storedCount);
        } else {
            body.put("errorMessage", errorMessage);
        }

        try {
            log.info("Webhook: 发送回调到 {}  documentId={} status={}", callbackUrl, documentId, success ? "completed" : "failed");
            restTemplate.postForEntity(callbackUrl, body, String.class);
            log.info("Webhook: 回调成功 documentId={}", documentId);
        } catch (Exception e) {
            // 回调失败不影响主流程，仅记录日志
            log.warn("Webhook: 回调发送失败 documentId={} callbackUrl={} error={}",
                    documentId, callbackUrl, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }
}
