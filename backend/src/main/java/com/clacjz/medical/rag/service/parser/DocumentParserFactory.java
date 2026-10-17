package com.clacjz.medical.rag.service.parser;

import com.clacjz.medical.rag.config.ParserProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;
    private final ParserProperties parserProperties;
    private final Map<String, DocumentParser> parserMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (DocumentParser parser : parsers) {
            for (String extension : parser.getSupportedExtensions()) {
                parserMap.put(extension.toLowerCase(), parser);
                log.debug("Registered parser for extension: {}", extension);
            }
        }
        log.info("Document parser factory initialized with {} parsers", parserMap.size());
    }

    public DocumentParser getParser(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        String extension = extractExtension(fileName);
        DocumentParser parser = parserMap.get(extension.toLowerCase());

        if (parser == null) {
            throw new UnsupportedOperationException(
                    "No parser found for file extension: " + extension + ". " +
                    "Supported types: " + String.join(", ", parserProperties.getSupportedTypes()));
        }

        return parser;
    }

    public String parse(String fileName, InputStream inputStream) {
        DocumentParser parser = getParser(fileName);
        return parser.parse(inputStream);
    }

    /**
     * 解析文件并返回页面布局信息（仅 PDF 支持）
     * @param fileName 文件名
     * @param inputStream 输入流
     * @return 页面布局列表（非 PDF 文件返回空列表）
     */
    public List<PageLayout> parseWithLayout(String fileName, InputStream inputStream) {
        String extension = extractExtension(fileName).toLowerCase();
        if ("pdf".equals(extension)) {
            PdfParser pdfParser = (PdfParser) getParser(fileName);
            return pdfParser.parseWithLayout(inputStream);
        }
        // 非 PDF 文件不支持页码布局
        return List.of();
    }

    public boolean isSupported(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String extension = extractExtension(fileName);
        return parserMap.containsKey(extension.toLowerCase());
    }

    public List<String> getSupportedExtensions() {
        return List.copyOf(parserMap.keySet());
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
