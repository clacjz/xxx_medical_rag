package com.clacjz.medical.rag.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        List<PageLayout> layouts = parseWithLayout(inputStream);
        return layouts.stream()
                .map(PageLayout::text)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 解析 PDF 并返回每页的布局信息
     * @param inputStream PDF 文件输入流
     * @return 每页的 PageLayout 列表
     */
    public List<PageLayout> parseWithLayout(InputStream inputStream) {
        List<PageLayout> layouts = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            int totalPages = document.getNumberOfPages();
            log.debug("PDF has {} pages", totalPages);

            int globalPos = 0;

            for (int i = 1; i <= totalPages; i++) {
                try {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);

                    if (pageText == null || pageText.isBlank()) {
                        log.debug("Page {} is empty", i);
                        // 空页也记录，但文本为空
                        layouts.add(PageLayout.builder()
                                .pageNumber(i)
                                .text("")
                                .startPos(globalPos)
                                .endPos(globalPos)
                                .build());
                    } else {
                        String cleanedText = textCleaner.clean(pageText);
                        int textLength = cleanedText.length();

                        layouts.add(PageLayout.builder()
                                .pageNumber(i)
                                .text(cleanedText)
                                .startPos(globalPos)
                                .endPos(globalPos + textLength)
                                .build());

                        globalPos += textLength + 2; // 加上 "\n\n" 的长度
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract text from page {}", i, e);
                    layouts.add(PageLayout.builder()
                            .pageNumber(i)
                            .text("")
                            .startPos(globalPos)
                            .endPos(globalPos)
                            .build());
                }
            }

            log.info("PDF parsed with layout: {} pages, total {} chars", layouts.size(), globalPos);
            return layouts;

        } catch (Exception e) {
            log.error("Error parsing PDF file", e);
            throw new RuntimeException("Failed to parse PDF file: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"pdf"};
    }
}
