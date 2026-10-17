package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.mapper.ChatMessageMapper;
import com.clacjz.medical.rag.mapper.ChatSessionMapper;
import com.clacjz.medical.rag.model.entity.AuditLog;
import com.clacjz.medical.rag.model.entity.ChatMessage;
import com.clacjz.medical.rag.model.entity.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExportService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 14;
    private static final float TITLE_LINE_HEIGHT = 20;
    private static final float FONT_SIZE_TITLE = 14;
    private static final float FONT_SIZE_NORMAL = 10;
    private static final float FONT_SIZE_ROLE = 11;
    private static final int MAX_EXPORT_MESSAGE_COUNT = 10000;
    private static final int MAX_CONTENT_LENGTH_PER_MESSAGE = 100000;

    private static final String[] FONT_PATHS_REGULAR = {
            "fonts/NotoSansSC-Regular.ttf",
            "fonts/NotoSansSC-Regular.otf",
            "fonts/SourceHanSans-Regular.otf"
    };

    private static final String[] FONT_PATHS_BOLD = {
            "fonts/NotoSansSC-Bold.ttf",
            "fonts/NotoSansSC-Bold.otf"
    };

    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[\\\\\\[\\](){}.*+?^$|]");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`]+`");
    private static final Pattern MARKDOWN_ESCAPE_PATTERN = Pattern.compile("([*_`\\[\\]()>~#+\\-=|{}!])");

    public ExportService(ChatSessionMapper sessionMapper,
                        ChatMessageMapper messageMapper,
                        AuditLogMapper auditLogMapper,
                        ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("ExportService initialized with font fallback support");
    }

    private PDFont loadFontWithFallback(PDDocument document, String[] fontPaths, boolean embed) throws IOException {
        IOException lastException = null;

        for (String fontPath : fontPaths) {
            try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream(fontPath)) {
                if (fontStream == null) {
                    log.warn("Font file not found: {}", fontPath);
                    lastException = new IOException("Font file not found: " + fontPath);
                    continue;
                }

                PDFont font = PDType0Font.load(document, fontStream, embed);
                log.info("Successfully loaded font: {}", fontPath);
                return font;
            } catch (IOException e) {
                log.warn("Failed to load font {}: {}", fontPath, e);
                lastException = e;
            }
        }

        String errorMsg = "Failed to load any of the specified fonts";
        log.error(errorMsg, lastException);
        throw new BizException(ErrorCode.FONT_LOAD_FAILED, errorMsg);
    }

    private PDFont loadRegularFont(PDDocument document) throws IOException {
        return loadFontWithFallback(document, FONT_PATHS_REGULAR, true);
    }

    private PDFont loadBoldFont(PDDocument document) throws IOException {
        return loadFontWithFallback(document, FONT_PATHS_BOLD, true);
    }

    public String exportSessionToMarkdown(String sessionId) {
        try {
            ChatSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + sessionId);
            }

            validateSessionAccess(session);

            List<ChatMessage> messages = messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId);

            validateExportData(messages);

            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(escapeMarkdown(session.getTitle())).append("\n\n");

            markdown.append("## 会话信息\n\n");
            markdown.append("| 属性 | 值 |\n");
            markdown.append("|------|-----|\n");
            markdown.append("| 会话ID | `").append(escapeMarkdown(sessionId)).append("` |\n");
            markdown.append("| 创建时间 | ").append(formatDateTime(session.getCreatedAt())).append(" |\n");
            markdown.append("| 消息数 | ").append(messages.size()).append(" |\n");
            markdown.append("| 状态 | ").append(escapeMarkdown(session.getStatus())).append(" |\n");
            markdown.append("\n---\n\n");

            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if ("user".equals(message.getRole())) {
                    markdown.append("## 用户\n\n");
                    markdown.append(escapeMarkdown(message.getContent())).append("\n\n");
                } else if ("assistant".equals(message.getRole())) {
                    markdown.append("## 助手\n\n");
                    markdown.append(escapeMarkdown(message.getContent())).append("\n\n");

                    if (message.getSources() != null && !message.getSources().isEmpty()) {
                        markdown.append("### 参考来源\n\n");
                        for (ChatMessage.SourceReference source : message.getSources()) {
                            markdown.append("- **").append(escapeMarkdown(source.getTitle())).append("**");
                            if (source.getScore() != null) {
                                markdown.append(" (相似度: ").append(String.format("%.2f", source.getScore())).append(")");
                            }
                            markdown.append("\n");
                            if (source.getSnippet() != null && !source.getSnippet().isEmpty()) {
                                markdown.append("  > ").append(escapeMarkdown(truncateText(source.getSnippet(), 200))).append("\n");
                            }
                        }
                        markdown.append("\n");
                    }

                    if (message.getModelInfo() != null) {
                        markdown.append("**模型信息**: ").append(escapeMarkdown(message.getModelInfo().getModelName())).append("\n");
                    }

                    if (message.getTokenUsage() != null) {
                        markdown.append("**Token使用**: ").append(message.getTokenUsage().getTotalTokens())
                                .append(" (输入: ")
                                .append(message.getTokenUsage().getPromptTokens() != null ? message.getTokenUsage().getPromptTokens() : 0)
                                .append(" / 输出: ")
                                .append(message.getTokenUsage().getCompletionTokens() != null ? message.getTokenUsage().getCompletionTokens() : 0)
                                .append(")\n");
                    }

                    if (message.getGenerationTimeMs() != null) {
                        markdown.append("**生成耗时**: ").append(message.getGenerationTimeMs()).append("ms\n");
                    }
                }

                markdown.append("*").append(formatDateTime(message.getCreatedAt())).append("*\n\n");
                markdown.append("---\n\n");
            }

            log.info("Session exported to Markdown: sessionId={}, messageCount={}", sessionId, messages.size());
            return markdown.toString();

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to export session to Markdown: sessionId={}", sessionId, e);
            throw new BizException(ErrorCode.MARKDOWN_EXPORT_FAILED, "Markdown导出失败: " + e.getMessage());
        }
    }

    public byte[] exportSessionToPdf(String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + sessionId);
        }

        validateSessionAccess(session);

        List<ChatMessage> messages = messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId);

        validateExportData(messages);

        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = loadRegularFont(document);
            PDFont boldFont = loadBoldFont(document);

            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;

            addNewPage(document, pageHeight);

            float yPosition = pageHeight - MARGIN;

            yPosition = drawText(document, boldFont, yPosition, pageWidth,
                    session.getTitle(), FONT_SIZE_TITLE, true);
            yPosition -= LINE_HEIGHT;

            String[] headerLines = {
                    "会话ID: " + sessionId,
                    "创建时间: " + formatDateTime(session.getCreatedAt()),
                    "消息数: " + messages.size(),
                    "状态: " + session.getStatus()
            };
            for (String line : headerLines) {
                yPosition = drawText(document, regularFont, yPosition, pageWidth, line, FONT_SIZE_NORMAL, false);
            }
            yPosition -= LINE_HEIGHT * 2;

            for (ChatMessage message : messages) {
                yPosition = processMessageToPdf(document, pageHeight, pageWidth, yPosition,
                        message, regularFont, boldFont);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);

            log.info("Session exported to PDF: sessionId={}, messageCount={}, pageCount={}, fileSize={}",
                    sessionId, messages.size(), document.getNumberOfPages(), outputStream.size());
            return outputStream.toByteArray();

        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to export session to PDF: sessionId={}", sessionId, e);
            throw new BizException(ErrorCode.PDF_EXPORT_FAILED, "PDF导出失败: " + e.getMessage());
        }
    }

    private float processMessageToPdf(PDDocument document, float pageHeight, float pageWidth,
                                     float yPosition, ChatMessage message,
                                     PDFont regularFont, PDFont boldFont) throws IOException {
        float requiredHeight = calculateRequiredHeight(message, regularFont);

        if (yPosition < MARGIN + requiredHeight) {
            addNewPage(document, pageHeight);
            yPosition = pageHeight - MARGIN;
        }

        String roleLabel = "user".equals(message.getRole()) ? "【用户】" : "【助手】";
        yPosition = drawText(document, boldFont, yPosition, pageWidth, roleLabel, FONT_SIZE_ROLE, false);

        String content = message.getContent() != null ? message.getContent() : "";
        if (content.length() > MAX_CONTENT_LENGTH_PER_MESSAGE) {
            content = content.substring(0, MAX_CONTENT_LENGTH_PER_MESSAGE) + "...[内容已截断]";
            log.warn("Message content truncated for export: messageId={}, originalLength={}",
                    message.getId(), message.getContent().length());
        }

        List<String> contentLines = wrapText(content, pageWidth, regularFont, FONT_SIZE_NORMAL);
        for (String contentLine : contentLines) {
            if (yPosition < MARGIN + LINE_HEIGHT) {
                addNewPage(document, pageHeight);
                yPosition = pageHeight - MARGIN;
            }
            yPosition = drawText(document, regularFont, yPosition, pageWidth, contentLine, FONT_SIZE_NORMAL, false);
        }

        if ("assistant".equals(message.getRole()) && message.getSources() != null && !message.getSources().isEmpty()) {
            yPosition -= LINE_HEIGHT / 2;
            yPosition = drawText(document, regularFont, yPosition, pageWidth, "参考来源:", FONT_SIZE_NORMAL, false);
            for (ChatMessage.SourceReference source : message.getSources()) {
                String sourceLine = "- " + source.getTitle() +
                        (source.getScore() != null ? " (相似度: " + String.format("%.2f", source.getScore()) + ")" : "");
                yPosition = drawText(document, regularFont, yPosition, pageWidth, sourceLine, FONT_SIZE_NORMAL, false);
            }
        }

        yPosition = drawText(document, regularFont, yPosition, pageWidth,
                formatDateTime(message.getCreatedAt()), FONT_SIZE_NORMAL, false);
        yPosition -= LINE_HEIGHT;

        return yPosition;
    }

    private float calculateRequiredHeight(ChatMessage message, PDFont font) {
        float height = LINE_HEIGHT * 2;
        String content = message.getContent() != null ? message.getContent() : "";
        if (content.length() > MAX_CONTENT_LENGTH_PER_MESSAGE) {
            content = content.substring(0, MAX_CONTENT_LENGTH_PER_MESSAGE) + "...[内容已截断]";
        }
        height += wrapText(content, 500, font, FONT_SIZE_NORMAL).size() * (FONT_SIZE_NORMAL + 4);
        if ("assistant".equals(message.getRole()) && message.getSources() != null) {
            height += message.getSources().size() * LINE_HEIGHT;
        }
        return height;
    }

    private void addNewPage(PDDocument document, float pageHeight) {
        PDPage newPage = new PDPage(new PDRectangle(PDRectangle.A4.getWidth(), pageHeight));
        document.addPage(newPage);
    }

    private float drawText(PDDocument document, PDFont font,
                          float yPosition, float pageWidth, String text, float fontSize, boolean isTitle) {
        try {
            List<String> lines = wrapText(text, pageWidth, font, fontSize);
            PDPage currentPage = document.getPage(document.getNumberOfPages() - 1);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                float xPosition = MARGIN;

                PDPageContentStream contentStream = new PDPageContentStream(document, currentPage,
                        PDPageContentStream.AppendMode.APPEND, true, true);
                try {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(xPosition, yPosition);
                    String safeText = sanitizePdfText(line);
                    contentStream.showText(safeText);
                    contentStream.endText();
                } finally {
                    contentStream.close();
                }
                yPosition -= fontSize + 4;
            }
        } catch (IOException e) {
            log.error("Error drawing text on PDF", e);
            throw new BizException(ErrorCode.PDF_EXPORT_FAILED, "PDF文本绘制失败: " + e.getMessage());
        }
        return yPosition;
    }

    private String sanitizePdfText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("\n", " ")
                   .replace("\r", " ");
    }

    private List<String> wrapText(String text, float maxWidth, PDFont font, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder currentLine = new StringBuilder();
            float currentWidth = 0;

            for (int i = 0; i < paragraph.length(); i++) {
                String charStr = String.valueOf(paragraph.charAt(i));
                float charWidth;
                try {
                    charWidth = font.getStringWidth(charStr) / 1000 * fontSize;
                    if (charWidth <= 0) {
                        charWidth = fontSize * 0.6f;
                    }
                } catch (Exception e) {
                    charWidth = fontSize * 0.6f;
                }

                if (currentWidth + charWidth > maxWidth && currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    currentWidth = 0;
                }

                currentLine.append(charStr);
                currentWidth += charWidth;
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    public String exportAuditLogsToCsv(LocalDateTime startTime, LocalDateTime endTime) {
        Page<AuditLog> mpPage = new Page<>(1, MAX_EXPORT_MESSAGE_COUNT);
        IPage<AuditLog> logs = auditLogMapper.selectByFilters(null, null, null, startTime, endTime, mpPage);

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        csv.append("ID,用户ID,用户名,显示名称,操作,级别,资源类型,资源ID,IP地址,时间,状态,详情\n");

        for (AuditLog auditLog : logs.getRecords()) {
            csv.append(escapeCsv(auditLog.getId())).append(",");
            csv.append(escapeCsv(auditLog.getUserId())).append(",");
            csv.append(escapeCsv(auditLog.getUsername())).append(",");
            csv.append(escapeCsv(auditLog.getDisplayName())).append(",");
            csv.append(escapeCsv(auditLog.getAction() != null ? auditLog.getAction().name() : "")).append(",");
            csv.append(escapeCsv(auditLog.getLevel() != null ? auditLog.getLevel().name() : "")).append(",");
            csv.append(escapeCsv(auditLog.getResourceType())).append(",");
            csv.append(escapeCsv(auditLog.getResourceId())).append(",");
            csv.append(escapeCsv(auditLog.getIpAddress())).append(",");
            csv.append(escapeCsv(formatDateTime(auditLog.getTimestamp()))).append(",");
            csv.append(escapeCsv(auditLog.getStatus())).append(",");
            csv.append(escapeCsv(auditLog.getDetails()));
            csv.append("\n");
        }

        log.info("Audit logs exported to CSV: count={}, startTime={}, endTime={}",
                logs.getTotal(), startTime, endTime);
        return csv.toString();
    }

    public String exportAuditLogsToJson(LocalDateTime startTime, LocalDateTime endTime) {
        Page<AuditLog> mpPage = new Page<>(1, MAX_EXPORT_MESSAGE_COUNT);
        IPage<AuditLog> logs = auditLogMapper.selectByFilters(null, null, null, startTime, endTime, mpPage);

        try {
            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.registerModule(new JavaTimeModule());
            jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);

            Map<String, Object> exportData = new HashMap<>();
            exportData.put("exportTime", LocalDateTime.now().format(DATETIME_FORMATTER));
            exportData.put("startTime", startTime.format(DATETIME_FORMATTER));
            exportData.put("endTime", endTime.format(DATETIME_FORMATTER));
            exportData.put("totalCount", logs.getTotal());

            List<Map<String, Object>> logEntries = logs.getRecords().stream()
                    .map(this::convertAuditLogToMap)
                    .toList();
            exportData.put("logs", logEntries);

            String json = jsonMapper.writeValueAsString(exportData);

            log.info("Audit logs exported to JSON: count={}, startTime={}, endTime={}",
                    logs.getTotal(), startTime, endTime);
            return json;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to export audit logs to JSON", e);
            throw new BizException(ErrorCode.EXPORT_FAILED, "JSON导出失败: " + e.getMessage());
        }
    }

    private Map<String, Object> convertAuditLogToMap(AuditLog auditLog) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", auditLog.getId());
        map.put("userId", auditLog.getUserId());
        map.put("username", auditLog.getUsername());
        map.put("displayName", auditLog.getDisplayName());
        map.put("action", auditLog.getAction() != null ? auditLog.getAction().name() : null);
        map.put("actionDescription", auditLog.getAction() != null ? auditLog.getAction().getDescription() : null);
        map.put("level", auditLog.getLevel() != null ? auditLog.getLevel().name() : null);
        map.put("levelDescription", auditLog.getLevel() != null ? auditLog.getLevel().getDescription() : null);
        map.put("resourceType", auditLog.getResourceType());
        map.put("resourceId", auditLog.getResourceId());
        map.put("description", auditLog.getDescription());
        map.put("ipAddress", auditLog.getIpAddress());
        map.put("userAgent", auditLog.getUserAgent());
        map.put("details", auditLog.getDetails());
        map.put("timestamp", auditLog.getTimestamp() != null ? auditLog.getTimestamp().format(DATETIME_FORMATTER) : null);
        map.put("status", auditLog.getStatus());
        return map;
    }

    private void validateSessionAccess(ChatSession session) {
        String userId = TenantContext.getUserId();
        String departmentId = TenantContext.getDepartmentId();

        if (TenantContext.isAdmin()) {
            if (departmentId != null && !session.getDepartmentId().equals(departmentId)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "无权访问该会话");
            }
            return;
        }

        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权访问该会话");
        }
    }

    private void validateExportData(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BizException(ErrorCode.EXPORT_VALIDATION_FAILED, "没有可导出的消息");
        }

        if (messages.size() > MAX_EXPORT_MESSAGE_COUNT) {
            log.warn("Export message count exceeds limit: {} > {}", messages.size(), MAX_EXPORT_MESSAGE_COUNT);
            throw new BizException(ErrorCode.EXPORT_DATA_TOO_LARGE,
                    String.format("导出消息数量过多(>%d)，请分段导出", MAX_EXPORT_MESSAGE_COUNT));
        }

        long totalContentLength = messages.stream()
                .mapToLong(m -> m.getContent() != null ? m.getContent().length() : 0)
                .sum();

        if (totalContentLength > MAX_EXPORT_MESSAGE_COUNT * MAX_CONTENT_LENGTH_PER_MESSAGE) {
            log.warn("Total content length too large: {}", totalContentLength);
            throw new BizException(ErrorCode.EXPORT_DATA_TOO_LARGE, "导出内容总长度过大，请减少消息数量");
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return MARKDOWN_ESCAPE_PATTERN.matcher(text).replaceAll("\\\\$1");
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "";
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
