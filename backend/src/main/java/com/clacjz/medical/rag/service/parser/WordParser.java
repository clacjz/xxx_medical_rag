package com.clacjz.medical.rag.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WordParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        List<String> warnings = new ArrayList<>();
        StringBuilder allText = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {

            int paragraphCount = document.getParagraphs().size();
            int tableCount = document.getTables().size();
            log.debug("Word document has {} paragraphs and {} tables", paragraphCount, tableCount);

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                try {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        String cleanedText = textCleaner.clean(text);
                        allText.append(cleanedText).append("\n");
                    }
                } catch (Exception e) {
                    warnings.add("Failed to extract paragraph: " + e.getMessage());
                    log.warn("Failed to extract paragraph", e);
                }
            }

            for (int i = 0; i < document.getTables().size(); i++) {
                try {
                    XWPFTable table = document.getTables().get(i);
                    allText.append("\n[Table ").append(i + 1).append("]\n");

                    for (int rowIdx = 0; rowIdx < table.getNumberOfRows(); rowIdx++) {
                        List<String> rowCells = new ArrayList<>();
                        var row = table.getRow(rowIdx);

                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.isBlank()) {
                                rowCells.add(textCleaner.clean(cellText));
                            }
                        }

                        if (!rowCells.isEmpty()) {
                            allText.append(String.join(" | ", rowCells)).append("\n");
                        }
                    }
                } catch (Exception e) {
                    warnings.add("Failed to extract table " + (i + 1) + ": " + e.getMessage());
                    log.warn("Failed to extract table {}", i, e);
                }
            }

            String result = allText.toString().trim();
            log.info("Word document parsed successfully: {} chars from {} paragraphs and {} tables",
                    result.length(), paragraphCount, tableCount);

            return result;

        } catch (Exception e) {
            log.error("Error parsing Word file", e);
            throw new RuntimeException("Failed to parse Word file: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"docx"};
    }
}
