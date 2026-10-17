package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rag.ocr")
public class OcrProperties {

    @NotBlank(message = "OCR服务地址不能为空")
    private String serviceUrl = "http://localhost:28347";

    private String fallbackUrl = "http://localhost:28347";

    @Positive(message = "超时时间必须为正数")
    private int timeout = 300000;

    @Positive(message = "缓存TTL必须为正数")
    private int maxCacheTtl = 30;

    private int maxPages = 10;

    private boolean enabled = true;

    // 批量 OCR

    /** 是否启用批量OCR */
    private boolean batchEnabled = true;

    /** 每批次页数（与 AI SDK batch_size 对齐） */
    @Min(1)
    private int batchSize = 4;

    /** PDF 渲染 DPI，150=约 150KB/页 */
    @Min(50)
    @Max(300)
    private int batchImageDpi = 150;

    /** JPEG 压缩质量 0.0-1.0 */
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    private float batchImageQuality = 0.85f;

    /** 图片格式：jpeg 或 png */
    private String batchImageFormat = "jpeg";

    /** Batch 请求专用超时（毫秒），默认 600s = 单页超时 × 2 */
    @Positive
    private int batchReadTimeout = 600000;

    /** 内存 PDF 缓存最大条数（LRU），默认 20 */
    @Min(1)
    private int batchCacheMaxSize = 20;
}