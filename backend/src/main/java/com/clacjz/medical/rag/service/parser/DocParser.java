package com.clacjz.medical.rag.service.parser;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        StringBuilder allText = new StringBuilder();

        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            Range range = document.getRange();

            int paragraphCount = range.numParagraphs();
            log.debug("DOC document has {} paragraphs", paragraphCount);

            for (int i = 0; i < paragraphCount; i++) {
                try {
                    Paragraph paragraph = range.getParagraph(i);
                    String text = paragraph.text();
                    if (text != null && !text.isBlank()) {
                        String cleanedText = textCleaner.clean(text);
                        allText.append(cleanedText).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract paragraph {}", i, e);
                }
            }

            String result = allText.toString().trim();
            log.info("DOC document parsed successfully: {} chars from {} paragraphs",
                    result.length(), paragraphCount);

            return result;

        } catch (Exception e) {
            log.error("Error parsing DOC file", e);
            throw new BizException(ErrorCode.DOCUMENT_PARSING_FAILED, "Failed to parse DOC file: " + e.getMessage());
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"doc"};
    }
}
