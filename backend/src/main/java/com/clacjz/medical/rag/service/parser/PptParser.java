package com.clacjz.medical.rag.service.parser;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PptParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        StringBuilder allText = new StringBuilder();

        try {
            byte[] header = new byte[4];
            inputStream.mark(4);
            inputStream.read(header);
            inputStream.reset();

            boolean isPptx = header[0] == 0x50 && header[1] == 0x4B; // ZIP format

            if (isPptx) {
                allText = parsePptx(inputStream);
            } else {
                allText = parsePpt(inputStream);
            }

            String result = allText.toString().trim();
            log.info("PPT document parsed successfully: {} chars", result.length());

            return result;

        } catch (Exception e) {
            log.error("Error parsing PPT file", e);
            throw new BizException(ErrorCode.DOCUMENT_PARSING_FAILED, "Failed to parse PPT file: " + e.getMessage());
        }
    }

    private StringBuilder parsePptx(InputStream inputStream) throws Exception {
        StringBuilder allText = new StringBuilder();

        try (XMLSlideShow pptx = new XMLSlideShow(inputStream)) {
            List<XSLFSlide> slides = pptx.getSlides();
            log.debug("PPTX has {} slides", slides.size());

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                allText.append("## Slide ").append(i + 1).append("\n");

                List<XSLFTextShape> shapes = Arrays.asList(slide.getPlaceholders());
                for (XSLFTextShape shape : shapes) {
                    extractTextFromPptxShape(allText, shape);
                }

                // 同时收集非占位符形状
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape && !shapes.contains(shape)) {
                        extractTextFromPptxShape(allText, (XSLFTextShape) shape);
                    }
                }

                allText.append("\n");
            }
        }

        return allText;
    }

    private void extractTextFromPptxShape(StringBuilder allText, XSLFTextShape shape) {
        List<XSLFTextParagraph> paragraphs = shape.getTextParagraphs();
        for (XSLFTextParagraph paragraph : paragraphs) {
            List<XSLFTextRun> runs = paragraph.getTextRuns();
            for (XSLFTextRun run : runs) {
                String text = run.getRawText();
                if (text != null && !text.isBlank()) {
                    String cleanedText = textCleaner.clean(text);
                    allText.append(cleanedText);
                }
            }
            if (!paragraph.getTextRuns().isEmpty()) {
                allText.append("\n");
            }
        }
    }

    private StringBuilder parsePpt(InputStream inputStream) throws Exception {
        StringBuilder allText = new StringBuilder();

        try (HSLFSlideShow ppt = new HSLFSlideShow(inputStream)) {
            List<HSLFSlide> slides = ppt.getSlides();
            log.debug("PPT has {} slides", slides.size());

            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                allText.append("## Slide ").append(i + 1).append("\n");

                for (var shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        extractTextFromPptShape(allText, (HSLFTextShape) shape);
                    }
                }

                allText.append("\n");
            }
        }

        return allText;
    }

    private void extractTextFromPptShape(StringBuilder allText, HSLFTextShape shape) {
        List<HSLFTextParagraph> paragraphs = shape.getTextParagraphs();
        for (HSLFTextParagraph paragraph : paragraphs) {
            List<HSLFTextRun> runs = paragraph.getTextRuns();
            for (HSLFTextRun run : runs) {
                String text = run.getRawText();
                if (text != null && !text.isBlank()) {
                    String cleanedText = textCleaner.clean(text);
                    allText.append(cleanedText);
                }
            }
            if (!paragraph.getTextRuns().isEmpty()) {
                allText.append("\n");
            }
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"pptx", "ppt"};
    }
}
