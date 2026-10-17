package com.clacjz.medical.rag.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文档类型Schema加载服务
 * 加载预定义的JSON Schema模板（如 manual.json），用于GLM-OCR结构化提取
 */
@Slf4j
@Component
public class DocSchemaService {

    @Value("classpath:schemas/manual.json")
    private Resource manualSchemaResource;

    private String manualSchema;

    @PostConstruct
    public void init() {
        try {
            manualSchema = new String(manualSchemaResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded manual schema ({} chars)", manualSchema.length());
        } catch (IOException e) {
            log.error("Failed to load manual schema", e);
            manualSchema = "{}";
        }
    }

    public String getManualSchema() {
        return manualSchema;
    }
}
