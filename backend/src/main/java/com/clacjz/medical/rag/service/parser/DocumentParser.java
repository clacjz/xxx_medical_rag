package com.clacjz.medical.rag.service.parser;

import java.io.InputStream;
import java.util.List;

public interface DocumentParser {

    String parse(InputStream inputStream);

    String[] getSupportedExtensions();

    default boolean supports(String extension) {
        String ext = extension.toLowerCase().replaceFirst("^\\.", "");
        for (String supported : getSupportedExtensions()) {
            if (supported.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    record ParseResult(
            String content,
            List<PageContent> pages,
            int totalPages,
            int charCount,
            List<String> warnings
    ) {
        public record PageContent(int pageNum, String text, int charCount) {}
    }
}
